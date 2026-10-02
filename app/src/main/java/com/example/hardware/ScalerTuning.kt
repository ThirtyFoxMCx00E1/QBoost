package com.example.hardware

import java.util.Locale

/** How much GPU / RAM headroom the phone has. Drives the built-in Scaler's defaults. */
enum class HardwareTier { LOW, MID, HIGH }

/** What the Scaler does. Stored as an Int in settings. */
object ScalerMode {
    const val UPSCALE = 0
    const val FRAME_GEN = 1
    const val BOTH = 2

    fun upscale(mode: Int): Boolean = mode == UPSCALE || mode == BOTH
    fun frameGen(mode: Int): Boolean = mode == FRAME_GEN || mode == BOTH
}

/** Capture-quality choices. Stored as an Int in settings. */
object ScalerQuality {
    const val AUTO = 0
    const val MAX = 1
    const val BALANCED = 2
    const val FAST = 3
}

/**
 * Pure logic (no Android classes) that turns the hardware facts reported by the native probe into a tier and
 * into the capture scale the built-in Scaler uses.
 */
object ScalerTuning {

    private val ADRENO = Regex("adreno[^0-9]*(\\d{3})", RegexOption.IGNORE_CASE)
    private val MALI = Regex("mali[- ]?g(\\d+)", RegexOption.IGNORE_CASE)
    private val IMMORTALIS = Regex("immortalis[- ]?g(\\d+)", RegexOption.IGNORE_CASE)

    /** GPU-only tier, or null when the GPU is not recognised (RAM then decides, capped at MID). */
    fun gpuTier(gpuName: String): HardwareTier? {
        val name = gpuName.trim()
        if (name.isEmpty()) return null
        val lower = name.lowercase(Locale.ROOT)
        if (lower.contains("llvmpipe") || lower.contains("swiftshader")) return HardwareTier.LOW
        if (lower.contains("xclipse") || IMMORTALIS.containsMatchIn(name)) return HardwareTier.HIGH

        ADRENO.find(name)?.let { m ->
            val n = m.groupValues[1].toInt()
            return when {
                n >= 700 -> HardwareTier.HIGH
                n >= 640 -> HardwareTier.MID
                else -> HardwareTier.LOW
            }
        }
        MALI.find(name)?.let { m ->
            val g = m.groupValues[1].toInt()
            return when {
                g >= 700 -> HardwareTier.HIGH
                g >= 600 || g == 57 || g == 68 || g == 76 || g == 77 || g == 78 -> HardwareTier.MID
                else -> HardwareTier.LOW
            }
        }
        return null
    }

    /** Total-RAM tier. Phones report a bit less than the number on the box (8 GB shows as about 7.4 GB). */
    fun ramTier(ramMb: Long): HardwareTier = when {
        ramMb >= 7000 -> HardwareTier.HIGH
        ramMb >= 5000 -> HardwareTier.MID
        else -> HardwareTier.LOW
    }

    fun classify(ramMb: Long, gpuName: String, vulkanOk: Boolean): HardwareTier {
        val ram = ramTier(ramMb)
        val gpu = if (vulkanOk) gpuTier(gpuName) else null
        // An unknown (or unreadable) GPU can't be called high-end, so RAM alone tops out at MID.
        val capped = if (gpu == null) lower(ram, HardwareTier.MID) else lower(ram, gpu)
        return capped
    }

    private fun lower(a: HardwareTier, b: HardwareTier): HardwareTier = if (a.ordinal <= b.ordinal) a else b

    /** Fraction of the screen resolution the Scaler captures at; the result is scaled back up to full size. */
    fun scaleFor(quality: Int, tier: HardwareTier): Float = when (quality) {
        ScalerQuality.MAX -> 1.0f
        ScalerQuality.BALANCED -> 0.75f
        ScalerQuality.FAST -> 0.6f
        else -> when (tier) { // AUTO
            HardwareTier.HIGH -> 1.0f
            HardwareTier.MID -> 0.75f
            HardwareTier.LOW -> 0.6f
        }
    }

    /** [px] * [scale], rounded to an even number (video surfaces prefer even sizes), never below 2. */
    fun scaledSize(px: Int, scale: Float): Int {
        val v = Math.round(px * scale.toDouble()).toInt()
        return maxOf(2, v - (v % 2))
    }

    /**
     * Whether the phone could be a candidate for a heavy Vulkan compute frame generator (LSFG-style):
     * Vulkan 1.1+, a compute queue, AHardwareBuffer import, a 64-bit ABI, a high-tier GPU, Android 10+.
     */
    fun heavyVulkanCandidate(
        abi: String,
        vulkanMajor: Int,
        vulkanMinor: Int,
        computeQueue: Boolean,
        ahbImport: Boolean,
        tier: HardwareTier,
        sdkInt: Int
    ): Boolean {
        val vulkan11 = vulkanMajor > 1 || (vulkanMajor == 1 && vulkanMinor >= 1)
        return vulkan11 && computeQueue && ahbImport && tier == HardwareTier.HIGH &&
            (abi == "arm64-v8a" || abi == "x86_64") && sdkInt >= 29
    }
}
