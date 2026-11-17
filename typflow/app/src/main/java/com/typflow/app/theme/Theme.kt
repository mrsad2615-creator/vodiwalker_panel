package com.typflow.app.theme

import androidx.compose.ui.graphics.Color

/**
 * TYPFLOW theme system — 4 independent, switchable cinematic themes.
 */
data class TypflowTheme(
    val id: String,
    val name: String,
    val nameFa: String,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurface: Color,
    val success: Color,
    val error: Color,
    val gradient: List<Color>,
    val glassAlpha: Float,
    val descriptionFa: String,
    val descriptionEn: String
)

val CyberFusionTheme = TypflowTheme(
    id = "cyber",
    name = "CYBER FUSION",
    nameFa = "سایبر فیوژن",
    primary = Color(0xFF00F5D4),
    secondary = Color(0xFF00B4D8),
    accent = Color(0xFFB537F2),
    background = Color(0xFF04070E),
    surface = Color(0xFF0A1220),
    surfaceVariant = Color(0xFF101B30),
    onBackground = Color(0xFFF2FBFA),
    onSurface = Color(0xFFD7E6E4),
    success = Color(0xFF2EE6A8),
    error = Color(0xFFFF5C7A),
    gradient = listOf(
        Color(0xFF00F5D4), Color(0xFF00B4D8),
        Color(0xFFB537F2), Color(0xFFFF5C8A)
    ),
    glassAlpha = 0.10f,
    descriptionFa = "مشکی، فیروزه‌ای و جلوه‌های هولوگرافیک",
    descriptionEn = "Black, turquoise & holographic glow"
)

val ObsidianTheme = TypflowTheme(
    id = "obsidian",
    name = "OBSIDIAN",
    nameFa = "ابسیدین",
    primary = Color(0xFFE8E8EC),
    secondary = Color(0xFF9AA0A6),
    accent = Color(0xFFC9CFD8),
    background = Color(0xFF0A0A0B),
    surface = Color(0xFF141416),
    surfaceVariant = Color(0xFF1E1E22),
    onBackground = Color(0xFFF5F5F7),
    onSurface = Color(0xFFD8D8DE),
    success = Color(0xFF7FD8A4),
    error = Color(0xFFFF6B81),
    gradient = listOf(
        Color(0xFFF5F5F7), Color(0xFFC9CFD8), Color(0xFF8E939E)
    ),
    glassAlpha = 0.07f,
    descriptionFa = "مشکی، نقره‌ای و طراحی لوکس",
    descriptionEn = "Black, silver & luxury minimal"
)

val NebulaTheme = TypflowTheme(
    id = "nebula",
    name = "NEBULA",
    nameFa = "نبولا",
    primary = Color(0xFFA855F7),
    secondary = Color(0xFF3B82F6),
    accent = Color(0xFFEC4899),
    background = Color(0xFF0B0518),
    surface = Color(0xFF160B2E),
    surfaceVariant = Color(0xFF20123F),
    onBackground = Color(0xFFF5F0FF),
    onSurface = Color(0xFFD9CCF5),
    success = Color(0xFF34D399),
    error = Color(0xFFFB7185),
    gradient = listOf(
        Color(0xFFA855F7), Color(0xFF3B82F6), Color(0xFFEC4899)
    ),
    glassAlpha = 0.10f,
    descriptionFa = "بنفش، آبی نئونی و فضای آینده‌نگر",
    descriptionEn = "Purple, neon blue & futuristic space"
)

val MonolithTheme = TypflowTheme(
    id = "monolith",
    name = "MONOLITH",
    nameFa = "مونولیت",
    primary = Color(0xFFFFFFFF),
    secondary = Color(0xFF8A8A8A),
    accent = Color(0xFFFFFFFF),
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF161616),
    surfaceVariant = Color(0xFF1F1F1F),
    onBackground = Color(0xFFF5F5F5),
    onSurface = Color(0xFFD9D9D9),
    success = Color(0xFF4ADE80),
    error = Color(0xFFFF5252),
    gradient = listOf(Color(0xFFFFFFFF), Color(0xFF9E9E9E)),
    glassAlpha = 0.05f,
    descriptionFa = "مینیمال، مونوکروم و تایپوگرافی حرفه‌ای",
    descriptionEn = "Minimal, monochrome & pro typography"
)

val AllThemes: List<TypflowTheme> = listOf(
    CyberFusionTheme, ObsidianTheme, NebulaTheme, MonolithTheme
)

fun themeById(id: String): TypflowTheme =
    AllThemes.firstOrNull { it.id == id } ?: CyberFusionTheme
