package com.example.hardware

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.util.Locale

/** What this phone actually has, as reported by the native probe (or a RAM-only estimate without it). */
data class HardwareProfile(
    val abi: String,
    val cores: Int,
    val bigCores: Int,
    val ramMb: Long,
    val nativeLoaded: Boolean,
    val vulkanOk: Boolean,
    val vulkanMajor: Int,
    val vulkanMinor: Int,
    val gpuName: String,
    val computeQueue: Boolean,
    val ahbImport: Boolean,
    val subgroupSize: Int,
    val tier: HardwareTier
) {
    /** Could this phone run a heavy Vulkan frame generator? (Qboost's built-in Scaler does not need it.) */
    val heavyVulkanCandidate: Boolean
        get() = ScalerTuning.heavyVulkanCandidate(
            abi, vulkanMajor, vulkanMinor, computeQueue, ahbImport, tier, Build.VERSION.SDK_INT
        )

    /** One line for the Scaler card, for example "Adreno 740 · Vulkan 1.3 · 8 cores · 11.2 GB · High". */
    fun summary(): String {
        val gpu = if (gpuName.isNotBlank()) {
            gpuName.replace("(TM)", "").replace(Regex("\\s+"), " ").trim()
        } else {
            "GPU unknown"
        }
        val vulkan = if (vulkanOk) "Vulkan $vulkanMajor.$vulkanMinor" else "no Vulkan"
        val ram = String.format(Locale.US, "%.1f GB", ramMb / 1024.0)
        val tierName = tier.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }
        return "$gpu · $vulkan · $cores cores · $ram · $tierName"
    }
}

/**
 * Reads the real hardware through `libqboost_hw.so` (plain C, built with the Android NDK for arm64-v8a,
 * armeabi-v7a, x86 and x86_64; see native/build-native.sh). The app never depends on that library: if it is
 * missing the profile falls back to a RAM-only estimate.
 *
 * Crash guard: asking a Vulkan driver questions is safe on virtually every phone, but a broken driver could
 * take the process down. The "probe pending" flag is written before the native call and cleared after it; if
 * a later start finds it still set, the previous probe died and Vulkan probing is turned off for good.
 */
object HardwareProbe {

    private const val PREFS = "qboost_settings"
    private const val KEY_PROBE_PENDING = "hw_probe_pending"
    private const val KEY_VULKAN_DISABLED = "hw_probe_vulkan_disabled"

    private val nativeLoaded: Boolean = try {
        System.loadLibrary("qboost_hw")
        true
    } catch (_: Throwable) {
        false
    }

    @Volatile
    private var cached: HardwareProfile? = null

    /** Called by the JVM: matches Java_com_example_hardware_HardwareProbe_nativeProbe in qboost_hw.c. */
    @JvmStatic
    external fun nativeProbe(withVulkan: Boolean): String

    /** The full probe. May take a moment and touches the GPU driver: call it from a background thread. */
    fun get(context: Context): HardwareProfile {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val profile = probe(context.applicationContext)
            cached = profile
            return profile
        }
    }

    /** Never blocks: the full profile if it is already known, otherwise a quick RAM-only estimate. */
    fun peek(context: Context): HardwareProfile = cached ?: fallback(context.applicationContext)

    private fun probe(context: Context): HardwareProfile {
        if (!nativeLoaded) return fallback(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        var vulkanAllowed = !prefs.getBoolean(KEY_VULKAN_DISABLED, false)
        if (prefs.getBoolean(KEY_PROBE_PENDING, false)) {
            // The last probe never finished: the driver crashed the process. Stay away from it from now on.
            prefs.edit().putBoolean(KEY_VULKAN_DISABLED, true).putBoolean(KEY_PROBE_PENDING, false).commit()
            vulkanAllowed = false
        }

        var json: String? = null
        try {
            if (vulkanAllowed) prefs.edit().putBoolean(KEY_PROBE_PENDING, true).commit()
            json = nativeProbe(vulkanAllowed)
        } catch (_: Throwable) {
            json = null
        } finally {
            if (vulkanAllowed) prefs.edit().putBoolean(KEY_PROBE_PENDING, false).commit()
        }
        return if (json == null) fallback(context) else parse(context, json)
    }

    private fun parse(context: Context, json: String): HardwareProfile {
        return try {
            val o = JSONObject(json)
            val vk = o.optJSONObject("vk")
            val vulkanOk = vk?.optBoolean("ok", false) == true
            val api = vk?.optString("api", "")?.split('.') ?: emptyList()
            val gpu = if (vulkanOk) vk?.optString("gpu", "").orEmpty() else ""
            val ramMb = o.optLong("ramMb", 0L).takeIf { it > 0L } ?: totalRamMb(context)
            HardwareProfile(
                abi = o.optString("abi", "unknown"),
                cores = o.optInt("cores", 1).coerceAtLeast(1),
                bigCores = o.optInt("big", 0),
                ramMb = ramMb,
                nativeLoaded = true,
                vulkanOk = vulkanOk,
                vulkanMajor = api.getOrNull(0)?.toIntOrNull() ?: 0,
                vulkanMinor = api.getOrNull(1)?.toIntOrNull() ?: 0,
                gpuName = gpu,
                computeQueue = vk?.optBoolean("compute", false) == true,
                ahbImport = vk?.optBoolean("ahb", false) == true,
                subgroupSize = vk?.optInt("subgroup", 0) ?: 0,
                tier = ScalerTuning.classify(ramMb, gpu, vulkanOk)
            )
        } catch (_: Exception) {
            fallback(context)
        }
    }

    private fun fallback(context: Context): HardwareProfile {
        val ramMb = totalRamMb(context)
        return HardwareProfile(
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            bigCores = 0,
            ramMb = ramMb,
            nativeLoaded = nativeLoaded,
            vulkanOk = false,
            vulkanMajor = 0,
            vulkanMinor = 0,
            gpuName = "",
            computeQueue = false,
            ahbImport = false,
            subgroupSize = 0,
            tier = ScalerTuning.classify(ramMb, "", false)
        )
    }

    private fun totalRamMb(context: Context): Long {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.totalMem / (1024L * 1024L)
        } catch (_: Exception) {
            0L
        }
    }
}
