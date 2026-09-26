package com.example.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.Intent

/**
 * Asks Android to stop BACKGROUND processes of installed apps, then measures how much memory was
 * really freed. The number you see is the measured difference in available RAM, never a random or
 * hard-coded value. If nothing could be freed the result is honestly 0.
 */
object RamBooster {

    data class Result(val requestedApps: Int, val freedMb: Long)

    /** Blocking (waits a moment for the kernel to reclaim memory). Call from a background thread. */
    fun purge(context: Context, keepPackages: Set<String>): Result {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = availableMb(am)

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packages = try {
            appContext.packageManager.queryIntentActivities(launcherIntent, 0)
                .map { it.activityInfo.packageName }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }

        var requested = 0
        for (pkg in packages) {
            if (pkg == appContext.packageName || pkg in keepPackages) continue
            try {
                am.killBackgroundProcesses(pkg)
                requested++
            } catch (_: Exception) {
            }
        }

        try {
            Thread.sleep(700)
        } catch (_: InterruptedException) {
        }
        val after = availableMb(am)
        return Result(requested, (after - before).coerceAtLeast(0L))
    }

    private fun availableMb(am: ActivityManager): Long {
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem / (1024L * 1024L)
    }
}
