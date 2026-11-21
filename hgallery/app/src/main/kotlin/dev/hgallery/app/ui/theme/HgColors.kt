package dev.hgallery.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The palette, transcribed from measured reference pixels.
 *
 * Values in `light` come from `docs/DESIGN_SYSTEM.md`, which was built by
 * sampling official Huawei captures — the accent `#0080FF` is the solid interior
 * of the `ADD ALBUM` link, not an approximation.
 */
@Immutable
data class HgPalette(
    val page: Color,
    val pageAlt: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentMuted: Color,
    val divider: Color,
    val scrim: Color,
    val viewerBackground: Color,
    val chipBackground: Color,
    val danger: Color,
    val isLight: Boolean,
)

val HgLightPalette = HgPalette(
    page = Color(0xFFFFFFFF),
    pageAlt = Color(0xFFFCFCFC),
    surface = Color(0xFFF4F4F5),
    textPrimary = Color(0xFF1A1A1A),
    textSecondary = Color(0xFF8C8C8C),
    textTertiary = Color(0xFFB0B0B0),
    accent = Color(0xFF0080FF),
    accentMuted = Color(0x220080FF),
    divider = Color(0xFFEDEDED),
    scrim = Color(0x66000000),
    viewerBackground = Color(0xFF000000),
    chipBackground = Color(0xFFF0F1F2),
    danger = Color(0xFFE64545),
    isLight = true,
)

val HgDarkPalette = HgPalette(
    page = Color(0xFF000000),
    pageAlt = Color(0xFF1C1C1E),
    surface = Color(0xFF232325),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8C8C8C),
    textTertiary = Color(0xFF5C5C5E),
    accent = Color(0xFF0A84FF),
    accentMuted = Color(0x330A84FF),
    divider = Color(0xFF2C2C2E),
    scrim = Color(0x99000000),
    viewerBackground = Color(0xFF000000),
    chipBackground = Color(0xFF2A2A2C),
    danger = Color(0xFFFF5A5A),
    isLight = false,
)

val LocalHgPalette = staticCompositionLocalOf { HgLightPalette }
