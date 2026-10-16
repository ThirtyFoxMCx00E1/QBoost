// SPDX-License-Identifier: GPL-3.0-or-later
//
// Qboost Scaler, real path: the captured game frames (AHardwareBuffers from an ImageReader fed by
// MediaProjection) go through the LSFG frame generation engine (Lossless Scaling's technique, shaders
// translated at run time from the user's own Lossless.dll) and are presented on Qboost's overlay with
// a Vulkan swapchain. The engine sources come from DroidDeck (framegen/lsfg, GPL-3.0-or-later); this
// file is the Qboost glue that replaces DroidDeck's Wayland compositor as the frame source.

#include "qboost_lsfg.h"

#include <android/hardware_buffer.h>
#include <android/log.h>
#include <android/native_window.h>

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <thread>
#include <vector>

#include "lsfg/lsfg_probe.h"
#include "lsfg/lsfg_vkd.h"

#define QLOGI(...) __android_log_print(ANDROID_LOG_INFO, "QboostLsfg", __VA_ARGS__)
#define QLOGW(...) __android_log_print(ANDROID_LOG_WARN, "QboostLsfg", __VA_ARGS__)

namespace qboost {
namespace {

constexpr VkFormat kFormat = VK_FORMAT_R8G8B8A8_UNORM;

#define QB_VK_TABLE(X)                                                                              \
    X(CreateInstance) X(DestroyInstance) X(EnumeratePhysicalDevices) X(GetPhysicalDeviceProperties)  \
    X(GetPhysicalDeviceMemoryProperties) X(GetPhysicalDeviceSurfaceCapabilitiesKHR)                   \
    X(GetPhysicalDeviceSurfaceFormatsKHR) X(GetPhysicalDeviceSurfacePresentModesKHR)                  \
    X(GetPhysicalDeviceQueueFamilyProperties) X(GetPhysicalDeviceSurfaceSupportKHR)                   \
    X(GetPhysicalDeviceFormatProperties) X(GetPhysicalDeviceFeatures2) X(CreateDevice)               \
    X(DestroySurfaceKHR) X(CreateAndroidSurfaceKHR) X(GetDeviceProcAddr) X(DestroyDevice)            \
    X(GetDeviceQueue) X(DeviceWaitIdle) X(CreateSwapchainKHR) X(DestroySwapchainKHR)                 \
    X(GetSwapchainImagesKHR) X(AcquireNextImageKHR) X(QueuePresentKHR) X(QueueSubmit)                \
    X(CreateQueryPool) X(DestroyQueryPool) X(CmdResetQueryPool) X(CmdWriteTimestamp)                 \
    X(GetQueryPoolResults) X(CreateRenderPass) X(DestroyRenderPass) X(CreateFramebuffer)             \
    X(DestroyFramebuffer) X(CreateImageView) X(DestroyImageView) X(CreateImage) X(DestroyImage)      \
    X(CreateBuffer) X(DestroyBuffer) X(AllocateMemory) X(FreeMemory) X(MapMemory)                    \
    X(FlushMappedMemoryRanges) X(BindBufferMemory) X(BindImageMemory) X(GetBufferMemoryRequirements) \
    X(GetImageMemoryRequirements) X(CreateDescriptorSetLayout) X(DestroyDescriptorSetLayout)         \
    X(CreateDescriptorPool) X(DestroyDescriptorPool) X(AllocateDescriptorSets) X(FreeDescriptorSets) \
    X(UpdateDescriptorSets) X(CreatePipelineLayout) X(DestroyPipelineLayout) X(CreateShaderModule)   \
    X(DestroyShaderModule) X(CreateGraphicsPipelines) X(DestroyPipeline) X(CreateCommandPool)        \
    X(DestroyCommandPool) X(AllocateCommandBuffers) X(FreeCommandBuffers) X(BeginCommandBuffer)      \
    X(EndCommandBuffer) X(ResetCommandBuffer) X(CmdBeginRenderPass) X(CmdEndRenderPass)              \
    X(CmdBindPipeline) X(CmdBindDescriptorSets) X(CmdDraw) X(CmdPushConstants) X(CmdSetViewport)     \
    X(CmdSetScissor) X(CmdPipelineBarrier) X(CmdCopyImage) X(CmdBlitImage) X(CmdDispatch)            \
    X(CreateComputePipelines) X(UnmapMemory) X(CmdClearColorImage) X(ResetDescriptorPool)            \
    X(CmdCopyBufferToImage) X(CreateSampler) X(DestroySampler) X(CreateSemaphore)                    \
    X(DestroySemaphore) X(CreateFence) X(DestroyFence) X(WaitForFences) X(ResetFences)               \
    X(GetFenceStatus) X(GetAndroidHardwareBufferPropertiesANDROID)

void fillInstance(VkTable& t, VkInstance inst) {
#define X(n) t.n = reinterpret_cast<PFN_vk##n>(vkGetInstanceProcAddr(inst, "vk" #n));
    QB_VK_TABLE(X)
#undef X
}

void fillDevice(VkTable& t, VkDevice dev) {
    if (!t.GetDeviceProcAddr) return;
#define X(n)                                                                                \
    if (auto p = reinterpret_cast<PFN_vk##n>(t.GetDeviceProcAddr(dev, "vk" #n))) t.n = p;
    QB_VK_TABLE(X)
#undef X
}

bool hasExt(const std::vector<VkExtensionProperties>& exts, const char* name) {
    for (const auto& e : exts)
        if (std::strcmp(e.extensionName, name) == 0) return true;
    return false;
}

VkImageMemoryBarrier barrier(VkImage image, VkAccessFlags src, VkAccessFlags dst, VkImageLayout from,
                             VkImageLayout to, uint32_t srcFamily = VK_QUEUE_FAMILY_IGNORED,
                             uint32_t dstFamily = VK_QUEUE_FAMILY_IGNORED) {
    VkImageMemoryBarrier b{};
    b.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    b.srcAccessMask = src;
    b.dstAccessMask = dst;
    b.oldLayout = from;
    b.newLayout = to;
    b.srcQueueFamilyIndex = srcFamily;
    b.dstQueueFamilyIndex = dstFamily;
    b.image = image;
    b.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    return b;
}

int64_t nowNs() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
               std::chrono::steady_clock::now().time_since_epoch())
        .count();
}

}  // namespace

Pipeline::Pipeline() = default;

Pipeline::~Pipeline() { destroy(); }

void Pipeline::fail(const char* what) {
    std::snprintf(error_, sizeof(error_), "%s", what);
    QLOGW("%s", what);
}

uint32_t Pipeline::memoryType(uint32_t bits, VkMemoryPropertyFlags want) const {
    for (uint32_t i = 0; i < memProps_.memoryTypeCount; i++)
        if ((bits & (1u << i)) && (memProps_.memoryTypes[i].propertyFlags & want) == want) return i;
    for (uint32_t i = 0; i < memProps_.memoryTypeCount; i++)
        if (bits & (1u << i)) return i;
    return UINT32_MAX;
}

bool Pipeline::createInstance() {
    uint32_t loaderVersion = VK_API_VERSION_1_1;
    auto enumVersion = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(
        vkGetInstanceProcAddr(VK_NULL_HANDLE, "vkEnumerateInstanceVersion"));
    if (enumVersion) enumVersion(&loaderVersion);
    if (loaderVersion < VK_API_VERSION_1_1) {
        fail("Vulkan 1.1 or newer is needed");
        return false;
    }
    VkApplicationInfo app{};
    app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.pApplicationName = "Qboost Scaler";
    app.pEngineName = "LSFG";
    app.apiVersion = std::min<uint32_t>(loaderVersion, VK_API_VERSION_1_3);
    const char* exts[] = {VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
    VkInstanceCreateInfo ici{};
    ici.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    ici.pApplicationInfo = &app;
    ici.enabledExtensionCount = window_ ? 2 : 0;
    ici.ppEnabledExtensionNames = exts;
    if (vkCreateInstance(&ici, nullptr, &instance_) != VK_SUCCESS) {
        fail("could not create a Vulkan instance");
        return false;
    }
    fillInstance(t_, instance_);
    uint32_t n = 0;
    t_.EnumeratePhysicalDevices(instance_, &n, nullptr);
    if (n == 0) {
        fail("no Vulkan GPU");
        return false;
    }
    std::vector<VkPhysicalDevice> pds(n);
    t_.EnumeratePhysicalDevices(instance_, &n, pds.data());
    pd_ = pds[0];
    t_.GetPhysicalDeviceMemoryProperties(pd_, &memProps_);
    VkPhysicalDeviceProperties props{};
    t_.GetPhysicalDeviceProperties(pd_, &props);
    std::snprintf(gpuName_, sizeof(gpuName_), "%s", props.deviceName);
    return true;
}

bool Pipeline::probeCaps() {
    uint32_t n = 0;
    auto enumExts = reinterpret_cast<PFN_vkEnumerateDeviceExtensionProperties>(
        vkGetInstanceProcAddr(instance_, "vkEnumerateDeviceExtensionProperties"));
    enumExts(pd_, nullptr, &n, nullptr);
    deviceExts_.resize(n);
    if (n) enumExts(pd_, nullptr, &n, deviceExts_.data());

    caps_ = lsfg::Caps{};
    caps_.features = lsfg::queryFeatures(t_, pd_, deviceExts_, false);
    caps_.probedFormat = kFormat;
    caps_.storageOnSwapchainFormat = lsfg::probeStorageFormat(t_, pd_, kFormat);
    caps_.linearBlitOnSwapchainFormat = lsfg::probeLinearBlit(t_, pd_, kFormat);
    caps_.featuresEnabled = caps_.features.deviceGatesPass();
    lsfg::explain(caps_);
    if (!caps_.supported()) {
        std::snprintf(error_, sizeof(error_), "%s: %s", gpuName_, caps_.reason);
        return false;
    }
    if (!hasExt(deviceExts_, VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME)) {
        fail("the GPU driver cannot read Android hardware buffers");
        return false;
    }
    return true;
}

bool Pipeline::createDevice() {
    if (window_) {
        VkAndroidSurfaceCreateInfoKHR sci{};
        sci.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
        sci.window = window_;
        if (t_.CreateAndroidSurfaceKHR(instance_, &sci, nullptr, &surface_) != VK_SUCCESS) {
            fail("could not attach Vulkan to the overlay");
            return false;
        }
    }
    uint32_t nq = 0;
    t_.GetPhysicalDeviceQueueFamilyProperties(pd_, &nq, nullptr);
    std::vector<VkQueueFamilyProperties> qf(nq);
    t_.GetPhysicalDeviceQueueFamilyProperties(pd_, &nq, qf.data());
    qfam_ = UINT32_MAX;
    for (uint32_t i = 0; i < nq; i++) {
        const VkQueueFlags need = VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT;
        if ((qf[i].queueFlags & need) != need) continue;
        VkBool32 present = VK_TRUE;
        if (surface_) t_.GetPhysicalDeviceSurfaceSupportKHR(pd_, i, surface_, &present);
        if (present) {
            qfam_ = i;
            break;
        }
    }
    if (qfam_ == UINT32_MAX) {
        fail("no GPU queue can draw on the overlay");
        return false;
    }

    std::vector<const char*> exts = lsfg::extensionNames(caps_.features);
    if (surface_) exts.push_back(VK_KHR_SWAPCHAIN_EXTENSION_NAME);
    exts.push_back(VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME);
    for (const char* dep : {VK_EXT_QUEUE_FAMILY_FOREIGN_EXTENSION_NAME,
                            VK_KHR_SAMPLER_YCBCR_CONVERSION_EXTENSION_NAME,
                            VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME,
                            VK_KHR_DEDICATED_ALLOCATION_EXTENSION_NAME,
                            VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME,
                            VK_KHR_MAINTENANCE1_EXTENSION_NAME, VK_KHR_BIND_MEMORY_2_EXTENSION_NAME}) {
        if (hasExt(deviceExts_, dep)) exts.push_back(dep);
    }
    foreignQueue_ = hasExt(deviceExts_, VK_EXT_QUEUE_FAMILY_FOREIGN_EXTENSION_NAME);

    const bool core12 = caps_.features.deviceApiVersion >= VK_API_VERSION_1_2;
    VkPhysicalDeviceVulkan12Features v12{};
    v12.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES;
    v12.vulkanMemoryModel = VK_TRUE;
    v12.vulkanMemoryModelDeviceScope = caps_.features.vulkanMemoryModelDeviceScope ? VK_TRUE : VK_FALSE;
    VkPhysicalDeviceVulkanMemoryModelFeaturesKHR mm{};
    mm.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_MEMORY_MODEL_FEATURES_KHR;
    mm.vulkanMemoryModel = VK_TRUE;
    mm.vulkanMemoryModelDeviceScope = v12.vulkanMemoryModelDeviceScope;
    VkPhysicalDeviceFeatures2 f2{};
    f2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;
    f2.pNext = core12 ? static_cast<void*>(&v12) : static_cast<void*>(&mm);
    f2.features.shaderStorageImageWriteWithoutFormat = VK_TRUE;
    f2.features.shaderStorageImageExtendedFormats = VK_TRUE;

    const float priority = 1.0f;
    VkDeviceQueueCreateInfo qci{};
    qci.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    qci.queueFamilyIndex = qfam_;
    qci.queueCount = 1;
    qci.pQueuePriorities = &priority;
    VkDeviceCreateInfo dci{};
    dci.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    dci.pNext = &f2;
    dci.queueCreateInfoCount = 1;
    dci.pQueueCreateInfos = &qci;
    dci.enabledExtensionCount = (uint32_t)exts.size();
    dci.ppEnabledExtensionNames = exts.data();
    if (t_.CreateDevice(pd_, &dci, nullptr, &device_) != VK_SUCCESS) {
        fail("the GPU refused the frame generation features");
        return false;
    }
    fillDevice(t_, device_);
    t_.GetDeviceQueue(device_, qfam_, 0, &queue_);

    VkCommandPoolCreateInfo pci{};
    pci.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    pci.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    pci.queueFamilyIndex = qfam_;
    if (t_.CreateCommandPool(device_, &pci, nullptr, &pool_) != VK_SUCCESS) {
        fail("could not create GPU command pool");
        return false;
    }
    VkCommandBufferAllocateInfo cai{};
    cai.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    cai.commandPool = pool_;
    cai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cai.commandBufferCount = kSlots;
    if (t_.AllocateCommandBuffers(device_, &cai, slotCmd_) != VK_SUCCESS) {
        fail("could not allocate GPU command buffers");
        return false;
    }
    VkFenceCreateInfo fci{};
    fci.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    fci.flags = VK_FENCE_CREATE_SIGNALED_BIT;
    VkSemaphoreCreateInfo sci{};
    sci.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
    for (uint32_t i = 0; i < kSlots; i++) {
        if (t_.CreateFence(device_, &fci, nullptr, &slotFence_[i]) != VK_SUCCESS ||
            t_.CreateSemaphore(device_, &sci, nullptr, &slotAcquire_[i]) != VK_SUCCESS) {
            fail("could not create GPU sync objects");
            return false;
        }
    }
    return true;
}

bool Pipeline::createSwapchain() {
    if (!surface_) return true;
    VkSurfaceCapabilitiesKHR sc{};
    t_.GetPhysicalDeviceSurfaceCapabilitiesKHR(pd_, surface_, &sc);
    uint32_t nf = 0;
    t_.GetPhysicalDeviceSurfaceFormatsKHR(pd_, surface_, &nf, nullptr);
    std::vector<VkSurfaceFormatKHR> formats(nf);
    t_.GetPhysicalDeviceSurfaceFormatsKHR(pd_, surface_, &nf, formats.data());
    VkSurfaceFormatKHR chosen{VK_FORMAT_UNDEFINED, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR};
    for (const auto& f : formats)
        if (f.format == kFormat) chosen = f;
    if (chosen.format == VK_FORMAT_UNDEFINED) {
        fail("the overlay has no RGBA8 format");
        return false;
    }

    VkSwapchainCreateInfoKHR ci{};
    ci.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
    ci.surface = surface_;
    ci.minImageCount = std::max<uint32_t>(sc.minImageCount, 3);
    if (sc.maxImageCount) ci.minImageCount = std::min(ci.minImageCount, sc.maxImageCount);
    ci.imageFormat = chosen.format;
    ci.imageColorSpace = chosen.colorSpace;
    // The swapchain is the capture size; SurfaceFlinger scales it to the screen in the display
    // hardware, so a reduced capture costs no GPU time to show.
    ci.imageExtent = {width_, height_};
    ci.imageArrayLayers = 1;
    ci.imageUsage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    ci.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    ci.preTransform = VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR;
    ci.compositeAlpha = (sc.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                            ? VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR
                            : VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;
    ci.presentMode = VK_PRESENT_MODE_FIFO_KHR;
    ci.clipped = VK_TRUE;
    ci.oldSwapchain = swapchain_;
    VkSwapchainKHR fresh = VK_NULL_HANDLE;
    const VkResult r = t_.CreateSwapchainKHR(device_, &ci, nullptr, &fresh);
    if (swapchain_) {
        t_.DeviceWaitIdle(device_);
        destroySwapchainImages();
        t_.DestroySwapchainKHR(device_, swapchain_, nullptr);
        swapchain_ = VK_NULL_HANDLE;
    }
    if (r != VK_SUCCESS) {
        fail("could not create the overlay swapchain");
        return false;
    }
    swapchain_ = fresh;
    uint32_t n = 0;
    t_.GetSwapchainImagesKHR(device_, swapchain_, &n, nullptr);
    swapImages_.resize(n);
    t_.GetSwapchainImagesKHR(device_, swapchain_, &n, swapImages_.data());
    swapRelease_.assign(n, VK_NULL_HANDLE);
    VkSemaphoreCreateInfo sci{};
    sci.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
    for (auto& s : swapRelease_) t_.CreateSemaphore(device_, &sci, nullptr, &s);
    QLOGI("swapchain %ux%u, %u images", width_, height_, n);
    return true;
}

void Pipeline::destroySwapchainImages() {
    for (auto s : swapRelease_)
        if (s) t_.DestroySemaphore(device_, s, nullptr);
    swapRelease_.clear();
    swapImages_.clear();
}

bool Pipeline::createImage(OwnedImage& img, uint32_t w, uint32_t h, VkImageUsageFlags usage) {
    VkImageCreateInfo ici{};
    ici.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    ici.imageType = VK_IMAGE_TYPE_2D;
    ici.format = kFormat;
    ici.extent = {w, h, 1};
    ici.mipLevels = 1;
    ici.arrayLayers = 1;
    ici.samples = VK_SAMPLE_COUNT_1_BIT;
    ici.tiling = VK_IMAGE_TILING_OPTIMAL;
    ici.usage = usage;
    ici.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    ici.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    if (t_.CreateImage(device_, &ici, nullptr, &img.image) != VK_SUCCESS) return false;
    VkMemoryRequirements req{};
    t_.GetImageMemoryRequirements(device_, img.image, &req);
    VkMemoryAllocateInfo mai{};
    mai.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    mai.allocationSize = req.size;
    mai.memoryTypeIndex = memoryType(req.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (mai.memoryTypeIndex == UINT32_MAX ||
        t_.AllocateMemory(device_, &mai, nullptr, &img.memory) != VK_SUCCESS)
        return false;
    t_.BindImageMemory(device_, img.image, img.memory, 0);
    VkImageViewCreateInfo vci{};
    vci.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    vci.image = img.image;
    vci.viewType = VK_IMAGE_VIEW_TYPE_2D;
    vci.format = kFormat;
    vci.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    if (t_.CreateImageView(device_, &vci, nullptr, &img.view) != VK_SUCCESS) return false;
    img.fresh = true;
    return true;
}

void Pipeline::destroyImage(OwnedImage& img) {
    if (img.view) t_.DestroyImageView(device_, img.view, nullptr);
    if (img.image) t_.DestroyImage(device_, img.image, nullptr);
    if (img.memory) t_.FreeMemory(device_, img.memory, nullptr);
    img = OwnedImage{};
}

bool Pipeline::createTargets() {
    const VkImageUsageFlags usage = VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_SAMPLED_BIT |
                                    VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
    if (!createImage(scene_, width_, height_, usage)) {
        fail("not enough GPU memory for the Scaler");
        return false;
    }
    for (uint32_t i = 0; i < lsfg::kMaxGenerations; i++) {
        if (!createImage(ring_[i], width_, height_, usage)) {
            fail("not enough GPU memory for the generated frames");
            return false;
        }
    }
    return true;
}

bool Pipeline::start(ANativeWindow* window, const char* cachePath, uint32_t width, uint32_t height,
                     uint32_t multiplier, float flowScale, float refreshHz) {
    window_ = window;
    width_ = width;
    height_ = height;
    refreshHz_ = refreshHz;
    multiplier_ = std::clamp<uint32_t>(multiplier, 1, lsfg::kMaxGenerations + 1);
    flowScale_ = std::clamp(flowScale, 0.25f, 1.0f);
    if (!createInstance() || !probeCaps() || !createDevice() || !createSwapchain() ||
        !createTargets())
        return false;
    if (cachePath && *cachePath) {
        if (!lsfgVkdInit(t_)) {
            fail("GPU driver is missing entry points LSFG needs");
            return false;
        }
        engine_ = std::make_unique<lsfg::Engine>();
        if (!engine_->init(device_, pd_, cachePath, caps_.features.spirvTarget)) {
            engine_.reset();
            fail("the shaders from Lossless.dll did not load on this GPU");
            return false;
        }
        engine_->configure(std::max<uint32_t>(multiplier_, 2), 0, flowScale_, refreshHz_);
        if (!engine_->prepare(width_, height_, kFormat)) {
            engine_.reset();
            fail("LSFG could not build its frame chain at this size");
            return false;
        }
    }
    QLOGI("started on %s, %ux%u, x%u, flow %.2f, %s", gpuName_, width_, height_, multiplier_,
          (double)flowScale_, engine_ ? "LSFG on" : "passthrough");
    return true;
}

void Pipeline::configure(uint32_t multiplier, float flowScale) {
    std::lock_guard<std::mutex> lock(configLock_);
    pendingMultiplier_ = std::clamp<uint32_t>(multiplier, 1, lsfg::kMaxGenerations + 1);
    pendingFlow_ = std::clamp(flowScale, 0.25f, 1.0f);
    configDirty_ = true;
}

Pipeline::Imported* Pipeline::importBuffer(AHardwareBuffer* buffer) {
    for (auto& entry : imported_)
        if (entry.buffer == buffer) return &entry;
    if (imported_.size() >= 8) {
        t_.DeviceWaitIdle(device_);
        releaseImported();
    }
    AHardwareBuffer_Desc desc{};
    AHardwareBuffer_describe(buffer, &desc);

    VkAndroidHardwareBufferFormatPropertiesANDROID formatProps{};
    formatProps.sType = VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_ANDROID;
    VkAndroidHardwareBufferPropertiesANDROID props{};
    props.sType = VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID;
    props.pNext = &formatProps;
    if (!t_.GetAndroidHardwareBufferPropertiesANDROID ||
        t_.GetAndroidHardwareBufferPropertiesANDROID(device_, buffer, &props) != VK_SUCCESS) {
        fail("the GPU could not read the captured frame");
        return nullptr;
    }
    if (formatProps.format == VK_FORMAT_UNDEFINED) {
        fail("the captured frame is not RGBA");
        return nullptr;
    }

    Imported entry{};
    entry.buffer = buffer;
    entry.width = desc.width;
    entry.height = desc.height;
    VkExternalMemoryImageCreateInfo ext{};
    ext.sType = VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO;
    ext.handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID;
    VkImageCreateInfo ici{};
    ici.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    ici.pNext = &ext;
    ici.imageType = VK_IMAGE_TYPE_2D;
    ici.format = formatProps.format;
    ici.extent = {desc.width, desc.height, 1};
    ici.mipLevels = 1;
    ici.arrayLayers = 1;
    ici.samples = VK_SAMPLE_COUNT_1_BIT;
    ici.tiling = VK_IMAGE_TILING_OPTIMAL;
    ici.usage = VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
    ici.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    ici.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    if (t_.CreateImage(device_, &ici, nullptr, &entry.image) != VK_SUCCESS) {
        fail("could not wrap the captured frame");
        return nullptr;
    }
    VkImportAndroidHardwareBufferInfoANDROID import{};
    import.sType = VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID;
    import.buffer = buffer;
    VkMemoryDedicatedAllocateInfo dedicated{};
    dedicated.sType = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO;
    dedicated.pNext = &import;
    dedicated.image = entry.image;
    VkMemoryAllocateInfo mai{};
    mai.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    mai.pNext = &dedicated;
    mai.allocationSize = props.allocationSize;
    mai.memoryTypeIndex = memoryType(props.memoryTypeBits, 0);
    if (mai.memoryTypeIndex == UINT32_MAX ||
        t_.AllocateMemory(device_, &mai, nullptr, &entry.memory) != VK_SUCCESS) {
        t_.DestroyImage(device_, entry.image, nullptr);
        fail("could not import the captured frame");
        return nullptr;
    }
    t_.BindImageMemory(device_, entry.image, entry.memory, 0);
    AHardwareBuffer_acquire(buffer);
    imported_.push_back(entry);
    return &imported_.back();
}

void Pipeline::releaseImported() {
    for (auto& e : imported_) {
        if (e.image) t_.DestroyImage(device_, e.image, nullptr);
        if (e.memory) t_.FreeMemory(device_, e.memory, nullptr);
        if (e.buffer) AHardwareBuffer_release(e.buffer);
    }
    imported_.clear();
}

bool Pipeline::beginSlot(uint32_t slot) {
    t_.WaitForFences(device_, 1, &slotFence_[slot], VK_TRUE, UINT64_MAX);
    t_.ResetFences(device_, 1, &slotFence_[slot]);
    t_.ResetCommandBuffer(slotCmd_[slot], 0);
    VkCommandBufferBeginInfo bi{};
    bi.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    bi.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    return t_.BeginCommandBuffer(slotCmd_[slot], &bi) == VK_SUCCESS;
}

int Pipeline::frame(AHardwareBuffer* buffer) {
    if (!device_) return -1;
    {
        std::lock_guard<std::mutex> lock(configLock_);
        if (configDirty_) {
            configDirty_ = false;
            multiplier_ = pendingMultiplier_;
            flowScale_ = pendingFlow_;
            if (engine_) engine_->configure(std::max<uint32_t>(multiplier_, 2), 0, flowScale_, refreshHz_);
        }
    }
    Imported* src = importBuffer(buffer);
    if (!src) return -1;

    const int64_t arrival = nowNs();
    if (lastArrivalNs_) {
        const double dt = (double)(arrival - lastArrivalNs_);
        sourceIntervalNs_ = sourceIntervalNs_ > 0 ? sourceIntervalNs_ * 0.85 + dt * 0.15 : dt;
    }
    lastArrivalNs_ = arrival;
    ++sourceFrames_;

    // Slot 0: copy the capture into the scene image, then LSFG's compute passes.
    if (!beginSlot(0)) return -1;
    VkCommandBuffer cmd = slotCmd_[0];
    const uint32_t foreign = foreignQueue_ ? VK_QUEUE_FAMILY_FOREIGN_EXT : VK_QUEUE_FAMILY_IGNORED;
    const uint32_t ours = foreignQueue_ ? qfam_ : VK_QUEUE_FAMILY_IGNORED;
    VkImageMemoryBarrier pre[2] = {
        barrier(src->image, 0, VK_ACCESS_TRANSFER_READ_BIT, VK_IMAGE_LAYOUT_UNDEFINED,
                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, foreign, ours),
        barrier(scene_.image, scene_.fresh ? 0 : VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_TRANSFER_READ_BIT,
                VK_ACCESS_TRANSFER_WRITE_BIT,
                scene_.fresh ? VK_IMAGE_LAYOUT_UNDEFINED : VK_IMAGE_LAYOUT_GENERAL,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL),
    };
    scene_.fresh = false;
    t_.CmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0,
                          nullptr, 0, nullptr, 2, pre);
    if (src->width == width_ && src->height == height_) {
        VkImageCopy region{};
        region.srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
        region.dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
        region.extent = {width_, height_, 1};
        t_.CmdCopyImage(cmd, src->image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, scene_.image,
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);
    } else {
        VkImageBlit blit{};
        blit.srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
        blit.dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
        blit.srcOffsets[1] = {(int32_t)src->width, (int32_t)src->height, 1};
        blit.dstOffsets[1] = {(int32_t)width_, (int32_t)height_, 1};
        t_.CmdBlitImage(cmd, src->image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, scene_.image,
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &blit,
                        caps_.linearBlitOnSwapchainFormat ? VK_FILTER_LINEAR : VK_FILTER_NEAREST);
    }
    VkImageMemoryBarrier post[2] = {
        barrier(src->image, VK_ACCESS_TRANSFER_READ_BIT, 0, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, ours, foreign),
        barrier(scene_.image, VK_ACCESS_TRANSFER_WRITE_BIT,
                VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_GENERAL),
    };
    t_.CmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT,
                          VK_PIPELINE_STAGE_TRANSFER_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0,
                          nullptr, 0, nullptr, 2, post);

    uint32_t generations = 0;
    if (engine_ && multiplier_ > 1 && !engine_->unavailable()) {
        engine_->setPresentedRate(presentedRate_.load());
        generations = engine_->plan(multiplier_ - 1, sourceFrames_);
        generations = std::min(generations, multiplier_ - 1);
        if (generations > 0) {
            VkImageMemoryBarrier rb[lsfg::kMaxGenerations];
            for (uint32_t g = 0; g < generations; g++) {
                rb[g] = barrier(ring_[g].image, ring_[g].fresh ? 0 : VK_ACCESS_TRANSFER_READ_BIT,
                                VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_SHADER_READ_BIT,
                                ring_[g].fresh ? VK_IMAGE_LAYOUT_UNDEFINED : VK_IMAGE_LAYOUT_GENERAL,
                                VK_IMAGE_LAYOUT_GENERAL);
                ring_[g].fresh = false;
            }
            t_.CmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                                  VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0, nullptr, 0, nullptr,
                                  generations, rb);
        }
        engine_->process(cmd, scene_.image, width_, height_, generations);
        for (uint32_t g = 0; g < generations; g++)
            engine_->generateInto(cmd, g, g, ring_[g].image, ring_[g].view, width_, height_);
        if (generations > 0) {
            VkImageMemoryBarrier rb[lsfg::kMaxGenerations];
            for (uint32_t g = 0; g < generations; g++)
                rb[g] = barrier(ring_[g].image, VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_TRANSFER_READ_BIT,
                                VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL);
            t_.CmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                                  VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr,
                                  generations, rb);
        }
    }
    t_.EndCommandBuffer(cmd);
    VkSubmitInfo si{};
    si.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    si.commandBufferCount = 1;
    si.pCommandBuffers = &cmd;
    if (t_.QueueSubmit(queue_, 1, &si, slotFence_[0]) != VK_SUCCESS) {
        fail("GPU submit failed");
        return -1;
    }
    // The capture buffer goes back to the ImageReader when this call returns, so the copy out of
    // it has to be finished first.
    t_.WaitForFences(device_, 1, &slotFence_[0], VK_TRUE, UINT64_MAX);

    if (!swapchain_) return (int)generations;

    // Generated frames sit between the previous real frame and this one, so they go first and the
    // real frame is shown one slot late, spread evenly over one source frame interval.
    const double interval = sourceIntervalNs_ > 0 ? sourceIntervalNs_ : 1e9 / 30.0;
    const int64_t step = (int64_t)(interval / (double)(generations + 1));
    int64_t due = nowNs();
    for (uint32_t o = 0; o <= generations; o++) {
        const VkImage from = o < generations ? ring_[o].image : scene_.image;
        if (o > 0) {
            due += step;
            const int64_t wait = due - nowNs();
            if (wait > 500000) std::this_thread::sleep_for(std::chrono::nanoseconds(wait));
        }
        if (!present(1 + (o % (kSlots - 1)), from)) return -1;
    }
    countPresented(generations);
    return (int)generations;
}

bool Pipeline::present(uint32_t slot, VkImage from) {
    t_.WaitForFences(device_, 1, &slotFence_[slot], VK_TRUE, UINT64_MAX);
    uint32_t index = 0;
    VkResult r = t_.AcquireNextImageKHR(device_, swapchain_, 200000000ull, slotAcquire_[slot],
                                        VK_NULL_HANDLE, &index);
    if (r == VK_ERROR_OUT_OF_DATE_KHR) {
        if (!createSwapchain()) return false;
        r = t_.AcquireNextImageKHR(device_, swapchain_, 200000000ull, slotAcquire_[slot],
                                   VK_NULL_HANDLE, &index);
    }
    if (r == VK_TIMEOUT || r == VK_NOT_READY) return true;
    if (r != VK_SUCCESS && r != VK_SUBOPTIMAL_KHR) {
        fail("the overlay stopped taking frames");
        return false;
    }
    if (!beginSlot(slot)) return false;
    VkCommandBuffer cmd = slotCmd_[slot];
    const VkImage target = swapImages_[index];
    VkImageMemoryBarrier pre = barrier(target, 0, VK_ACCESS_TRANSFER_WRITE_BIT, VK_IMAGE_LAYOUT_UNDEFINED,
                                       VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
    t_.CmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0,
                          nullptr, 0, nullptr, 1, &pre);
    VkImageCopy region{};
    region.srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    region.dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    region.extent = {width_, height_, 1};
    t_.CmdCopyImage(cmd, from, VK_IMAGE_LAYOUT_GENERAL, target, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1,
                    &region);
    VkImageMemoryBarrier post = barrier(target, VK_ACCESS_TRANSFER_WRITE_BIT, 0,
                                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
    t_.CmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0,
                          nullptr, 0, nullptr, 1, &post);
    t_.EndCommandBuffer(cmd);

    const VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
    VkSubmitInfo si{};
    si.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    si.waitSemaphoreCount = 1;
    si.pWaitSemaphores = &slotAcquire_[slot];
    si.pWaitDstStageMask = &waitStage;
    si.commandBufferCount = 1;
    si.pCommandBuffers = &cmd;
    si.signalSemaphoreCount = 1;
    si.pSignalSemaphores = &swapRelease_[index];
    if (t_.QueueSubmit(queue_, 1, &si, slotFence_[slot]) != VK_SUCCESS) {
        fail("GPU submit failed");
        return false;
    }
    VkPresentInfoKHR pi{};
    pi.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    pi.waitSemaphoreCount = 1;
    pi.pWaitSemaphores = &swapRelease_[index];
    pi.swapchainCount = 1;
    pi.pSwapchains = &swapchain_;
    pi.pImageIndices = &index;
    r = t_.QueuePresentKHR(queue_, &pi);
    if (r == VK_ERROR_OUT_OF_DATE_KHR) return createSwapchain();
    if (r != VK_SUCCESS && r != VK_SUBOPTIMAL_KHR) {
        fail("present failed");
        return false;
    }
    return true;
}

void Pipeline::countPresented(uint32_t generations) {
    const int64_t now = nowNs();
    if (!windowStartNs_) windowStartNs_ = now;
    windowPresented_ += generations + 1;
    windowSource_ += 1;
    totalGenerated_ += generations;
    const double elapsed = (double)(now - windowStartNs_) / 1e9;
    if (elapsed < 0.5) return;
    const float out = (float)(windowPresented_ / elapsed);
    const float in = (float)(windowSource_ / elapsed);
    const float p = presentedRate_.load(), s = sourceRate_.load();
    presentedRate_.store(p > 0 ? p + (out - p) * 0.3f : out);
    sourceRate_.store(s > 0 ? s + (in - s) * 0.3f : in);
    statMultiplier_.store(multiplier_);
    windowStartNs_ = now;
    windowPresented_ = 0;
    windowSource_ = 0;
}

void Pipeline::stats(float out[4]) const {
    out[0] = sourceRate_.load();
    out[1] = presentedRate_.load();
    out[2] = (float)statMultiplier_.load();
    out[3] = (float)totalGenerated_.load();
}

void Pipeline::destroy() {
    if (device_) t_.DeviceWaitIdle(device_);
    engine_.reset();
    if (device_) {
        releaseImported();
        destroyImage(scene_);
        for (auto& r : ring_) destroyImage(r);
        destroySwapchainImages();
        if (swapchain_) t_.DestroySwapchainKHR(device_, swapchain_, nullptr);
        swapchain_ = VK_NULL_HANDLE;
        for (uint32_t i = 0; i < kSlots; i++) {
            if (slotFence_[i]) t_.DestroyFence(device_, slotFence_[i], nullptr);
            if (slotAcquire_[i]) t_.DestroySemaphore(device_, slotAcquire_[i], nullptr);
            slotFence_[i] = VK_NULL_HANDLE;
            slotAcquire_[i] = VK_NULL_HANDLE;
        }
        if (pool_) t_.DestroyCommandPool(device_, pool_, nullptr);
        pool_ = VK_NULL_HANDLE;
        t_.DestroyDevice(device_, nullptr);
        device_ = VK_NULL_HANDLE;
    }
    if (instance_) {
        if (surface_) t_.DestroySurfaceKHR(instance_, surface_, nullptr);
        surface_ = VK_NULL_HANDLE;
        t_.DestroyInstance(instance_, nullptr);
        instance_ = VK_NULL_HANDLE;
    }
}

std::string probe() {
    Pipeline p;
    if (!p.createInstance()) return p.error();
    if (!p.probeCaps()) return p.error();
    return std::string();
}

std::string gpuName() {
    Pipeline p;
    if (!p.createInstance()) return std::string();
    return p.gpu();
}

}  // namespace qboost
