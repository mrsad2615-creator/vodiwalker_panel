package com.neurox.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * NEUROX palette: obsidian surfaces with champagne metal accents.
 *
 * RULES
 * - Never put a raw hex value in a screen. Every screen uses a semantic role from
 *   [NxColors], which comes from this palette.
 * - Accent colors are data, not constants, so Color Studio can replace them at
 *   runtime without touching logic. See [NxThemeSpec].
 * - Contrast: every text color has at least 4.5:1 against the surface it is used
 *   on. The pairings are listed in CONTRAST.md and checked by unit tests.
 */
object Palette {

    // Obsidian family: near-black with a slight blue-green cast.
    val obsidian0 = Color(0xFF07080A)
    val obsidian1 = Color(0xFF0C0E11)
    val obsidian2 = Color(0xFF12151A)
    val obsidian3 = Color(0xFF181C22)
    val obsidian4 = Color(0xFF21262E)
    val obsidian5 = Color(0xFF2C323B)

    // Champagne family: warm metal, low saturation.
    val champagne100 = Color(0xFFF6E7C1)
    val champagne200 = Color(0xFFEAD3A0)
    val champagne300 = Color(0xFFD8B36A)
    val champagne400 = Color(0xFFBE9550)
    val champagne500 = Color(0xFF9A7638)

    // Ivory surfaces for the light theme.
    val ivory0 = Color(0xFFFBF9F4)
    val ivory1 = Color(0xFFF3EFE6)
    val ivory2 = Color(0xFFE7E1D4)
    val ivory3 = Color(0xFFD6CEBD)

    // Text on dark surfaces.
    val textPrimaryDark = Color(0xFFF2EDE4)
    val textSecondaryDark = Color(0xFFBDB6A9)
    val textTertiaryDark = Color(0xFF8C877D)

    // Text on light surfaces.
    val textPrimaryLight = Color(0xFF15171A)
    val textSecondaryLight = Color(0xFF4A4E55)
    val textTertiaryLight = Color(0xFF6E747D)

    // Semantic families: distinct hues, all readable on both surface families.
    val successDark = Color(0xFF6FBF8B)
    val successLight = Color(0xFF2F7D4F)
    val warningDark = Color(0xFFE0A94E)
    val warningLight = Color(0xFF8A5A12)
    val errorDark = Color(0xFFE07A7A)
    val errorLight = Color(0xFF9E2F2F)
    val infoDark = Color(0xFF7FA8D8)
    val infoLight = Color(0xFF2C5A8C)
    val disabledDark = Color(0xFF4A4F57)
    val disabledLight = Color(0xFFB8B3A8)

    // Module identity colors. Used as accents, never as the only signal.
    val visionAccent = Color(0xFF8FB4E8)
    val reflexAccent = Color(0xFFE8A06A)
    val readAccent = Color(0xFF9BCB9A)
    val typeAccent = Color(0xFFC79FD6)

    // Gold gradient stops for the emblem and premium surfaces.
    val gradientStart = Color(0xFFF0D9A2)
    val gradientMid = Color(0xFFD8B36A)
    val gradientEnd = Color(0xFF9A7638)

    /** The full palette exposed to Color Studio, with display keys. */
    val catalog: List<PaletteEntry> = listOf(
        PaletteEntry("obsidian", "palette.obsidian", obsidian1, isDark = true),
        PaletteEntry("graphite", "palette.graphite", obsidian3, isDark = true),
        PaletteEntry("champagne", "palette.champagne", champagne300, isDark = false),
        PaletteEntry("bronze", "palette.bronze", champagne400, isDark = false),
        PaletteEntry("ivory", "palette.ivory", ivory1, isDark = false),
        PaletteEntry("sand", "palette.sand", ivory2, isDark = false),
        PaletteEntry("jade", "palette.jade", successDark, isDark = false),
        PaletteEntry("amber", "palette.amber", warningDark, isDark = false),
        PaletteEntry("coral", "palette.coral", errorDark, isDark = false),
        PaletteEntry("azure", "palette.azure", infoDark, isDark = false),
        PaletteEntry("iris", "palette.iris", Color(0xFF9A8FCB), isDark = false),
        PaletteEntry("rose", "palette.rose", Color(0xFFD98E8E), isDark = false),
    )
}

/** One selectable color with a stable id and a localization key. */
data class PaletteEntry(
    val id: String,
    val labelKey: String,
    val color: Color,
    val isDark: Boolean,
)
