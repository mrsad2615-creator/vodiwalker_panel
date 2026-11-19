package dev.mrsad.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import dev.mrsad.app.ui.theme.MrsadTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * The MRSAD Core: the home dashboard's living object.
 *
 * It is procedural (no bitmaps, no fake 3D library): layered faceted metal
 * rings with real depth shading, an orbital ring and an energy field whose
 * intensity and layout are driven by *actual* user activity passed in by the
 * screen. When [energy] is 0 the object is calm; every completed exercise
 * raises it. Nothing here displays fake data — it only visualises values the
 * caller measured.
 */
@Composable
fun MrsadCore(
    energy: Float,
    modifier: Modifier = Modifier,
    pulse: Boolean = true,
    streakDays: Int = 0,
    bestWpm: Int = 0,
) {
    val colors = MrsadTheme.colors
    val motion = MrsadTheme.motion

    val phase = if (motion.animationsEnabled && pulse) {
        val transition = rememberInfiniteTransition(label = "core")
        val orbit by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween((24000f / motion.animationScale).toInt().coerceAtLeast(6000), easing = LinearEasing),
            ),
            label = "orbit",
        )
        val breathe by transition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(4200, easing = LinearEasing),
            ),
            label = "breathe",
        )
        CorePhase(orbitDeg = orbit, breathe = breathe)
    } else {
        CorePhase(orbitDeg = 24f, breathe = 1f)
    }

    Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCore(
                phase = phase,
                energy = energy.coerceIn(0f, 1f),
                colors = colors,
                streakDays = streakDays,
                quality = motion.quality,
            )
        }
    }
}

private data class CorePhase(val orbitDeg: Float, val breathe: Float)

private fun DrawScope.drawCore(
    phase: CorePhase,
    energy: Float,
    colors: dev.mrsad.app.ui.theme.MrsadColors,
    streakDays: Int,
    quality: dev.mrsad.app.data.settings.GraphicsQuality,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension * 0.30f * phase.breathe
    val highQuality = quality == dev.mrsad.app.data.settings.GraphicsQuality.HIGH

    // 1) Outer atmosphere: energy halo grows with real activity.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.accent.copy(alpha = 0.06f + 0.16f * energy),
                Color.Transparent,
            ),
            center = center,
            radius = radius * 2.2f,
        ),
        radius = radius * 2.2f,
        center = center,
    )

    // 2) Tilted orbital rings (the "spatial" layer).
    rotate(degrees = -18f + phase.orbitDeg * 0.25f, pivot = center) {
        for (i in 0 until if (highQuality) 3 else 2) {
            val ringRadius = radius * (1.32f + i * 0.16f)
            drawOval(
                color = colors.metalMid.copy(alpha = 0.16f - i * 0.04f),
                topLeft = Offset(center.x - ringRadius, center.y - ringRadius * 0.42f),
                size = androidx.compose.ui.geometry.Size(ringRadius * 2, ringRadius * 0.84f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }

    // 3) Faceted metallic body: 12 planar sectors with light from top-left.
    val facets = if (highQuality) 14 else 10
    for (i in 0 until facets) {
        val startAngle = i * (360f / facets) + phase.orbitDeg * 0.08f
        val shade = fakeLight(startAngle)
        val inner = radius * 0.55f
        val outer = radius
        val path = Path().apply {
            val a0 = Math.toRadians(startAngle.toDouble())
            val a1 = Math.toRadians((startAngle + 360f / facets * 0.92f).toDouble())
            moveTo(center.x + (cos(a0) * inner).toFloat(), center.y + (sin(a0) * inner).toFloat())
            lineTo(center.x + (cos(a0) * outer).toFloat(), center.y + (sin(a0) * outer).toFloat())
            lineTo(center.x + (cos(a1) * outer).toFloat(), center.y + (sin(a1) * outer).toFloat())
            lineTo(center.x + (cos(a1) * inner).toFloat(), center.y + (sin(a1) * inner).toFloat())
            close()
        }
        val base = lerpColor(colors.metalDark, colors.metalLight, shade)
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    base.copy(alpha = 0.75f),
                    lerpColor(colors.surface, base, 0.35f).copy(alpha = 0.85f),
                ),
                start = Offset(center.x - radius, center.y - radius),
                end = Offset(center.x + radius, center.y + radius),
            ),
        )
        drawPath(path, color = colors.metalLight.copy(alpha = 0.06f + 0.05f * shade), style = Stroke(0.6.dp.toPx()))
    }

    // 4) Inner glass sphere with a bright specular point.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.background.copy(alpha = 0.55f),
                colors.surfaceHigh.copy(alpha = 0.92f),
            ),
            center = Offset(center.x - radius * 0.25f, center.y - radius * 0.3f),
            radius = radius * 0.62f,
        ),
        radius = radius * 0.55f,
        center = center,
    )
    drawCircle(
        color = colors.metalLight.copy(alpha = 0.5f + 0.3f * energy),
        radius = (radius * 0.10f) * (0.8f + energy * 0.6f),
        center = Offset(center.x - radius * 0.22f, center.y - radius * 0.24f),
    )

    // 5) Energy arc: length = real activity progress.
    if (energy > 0.01f) {
        rotate(degrees = phase.orbitDeg, pivot = center) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color.Transparent, colors.accent.copy(alpha = 0.85f), Color.Transparent),
                    center = center,
                ),
                startAngle = 0f,
                sweepAngle = 300f * energy,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.78f, center.y - radius * 0.78f),
                size = androidx.compose.ui.geometry.Size(radius * 1.56f, radius * 1.56f),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }

    // 6) Streak sparks orbiting the core: one per active streak day (max 30).
    val sparks = streakDays.coerceIn(0, 30)
    if (sparks > 0) {
        val orbitRadius = radius * 1.18f
        for (s in 0 until sparks) {
            val angle = Math.toRadians((s * 360.0 / sparks) + phase.orbitDeg)
            drawCircle(
                color = colors.accent.copy(alpha = 0.55f),
                radius = 1.6.dp.toPx(),
                center = Offset(
                    center.x + (cos(angle) * orbitRadius).toFloat(),
                    center.y + (sin(angle) * orbitRadius * 0.55f).toFloat(),
                ),
            )
        }
    }
}

/** Deterministic fake light: top-left source. */
private fun fakeLight(angleDeg: Float): Float {
    val rad = Math.toRadians((angleDeg - 225f).toDouble())
    val v = (cos(rad) * 0.5 + 0.5).toFloat() // 1 at top-left, 0 at bottom-right
    return 0.18f + 0.82f * v * v
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)
