package com.vodiwalker.gesture.core.model

/**
 * Scoping rule binding a [GestureTemplate] to a [SmartActionGraph] either globally or within a
 * specific foreground application/activity.
 */
data class AppTriggerRule(
    val id: String,
    val gestureId: String,
    val actionGraphId: String,
    val scope: TriggerScope,
    val priority: Int = 0,
    val isEnabled: Boolean = true,
    val cooldownMillis: Long = 250L,
    val hapticOverrideId: String? = null,
)

/**
 * Scope of a trigger binding; specificity order Activity > Package > Category > Global.
 */
sealed interface TriggerScope {
    /** Active everywhere except sensitive/excluded windows. */
    data object Global : TriggerScope

    /** Active for any app within the target [category]. */
    data class Category(val category: AppCategory) : TriggerScope

    /** Active when [packageName] occupies the active foreground window. */
    data class Package(val packageName: String) : TriggerScope

    /** Highest specificity: matches exact [packageName] and [activityClassName]. */
    data class Activity(
        val packageName: String,
        val activityClassName: String,
    ) : TriggerScope
}

/**
 * Directed Acyclic Graph (DAG) representing a Smart-Action automation routine.
 */
data class SmartActionGraph(
    val id: String,
    val name: String,
    val description: String,
    val entryNodeId: String,
    val nodes: List<ActionNode>,
    val maxExecutionTimeMillis: Long = 5_000L,
)

/**
 * Single node in a [SmartActionGraph] with retry policy, timeout, and conditional routing.
 */
data class ActionNode(
    val id: String,
    val command: ActionCommand,
    val nextNodeIds: List<String> = emptyList(),
    val fallbackNodeId: String? = null,
    val guard: ActionGuard = ActionGuard.Always,
    val timeoutMillis: Long = 1_500L,
    val maxRetries: Int = 0,
    val retryBackoffMillis: Long = 80L,
)

/**
 * Runtime condition evaluated before executing an [ActionNode].
 */
sealed interface ActionGuard {
    /**
     * Unconditional guard.
     */
    data object Always : ActionGuard
    /**
     * Requires at least [minPercent] battery before executing the node.
     */
    data class BatteryAtLeast(val minPercent: Int) : ActionGuard
    /**
     * Requires the foreground package to be one of [packages].
     */
    data class ForegroundPackageIn(val packages: Set<String>) : ActionGuard
    /**
     * Requires the foreground package to be outside [packages] (sensitive-app shield).
     */
    data class ForegroundPackageNotIn(val packages: Set<String>) : ActionGuard
    /**
     * Inclusive hour window; supports overnight ranges such as 22 → 6.
     */
    data class TimeWindow(val startHour: Int, val endHour: Int) : ActionGuard
    /**
     * Requires an unlocked device; blocks automation behind the keyguard.
     */
    data object DeviceUnlocked : ActionGuard
    /**
     * Requires a wired or Bluetooth headset.
     */
    data object HeadsetConnected : ActionGuard
    /**
     * Conjunction of nested guards.
     */
    data class AllOf(val guards: List<ActionGuard>) : ActionGuard
}

/**
 * Strongly typed automation primitives executed through Android 14/15 compliant platform adapters.
 */
sealed interface ActionCommand {
    /**
     * Performs a GLOBAL_ACTION_* system command through the AccessibilityService.
     */
    data class GlobalSystemAction(val action: SystemAccessibilityAction) : ActionCommand

    /**
     * Dispatches synthetic pointer strokes via `dispatchGesture`, guarded against recursion.
     */
    data class SynthesizeGesture(
        val strokes: List<SyntheticStrokeSpec>,
    ) : ActionCommand

    /**
     * Launches another app through its launcher intent (scoped visibility).
     */
    data class LaunchApplication(
        val packageName: String,
        val clearTop: Boolean = false,
    ) : ActionCommand

    /**
     * Resolves a validated https/custom-scheme URI, optionally pinned to [targetPackage].
     */
    data class OpenDeepLink(
        val uri: String,
        val targetPackage: String? = null,
    ) : ActionCommand

    /**
     * Dispatches a media transport key event through `AudioManager`.
     */
    data class MediaTransport(val control: MediaControlType) : ActionCommand

    /**
     * Toggles a hardware feature (torch, auto-rotate) through its system service.
     */
    data class ToggleHardware(val toggle: HardwareToggleType) : ActionCommand

    /**
     * Acts on a single semantic node; refused on Android 14+ sensitive surfaces.
     */
    data class SemanticNodeAction(
        val viewIdResourceName: String,
        val interaction: NodeInteractionType,
    ) : ActionCommand

    /**
     * Emits a curated haptic preset scaled by the current power budget.
     */
    data class EmitHaptic(val preset: HapticPreset) : ActionCommand

    /**
     * Pauses the graph for [durationMillis] via the injected `Sleeper` (never a raw sleep).
     */
    data class WaitDelay(val durationMillis: Long) : ActionCommand
}

/**
 * Maps 1:1 to non-deprecated `AccessibilityService.GLOBAL_ACTION_*` constants (API 29–35).
 */
enum class SystemAccessibilityAction {
    BACK,
    HOME,
    RECENTS,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    POWER_DIALOG,
    TOGGLE_SPLIT_SCREEN,
    LOCK_SCREEN,
    TAKE_SCREENSHOT,
    KEYCODE_HEADSETHOOK,
    ACCESSIBILITY_ALL_APPS,
    DISMISS_NOTIFICATION_SHADE,
}

/**
 * Media transport keys supported by the Smart-Action engine.
 */
enum class MediaControlType {
    PLAY_PAUSE,
    NEXT_TRACK,
    PREVIOUS_TRACK,
    VOLUME_UP,
    VOLUME_DOWN,
    VOLUME_MUTE,
}

/**
 * Hardware toggles supported on Android 14/15.
 */
enum class HardwareToggleType {
    TORCH_FLASHLIGHT,
    AUTO_ROTATE_DISPLAY,
    ONE_HANDED_OVERLAY,
}

/**
 * Semantic node interactions available to `SemanticNodeAction`.
 */
enum class NodeInteractionType {
    CLICK,
    LONG_CLICK,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    FOCUS,
}

/**
 * Parametric Bezier/polyline stroke compiled to `GestureDescription.StrokeDescription`.
 */
data class SyntheticStrokeSpec(
    val waypoints: List<GesturePoint>,
    val startTimeMillis: Long = 0L,
    val durationMillis: Long = 180L,
    val willContinue: Boolean = false,
)

/**
 * Runtime device snapshot used for guard evaluation (zero sensitive screen content).
 */
data class ExecutionEnvironment(
    val foregroundPackage: String?,
    val foregroundActivity: String?,
    val appCategory: AppCategory,
    val batteryPercent: Int,
    val hourOfDay: Int,
    val isDeviceUnlocked: Boolean,
    val isHeadsetConnected: Boolean,
    val isPrivacySensitiveWindow: Boolean,
    val grantedPermissions: Set<AppPermission>,
)

/**
 * Required platform permissions modeled explicitly for preflight gating and onboarding UX.
 */
enum class AppPermission {
    ACCESSIBILITY_SERVICE,
    POST_NOTIFICATIONS,
    CAMERA_TORCH_CONTROL,
    WRITE_SYSTEM_SETTINGS,
}

/**
 * Immutable audit record of a single [SmartActionGraph] run.
 */
data class NodeExecutionTrace(
    val nodeId: String,
    val status: NodeExecutionStatus,
    val attempts: Int,
    val durationMicros: Long,
    val detail: String? = null,
)

/**
 * Per-node outcome classification recorded in the execution trace.
 */
enum class NodeExecutionStatus {
    SUCCEEDED,
    SKIPPED_BY_GUARD,
    FAILED_RECOVERED_BY_FALLBACK,
    FAILED_TERMINAL,
    BLOCKED_MISSING_PERMISSION,
    BLOCKED_CIRCUIT_OPEN,
}

/**
 * Complete audit record of one Smart-Action graph run.
 */
data class ExecutionReport(
    val graphId: String,
    val triggerRuleId: String?,
    val overallSuccess: Boolean,
    val traces: List<NodeExecutionTrace>,
    val totalDurationMicros: Long,
    val syntheticGestureToken: Long?,
)
