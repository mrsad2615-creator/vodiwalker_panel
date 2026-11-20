package dev.mrsad.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dev.mrsad.app.data.settings.GraphicsQuality
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.data.settings.ThemeVariant
import dev.mrsad.app.testing.MrsadTestHooks

/**
 * MRSAD semantic colour system.
 *
 * Identity rule: black + silver first; accent colours appear only where they
 * carry meaning (live data, focus, interactivity). Every screen reads from
 * these tokens — no hard-coded colours in feature code.
 */
@Immutable
data class MrsadColors(
    val background: Color,
    val backgroundElevated: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val glass: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val metalLight: Color,
    val metalMid: Color,
    val metalDark: Color,
    val accent: Color,
    val accentSoft: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val disabled: Color,
    val isLight: Boolean,
) {
    val metalGradient: List<Color> get() = listOf(metalLight, metalMid, metalDark)
}

// Brand constants (semantic system is built from these).
private val Obsidian = Color(0xFF08090C)
private val Graphite = Color(0xFF17191F)
private val GraphiteHigh = Color(0xFF1E2129)
private val Silver = Color(0xFFAEB4BE)
private val Pearl = Color(0xFFF5F6F8)
private val Chrome = Color(0xFFD8DDE5)
private val Violet = Color(0xFF8A75FF)
private val IceBlue = Color(0xFF5DC8E8)
private val Lavender = Color(0xFFC6A0FF)

private val Success = Color(0xFF6FD3A8)   // semantic only — never brand
private val Warning = Color(0xFFE8C06A)
private val Danger = Color(0xFFF1788C)

private val ObsidianColors = MrsadColors(
    background = Obsidian,
    backgroundElevated = Color(0xFF0B0D11),
    surface = Graphite,
    surfaceHigh = GraphiteHigh,
    glass = Color(0xCC17191F),
    border = Color(0x33AEB4BE),
    borderStrong = Color(0x66D8DDE5),
    textPrimary = Pearl,
    textSecondary = Silver,
    textMuted = Color(0xFF6E7480),
    metalLight = Pearl,
    metalMid = Chrome,
    metalDark = Silver,
    accent = Chrome,
    accentSoft = Color(0x268A75FF),
    success = Success,
    warning = Warning,
    error = Danger,
    disabled = Color(0xFF3A3E47),
    isLight = false,
)

private val QuantumColors = ObsidianColors.copy(
    background = Color(0xFF0A0812),
    backgroundElevated = Color(0xFF100C1C),
    surface = Color(0xFF191527),
    surfaceHigh = Color(0xFF221B36),
    accent = Violet,
    accentSoft = Color(0x338A75FF),
    metalLight = Color(0xFFEDE9FF),
    metalMid = Color(0xFFC9BEFF),
    metalDark = Lavender,
)

private val CrystalColors = MrsadColors(
    background = Color(0xFFF2F3F6),
    backgroundElevated = Color(0xFFF8F9FB),
    surface = Color(0xFFFDFDFE),
    surfaceHigh = Color(0xFFFFFFFF),
    glass = Color(0xE6FFFFFF),
    border = Color(0x22202A38),
    borderStrong = Color(0x44202A38),
    textPrimary = Color(0xFF12151B),
    textSecondary = Color(0xFF4C5260),
    textMuted = Color(0xFF8A909C),
    metalLight = Color(0xFF2A2F3A),
    metalMid = Color(0xFF4A5060),
    metalDark = Color(0xFF6E7484),
    accent = Color(0xFF3D6BFF),
    accentSoft = Color(0x1F3D6BFF),
    success = Color(0xFF1E9E6E),
    warning = Color(0xFFB07B18),
    error = Color(0xFFC4415A),
    disabled = Color(0xFFC9CDD6),
    isLight = true,
)

private val ArcticColors = ObsidianColors.copy(
    background = Color(0xFF060B10),
    backgroundElevated = Color(0xFF081018),
    surface = Color(0xFF0E1822),
    surfaceHigh = Color(0xFF14212E),
    accent = IceBlue,
    accentSoft = Color(0x265DC8E8),
    metalLight = Color(0xFFEAF7FC),
    metalMid = Color(0xFFB9E2F0),
    metalDark = Color(0xFF6FA9BF),
)

private val PhantomColors = ObsidianColors.copy(
    background = Color(0xFF07060A),
    backgroundElevated = Color(0xFF0B0910),
    surface = Color(0xFF120F19),
    surfaceHigh = Color(0xFF1A1524),
    accent = Color(0xFFB08CFF),
    accentSoft = Color(0x29B08CFF),
    metalLight = Color(0xFFE6DEF5),
    metalMid = Color(0xFFA99BC4),
    metalDark = Color(0xFF6E6288),
)

private val SpectrumColors = ObsidianColors.copy(
    background = Color(0xFF07080B),
    backgroundElevated = Color(0xFF0A0C11),
    surface = Color(0xFF12141B),
    surfaceHigh = Color(0xFF1B1E28),
    accent = Color(0xFF9BB6FF),
    accentSoft = Color(0x339BB6FF),
    metalLight = Color(0xFFF2F2FF),
    metalMid = Color(0xFFC9D4F5),
    metalDark = Color(0xFF8E9AC0),
)

fun colorsFor(variant: ThemeVariant): MrsadColors = when (variant) {
    ThemeVariant.OBSIDIAN -> ObsidianColors
    ThemeVariant.QUANTUM -> QuantumColors
    ThemeVariant.CRYSTAL -> CrystalColors
    ThemeVariant.ARCTIC -> ArcticColors
    ThemeVariant.PHANTOM -> PhantomColors
    ThemeVariant.SPECTRUM -> SpectrumColors
}

/** Motion/effects budget derived from user settings + quality. */
@Immutable
data class MrsadMotion(
    val animationsEnabled: Boolean,
    val particleCount: Int,
    val animationScale: Float,
    val quality: GraphicsQuality,
) {
    companion object {
        /**
         * Derives the motion budget from the user's settings.
         *
         * [MrsadTestHooks.isDeterministic] collapses the budget to "no motion
         * at all" so instrumented tests never wait on an infinite transition.
         */
        fun of(settings: MrsadSettings, deterministic: Boolean = MrsadTestHooks.isDeterministic) = MrsadMotion(
            animationsEnabled = settings.animationsEnabled && !deterministic,
            particleCount = if (deterministic) 0 else settings.particleCount,
            animationScale = if (settings.reduceMotion) 0.6f else 1f,
            quality = settings.quality,
        )
    }
}

val LocalMrsadColors = staticCompositionLocalOf { ObsidianColors }
val LocalMrsadTypography = staticCompositionLocalOf<MrsadTypography> { MrsadTypography.default }
val LocalMrsadMotion = staticCompositionLocalOf {
    MrsadMotion(animationsEnabled = true, particleCount = 45, animationScale = 1f, quality = GraphicsQuality.BALANCED)
}

object MrsadTheme {
    val colors: MrsadColors
        @Composable @ReadOnlyComposable get() = LocalMrsadColors.current

    val type: MrsadTypography
        @Composable @ReadOnlyComposable get() = LocalMrsadTypography.current

    val motion: MrsadMotion
        @Composable @ReadOnlyComposable get() = LocalMrsadMotion.current
}

@Composable
fun MrsadTheme(settings: MrsadSettings, content: @Composable () -> Unit) {
    val colors = colorsFor(settings.theme)
    val typography = MrsadTypography.remember(fontScale = settings.fontScale, readingFont = false)
    // Observed here so that switching the deterministic-test hook rebuilds the
    // whole motion budget and disposes any running infinite transition.
    val deterministic = MrsadTestHooks.isDeterministic
    val motion = MrsadMotion.of(settings, deterministic)

    val materialScheme = if (colors.isLight) {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceHigh,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.borderStrong,
            error = colors.error,
        )
    } else {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = Obsidian,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceHigh,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.borderStrong,
            error = colors.error,
        )
    }

    CompositionLocalProvider(
        LocalMrsadColors provides colors,
        LocalMrsadTypography provides typography,
        LocalMrsadMotion provides motion,
    ) {
        MaterialTheme(colorScheme = materialScheme, content = content)
    }
}
