package com.vodiwalker.gesture.engine.smartaction

import com.vodiwalker.gesture.core.common.AppResult
import com.vodiwalker.gesture.core.common.GestureError
import com.vodiwalker.gesture.core.common.MonotonicClock
import com.vodiwalker.gesture.core.common.SyntheticGestureGuard
import com.vodiwalker.gesture.core.model.ActionCommand
import com.vodiwalker.gesture.core.model.ActionGuard
import com.vodiwalker.gesture.core.model.ActionNode
import com.vodiwalker.gesture.core.model.AppCategory
import com.vodiwalker.gesture.core.model.AppPermission
import com.vodiwalker.gesture.core.model.AppTriggerRule
import com.vodiwalker.gesture.core.model.CompiledHapticPlan
import com.vodiwalker.gesture.core.model.DevicePowerTelemetry
import com.vodiwalker.gesture.core.model.ExecutionEnvironment
import com.vodiwalker.gesture.core.model.GesturePoint
import com.vodiwalker.gesture.core.model.HapticPreset
import com.vodiwalker.gesture.core.model.HapticPrimitiveType
import com.vodiwalker.gesture.core.model.HardwareToggleType
import com.vodiwalker.gesture.core.model.MediaControlType
import com.vodiwalker.gesture.core.model.NodeExecutionStatus
import com.vodiwalker.gesture.core.model.PowerMode
import com.vodiwalker.gesture.core.model.SmartActionGraph
import com.vodiwalker.gesture.core.model.SyntheticStrokeSpec
import com.vodiwalker.gesture.core.model.SystemAccessibilityAction
import com.vodiwalker.gesture.core.model.ThermalSeverity
import com.vodiwalker.gesture.core.model.TriggerScope
import com.vodiwalker.gesture.core.model.VibratorCapabilities
import com.vodiwalker.gesture.engine.haptics.HapticChoreographer
import com.vodiwalker.gesture.engine.power.AdaptivePowerGovernor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmartActionHapticsAndPowerEngineTest {

    private val baseEnv = ExecutionEnvironment(
        foregroundPackage = "com.google.android.youtube",
        foregroundActivity = "com.google.android.apps.youtube.app.WatchWhileActivity",
        appCategory = AppCategory.MEDIA_VIDEO,
        batteryPercent = 75,
        hourOfDay = 21,
        isDeviceUnlocked = true,
        isHeadsetConnected = true,
        isPrivacySensitiveWindow = false,
        grantedPermissions = setOf(AppPermission.ACCESSIBILITY_SERVICE),
    )

    @Test
    fun validatorRejectsCyclicActionGraphsAndReportsRequiredPermissions() {
        val cyclic = SmartActionGraph(
            id = "bad_cycle",
            name = "Cyclic",
            description = "Must fail",
            entryNodeId = "n1",
            nodes = listOf(
                ActionNode("n1", ActionCommand.GlobalSystemAction(SystemAccessibilityAction.BACK), listOf("n2")),
                ActionNode("n2", ActionCommand.ToggleHardware(HardwareToggleType.TORCH_FLASHLIGHT), listOf("n1")),
            ),
        )
        val validation = ActionGraphValidator.validate(cyclic)
        assertIs<AppResult.Err<GestureError>>(validation)
        assertIs<GestureError.CyclicActionGraph>(validation.error)

        val perms = ActionGraphValidator.requiredPermissions(cyclic)
        assertEquals(setOf(AppPermission.ACCESSIBILITY_SERVICE, AppPermission.CAMERA_TORCH_CONTROL), perms)
    }

    @Test
    fun executesMultiNodeDagWithFallbackCircuitBreakerAndSyntheticToken() {
        var now = 5_000L
        val clock = MonotonicClock { now }
        val guard = SyntheticGestureGuard(safetyMarginMillis = 100L, clock = clock)

        val executed = mutableListOf<String>()
        val engine = SmartActionEngine(
            executor = { cmd, _ ->
                when (cmd) {
                    is ActionCommand.OpenDeepLink -> throw IllegalStateException("Target activity missing")
                    is ActionCommand.MediaTransport -> {
                        executed.add("media:${cmd.control}")
                        AppResult.Ok(Unit)
                    }
                    is ActionCommand.SynthesizeGesture -> {
                        executed.add("gesture:${cmd.strokes.size}")
                        AppResult.Ok(Unit)
                    }
                    is ActionCommand.EmitHaptic -> {
                        executed.add("haptic:${cmd.preset.id}")
                        AppResult.Ok(Unit)
                    }
                    else -> AppResult.Ok(Unit)
                }
            },
            syntheticGuard = guard,
            clock = clock,
            breakerFailureThreshold = 2,
            breakerResetMillis = 5_000L,
        )

        val graph = SmartActionGraph(
            id = "cinema_macro",
            name = "Cinema Quick Routine",
            description = "Haptic -> DeepLink (with fallback to PlayPause) -> Synthetic Double Swipe",
            entryNodeId = "haptic",
            nodes = listOf(
                ActionNode("haptic", ActionCommand.EmitHaptic(HapticPreset.CONFIRM_CINEMATIC), listOf("deeplink")),
                ActionNode(
                    id = "deeplink",
                    command = ActionCommand.OpenDeepLink("vnd.youtube://pip"),
                    fallbackNodeId = "fallback_media",
                    maxRetries = 1,
                ),
                ActionNode(
                    id = "fallback_media",
                    command = ActionCommand.MediaTransport(MediaControlType.PLAY_PAUSE),
                    nextNodeIds = listOf("synth_swipe"),
                    guard = ActionGuard.AllOf(listOf(ActionGuard.DeviceUnlocked, ActionGuard.BatteryAtLeast(20))),
                ),
                ActionNode(
                    id = "synth_swipe",
                    command = ActionCommand.SynthesizeGesture(
                        listOf(
                            SyntheticStrokeSpec(
                                waypoints = listOf(GesturePoint(500f, 1200f, 0L), GesturePoint(500f, 500f, 150L)),
                                durationMillis = 150L,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val report = engine.executeGraph(graph, baseEnv, triggerRuleId = "rule_yt")
        assertTrue(report.overallSuccess, "Fallback should recover macro: $report")
        assertNotNull(report.syntheticGestureToken)
        assertTrue(guard.isSyntheticWindowActive(), "Synthetic guard must arm immediately")
        assertEquals(
            listOf("haptic:confirm_cinematic", "media:PLAY_PAUSE", "gesture:1"),
            executed,
        )
        assertEquals(NodeExecutionStatus.FAILED_RECOVERED_BY_FALLBACK, report.traces[1].status)
        assertEquals(2, report.traces[1].attempts)

        // Second failure trips circuit breaker; third call short-circuits cleanly without exception
        engine.executeGraph(graph, baseEnv)
        val third = engine.executeGraph(graph, baseEnv)
        assertEquals(NodeExecutionStatus.BLOCKED_CIRCUIT_OPEN, third.traces[1].status)
        assertTrue(third.overallSuccess)
    }

    @Test
    fun appTriggerResolverHonorsSpecificityHierarchyAndCooldown() {
        var now = 10_000L
        val resolver = AppTriggerResolver(MonotonicClock { now })
        val rules = listOf(
            AppTriggerRule("r_global", "swipe_right", "g_back", TriggerScope.Global, cooldownMillis = 300L),
            AppTriggerRule(
                "r_cat",
                "swipe_right",
                "g_seek",
                TriggerScope.Category(AppCategory.MEDIA_VIDEO),
                cooldownMillis = 300L,
            ),
            AppTriggerRule(
                "r_pkg",
                "swipe_right",
                "g_yt_chapter",
                TriggerScope.Package("com.google.android.youtube"),
                cooldownMillis = 300L,
            ),
        )

        val first = resolver.resolve("swipe_right", rules, baseEnv)
        assertEquals("r_pkg", first?.id)

        // Immediate repeat within 300ms falls through package rule cooldown
        now += 50L
        val withinCooldown = resolver.resolve("swipe_right", rules, baseEnv)
        assertEquals("r_cat", withinCooldown?.id)

        // After cooldown window expires, package-specific rule wins again
        now += 400L
        assertEquals("r_pkg", resolver.resolve("swipe_right", rules, baseEnv)?.id)

        // Privacy-sensitive window yields null
        assertNull(resolver.resolve("swipe_right", rules, baseEnv.copy(isPrivacySensitiveWindow = true)))
    }

    @Test
    fun hapticChoreographerNegotiatesHardwareCapabilitiesAndPowerBudget() {
        val choreographer = HapticChoreographer(MonotonicClock { 1_000L })

        val flagshipApi35 = VibratorCapabilities(
            hasVibrator = true,
            hasAmplitudeControl = true,
            apiLevel = 35,
            supportedPrimitives = HapticPrimitiveType.entries.toSet(),
        )
        val midRangeAmplitudeOnly = VibratorCapabilities(
            hasVibrator = true,
            hasAmplitudeControl = true,
            apiLevel = 34,
            supportedPrimitives = emptySet(),
        )
        val legacyBasic = VibratorCapabilities(
            hasVibrator = true,
            hasAmplitudeControl = false,
            apiLevel = 29,
            supportedPrimitives = emptySet(),
        )

        val rich = choreographer.compilePreset(HapticPreset.CONFIRM_CINEMATIC, flagshipApi35)
        assertIs<CompiledHapticPlan.RichComposition>(rich)
        assertEquals(3, rich.steps.size)

        val wave = choreographer.compilePreset(HapticPreset.CONFIRM_CINEMATIC, midRangeAmplitudeOnly)
        assertIs<CompiledHapticPlan.AmplitudeWaveform>(wave)
        assertEquals(wave.timingsMillis.size, wave.amplitudes.size)

        val predefined = choreographer.compilePreset(HapticPreset.REJECT_DOUBLE_THUD, legacyBasic)
        assertIs<CompiledHapticPlan.PredefinedFallback>(predefined)

        // ULTRA_SAVER power multiplier = 0f completely suppresses LRA energy draw
        val suppressed = choreographer.compilePreset(
            HapticPreset.CONFIRM_CRISP,
            flagshipApi35,
            powerBudgetMultiplier = 0f,
        )
        assertIs<CompiledHapticPlan.Suppressed>(suppressed)
    }

    @Test
    fun adaptivePowerGovernorEnforcesDozeThermalBatteryAndPrivacyBudgets() {
        val governor = AdaptivePowerGovernor()
        val nominal = DevicePowerTelemetry(
            batteryPercent = 80,
            isCharging = false,
            isSystemPowerSaveMode = false,
            isDeviceIdleDoze = false,
            isScreenInteractive = true,
            thermalSeverity = ThermalSeverity.NONE,
            isPrivacySensitiveWindow = false,
            recentGesturesPerMinute = 1.2f,
        )

        val balanced = governor.evaluate(nominal)
        assertEquals(PowerMode.BALANCED, balanced.mode)
        assertEquals(90, balanced.touchSampleRateHz)
        assertFalse(balanced.listenWindowContentChanges)

        // Screen-off / Doze drops battery drain to 0.0% and halts all wakeups
        val doze = governor.evaluate(nominal.copy(isScreenInteractive = false, userPreferredMode = PowerMode.PERFORMANCE))
        assertEquals(PowerMode.DOZE_SUSPENDED, doze.mode)
        assertEquals(0, doze.touchSampleRateHz)
        assertEquals(0f, doze.estimatedBatteryPercentPerHour)
        assertFalse(doze.enableOverlayRendering)

        // Severe thermal overrides user PERFORMANCE mode into ULTRA_SAVER
        val thermal = governor.evaluate(
            nominal.copy(thermalSeverity = ThermalSeverity.SEVERE, userPreferredMode = PowerMode.PERFORMANCE),
        )
        assertEquals(PowerMode.ULTRA_SAVER, thermal.mode)
        assertEquals(0f, thermal.hapticScaleMultiplier)

        // Privacy-sensitive window immediately hides overlay and halts sampling
        val privacy = governor.evaluate(nominal.copy(isPrivacySensitiveWindow = true))
        assertEquals(PowerMode.PRIVACY_SHIELD_SUSPENDED, privacy.mode)
        assertFalse(privacy.enableOverlayRendering)
    }
}
