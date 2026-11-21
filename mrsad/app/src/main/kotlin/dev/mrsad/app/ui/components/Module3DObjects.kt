package dev.mrsad.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.mrsad.app.data.settings.GraphicsQuality
import dev.mrsad.app.testing.MrsadTestHooks
import dev.mrsad.app.ui.theme.MrsadColors
import dev.mrsad.app.ui.theme.MrsadTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimal Monochrome Frosted Glass Visuals for the Four Core Capabilities:
 *
 * - [ModuleSculptureType.TYPING]: Frosted glass keycap matrix with active pulse.
 * - [ModuleSculptureType.READING]: Minimal glass book pages & kinetic focus line.
 * - [ModuleSculptureType.EYE]: Minimal concentric ocular iris & smooth tracking dot.
 * - [ModuleSculptureType.BRAIN]: Minimal neural constellation ring & synaptic nodes.
 */
enum class ModuleSculptureType {
    TYPING,
    READING,
    EYE,
    BRAIN,
}

@Composable
fun ModuleSculpture3D(
    type: ModuleSculptureType,
    modifier: Modifier = Modifier,
    intensity: Float = 0.85f,
    animated: Boolean = true,
) {
    val colors = MrsadTheme.colors
    val motion = MrsadTheme.motion
    val scratchPath = remember { Path() }

    val animate = animated &&
        !MrsadTestHooks.isDeterministic &&
        motion.animationsEnabled &&
        motion.quality != GraphicsQuality.PERFORMANCE

    val phase = if (animate) {
        val transition = rememberInfiniteTransition(label = "glass_visual_${type.name}")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "phase",
        )
        p
    } else {
        0.85f
    }

    Canvas(modifier = modifier) {
        if (size.minDimension <= 4f) return@Canvas
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val r = size.minDimension * 0.45f

        // Frosted dark glass lens base
        drawCircle(
            color = Color(0xFF0A0A0E),
            radius = r,
            center = Offset(cx, cy),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.10f * intensity),
                    Color.Transparent,
                ),
                center = Offset(cx, cy - r * 0.35f),
                radius = r * 1.1f,
            ),
            radius = r,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.22f),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx()),
        )

        when (type) {
            ModuleSculptureType.TYPING -> drawMinimalTypingGlass(phase, colors)
            ModuleSculptureType.READING -> drawMinimalReadingGlass(phase, colors)
            ModuleSculptureType.EYE -> drawMinimalEyeGlass(phase, scratchPath, colors)
            ModuleSculptureType.BRAIN -> drawMinimalBrainGlass(phase, colors)
        }
    }
}

private fun DrawScope.drawMinimalTypingGlass(phase: Float, colors: MrsadColors) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.5f
    val pulse = 0.5f + 0.5f * sin(phase)

    val kw = w * 0.18f
    val kh = h * 0.16f
    val gap = w * 0.05f
    val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())

    for (row in 0..1) {
        for (col in -1..1) {
            val x = cx + col * (kw + gap) - kw / 2f
            val y = cy - h * 0.18f + row * (kh + gap)
            val active = (row == 0 && col == 0)
            drawRoundRect(
                color = if (active) Color.White.copy(alpha = 0.75f + 0.20f * pulse) else Color.White.copy(alpha = 0.14f),
                topLeft = Offset(x, y),
                size = Size(kw, kh),
                cornerRadius = corner,
            )
            drawRoundRect(
                color = Color.White.copy(alpha = if (active) 0.95f else 0.35f),
                topLeft = Offset(x, y),
                size = Size(kw, kh),
                cornerRadius = corner,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
    // Minimal glass spacebar
    val sbW = kw * 2.4f
    val sbH = kh * 0.72f
    val sbX = cx - sbW / 2f
    val sbY = cy + h * 0.19f
    drawRoundRect(
        color = colors.textPrimary.copy(alpha = 0.22f + 0.18f * pulse),
        topLeft = Offset(sbX, sbY),
        size = Size(sbW, sbH),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.65f),
        topLeft = Offset(sbX, sbY),
        size = Size(sbW, sbH),
        cornerRadius = corner,
        style = Stroke(width = 1.dp.toPx()),
    )
}

private fun DrawScope.drawMinimalReadingGlass(phase: Float, colors: MrsadColors) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.5f
    val scan = 0.5f + 0.5f * sin(phase)

    val pageW = w * 0.26f
    val pageH = h * 0.44f
    val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())

    // Left & right frosted glass pages
    drawRoundRect(
        color = Color.White.copy(alpha = 0.10f),
        topLeft = Offset(cx - pageW - 2.dp.toPx(), cy - pageH / 2f),
        size = Size(pageW, pageH),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.45f),
        topLeft = Offset(cx - pageW - 2.dp.toPx(), cy - pageH / 2f),
        size = Size(pageW, pageH),
        cornerRadius = corner,
        style = Stroke(width = 1.1.dp.toPx()),
    )

    drawRoundRect(
        color = Color.White.copy(alpha = 0.16f),
        topLeft = Offset(cx + 2.dp.toPx(), cy - pageH / 2f),
        size = Size(pageW, pageH),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.72f),
        topLeft = Offset(cx + 2.dp.toPx(), cy - pageH / 2f),
        size = Size(pageW, pageH),
        cornerRadius = corner,
        style = Stroke(width = 1.1.dp.toPx()),
    )

    // Minimal typography scan lines
    for (i in 0..2) {
        val ly = cy - pageH * 0.24f + i * (pageH * 0.24f)
        val alpha = if (i == 1) (0.65f + 0.35f * scan) else 0.35f
        drawLine(
            color = colors.textPrimary.copy(alpha = alpha),
            start = Offset(cx - pageW * 0.82f, ly),
            end = Offset(cx - pageW * 0.22f, ly),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = colors.textPrimary.copy(alpha = alpha),
            start = Offset(cx + pageW * 0.22f, ly),
            end = Offset(cx + pageW * 0.82f, ly),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawMinimalEyeGlass(
    phase: Float,
    path: Path,
    colors: MrsadColors,
) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.5f
    val rx = w * 0.30f
    val ry = h * 0.19f

    path.reset()
    path.moveTo(cx - rx, cy)
    path.quadraticTo(cx, cy - ry * 1.55f, cx + rx, cy)
    path.quadraticTo(cx, cy + ry * 1.55f, cx - rx, cy)
    path.close()

    drawPath(path, Color.White.copy(alpha = 0.10f))
    drawPath(path, Color.White.copy(alpha = 0.78f), style = Stroke(width = 1.4.dp.toPx()))

    val gazeX = cx + cos(phase) * (w * 0.04f)
    val gazeY = cy + sin(phase * 1.3f) * (h * 0.025f)
    val irisR = size.minDimension * 0.13f

    drawCircle(
        color = Color.White.copy(alpha = 0.45f),
        radius = irisR,
        center = Offset(gazeX, gazeY),
        style = Stroke(width = 1.2.dp.toPx()),
    )
    drawCircle(
        color = colors.textPrimary,
        radius = irisR * 0.45f,
        center = Offset(gazeX, gazeY),
    )
}

private fun DrawScope.drawMinimalBrainGlass(phase: Float, colors: MrsadColors) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val r = size.minDimension * 0.26f
    val pulse = 0.5f + 0.5f * sin(phase)

    val pts = Array(6) { i ->
        val angle = (2.0 * PI * i / 6.0 - PI / 2.0).toFloat() + phase * 0.08f
        Offset(cx + cos(angle) * r, cy + sin(angle) * r)
    }

    for (i in 0 until 6) {
        drawLine(
            color = Color.White.copy(alpha = 0.42f),
            start = pts[i],
            end = pts[(i + 1) % 6],
            strokeWidth = 1.2.dp.toPx(),
        )
        drawLine(
            color = Color.White.copy(alpha = 0.22f),
            start = Offset(cx, cy),
            end = pts[i],
            strokeWidth = 1.dp.toPx(),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = 2.4.dp.toPx(),
            center = pts[i],
        )
    }
    drawCircle(
        color = colors.textPrimary.copy(alpha = 0.25f + 0.20f * pulse),
        radius = 6.5.dp.toPx(),
        center = Offset(cx, cy),
    )
    drawCircle(
        color = colors.textPrimary,
        radius = 3.2.dp.toPx(),
        center = Offset(cx, cy),
    )
}
