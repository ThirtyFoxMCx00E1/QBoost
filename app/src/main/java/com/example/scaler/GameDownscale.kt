package com.example.scaler

import com.example.hardware.RootShell
import java.util.Locale

/**
 * The real half of "Upscale": with root, Android's own Game Mode downscaling makes the game render
 * fewer pixels (that is where the frame rate comes from), and the display scales the picture back up.
 * Without root an app cannot change another game's render size, so nothing is pretended.
 * Blocking: call from a background thread.
 */
object GameDownscale {

    /** True when the downscale was accepted for [packageName]. */
    fun apply(packageName: String, ratio: Float): Boolean {
        if (packageName.isBlank() || !RootShell.probe()) return false
        val r = String.format(Locale.US, "%.2f", ratio.coerceIn(0.3f, 1.0f))
        val pkg = shellQuote(packageName)
        // Android 13+ syntax first, then the older one; both only print text when they fail.
        val modern = RootShell.run("cmd game set --downscale $r $pkg 2>&1") ?: return false
        if (looksOk(modern)) return true
        val legacy = RootShell.run("cmd game downscale $r $pkg 2>&1") ?: return false
        return looksOk(legacy)
    }

    fun reset(packageName: String) {
        if (packageName.isBlank() || !RootShell.isKnownAvailable) return
        RootShell.run("cmd game reset ${shellQuote(packageName)} 2>&1")
    }

    private fun looksOk(output: String): Boolean {
        val o = output.lowercase(Locale.ROOT)
        return !(o.contains("error") || o.contains("unknown") || o.contains("exception") ||
            o.contains("not ") || o.contains("invalid") || o.contains("usage"))
    }

    private fun shellQuote(s: String): String = "'" + s.replace("'", "") + "'"
}
