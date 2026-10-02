package com.example.settings

import android.content.Context
import com.example.hardware.ScalerMode
import com.example.hardware.ScalerQuality
import com.example.i18n.I18n

/** User settings shown in the Qboost Settings screen. The in-game panel service reads the same values. */
data class AppSettings(
    val language: String = "en",
    val controllerHints: Int = CONTROLLER_HINTS_AUTO,
    val musicVolume: Float = 1.0f,
    val handleOpacity: Float = 0.0f,
    val handleOutsideEdge: Boolean = false, // false = inset inside the visible 16:9 safe area, true = pinned right at the screen corner
    val panelOpacity: Float = 0.9f,
    val showDock: Boolean = true,
    val scalerSharpness: Float = 0.6f,
    val updateAlerts: Boolean = true,
    val themeMode: String = "default", // "default" | "dark" | "light" | "custom"
    val customBackground: Int = 0xFF080B12.toInt(),
    val customAccent: Int = 0xFF2F80FF.toInt(),
    val customText: Int = 0xFFF5F7FA.toInt(),
    val customOpacity: Float = 1f
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
    const val KEY_HANDLE_OUTSIDE_EDGE = "handle_outside_edge"
    const val KEY_PANEL_OPACITY = "panel_opacity"
    const val KEY_SHOW_DOCK = "show_dock"
    const val KEY_SCALER_SHARPNESS = "scaler_sharpness"
    const val KEY_UPDATE_ALERTS = "update_alerts"
    const val KEY_SCALER_MODE = "scaler_mode"
    const val KEY_SCALER_QUALITY = "scaler_quality"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_CUSTOM_BACKGROUND = "custom_background"
    const val KEY_CUSTOM_ACCENT = "custom_accent"
    const val KEY_CUSTOM_TEXT = "custom_text"
    const val KEY_CUSTOM_OPACITY = "custom_opacity"

    fun prefsName(): String = PREFS

    // ---- Scaler (the merged Upscaler + Frame gen) ----
    fun scalerMode(context: Context): Int =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SCALER_MODE, ScalerMode.BOTH).coerceIn(ScalerMode.UPSCALE, ScalerMode.BOTH)

    fun setScalerMode(context: Context, mode: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SCALER_MODE, mode.coerceIn(ScalerMode.UPSCALE, ScalerMode.BOTH)).apply()
    }

    fun scalerQuality(context: Context): Int =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SCALER_QUALITY, ScalerQuality.AUTO).coerceIn(ScalerQuality.AUTO, ScalerQuality.FAST)

    fun setScalerQuality(context: Context, quality: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SCALER_QUALITY, quality.coerceIn(ScalerQuality.AUTO, ScalerQuality.FAST)).apply()
    }

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
            handleOutsideEdge = prefs.getBoolean(KEY_HANDLE_OUTSIDE_EDGE, false),
            panelOpacity = prefs.getFloat(KEY_PANEL_OPACITY, 0.9f).coerceIn(0.3f, 1f),
            showDock = prefs.getBoolean(KEY_SHOW_DOCK, true),
            scalerSharpness = prefs.getFloat(KEY_SCALER_SHARPNESS, 0.6f).coerceIn(0f, 1f),
            updateAlerts = prefs.getBoolean(KEY_UPDATE_ALERTS, true),
            themeMode = prefs.getString(KEY_THEME_MODE, "default")?.takeIf {
                it in listOf("default", "dark", "light", "custom")
            } ?: "default",
            customBackground = prefs.getInt(KEY_CUSTOM_BACKGROUND, 0xFF080B12.toInt()),
            customAccent = prefs.getInt(KEY_CUSTOM_ACCENT, 0xFF2F80FF.toInt()),
            customText = prefs.getInt(KEY_CUSTOM_TEXT, 0xFFF5F7FA.toInt()),
            customOpacity = prefs.getFloat(KEY_CUSTOM_OPACITY, 1f).coerceIn(0.4f, 1f)
        )
    }

    fun save(context: Context, settings: AppSettings) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, settings.language)
            .putInt(KEY_CONTROLLER_HINTS, settings.controllerHints)
            .putFloat(KEY_MUSIC_VOLUME, settings.musicVolume)
            .putFloat(KEY_HANDLE_OPACITY, settings.handleOpacity)
            .putBoolean(KEY_HANDLE_OUTSIDE_EDGE, settings.handleOutsideEdge)
            .putFloat(KEY_PANEL_OPACITY, settings.panelOpacity)
            .putBoolean(KEY_SHOW_DOCK, settings.showDock)
            .putFloat(KEY_SCALER_SHARPNESS, settings.scalerSharpness)
            .putBoolean(KEY_UPDATE_ALERTS, settings.updateAlerts)
            .putString(KEY_THEME_MODE, settings.themeMode)
            .putInt(KEY_CUSTOM_BACKGROUND, settings.customBackground)
            .putInt(KEY_CUSTOM_ACCENT, settings.customAccent)
            .putInt(KEY_CUSTOM_TEXT, settings.customText)
            .putFloat(KEY_CUSTOM_OPACITY, settings.customOpacity)
            .apply()
    }
}
