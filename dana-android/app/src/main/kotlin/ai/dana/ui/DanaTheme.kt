package ai.dana.ui

import ai.dana.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Obsidian = Color(0xFF08090F)
internal val Ice = Color(0xFFC4DEFF)
internal val Titanium = Color(0xFFADB9CE)
internal val Mint = Color(0xFF8DE5C0)
internal val Amber = Color(0xFFFFD599)

private val DanaColors = darkColorScheme(
    primary = Ice,
    onPrimary = Color(0xFF0D2542),
    primaryContainer = Color(0xFF253D61),
    onPrimaryContainer = Color(0xFFE1EEFF),
    background = Obsidian,
    onBackground = Color(0xFFF0F3FB),
    surface = Color(0xFF111626),
    onSurface = Color(0xFFF0F3FB),
    surfaceVariant = Color(0xFF202939),
    onSurfaceVariant = Titanium,
    outline = Color(0xFF74839F),
    outlineVariant = Color(0xFF34415B),
    error = Color(0xFFFFB4AB),
)

private val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_light, FontWeight.Light),
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private fun TextStyle.persian(weight: FontWeight = FontWeight.Normal) = copy(
    fontFamily = Vazirmatn,
    fontWeight = weight,
    letterSpacing = 0.sp,
    textDirection = TextDirection.ContentOrRtl,
)

private val Base = Typography()
private val DanaType = Typography(
    displayLarge = Base.displayLarge.persian(FontWeight.Bold),
    displayMedium = Base.displayMedium.persian(FontWeight.Bold),
    displaySmall = Base.displaySmall.persian(FontWeight.Bold),
    headlineLarge = Base.headlineLarge.persian(FontWeight.Bold),
    headlineMedium = Base.headlineMedium.persian(FontWeight.Bold),
    headlineSmall = Base.headlineSmall.persian(FontWeight.Bold),
    titleLarge = Base.titleLarge.persian(FontWeight.Bold),
    titleMedium = Base.titleMedium.persian(FontWeight.Medium),
    titleSmall = Base.titleSmall.persian(FontWeight.Medium),
    bodyLarge = Base.bodyLarge.persian(),
    bodyMedium = Base.bodyMedium.persian(),
    bodySmall = Base.bodySmall.persian(),
    labelLarge = Base.labelLarge.persian(FontWeight.Medium),
    labelMedium = Base.labelMedium.persian(FontWeight.Medium),
    labelSmall = Base.labelSmall.persian(FontWeight.Medium),
)

@Composable
internal fun DanaTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = DanaColors, typography = DanaType, content = content)
    }
}

@Composable
internal fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.clip(shape)
            .background(
                Brush.linearGradient(listOf(Ice.copy(alpha = 0.12f), Color.White.copy(alpha = 0.055f),
                    Color(0xFF2B3B60).copy(alpha = 0.21f))),
            )
            .border(1.dp, Brush.linearGradient(listOf(Ice.copy(alpha = 0.34f), Color(0xFF212D43))), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}
