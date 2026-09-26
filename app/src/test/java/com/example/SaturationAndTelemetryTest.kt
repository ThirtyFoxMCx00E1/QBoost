package com.example

import com.example.display.SaturationEngine
import com.example.hardware.TelemetryFormat
import com.example.model.PerformanceStats
import org.junit.Assert.assertEquals
import org.junit.Test

class SaturationAndTelemetryTest {

    @Test
    fun saturationCommandsAreClampedAndFormatted() {
        assertEquals("service call SurfaceFlinger 1022 f 1.30", SaturationEngine.shellCommand(1.3f))
        assertEquals("service call SurfaceFlinger 1022 f 2.00", SaturationEngine.shellCommand(5f))
        assertEquals("service call SurfaceFlinger 1022 f 0.00", SaturationEngine.shellCommand(-1f))
        assertEquals("adb shell service call SurfaceFlinger 1022 f 1.00", SaturationEngine.adbCommand(1f))
    }

    @Test
    fun saturationPercentText() {
        assertEquals("100%", SaturationEngine.percentText(1f))
        assertEquals("130%", SaturationEngine.percentText(1.3f))
        assertEquals("200%", SaturationEngine.percentText(9f))
        assertEquals("0%", SaturationEngine.percentText(-2f))
    }

    @Test
    fun unavailableTelemetryIsNeverFaked() {
        val stats = PerformanceStats()
        assertEquals("--", TelemetryFormat.fps(stats))
        assertEquals("N/A", TelemetryFormat.load(stats.cpuUsagePercent))
        assertEquals("N/A", TelemetryFormat.load(stats.gpuUsagePercent))
        assertEquals("--", TelemetryFormat.ping(stats))
    }

    @Test
    fun realTelemetryFormatting() {
        val stats = PerformanceStats(
            fps = 118,
            cpuUsagePercent = 52,
            cpuFreqMhz = 2840,
            cpuCores = 8,
            ramUsedMb = 6144,
            ramTotalMb = 12288,
            ramUsedPercent = 50,
            swapUsedMb = 1024,
            swapTotalMb = 4096
        )
        assertEquals("118", TelemetryFormat.fps(stats))
        assertEquals("52%", TelemetryFormat.load(52))
        assertEquals("~52%", TelemetryFormat.load(52, estimated = true))
        assertEquals("2.84 GHz", TelemetryFormat.ghz(2840))
        assertEquals("8 cores · 2.84 GHz", TelemetryFormat.cpuDetail(stats))
        assertEquals("6.0 / 12.0 GB (50%)", TelemetryFormat.ram(stats))
        assertEquals("1.0 / 4.0 GB", TelemetryFormat.swap(stats))
        assertEquals("off", TelemetryFormat.swap(PerformanceStats()))
    }
}
