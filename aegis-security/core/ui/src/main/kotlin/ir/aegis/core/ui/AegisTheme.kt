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

/**
 * Smoked Jade & Mineral Pearl Frosted Glass (Minimalist Dark Glass)
 */
val DarkObsidianColors = ObsidianColors(
    isDark = true,
    canvasTop = Color(0xFF070A0E),
    canvasMid = Color(0xFF0C1219),
    canvasBottom = Color(0xFF090D13),
    orbCyan = Color(0x1E5EEAD4),       // Soft Glacial Jade Mist
    orbViolet = Color(0x16E8D8C3),     // Warm Champagne Quartz Frost
    glassSurface = Color(0xA6141C25),  // True translucent smoked frost
    glassElevated = Color(0xBF1C2632), // Elevated frosted sheet
    glassTopSheen = Color(0x3EFFFFFF), // Specular glass lip highlight
    glassRimStart = Color(0x4CFFFFFF), // Top-left light refraction bevel
    glassRimEnd = Color(0x225EEAD4),   // Subtle glacial mint refraction tail
    textPrimary = Color(0xFFF3F6F9),
    textSecondary = Color(0xFFBAC6D4),
    textMuted = Color(0xFF7D8C9D),
    accentCyan = Color(0xFF5EEAD4),    // Glacial Jade (Signature Primary)
    accentTeal = Color(0xFF7DD3FC),    // Ice Sky Quartz
    accentEmerald = Color(0xFF6EE7B7), // Mineral Sage
    accentAmber = Color(0xFFFCD34D),   // Soft Warm Gold
    accentCrimson = Color(0xFFFB7185), // Rose Coral Alert
    accentViolet = Color(0xFFC4B5FD),  // Frosted Iris
    chipFill = Color(0x205EEAD4),
    divider = Color(0x26CBD5E1),
)

/**
 * Porcelain Alabaster & Botanical Jade Frosted Glass (Minimalist Light Glass)
 */
val LightObsidianColors = ObsidianColors(
    isDark = false,
    canvasTop = Color(0xFFF1F4F7),
    canvasMid = Color(0xFFE9EFF4),
    canvasBottom = Color(0xFFF4F6F9),
    orbCyan = Color(0x1C0F766E),       // Soft Jade Mist
    orbViolet = Color(0x16B45309),     // Warm Sand Pearl
    glassSurface = Color(0xC2FFFFFF),  // Translucent Porcelain Frost
    glassElevated = Color(0xDEFFFFFF), // Elevated Satin Frost
    glassTopSheen = Color(0xF0FFFFFF), // Pure porcelain top sheen
    glassRimStart = Color(0xE8FFFFFF), // Crisp frosted border start
    glassRimEnd = Color(0x280F766E),   // Botanical jade rim tail
    textPrimary = Color(0xFF0D1721),
    textSecondary = Color(0xFF3E4E5E),
    textMuted = Color(0xFF6B7B8C),
    accentCyan = Color(0xFF0F766E),    // Deep Botanical Jade
    accentTeal = Color(0xFF0369A1),    // Nordic Slate Sapphire
    accentEmerald = Color(0xFF15803D), // Clean Forest Sage
    accentAmber = Color(0xFFB45309),   // Burnished Ochre
    accentCrimson = Color(0xFFBE123C), // Deep Ruby Rose
    accentViolet = Color(0xFF6D28D9),  // Smoked Amethyst
    chipFill = Color(0x160F766E),
    divider = Color(0x220F172A),
)

val LocalObsidianColors = staticCompositionLocalOf { DarkObsidianColors }
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.FA }

private val AegisTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 23.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineMedium = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 19.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 16.5.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 15.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 13.5.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 14.5.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 13.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 12.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontFamily = AegisFontFamily,
        fontSize = 11.sp,
        lineHeight = 17.sp,
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
            onPrimary = Color(0xFF041714),
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
