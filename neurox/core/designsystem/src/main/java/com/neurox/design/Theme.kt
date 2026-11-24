package com.neurox.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.neurox.domain.ColorMath

val NeuroxFont = FontFamily(Font(R.font.vazirmatn_regular), Font(R.font.vazirmatn_bold, FontWeight.Bold))
data class AccentPreset(val en: String, val fa: String, val dark: Long, val light: Long)
val AccentPresets = listOf(
    AccentPreset("Champagne Gold", "طلایی شامپاینی", 0xFFD4B982, 0xFF765A28),
    AccentPreset("Soft Sand", "شن نرم", 0xFFD8CCB4, 0xFF706049),
    AccentPreset("Warm Beige", "بژ گرم", 0xFFD7BDA4, 0xFF775439),
    AccentPreset("Natural Stone", "سنگ طبیعی", 0xFFB7B5AC, 0xFF5D5C55),
    AccentPreset("Dusty Rose", "رز ملایم", 0xFFD8A9B5, 0xFF8A4558),
    AccentPreset("Muted Olive", "زیتونی", 0xFFB4C392, 0xFF54632C),
    AccentPreset("Soft Lavender", "اسطوخودوس", 0xFFC1B4DE, 0xFF68518B),
    AccentPreset("Silver Mist", "نقره‌ای", 0xFFBFC9D6, 0xFF536173),
    AccentPreset("Pure Red", "قرمز", 0xFFFF6464, 0xFFB32020),
    AccentPreset("Pure Blue", "آبی", 0xFF83B6FF, 0xFF155ABB),
    AccentPreset("Pure Green", "سبز", 0xFF80CE99, 0xFF236B38),
)

@Composable
fun NeuroxTheme(mode: String, accent: Long, textScale: Float = 1f, content: @Composable () -> Unit) {
    val dark = when (mode) { "LIGHT" -> false; "DARK" -> true; else -> isSystemInDarkTheme() }
    val background = if (dark) 0xFF0C0E11 else 0xFFF6F2E9
    val surface = if (dark) 0xFF181B20 else 0xFFFFFAF2
    // Use the more demanding surface to protect text on cards and on the page.
    val safe = ColorMath.accessibleAccent(accent, surface, dark)
    val primary = Color(safe)
    val onPrimary = if (ColorMath.contrast(safe, 0xFF0C0E11) >= 4.5) Color(0xFF0C0E11) else Color.White
    val colors = if (dark) darkColorScheme(
        primary = primary, onPrimary = onPrimary, primaryContainer = Color(0xFF302B23), onPrimaryContainer = Color(0xFFF4E2B9),
        secondary = Color(0xFFB7AC96), onSecondary = Color(0xFF17191D),
        background = Color(background), onBackground = Color(0xFFF2EDE4),
        surface = Color(surface), onSurface = Color(0xFFF2EDE4),
        surfaceVariant = Color(0xFF25282F), onSurfaceVariant = Color(0xFFB8BBC3),
        outline = Color(0xFF777B84), error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    ) else lightColorScheme(
        primary = primary, onPrimary = onPrimary, primaryContainer = Color(0xFFE9DCC1), onPrimaryContainer = Color(0xFF382B16),
        secondary = Color(0xFF635B4E), onSecondary = Color.White,
        background = Color(background), onBackground = Color(0xFF202329),
        surface = Color(surface), onSurface = Color(0xFF202329),
        surfaceVariant = Color(0xFFE0D8CB), onSurfaceVariant = Color(0xFF565A62),
        outline = Color(0xFF777267), error = Color(0xFFAB2121), onError = Color.White,
    )
    val typography = Typography(
        displayLarge = TextStyle(fontFamily = NeuroxFont, fontSize = 48.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold),
        displayMedium = TextStyle(fontFamily = NeuroxFont, fontSize = 42.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold),
        headlineLarge = TextStyle(fontFamily = NeuroxFont, fontSize = 32.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
        headlineSmall = TextStyle(fontFamily = NeuroxFont, fontSize = 24.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
        titleSmall = TextStyle(fontFamily = NeuroxFont, fontSize = 14.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
        displaySmall = TextStyle(fontFamily = NeuroxFont, fontSize = 36.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold),
        headlineMedium = TextStyle(fontFamily = NeuroxFont, fontSize = 30.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontFamily = NeuroxFont, fontSize = 22.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontFamily = NeuroxFont, fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
        bodyLarge = TextStyle(fontFamily = NeuroxFont, fontSize = 16.sp, lineHeight = 29.sp),
        bodyMedium = TextStyle(fontFamily = NeuroxFont, fontSize = 14.sp, lineHeight = 24.sp),
        bodySmall = TextStyle(fontFamily = NeuroxFont, fontSize = 12.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontFamily = NeuroxFont, fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
        labelMedium = TextStyle(fontFamily = NeuroxFont, fontSize = 12.sp, lineHeight = 20.sp),
        labelSmall = TextStyle(fontFamily = NeuroxFont, fontSize = 10.sp, lineHeight = 16.sp),
    )
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * textScale)) {
        MaterialTheme(colorScheme = colors, typography = typography,
            shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(NeuroxRadius.control), large = RoundedCornerShape(NeuroxRadius.card),
                extraLarge = RoundedCornerShape(NeuroxRadius.hero)), content = content)
    }
}
