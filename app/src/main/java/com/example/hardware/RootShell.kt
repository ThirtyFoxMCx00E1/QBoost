package com.example.hardware

/**
 * Tiny wrapper around `su`.
 *
 * Qboost never asks for root on its own at start-up. Root is only tried when the user does
 * something that genuinely needs it (moving the saturation slider, pressing a preset).
 * Everything here blocks, so call it from a background thread.
 */
object RootShell {

    @Volatile
    private var granted = false

    @Volatile
    private var lastProbeAt = 0L

    /** True only after a successful `su` call, so telemetry can use it without ever prompting. */
    val isKnownAvailable: Boolean
        get() = granted

    /**
     * Checks (and caches) whether `su` works. If it failed recently we do not try again for a
     * few seconds so a slider drag does not spawn a process for every tick.
     */
    @Synchronized
    fun probe(): Boolean {
        if (granted) return true
        val now = System.currentTimeMillis()
        if (lastProbeAt != 0L && now - lastProbeAt < 20_000L) return false
        lastProbeAt = now
        val out = run("id")
        granted = out?.contains("uid=0") == true
        return granted
    }

    /** Runs [command] as root and returns stdout+stderr, or null if `su` could not be started. */
    fun run(command: String): String? {
        var process: Process? = null
        return try {
            process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output
        } catch (_: Throwable) {
            null
        } finally {
            try {
                process?.destroy()
            } catch (_: Throwable) {
            }
        }
    }
}
