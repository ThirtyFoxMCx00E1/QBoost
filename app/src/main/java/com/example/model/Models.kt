package com.example.model

enum class PerformanceMode(val displayName: String, val description: String) {
    PERFORMANCE("Performance", "Max CPU/GPU clock, thermal limit elevated, high touch rate"),
    BALANCED("Balanced", "Optimized power-to-performance ratio, steady thermals"),
    BATTERY("Battery saver", "Power saving mode, conservative clocks, lower screen refresh")
}

data class GameItem(
    val id: String,
    val name: String,
    val packageName: String = "",
    val initials: String = name.take(2).uppercase(),
    val iconColor: Long = 0xFF2F80FF,
    val isInstalled: Boolean = false,
    val isFavorite: Boolean = false,
    val performanceMode: PerformanceMode = PerformanceMode.PERFORMANCE,
    val saturation: Float = 1.25f, // 0.5f to 2.0f, default 1.25 for vibrant gaming
    val touchSensitivity: Int = 8, // 1 to 10
    val blockNotifications: Boolean = true,
    val blockCalls: Boolean = true,
    val targetFps: Int = 120, // 60, 90, 120, 144
    val touchBooster: Boolean = true,
    val gameModeActive: Boolean = true
)

data class InstalledAppItem(
    val name: String,
    val packageName: String,
    val isGame: Boolean = false,
    val isAlreadyAdded: Boolean = false
)

/**
 * Live device telemetry. Every value comes from the real device (see DeviceTelemetry).
 * A value of -1 means "this ROM does not let a normal app read it" - it is shown as N/A / "--"
 * instead of being faked.
 */
data class PerformanceStats(
    val fps: Int = -1,
    val cpuTempC: Float = 0f,
    val cpuUsagePercent: Int = -1,
    val ramUsedPercent: Int = 0,
    val ramUsedMb: Long = 0,
    val ramTotalMb: Long = 0,
    val gpuUsagePercent: Int = -1,
    val gpuFreqMhz: Int = -1,
    val pingMs: Int = -1,
    val isOnline: Boolean = true,
    val processesCleared: Int = 0,
    val freedRamMb: Long = 0,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val batteryTempC: Float = 0f,
    val refreshRate: Int = 60,
    val cpuCores: Int = 8,
    val deviceModel: String = "",
    // ---- v5.0 additions ----
    val cpuFreqMhz: Int = -1,
    val cpuLoadEstimated: Boolean = false, // true = derived from real per-core clocks
    val gpuName: String = "",              // e.g. "Vulkan 1.3 / Adreno 740"
    val swapUsedMb: Long = 0,
    val swapTotalMb: Long = 0,
    val tempSource: String = "BAT",        // "CPU" thermal zone, or "BAT" battery sensor
    val fpsIsSystemMeasured: Boolean = false
)

/** Result of the last attempt to change the screen saturation. */
data class SaturationUiState(
    val attempted: Boolean = false,
    val ok: Boolean = false,
    val appliedValue: Float = 1.0f,
    val message: String = ""
)

data class OverlayConfig(
    val isFpsVisible: Boolean = true,
    val isCpuTempVisible: Boolean = true,
    val isRamVisible: Boolean = true,
    val isGpuVisible: Boolean = true,
    val isPingVisible: Boolean = true,
    val posX: Float = 16f,
    val posY: Float = 16f,
    val scale: Float = 1.0f,
    val alpha: Float = 0.95f,
    val isFloatingWindowOpen: Boolean = false
)

data class FloatingAppItem(
    val id: String,
    val name: String,
    val iconType: String, // "browser", "youtube", "phone", "notes", "clock"
    val contentUrl: String = ""
)
