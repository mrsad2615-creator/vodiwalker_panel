package com.vodiwalker.gesture.core.designsystem.theme

import android.app.UiModeManager
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vodiwalker.gesture.core.designsystem.R

/**
 * Vazirmatn — the reference open-source Persian UI typeface (SIL OFL 1.1, bundled under
 * `res/font`). Persian text needs a taller line box than Latin (ascenders, descenders, dots and
 * floating marks), and must never be letter-spaced: tracking tears the cursive joins apart.
 *
 * Every style therefore uses a line height of 1.55–1.75× the font size and `letterSpacing = 0.sp`.
 */
val VodiFontFamily: FontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

/**
 * Complete 15-style Material Design 3 Type Scale engineered for high-density system telemetry and
 * expressive cinematic headings. Uses tabular numerals (`tnum`) on label/metric slots to eliminate
 * layout jitter during live 120Hz latency and confidence updates.
 */
val VodiTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = VodiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 57.sp,
        lineHeight = 77.0.sp,
        letterSpacing = 0.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = VodiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 45.sp,
        lineHeight = 60.8.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = VodiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 55.8.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = VodiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 49.6.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = VodiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 43.4.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = VodiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 37.2.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = VodiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 37.4.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = VodiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 27.2.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = VodiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 23.8.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = VodiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 27.2.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = VodiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 23.8.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = VodiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 20.4.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = VodiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 23.8.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    ),
    labelMedium = TextStyle(
        fontFamily = VodiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 20.4.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    ),
    labelSmall = TextStyle(
        fontFamily = VodiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 18.7.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    ),
)

/**
 * Official Material Design 3 corner radius scale (`4dp / 8dp / 12dp / 16dp / 28dp`).
 */
val VodiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Material Design 3 Expressive spring physics & emphasized easing curves.
 */
object VodiMotion {
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    const val DurationShortMillis: Int = 180
    const val DurationMediumMillis: Int = 320
    const val DurationLongMillis: Int = 500

    /**
     * Fast spatial spring for pointer/selection feedback (M3 'spatial-fast').
     */
    fun <T> spatialFastSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.78f,
        stiffness = Spring.StiffnessMedium,
    )

    /**
     * Expressive spatial spring for large surface transitions.
     */
    fun <T> spatialExpressiveSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.68f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /**
     * Effects spring for fades, scrims, and glass opacity.
     */
    fun <T> effectsSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}

/**
 * 4dp-grid M3 spatial layout tokens and minimum accessibility touch targets (`48.dp`).
 */
@Immutable
data class VodiSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val minTouchTarget: Dp = 48.dp,
    val glassBlurRadius: Dp = 24.dp,
)

val LocalVodiSpacing = staticCompositionLocalOf { VodiSpacing() }

/**
 * Root Material Design 3 theme honoring Android 12+ Dynamic Color (Monet), Android 14+ system
 * contrast thresholds (`UiModeManager.contrast >= 0.5f`), and Obsidian Cinema dark mode.
 */
@Composable
fun VodiGestureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    forceHighContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemHighContrast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val uiMode = context.getSystemService(UiModeManager::class.java)
        (uiMode?.contrast ?: 0f) >= 0.5f
    } else {
        false
    }
    val useHighContrast = forceHighContrast || systemHighContrast

    val colorScheme = when {
        useHighContrast && darkTheme -> VodiDarkHighContrastColorScheme
        useHighContrast && !darkTheme -> VodiLightHighContrastColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> VodiDarkColorScheme
        else -> VodiLightColorScheme
    }

    val extendedColors = if (darkTheme) DarkExtendedCinematicColors else LightExtendedCinematicColors

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors,
        LocalVodiSpacing provides VodiSpacing(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VodiTypography,
            shapes = VodiShapes,
            content = content,
        )
    }
}
