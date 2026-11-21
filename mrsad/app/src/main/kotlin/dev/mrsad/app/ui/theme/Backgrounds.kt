package dev.mrsad.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.mrsad.core.random.SplitMix64

/** Each major page has its own environmental identity. */
enum class PageEnvironment {
    HOME, TYPE, READ, EYE, MIND, ANALYTICS, AI, ARENA, STUDIO, FOCUS, PLANNER, LIBRARY, SETTINGS
}

/**
 * Dual-System Ambient Page Background (OBSIDIAN × NOIR).
 *
 * - **OBSIDIAN (Luxury / 3D / Cinematic)**:
 *   1. Deep vertical horizon gradient (`#07080B` -> `#0D1016` -> `#07080B`),
 *   2. Controlled cold-silver specular light pools drifting slowly,
 *   3. 3D depth-sorted particle field on a single Canvas,
 *   4. Soft radial vignette focusing attention toward the center.
 *
 * - **NOIR (Pure Minimalism / Swiss Editorial / Monochrome)**:
 *   Pure `#000000` canvas with an architectural Swiss editorial hairline grid
 *   and registration crosshairs — zero drifting light pools, zero particles,
 *   and zero background GPU frame loop.
 */
@Composable
fun AmbientBackground(
    environment: PageEnvironment,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MrsadTheme.colors
    val motion = MrsadTheme.motion

    if (colors.isNoir) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawNoirSwissGrid(colors)
            }
            content()
        }
        return
    }

    val deterministic = dev.mrsad.app.testing.MrsadTestHooks.isDeterministic
    var appResumed by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            appResumed = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        appResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val accentStrength = when (environment) {
        PageEnvironment.HOME -> 0.24f
        PageEnvironment.TYPE -> 0.14f
        PageEnvironment.READ -> 0.12f
        PageEnvironment.EYE -> 0.20f
        PageEnvironment.MIND -> 0.22f
        PageEnvironment.ANALYTICS -> 0.18f
        PageEnvironment.AI -> 0.24f
        PageEnvironment.ARENA -> 0.20f
        PageEnvironment.STUDIO -> 0.24f
        PageEnvironment.FOCUS -> 0.10f
        PageEnvironment.PLANNER -> 0.12f
        PageEnvironment.LIBRARY -> 0.12f
        PageEnvironment.SETTINGS -> 0.10f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.background,
                        colors.backgroundElevated,
                        colors.background,
                    ),
                ),
            ),
    ) {
        val animated = !deterministic && motion.animationsEnabled && motion.particleCount > 0
        if (animated) {
            val transition = rememberInfiniteTransition(label = "ambient")
            val drift by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 90_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "drift",
            )
            val counter by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 47_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "counter",
            )
            val clock = if (appResumed) drift else 0f
            val counterClock = if (appResumed) counter else 0f

            Canvas(Modifier.fillMaxSize()) {
                drawAtmosphere(
                    colors = colors,
                    accentStrength = accentStrength,
                    drift = clock,
                    counter = counterClock,
                )
            }
            ParticleLayer(
                count = motion.particleCount,
                progress = clock,
                tint = colors.metalMid,
                accent = colors.accent,
                enabled = appResumed,
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawAtmosphere(
                    colors = colors,
                    accentStrength = accentStrength,
                    drift = 0f,
                    counter = 0f,
                )
            }
        }

        content()
    }
}

/**
 * Draws the subtle Swiss editorial structural grid on the pure black NOIR canvas.
 */
private fun DrawScope.drawNoirSwissGrid(colors: MrsadColors) {
    val stroke = 1.dp.toPx()
    val gridColor = colors.border.copy(alpha = 0.24f)
    val margin = 16.dp.toPx()
    val usableWidth = (size.width - margin * 2f).coerceAtLeast(1f)

    // 4 architectural vertical column hairlines
    for (col in 0..4) {
        val x = margin + (usableWidth * col / 4f)
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = stroke,
        )
    }

    // Subtle horizontal baseline ticks every 96dp
    val stepY = 96.dp.toPx().coerceAtLeast(48f)
    var y = stepY
    while (y < size.height) {
        drawLine(
            color = gridColor.copy(alpha = 0.14f),
            start = Offset(margin, y),
            end = Offset(size.width - margin, y),
            strokeWidth = stroke,
        )
        y += stepY
    }
}

private fun DrawScope.drawAtmosphere(
    colors: MrsadColors,
    accentStrength: Float,
    drift: Float,
    counter: Float,
) {
    val min = size.minDimension

    val poolOneX = size.width * (0.82f - 0.34f * drift)
    val poolOneY = size.height * (0.06f + 0.10f * drift)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.accent.copy(alpha = accentStrength * 0.24f),
                Color.Transparent,
            ),
            center = Offset(poolOneX, poolOneY),
            radius = min * 0.95f,
        ),
        radius = min * 0.95f,
        center = Offset(poolOneX, poolOneY),
    )

    val poolTwoX = size.width * (0.12f + 0.40f * counter)
    val poolTwoY = size.height * (0.94f - 0.12f * counter)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.metalMid.copy(alpha = 0.08f),
                Color.Transparent,
            ),
            center = Offset(poolTwoX, poolTwoY),
            radius = min * 0.85f,
        ),
        radius = min * 0.85f,
        center = Offset(poolTwoX, poolTwoY),
    )

    val vignetteRadius = maxOf(size.width, size.height) * 0.78f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                colors.background.copy(alpha = 0.55f),
            ),
            center = Offset(size.width / 2f, size.height * 0.42f),
            radius = vignetteRadius,
        ),
    )
}

@Composable
private fun ParticleLayer(
    count: Int,
    progress: Float,
    tint: Color,
    accent: Color,
    enabled: Boolean,
) {
    val particles = remember(count) {
        val rng = SplitMix64(0x4D52_5341L)
        List(count) {
            val depth = rng.nextDouble().toFloat()
            ParticleField(
                x = rng.nextDouble().toFloat(),
                y = rng.nextDouble().toFloat(),
                radius = 0.5f + depth * 1.7f,
                speed = 0.22f + depth * 0.9f,
                depth = depth,
                phase = rng.nextDouble().toFloat(),
                accent = rng.nextInt(100) < 16,
            )
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        if (!enabled) return@Canvas
        drawParticles(particles, progress, tint, accent)
    }
}

private data class ParticleField(
    val x: Float,
    val y: Float,
    val radius: Float,
    val speed: Float,
    val depth: Float,
    val phase: Float,
    val accent: Boolean,
)

private fun DrawScope.drawParticles(
    particles: List<ParticleField>,
    progress: Float,
    tint: Color,
    accent: Color,
) {
    particles.forEach { p ->
        val travel = (p.y - progress * p.speed) % 1f
        val y = (if (travel < 0f) travel + 1f else travel) * size.height
        val x = ((p.x + progress * 0.04f * p.depth) % 1f) * size.width
        val twinkle = 0.35f + 0.35f * kotlin.math.sin((progress * 6.2832f * 2f + p.phase * 6.2832f)).toFloat()
        val color = if (p.accent) accent else tint
        drawCircle(
            color = color.copy(alpha = (0.04f + 0.14f * p.depth * twinkle).coerceIn(0f, 0.25f)),
            radius = p.radius,
            center = Offset(x, y),
        )
    }
}
