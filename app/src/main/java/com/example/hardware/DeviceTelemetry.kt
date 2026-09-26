package com.example.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.BatteryManager
import android.os.Build
import android.view.Display
import com.example.model.PerformanceStats
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Reads REAL numbers from the device. Nothing in here is simulated.
 *
 * What Android lets a normal app read (and what it does not):
 *  - RAM / swap ............. always available (ActivityManager + /proc/meminfo)
 *  - Battery temp / level ... always available (battery broadcast)
 *  - Refresh rate ........... always available (Display)
 *  - CPU load ............... /proc/stat when the ROM allows it, otherwise estimated from the real
 *                             per-core clock speeds (scaling_cur_freq / cpuinfo_max_freq)
 *  - CPU temperature ........ thermal zones when readable, otherwise battery temperature
 *  - GPU load / clock ....... Adreno (kgsl), Mali and MediaTek nodes when readable
 *  - FPS .................... display "measured_fps" node when readable
 *
 * Anything the ROM blocks is reported as unavailable (-1) instead of being faked. If root has been
 * granted to Qboost (see [RootShell]) blocked files are read through root as a fallback.
 *
 * Not thread safe: call [sample] from one background thread.
 */
class DeviceTelemetry(context: Context) {

    private val appContext = context.applicationContext
    private val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    private val deviceModel = "${Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} ${Build.MODEL}"

    private var prevCpuTotal = 0L
    private var prevCpuIdle = 0L
    private var cpuThermalPaths: List<String>? = null
    private var gpuName: String? = null
    private var vulkanVersion: String? = null

    fun sample(): PerformanceStats {
        // ---------- RAM (system-wide, from ActivityManager) ----------
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / BYTES_PER_MB
        val availMb = memInfo.availMem / BYTES_PER_MB
        val usedMb = (totalMb - availMb).coerceAtLeast(0L)
        val ramPercent = if (totalMb > 0) ((usedMb * 100L) / totalMb).toInt().coerceIn(0, 100) else 0

        // ---------- Everything read from files, in one batch ----------
        val thermalPaths = cpuThermalPaths ?: discoverCpuThermalZones().also { cpuThermalPaths = it }
        val wanted = ArrayList<String>()
        wanted.add(PROC_MEMINFO)
        wanted.add(PROC_STAT)
        for (i in 0 until cores) {
            wanted.add(curFreqPath(i))
            wanted.add(maxFreqPath(i))
            wanted.add(scalingMaxFreqPath(i))
        }
        wanted.addAll(GPU_BUSY_PATHS)
        wanted.addAll(GPU_CLOCK_PATHS)
        wanted.addAll(FPS_PATHS)
        wanted.addAll(thermalPaths)
        val files = SysfsReader.readAll(wanted)

        // ---------- Swap / zRAM ----------
        val (swapTotalMb, swapUsedMb) = parseSwap(files[PROC_MEMINFO])

        // ---------- CPU ----------
        val procStatLoad = cpuLoadFromProcStat(files[PROC_STAT])
        var sumCur = 0L
        var sumMax = 0L
        var fastestCurKhz = 0L
        for (i in 0 until cores) {
            val cur = files[curFreqPath(i)]?.trim()?.toLongOrNull() ?: continue
            if (cur <= 0L) continue
            fastestCurKhz = maxOf(fastestCurKhz, cur)
            val max = files[maxFreqPath(i)]?.trim()?.toLongOrNull()
                ?: files[scalingMaxFreqPath(i)]?.trim()?.toLongOrNull()
            if (max != null && max > 0L) {
                sumCur += cur
                sumMax += max
            }
        }
        val freqLoad = if (sumMax > 0L) ((sumCur * 100L) / sumMax).toInt().coerceIn(0, 100) else -1
        val cpuLoad = procStatLoad ?: freqLoad
        val cpuLoadEstimated = procStatLoad == null && freqLoad >= 0
        val cpuFreqMhz = if (fastestCurKhz > 0L) (fastestCurKhz / 1000L).toInt() else -1

        // ---------- GPU ----------
        var gpuLoad = -1
        for (path in GPU_BUSY_PATHS) {
            val text = files[path] ?: continue
            val parsed = parseGpuBusy(path, text)
            if (parsed != null) {
                gpuLoad = parsed
                break
            }
        }
        var gpuMhz = -1
        for (path in GPU_CLOCK_PATHS) {
            val raw = files[path]?.trim()?.toLongOrNull() ?: continue
            if (raw > 0L) {
                gpuMhz = normalizeToMhz(raw)
                break
            }
        }
        val renderer = gpuName ?: queryGpuName().also { gpuName = it }
        val vulkan = vulkanVersion ?: queryVulkanVersion().also { vulkanVersion = it }
        val gpuLabel = buildString {
            if (vulkan.isNotEmpty()) append("Vulkan ").append(vulkan)
            if (renderer.isNotEmpty()) {
                if (isNotEmpty()) append(" / ")
                append(renderer)
            }
        }

        // ---------- FPS (display measured frame rate) ----------
        var fps = -1
        for (path in FPS_PATHS) {
            val text = files[path] ?: continue
            val value = FIRST_NUMBER.find(text)?.value?.toFloatOrNull() ?: continue
            if (value >= 0f) {
                fps = value.roundToInt()
                break
            }
        }

        // ---------- Temperature ----------
        var cpuTemp = -1f
        for (path in thermalPaths) {
            val t = normalizeTemp(files[path]?.trim()?.toLongOrNull()) ?: continue
            if (t > cpuTemp) cpuTemp = t
        }

        // ---------- Battery ----------
        val battery: Intent? = try {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        }
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else 100
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val batteryTempRaw = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val batteryTemp = if (batteryTempRaw == Int.MIN_VALUE) 0f else batteryTempRaw / 10f

        val useCpuTemp = cpuTemp > 0f
        val shownTemp = if (useCpuTemp) cpuTemp else batteryTemp

        return PerformanceStats(
            fps = fps,
            cpuTempC = (shownTemp * 10f).roundToInt() / 10f,
            cpuUsagePercent = cpuLoad,
            ramUsedPercent = ramPercent,
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            gpuUsagePercent = gpuLoad,
            gpuFreqMhz = gpuMhz,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            batteryTempC = (batteryTemp * 10f).roundToInt() / 10f,
            refreshRate = readRefreshRate(),
            cpuCores = cores,
            deviceModel = deviceModel,
            cpuFreqMhz = cpuFreqMhz,
            cpuLoadEstimated = cpuLoadEstimated,
            gpuName = gpuLabel,
            swapUsedMb = swapUsedMb,
            swapTotalMb = swapTotalMb,
            tempSource = if (useCpuTemp) "CPU" else "BAT",
            fpsIsSystemMeasured = fps >= 0
        )
    }

    // ------------------------------------------------------------------------------------------

    private fun readRefreshRate(): Int {
        return try {
            val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            val hz = display?.refreshRate?.roundToInt() ?: 60
            hz.coerceIn(30, 240)
        } catch (_: Exception) {
            60
        }
    }

    private fun cpuLoadFromProcStat(text: String?): Int? {
        if (text == null) return null
        val line = text.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return null
        val parts = line.trim().split(WHITESPACE).drop(1).mapNotNull { it.toLongOrNull() }
        if (parts.size < 4) return null
        val idle = parts[3] + (parts.getOrNull(4) ?: 0L)
        val total = parts.take(8).sum()
        val hadPrevious = prevCpuTotal != 0L
        val dTotal = total - prevCpuTotal
        val dIdle = idle - prevCpuIdle
        prevCpuTotal = total
        prevCpuIdle = idle
        if (!hadPrevious || dTotal <= 0L) return null
        return (((dTotal - dIdle) * 100L) / dTotal).toInt().coerceIn(0, 100)
    }

    /** Returns (swapTotalMb, swapUsedMb). */
    private fun parseSwap(meminfo: String?): Pair<Long, Long> {
        if (meminfo == null) return Pair(0L, 0L)
        var totalKb = 0L
        var freeKb = 0L
        for (line in meminfo.lineSequence()) {
            when {
                line.startsWith("SwapTotal:") -> totalKb = firstLong(line)
                line.startsWith("SwapFree:") -> freeKb = firstLong(line)
            }
        }
        return Pair(totalKb / 1024L, ((totalKb - freeKb).coerceAtLeast(0L)) / 1024L)
    }

    private fun firstLong(line: String): Long =
        Regex("\\d+").find(line)?.value?.toLongOrNull() ?: 0L

    private fun discoverCpuThermalZones(): List<String> {
        val found = ArrayList<String>()
        for (i in 0 until 100) {
            val type = SysfsReader.readDirect("/sys/class/thermal/thermal_zone$i/type")
                ?.trim()?.lowercase(Locale.US) ?: continue
            val isCpu = type.contains("cpu") ||
                type == "soc" || type.startsWith("soc-") || type.startsWith("soc_") ||
                type == "big" || type == "mid" || type == "little"
            if (isCpu && !type.contains("gpu")) {
                found.add("/sys/class/thermal/thermal_zone$i/temp")
                if (found.size >= 12) break
            }
        }
        return found
    }

    private fun normalizeTemp(raw: Long?): Float? {
        if (raw == null) return null
        val c = when {
            raw > 1000L -> raw / 1000f
            raw > 200L -> raw / 10f
            else -> raw.toFloat()
        }
        return if (c in 10f..120f) c else null
    }

    private fun parseGpuBusy(path: String, text: String): Int? {
        if (path.endsWith("/gpubusy")) {
            val nums = Regex("\\d+").findAll(text).mapNotNull { it.value.toLongOrNull() }.toList()
            if (nums.size >= 2 && nums[1] > 0L) {
                return ((nums[0] * 100L) / nums[1]).toInt().coerceIn(0, 100)
            }
            return if (nums.size >= 2) 0 else null
        }
        val v = FIRST_NUMBER.find(text)?.value?.toFloatOrNull() ?: return null
        return v.roundToInt().coerceIn(0, 100)
    }

    /** Sysfs reports GPU clocks in Hz, kHz or MHz depending on the driver. */
    private fun normalizeToMhz(raw: Long): Int = when {
        raw >= 1_000_000L -> (raw / 1_000_000L).toInt()
        raw >= 10_000L -> (raw / 1000L).toInt()
        else -> raw.toInt()
    }

    /** Reads the real GL renderer string (for example "Adreno (TM) 740") through a 1x1 EGL surface. */
    private fun queryGpuName(): String {
        return try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return ""
            val attribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, numConfigs, 0)
            val config = configs[0] ?: return ""
            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val ctx = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            val surfAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            val surface = EGL14.eglCreatePbufferSurface(display, config, surfAttribs, 0)
            EGL14.eglMakeCurrent(display, surface, surface, ctx)
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, ctx)
            renderer.replace("(TM)", "").replace("  ", " ").trim()
        } catch (_: Throwable) {
            ""
        }
    }

    /** Reads the real Vulkan version the hardware reports, for example "1.3". */
    private fun queryVulkanVersion(): String {
        return try {
            val feature = appContext.packageManager.systemAvailableFeatures
                .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
            if (feature == null) "" else "${feature.version ushr 22}.${(feature.version ushr 12) and 0x3FF}"
        } catch (_: Throwable) {
            ""
        }
    }

    private fun curFreqPath(i: Int) = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq"
    private fun maxFreqPath(i: Int) = "/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq"
    private fun scalingMaxFreqPath(i: Int) = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_max_freq"

    companion object {
        private const val BYTES_PER_MB = 1024L * 1024L
        private const val PROC_STAT = "/proc/stat"
        private const val PROC_MEMINFO = "/proc/meminfo"
        private val WHITESPACE = Regex("\\s+")
        private val FIRST_NUMBER = Regex("\\d+(?:\\.\\d+)?")

        private val GPU_BUSY_PATHS = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
            "/sys/kernel/gpu/gpu_busy",
            "/sys/class/misc/mali0/device/utilization",
            "/sys/kernel/ged/hal/gpu_utilization"
        )
        private val GPU_CLOCK_PATHS = listOf(
            "/sys/class/kgsl/kgsl-3d0/clock_mhz",
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/kernel/gpu/gpu_clock"
        )
        private val FPS_PATHS = listOf(
            "/sys/class/graphics/fb0/measured_fps",
            "/sys/class/drm/sde-crtc-0/measured_fps",
            "/sys/class/drm/card0/sde-crtc-0/measured_fps"
        )
    }
}

/** Reads text files directly, and (only if root was already granted) through root as a fallback. */
internal object SysfsReader {

    private val rootMissing = HashSet<String>()
    private var callCount = 0

    fun readDirect(path: String): String? {
        return try {
            File(path).readText()
        } catch (_: Throwable) {
            null
        }
    }

    fun readAll(paths: List<String>): Map<String, String> {
        val result = HashMap<String, String>()
        val failed = ArrayList<String>()
        for (path in paths) {
            val text = readDirect(path)
            if (text != null) result[path] = text else failed.add(path)
        }

        if (failed.isNotEmpty() && RootShell.isKnownAvailable) {
            callCount++
            if (callCount % 60 == 0) rootMissing.clear()
            val todo = failed.filter { it !in rootMissing }
            if (todo.isNotEmpty()) {
                val script = todo.joinToString(";") { "echo '#@@$it'; cat $it 2>/dev/null" }
                val output = RootShell.run(script)
                if (output != null) {
                    val sections = HashMap<String, StringBuilder>()
                    var current: StringBuilder? = null
                    for (line in output.lineSequence()) {
                        if (line.startsWith("#@@")) {
                            current = StringBuilder().also { sections[line.substring(3)] = it }
                        } else {
                            current?.append(line)?.append('\n')
                        }
                    }
                    for (path in todo) {
                        val body = sections[path]?.toString()
                        if (!body.isNullOrBlank()) result[path] = body else rootMissing.add(path)
                    }
                }
            }
        }
        return result
    }
}
