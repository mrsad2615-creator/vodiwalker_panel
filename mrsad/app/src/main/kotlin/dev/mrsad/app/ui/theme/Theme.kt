package dev.mrsad.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.mrsad.app.data.settings.GraphicsQuality
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.data.settings.ThemeVariant
import dev.mrsad.app.testing.MrsadTestHooks

/**
 * MRSAD Minimal OLED Black & Monochrome Frosted Glass Colour System.
 *
 * - **THEME 01 — OBSIDIAN (OLED Frosted Glass Monochrome)**:
 *   Pure OLED Black (`#000000`), Translucent Dark Glass (`#0D0D10` / `#151519`),
 *   Pure White (`#FFFFFF`), Luminous Silver (`#D4D4D8`), Neutral Grey (`#9E9EA8`),
 *   and Pure Monochrome White-Silver Accent (`#F4F4F6`). Zero blue/neon tint.
 *
 * - **THEME 02 — NOIR (Pure Minimalism / Swiss Editorial Monochrome)**:
 *   Pure Black (`#000000`), Soft Black (`#0A0A0A`), Charcoal (`#121212`),
 *   Pure White (`#FFFFFF`), Silver Grey (`#C4C4CC`), Neutral Grey (`#909098`, `#27272A`).
 */
@Immutable
data class MrsadColors(
    val variant: ThemeVariant,
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
    val navy: Color,
    val iceBlue: Color,
    val electricBlue: Color,
    val violet: Color,
    val accent: Color,
    val accentSoft: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val disabled: Color,
    val isLight: Boolean,
) {
    val isNoir: Boolean get() = variant == ThemeVariant.NOIR
    val isObsidian: Boolean get() = variant == ThemeVariant.OBSIDIAN
    val metalGradient: List<Color> get() = listOf(metalLight, metalMid)
}

@Immutable
data class MrsadDesignSpec(
    val variant: ThemeVariant,
    val use3DSculptures: Boolean,
    val useChamferedCards: Boolean,
    val useLinearIcons: Boolean,
    val useFloatingDock: Boolean,
    val useSwissGridBackground: Boolean,
    val cardCornerDp: Float,
)

private val ObsidianSpec = MrsadDesignSpec(
    variant = ThemeVariant.OBSIDIAN,
    use3DSculptures = true,
    useChamferedCards = false,
    useLinearIcons = true,
    useFloatingDock = true,
    useSwissGridBackground = false,
    cardCornerDp = 22f,
)

private val NoirSpec = MrsadDesignSpec(
    variant = ThemeVariant.NOIR,
    use3DSculptures = false,
    useChamferedCards = false,
    useLinearIcons = true,
    useFloatingDock = false,
    useSwissGridBackground = true,
    cardCornerDp = 12f,
)

// ---- THEME 01: OBSIDIAN (OLED Pitch Black + Monochrome Frosted Glass) ------
private val OledPitchBlack = Color(0xFF000000)
private val OledGlassLow = Color(0xFF070709)
private val OledGlassSurface = Color(0xFF0E0E12)
private val OledGlassHigh = Color(0xFF16161C)
private val PureWhite = Color(0xFFFFFFFF)
private val FrostedSilverLight = Color(0xFFF4F4F6)
private val FrostedSilver = Color(0xFFD4D4D8)
private val FrostedMutedGrey = Color(0xFF9E9EA8)
private val FrostedDarkRim = Color(0xFF52525B)

private val ObsidianColors = MrsadColors(
    variant = ThemeVariant.OBSIDIAN,
    background = OledPitchBlack,
    backgroundElevated = OledGlassLow,
    surface = OledGlassSurface,
    surfaceHigh = OledGlassHigh,
    glass = Color(0xD90E0E12),
    border = Color(0x2EFFFFFF),
    borderStrong = Color(0xFF5A5A64),
    textPrimary = PureWhite,
    textSecondary = FrostedSilver,
    textMuted = FrostedMutedGrey,
    metalLight = PureWhite,
    metalMid = FrostedSilver,
    metalDark = FrostedDarkRim,
    navy = OledGlassLow,
    iceBlue = FrostedSilverLight,
    electricBlue = FrostedSilverLight,
    violet = FrostedSilver,
    accent = FrostedSilverLight,
    accentSoft = Color(0x1FFFFFFF),
    success = Color(0xFFD4D4D8),
    warning = Color(0xFFE4E4E7),
    error = Color(0xFFF27474),
    disabled = Color(0xFF27272A),
    isLight = false,
)

// ---- THEME 02: NOIR (Pure Minimalism / Swiss Editorial / Monochrome) -------
private val SoftBlack = Color(0xFF080808)
private val Charcoal = Color(0xFF101012)
private val CharcoalHigh = Color(0xFF18181B)
private val SilverGrey = Color(0xFFC4C4CC)
private val NeutralGreyText = Color(0xFF909098)
private val NeutralGreyLine = Color(0xFF27272A)
private val NeutralGreyStrong = Color(0xFF52525B)

private val NoirColors = MrsadColors(
    variant = ThemeVariant.NOIR,
    background = OledPitchBlack,
    backgroundElevated = SoftBlack,
    surface = Charcoal,
    surfaceHigh = CharcoalHigh,
    glass = Charcoal,
    border = NeutralGreyLine,
    borderStrong = NeutralGreyStrong,
    textPrimary = PureWhite,
    textSecondary = SilverGrey,
    textMuted = NeutralGreyText,
    metalLight = PureWhite,
    metalMid = SilverGrey,
    metalDark = NeutralGreyStrong,
    navy = SoftBlack,
    iceBlue = PureWhite,
    electricBlue = PureWhite,
    violet = SilverGrey,
    accent = PureWhite,
    accentSoft = Color(0x1AFFFFFF),
    success = PureWhite,
    warning = SilverGrey,
    error = Color(0xFFE4E4E7),
    disabled = NeutralGreyLine,
    isLight = false,
)

fun colorsFor(variant: ThemeVariant): MrsadColors = when (variant) {
    ThemeVariant.OBSIDIAN -> ObsidianColors
    ThemeVariant.NOIR -> NoirColors
}

fun specFor(variant: ThemeVariant): MrsadDesignSpec = when (variant) {
    ThemeVariant.OBSIDIAN -> ObsidianSpec
    ThemeVariant.NOIR -> NoirSpec
}

val LocalMrsadColors = staticCompositionLocalOf { ObsidianColors }
val LocalMrsadSpec = staticCompositionLocalOf { ObsidianSpec }
val LocalMrsadTypography = staticCompositionLocalOf<MrsadTypography> { MrsadTypography.default }
val LocalMrsadMotion = staticCompositionLocalOf {
    MrsadMotion(
        animationsEnabled = true,
        particleCount = 40,
        animationScale = 1f,
        quality = GraphicsQuality.BALANCED,
    )
}

object MrsadTheme {
    val colors: MrsadColors
        @Composable @ReadOnlyComposable get() = LocalMrsadColors.current

    val spec: MrsadDesignSpec
        @Composable @ReadOnlyComposable get() = LocalMrsadSpec.current

    val type: MrsadTypography
        @Composable @ReadOnlyComposable get() = LocalMrsadTypography.current

    val motion: MrsadMotion
        @Composable @ReadOnlyComposable get() = LocalMrsadMotion.current
}

@Composable
fun MrsadTheme(settings: MrsadSettings, content: @Composable () -> Unit) {
    val colors = colorsFor(settings.theme)
    val spec = specFor(settings.theme)
    val typography = MrsadTypography.remember(fontScale = settings.fontScale, readingFont = false)
    val deterministic = MrsadTestHooks.isDeterministic
    val motion = MrsadMotion.of(settings, deterministic)

    val materialScheme = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.background,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceHigh,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.borderStrong,
        error = colors.error,
    )

    CompositionLocalProvider(
        LocalMrsadColors provides colors,
        LocalMrsadSpec provides spec,
        LocalMrsadTypography provides typography,
        LocalMrsadMotion provides motion,
    ) {
        MaterialTheme(colorScheme = materialScheme, content = content)
    }
}
