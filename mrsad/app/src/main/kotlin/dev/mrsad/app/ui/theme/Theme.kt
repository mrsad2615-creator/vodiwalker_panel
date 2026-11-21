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
 * MRSAD Dual-Theme Semantic Colour System (OBSIDIAN × NOIR).
 *
 * - **THEME 01 — OBSIDIAN**: Luxury / 3D / Premium / Cinematic.
 *   Obsidian Black (`#07080B`), Deep Graphite (`#11151D`), Metallic Silver
 *   (`#E2E7F0` / `#9BA4B4`), Platinum White (`#F3F5F8`), Soft Grey (`#B4BCCB`),
 *   and Controlled Accent Light (`#C8D8EA`). No neon or unrelated rainbow hues.
 *
 * - **THEME 02 — NOIR**: Pure Minimalism / Intelligence / Swiss Editorial / Monochrome.
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
    val metalGradient: List<Color> get() = listOf(metalLight, metalMid, metalDark)
}

/**
 * Structural & visual tokens that separate the two independent design systems
 * across cards, icons, 3D sculptures, charts, and navigation (Master Prompt §3).
 */
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
    useChamferedCards = true,
    useLinearIcons = false,
    useFloatingDock = true,
    useSwissGridBackground = false,
    cardCornerDp = 16f,
)

private val NoirSpec = MrsadDesignSpec(
    variant = ThemeVariant.NOIR,
    use3DSculptures = false,
    useChamferedCards = false,
    useLinearIcons = true,
    useFloatingDock = false,
    useSwissGridBackground = true,
    cardCornerDp = 2f,
)

// ---- THEME 01: OBSIDIAN (Luxury / 3D / Premium / Cinematic) ----------------
private val ObsidianBlack = Color(0xFF07080B)
private val DeepGraphiteLow = Color(0xFF0D1016)
private val DeepGraphite = Color(0xFF11151D)
private val DeepGraphiteHigh = Color(0xFF181D27)
private val PlatinumWhite = Color(0xFFF3F5F8)
private val MetallicSilverLight = Color(0xFFE2E7F0)
private val MetallicSilver = Color(0xFFB4BCCB)
private val SoftGrey = Color(0xFF8C95A6)
private val BrushedTitaniumDark = Color(0xFF525A68)
private val AccentColdLight = Color(0xFFC8D8EA)

private val ObsidianColors = MrsadColors(
    variant = ThemeVariant.OBSIDIAN,
    background = ObsidianBlack,
    backgroundElevated = DeepGraphiteLow,
    surface = DeepGraphite,
    surfaceHigh = DeepGraphiteHigh,
    glass = Color(0xD911151D),
    border = Color(0x30C8D8EA),
    borderStrong = Color(0xFF5E687A),
    textPrimary = PlatinumWhite,
    textSecondary = MetallicSilver,
    textMuted = SoftGrey,
    metalLight = MetallicSilverLight,
    metalMid = MetallicSilver,
    metalDark = BrushedTitaniumDark,
    navy = DeepGraphiteLow,
    iceBlue = AccentColdLight,
    electricBlue = AccentColdLight,
    violet = MetallicSilver,
    accent = AccentColdLight,
    accentSoft = Color(0x24C8D8EA),
    success = Color(0xFF5CD6A0),
    warning = Color(0xFFF0B85A),
    error = Color(0xFFF27474),
    disabled = Color(0xFF343A46),
    isLight = false,
)

// ---- THEME 02: NOIR (Pure Minimalism / Swiss Editorial / Monochrome) -------
private val PureBlack = Color(0xFF000000)
private val SoftBlack = Color(0xFF0A0A0A)
private val Charcoal = Color(0xFF121212)
private val CharcoalHigh = Color(0xFF1A1A1A)
private val PureWhite = Color(0xFFFFFFFF)
private val SilverGrey = Color(0xFFC4C4CC)
private val NeutralGreyText = Color(0xFF909098)
private val NeutralGreyLine = Color(0xFF27272A)
private val NeutralGreyStrong = Color(0xFF52525B)

private val NoirColors = MrsadColors(
    variant = ThemeVariant.NOIR,
    background = PureBlack,
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
    accentSoft = Color(0x1FFFFFFF),
    success = Color(0xFFA8E6CF),
    warning = Color(0xFFF5D08A),
    error = Color(0xFFF59898),
    disabled = Color(0xFF27272A),
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
