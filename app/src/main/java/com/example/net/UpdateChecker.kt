package com.example.net

import java.net.HttpURLConnection
import java.net.URL

/**
 * Looks at the versionName on the main branch of the Qboost GitHub repo and compares it with the
 * installed version. Blocking: call from a background thread.
 */
object UpdateChecker {

    const val REPO_URL = "https://github.com/ThirtyFoxMCx00E1/Qboost"
    private const val GRADLE_URL =
        "https://raw.githubusercontent.com/ThirtyFoxMCx00E1/Qboost/main/app/build.gradle.kts"

    sealed class Result {
        object UpToDate : Result()
        data class Available(val latest: String) : Result()
        object Failed : Result()
    }

    fun check(currentVersion: String): Result {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(GRADLE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }
            if (connection.responseCode != 200) return Result.Failed
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val latest = Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
                ?: return Result.Failed
            if (isNewer(latest, currentVersion)) Result.Available(latest) else Result.UpToDate
        } catch (_: Exception) {
            Result.Failed
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /** "8.0.1" > "8.0", compared number by number. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
