package com.example.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.settings.AppSettings

/**
 * The palette Qboost's shared chrome (backgrounds, cards, accent, text) draws itself with.
 * Read via [LocalQboostColors] instead of the static color `val`s above when a screen should follow
 * the user's Appearance choice in Settings.
 */
data class QboostColors(
    val background: Color,
    val surface: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

object ThemeMode {
    const val DEFAULT = "default"
    const val DARK = "dark"
    const val LIGHT = "light"
    const val CUSTOM = "custom"
    val all = listOf(DEFAULT, DARK, LIGHT, CUSTOM)
}

/** The look Qboost has always had. */
val DefaultQboostColors = QboostColors(
    background = CyberDarkBg,
    surface = CyberCardBg,
    accent = QboostBlue,
    textPrimary = TextWhite,
    textSecondary = TextGray
)

/** A deeper, pure-black dark mode. */
val DarkQboostColors = QboostColors(
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0A),
    accent = QboostBlue,
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA0A0A0)
)

/** A light mode - background and text flip; the blue accent stays for brand consistency. */
val LightQboostColors = QboostColors(
    background = Color(0xFFF5F7FA),
    surface = Color(0xFFFFFFFF),
    accent = QboostBlue,
    textPrimary = Color(0xFF10141C),
    textSecondary = Color(0xFF5A6472)
)

/** Small, safe preset swatches for the Customize pickers - not a full color wheel, but enough real choice. */
object ThemeSwatches {
    val backgrounds = listOf(
        Color(0xFF080B12), Color(0xFF000000), Color(0xFF0D1117),
        Color(0xFF1A1425), Color(0xFF141B12), Color(0xFFF5F7FA)
    )
    val accents = listOf(
        Color(0xFF2F80FF), Color(0xFF00E5FF), Color(0xFF00E676),
        Color(0xFFFFAB00), Color(0xFFFF5C7A), Color(0xFFB388FF)
    )
    val text = listOf(
        Color(0xFFF5F7FA), Color(0xFF10141C), Color(0xFFFFFFFF), Color(0xFF000000)
    )
}

/** Resolves the settings' theme choice into the palette that should actually be drawn. */
fun resolveQboostColors(settings: AppSettings): QboostColors = when (settings.themeMode) {
    ThemeMode.DARK -> DarkQboostColors
    ThemeMode.LIGHT -> LightQboostColors
    ThemeMode.CUSTOM -> QboostColors(
        background = Color(settings.customBackground),
        surface = Color(settings.customBackground).let { bg ->
            // A slightly lighter card surface derived from the chosen background, so custom themes
            // still have visible card/background contrast without asking for a second color.
            Color(
                red = (bg.red + 0.08f).coerceAtMost(1f),
                green = (bg.green + 0.08f).coerceAtMost(1f),
                blue = (bg.blue + 0.08f).coerceAtMost(1f)
            )
        },
        accent = Color(settings.customAccent),
        textPrimary = Color(settings.customText),
        textSecondary = Color(settings.customText).copy(alpha = 0.6f)
    )
    else -> DefaultQboostColors
}

val LocalQboostColors = compositionLocalOf { DefaultQboostColors }
