package com.example.display

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.example.hardware.RootShell
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Real, system-wide screen saturation.
 *
 * Android does not let a normal app recolor the pixels of another app (a game). The only real
 * switch is SurfaceFlinger's global saturation control - the same one Android itself uses for
 * grayscale / color modes. It can be driven:
 *   1. by Qboost, when root is available (`su`), or
 *   2. once from a computer over ADB with the command from [adbCommand].
 *
 * 1.00 = normal, 0.00 = grayscale, 2.00 = maximum. The setting is global and lasts until it is
 * reset or the phone reboots, so Qboost resets it to 1.00 when the HUD is stopped.
 */
object SaturationEngine {

    const val MIN = 0.0f
    const val MAX = 2.0f
    const val NEUTRAL = 1.0f

    /** SurfaceFlinger transaction code "set saturation". */
    private const val SF_SET_SATURATION = 1022

    /** Accepts both the old `Parcel(00000000 ...)` and newer `Parcel(\n 0x00000000: 00000000 ...)` output. */
    private val SUCCESS = Regex("Parcel\\(\\s*(?:0x[0-9a-fA-F]+:\\s*)?00000000")

    data class Result(val ok: Boolean, val message: String)

    @Volatile
    var lastApplied: Float = NEUTRAL
        private set

    fun format(value: Float): String = String.format(Locale.US, "%.2f", value.coerceIn(MIN, MAX))

    /** The command Qboost runs through root. */
    fun shellCommand(value: Float): String = "service call SurfaceFlinger $SF_SET_SATURATION f ${format(value)}"

    /** The same command for a PC / wireless-debugging shell. */
    fun adbCommand(value: Float): String = "adb shell ${shellCommand(value)}"

    fun percentText(value: Float): String = "${(value.coerceIn(MIN, MAX) * 100f).roundToInt()}%"

    /** Blocking: spawns `su`. Always call from a background thread. */
    fun apply(value: Float): Result {
        val v = value.coerceIn(MIN, MAX)
        if (!RootShell.probe()) {
            return Result(false, NO_ROOT_MESSAGE)
        }
        val output = RootShell.run(shellCommand(v))
        val ok = output != null && SUCCESS.containsMatchIn(output)
        return if (ok) {
            lastApplied = v
            Result(true, "Saturation ${percentText(v)} active (root)")
        } else {
            val detail = output?.trim()?.replace('\n', ' ')?.take(90).orEmpty()
            Result(false, "SurfaceFlinger did not accept the change. $detail".trim())
        }
    }

    /** Back to normal colors. Blocking, background thread only. */
    fun reset(): Result = apply(NEUTRAL)

    /** Copies the ADB command to the clipboard so the user can paste it on a PC / Termux+ADB. */
    fun copyAdbCommand(context: Context, value: Float) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Qboost saturation", adbCommand(value)))
        } catch (_: Exception) {
        }
    }

    const val NO_ROOT_MESSAGE =
        "No root access. Android blocks apps from recoloring other apps. Grant root to Qboost, " +
            "or run the ADB command once."
}

/** Remembers the chosen saturation per game (keyed by package name, or id for custom entries). */
object SaturationStore {

    private const val PREFS = "qboost_settings"

    private fun key(gameKey: String) = "sat_$gameKey"

    fun get(context: Context, gameKey: String): Float? {
        if (gameKey.isBlank()) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.contains(key(gameKey))) prefs.getFloat(key(gameKey), SaturationEngine.NEUTRAL) else null
    }

    fun set(context: Context, gameKey: String, value: Float) {
        if (gameKey.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(key(gameKey), value.coerceIn(SaturationEngine.MIN, SaturationEngine.MAX))
            .apply()
    }
}
