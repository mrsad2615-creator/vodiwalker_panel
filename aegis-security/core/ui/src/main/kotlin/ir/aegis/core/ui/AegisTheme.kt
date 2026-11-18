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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import ir.aegis.core.model.AppLanguage
import ir.aegis.core.model.ThemeMode

val AegisFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_bold, FontWeight.ExtraBold),
)

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
    canvasTop = Color(0xFF060911),
    canvasMid = Color(0xFF0B1221),
    canvasBottom = Color(0xFF070B14),
    orbCyan = Color(0x2438BDF8),
    orbViolet = Color(0x1E8B5CF6),
    glassSurface = Color(0xD8111B2E),
    glassElevated = Color(0xEC18253E),
    glassTopSheen = Color(0x28FFFFFF),
    glassRimStart = Color(0x4CFFFFFF),
    glassRimEnd = Color(0x2438BDF8),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFC2CFE0),
    textMuted = Color(0xFF8C9DB5),
    accentCyan = Color(0xFF38BDF8),
    accentTeal = Color(0xFF2DD4BF),
    accentEmerald = Color(0xFF34D399),
    accentAmber = Color(0xFFFBBF24),
    accentCrimson = Color(0xFFFB7185),
    accentViolet = Color(0xFFA78BFA),
    chipFill = Color(0x2838BDF8),
    divider = Color(0x2994A3B8),
)

val LightObsidianColors = ObsidianColors(
    isDark = false,
    canvasTop = Color(0xFFEAF0F8),
    canvasMid = Color(0xFFF4F7FC),
    canvasBottom = Color(0xFFE3ECF6),
    orbCyan = Color(0x1E0284C7),
    orbViolet = Color(0x167C3AED),
    glassSurface = Color(0xF2FFFFFF),
    glassElevated = Color(0xFFFFFFFF),
    glassTopSheen = Color(0xE6FFFFFF),
    glassRimStart = Color(0xD9FFFFFF),
    glassRimEnd = Color(0x360F172A),
    textPrimary = Color(0xFF0B1324),
    textSecondary = Color(0xFF334155),
    textMuted = Color(0xFF64748B),
    accentCyan = Color(0xFF0284C7),
    accentTeal = Color(0xFF0D9488),
    accentEmerald = Color(0xFF059669),
    accentAmber = Color(0xFFD97706),
    accentCrimson = Color(0xFFE11D48),
    accentViolet = Color(0xFF7C3AED),
    chipFill = Color(0x180284C7),
    divider = Color(0x240F172A),
)

val LocalObsidianColors = staticCompositionLocalOf { DarkObsidianColors }
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.EN }

private val AegisTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 24.sp,
        lineHeight = 35.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineMedium = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 20.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 13.5.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 13.5.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
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
            surfaceVariant = obsidian.glassElevated,
            onPrimary = Color(0xFF04121E),
            onBackground = obsidian.textPrimary,
            onSurface = obsidian.textPrimary,
            onSurfaceVariant = obsidian.textSecondary,
            error = obsidian.accentCrimson,
        )
    } else {
        lightColorScheme(
            primary = obsidian.accentCyan,
            secondary = obsidian.accentTeal,
            tertiary = obsidian.accentViolet,
            background = obsidian.canvasTop,
            surface = obsidian.glassSurface,
            surfaceVariant = obsidian.glassElevated,
            onPrimary = Color.White,
            onBackground = obsidian.textPrimary,
            onSurface = obsidian.textPrimary,
            onSurfaceVariant = obsidian.textSecondary,
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
