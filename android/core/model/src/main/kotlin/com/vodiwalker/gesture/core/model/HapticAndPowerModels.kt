package com.vodiwalker.gesture.core.model

/**
 * Built-in haptic feedback presets authored for Android 12–15 `VibrationEffect.Composition`.
 */
enum class HapticPreset(val id: String, val displayName: String) {
    EDGE_DETENT("edge_detent", "Edge Detent Snap"),
    STROKE_TICK("stroke_tick", "Velocity Ribbon Tick"),
    AI_LOCK_IN("ai_lock_in", "AI Prediction Lock"),
    CONFIRM_CRISP("confirm_crisp", "Crisp Confirmation"),
    CONFIRM_CINEMATIC("confirm_cinematic", "Cinematic Chord"),
    REJECT_DOUBLE_THUD("reject_double_thud", "Rejection Double Thud"),
    PRIVACY_SHIELD("privacy_shield", "Privacy Guard Pulse"),
    RECORD_SAMPLE_SAVED("record_sample_saved", "Calibration Milestone"),
}

/**
 * Hardware haptic primitives aligned with `VibrationEffect.Composition.PRIMITIVE_*` (API 30–31+).
 */
enum class HapticPrimitiveType(val minApiLevel: Int) {
    CLICK(30),
    TICK(30),
    LOW_TICK(31),
    THUD(31),
    SPIN(31),
    QUICK_RISE(30),
    SLOW_RISE(30),
    QUICK_FALL(30),
}

/**
 * Single timed primitive within a rich `VibrationEffect.Composition`.
 */
data class HapticPrimitiveStep(
    val primitive: HapticPrimitiveType,
    val scale: Float,
    val delayMillis: Int = 0,
)

/**
 * Device vibrator capability descriptor reported by Android's `VibratorManager`.
 */
data class VibratorCapabilities(
    val hasVibrator: Boolean,
    val hasAmplitudeControl: Boolean,
    val apiLevel: Int,
    val supportedPrimitives: Set<HapticPrimitiveType>,
    val resonantFrequencyHz: Float = 170f,
)

/**
 * Platform-agnostic compiled haptic execution plan dispatched to `VibratorManager`.
 */
sealed interface CompiledHapticPlan {
    /**
     * No haptic output (privacy shield, power mode, or missing vibrator).
     */
    data object Suppressed : CompiledHapticPlan

    /**
     * API 30/31+ `VibrationEffect.Composition` plan with primitive steps.
     */
    data class RichComposition(
        val steps: List<HapticPrimitiveStep>,
    ) : CompiledHapticPlan

    /**
     * API 26+ amplitude waveform plan (timings/amplitudes pairs).
     */
    data class AmplitudeWaveform(
        val timingsMillis: LongArray,
        val amplitudes: IntArray,
    ) : CompiledHapticPlan {
        override fun equals(other: Any?): Boolean =
            other is AmplitudeWaveform &&
                timingsMillis.contentEquals(other.timingsMillis) &&
                amplitudes.contentEquals(other.amplitudes)

        override fun hashCode(): Int =
            31 * timingsMillis.contentHashCode() + amplitudes.contentHashCode()
    }

    /**
     * Lowest tier: a single predefined `VibrationEffect` click.
     */
    data class PredefinedFallback(
        val effect: PredefinedHapticEffect,
    ) : CompiledHapticPlan
}

/** Maps to `VibrationEffect.EFFECT_*` (API 29+, non-deprecated). */
enum class PredefinedHapticEffect {
    TICK,
    CLICK,
    HEAVY_CLICK,
    DOUBLE_CLICK,
}

/**
 * Adaptive battery & thermal operating modes governed by [AdaptivePowerGovernor].
 */
enum class PowerMode {
    PERFORMANCE,
    BALANCED,
    ECO,
    ULTRA_SAVER,
    PRIVACY_SHIELD_SUSPENDED,
    DOZE_SUSPENDED,
}

/**
 * System thermal status aligned 1:1 with `PowerManager.THERMAL_STATUS_*` (API 29+).
 */
enum class ThermalSeverity {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
}

/**
 * Input telemetry snapshot consumed by the battery optimization engine.
 */
data class DevicePowerTelemetry(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val isSystemPowerSaveMode: Boolean,
    val isDeviceIdleDoze: Boolean,
    val isScreenInteractive: Boolean,
    val thermalSeverity: ThermalSeverity,
    val isPrivacySensitiveWindow: Boolean,
    val recentGesturesPerMinute: Float,
    val userPreferredMode: PowerMode? = null,
)

/**
 * Concrete scheduling & sampling budget enforced across Accessibility, Overlay, AI, and Haptics.
 */
data class PowerExecutionBudget(
    val mode: PowerMode,
    val touchSampleRateHz: Int,
    val accessibilityNotificationTimeoutMillis: Long,
    val listenWindowStateChanges: Boolean,
    val listenWindowContentChanges: Boolean,
    val enableOverlayRendering: Boolean,
    val enableFullEnsembleRecognition: Boolean,
    val enablePrefixAiPrediction: Boolean,
    val hapticScaleMultiplier: Float,
    val animationScaleMultiplier: Float,
    val estimatedBatteryPercentPerHour: Float,
)

/**
 * Global floating glassmorphic overlay geometry and interaction state.
 */
data class OverlayUiConfig(
    val dockEdge: DockEdge = DockEdge.END,
    val verticalFraction: Float = 0.45f,
    val handleHeightDp: Int = 88,
    val handleThicknessDp: Int = 8,
    val idleOpacity: Float = 0.58f,
    val enableGlassBlur: Boolean = true,
    val showAiGhostTrail: Boolean = true,
    val respectSystemGestureInsets: Boolean = true,
)

/**
 * Screen edge on which the global overlay handle docks.
 */
enum class DockEdge {
    START,
    END,
}

/**
 * Aggregate permission and compliance posture surfaced in the M3 onboarding & dashboard flows.
 */
data class PermissionStatusSnapshot(
    val accessibilityEnabled: Boolean,
    val prominentDisclosureAccepted: Boolean,
    val notificationsGranted: Boolean,
    val restrictedSettingsLikelyBlocking: Boolean,
    val canPerformGesturesVerified: Boolean,
) {
    val isCoreAutomationReady: Boolean
        get() = prominentDisclosureAccepted && accessibilityEnabled && canPerformGesturesVerified
}
