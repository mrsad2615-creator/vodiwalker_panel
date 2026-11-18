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
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

internal val Obsidian = Color(0xFF101018)
internal val Ice = Color(0xFFEEEDF7)
internal val Titanium = Color(0xFFBCBACD)
internal val Mint = Color(0xFFC9BAFF)
internal val Amber = Color(0xFFFFCC8B)

private val DanaColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF241749),
    primaryContainer = Color(0xFF332759),
    onPrimaryContainer = Color(0xFFF0E9FF),
    secondary = Color(0xFFB8E2D5),
    onSecondary = Color(0xFF183D34),
    secondaryContainer = Color(0xFF28273C),
    onSecondaryContainer = Color(0xFFDDD5F3),
    tertiary = Color(0xFFF1CF96),
    background = Obsidian,
    onBackground = Color(0xFFF3F0FF),
    surface = Color(0xFF171721),
    onSurface = Color(0xFFF3F0FF),
    surfaceVariant = Color(0xFF28263A),
    onSurfaceVariant = Titanium,
    surfaceContainer = Color(0xFF1C1B29),
    surfaceContainerLow = Color(0xFF15151F),
    surfaceContainerHigh = Color(0xFF242336),
    outline = Color(0xFF8C86A3),
    outlineVariant = Color(0xFF36334A),
    error = Color(0xFFFFB4AB),
)

private val DanaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(15.dp),
    large = RoundedCornerShape(21.dp), extraLarge = RoundedCornerShape(27.dp),
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
        MaterialTheme(colorScheme = DanaColors, typography = DanaType, shapes = DanaShapes, content = content)
    }
}

@Composable
internal fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}
