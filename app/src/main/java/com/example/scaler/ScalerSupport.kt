package com.example.scaler

import android.content.Context

/** What the Scaler can really do on this phone, worked out once (it creates a Vulkan instance). */
object ScalerSupport {

    data class Report(
        /** The native library loaded (64-bit phone, Android 10+). */
        val libraryLoaded: Boolean,
        /** Empty when the GPU passes LSFG's checks, otherwise the driver's reason. */
        val gpuProblem: String,
        /** Lossless.dll imported and its shader cache built. */
        val losslessReady: Boolean,
        /** "SPIR-V fp16" etc., empty when not ready. */
        val shaderVariant: String
    ) {
        val frameGenPossible: Boolean get() = libraryLoaded && gpuProblem.isEmpty()
        val frameGenReady: Boolean get() = frameGenPossible && losslessReady
    }

    @Volatile
    private var gpuProblem: String? = null

    /** Called (on the main thread) after a Lossless.dll import, so the panel can refresh. */
    var onLosslessChanged: (() -> Unit)? = null

    /** Blocking (first call creates a Vulkan instance): call from a background thread. */
    fun report(context: Context): Report {
        if (!LsfgNative.available) return Report(false, "", false, "")
        val problem = gpuProblem ?: runCatching { LsfgNative.nativeProbe() }
            .getOrElse { "probe failed: ${it.message}" }
            .also { gpuProblem = it }
        val state = Lossless.state(context)
        return Report(true, problem, state.ready, state.variant)
    }
}
