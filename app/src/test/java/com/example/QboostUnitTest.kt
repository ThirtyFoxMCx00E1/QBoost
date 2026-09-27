package com.example

import com.example.model.GameItem
import com.example.model.OverlayConfig
import com.example.model.PerformanceMode
import com.example.model.PerformanceStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QboostUnitTest {

    @Test
    fun testPerformanceModes() {
        assertEquals("Performance", PerformanceMode.PERFORMANCE.displayName)
        assertEquals("Balanced", PerformanceMode.BALANCED.displayName)
        assertEquals("Battery saver", PerformanceMode.BATTERY.displayName)
    }

    @Test
    fun testGameItemDefaultsAndCustomization() {
        val game = GameItem(
            id = "wuthering",
            name = "Wuthering Waves",
            packageName = "com.kurogame.wutheringwaves.global",
            performanceMode = PerformanceMode.PERFORMANCE,
            saturation = 1.4f,
            touchSensitivity = 9,
            blockNotifications = true,
            targetFps = 120
        )

        assertEquals("Wuthering Waves", game.name)
        assertEquals(1.4f, game.saturation)
        assertEquals(9, game.touchSensitivity)
        assertTrue(game.blockNotifications)
        assertEquals(120, game.targetFps)
        assertEquals("WU", game.initials)
    }

    @Test
    fun testOverlayConfigToggles() {
        val config = OverlayConfig(
            isFpsVisible = true,
            isCpuTempVisible = true,
            isRamVisible = true,
            isGpuVisible = true,
            isPingVisible = true,
            posX = 20f,
            posY = 50f
        )

        assertTrue(config.isFpsVisible)
        assertTrue(config.isPingVisible)
        assertEquals(20f, config.posX)

        val updated = config.copy(isPingVisible = false, posX = 100f)
        assertFalse(updated.isPingVisible)
        assertEquals(100f, updated.posX)
    }

    @Test
    fun testPerformanceStatsCalculations() {
        val stats = PerformanceStats(
            fps = 120,
            cpuTempC = 38.5f,
            cpuUsagePercent = 75,
            ramUsedPercent = 80,
            ramUsedMb = 6400,
            ramTotalMb = 8000,
            pingMs = 28,
            isOnline = true
        )

        assertEquals(120, stats.fps)
        assertEquals(38.5f, stats.cpuTempC)
        assertTrue(stats.isOnline)
        assertEquals(28, stats.pingMs)
    }
}
