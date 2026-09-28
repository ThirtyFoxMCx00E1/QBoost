package com.example.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
    val textSecondary: Color,
    /** True when [background] is light, so screens can swap dark-only scrims/overlays for light ones. */
    val isLight: Boolean = false,
    /** Settings cards / big rounded containers sitting on [background]. */
    val panel: Color = Color(0x66121C30),
    /** Unselected chips and secondary buttons. */
    val chip: Color = Color(0xFF1B2438),
    /** Hairline dividers between rows. */
    val divider: Color = Color(0x14FFFFFF),
    /** The highlighted row in a list (e.g. the current Settings category). */
    val selectedRow: Color = Color(0x26FFFFFF),
    /** Frosted "glass" fill for small buttons/fields that sit right on the background. */
    val glass: Color = Color(0x33FFFFFF)
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

/**
 * A real light mode, modeled on the phone's own light quick-settings look: a cool blue-grey backdrop,
 * pure-white rounded cards, near-black text, and one vivid blue accent.
 */
val LightQboostColors = QboostColors(
    background = Color(0xFFE5EBF3),
    surface = Color(0xFFFFFFFF),
    accent = Color(0xFF0066FF),
    textPrimary = Color(0xFF0B0F14),
    textSecondary = Color(0xFF6B7480),
    isLight = true,
    panel = Color(0xFFFFFFFF),
    chip = Color(0xFFEEF2F8),
    divider = Color(0x14000000),
    selectedRow = Color(0x1F0066FF),
    glass = Color(0x14000000)
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
    ThemeMode.CUSTOM -> {
        val bg = Color(settings.customBackground)
        val light = bg.luminance() > 0.5f
        val accent = Color(settings.customAccent)
        // A slightly lighter card surface derived from the chosen background, so custom themes
        // still have visible card/background contrast without asking for a second color.
        val surface = Color(
            red = (bg.red + 0.08f).coerceAtMost(1f),
            green = (bg.green + 0.08f).coerceAtMost(1f),
            blue = (bg.blue + 0.08f).coerceAtMost(1f)
        )
        QboostColors(
            background = bg,
            surface = surface,
            accent = accent,
            textPrimary = Color(settings.customText),
            textSecondary = Color(settings.customText).copy(alpha = 0.6f),
            isLight = light,
            panel = if (light) Color(0xFFFFFFFF) else Color(0x66121C30),
            chip = if (light) Color(0x0F000000) else Color(0x1FFFFFFF),
            divider = if (light) Color(0x14000000) else Color(0x14FFFFFF),
            selectedRow = accent.copy(alpha = 0.16f),
            glass = if (light) Color(0x14000000) else Color(0x33FFFFFF)
        )
    }
    else -> DefaultQboostColors
}

val LocalQboostColors = compositionLocalOf { DefaultQboostColors }
