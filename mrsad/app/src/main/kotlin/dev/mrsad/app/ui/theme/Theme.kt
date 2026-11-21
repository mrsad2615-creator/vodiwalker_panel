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
 * OBSIDIAN SPECTRUM — Global Color Architecture & Module Spectral Identities
 * (Master Prompt §2, §3 & §4).
 *
 * Foundation colors:
 * - Obsidian Black: `#090810`
 * - Deep Black: `#100E18`
 * - Dark Surface: `#181522`
 * - Elevated Surface: `#211B2C`
 * - Metallic Gray: `#96909F`
 * - Soft White: `#F5F2FA`
 *
 * Spectral colors:
 * - MODULE 01 (TYPING): Electric Blue `#176ED4` & Bright Blue `#54B8FF`
 * - MODULE 02 (READING): Deep Violet `#7040B9` & Lavender `#C69CFF`
 * - MODULE 03 (EYE TRAINING): Teal `#138F91` & Aqua `#67E7DA`
 * - MODULE 04 (BRAIN TRAINING): Magenta `#B53D91` & Pink `#F18ACD`
 */
object ObsidianSpectrum {
    // Foundation
    val ObsidianBlack = Color(0xFF090810)
    val DeepBlack = Color(0xFF100E18)
    val DarkSurface = Color(0xFF181522)
    val ElevatedSurface = Color(0xFF211B2C)
    val MetallicGray = Color(0xFF96909F)
    val SoftSilver = Color(0xFFD0C9DE)
    val SoftWhite = Color(0xFFF5F2FA)

    // Module 01 — Typing (Electric Blue & Bright Blue)
    val ElectricBlue = Color(0xFF176ED4)
    val BrightBlue = Color(0xFF54B8FF)

    // Module 02 — Reading (Deep Violet & Lavender)
    val DeepViolet = Color(0xFF7040B9)
    val Lavender = Color(0xFFC69CFF)

    // Module 03 — Eye Training (Teal & Aqua)
    val Teal = Color(0xFF138F91)
    val Aqua = Color(0xFF67E7DA)

    // Module 04 — Brain Training (Magenta & Pink)
    val Magenta = Color(0xFFB53D91)
    val Pink = Color(0xFFF18ACD)
}

/**
 * Visual identity token pair for each of the four core modules (Master Prompt §4).
 */
@Immutable
data class ModuleSpectralIdentity(
    val primary: Color,
    val luminous: Color,
    val softGlow: Color,
    val borderGlow: Color,
)

object ModulePalettes {
    val Typing = ModuleSpectralIdentity(
        primary = ObsidianSpectrum.ElectricBlue,
        luminous = ObsidianSpectrum.BrightBlue,
        softGlow = ObsidianSpectrum.ElectricBlue.copy(alpha = 0.20f),
        borderGlow = ObsidianSpectrum.BrightBlue.copy(alpha = 0.48f),
    )
    val Reading = ModuleSpectralIdentity(
        primary = ObsidianSpectrum.DeepViolet,
        luminous = ObsidianSpectrum.Lavender,
        softGlow = ObsidianSpectrum.DeepViolet.copy(alpha = 0.22f),
        borderGlow = ObsidianSpectrum.Lavender.copy(alpha = 0.48f),
    )
    val Eye = ModuleSpectralIdentity(
        primary = ObsidianSpectrum.Teal,
        luminous = ObsidianSpectrum.Aqua,
        softGlow = ObsidianSpectrum.Teal.copy(alpha = 0.20f),
        borderGlow = ObsidianSpectrum.Aqua.copy(alpha = 0.48f),
    )
    val Brain = ModuleSpectralIdentity(
        primary = ObsidianSpectrum.Magenta,
        luminous = ObsidianSpectrum.Pink,
        softGlow = ObsidianSpectrum.Magenta.copy(alpha = 0.20f),
        borderGlow = ObsidianSpectrum.Pink.copy(alpha = 0.48f),
    )
}

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
    useChamferedCards = true,
    useLinearIcons = false,
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
    cardCornerDp = 18f,
)

// ---- Unified OBSIDIAN SPECTRUM Foundation ----------------------------------
private val ObsidianColors = MrsadColors(
    variant = ThemeVariant.OBSIDIAN,
    background = ObsidianSpectrum.ObsidianBlack,          // #090810
    backgroundElevated = ObsidianSpectrum.DeepBlack,      // #100E18
    surface = ObsidianSpectrum.DarkSurface,               // #181522
    surfaceHigh = ObsidianSpectrum.ElevatedSurface,       // #211B2C
    glass = Color(0xE6181522),
    border = Color(0xFF332C42),
    borderStrong = Color(0xFF665C7A),
    textPrimary = ObsidianSpectrum.SoftWhite,             // #F5F2FA
    textSecondary = ObsidianSpectrum.SoftSilver,          // #D0C9DE
    textMuted = ObsidianSpectrum.MetallicGray,            // #96909F
    metalLight = ObsidianSpectrum.SoftWhite,              // #F5F2FA
    metalMid = ObsidianSpectrum.Lavender,                 // #C69CFF
    metalDark = Color(0xFF5A526B),
    navy = ObsidianSpectrum.ElectricBlue,                 // #176ED4
    iceBlue = ObsidianSpectrum.Aqua,                      // #67E7DA
    electricBlue = ObsidianSpectrum.BrightBlue,           // #54B8FF
    violet = ObsidianSpectrum.Lavender,                   // #C69CFF
    accent = ObsidianSpectrum.BrightBlue,                 // #54B8FF
    accentSoft = Color(0x2854B8FF),
    success = ObsidianSpectrum.Aqua,                      // #67E7DA
    warning = ObsidianSpectrum.Lavender,                  // #C69CFF
    error = ObsidianSpectrum.Pink,                        // #F18ACD
    disabled = Color(0xFF2D273A),
    isLight = false,
)

// Secondary variant shares the exact same unified Obsidian foundation so
// switching themes never breaks visual consistency or introduces noise.
private val NoirColors = MrsadColors(
    variant = ThemeVariant.NOIR,
    background = ObsidianSpectrum.ObsidianBlack,          // #090810
    backgroundElevated = ObsidianSpectrum.DeepBlack,      // #100E18
    surface = ObsidianSpectrum.DarkSurface,               // #181522
    surfaceHigh = ObsidianSpectrum.ElevatedSurface,       // #211B2C
    glass = Color(0xE6181522),
    border = Color(0xFF332C42),
    borderStrong = Color(0xFF665C7A),
    textPrimary = ObsidianSpectrum.SoftWhite,             // #F5F2FA
    textSecondary = ObsidianSpectrum.SoftSilver,          // #D0C9DE
    textMuted = ObsidianSpectrum.MetallicGray,            // #96909F
    metalLight = ObsidianSpectrum.SoftWhite,
    metalMid = ObsidianSpectrum.SoftSilver,
    metalDark = Color(0xFF5A526B),
    navy = ObsidianSpectrum.ElectricBlue,
    iceBlue = ObsidianSpectrum.Aqua,
    electricBlue = ObsidianSpectrum.BrightBlue,
    violet = ObsidianSpectrum.Lavender,
    accent = ObsidianSpectrum.Lavender,                   // #C69CFF
    accentSoft = Color(0x28C69CFF),
    success = ObsidianSpectrum.Aqua,                      // #67E7DA
    warning = ObsidianSpectrum.BrightBlue,                // #54B8FF
    error = ObsidianSpectrum.Pink,                        // #F18ACD
    disabled = Color(0xFF2D273A),
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
