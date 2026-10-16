package com.example.scaler

import android.hardware.HardwareBuffer
import android.os.Build
import android.view.Surface

/**
 * JNI bridge to libqboost_lsfg.so: the LSFG frame generation engine (from DroidDeck, GPL-3.0-or-later)
 * plus Qboost's capture -> Vulkan -> overlay pipeline. The library is only shipped for arm64-v8a and
 * x86_64; everywhere else [available] is false and the Scaler says so instead of faking frames.
 */
object LsfgNative {
    const val STATUS_OK = 0
    const val STATUS_NOT_INSTALLED = 1
    const val STATUS_UNREADABLE_FILE = 2
    const val STATUS_NOT_PORTABLE_EXECUTABLE = 3
    const val STATUS_MISSING_SHADERS = 4
    const val STATUS_TRANSLATION_FAILED = 5
    const val STATUS_CACHE_UNUSABLE = 6

    const val VARIANT_NONE = 0

    val available: Boolean by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@lazy false
        try {
            System.loadLibrary("qboost_lsfg")
            true
        } catch (_: Throwable) {
            false
        }
    }

    @JvmStatic external fun nativeValidateDll(dllPath: String): Int
    @JvmStatic external fun nativeBuildCache(dllPath: String, cachePath: String, preferFp16: Boolean): Int
    @JvmStatic external fun nativeCacheVariant(cachePath: String): Int
    @JvmStatic external fun nativeStatusName(status: Int): String
    @JvmStatic external fun nativeVariantName(variant: Int): String

    /** Empty when this phone's GPU can run LSFG, otherwise why not. Creates (and drops) a Vulkan instance. */
    @JvmStatic external fun nativeProbe(): String
    @JvmStatic external fun nativeLastError(): String

    /** [cachePath] null = show the capture only (no generated frames). Returns 0 on failure. */
    @JvmStatic external fun nativeStart(
        surface: Surface,
        cachePath: String?,
        width: Int,
        height: Int,
        multiplier: Int,
        flowScale: Float,
        refreshHz: Float
    ): Long

    /** Processes and presents one captured frame; returns generated frames, or -1 on a fatal error. */
    @JvmStatic external fun nativeFrame(handle: Long, buffer: HardwareBuffer): Int
    @JvmStatic external fun nativeConfigure(handle: Long, multiplier: Int, flowScale: Float)

    /** out = [source fps, shown fps, multiplier, frames generated in total] */
    @JvmStatic external fun nativeStats(handle: Long, out: FloatArray)
    @JvmStatic external fun nativeStop(handle: Long)
}
