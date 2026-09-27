package com.example.hardware

import com.example.model.PerformanceStats
import java.util.Locale

/** One place that turns real telemetry into text, so Compose screens and overlay views agree. */
object TelemetryFormat {

    const val NA = "N/A"

    fun fps(stats: PerformanceStats): String = if (stats.fps >= 0) "${stats.fps}" else "--"

    /** "52%", "~52%" when estimated from clock speeds, or "N/A" when the ROM blocks the data. */
    fun load(percent: Int, estimated: Boolean = false): String =
        if (percent < 0) NA else (if (estimated) "~" else "") + "$percent%"

    fun gb(mb: Long): String = String.format(Locale.US, "%.1f", mb / 1024.0)

    fun ghz(mhz: Int): String = String.format(Locale.US, "%.2f GHz", mhz / 1000.0)

    fun temp(c: Float): String = String.format(Locale.US, "%.1f°C", c)

    fun cpuDetail(stats: PerformanceStats): String {
        val cores = "${stats.cpuCores} cores"
        return if (stats.cpuFreqMhz > 0) "$cores · ${ghz(stats.cpuFreqMhz)}" else cores
    }

    fun gpuDetail(stats: PerformanceStats): String {
        val parts = ArrayList<String>()
        if (stats.gpuName.isNotEmpty()) parts.add(stats.gpuName)
        if (stats.gpuFreqMhz > 0) parts.add("${stats.gpuFreqMhz} MHz")
        return if (parts.isEmpty()) "GPU" else parts.joinToString(" · ")
    }

    fun ram(stats: PerformanceStats): String =
        "${gb(stats.ramUsedMb)} / ${gb(stats.ramTotalMb)} GB (${stats.ramUsedPercent}%)"

    fun swap(stats: PerformanceStats): String =
        if (stats.swapTotalMb <= 0L) "off"
        else "${gb(stats.swapUsedMb)} / ${gb(stats.swapTotalMb)} GB"

    fun ping(stats: PerformanceStats): String = if (stats.pingMs >= 0) "${stats.pingMs}ms" else "--"
}
