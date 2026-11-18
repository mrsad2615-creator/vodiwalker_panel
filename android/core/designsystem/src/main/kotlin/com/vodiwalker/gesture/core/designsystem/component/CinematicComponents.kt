package com.vodiwalker.gesture.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vodiwalker.gesture.core.designsystem.theme.LocalExtendedColors
import com.vodiwalker.gesture.core.designsystem.theme.LocalVodiSpacing
import com.vodiwalker.gesture.core.designsystem.theme.VodiMotion
import com.vodiwalker.gesture.core.model.GesturePoint
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Frosted glassmorphic M3 container with specular top-left rim lighting and automatic opaque
 * fallback when reduced-transparency or high-contrast accessibility mode is active.
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    reduceTransparency: Boolean = false,
    contentPadding: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val extended = LocalExtendedColors.current
    val fillBrush = if (reduceTransparency) {
        Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.surfaceContainer,
            ),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                extended.glassSurface.copy(alpha = 0.84f),
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.68f),
            ),
        )
    }
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            extended.glassBorderHighlight,
            extended.glassBorderSubtle,
            extended.glassBorderHighlight.copy(alpha = 0.16f),
        ),
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(fillBrush)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Interactive M3 card with spring-driven press compression micro-interaction (`0.972f`) and
 * enforced `48dp x 48dp` minimum accessibility touch target.
 */
@Composable
fun SpringPressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.972f else 1f,
        animationSpec = VodiMotion.spatialFastSpring(),
        label = "card_press_scale",
    )
    val spacing = LocalVodiSpacing.current

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        interactionSource = interactionSource,
        modifier = modifier
            .defaultMinSize(minWidth = spacing.minTouchTarget, minHeight = spacing.minTouchTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        content = content,
    )
}

/**
 * Animated radial confidence gauge displaying hybrid recognition / AI prediction probability.
 */
@Composable
fun ConfidenceArcGauge(
    confidence: Float,
    label: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 88.dp,
) {
    val animatedConfidence by animateFloatAsState(
        targetValue = confidence.coerceIn(0f, 1f),
        animationSpec = VodiMotion.spatialExpressiveSpring(),
        label = "confidence_arc",
    )
    val extended = LocalExtendedColors.current
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val percent = (animatedConfidence * 100f).roundToInt()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(diameter)
            .semantics { contentDescription = "$label confidence $percent percent" },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 7.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(extended.gestureRibbonStart, extended.gestureRibbonEnd, extended.gestureRibbonStart),
                ),
                startAngle = 135f,
                sweepAngle = 270f * animatedConfidence,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 120Hz Cinematic Gesture Canvas used in both the Custom Gesture Recorder Studio and the Global
 * Glassmorphic Overlay. Renders a velocity-tapered luminous ink ribbon, radial contact aura, and
 * dashed AI prediction ghost completion path.
 */
@Composable
fun CinematicGestureCanvas(
    activePoints: List<GesturePoint>,
    ghostTrailPoints: List<GesturePoint>,
    onPointSampled: (GesturePoint) -> Unit,
    onStrokeStarted: (strokeId: Int) -> Unit,
    onStrokeFinished: (strokeId: Int) -> Unit,
    modifier: Modifier = Modifier,
    currentStrokeId: Int = 0,
    accessibilityLabel: String = "Gesture drawing surface",
) {
    val extended = LocalExtendedColors.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)

    Canvas(
        modifier = modifier
            .testTag("cinematic_gesture_canvas")
            .semantics { contentDescription = accessibilityLabel }
            .pointerInput(currentStrokeId) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    onStrokeStarted(currentStrokeId)
                    onPointSampled(
                        GesturePoint(
                            x = down.position.x,
                            y = down.position.y,
                            timestampMillis = down.uptimeMillis,
                            pressure = down.pressure.coerceIn(0f, 1f),
                            strokeId = currentStrokeId,
                        ),
                    )
                    do {
                        val event = awaitPointerEvent()
                        val primary = event.changes.firstOrNull { it.id == down.id } ?: break
                        // Preserve high-rate 120Hz/240Hz batched historical motion samples
                        for (hist in primary.historical) {
                            onPointSampled(
                                GesturePoint(
                                    x = hist.position.x,
                                    y = hist.position.y,
                                    timestampMillis = hist.uptimeMillis,
                                    pressure = primary.pressure.coerceIn(0f, 1f),
                                    strokeId = currentStrokeId,
                                ),
                            )
                        }
                        if (primary.pressed) {
                            onPointSampled(
                                GesturePoint(
                                    x = primary.position.x,
                                    y = primary.position.y,
                                    timestampMillis = primary.uptimeMillis,
                                    pressure = primary.pressure.coerceIn(0f, 1f),
                                    strokeId = currentStrokeId,
                                ),
                            )
                        }
                        primary.consume()
                    } while (event.changes.any { it.pressed })
                    onStrokeFinished(currentStrokeId)
                }
            },
    ) {
        // Subtle spatial calibration reticle
        val step = 48.dp.toPx()
        var gx = step
        while (gx < size.width) {
            var gy = step
            while (gy < size.height) {
                drawCircle(color = gridColor, radius = 1.2.dp.toPx(), center = Offset(gx, gy))
                gy += step
            }
            gx += step
        }

        // AI Predicted Ghost Completion Path (dashed luminous preview)
        if (ghostTrailPoints.size >= 2) {
            val ghostPath = buildStrokePath(ghostTrailPoints)
            drawPath(
                path = ghostPath,
                color = extended.aiGhostTrail,
                style = Stroke(
                    width = 3.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), phase = 0f),
                ),
            )
        }

        // Velocity-coupled luminous active gesture ribbon + outer bloom halo
        if (activePoints.size >= 2) {
            val ribbonBrush = Brush.linearGradient(
                colors = listOf(extended.gestureRibbonStart, extended.gestureRibbonEnd),
            )
            for (i in 1 until activePoints.size) {
                val a = activePoints[i - 1]
                val b = activePoints[i]
                if (a.strokeId != b.strokeId) continue
                val dt = (b.timestampMillis - a.timestampMillis).coerceAtLeast(1L)
                val speed = hypot(b.x - a.x, b.y - a.y) / dt.toFloat()
                val dynamicWidth = (8.5f - (speed * 1.15f).coerceIn(0f, 4.5f)).dp.toPx()
                drawLine(
                    color = extended.gestureRibbonStart.copy(alpha = 0.22f),
                    start = Offset(a.x, a.y),
                    end = Offset(b.x, b.y),
                    strokeWidth = dynamicWidth * 2.4f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    brush = ribbonBrush,
                    start = Offset(a.x, a.y),
                    end = Offset(b.x, b.y),
                    strokeWidth = dynamicWidth,
                    cap = StrokeCap.Round,
                )
            }

            // Live contact aura at latest pointer sample
            val tip = activePoints.last()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        extended.gestureRibbonEnd.copy(alpha = 0.75f),
                        Color.Transparent,
                    ),
                    center = Offset(tip.x, tip.y),
                    radius = 26.dp.toPx(),
                ),
                radius = 26.dp.toPx(),
                center = Offset(tip.x, tip.y),
            )
            drawCircle(
                color = Color.White,
                radius = 4.5.dp.toPx(),
                center = Offset(tip.x, tip.y),
            )
        }
    }
}

private fun buildStrokePath(points: List<GesturePoint>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]
        if (curr.strokeId != prev.strokeId) {
            moveTo(curr.x, curr.y)
        } else {
            val midX = (prev.x + curr.x) * 0.5f
            val midY = (prev.y + curr.y) * 0.5f
            quadraticTo(prev.x, prev.y, midX, midY)
        }
    }
}
