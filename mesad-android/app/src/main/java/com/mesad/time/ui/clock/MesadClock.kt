package com.mesad.time.ui.clock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mesad.time.domain.model.ClockEngine
import com.mesad.time.domain.model.StudioPreset
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * MESAD Signature Clock — Compose Canvas rendering.
 * Native mode: procedural GLES geometry and shader in MesadClockView.
 * Performance mode: this efficient Compose Canvas renderer.
 *
 * Three engines:
 * - ORBIT: Orbital rings + precise hands, continuous motion
 * - CHRONOS: Luxury mechanical dial, multi-layer metallic indices
 * - NEXUS: Luminous core, spatial rings, 3D depth parallax
 */
@Composable
fun MesadClock(
    engine: ClockEngine,
    preset: StudioPreset,
    modifier: Modifier = Modifier.size(320.dp),
    showSeconds: Boolean = true,
    use24Hour: Boolean = false
) {
    var now by remember { mutableStateOf(ZonedDateTime.now(ZoneId.systemDefault())) }

    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now(ZoneId.systemDefault())
            // 16ms for 60fps, 33ms for Performance Mode (30fps)
            delay(if (preset.engine == ClockEngine.ORBIT) 16 else 33)
        }
    }

    val primaryColor = Color(preset.primaryColor)
    val accentColor = Color(preset.accentColor)
    val ringColor = Color(preset.ringColor)
    val coreColor = Color(preset.coreColor)

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.size(320.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 * preset.scale

            val hour = now.hour % 12
            val minute = now.minute
            val second = now.second + now.nano / 1_000_000_000f

            val hourAngle = ((hour + minute / 60f) / 12f) * 2 * PI - PI / 2
            val minuteAngle = ((minute + second / 60f) / 60f) * 2 * PI - PI / 2
            val secondAngle = (second / 60f) * 2 * PI - PI / 2

            when (engine) {
                ClockEngine.ORBIT -> drawOrbitClock(
                    center, radius, hourAngle, minuteAngle, secondAngle,
                    primaryColor, accentColor, ringColor, coreColor, preset
                )
                ClockEngine.CHRONOS -> drawChronosClock(
                    center, radius, hourAngle, minuteAngle, secondAngle,
                    primaryColor, accentColor, ringColor, coreColor, preset
                )
                ClockEngine.NEXUS -> drawNexusClock(
                    center, radius, hourAngle, minuteAngle, secondAngle,
                    primaryColor, accentColor, ringColor, coreColor, preset
                )
            }
        }
    }
}

// ── ORBIT Engine ─────────────────────────────────────────────────────────────

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOrbitClock(
    center: Offset,
    radius: Float,
    hourAngle: Double,
    minuteAngle: Double,
    secondAngle: Double,
    primary: Color,
    accent: Color,
    ring: Color,
    core: Color,
    preset: StudioPreset
) {
    val t = (System.currentTimeMillis() % 60000) / 60000f * 2 * PI.toFloat()

    // Outer orbital ring (titanium)
    drawCircle(
        color = ring,
        radius = radius,
        style = Stroke(width = radius * 0.08f)
    )
    // Champagne micro-rim
    drawCircle(
        color = accent.copy(alpha = 0.6f),
        radius = radius + radius * 0.04f,
        style = Stroke(width = radius * 0.015f)
    )
    // Inner cyan halo
    drawCircle(
        color = primary.copy(alpha = 0.35f * preset.glowIntensity),
        radius = radius * 0.72f,
        style = Stroke(width = radius * 0.02f)
    )
    // Sapphire core
    drawCircle(color = core, radius = radius * 0.68f)
    drawCircle(
        color = accent,
        radius = radius * 0.68f,
        style = Stroke(width = radius * 0.015f)
    )

    // Orbital satellite (rotating dot on outer ring)
    val satAngle = t * preset.rotationSpeed
    val satPos = Offset(
        center.x + cos(satAngle).toFloat() * radius,
        center.y + sin(satAngle).toFloat() * radius
    )
    drawCircle(color = primary, radius = radius * 0.045f, center = satPos)

    // Hour hand (titanium, thick)
    drawLine(
        color = ring,
        start = center,
        end = Offset(
            center.x + cos(hourAngle).toFloat() * radius * 0.45f,
            center.y + sin(hourAngle).toFloat() * radius * 0.45f
        ),
        strokeWidth = radius * 0.055f,
        cap = androidx.compose.ui.graphics.StrokeCap.Round
    )
    // Minute hand (titanium, medium)
    drawLine(
        color = ring,
        start = center,
        end = Offset(
            center.x + cos(minuteAngle).toFloat() * radius * 0.62f,
            center.y + sin(minuteAngle).toFloat() * radius * 0.62f
        ),
        strokeWidth = radius * 0.04f,
        cap = androidx.compose.ui.graphics.StrokeCap.Round
    )
    // Second hand (cyan, thin, continuous sweep)
    drawLine(
        color = primary,
        start = Offset(
            center.x - cos(secondAngle).toFloat() * radius * 0.12f,
            center.y - sin(secondAngle).toFloat() * radius * 0.12f
        ),
        end = Offset(
            center.x + cos(secondAngle).toFloat() * radius * 0.68f,
            center.y + sin(secondAngle).toFloat() * radius * 0.68f
        ),
        strokeWidth = radius * 0.018f,
        cap = androidx.compose.ui.graphics.StrokeCap.Round
    )

    // Center cap
    drawCircle(color = accent, radius = radius * 0.055f)
    drawCircle(color = primary, radius = radius * 0.028f)

    // 12 o'clock zenith wedge (champagne)
    val wedgeTop = Offset(center.x, center.y - radius * 1.08f)
    val wedgeLeft = Offset(center.x - radius * 0.09f, center.y - radius * 0.82f)
    val wedgeRight = Offset(center.x + radius * 0.09f, center.y - radius * 0.82f)
    drawPath(
        path = androidx.compose.ui.graphics.Path().apply {
            moveTo(wedgeTop.x, wedgeTop.y)
            lineTo(wedgeRight.x, wedgeRight.y)
            lineTo(wedgeLeft.x, wedgeLeft.y)
            close()
        },
        color = accent
    )
}

// ── CHRONOS Engine ───────────────────────────────────────────────────────────

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChronosClock(
    center: Offset,
    radius: Float,
    hourAngle: Double,
    minuteAngle: Double,
    secondAngle: Double,
    primary: Color,
    accent: Color,
    ring: Color,
    core: Color,
    preset: StudioPreset
) {
    // Multi-layer metallic dial
    drawCircle(color = core, radius = radius)
    drawCircle(
        color = ring.copy(alpha = 0.15f),
        radius = radius * 0.92f
    )
    drawCircle(
        color = ring,
        radius = radius,
        style = Stroke(width = radius * 0.06f)
    )
    drawCircle(
        color = accent,
        radius = radius * 0.94f,
        style = Stroke(width = radius * 0.012f)
    )

    // Engineered indices (12, 3, 6, 9 prominent; others minimal)
    for (i in 0 until 60) {
        val angle = (i / 60f) * 2 * PI - PI / 2
        val isHour = i % 5 == 0
        val isQuarter = i % 15 == 0
        val innerR = if (isQuarter) radius * 0.82f else if (isHour) radius * 0.86f else radius * 0.89f
        val outerR = radius * 0.93f
        val tickColor = if (isQuarter) accent else ring.copy(alpha = if (isHour) 0.9f else 0.4f)
        val tickWidth = if (isQuarter) radius * 0.025f else if (isHour) radius * 0.018f else radius * 0.008f

        drawLine(
            color = tickColor,
            start = Offset(
                center.x + cos(angle).toFloat() * innerR,
                center.y + sin(angle).toFloat() * innerR
            ),
            end = Offset(
                center.x + cos(angle).toFloat() * outerR,
                center.y + sin(angle).toFloat() * outerR
            ),
            strokeWidth = tickWidth
        )
    }

    // Sub-dial (small seconds at 6 o'clock)
    val subCenter = Offset(center.x, center.y + radius * 0.38f)
    val subRadius = radius * 0.18f
    drawCircle(color = core, radius = subRadius)
    drawCircle(
        color = ring,
        radius = subRadius,
        style = Stroke(width = radius * 0.015f)
    )
    drawLine(
        color = primary,
        start = subCenter,
        end = Offset(
            subCenter.x + cos(secondAngle).toFloat() * subRadius * 0.85f,
            subCenter.y + sin(secondAngle).toFloat() * subRadius * 0.85f
        ),
        strokeWidth = radius * 0.015f
    )

    // Hands (dauphine style — tapered)
    drawLine(
        color = ring,
        start = center,
        end = Offset(
            center.x + cos(hourAngle).toFloat() * radius * 0.48f,
            center.y + sin(hourAngle).toFloat() * radius * 0.48f
        ),
        strokeWidth = radius * 0.06f,
        cap = androidx.compose.ui.graphics.StrokeCap.Round
    )
    drawLine(
        color = ring,
        start = center,
        end = Offset(
            center.x + cos(minuteAngle).toFloat() * radius * 0.68f,
            center.y + sin(minuteAngle).toFloat() * radius * 0.68f
        ),
        strokeWidth = radius * 0.045f,
        cap = androidx.compose.ui.graphics.StrokeCap.Round
    )

    // Center cap with champagne detail
    drawCircle(color = accent, radius = radius * 0.06f)
    drawCircle(color = core, radius = radius * 0.03f)
}

// ── NEXUS Engine ─────────────────────────────────────────────────────────────

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNexusClock(
    center: Offset,
    radius: Float,
    hourAngle: Double,
    minuteAngle: Double,
    secondAngle: Double,
    primary: Color,
    accent: Color,
    ring: Color,
    core: Color,
    preset: StudioPreset
) {
    val t = (System.currentTimeMillis() % 120000) / 120000f * 2 * PI.toFloat()

    // Luminous core (radial gradient effect via layered circles)
    drawCircle(color = core, radius = radius * 0.55f)
    drawCircle(
        color = primary.copy(alpha = 0.08f * preset.glowIntensity),
        radius = radius * 0.55f
    )
    drawCircle(
        color = primary.copy(alpha = 0.15f * preset.glowIntensity),
        radius = radius * 0.38f
    )
    drawCircle(
        color = primary.copy(alpha = 0.25f * preset.glowIntensity),
        radius = radius * 0.22f
    )

    // Spatial rings (3 rings at different radii, rotating at different speeds)
    val ringSpeeds = listOf(1.0f, -0.6f, 0.35f)
    val ringRadii = listOf(0.95f, 0.78f, 0.62f)
    val ringColors = listOf(ring, primary, accent)

    ringSpeeds.forEachIndexed { index, speed ->
        val r = radius * ringRadii[index]
        val rot = t * speed * preset.rotationSpeed
        // Draw arc segments (broken ring effect)
        for (seg in 0 until 3) {
            val startAngle = (rot * 180 / PI + seg * 120).toFloat()
            drawArc(
                color = ringColors[index].copy(alpha = 0.7f - index * 0.15f),
                startAngle = startAngle,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(width = radius * (0.025f - index * 0.006f))
            )
        }
    }

    // Hands (luminous, thin)
    drawLine(
        color = primary,
        start = center,
        end = Offset(
            center.x + cos(hourAngle).toFloat() * radius * 0.42f,
            center.y + sin(hourAngle).toFloat() * radius * 0.42f
        ),
        strokeWidth = radius * 0.04f,
        cap = androidx.compose.ui.graphics.StrokeCap.Round
    )
    drawLine(
        color = primary,
        start = center,
        end = Offset(
            center.x + cos(minuteAngle).toFloat() * radius * 0.58f,
            center.y + sin(minuteAngle).toFloat() * radius * 0.58f
        ),
        strokeWidth = radius * 0.03f,
        cap = androidx.compose.ui.graphics.StrokeCap.Round
    )
    drawLine(
        color = accent,
        start = center,
        end = Offset(
            center.x + cos(secondAngle).toFloat() * radius * 0.65f,
            center.y + sin(secondAngle).toFloat() * radius * 0.65f
        ),
        strokeWidth = radius * 0.015f,
        cap = androidx.compose.ui.graphics.StrokeCap.Round
    )

    // Nexus core (bright center)
    drawCircle(color = primary, radius = radius * 0.07f)
    drawCircle(color = Color.White, radius = radius * 0.035f)
}
