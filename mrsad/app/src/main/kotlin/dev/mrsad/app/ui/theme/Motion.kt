package dev.mrsad.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import dev.mrsad.app.data.settings.GraphicsQuality

/**
 * The MRSAD motion system.
 *
 * Every animation in the app is built from the tokens in this file and from
 * nothing else. There are exactly two sources:
 *
 *  - the **durations** and **easings** are the published Material 3 tokens
 *    (`md.sys.motion.duration.*` / `md.sys.motion.easing.*`);
 *  - the **springs** are the Material 3 Expressive springs, which come in
 *    two families that are deliberately not interchangeable.
 *
 * ### Spatial vs. effects
 *
 *  - A **spatial** spring moves something that occupies space: position,
 *    size, scale, rotation. It is allowed to overshoot slightly, and that
 *    overshoot is exactly what makes a movement read as physical instead of
 *    mechanical.
 *  - An **effects** spring changes something that does not occupy space:
 *    opacity, colour. It is critically damped and therefore never overshoots
 *    — a colour that overshoots would travel through a colour that is not
 *    in the palette at all.
 *
 * Using a spatial spring for a colour is what makes an interface feel
 * cheap; using an effects spring for a position is what makes it feel
 * sluggish. Every animated property in the app is classified once, here.
 *
 * ### Reduced motion
 *
 * Under reduced motion the spatial family is switched off entirely: the
 * object still has to appear and disappear, but it must not travel. The
 * effects family keeps a short, calm fade, so the interface stays legible
 * without moving.
 */
object MrsadDuration {

    /** 50 ms — micro feedback such as a press highlight. */
    const val short1 = 50

    /** 100 ms — small, local state changes. */
    const val short2 = 100

    /** 150 ms — chips, small indicators. */
    const val short3 = 150

    /** 200 ms — the default for effects that fade. */
    const val short4 = 200

    /** 250 ms — small components appearing. */
    const val medium1 = 250

    /** 300 ms — containers, sheets. */
    const val medium2 = 300

    /** 350 ms — larger panels. */
    const val medium3 = 350

    /** 400 ms — full-screen transitions. */
    const val medium4 = 400

    /** 450 ms — long containers. */
    const val long1 = 450

    /** 500 ms — page-level transitions. */
    const val long2 = 500

    /** 550 ms — long page transitions. */
    const val long3 = 550

    /** 600 ms — the longest standard transition. */
    const val long4 = 600

    /** 700 ms — extra-long transitions. */
    const val extraLong1 = 700

    /** 800 ms — extra-long transitions. */
    const val extraLong2 = 800

    /** 900 ms — extra-long transitions. */
    const val extraLong3 = 900

    /** 1000 ms — the longest transition in the system. */
    const val extraLong4 = 1000
}

/**
 * The published Material 3 easing curves.
 *
 * `emphasized` and `standard` share the same curve (`cubic-bezier(.2, 0, 0,
 * 1)`) in Material 3; they are kept apart because the two families answer
 * different questions and the system distinguishes them by name.
 */
object MrsadEasing {

    /** `cubic-bezier(0.2, 0, 0, 1)` — the general-purpose curve. */
    val standard: CubicBezierEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** `cubic-bezier(0.3, 0, 1, 1)` — leaves quickly, arrives slowly. */
    val standardAccelerate: CubicBezierEasing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** `cubic-bezier(0, 0, 0, 1)` — leaves slowly, arrives quickly. */
    val standardDecelerate: CubicBezierEasing = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** `cubic-bezier(0.2, 0, 0, 1)` — the emphasised curve. */
    val emphasized: CubicBezierEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** `cubic-bezier(0.3, 0, 0.8, 0.15)` — the emphasised exit curve. */
    val emphasizedAccelerate: CubicBezierEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** `cubic-bezier(0.05, 0.7, 0.1, 1)` — the emphasised entry curve. */
    val emphasizedDecelerate: CubicBezierEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** `cubic-bezier(0.4, 0, 0.2, 1)` — the legacy curve. */
    val legacy: CubicBezierEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
}

/**
 * A Material 3 spring token: a damping ratio and a stiffness.
 *
 * Compose models a spring as a unit mass on a spring, so the two numbers
 * are the whole specification: the damping ratio decides how much the
 * movement overshoots (`1.0` never overshoots, `0.6` overshoots visibly)
 * and the stiffness decides how quickly it settles.
 */
@Immutable
data class MrsadSpring(val dampingRatio: Float, val stiffness: Float) {

    /** The Compose specification for this token. */
    fun <T> spec(): SpringSpec<T> = spring(dampingRatio = dampingRatio, stiffness = stiffness)
}

/**
 * The two Material 3 motion schemes.
 *
 * The *expressive* scheme is the default: it is the one that gives an
 * interface its character. The *standard* scheme is the utilitarian
 * variant, with tighter, almost imperceptible springs.
 */
enum class MrsadMotionScheme {
    EXPRESSIVE,
    STANDARD,
}

/**
 * The Material 3 Expressive and Standard spring tokens.
 *
 * Six springs per scheme: three tempos (fast / default / slow) for each of
 * the two families (spatial / effects).
 */
object MrsadSpringTokens {

    // ---- Expressive ---------------------------------------------------------

    /** Fast spatial: `damping 0.6 / stiffness 800` — a decisive, bouncy move. */
    val expressiveSpatialFast = MrsadSpring(0.6f, 800f)

    /** Default spatial: `damping 0.8 / stiffness 380` — a confident settle. */
    val expressiveSpatialDefault = MrsadSpring(0.8f, 380f)

    /** Slow spatial: `damping 0.8 / stiffness 200` — a heavy, calm move. */
    val expressiveSpatialSlow = MrsadSpring(0.8f, 200f)

    /** Fast effects: `damping 1.0 / stiffness 3800` — an instant fade. */
    val expressiveEffectsFast = MrsadSpring(1f, 3800f)

    /** Default effects: `damping 1.0 / stiffness 1600` — a crisp fade. */
    val expressiveEffectsDefault = MrsadSpring(1f, 1600f)

    /** Slow effects: `damping 1.0 / stiffness 800` — a gentle fade. */
    val expressiveEffectsSlow = MrsadSpring(1f, 800f)

    // ---- Standard -----------------------------------------------------------

    /** Fast spatial: `damping 0.9 / stiffness 1400`. */
    val standardSpatialFast = MrsadSpring(0.9f, 1400f)

    /** Default spatial: `damping 0.9 / stiffness 700`. */
    val standardSpatialDefault = MrsadSpring(0.9f, 700f)

    /** Slow spatial: `damping 0.9 / stiffness 300`. */
    val standardSpatialSlow = MrsadSpring(0.9f, 300f)

    /** Fast effects: `damping 1.0 / stiffness 3800`. */
    val standardEffectsFast = MrsadSpring(1f, 3800f)

    /** Default effects: `damping 1.0 / stiffness 1600`. */
    val standardEffectsDefault = MrsadSpring(1f, 1600f)

    /** Slow effects: `damping 1.0 / stiffness 800`. */
    val standardEffectsSlow = MrsadSpring(1f, 800f)
}

/**
 * The motion budget of the app.
 *
 * This object is the *only* thing that decides how the interface moves: it
 * carries the user's motion preference, the device's graphics tier and the
 * scheme the design is currently expressed in. Feature code asks it for a
 * specification and never hard-codes a duration or a spring.
 */
@Immutable
data class MrsadMotion(
    /** True while a continuous animation may run at all. */
    val animationsEnabled: Boolean,
    /** How many ambient particles the background may draw. */
    val particleCount: Int,
    /** A global multiplier for time-based animation. */
    val animationScale: Float,
    /** The device's graphics tier. */
    val quality: GraphicsQuality,
    /** The motion scheme the tokens are resolved from. */
    val scheme: MrsadMotionScheme = MrsadMotionScheme.EXPRESSIVE,
    /** True when the user asked for less motion. */
    val reducedMotion: Boolean = false,
    /** True inside instrumentation, where every animation must be static. */
    val deterministic: Boolean = false,
) {

    // ---- Token resolution ----------------------------------------------------

    /** The fast spatial spring of the active scheme. */
    val spatialFast: MrsadSpring
        get() = when (scheme) {
            MrsadMotionScheme.EXPRESSIVE -> MrsadSpringTokens.expressiveSpatialFast
            MrsadMotionScheme.STANDARD -> MrsadSpringTokens.standardSpatialFast
        }

    /** The default spatial spring of the active scheme. */
    val spatialDefault: MrsadSpring
        get() = when (scheme) {
            MrsadMotionScheme.EXPRESSIVE -> MrsadSpringTokens.expressiveSpatialDefault
            MrsadMotionScheme.STANDARD -> MrsadSpringTokens.standardSpatialDefault
        }

    /** The slow spatial spring of the active scheme. */
    val spatialSlow: MrsadSpring
        get() = when (scheme) {
            MrsadMotionScheme.EXPRESSIVE -> MrsadSpringTokens.expressiveSpatialSlow
            MrsadMotionScheme.STANDARD -> MrsadSpringTokens.standardSpatialSlow
        }

    /** The fast effects spring of the active scheme. */
    val effectsFast: MrsadSpring
        get() = when (scheme) {
            MrsadMotionScheme.EXPRESSIVE -> MrsadSpringTokens.expressiveEffectsFast
            MrsadMotionScheme.STANDARD -> MrsadSpringTokens.standardEffectsFast
        }

    /** The default effects spring of the active scheme. */
    val effectsDefault: MrsadSpring
        get() = when (scheme) {
            MrsadMotionScheme.EXPRESSIVE -> MrsadSpringTokens.expressiveEffectsDefault
            MrsadMotionScheme.STANDARD -> MrsadSpringTokens.standardEffectsDefault
        }

    /** The slow effects spring of the active scheme. */
    val effectsSlow: MrsadSpring
        get() = when (scheme) {
            MrsadMotionScheme.EXPRESSIVE -> MrsadSpringTokens.expressiveEffectsSlow
            MrsadMotionScheme.STANDARD -> MrsadSpringTokens.standardEffectsSlow
        }

    // ---- Specifications ------------------------------------------------------

    /**
     * A spatial specification: for anything that occupies space.
     *
     * Under reduced motion — and inside instrumentation — the movement is
     * switched off and the value snaps to its target.
     */
    fun <T> spatial(token: MrsadSpring): FiniteAnimationSpec<T> =
        if (deterministic || reducedMotion) {
            tween(durationMillis = 0)
        } else {
            token.spec()
        }

    /** Fast spatial motion: presses, small confirmations. */
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spatial(spatialFast)

    /** Default spatial motion: the movement of a container. */
    fun <T> spatialDefault(): FiniteAnimationSpec<T> = spatial(spatialDefault)

    /** Slow spatial motion: large, heavy movements. */
    fun <T> spatialSlow(): FiniteAnimationSpec<T> = spatial(spatialSlow)

    /**
     * An effects specification: for anything that only fades or changes
     * colour.
     *
     * Under reduced motion the fade survives — a short, calm one — because
     * an interface that changes without any transition is disorienting.
     */
    fun <T> effects(token: MrsadSpring): FiniteAnimationSpec<T> = when {
        deterministic -> tween(durationMillis = 0)
        reducedMotion -> tween(durationMillis = MrsadDuration.short4, easing = MrsadEasing.standard)
        else -> token.spec()
    }

    /** Fast effects motion: an instant fade. */
    fun <T> effectsFast(): FiniteAnimationSpec<T> = effects(effectsFast)

    /** Default effects motion: the workhorse fade. */
    fun <T> effectsDefault(): FiniteAnimationSpec<T> = effects(effectsDefault)

    /** Slow effects motion: a gentle fade. */
    fun <T> effectsSlow(): FiniteAnimationSpec<T> = effects(effectsSlow)

    /**
     * A timed specification for effects that are not a spring at all.
     *
     * Used for the few transitions that have a fixed, meaningful length —
     * a shimmer cycle, for instance.
     */
    fun <T> timed(durationMillis: Int, easing: androidx.compose.animation.core.Easing = MrsadEasing.standard): FiniteAnimationSpec<T> =
        if (deterministic) {
            tween(durationMillis = 0)
        } else {
            tween(
                durationMillis = (durationMillis * animationScale).toInt().coerceAtLeast(0),
                easing = easing,
            )
        }

    companion object {
        /**
         * Derives the motion budget from the user's settings.
         *
         * [dev.mrsad.app.testing.MrsadTestHooks.isDeterministic] collapses the
         * budget to "no motion at all" so instrumented tests never wait on an
         * infinite transition.
         */
        fun of(
            settings: dev.mrsad.app.data.settings.MrsadSettings,
            deterministic: Boolean = dev.mrsad.app.testing.MrsadTestHooks.isDeterministic,
        ) = MrsadMotion(
            animationsEnabled = settings.animationsEnabled && !deterministic,
            particleCount = if (deterministic) 0 else settings.particleCount,
            animationScale = 1f,
            quality = settings.quality,
            scheme = MrsadMotionScheme.EXPRESSIVE,
            reducedMotion = settings.reduceMotion,
            deterministic = deterministic,
        )
    }
}
