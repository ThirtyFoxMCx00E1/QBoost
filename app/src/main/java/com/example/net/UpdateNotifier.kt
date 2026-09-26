package com.example.net

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.R
import com.example.i18n.I18n
import com.example.settings.SettingsStore

/**
 * "Update alerts": at most once every 12 hours Qboost looks for a newer version on GitHub and, if there
 * is one you were not told about yet, shows a normal notification. Blocking: run on a background thread.
 */
object UpdateNotifier {

    private const val CHANNEL_ID = "qboost_update_channel"
    private const val NOTIFICATION_ID = 4001
    private const val CHECK_EVERY_MS = 12L * 60L * 60L * 1000L
    private const val PREFS = "qboost_settings"
    private const val KEY_LAST_CHECK = "update_last_check"
    private const val KEY_NOTIFIED = "update_notified_version"

    fun checkAndNotify(context: Context) {
        val app = context.applicationContext
        val settings = SettingsStore.load(app)
        if (!settings.updateAlerts) return

        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_EVERY_MS) return
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

        val current = try {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: return
        } catch (_: Exception) {
            return
        }
        val result = UpdateChecker.check(current)
        if (result !is UpdateChecker.Result.Available) return
        if (prefs.getString(KEY_NOTIFIED, "") == result.latest) return
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return

        I18n.init(app)
        val language = settings.language
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Qboost updates", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            app,
            5,
            Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.REPO_URL)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(I18n.t(language, "update_notif_title"))
            .setContentText(I18n.tf(language, "update_notif_text", result.latest))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification)
            prefs.edit().putString(KEY_NOTIFIED, result.latest).apply()
        } catch (_: SecurityException) {
            // notification permission was revoked in the meantime
        }
    }
}
