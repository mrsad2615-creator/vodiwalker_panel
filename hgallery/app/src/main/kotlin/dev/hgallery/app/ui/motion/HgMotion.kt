package dev.hgallery.app.ui.motion

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * The app's motion vocabulary, in one place.
 *
 * ## Why springs and not durations
 *
 * A duration with an easing curve cannot be interrupted: retargeting mid-flight
 * restarts from rest, which reads as a stutter on a grid that is being scrolled while
 * a tile is still settling. A spring keeps its velocity, so motion that the user
 * interrupts continues instead of jumping back.
 *
 * ## Where the numbers come from
 *
 * These are the canonical Material spring constants, not invented ones: damping ratio
 * and stiffness as published in the Material motion tokens. Spatial springs may
 * overshoot because they move things. Effect springs (colour, opacity) must not
 * overshoot, so their damping ratio is 1.0.
 *
 * ## Reduced motion
 *
 * If the platform's animation scale is zero or near it, the user has asked for less
 * motion. Every helper here collapses to an instant change in that case, instead of
 * animating and hoping nobody minds.
 */

/** Spatial spring for small components: press feedback, chip selection. */
private const val FAST_SPATIAL_DAMPING = 0.9f
private const val FAST_SPATIAL_STIFFNESS = 1400f

/** Spatial spring for medium surfaces: sheets, the nav indicator. */
private const val DEFAULT_SPATIAL_DAMPING = 0.9f
private const val DEFAULT_SPATIAL_STIFFNESS = 700f

/** Spatial spring for full-screen change: opening a photo, switching tabs. */
private const val SLOW_SPATIAL_DAMPING = 0.9f
private const val SLOW_SPATIAL_STIFFNESS = 300f

/** Effect springs never overshoot: a colour that overshoots looks like a flash. */
private const val FAST_EFFECT_DAMPING = 1.0f
private const val FAST_EFFECT_STIFFNESS = 3800f
private const val DEFAULT_EFFECT_DAMPING = 1.0f
private const val DEFAULT_EFFECT_STIFFNESS = 1600f

/** Fraction of full animation scale below which motion is treated as off. */
private const val REDUCED_MOTION_THRESHOLD = 0.05f

object HgMotion {

    /** Press feedback and other quick, small movements. */
    fun fastSpatial(reduceMotion: Boolean = false): AnimationSpec<Float> =
        specOrSnap(reduceMotion, FAST_SPATIAL_DAMPING, FAST_SPATIAL_STIFFNESS)

    /** Moving a medium surface, such as the selected-tab indicator. */
    fun defaultSpatial(reduceMotion: Boolean = false): AnimationSpec<Float> =
        specOrSnap(reduceMotion, DEFAULT_SPATIAL_DAMPING, DEFAULT_SPATIAL_STIFFNESS)

    /** A whole screen arriving. */
    fun slowSpatial(reduceMotion: Boolean = false): AnimationSpec<Float> =
        specOrSnap(reduceMotion, SLOW_SPATIAL_DAMPING, SLOW_SPATIAL_STIFFNESS)

    /** Colour and opacity: no overshoot, by construction. */
    fun fastEffects(reduceMotion: Boolean = false): AnimationSpec<Float> =
        specOrSnap(reduceMotion, FAST_EFFECT_DAMPING, FAST_EFFECT_STIFFNESS)

    fun defaultEffects(reduceMotion: Boolean = false): AnimationSpec<Float> =
        specOrSnap(reduceMotion, DEFAULT_EFFECT_DAMPING, DEFAULT_EFFECT_STIFFNESS)

    /**
     * The expressive curve: quick to leave, slow to arrive, with no overshoot.
     *
     * Used for the play-button morph and the toolbar, where a spring would bounce in
     * a place that should feel controlled.
     */
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** The matching curve for something that is leaving. */
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    private fun specOrSnap(damping: Float, stiffness: Float, reduceMotion: Boolean): SpringSpec<Float> =
        if (reduceMotion) infiniteStiff() else spring(dampingRatio = damping, stiffness = stiffness)

    private fun infiniteStiff(): SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 100_000f)
}

/**
 * True when the user asked the system to reduce or remove animation.
 *
 * The platform signals this through the animator duration scale. A scale of zero means
 * "no animation", which is what a screen-reader user or a motion-sensitive user sets.
 * The value is read once per composition and cached with the resolver as the key.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        val scale = runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        scale < REDUCED_MOTION_THRESHOLD
    }
}

/**
 * Scale for a control while it is pressed.
 *
 * Applying this to a tile or a button is the difference between a tap that works and a
 * tap that feels like it worked: the surface acknowledges the finger before the result
 * arrives. [pressedScale] stays close to 1 so the tile does not visibly jump.
 */
@Composable
fun rememberPressScale(
    interaction: InteractionSource,
    pressedScale: Float = 0.96f,
): Float {
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = rememberReduceMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = HgMotion.fastSpatial(reduceMotion),
        label = "pressScale",
    )
    return scale
}

/**
 * The same scaling as a modifier, for a control that owns its interaction source.
 *
 * `graphicsLayer` is used rather than layout padding so the transform happens at draw
 * time: it cannot trigger a re-layout of the grid beneath it.
 */
fun Modifier.hgScale(scale: Float): Modifier =
    if (scale == 1f) this else this.graphicsLayer { scaleX = scale; scaleY = scale }

/**
 * How far one item is into a staggered entrance.
 *
 * One animation drives the whole screen; each item reads a window of it. This keeps a
 * twenty-tile entrance to a single animated value instead of twenty, which matters on
 * the low-end hardware this app targets.
 *
 * @param progress the screen's entrance animation, from 0 to 1.
 * @param index the item's position, used to offset its window.
 * @param step how much of the animation each item waits behind the one before it.
 */
fun staggerFraction(progress: Float, index: Int, step: Float = 0.06f): Float {
    if (progress >= 1f) return 1f
    // Beyond the eighth item the delay would push the last tile past the end of the
    // animation, so the windows are clamped to the first few items.
    val offset = (index.coerceIn(0, MAX_STAGGER_STEPS) * step)
    val span = (1f - offset).coerceAtLeast(MIN_STAGGER_SPAN)
    return ((progress - offset) / span).coerceIn(0f, 1f)
}

/** After this many items, further delay would outlast the entrance itself. */
private const val MAX_STAGGER_STEPS = 8

/** The smallest window worth dividing by; below this the stagger stops being visible. */
private const val MIN_STAGGER_SPAN = 0.4f
