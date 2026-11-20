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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.mrsad.core.random.SplitMix64

/** Each major page has its own environmental identity (spec §10). */
enum class PageEnvironment {
    HOME, TYPE, READ, MIND, ANALYTICS, AI, ARENA, STUDIO, FOCUS, PLANNER, LIBRARY, SETTINGS
}

/**
 * Ambient page background.
 *
 * The atmosphere is built in four layers, from the back to the front:
 *
 *  1. a **base gradient** that falls from the page colour into the
 *     elevated one and back, so the page has a horizon instead of being a
 *     flat sheet of colour,
 *  2. two **light pools** that drift slowly across the page at different
 *     speeds — a still background is what makes an interface feel dead,
 *  3. a **particle field** with real depth: the particles near the viewer
 *     are bigger, brighter and move faster than the ones far away,
 *  4. a **vignette** that pulls the eye towards the middle of the page.
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
                    listOf(
                        colors.background,
                        colors.backgroundElevated,
                        colors.background,
                    )
                )
            )
    ) {
        val animated = !deterministic && motion.animationsEnabled && motion.particleCount > 0
        if (animated) {
            // Two clocks with different periods: the pools never line up
            // again, so the page never repeats a pattern.
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
            // A paused clock: when the app is not resumed the same Canvas is
            // recomposed once with a frozen progress — no frames are scheduled.
            val clock = if (appResumed) drift else 0f
            val counterClock = if (appResumed) counter else 0f

            Canvas(Modifier.fillMaxSize()) {
                drawAtmosphere(colors = colors, accentStrength = accentStrength, drift = clock, counter = counterClock)
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
                drawAtmosphere(colors = colors, accentStrength = accentStrength, drift = 0f, counter = 0f)
            }
        }

        content()
    }
}

/**
 * The light of the page.
 *
 * Two pools of light drift across the background at different speeds, and
 * a vignette closes the corners of the page so that the content in the
 * middle is what the eye goes to.
 */
private fun DrawScope.drawAtmosphere(
    colors: MrsadColors,
    accentStrength: Float,
    drift: Float,
    counter: Float,
) {
    val min = size.minDimension

    // The first pool: the accent light from above, drifting sideways.
    val poolOneX = size.width * (0.82f - 0.34f * drift)
    val poolOneY = size.height * (0.06f + 0.10f * drift)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.accent.copy(alpha = accentStrength * 0.32f),
                Color.Transparent,
            ),
            center = Offset(poolOneX, poolOneY),
            radius = min * 0.95f,
        ),
        radius = min * 0.95f,
        center = Offset(poolOneX, poolOneY),
    )

    // The second pool: a colder light from the opposite corner, drifting
    // the other way and on a different clock.
    val poolTwoX = size.width * (0.12f + 0.40f * counter)
    val poolTwoY = size.height * (0.94f - 0.12f * counter)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.metalMid.copy(alpha = 0.09f),
                Color.Transparent,
            ),
            center = Offset(poolTwoX, poolTwoY),
            radius = min * 0.85f,
        ),
        radius = min * 0.85f,
        center = Offset(poolTwoX, poolTwoY),
    )

    // The vignette.
    val vignetteRadius = maxOf(size.width, size.height) * 0.78f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                colors.background.copy(alpha = 0.55f),
            ),
            center = Offset(size.width / 2f, size.height * 0.42f),
            radius = vignetteRadius,
        )
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
    // Deterministic particle field: stable layout across recompositions.
    val particles = remember(count) {
        val rng = SplitMix64(0x4D52_5341L) // "MRSA"
        List(count) {
            val depth = rng.nextDouble().toFloat()
            ParticleField(
                x = rng.nextDouble().toFloat(),
                y = rng.nextDouble().toFloat(),
                radius = 0.5f + depth * 1.9f,
                speed = 0.25f + depth * 1.0f,
                depth = depth,
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
    /** How close the particle is to the viewer, from 0 to 1. */
    val depth: Float,
    val phase: Float,
    val accent: Boolean,
)

/**
 * The particle field.
 *
 * The particles are not a uniform snow: each one has a depth, and the
 * depth decides how big it is, how bright it is and how fast it travels.
 * That is what turns a flat overlay into a space.
 */
private fun DrawScope.drawParticles(
    particles: List<ParticleField>,
    progress: Float,
    tint: Color,
    accent: Color,
) {
    particles.forEach { p ->
        val travel = (p.y - progress * p.speed) % 1f
        val y = (if (travel < 0f) travel + 1f else travel) * size.height
        // The near particles drift sideways as well: that is the parallax.
        val x = ((p.x + progress * 0.04f * p.depth) % 1f) * size.width
        val twinkle = 0.35f + 0.35f * kotlin.math.sin((progress * 6.2832f * 2f + p.phase * 6.2832f)).toFloat()
        val color = if (p.accent) accent else tint
        drawCircle(
            color = color.copy(alpha = (0.05f + 0.17f * p.depth * twinkle).coerceIn(0f, 0.3f)),
            radius = p.radius,
            center = Offset(x, y),
        )
    }
}
