package com.vodiwalker.gesture.engine.power

import com.vodiwalker.gesture.core.model.DevicePowerTelemetry
import com.vodiwalker.gesture.core.model.PowerExecutionBudget
import com.vodiwalker.gesture.core.model.PowerMode
import com.vodiwalker.gesture.core.model.ThermalSeverity

/**
 * Engine #5: Battery-Optimized Adaptive Power Governor.
 *
 * Dynamically throttles Accessibility event subscriptions, touch digitizer sampling rate, overlay
 * blur rendering, AI prefix inference, and LRA haptic output in response to Android 14/15 Doze,
 * Battery Saver, thermal headroom (`PowerManager.OnThermalStatusChangedListener`), screen state,
 * and sensitive-window privacy shields. Zero partial wake locks are ever acquired.
 */
class AdaptivePowerGovernor {

    /**
     * Evaluates [telemetry] deterministically and returns the optimal [PowerExecutionBudget].
     * Hard safety invariants (screen-off/Doze, severe thermals, sensitive windows, critical battery)
     * always override user-selected performance preferences.
     */
    fun evaluate(telemetry: DevicePowerTelemetry): PowerExecutionBudget {
        val safeBattery = telemetry.batteryPercent.coerceIn(0, 100)
        val resolvedMode = resolveMode(telemetry, safeBattery)
        return budgetFor(resolvedMode)
    }

    private fun resolveMode(telemetry: DevicePowerTelemetry, battery: Int): PowerMode {
        // 1. Absolute suspension when display is non-interactive or device enters Doze idle
        if (!telemetry.isScreenInteractive || telemetry.isDeviceIdleDoze) {
            return PowerMode.DOZE_SUSPENDED
        }

        // 2. Mandatory privacy pause over password, banking, Keyguard, or isAccessibilityDataSensitive windows
        if (telemetry.isPrivacySensitiveWindow) {
            return PowerMode.PRIVACY_SHIELD_SUSPENDED
        }

        // 3. Hardware thermal & critical battery protection
        if (telemetry.thermalSeverity >= ThermalSeverity.SEVERE || (!telemetry.isCharging && battery <= 10)) {
            return PowerMode.ULTRA_SAVER
        }

        // 4. Android Battery Saver or moderate thermal throttling
        if (telemetry.isSystemPowerSaveMode ||
            telemetry.thermalSeverity == ThermalSeverity.MODERATE ||
            (!telemetry.isCharging && battery <= 20)
        ) {
            return PowerMode.ECO
        }

        // 5. Respect explicit user preference when hardware envelope is healthy
        telemetry.userPreferredMode?.let { pref ->
            if (pref in setOf(PowerMode.PERFORMANCE, PowerMode.BALANCED, PowerMode.ECO, PowerMode.ULTRA_SAVER)) {
                return pref
            }
        }

        // 6. Demand-driven promotion to 120Hz PERFORMANCE during active bursts or while charging
        return when {
            telemetry.isCharging && telemetry.thermalSeverity == ThermalSeverity.NONE -> PowerMode.PERFORMANCE
            battery >= 45 && telemetry.recentGesturesPerMinute >= 4.0f -> PowerMode.PERFORMANCE
            battery <= 30 && telemetry.recentGesturesPerMinute < 0.5f -> PowerMode.ECO
            else -> PowerMode.BALANCED
        }
    }

    /**
     * Resolves the concrete execution budget for a [PowerMode] (pure, testable).
     */
    fun budgetFor(mode: PowerMode): PowerExecutionBudget = when (mode) {
        PowerMode.PERFORMANCE -> PowerExecutionBudget(
            mode = PowerMode.PERFORMANCE,
            touchSampleRateHz = 120,
            accessibilityNotificationTimeoutMillis = 60L,
            listenWindowStateChanges = true,
            listenWindowContentChanges = false, // Strictly event-driven; content queried only on-demand
            enableOverlayRendering = true,
            enableFullEnsembleRecognition = true,
            enablePrefixAiPrediction = true,
            hapticScaleMultiplier = 1.0f,
            animationScaleMultiplier = 1.0f,
            estimatedBatteryPercentPerHour = 0.55f,
        )
        PowerMode.BALANCED -> PowerExecutionBudget(
            mode = PowerMode.BALANCED,
            touchSampleRateHz = 90,
            accessibilityNotificationTimeoutMillis = 110L,
            listenWindowStateChanges = true,
            listenWindowContentChanges = false,
            enableOverlayRendering = true,
            enableFullEnsembleRecognition = true,
            enablePrefixAiPrediction = true,
            hapticScaleMultiplier = 0.85f,
            animationScaleMultiplier = 1.0f,
            estimatedBatteryPercentPerHour = 0.32f,
        )
        PowerMode.ECO -> PowerExecutionBudget(
            mode = PowerMode.ECO,
            touchSampleRateHz = 60,
            accessibilityNotificationTimeoutMillis = 220L,
            listenWindowStateChanges = true,
            listenWindowContentChanges = false,
            enableOverlayRendering = true,
            enableFullEnsembleRecognition = false, // Activates Protractor fast-path cascade
            enablePrefixAiPrediction = false,
            hapticScaleMultiplier = 0.50f,
            animationScaleMultiplier = 0.75f,
            estimatedBatteryPercentPerHour = 0.14f,
        )
        PowerMode.ULTRA_SAVER -> PowerExecutionBudget(
            mode = PowerMode.ULTRA_SAVER,
            touchSampleRateHz = 45,
            accessibilityNotificationTimeoutMillis = 400L,
            listenWindowStateChanges = true,
            listenWindowContentChanges = false,
            enableOverlayRendering = true,
            enableFullEnsembleRecognition = false,
            enablePrefixAiPrediction = false,
            hapticScaleMultiplier = 0.0f, // Suppresses LRA coil draw completely
            animationScaleMultiplier = 0.0f, // Instant state snaps, zero continuous shaders
            estimatedBatteryPercentPerHour = 0.06f,
        )
        PowerMode.PRIVACY_SHIELD_SUSPENDED -> PowerExecutionBudget(
            mode = PowerMode.PRIVACY_SHIELD_SUSPENDED,
            touchSampleRateHz = 0,
            accessibilityNotificationTimeoutMillis = 250L,
            listenWindowStateChanges = true, // Watch only for exit from sensitive foreground window
            listenWindowContentChanges = false,
            enableOverlayRendering = false, // Detaches/hides overlay completely over sensitive window
            enableFullEnsembleRecognition = false,
            enablePrefixAiPrediction = false,
            hapticScaleMultiplier = 0.0f,
            animationScaleMultiplier = 0.0f,
            estimatedBatteryPercentPerHour = 0.02f,
        )
        PowerMode.DOZE_SUSPENDED -> PowerExecutionBudget(
            mode = PowerMode.DOZE_SUSPENDED,
            touchSampleRateHz = 0,
            accessibilityNotificationTimeoutMillis = 1_000L,
            listenWindowStateChanges = false, // Zero accessibility event wakeups while screen is off
            listenWindowContentChanges = false,
            enableOverlayRendering = false,
            enableFullEnsembleRecognition = false,
            enablePrefixAiPrediction = false,
            hapticScaleMultiplier = 0.0f,
            animationScaleMultiplier = 0.0f,
            estimatedBatteryPercentPerHour = 0.0f,
        )
    }
}
