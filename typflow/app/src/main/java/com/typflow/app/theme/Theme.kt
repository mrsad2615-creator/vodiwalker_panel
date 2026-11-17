package com.typflow.app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * TYPFLOW design tokens — a strictly monochrome, raw editorial system.
 * Every theme is black & white only; the difference is contrast, grid and type.
 */
data class TypflowTheme(
    val id: String,
    val name: String,
    val nameFa: String,
    val descriptionFa: String,
    val descriptionEn: String,
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val dim: Color,
    val line: Color,
    val grid: Color,
    val light: Boolean,
    val mono: Boolean,
    val gridStep: Int,
    val radius: Dp
) {
    val primary: Color get() = ink
    val secondary: Color get() = dim
    val accent: Color get() = ink
    val onAccent: Color get() = bg
    val background: Color get() = bg
    val surfaceVariant: Color get() = surface
    val onBackground: Color get() = ink
    val onSurface: Color get() = ink
    val success: Color get() = ink
    val error: Color get() = ink
    val glassAlpha: Float get() = 1f
    val gradient: List<Color>
        get() = listOf(ink, ink.copy(alpha = 0.7f), ink.copy(alpha = 0.4f))
}

/** 01 — pure black, no grid, hard contrast. */
val VoidTheme = TypflowTheme(
    id = "void",
    name = "VOID",
    nameFa = "وید",
    descriptionFa = "سیاه مطلق، سفید خالص، خطوط مویی",
    descriptionEn = "Absolute black, pure white, hairlines",
    bg = Color(0xFF000000),
    surface = Color(0xFF080808),
    ink = Color(0xFFFFFFFF),
    dim = Color(0xFF808080),
    line = Color(0xFF262626),
    grid = Color(0xFF111111),
    light = false,
    mono = false,
    gridStep = 0,
    radius = 0.dp
)

/** 02 — technical grid + monospace numerals. */
val SignalTheme = TypflowTheme(
    id = "signal",
    name = "SIGNAL",
    nameFa = "سیگنال",
    descriptionFa = "شبکه فنی، اعداد مونواسپیس، کنتراست بالا",
    descriptionEn = "Technical grid, monospace figures",
    bg = Color(0xFF000000),
    surface = Color(0xFF050505),
    ink = Color(0xFFFFFFFF),
    dim = Color(0xFF9A9A9A),
    line = Color(0xFF3A3A3A),
    grid = Color(0xFF1A1A1A),
    light = false,
    mono = true,
    gridStep = 36,
    radius = 0.dp
)

/** 03 — soft charcoal, calm greys, no grid. */
val CarbonTheme = TypflowTheme(
    id = "carbon",
    name = "CARBON",
    nameFa = "کربن",
    descriptionFa = "زغالی نرم، خاکستری آرام، بدون شبکه",
    descriptionEn = "Soft charcoal, calm greys",
    bg = Color(0xFF0B0B0C),
    surface = Color(0xFF141416),
    ink = Color(0xFFEDEDED),
    dim = Color(0xFF86868C),
    line = Color(0xFF232326),
    grid = Color(0xFF151517),
    light = false,
    mono = false,
    gridStep = 0,
    radius = 2.dp
)

/** 04 — inverted: paper white, ink black. */
val PaperTheme = TypflowTheme(
    id = "paper",
    name = "PAPER",
    nameFa = "کاغذ",
    descriptionFa = "وارونه: کاغذ سفید، مرکب سیاه",
    descriptionEn = "Inverted: paper white, black ink",
    bg = Color(0xFFF2F2EF),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF0A0A0A),
    dim = Color(0xFF6B6B66),
    line = Color(0xFFD3D3CD),
    grid = Color(0xFFE4E4DE),
    light = true,
    mono = true,
    gridStep = 48,
    radius = 0.dp
)

val AllThemes: List<TypflowTheme> = listOf(VoidTheme, SignalTheme, CarbonTheme, PaperTheme)

fun themeById(id: String?): TypflowTheme = AllThemes.firstOrNull { it.id == id } ?: VoidTheme
