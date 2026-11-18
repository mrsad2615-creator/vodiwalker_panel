package ir.aegis.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import ir.aegis.core.model.AppLanguage
import ir.aegis.core.model.ThemeMode

@Immutable
data class ObsidianColors(
    val isDark: Boolean,
    val canvasTop: Color,
    val canvasMid: Color,
    val canvasBottom: Color,
    val orbCyan: Color,
    val orbViolet: Color,
    val glassSurface: Color,
    val glassElevated: Color,
    val glassTopSheen: Color,
    val glassRimStart: Color,
    val glassRimEnd: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentCyan: Color,
    val accentTeal: Color,
    val accentEmerald: Color,
    val accentAmber: Color,
    val accentCrimson: Color,
    val accentViolet: Color,
    val chipFill: Color,
    val divider: Color,
)

val DarkObsidianColors = ObsidianColors(
    isDark = true,
    canvasTop = Color(0xFF05070D),
    canvasMid = Color(0xFF0A101D),
    canvasBottom = Color(0xFF060911),
    orbCyan = Color(0x2838BDF8),
    orbViolet = Color(0x228B5CF6),
    glassSurface = Color(0xBD101827),
    glassElevated = Color(0xDB172338),
    glassTopSheen = Color(0x28FFFFFF),
    glassRimStart = Color(0x52FFFFFF),
    glassRimEnd = Color(0x1C38BDF8),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFFB6C4D8),
    textMuted = Color(0xFF7B8CA6),
    accentCyan = Color(0xFF38BDF8),
    accentTeal = Color(0xFF2DD4BF),
    accentEmerald = Color(0xFF34D399),
    accentAmber = Color(0xFFFBBF24),
    accentCrimson = Color(0xFFFB7185),
    accentViolet = Color(0xFFA78BFA),
    chipFill = Color(0x2838BDF8),
    divider = Color(0x2294A3B8),
)

val LightObsidianColors = ObsidianColors(
    isDark = false,
    canvasTop = Color(0xFFE7EEF7),
    canvasMid = Color(0xFFF2F6FC),
    canvasBottom = Color(0xFFDFE8F4),
    orbCyan = Color(0x280284C7),
    orbViolet = Color(0x1D7C3AED),
    glassSurface = Color(0xCCFFFFFF),
    glassElevated = Color(0xEBFFFFFF),
    glassTopSheen = Color(0xE6FFFFFF),
    glassRimStart = Color(0xD9FFFFFF),
    glassRimEnd = Color(0x3D0F172A),
    textPrimary = Color(0xFF0B1220),
    textSecondary = Color(0xFF334155),
    textMuted = Color(0xFF64748B),
    accentCyan = Color(0xFF0284C7),
    accentTeal = Color(0xFF0D9488),
    accentEmerald = Color(0xFF059669),
    accentAmber = Color(0xFFD97706),
    accentCrimson = Color(0xFFE11D48),
    accentViolet = Color(0xFF7C3AED),
    chipFill = Color(0x180284C7),
    divider = Color(0x220F172A),
)

val LocalObsidianColors = staticCompositionLocalOf { DarkObsidianColors }
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.EN }

private val AegisTypography = Typography(
    headlineLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Normal, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun AegisTheme(
    themeMode: ThemeMode,
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val obsidian = if (useDark) DarkObsidianColors else LightObsidianColors
    val m3Scheme = if (useDark) {
        darkColorScheme(
            primary = obsidian.accentCyan,
            secondary = obsidian.accentTeal,
            tertiary = obsidian.accentViolet,
            background = obsidian.canvasTop,
            surface = obsidian.glassSurface,
            onPrimary = Color(0xFF04121E),
            onBackground = obsidian.textPrimary,
            onSurface = obsidian.textPrimary,
            error = obsidian.accentCrimson,
        )
    } else {
        lightColorScheme(
            primary = obsidian.accentCyan,
            secondary = obsidian.accentTeal,
            tertiary = obsidian.accentViolet,
            background = obsidian.canvasTop,
            surface = obsidian.glassSurface,
            onPrimary = Color.White,
            onBackground = obsidian.textPrimary,
            onSurface = obsidian.textPrimary,
            error = obsidian.accentCrimson,
        )
    }

    val layoutDir = if (language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalObsidianColors provides obsidian,
        LocalAppLanguage provides language,
        LocalLayoutDirection provides layoutDir,
    ) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = AegisTypography,
            content = content,
        )
    }
}
