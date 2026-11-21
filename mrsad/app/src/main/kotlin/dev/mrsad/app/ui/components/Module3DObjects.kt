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
import dev.mrsad.app.ui.theme.ModulePalettes
import dev.mrsad.app.ui.theme.ModuleSpectralIdentity
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.ObsidianSpectrum
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * OBSIDIAN SPECTRUM — Dimensional 3D-Inspired Module Iconography (Master Prompt §4 & §8).
 *
 * Each module carries its dedicated spectral identity on a unified Obsidian
 * metallic-glass orb (`#211B2C` -> `#090810`):
 * - [ModuleSculptureType.TYPING]: Electric Blue (`#176ED4`) & Bright Blue (`#54B8FF`)
 * - [ModuleSculptureType.READING]: Deep Violet (`#7040B9`) & Lavender (`#C69CFF`)
 * - [ModuleSculptureType.EYE]: Teal (`#138F91`) & Aqua (`#67E7DA`)
 * - [ModuleSculptureType.BRAIN]: Magenta (`#B53D91`) & Pink (`#F18ACD`)
 */
enum class ModuleSculptureType {
    TYPING,
    READING,
    EYE,
    BRAIN;

    val palette: ModuleSpectralIdentity
        get() = when (this) {
            TYPING -> ModulePalettes.Typing
            READING -> ModulePalettes.Reading
            EYE -> ModulePalettes.Eye
            BRAIN -> ModulePalettes.Brain
        }
}

@Composable
fun ModuleSculpture3D(
    type: ModuleSculptureType,
    modifier: Modifier = Modifier,
    intensity: Float = 0.90f,
    animated: Boolean = true,
) {
    val motion = MrsadTheme.motion
    val scratchPath = remember { Path() }
    val palette = type.palette

    val animate = animated &&
        !MrsadTestHooks.isDeterministic &&
        motion.animationsEnabled &&
        motion.quality != GraphicsQuality.PERFORMANCE

    val phase = if (animate) {
        val transition = rememberInfiniteTransition(label = "spectrum_visual_${type.name}")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4800, easing = LinearEasing),
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

        // 1. Dimensional Obsidian-glass orb foundation (#211B2C -> #090810)
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(
                    ObsidianSpectrum.ElevatedSurface,
                    ObsidianSpectrum.ObsidianBlack,
                ),
            ),
            radius = r,
            center = Offset(cx, cy),
        )

        // 2. Module-specific spectral internal illumination (2-stop radial gradient)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.primary.copy(alpha = 0.34f * intensity),
                    Color.Transparent,
                ),
                center = Offset(cx, cy - r * 0.25f),
                radius = r * 1.05f,
            ),
            radius = r,
            center = Offset(cx, cy),
        )

        // 3. Iridescent spectral rim border
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(
                    palette.luminous.copy(alpha = 0.72f),
                    palette.primary.copy(alpha = 0.22f),
                ),
            ),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 1.3.dp.toPx()),
        )

        when (type) {
            ModuleSculptureType.TYPING -> drawSpectrumTyping(phase, palette)
            ModuleSculptureType.READING -> drawSpectrumReading(phase, palette)
            ModuleSculptureType.EYE -> drawSpectrumEye(phase, scratchPath, palette)
            ModuleSculptureType.BRAIN -> drawSpectrumBrain(phase, palette)
        }
    }
}

private fun DrawScope.drawSpectrumTyping(phase: Float, palette: ModuleSpectralIdentity) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.5f
    val pulse = 0.5f + 0.5f * sin(phase)

    val kw = w * 0.17f
    val kh = h * 0.15f
    val gap = w * 0.045f
    val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())

    for (row in 0..1) {
        for (col in -1..1) {
            val x = cx + col * (kw + gap) - kw / 2f
            val y = cy - h * 0.19f + row * (kh + gap)
            val active = (row == 0 && col == 0)
            drawRoundRect(
                color = if (active) {
                    palette.primary.copy(alpha = 0.55f + 0.30f * pulse)
                } else {
                    ObsidianSpectrum.DarkSurface
                },
                topLeft = Offset(x, y),
                size = Size(kw, kh),
                cornerRadius = corner,
            )
            drawRoundRect(
                color = if (active) palette.luminous else ObsidianSpectrum.SoftWhite.copy(alpha = 0.45f),
                topLeft = Offset(x, y),
                size = Size(kw, kh),
                cornerRadius = corner,
                style = Stroke(width = 1.1.dp.toPx()),
            )
        }
    }

    // Precision Electric Blue spacebar deck
    val sbW = kw * 2.45f
    val sbH = kh * 0.74f
    val sbX = cx - sbW / 2f
    val sbY = cy + h * 0.18f
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                palette.primary.copy(alpha = 0.65f),
                palette.luminous.copy(alpha = 0.45f + 0.30f * pulse),
            ),
        ),
        topLeft = Offset(sbX, sbY),
        size = Size(sbW, sbH),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = palette.luminous,
        topLeft = Offset(sbX, sbY),
        size = Size(sbW, sbH),
        cornerRadius = corner,
        style = Stroke(width = 1.1.dp.toPx()),
    )
}

private fun DrawScope.drawSpectrumReading(phase: Float, palette: ModuleSpectralIdentity) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.5f
    val scan = 0.5f + 0.5f * sin(phase)

    val pageW = w * 0.25f
    val pageH = h * 0.42f
    val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())

    // Left page (Deep Violet glass)
    drawRoundRect(
        color = palette.primary.copy(alpha = 0.26f),
        topLeft = Offset(cx - pageW - 2.dp.toPx(), cy - pageH / 2f),
        size = Size(pageW, pageH),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = palette.luminous.copy(alpha = 0.62f),
        topLeft = Offset(cx - pageW - 2.dp.toPx(), cy - pageH / 2f),
        size = Size(pageW, pageH),
        cornerRadius = corner,
        style = Stroke(width = 1.1.dp.toPx()),
    )

    // Right page (Lavender highlighted glass)
    drawRoundRect(
        color = palette.primary.copy(alpha = 0.38f),
        topLeft = Offset(cx + 2.dp.toPx(), cy - pageH / 2f),
        size = Size(pageW, pageH),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = ObsidianSpectrum.SoftWhite.copy(alpha = 0.85f),
        topLeft = Offset(cx + 2.dp.toPx(), cy - pageH / 2f),
        size = Size(pageW, pageH),
        cornerRadius = corner,
        style = Stroke(width = 1.2.dp.toPx()),
    )

    // Animated Lavender & Soft White reading lines
    for (i in 0..2) {
        val ly = cy - pageH * 0.24f + i * (pageH * 0.24f)
        val lineColor = if (i == 1) {
            palette.luminous.copy(alpha = 0.75f + 0.25f * scan)
        } else {
            ObsidianSpectrum.SoftWhite.copy(alpha = 0.48f)
        }
        drawLine(
            color = lineColor,
            start = Offset(cx - pageW * 0.82f, ly),
            end = Offset(cx - pageW * 0.22f, ly),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = lineColor,
            start = Offset(cx + pageW * 0.22f, ly),
            end = Offset(cx + pageW * 0.82f, ly),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawSpectrumEye(
    phase: Float,
    path: Path,
    palette: ModuleSpectralIdentity,
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

    drawPath(path, palette.primary.copy(alpha = 0.28f))
    drawPath(path, palette.luminous, style = Stroke(width = 1.5.dp.toPx()))

    val gazeX = cx + cos(phase) * (w * 0.04f)
    val gazeY = cy + sin(phase * 1.3f) * (h * 0.025f)
    val irisR = size.minDimension * 0.135f

    drawCircle(
        color = palette.primary.copy(alpha = 0.55f),
        radius = irisR,
        center = Offset(gazeX, gazeY),
    )
    drawCircle(
        color = palette.luminous,
        radius = irisR,
        center = Offset(gazeX, gazeY),
        style = Stroke(width = 1.3.dp.toPx()),
    )
    drawCircle(
        color = ObsidianSpectrum.SoftWhite,
        radius = irisR * 0.42f,
        center = Offset(gazeX, gazeY),
    )
}

private fun DrawScope.drawSpectrumBrain(phase: Float, palette: ModuleSpectralIdentity) {
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
            color = palette.primary.copy(alpha = 0.72f),
            start = pts[i],
            end = pts[(i + 1) % 6],
            strokeWidth = 1.3.dp.toPx(),
        )
        drawLine(
            color = palette.luminous.copy(alpha = 0.38f),
            start = Offset(cx, cy),
            end = pts[i],
            strokeWidth = 1.1.dp.toPx(),
        )
        drawCircle(
            color = palette.luminous,
            radius = 2.6.dp.toPx(),
            center = pts[i],
        )
    }
    drawCircle(
        color = palette.primary.copy(alpha = 0.55f + 0.30f * pulse),
        radius = 7.dp.toPx(),
        center = Offset(cx, cy),
    )
    drawCircle(
        color = ObsidianSpectrum.SoftWhite,
        radius = 3.2.dp.toPx(),
        center = Offset(cx, cy),
    )
}
