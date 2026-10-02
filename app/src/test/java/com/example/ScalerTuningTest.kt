package com.example

import com.example.hardware.HardwareProfile
import com.example.hardware.HardwareTier
import com.example.hardware.ScalerMode
import com.example.hardware.ScalerQuality
import com.example.hardware.ScalerTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScalerTuningTest {

    @Test
    fun gpuTiersFromRealDeviceNames() {
        assertEquals(HardwareTier.HIGH, ScalerTuning.gpuTier("Adreno (TM) 740"))
        assertEquals(HardwareTier.HIGH, ScalerTuning.gpuTier("Adreno (TM) 830"))
        assertEquals(HardwareTier.MID, ScalerTuning.gpuTier("Adreno (TM) 650"))
        assertEquals(HardwareTier.LOW, ScalerTuning.gpuTier("Adreno (TM) 619"))
        assertEquals(HardwareTier.HIGH, ScalerTuning.gpuTier("Mali-G710 MC10"))
        assertEquals(HardwareTier.MID, ScalerTuning.gpuTier("Mali-G610 MC4"))
        assertEquals(HardwareTier.MID, ScalerTuning.gpuTier("Mali-G77 MC9"))
        assertEquals(HardwareTier.LOW, ScalerTuning.gpuTier("Mali-G52 MC2"))
        assertEquals(HardwareTier.HIGH, ScalerTuning.gpuTier("Immortalis-G720 MC12"))
        assertEquals(HardwareTier.HIGH, ScalerTuning.gpuTier("Samsung Xclipse 940"))
        assertEquals(HardwareTier.LOW, ScalerTuning.gpuTier("llvmpipe (LLVM 15.0.7, 128 bits)"))
        assertNull(ScalerTuning.gpuTier("PowerVR GE8320"))
        assertNull(ScalerTuning.gpuTier(""))
    }

    @Test
    fun tierIsTheWeakerOfRamAndGpu() {
        assertEquals(HardwareTier.HIGH, ScalerTuning.classify(11_000, "Adreno (TM) 740", true))
        assertEquals(HardwareTier.LOW, ScalerTuning.classify(3_700, "Adreno (TM) 619", true))
        assertEquals(HardwareTier.MID, ScalerTuning.classify(12_000, "Adreno (TM) 650", true))
        assertEquals(HardwareTier.LOW, ScalerTuning.classify(8_000, "Mali-G52 MC2", true))
        assertEquals(HardwareTier.MID, ScalerTuning.classify(6_000, "Mali-G610 MC4", true))
        assertEquals(HardwareTier.LOW, ScalerTuning.classify(4_000, "Adreno (TM) 740", true))
    }

    @Test
    fun anUnknownGpuIsNeverCalledHighEnd() {
        assertEquals(HardwareTier.MID, ScalerTuning.classify(12_000, "", false))
        // Without a working Vulkan driver the GPU name is not trusted either
        assertEquals(HardwareTier.MID, ScalerTuning.classify(12_000, "Adreno (TM) 740", false))
        assertEquals(HardwareTier.LOW, ScalerTuning.classify(3_700, "", false))
    }

    @Test
    fun captureScaleFollowsQualityAndTier() {
        assertEquals(1.0f, ScalerTuning.scaleFor(ScalerQuality.AUTO, HardwareTier.HIGH), 0.001f)
        assertEquals(0.75f, ScalerTuning.scaleFor(ScalerQuality.AUTO, HardwareTier.MID), 0.001f)
        assertEquals(0.6f, ScalerTuning.scaleFor(ScalerQuality.AUTO, HardwareTier.LOW), 0.001f)
        assertEquals(1.0f, ScalerTuning.scaleFor(ScalerQuality.MAX, HardwareTier.LOW), 0.001f)
        assertEquals(0.75f, ScalerTuning.scaleFor(ScalerQuality.BALANCED, HardwareTier.HIGH), 0.001f)
        assertEquals(0.6f, ScalerTuning.scaleFor(ScalerQuality.FAST, HardwareTier.HIGH), 0.001f)
    }

    @Test
    fun scaledSizesAreEvenAndNeverTiny() {
        assertEquals(1200, ScalerTuning.scaledSize(1600, 0.75f))
        assertEquals(540, ScalerTuning.scaledSize(720, 0.75f))
        assertEquals(1440, ScalerTuning.scaledSize(2400, 0.6f))
        assertEquals(720, ScalerTuning.scaledSize(721, 1.0f))
        assertEquals(2, ScalerTuning.scaledSize(1, 0.1f))
    }

    @Test
    fun heavyVulkanCandidateNeedsEverything() {
        val high = HardwareTier.HIGH
        assertTrue(ScalerTuning.heavyVulkanCandidate("arm64-v8a", 1, 3, true, true, high, 34))
        assertTrue(ScalerTuning.heavyVulkanCandidate("x86_64", 1, 1, true, true, high, 29))
        assertFalse(ScalerTuning.heavyVulkanCandidate("armeabi-v7a", 1, 3, true, true, high, 34))
        assertFalse(ScalerTuning.heavyVulkanCandidate("arm64-v8a", 1, 3, true, true, HardwareTier.MID, 34))
        assertFalse(ScalerTuning.heavyVulkanCandidate("arm64-v8a", 1, 3, true, false, high, 34))
        assertFalse(ScalerTuning.heavyVulkanCandidate("arm64-v8a", 1, 0, true, true, high, 34))
        assertFalse(ScalerTuning.heavyVulkanCandidate("arm64-v8a", 1, 3, true, true, high, 28))
    }

    @Test
    fun modesMapToTheTwoEngineFeatures() {
        assertTrue(ScalerMode.upscale(ScalerMode.UPSCALE) && !ScalerMode.frameGen(ScalerMode.UPSCALE))
        assertTrue(!ScalerMode.upscale(ScalerMode.FRAME_GEN) && ScalerMode.frameGen(ScalerMode.FRAME_GEN))
        assertTrue(ScalerMode.upscale(ScalerMode.BOTH) && ScalerMode.frameGen(ScalerMode.BOTH))
    }

    @Test
    fun summaryLineReadsLikeAHardwareLabel() {
        val strong = HardwareProfile(
            abi = "arm64-v8a", cores = 8, bigCores = 4, ramMb = 11264, nativeLoaded = true,
            vulkanOk = true, vulkanMajor = 1, vulkanMinor = 3, gpuName = "Adreno (TM) 740",
            computeQueue = true, ahbImport = true, subgroupSize = 64, tier = HardwareTier.HIGH
        )
        assertEquals("Adreno 740 · Vulkan 1.3 · 8 cores · 11.0 GB · High", strong.summary())

        val weak = HardwareProfile(
            abi = "armeabi-v7a", cores = 4, bigCores = 0, ramMb = 3789, nativeLoaded = false,
            vulkanOk = false, vulkanMajor = 0, vulkanMinor = 0, gpuName = "",
            computeQueue = false, ahbImport = false, subgroupSize = 0, tier = HardwareTier.LOW
        )
        assertEquals("GPU unknown · no Vulkan · 4 cores · 3.7 GB · Low", weak.summary())
    }
}
