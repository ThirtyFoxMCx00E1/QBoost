// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#ifndef VK_USE_PLATFORM_ANDROID_KHR
#define VK_USE_PLATFORM_ANDROID_KHR
#endif
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>

#include <cstdint>
#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "VulkanRendererContext.h"
#include "lsfg/lsfg_engine.h"
#include "lsfg/lsfg_probe.h"

struct ANativeWindow;
struct AHardwareBuffer;

namespace qboost {

/** One Scaler session: capture buffers in, LSFG frames out on the overlay window. */
class Pipeline {
public:
    Pipeline();
    ~Pipeline();
    Pipeline(const Pipeline&) = delete;
    Pipeline& operator=(const Pipeline&) = delete;

    /** cachePath empty = no frame generation, the capture is only shown (scaled by the display). */
    bool start(ANativeWindow* window, const char* cachePath, uint32_t width, uint32_t height,
               uint32_t multiplier, float flowScale, float refreshHz);
    /** Processes and presents one captured frame. Returns the number of generated frames, or -1. */
    int frame(AHardwareBuffer* buffer);
    void configure(uint32_t multiplier, float flowScale);
    /** source fps, presented fps, multiplier, total generated frames */
    void stats(float out[4]) const;

    const char* error() const { return error_; }
    const char* gpu() const { return gpuName_; }

    bool createInstance();
    bool probeCaps();

private:
    static constexpr uint32_t kSlots = 5;

    struct OwnedImage {
        VkImage image = VK_NULL_HANDLE;
        VkDeviceMemory memory = VK_NULL_HANDLE;
        VkImageView view = VK_NULL_HANDLE;
        bool fresh = true;
    };
    struct Imported {
        AHardwareBuffer* buffer = nullptr;
        VkImage image = VK_NULL_HANDLE;
        VkDeviceMemory memory = VK_NULL_HANDLE;
        uint32_t width = 0, height = 0;
    };

    void fail(const char* what);
    uint32_t memoryType(uint32_t bits, VkMemoryPropertyFlags want) const;
    bool createDevice();
    bool createSwapchain();
    void destroySwapchainImages();
    bool createImage(OwnedImage& img, uint32_t w, uint32_t h, VkImageUsageFlags usage);
    void destroyImage(OwnedImage& img);
    bool createTargets();
    Imported* importBuffer(AHardwareBuffer* buffer);
    void releaseImported();
    bool beginSlot(uint32_t slot);
    bool present(uint32_t slot, VkImage from);
    void countPresented(uint32_t generations);
    void destroy();

    VkTable t_{};
    VkInstance instance_ = VK_NULL_HANDLE;
    VkPhysicalDevice pd_ = VK_NULL_HANDLE;
    VkDevice device_ = VK_NULL_HANDLE;
    VkQueue queue_ = VK_NULL_HANDLE;
    uint32_t qfam_ = 0;
    bool foreignQueue_ = false;
    VkPhysicalDeviceMemoryProperties memProps_{};
    std::vector<VkExtensionProperties> deviceExts_;
    lsfg::Caps caps_{};

    ANativeWindow* window_ = nullptr;
    VkSurfaceKHR surface_ = VK_NULL_HANDLE;
    VkSwapchainKHR swapchain_ = VK_NULL_HANDLE;
    std::vector<VkImage> swapImages_;
    std::vector<VkSemaphore> swapRelease_;

    VkCommandPool pool_ = VK_NULL_HANDLE;
    VkCommandBuffer slotCmd_[kSlots]{};
    VkFence slotFence_[kSlots]{};
    VkSemaphore slotAcquire_[kSlots]{};

    OwnedImage scene_;
    OwnedImage ring_[lsfg::kMaxGenerations];
    std::vector<Imported> imported_;

    std::unique_ptr<lsfg::Engine> engine_;
    uint32_t width_ = 0, height_ = 0;
    uint32_t multiplier_ = 2;
    float flowScale_ = 0.8f;
    float refreshHz_ = 60.0f;

    std::mutex configLock_;
    bool configDirty_ = false;
    uint32_t pendingMultiplier_ = 2;
    float pendingFlow_ = 0.8f;

    uint64_t sourceFrames_ = 0;
    int64_t lastArrivalNs_ = 0;
    double sourceIntervalNs_ = 0;
    int64_t windowStartNs_ = 0;
    uint32_t windowPresented_ = 0, windowSource_ = 0;
    std::atomic<uint64_t> totalGenerated_{0};
    std::atomic<float> presentedRate_{0}, sourceRate_{0};
    std::atomic<uint32_t> statMultiplier_{2};

    char error_[200] = "";
    char gpuName_[128] = "";
};

/** Empty when this phone can run LSFG, otherwise the reason it cannot. */
std::string probe();
std::string gpuName();

}  // namespace qboost
