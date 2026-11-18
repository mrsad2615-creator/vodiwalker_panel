package com.vodiwalker.gesture.engine.haptics

import com.vodiwalker.gesture.core.common.MonotonicClock
import com.vodiwalker.gesture.core.model.CompiledHapticPlan
import com.vodiwalker.gesture.core.model.HapticPreset
import com.vodiwalker.gesture.core.model.HapticPrimitiveStep
import com.vodiwalker.gesture.core.model.HapticPrimitiveType
import com.vodiwalker.gesture.core.model.PredefinedHapticEffect
import com.vodiwalker.gesture.core.model.VibratorCapabilities
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * Engine #4: Parametric Haptic Choreography Engine.
 *
 * Compiles semantic [HapticPreset] patterns and real-time gesture velocity/curvature impulses into
 * optimal Android 12–15 `VibrationEffect.Composition` hardware primitive plans, automatically
 * degrading across a 3-tier capability ladder with zero deprecated APIs:
 * 1. `VibrationEffect.Composition` (API 30–35 LRA actuator primitives)
 * 2. `VibrationEffect.createWaveform(timings, amplitudes, -1)` (API 29+ custom envelope)
 * 3. `VibrationEffect.createPredefined(EFFECT_*)` (API 29+ basic vibrator)
 */
class HapticChoreographer(
    private val clock: MonotonicClock = MonotonicClock.SYSTEM,
    private val minContinuousTickIntervalMillis: Long = 38L,
) {
    private val lastContinuousTickMillis = AtomicLong(0L)

    /**
     * Compiles a semantic [preset] against [capabilities], scaling amplitude by both user
     * preference `[0f, 1f]` and active [powerBudgetMultiplier] (`:engine:power`).
     */
    fun compilePreset(
        preset: HapticPreset,
        capabilities: VibratorCapabilities,
        userIntensity: Float = 0.85f,
        powerBudgetMultiplier: Float = 1.0f,
    ): CompiledHapticPlan {
        val gain = (userIntensity.coerceIn(0f, 1f) * powerBudgetMultiplier.coerceIn(0f, 1f))
        if (!capabilities.hasVibrator || gain < 0.05f) {
            return CompiledHapticPlan.Suppressed
        }

        val idealSteps = compositionRecipe(preset).map { step ->
            step.copy(scale = (step.scale * gain).coerceIn(0.05f, 1.0f))
        }

        val canRunRich = capabilities.apiLevel >= 30 && idealSteps.all { step ->
            capabilities.apiLevel >= step.primitive.minApiLevel &&
                step.primitive in capabilities.supportedPrimitives
        }
        if (canRunRich) {
            return CompiledHapticPlan.RichComposition(idealSteps)
        }

        if (capabilities.hasAmplitudeControl) {
            return buildWaveformFallback(preset, gain)
        }

        return CompiledHapticPlan.PredefinedFallback(predefinedFallbackFor(preset))
    }

    /**
     * Produces rate-limited velocity- and curvature-coupled micro-ticks during active gesture
     * drawing on the global glassmorphic canvas, giving tactile detent feel at sharp corners.
     */
    fun compileContinuousStrokeTick(
        velocityPxPerMs: Float,
        turningAngleRad: Float,
        capabilities: VibratorCapabilities,
        userIntensity: Float = 0.85f,
        powerBudgetMultiplier: Float = 1.0f,
    ): CompiledHapticPlan {
        val gain = (userIntensity.coerceIn(0f, 1f) * powerBudgetMultiplier.coerceIn(0f, 1f))
        if (!capabilities.hasVibrator || gain < 0.15f) return CompiledHapticPlan.Suppressed

        val absAngle = kotlin.math.abs(turningAngleRad)
        val safeVelocity = if (velocityPxPerMs.isFinite()) velocityPxPerMs.coerceAtLeast(0f) else 0f
        // Emit tactile micro-detent only on distinct corners (> 26 deg) or crisp ballistic flicks
        if (absAngle < 0.45f && safeVelocity < 2.2f) return CompiledHapticPlan.Suppressed

        val now = clock.nowMillis()
        val prev = lastContinuousTickMillis.get()
        if (now - prev < minContinuousTickIntervalMillis) return CompiledHapticPlan.Suppressed
        if (!lastContinuousTickMillis.compareAndSet(prev, now)) return CompiledHapticPlan.Suppressed

        val cornerBoost = (absAngle / 1.8f).coerceIn(0f, 0.55f)
        val speedBoost = (safeVelocity / 8.0f).coerceIn(0f, 0.35f)
        val scale = ((0.18f + cornerBoost + speedBoost) * gain).coerceIn(0.10f, 0.72f)

        val preferred = when {
            capabilities.apiLevel >= 31 && HapticPrimitiveType.LOW_TICK in capabilities.supportedPrimitives ->
                HapticPrimitiveType.LOW_TICK
            capabilities.apiLevel >= 30 && HapticPrimitiveType.TICK in capabilities.supportedPrimitives ->
                HapticPrimitiveType.TICK
            else -> null
        }

        return when {
            preferred != null -> CompiledHapticPlan.RichComposition(
                listOf(HapticPrimitiveStep(preferred, scale, delayMillis = 0)),
            )
            capabilities.hasAmplitudeControl -> CompiledHapticPlan.AmplitudeWaveform(
                timingsMillis = longArrayOf(0L, 10L),
                amplitudes = intArrayOf(0, (scale * 190f).roundToInt().coerceIn(1, 255)),
            )
            else -> CompiledHapticPlan.PredefinedFallback(PredefinedHapticEffect.TICK)
        }
    }

    /** Looks up a [HapticPreset] by persisted ID with safe fallback to [HapticPreset.CONFIRM_CRISP]. */
    fun resolvePresetById(id: String?): HapticPreset =
        HapticPreset.entries.firstOrNull { it.id == id } ?: HapticPreset.CONFIRM_CRISP

    private fun compositionRecipe(preset: HapticPreset): List<HapticPrimitiveStep> = when (preset) {
        HapticPreset.EDGE_DETENT -> listOf(
            HapticPrimitiveStep(HapticPrimitiveType.LOW_TICK, scale = 0.42f, delayMillis = 0),
            HapticPrimitiveStep(HapticPrimitiveType.CLICK, scale = 0.65f, delayMillis = 18),
        )
        HapticPreset.STROKE_TICK -> listOf(
            HapticPrimitiveStep(HapticPrimitiveType.LOW_TICK, scale = 0.35f, delayMillis = 0),
        )
        HapticPreset.AI_LOCK_IN -> listOf(
            HapticPrimitiveStep(HapticPrimitiveType.TICK, scale = 0.48f, delayMillis = 0),
            HapticPrimitiveStep(HapticPrimitiveType.QUICK_RISE, scale = 0.55f, delayMillis = 22),
        )
        HapticPreset.CONFIRM_CRISP -> listOf(
            HapticPrimitiveStep(HapticPrimitiveType.CLICK, scale = 0.85f, delayMillis = 0),
        )
        HapticPreset.CONFIRM_CINEMATIC -> listOf(
            HapticPrimitiveStep(HapticPrimitiveType.QUICK_RISE, scale = 0.62f, delayMillis = 0),
            HapticPrimitiveStep(HapticPrimitiveType.CLICK, scale = 0.92f, delayMillis = 36),
            HapticPrimitiveStep(HapticPrimitiveType.LOW_TICK, scale = 0.30f, delayMillis = 44),
        )
        HapticPreset.REJECT_DOUBLE_THUD -> listOf(
            HapticPrimitiveStep(HapticPrimitiveType.THUD, scale = 0.78f, delayMillis = 0),
            HapticPrimitiveStep(HapticPrimitiveType.THUD, scale = 0.58f, delayMillis = 68),
        )
        HapticPreset.PRIVACY_SHIELD -> listOf(
            HapticPrimitiveStep(HapticPrimitiveType.QUICK_FALL, scale = 0.60f, delayMillis = 0),
        )
        HapticPreset.RECORD_SAMPLE_SAVED -> listOf(
            HapticPrimitiveStep(HapticPrimitiveType.SPIN, scale = 0.45f, delayMillis = 0),
            HapticPrimitiveStep(HapticPrimitiveType.CLICK, scale = 0.88f, delayMillis = 48),
        )
    }

    private fun buildWaveformFallback(preset: HapticPreset, gain: Float): CompiledHapticPlan.AmplitudeWaveform {
        /**
         * Clamps an amplitude scale into the `[0,255]` waveform domain.
         */
        fun amp(base: Int): Int = (base * gain).roundToInt().coerceIn(1, 255)
        return when (preset) {
            HapticPreset.EDGE_DETENT -> CompiledHapticPlan.AmplitudeWaveform(
                timingsMillis = longArrayOf(0L, 10L, 16L, 14L),
                amplitudes = intArrayOf(0, amp(95), 0, amp(175)),
            )
            HapticPreset.STROKE_TICK -> CompiledHapticPlan.AmplitudeWaveform(
                timingsMillis = longArrayOf(0L, 9L),
                amplitudes = intArrayOf(0, amp(85)),
            )
            HapticPreset.AI_LOCK_IN -> CompiledHapticPlan.AmplitudeWaveform(
                timingsMillis = longArrayOf(0L, 12L, 18L, 22L),
                amplitudes = intArrayOf(0, amp(110), 0, amp(165)),
            )
            HapticPreset.CONFIRM_CRISP -> CompiledHapticPlan.AmplitudeWaveform(
                timingsMillis = longArrayOf(0L, 16L),
                amplitudes = intArrayOf(0, amp(220)),
            )
            HapticPreset.CONFIRM_CINEMATIC -> CompiledHapticPlan.AmplitudeWaveform(
                timingsMillis = longArrayOf(0L, 20L, 14L, 18L, 24L, 10L),
                amplitudes = intArrayOf(0, amp(140), 0, amp(240), 0, amp(80)),
            )
            HapticPreset.REJECT_DOUBLE_THUD -> CompiledHapticPlan.AmplitudeWaveform(
                timingsMillis = longArrayOf(0L, 28L, 52L, 26L),
                amplitudes = intArrayOf(0, amp(205), 0, amp(155)),
            )
            HapticPreset.PRIVACY_SHIELD -> CompiledHapticPlan.AmplitudeWaveform(
                timingsMillis = longArrayOf(0L, 24L),
                amplitudes = intArrayOf(0, amp(150)),
            )
            HapticPreset.RECORD_SAMPLE_SAVED -> CompiledHapticPlan.AmplitudeWaveform(
                timingsMillis = longArrayOf(0L, 18L, 22L, 18L),
                amplitudes = intArrayOf(0, amp(130), 0, amp(230)),
            )
        }
    }

    private fun predefinedFallbackFor(preset: HapticPreset): PredefinedHapticEffect = when (preset) {
        HapticPreset.EDGE_DETENT,
        HapticPreset.STROKE_TICK,
        HapticPreset.AI_LOCK_IN,
        -> PredefinedHapticEffect.TICK

        HapticPreset.CONFIRM_CRISP,
        HapticPreset.PRIVACY_SHIELD,
        -> PredefinedHapticEffect.CLICK

        HapticPreset.CONFIRM_CINEMATIC,
        HapticPreset.RECORD_SAMPLE_SAVED,
        -> PredefinedHapticEffect.HEAVY_CLICK

        HapticPreset.REJECT_DOUBLE_THUD -> PredefinedHapticEffect.DOUBLE_CLICK
    }
}
