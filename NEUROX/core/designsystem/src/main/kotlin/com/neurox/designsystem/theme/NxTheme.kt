package com.neurox.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * A complete theme description. This is the unit that Color Studio edits and that
 * the profile stores.
 *
 * [id] is stable and persisted; [name] is user-editable; [accentArgb] and
 * [secondaryArgb] are ARGB values, so a theme survives a JSON round trip exactly.
 */
@Immutable
data class NxThemeSpec(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean,
    val accentArgb: Long,
    val secondaryArgb: Long,
    val lightSurfaces: Boolean,
) {
    fun withColors(accent: Long, secondary: Long): NxThemeSpec =
        copy(accentArgb = accent, secondaryArgb = secondary)

    companion object {
        /** The signature NEUROX look: obsidian surfaces, champagne accent. */
        val OBSIDIAN = NxThemeSpec(
            id = "neurox.obsidian",
            name = "Obsidian Gold",
            isBuiltIn = true,
            accentArgb = 0xFFD8B36A,
            secondaryArgb = 0xFFF6E7C1,
            lightSurfaces = false,
        )

        /** Ivory surfaces for daylight use. */
        val IVORY = NxThemeSpec(
            id = "neurox.ivory",
            name = "Ivory Gold",
            isBuiltIn = true,
            accentArgb = 0xFFBE9550,
            secondaryArgb = 0xFF9A7638,
            lightSurfaces = true,
        )

        /** Deep contrast theme for accessibility. */
        val HIGH_CONTRAST = NxThemeSpec(
            id = "neurox.contrast",
            name = "High Contrast",
            isBuiltIn = true,
            accentArgb = 0xFFFFD98A,
            secondaryArgb = 0xFFFFFFFF,
            lightSurfaces = false,
        )

        val builtIns: List<NxThemeSpec> = listOf(OBSIDIAN, IVORY, HIGH_CONTRAST)

        fun byId(id: String): NxThemeSpec = builtIns.firstOrNull { it.id == id } ?: OBSIDIAN
    }
}

/**
 * Semantic colors resolved for a theme. Screens use these roles only.
 */
@Immutable
data class NxColors(
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceSunken: Color,
    val surfaceInverse: Color,
    val accent: Color,
    val accentMuted: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnAccent: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
    val disabled: Color,
    val border: Color,
    val borderStrong: Color,
    val scrim: Color,
    val vision: Color,
    val reflex: Color,
    val read: Color,
    val type: Color,
) {
    /** Module accent, used for identity only; status uses semantic colors. */
    fun module(moduleId: String): Color = when (moduleId) {
        "vision" -> vision
        "reflex" -> reflex
        "read" -> read
        else -> type
    }
}

/** Resolves a [NxThemeSpec] into concrete colors. Pure and testable. */
object NxColorResolver {

    fun resolve(spec: NxThemeSpec): NxColors {
        val accent = Color(spec.accentArgb)
        val secondary = Color(spec.secondaryArgb)
        return if (spec.lightSurfaces) {
            NxColors(
                surface = Palette.ivory0,
                surfaceElevated = Palette.ivory1,
                surfaceSunken = Palette.ivory2,
                surfaceInverse = Palette.obsidian2,
                accent = accent,
                accentMuted = secondary,
                onAccent = Palette.obsidian1,
                textPrimary = Palette.textPrimaryLight,
                textSecondary = Palette.textSecondaryLight,
                textTertiary = Palette.textTertiaryLight,
                textOnAccent = Palette.obsidian1,
                success = Palette.successLight,
                warning = Palette.warningLight,
                error = Palette.errorLight,
                info = Palette.infoLight,
                disabled = Palette.disabledLight,
                border = Palette.ivory3,
                borderStrong = Palette.textTertiaryLight,
                scrim = Color(0x6615171A),
                vision = Palette.visionAccent,
                reflex = Palette.reflexAccent,
                read = Palette.readAccent,
                type = Palette.typeAccent,
            )
        } else {
            NxColors(
                surface = Palette.obsidian1,
                surfaceElevated = Palette.obsidian3,
                surfaceSunken = Palette.obsidian0,
                surfaceInverse = Palette.ivory1,
                accent = accent,
                accentMuted = secondary,
                onAccent = Palette.obsidian0,
                textPrimary = Palette.textPrimaryDark,
                textSecondary = Palette.textSecondaryDark,
                textTertiary = Palette.textTertiaryDark,
                textOnAccent = Palette.obsidian0,
                success = Palette.successDark,
                warning = Palette.warningDark,
                error = Palette.errorDark,
                info = Palette.infoDark,
                disabled = Palette.disabledDark,
                border = Palette.obsidian4,
                borderStrong = Palette.obsidian5,
                scrim = Color(0x9907080A),
                vision = Palette.visionAccent,
                reflex = Palette.reflexAccent,
                read = Palette.readAccent,
                type = Palette.typeAccent,
            )
        }
    }

    /**
     * Relative luminance as defined by WCAG 2.x.
     * Used to pick a readable foreground and to verify contrast in tests.
     */
    fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    /** WCAG contrast ratio between two colors, from 1.0 to 21.0. */
    fun contrastRatio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** True when the pair meets WCAG AA for normal text. */
    fun meetsAa(text: Color, background: Color): Boolean =
        contrastRatio(text, background) >= 4.5
}

/**
 * Extended theme values that Material 3 does not model: the whole semantic palette,
 * the motion mode and the layout class.
 */
@Immutable
data class NxExtendedTheme(
    val colors: NxColors,
    val spec: NxThemeSpec,
    val motionMode: com.neurox.designsystem.tokens.NxMotion.MotionMode,
    val reducedMotion: Boolean,
    val fontScale: Float,
) {
    val animate: Boolean
        get() = !reducedMotion && motionMode != com.neurox.designsystem.tokens.NxMotion.MotionMode.PERFORMANCE

    fun duration(baseMillis: Int): Int =
        if (!animate) 0 else com.neurox.designsystem.tokens.NxMotion.durationMillis(baseMillis, motionMode)
}

val LocalNxTheme = staticCompositionLocalOf {
    NxExtendedTheme(
        colors = NxColorResolver.resolve(NxThemeSpec.OBSIDIAN),
        spec = NxThemeSpec.OBSIDIAN,
        motionMode = com.neurox.designsystem.tokens.NxMotion.MotionMode.BALANCED,
        reducedMotion = false,
        fontScale = 1f,
    )
}

/** Convenience accessor used by every screen. */
object NxTheme {
    val colors: NxColors
        @Composable get() = LocalNxTheme.current.colors

    val spec: NxThemeSpec
        @Composable get() = LocalNxTheme.current.spec

    val extended: NxExtendedTheme
        @Composable get() = LocalNxTheme.current
}

/**
 * The root theme. Material 3 provides components; [NxTheme] provides identity.
 */
@Composable
fun NeuroxTheme(
    spec: NxThemeSpec = NxThemeSpec.OBSIDIAN,
    motionMode: com.neurox.designsystem.tokens.NxMotion.MotionMode =
        com.neurox.designsystem.tokens.NxMotion.MotionMode.BALANCED,
    reducedMotion: Boolean = false,
    rtl: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = NxColorResolver.resolve(spec)
    val material = if (spec.lightSurfaces) {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.accentMuted,
            background = colors.surface,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceElevated,
            onSurfaceVariant = colors.textSecondary,
            error = colors.error,
            outline = colors.border,
        )
    } else {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.accentMuted,
            background = colors.surface,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceElevated,
            onSurfaceVariant = colors.textSecondary,
            error = colors.error,
            outline = colors.border,
        )
    }
    val extended = NxExtendedTheme(
        colors = colors,
        spec = spec,
        motionMode = motionMode,
        reducedMotion = reducedMotion,
        fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale,
    )
    CompositionLocalProvider(
        LocalNxTheme provides extended,
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        MaterialTheme(
            colorScheme = material,
            typography = NxTypography.materialTypography(spec.lightSurfaces),
            content = content,
        )
    }
}
