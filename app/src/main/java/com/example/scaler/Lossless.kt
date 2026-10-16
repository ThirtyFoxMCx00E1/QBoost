package com.example.scaler

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.InputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

/**
 * The user's own Lossless.dll (Lossless Scaling on Steam, app 993090). Qboost never ships it: the user
 * copies it from `steamapps/common/Lossless Scaling/Lossless.dll` to the phone and picks it once. The
 * LSFG shaders inside are translated into a SPIR-V cache next to it, and that cache is all the Scaler
 * loads at run time. Adapted from DroidDeck's `gpu/Lossless.kt` (which finds the DLL in its own Steam).
 */
object Lossless {
    enum class ImportResult { UPDATED, UNCHANGED, INVALID, NOT_SUPPORTED, FAILED }

    data class State(val ready: Boolean, val variant: String)

    private const val TAG = "QboostLossless"
    private const val PREFS = "qboost_lossless"
    private const val KEY_ID = "id"

    /** Status of the imported DLL. Cheap: only reads a small header of the cache file. */
    fun state(context: Context): State {
        if (!LsfgNative.available) return State(false, "")
        val cache = cacheFile(context) ?: return State(false, "")
        val variant = runCatching { LsfgNative.nativeCacheVariant(cache.path) }.getOrDefault(LsfgNative.VARIANT_NONE)
        if (variant == LsfgNative.VARIANT_NONE) return State(false, "")
        return State(true, runCatching { LsfgNative.nativeVariantName(variant) }.getOrDefault(""))
    }

    /** The shader cache the Scaler should load, rebuilt from the stored DLL if needed; null if none. */
    @Synchronized
    fun readyCache(context: Context): File? {
        if (!LsfgNative.available) return null
        val cache = cacheFile(context) ?: return null
        if (usable(cache)) return cache
        val dll = storedDll(context)
        if (!dll.isFile) return null
        return if (build(dll, cache) == LsfgNative.STATUS_OK) cache else null
    }

    /** Copies the picked file in, checks it really is Lossless.dll with the LSFG shaders, and builds the cache. */
    @Synchronized
    fun import(context: Context, uri: Uri): ImportResult {
        if (!LsfgNative.available) return ImportResult.NOT_SUPPORTED
        val input = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            Log.w(TAG, "cannot open $uri", e)
            null
        } ?: return ImportResult.FAILED
        return input.use { adopt(context, it) }
    }

    private fun adopt(context: Context, source: InputStream): ImportResult {
        val staged = File(dir(context).apply { mkdirs() }, "Lossless.dll.staged")
        try {
            val id = copyHashing(source, staged)
            if (id == prefs(context).getString(KEY_ID, null) && usable(cacheFor(context, id))) {
                return ImportResult.UNCHANGED
            }
            val cache = cacheFor(context, id)
            var status = LsfgNative.nativeValidateDll(staged.path)
            if (status == LsfgNative.STATUS_OK) status = build(staged, cache)
            if (status != LsfgNative.STATUS_OK) {
                cache.delete()
                Log.w(TAG, "Lossless.dll rejected: ${statusName(status)}")
                return when (status) {
                    LsfgNative.STATUS_UNREADABLE_FILE, LsfgNative.STATUS_NOT_PORTABLE_EXECUTABLE,
                    LsfgNative.STATUS_MISSING_SHADERS -> ImportResult.INVALID
                    else -> ImportResult.FAILED
                }
            }
            val stored = storedDll(context)
            stored.delete()
            if (!staged.renameTo(stored)) {
                cache.delete()
                return ImportResult.FAILED
            }
            prefs(context).edit().putString(KEY_ID, id).apply()
            removeStale(context, id)
            Log.i(TAG, "Lossless.dll ready as $id")
            return ImportResult.UPDATED
        } catch (e: java.io.IOException) {
            Log.w(TAG, "could not copy Lossless.dll", e)
            return ImportResult.FAILED
        } catch (t: Throwable) {
            Log.e(TAG, "could not take Lossless.dll", t)
            return ImportResult.FAILED
        } finally {
            staged.delete()
        }
    }

    fun remove(context: Context) {
        dir(context).deleteRecursively()
        prefs(context).edit().remove(KEY_ID).apply()
    }

    private fun build(dll: File, cache: File): Int = try {
        LsfgNative.nativeBuildCache(dll.path, cache.path, true)
    } catch (t: Throwable) {
        Log.e(TAG, "shader cache ${cache.name}", t)
        LsfgNative.STATUS_CACHE_UNUSABLE
    }

    private fun usable(cache: File): Boolean = cache.isFile &&
        runCatching { LsfgNative.nativeCacheVariant(cache.path) != LsfgNative.VARIANT_NONE }.getOrDefault(false)

    private fun copyHashing(source: InputStream, target: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestOutputStream(target.outputStream(), digest).use { output -> source.copyTo(output) }
        return digest.digest().take(8).joinToString("") { "%02x".format(it) }
    }

    private fun removeStale(context: Context, keep: String) {
        dir(context).listFiles { f -> f.name.startsWith("shaders-") && f.name != cacheFor(context, keep).name }
            ?.forEach { it.delete() }
    }

    private fun statusName(status: Int): String =
        runCatching { LsfgNative.nativeStatusName(status) }.getOrDefault("status $status")

    private fun cacheFile(context: Context): File? = prefs(context).getString(KEY_ID, null)?.let { cacheFor(context, it) }
    private fun dir(context: Context) = File(context.filesDir, "lsfg")
    private fun storedDll(context: Context) = File(dir(context), "Lossless.dll")
    private fun cacheFor(context: Context, id: String) = File(dir(context), "shaders-$id.cache")
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
