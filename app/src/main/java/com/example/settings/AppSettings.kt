package com.example.settings

import android.content.Context
import com.example.i18n.I18n

/** User settings shown in the Qboost Settings screen. The in-game panel service reads the same values. */
data class AppSettings(
    val language: String = "en",
    val controllerHints: Int = CONTROLLER_HINTS_AUTO,
    val musicVolume: Float = 1.0f,
    val handleOpacity: Float = 0.0f,
    val panelOpacity: Float = 0.9f,
    val showDock: Boolean = true,
    val scalerSharpness: Float = 0.6f,
    val updateAlerts: Boolean = true
) {
    companion object {
        const val CONTROLLER_HINTS_AUTO = 0
        const val CONTROLLER_HINTS_ALWAYS = 1
        const val CONTROLLER_HINTS_NEVER = 2
    }
}

object SettingsStore {

    private const val PREFS = "qboost_settings"
    const val KEY_LANGUAGE = "language"
    const val KEY_CONTROLLER_HINTS = "controller_hints"
    const val KEY_MUSIC_VOLUME = "music_volume"
    const val KEY_HANDLE_OPACITY = "handle_opacity"
    const val KEY_PANEL_OPACITY = "panel_opacity"
    const val KEY_SHOW_DOCK = "show_dock"
    const val KEY_SCALER_SHARPNESS = "scaler_sharpness"
    const val KEY_UPDATE_ALERTS = "update_alerts"

    fun prefsName(): String = PREFS

    fun load(context: Context): AppSettings {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedLanguage = prefs.getString(KEY_LANGUAGE, null)
        val language = if (savedLanguage != null && I18n.languages.any { it.code == savedLanguage }) {
            savedLanguage
        } else {
            I18n.defaultLanguage()
        }
        return AppSettings(
            language = language,
            controllerHints = prefs.getInt(KEY_CONTROLLER_HINTS, AppSettings.CONTROLLER_HINTS_AUTO).coerceIn(0, 2),
            musicVolume = prefs.getFloat(KEY_MUSIC_VOLUME, 1.0f).coerceIn(0f, 1f),
            handleOpacity = prefs.getFloat(KEY_HANDLE_OPACITY, 0.0f).coerceIn(0f, 1f),
            panelOpacity = prefs.getFloat(KEY_PANEL_OPACITY, 0.9f).coerceIn(0.3f, 1f),
            showDock = prefs.getBoolean(KEY_SHOW_DOCK, true),
            scalerSharpness = prefs.getFloat(KEY_SCALER_SHARPNESS, 0.6f).coerceIn(0f, 1f),
            updateAlerts = prefs.getBoolean(KEY_UPDATE_ALERTS, true)
        )
    }

    fun save(context: Context, settings: AppSettings) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, settings.language)
            .putInt(KEY_CONTROLLER_HINTS, settings.controllerHints)
            .putFloat(KEY_MUSIC_VOLUME, settings.musicVolume)
            .putFloat(KEY_HANDLE_OPACITY, settings.handleOpacity)
            .putFloat(KEY_PANEL_OPACITY, settings.panelOpacity)
            .putBoolean(KEY_SHOW_DOCK, settings.showDock)
            .putFloat(KEY_SCALER_SHARPNESS, settings.scalerSharpness)
            .putBoolean(KEY_UPDATE_ALERTS, settings.updateAlerts)
            .apply()
    }
}
