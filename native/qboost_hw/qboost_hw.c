/*
 * qboost_hw - real hardware probe for Qboost's Scaler.
 *
 * Reports what the phone actually has (CPU cores and clocks, RAM, and, when the driver allows it,
 * the Vulkan GPU with its compute/AHardwareBuffer/subgroup capabilities) as one small JSON string.
 * Qboost uses that to pick a tier and tune the built-in Scaler (capture scale, defaults), and to say
 * honestly whether the phone is even a candidate for heavier Vulkan frame generation.
 *
 * Plain C11, no STL and no third-party code. Vulkan is loaded with dlopen at run time, so this library
 * loads fine on phones without Vulkan and never links against libvulkan.
 *
 * Build (all four ABIs):  native/build-native.sh   (needs an Android NDK)
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <vulkan/vulkan.h>

#ifndef QBOOST_HOST_TEST
#include <jni.h>
#endif

#define MAX_CORES 32
#define MAX_DEVICES 8
#define MAX_QUEUE_FAMILIES 16

/* ------------------------------------------------------------------------------------------ */
/*  tiny bounded JSON writer                                                                    */
/* ------------------------------------------------------------------------------------------ */

typedef struct {
    char *buf;
    size_t cap;
    size_t len;
} sb_t;

static void sb_printf(sb_t *sb, const char *fmt, ...) {
    if (sb->len + 1 >= sb->cap) return;
    va_list ap;
    va_start(ap, fmt);
    int n = vsnprintf(sb->buf + sb->len, sb->cap - sb->len, fmt, ap);
    va_end(ap);
    if (n < 0) return;
    if ((size_t)n >= sb->cap - sb->len) {
        sb->len = sb->cap - 1; /* truncated: stop writing */
    } else {
        sb->len += (size_t)n;
    }
}

/* Always writes plain ASCII: anything else becomes '?', so the result is valid for NewStringUTF. */
static void sb_json_string(sb_t *sb, const char *s) {
    sb_printf(sb, "\"");
    for (; *s; ++s) {
        unsigned char c = (unsigned char)*s;
        if (c == '"' || c == '\\') {
            sb_printf(sb, "\\%c", c);
        } else if (c < 0x20 || c >= 0x7f) {
            sb_printf(sb, "?");
        } else {
            sb_printf(sb, "%c", c);
        }
    }
    sb_printf(sb, "\"");
}

/* ------------------------------------------------------------------------------------------ */
/*  CPU / RAM                                                                                   */
/* ------------------------------------------------------------------------------------------ */

static const char *abi_name(void) {
#if defined(__aarch64__)
    return "arm64-v8a";
#elif defined(__arm__)
    return "armeabi-v7a";
#elif defined(__x86_64__)
    return "x86_64";
#elif defined(__i386__)
    return "x86";
#else
    return "unknown";
#endif
}

static long read_long_file(const char *path) {
    FILE *f = fopen(path, "r");
    if (!f) return -1;
    long v = -1;
    if (fscanf(f, "%ld", &v) != 1) v = -1;
    fclose(f);
    return v;
}

typedef struct {
    int cores;
    int big;
    int little;
    long mhz[MAX_CORES]; /* -1 = unreadable */
    unsigned long long ramMb;
} cpu_info_t;

static void read_cpu(cpu_info_t *c) {
    memset(c, 0, sizeof *c);
    long n = sysconf(_SC_NPROCESSORS_CONF);
    if (n < 1) n = 1;
    c->cores = (int)n;

    long best = 0;
    int count = c->cores < MAX_CORES ? c->cores : MAX_CORES;
    for (int i = 0; i < count; i++) {
        char path[96];
        snprintf(path, sizeof path, "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", i);
        long khz = read_long_file(path);
        c->mhz[i] = khz > 0 ? khz / 1000 : -1;
        if (c->mhz[i] > best) best = c->mhz[i];
    }
    if (best > 0) {
        /* "big" = within 25% of the fastest core; everything else is "little". */
        for (int i = 0; i < count; i++) {
            if (c->mhz[i] < 0) continue;
            if (c->mhz[i] * 4 >= best * 3) c->big++; else c->little++;
        }
    }

    long pages = sysconf(_SC_PHYS_PAGES);
    long pageSize = sysconf(_SC_PAGESIZE);
    if (pages > 0 && pageSize > 0) {
        c->ramMb = ((unsigned long long)pages * (unsigned long long)pageSize) / (1024ULL * 1024ULL);
    }
}

/* ------------------------------------------------------------------------------------------ */
/*  Vulkan                                                                                      */
/* ------------------------------------------------------------------------------------------ */

typedef struct {
    int ok;
    uint32_t instanceApi;
    uint32_t api;
    char name[VK_MAX_PHYSICAL_DEVICE_NAME_SIZE];
    uint32_t vendorId;
    uint32_t deviceId;
    uint32_t driverVersion;
    int deviceType;
    int compute;
    int ahb;      /* VK_ANDROID_external_memory_android_hardware_buffer */
    int fd;       /* VK_KHR_external_memory_fd */
    int fp16;     /* VK_KHR_shader_float16_int8 or VK_KHR_16bit_storage */
    uint32_t subgroup;
    uint32_t maxInvocations;
    uint32_t sharedBytes;
    char err[80];
} vk_info_t;

static int device_score(int type) {
    switch (type) {
        case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU: return 4;
        case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU: return 3;
        case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU: return 2;
        case VK_PHYSICAL_DEVICE_TYPE_OTHER: return 1;
        default: return 0; /* CPU (software) renderer */
    }
}

static void set_err(vk_info_t *o, const char *msg) {
    snprintf(o->err, sizeof o->err, "%s", msg);
}

static void probe_vulkan(vk_info_t *o) {
    memset(o, 0, sizeof *o);

    void *lib = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!lib) {
        set_err(o, "libvulkan.so not available");
        return;
    }
    PFN_vkGetInstanceProcAddr gipa = (PFN_vkGetInstanceProcAddr)dlsym(lib, "vkGetInstanceProcAddr");
    if (!gipa) {
        set_err(o, "vkGetInstanceProcAddr missing");
        return;
    }

    uint32_t instApi = VK_API_VERSION_1_0;
    PFN_vkEnumerateInstanceVersion enumVer =
        (PFN_vkEnumerateInstanceVersion)gipa(NULL, "vkEnumerateInstanceVersion");
    if (enumVer) enumVer(&instApi);
    o->instanceApi = instApi;

    VkApplicationInfo app;
    memset(&app, 0, sizeof app);
    app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.pApplicationName = "qboost-hw-probe";
    app.applicationVersion = 1;
    app.pEngineName = "qboost";
    app.engineVersion = 1;
    app.apiVersion = instApi >= VK_API_VERSION_1_1 ? VK_API_VERSION_1_1 : VK_API_VERSION_1_0;

    VkInstanceCreateInfo ici;
    memset(&ici, 0, sizeof ici);
    ici.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    ici.pApplicationInfo = &app;

    PFN_vkCreateInstance createInstance = (PFN_vkCreateInstance)gipa(NULL, "vkCreateInstance");
    if (!createInstance) {
        set_err(o, "vkCreateInstance missing");
        return;
    }
    VkInstance inst = NULL;
    if (createInstance(&ici, NULL, &inst) != VK_SUCCESS || !inst) {
        set_err(o, "vkCreateInstance failed");
        return;
    }

    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gipa(inst, "vkDestroyInstance");
    PFN_vkEnumeratePhysicalDevices enumDevices =
        (PFN_vkEnumeratePhysicalDevices)gipa(inst, "vkEnumeratePhysicalDevices");
    PFN_vkGetPhysicalDeviceProperties getProps =
        (PFN_vkGetPhysicalDeviceProperties)gipa(inst, "vkGetPhysicalDeviceProperties");
    PFN_vkGetPhysicalDeviceQueueFamilyProperties getQueues =
        (PFN_vkGetPhysicalDeviceQueueFamilyProperties)gipa(inst, "vkGetPhysicalDeviceQueueFamilyProperties");
    PFN_vkEnumerateDeviceExtensionProperties enumExt =
        (PFN_vkEnumerateDeviceExtensionProperties)gipa(inst, "vkEnumerateDeviceExtensionProperties");
    PFN_vkGetPhysicalDeviceProperties2 getProps2 = NULL;
    if (instApi >= VK_API_VERSION_1_1) {
        getProps2 = (PFN_vkGetPhysicalDeviceProperties2)gipa(inst, "vkGetPhysicalDeviceProperties2");
    }

    if (!enumDevices || !getProps || !getQueues) {
        set_err(o, "Vulkan entry points missing");
        if (destroyInstance) destroyInstance(inst, NULL);
        return;
    }

    uint32_t count = 0;
    if (enumDevices(inst, &count, NULL) != VK_SUCCESS || count == 0) {
        set_err(o, "no Vulkan device");
        if (destroyInstance) destroyInstance(inst, NULL);
        return;
    }
    if (count > MAX_DEVICES) count = MAX_DEVICES;
    VkPhysicalDevice devices[MAX_DEVICES];
    if (enumDevices(inst, &count, devices) < 0) { /* VK_INCOMPLETE (>0) is fine */
        set_err(o, "device enumeration failed");
        if (destroyInstance) destroyInstance(inst, NULL);
        return;
    }

    int bestScore = -1;
    for (uint32_t i = 0; i < count; i++) {
        VkPhysicalDeviceProperties props;
        memset(&props, 0, sizeof props);
        getProps(devices[i], &props);

        uint32_t qn = 0;
        getQueues(devices[i], &qn, NULL);
        if (qn > MAX_QUEUE_FAMILIES) qn = MAX_QUEUE_FAMILIES;
        VkQueueFamilyProperties qf[MAX_QUEUE_FAMILIES];
        getQueues(devices[i], &qn, qf);
        int compute = 0;
        for (uint32_t q = 0; q < qn; q++) {
            if (qf[q].queueFlags & VK_QUEUE_COMPUTE_BIT) compute = 1;
        }

        int score = device_score((int)props.deviceType) * 2 + (compute ? 1 : 0);
        if (score <= bestScore) continue;
        bestScore = score;

        o->ok = 1;
        o->api = props.apiVersion;
        snprintf(o->name, sizeof o->name, "%s", props.deviceName);
        o->vendorId = props.vendorID;
        o->deviceId = props.deviceID;
        o->driverVersion = props.driverVersion;
        o->deviceType = (int)props.deviceType;
        o->compute = compute;
        o->maxInvocations = props.limits.maxComputeWorkGroupInvocations;
        o->sharedBytes = props.limits.maxComputeSharedMemorySize;
        o->ahb = o->fd = o->fp16 = 0;
        o->subgroup = 0;

        if (enumExt) {
            uint32_t en = 0;
            if (enumExt(devices[i], NULL, &en, NULL) == VK_SUCCESS && en > 0) {
                VkExtensionProperties *ext = (VkExtensionProperties *)calloc(en, sizeof *ext);
                if (ext) {
                    if (enumExt(devices[i], NULL, &en, ext) >= 0) {
                        for (uint32_t e = 0; e < en; e++) {
                            const char *nm = ext[e].extensionName;
                            if (!strcmp(nm, "VK_ANDROID_external_memory_android_hardware_buffer")) o->ahb = 1;
                            else if (!strcmp(nm, "VK_KHR_external_memory_fd")) o->fd = 1;
                            else if (!strcmp(nm, "VK_KHR_shader_float16_int8") ||
                                     !strcmp(nm, "VK_KHR_16bit_storage")) o->fp16 = 1;
                        }
                    }
                    free(ext);
                }
            }
        }

        if (getProps2 && props.apiVersion >= VK_API_VERSION_1_1) {
            VkPhysicalDeviceSubgroupProperties sg;
            memset(&sg, 0, sizeof sg);
            sg.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SUBGROUP_PROPERTIES;
            VkPhysicalDeviceProperties2 p2;
            memset(&p2, 0, sizeof p2);
            p2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2;
            p2.pNext = &sg;
            getProps2(devices[i], &p2);
            o->subgroup = sg.subgroupSize;
        }
    }

    if (!o->ok) set_err(o, "no usable Vulkan device");
    if (destroyInstance) destroyInstance(inst, NULL);
    /* libvulkan is intentionally not dlclose()d: some drivers leave worker threads behind. */
}

/* ------------------------------------------------------------------------------------------ */
/*  JSON                                                                                        */
/* ------------------------------------------------------------------------------------------ */

static void fmt_version(char *out, size_t n, uint32_t v) {
    snprintf(out, n, "%u.%u.%u", (unsigned)((v >> 22) & 0x7F), (unsigned)((v >> 12) & 0x3FF),
             (unsigned)(v & 0xFFF));
}

static size_t build_json(char *out, size_t cap, int withVulkan) {
    sb_t sb = {out, cap, 0};
    if (cap) out[0] = '\0';

    cpu_info_t cpu;
    read_cpu(&cpu);

    sb_printf(&sb, "{\"v\":1,\"abi\":");
    sb_json_string(&sb, abi_name());
    sb_printf(&sb, ",\"cores\":%d,\"big\":%d,\"little\":%d,\"ramMb\":%llu,\"maxMhz\":[", cpu.cores, cpu.big,
              cpu.little, cpu.ramMb);
    int shown = cpu.cores < MAX_CORES ? cpu.cores : MAX_CORES;
    for (int i = 0; i < shown; i++) sb_printf(&sb, "%s%ld", i ? "," : "", cpu.mhz[i]);
    sb_printf(&sb, "],\"vulkanTried\":%s", withVulkan ? "true" : "false");

    if (withVulkan) {
        vk_info_t vk;
        probe_vulkan(&vk);
        char api[24], instApi[24];
        fmt_version(api, sizeof api, vk.api);
        fmt_version(instApi, sizeof instApi, vk.instanceApi);
        sb_printf(&sb, ",\"vk\":{\"ok\":%s,\"api\":\"%s\",\"instanceApi\":\"%s\",\"gpu\":", vk.ok ? "true" : "false",
                  api, instApi);
        sb_json_string(&sb, vk.name);
        sb_printf(&sb,
                  ",\"vendor\":%u,\"device\":%u,\"driver\":%u,\"type\":%d,\"compute\":%s,\"ahb\":%s,\"fd\":%s,"
                  "\"fp16\":%s,\"subgroup\":%u,\"maxInvocations\":%u,\"sharedKb\":%u,\"err\":",
                  (unsigned)vk.vendorId, (unsigned)vk.deviceId, (unsigned)vk.driverVersion, vk.deviceType,
                  vk.compute ? "true" : "false", vk.ahb ? "true" : "false", vk.fd ? "true" : "false",
                  vk.fp16 ? "true" : "false", (unsigned)vk.subgroup, (unsigned)vk.maxInvocations,
                  (unsigned)(vk.sharedBytes / 1024u));
        sb_json_string(&sb, vk.err);
        sb_printf(&sb, "}");
    }
    sb_printf(&sb, "}");
    return sb.len;
}

/* ------------------------------------------------------------------------------------------ */
/*  entry points                                                                                */
/* ------------------------------------------------------------------------------------------ */

#ifndef QBOOST_HOST_TEST
JNIEXPORT jstring JNICALL Java_com_example_hardware_HardwareProbe_nativeProbe(JNIEnv *env, jclass clazz,
                                                                            jboolean withVulkan) {
    (void)clazz;
    char buf[4096];
    build_json(buf, sizeof buf, withVulkan == JNI_TRUE);
    return (*env)->NewStringUTF(env, buf);
}
#else
int main(int argc, char **argv) {
    char buf[4096];
    build_json(buf, sizeof buf, argc < 2 || strcmp(argv[1], "--no-vulkan") != 0);
    puts(buf);
    return 0;
}
#endif
