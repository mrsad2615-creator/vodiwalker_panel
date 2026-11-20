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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import dev.mrsad.app.testing.MrsadTestHooks
import dev.mrsad.core.random.SplitMix64

/** Each major page has its own environmental identity (spec §10). */
enum class PageEnvironment {
    HOME, TYPE, READ, MIND, ANALYTICS, AI, ARENA, STUDIO, FOCUS, PLANNER, LIBRARY, SETTINGS
}

/**
 * Ambient page background.
 *
 * Performance contract:
 * - Particles are drawn on a single Canvas, never as composables.
 * - The animation clock is suspended whenever the app is not RESUMED, so a
 *   backgrounded app costs no GPU.
 * - Quality=Performance and reduced-motion disable motion entirely while
 *   keeping the static atmosphere.
 */
@Composable
fun AmbientBackground(
    environment: PageEnvironment,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MrsadTheme.colors
    val motion = MrsadTheme.motion
    // An infinite transition requests frames forever, which would keep an
    // instrumented test from ever reaching idle.
    val deterministic = MrsadTestHooks.isDeterministic
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
        PageEnvironment.HOME -> 0.30f
        PageEnvironment.TYPE -> 0.14f
        PageEnvironment.READ -> 0.12f
        PageEnvironment.MIND -> 0.26f
        PageEnvironment.ANALYTICS -> 0.20f
        PageEnvironment.AI -> 0.34f
        PageEnvironment.ARENA -> 0.22f
        PageEnvironment.STUDIO -> 0.26f
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
                    listOf(colors.background, colors.backgroundElevated, colors.background)
                )
            )
    ) {
        // Static atmosphere: two soft light sources (top accent wash + horizon).
        Canvas(Modifier.fillMaxSize()) {
            val accentWash = Brush.radialGradient(
                colors = listOf(colors.accent.copy(alpha = accentStrength * 0.30f), Color.Transparent),
                center = Offset(size.width * 0.82f, size.height * 0.06f),
                radius = size.minDimension * 0.95f,
            )
            drawRect(accentWash)
            val horizon = Brush.radialGradient(
                colors = listOf(colors.metalMid.copy(alpha = 0.07f), Color.Transparent),
                center = Offset(size.width * 0.12f, size.height * 0.92f),
                radius = size.minDimension * 0.8f,
            )
            drawRect(horizon)
        }

        if (!deterministic && motion.particleCount > 0 && motion.animationsEnabled) {
            val transition = rememberInfiniteTransition(label = "ambient")
            val drift by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = (90_000f / motion.animationScale).toInt().coerceAtLeast(20_000),
                        easing = LinearEasing,
                    ),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "drift",
            )
            // A paused clock: when the app is not resumed the same Canvas is
            // recomposed once with a frozen progress — no frames are scheduled.
            val clock = if (appResumed) drift else 0f
            ParticleLayer(
                count = motion.particleCount,
                progress = clock,
                tint = colors.metalMid,
                accent = colors.accent,
                enabled = appResumed,
            )
        }

        content()
    }
}

@Composable
private fun ParticleLayer(
    count: Int,
    progress: Float,
    tint: Color,
    accent: Color,
    enabled: Boolean,
) {
    // Deterministic particle field: stable layout across recompositions.
    val particles = remember(count) {
        val rng = SplitMix64(0x4D52_5341L) // "MRSA"
        List(count) {
            ParticleField(
                x = rng.nextDouble().toFloat(),
                y = rng.nextDouble().toFloat(),
                radius = 0.6f + rng.nextDouble().toFloat() * 1.8f,
                speed = 0.3f + rng.nextDouble().toFloat() * 0.9f,
                phase = rng.nextDouble().toFloat(),
                accent = rng.nextInt(100) < 18,
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
        val twinkle = 0.35f + 0.35f * kotlin.math.sin((progress * 6.2832f * 2f + p.phase * 6.2832f)).toFloat()
        val color = if (p.accent) accent else tint
        drawCircle(
            color = color.copy(alpha = (0.10f + 0.14f * twinkle).coerceIn(0f, 0.3f)),
            radius = p.radius,
            center = Offset(p.x * size.width, y),
        )
    }
}
