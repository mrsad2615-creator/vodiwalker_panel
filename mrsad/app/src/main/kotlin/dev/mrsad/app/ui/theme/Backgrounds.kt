package dev.mrsad.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/** Each major page has its own environmental identity. */
enum class PageEnvironment {
    HOME, TYPE, READ, EYE, MIND, ANALYTICS, AI, ARENA, STUDIO, FOCUS, PLANNER, LIBRARY, SETTINGS
}

/**
 * Minimal OLED Pitch Black & Frosted Monochrome Glass Background.
 *
 * - Pure `#000000` OLED black base for infinite contrast and zero light bleed.
 * - In **OBSIDIAN**, renders a whisper-quiet frosted monochrome ambient glow
 *   at the top horizon so translucent glass cards catch subtle light.
 * - In **NOIR**, renders clean architectural monochrome hairlines on `#000000`.
 */
@Composable
fun AmbientBackground(
    environment: PageEnvironment,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MrsadTheme.colors

    val glowStrength = when (environment) {
        PageEnvironment.HOME -> 0.07f
        PageEnvironment.STUDIO -> 0.07f
        PageEnvironment.AI -> 0.06f
        else -> 0.05f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (colors.isNoir) {
                drawNoirSwissGrid(colors)
            } else {
                drawMinimalGlassMist(colors, glowStrength)
            }
        }
        content()
    }
}

private fun DrawScope.drawMinimalGlassMist(
    colors: MrsadColors,
    glowStrength: Float,
) {
    if (size.minDimension <= 4f) return
    val topPoolRadius = size.width * 0.90f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.textPrimary.copy(alpha = glowStrength),
                Color.Transparent,
            ),
            center = Offset(size.width * 0.5f, 0f),
            radius = topPoolRadius,
        ),
        radius = topPoolRadius,
        center = Offset(size.width * 0.5f, 0f),
    )
}

private fun DrawScope.drawNoirSwissGrid(colors: MrsadColors) {
    val stroke = 1.dp.toPx()
    val gridColor = colors.border.copy(alpha = 0.22f)
    val margin = 16.dp.toPx()
    val usableWidth = (size.width - margin * 2f).coerceAtLeast(1f)

    for (col in 0..4) {
        val x = margin + (usableWidth * col / 4f)
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = stroke,
        )
    }

    val stepY = 96.dp.toPx().coerceAtLeast(48f)
    var y = stepY
    while (y < size.height) {
        drawLine(
            color = gridColor.copy(alpha = 0.12f),
            start = Offset(margin, y),
            end = Offset(size.width - margin, y),
            strokeWidth = stroke,
        )
        y += stepY
    }
}
