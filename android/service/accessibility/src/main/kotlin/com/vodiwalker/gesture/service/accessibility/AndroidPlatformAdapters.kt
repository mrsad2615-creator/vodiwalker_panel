package com.vodiwalker.gesture.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.vodiwalker.gesture.core.common.AppResult
import com.vodiwalker.gesture.core.common.GestureError
import com.vodiwalker.gesture.core.common.Sleeper
import com.vodiwalker.gesture.core.model.ActionCommand
import com.vodiwalker.gesture.core.model.CompiledHapticPlan
import com.vodiwalker.gesture.core.model.HapticPreset
import com.vodiwalker.gesture.core.model.HapticPrimitiveType
import com.vodiwalker.gesture.core.model.HardwareToggleType
import com.vodiwalker.gesture.core.model.MediaControlType
import com.vodiwalker.gesture.core.model.NodeInteractionType
import com.vodiwalker.gesture.core.model.PredefinedHapticEffect
import com.vodiwalker.gesture.core.model.SyntheticStrokeSpec
import com.vodiwalker.gesture.core.model.SystemAccessibilityAction
import com.vodiwalker.gesture.core.model.VibratorCapabilities
import com.vodiwalker.gesture.engine.haptics.HapticChoreographer
import com.vodiwalker.gesture.engine.smartaction.PlatformActionExecutor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android 12–15 hardware haptic driver wrapping `VibratorManager` (API 31+) and
 * `VibrationAttributes.USAGE_TOUCH` (API 33+) with zero deprecated vibration APIs.
 */
class AndroidHapticDriver(
    context: Context,
    private val choreographer: HapticChoreographer,
) {
    /**
     * Resolved exclusively through `VibratorManager` (API 31+). The legacy
     * `getSystemService(VIBRATOR_SERVICE)` path is intentionally absent: on API 29–30 the driver
     * reports [VibratorCapabilities.hasVibrator] = false so the choreographer degrades to a no-op
     * plan instead of touching the deprecated service handle.
     */
    private val vibrator: Vibrator? = context
        .getSystemService(VibratorManager::class.java)
        ?.defaultVibrator

    val capabilities: VibratorCapabilities by lazy { probeCapabilities() }

    /**
     * Plays a curated preset, scaled by user intensity and the power budget.
     */
    fun playPreset(
        preset: HapticPreset,
        userIntensity: Float,
        powerBudgetMultiplier: Float,
    ) {
        val plan = choreographer.compilePreset(preset, capabilities, userIntensity, powerBudgetMultiplier)
        dispatchPlan(plan)
    }

    /**
     * Plays a velocity/curvature micro-tick during an active stroke (throttled by the engine).
     */
    fun playContinuousTick(
        velocityPxPerMs: Float,
        turningAngleRad: Float,
        userIntensity: Float,
        powerBudgetMultiplier: Float,
    ) {
        val plan = choreographer.compileContinuousStrokeTick(
            velocityPxPerMs = velocityPxPerMs,
            turningAngleRad = turningAngleRad,
            capabilities = capabilities,
            userIntensity = userIntensity,
            powerBudgetMultiplier = powerBudgetMultiplier,
        )
        dispatchPlan(plan)
    }

    private fun dispatchPlan(plan: CompiledHapticPlan) {
        val hw = vibrator ?: return
        val effect: VibrationEffect = when (plan) {
            CompiledHapticPlan.Suppressed -> return
            is CompiledHapticPlan.RichComposition -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
                val composition = VibrationEffect.startComposition()
                for (step in plan.steps) {
                    composition.addPrimitive(toPlatformPrimitive(step.primitive), step.scale, step.delayMillis)
                }
                composition.compose()
            }
            is CompiledHapticPlan.AmplitudeWaveform -> {
                VibrationEffect.createWaveform(plan.timingsMillis, plan.amplitudes, -1)
            }
            is CompiledHapticPlan.PredefinedFallback -> {
                val id = when (plan.effect) {
                    PredefinedHapticEffect.TICK -> VibrationEffect.EFFECT_TICK
                    PredefinedHapticEffect.CLICK -> VibrationEffect.EFFECT_CLICK
                    PredefinedHapticEffect.HEAVY_CLICK -> VibrationEffect.EFFECT_HEAVY_CLICK
                    PredefinedHapticEffect.DOUBLE_CLICK -> VibrationEffect.EFFECT_DOUBLE_CLICK
                }
                VibrationEffect.createPredefined(id)
            }
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
                hw.vibrate(effect, attrs)
            } else {
                hw.vibrate(effect)
            }
        }
    }

    private fun probeCapabilities(): VibratorCapabilities {
        val hw = vibrator
        if (hw == null || !hw.hasVibrator()) {
            return VibratorCapabilities(
                hasVibrator = false,
                hasAmplitudeControl = false,
                apiLevel = Build.VERSION.SDK_INT,
                supportedPrimitives = emptySet(),
            )
        }
        val supported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticPrimitiveType.entries.filterTo(LinkedHashSet()) { prim ->
                Build.VERSION.SDK_INT >= prim.minApiLevel &&
                    hw.areAllPrimitivesSupported(toPlatformPrimitive(prim))
            }
        } else {
            emptySet()
        }
        val qResonance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            hw.resonantFrequency.takeIf { it.isFinite() && it > 0f } ?: 170f
        } else {
            170f
        }
        return VibratorCapabilities(
            hasVibrator = true,
            hasAmplitudeControl = hw.hasAmplitudeControl(),
            apiLevel = Build.VERSION.SDK_INT,
            supportedPrimitives = supported,
            resonantFrequencyHz = qResonance,
        )
    }

    private fun toPlatformPrimitive(type: HapticPrimitiveType): Int = when (type) {
        HapticPrimitiveType.CLICK -> VibrationEffect.Composition.PRIMITIVE_CLICK
        HapticPrimitiveType.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK
        HapticPrimitiveType.LOW_TICK -> VibrationEffect.Composition.PRIMITIVE_LOW_TICK
        HapticPrimitiveType.THUD -> VibrationEffect.Composition.PRIMITIVE_THUD
        HapticPrimitiveType.SPIN -> VibrationEffect.Composition.PRIMITIVE_SPIN
        HapticPrimitiveType.QUICK_RISE -> VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
        HapticPrimitiveType.SLOW_RISE -> VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
        HapticPrimitiveType.QUICK_FALL -> VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
    }
}

/**
 * Concrete Android 14/15 [PlatformActionExecutor] bridging `:engine:smartaction` commands to
 * `AccessibilityService`, `GestureDescription`, `CameraManager`, and `AudioManager`.
 *
 * Strictly checks `AccessibilityNodeInfo.isAccessibilityDataSensitive()` on Android 14+ (API 34)
 * before invoking any semantic node interaction.
 */
class AndroidPlatformActionExecutor(
    private val service: AccessibilityService,
    private val hapticDriver: AndroidHapticDriver,
    private val currentHapticGainProvider: () -> Pair<Float, Float>,
    private val sleeper: Sleeper = Sleeper.BLOCKING,
) : PlatformActionExecutor {

    private val torchEnabled = AtomicBoolean(false)

    override fun execute(command: ActionCommand, timeoutMillis: Long): AppResult<Unit, GestureError> =
        when (command) {
            is ActionCommand.GlobalSystemAction -> executeGlobalAction(command.action)
            is ActionCommand.SynthesizeGesture -> dispatchSyntheticGesture(command.strokes, timeoutMillis)
            is ActionCommand.LaunchApplication -> launchPackage(command.packageName, command.clearTop)
            is ActionCommand.OpenDeepLink -> openDeepLink(command.uri, command.targetPackage)
            is ActionCommand.MediaTransport -> dispatchMediaControl(command.control)
            is ActionCommand.ToggleHardware -> toggleHardware(command.toggle)
            is ActionCommand.SemanticNodeAction -> performSemanticNodeAction(
                viewId = command.viewIdResourceName,
                interaction = command.interaction,
            )
            is ActionCommand.EmitHaptic -> {
                val (userIntensity, powerMult) = currentHapticGainProvider()
                hapticDriver.playPreset(command.preset, userIntensity, powerMult)
                AppResult.Ok(Unit)
            }
            is ActionCommand.WaitDelay -> {
                // Pause on the calling background thread through the injected [Sleeper]; the
                // AccessibilityService host invokes `executeGraph` from Dispatchers.Default, so no
                // main-thread/ANR surface is created. Interruption is reported, never swallowed.
                val bounded = command.durationMillis.coerceIn(0L, timeoutMillis.coerceAtMost(3_000L))
                if (sleeper.sleep(bounded)) {
                    AppResult.Ok(Unit)
                } else {
                    AppResult.Err(
                        GestureError.PlatformFailure(
                            code = "INTERRUPTED",
                            message = "WaitDelay interrupted after ${bounded}ms",
                        ),
                    )
                }
            }
        }

    private fun executeGlobalAction(action: SystemAccessibilityAction): AppResult<Unit, GestureError> {
        val actionConst = when (action) {
            SystemAccessibilityAction.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            SystemAccessibilityAction.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            SystemAccessibilityAction.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            SystemAccessibilityAction.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            SystemAccessibilityAction.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            SystemAccessibilityAction.POWER_DIALOG -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
            SystemAccessibilityAction.TOGGLE_SPLIT_SCREEN -> AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
            SystemAccessibilityAction.LOCK_SCREEN -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            SystemAccessibilityAction.TAKE_SCREENSHOT -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
            SystemAccessibilityAction.KEYCODE_HEADSETHOOK -> AccessibilityService.GLOBAL_ACTION_KEYCODE_HEADSETHOOK
            SystemAccessibilityAction.ACCESSIBILITY_ALL_APPS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS
                } else {
                    AccessibilityService.GLOBAL_ACTION_HOME
                }
            SystemAccessibilityAction.DISMISS_NOTIFICATION_SHADE ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE
                } else {
                    AccessibilityService.GLOBAL_ACTION_BACK
                }
        }
        return if (service.performGlobalAction(actionConst)) {
            AppResult.Ok(Unit)
        } else {
            AppResult.Err(GestureError.PlatformFailure("GLOBAL_ACTION_REJECTED", action.name))
        }
    }

    private fun dispatchSyntheticGesture(
        strokes: List<SyntheticStrokeSpec>,
        timeoutMillis: Long,
    ): AppResult<Unit, GestureError> {
        if (strokes.isEmpty() || strokes.size > GestureDescription.getMaxStrokeCount()) {
            return AppResult.Err(GestureError.InvalidInput("Invalid synthetic stroke count: ${strokes.size}"))
        }
        val maxDuration = GestureDescription.getMaxGestureDuration()
        val builder = GestureDescription.Builder()

        for (spec in strokes) {
            if (spec.waypoints.isEmpty()) continue
            val path = Path().apply {
                moveTo(spec.waypoints.first().x, spec.waypoints.first().y)
                for (i in 1 until spec.waypoints.size) {
                    lineTo(spec.waypoints[i].x, spec.waypoints[i].y)
                }
            }
            val start = spec.startTimeMillis.coerceIn(0L, maxDuration - 1L)
            val duration = spec.durationMillis.coerceIn(16L, (maxDuration - start).coerceAtLeast(16L))
            builder.addStroke(
                GestureDescription.StrokeDescription(path, start, duration, spec.willContinue),
            )
        }

        val latch = CountDownLatch(1)
        val completed = AtomicBoolean(false)
        val accepted = service.dispatchGesture(
            builder.build(),
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    completed.set(true)
                    latch.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    completed.set(false)
                    latch.countDown()
                }
            },
            null,
        )
        if (!accepted) {
            return AppResult.Err(GestureError.PlatformFailure("GESTURE_DISPATCH_REJECTED", "System busy"))
        }
        val finishedInTime = latch.await(timeoutMillis.coerceIn(100L, 5_000L), TimeUnit.MILLISECONDS)
        return when {
            !finishedInTime -> AppResult.Err(GestureError.TimeoutExceeded("dispatchGesture", timeoutMillis))
            completed.get() -> AppResult.Ok(Unit)
            else -> AppResult.Err(GestureError.PlatformFailure("GESTURE_CANCELLED", "Cancelled by user/system"))
        }
    }

    private fun launchPackage(packageName: String, clearTop: Boolean): AppResult<Unit, GestureError> {
        val intent = service.packageManager.getLaunchIntentForPackage(packageName)
            ?: return AppResult.Err(GestureError.PlatformFailure("PACKAGE_NOT_FOUND", packageName))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (clearTop) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return runCatching { service.startActivity(intent) }
            .fold(
                onSuccess = { AppResult.Ok(Unit) },
                onFailure = { AppResult.Err(GestureError.PlatformFailure("LAUNCH_FAILED", it.message.orEmpty())) },
            )
    }

    private fun openDeepLink(rawUri: String, targetPackage: String?): AppResult<Unit, GestureError> {
        val parsed = Uri.parse(rawUri)
        val scheme = parsed.scheme?.lowercase()
        if (scheme == null || scheme in setOf("file", "javascript", "content")) {
            return AppResult.Err(GestureError.InvalidInput("Blocked unsafe URI scheme: $scheme"))
        }
        val intent = Intent(Intent.ACTION_VIEW, parsed).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!targetPackage.isNullOrBlank()) setPackage(targetPackage)
        }
        return runCatching { service.startActivity(intent) }
            .fold(
                onSuccess = { AppResult.Ok(Unit) },
                onFailure = { AppResult.Err(GestureError.PlatformFailure("DEEPLINK_FAILED", it.message.orEmpty())) },
            )
    }

    private fun dispatchMediaControl(control: MediaControlType): AppResult<Unit, GestureError> {
        val audio = service.getSystemService(AudioManager::class.java)
            ?: return AppResult.Err(GestureError.PlatformFailure("AUDIO_UNAVAILABLE", "No AudioManager"))
        when (control) {
            MediaControlType.VOLUME_UP ->
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            MediaControlType.VOLUME_DOWN ->
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            MediaControlType.VOLUME_MUTE ->
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_TOGGLE_MUTE,
                    AudioManager.FLAG_SHOW_UI,
                )
            MediaControlType.PLAY_PAUSE -> sendMediaKey(audio, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            MediaControlType.NEXT_TRACK -> sendMediaKey(audio, KeyEvent.KEYCODE_MEDIA_NEXT)
            MediaControlType.PREVIOUS_TRACK -> sendMediaKey(audio, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        return AppResult.Ok(Unit)
    }

    private fun sendMediaKey(audio: AudioManager, keyCode: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun toggleHardware(toggle: HardwareToggleType): AppResult<Unit, GestureError> = when (toggle) {
        HardwareToggleType.TORCH_FLASHLIGHT -> {
            val cm = service.getSystemService(CameraManager::class.java)
                ?: return AppResult.Err(GestureError.PlatformFailure("CAMERA_UNAVAILABLE", "No CameraManager"))
            val flashId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return AppResult.Err(GestureError.PlatformFailure("NO_FLASH_UNIT", "Device lacks torch"))
            val next = !torchEnabled.get()
            runCatching {
                cm.setTorchMode(flashId, next)
                torchEnabled.set(next)
            }.fold(
                onSuccess = { AppResult.Ok(Unit) },
                onFailure = { AppResult.Err(GestureError.PlatformFailure("TORCH_ERROR", it.message.orEmpty())) },
            )
        }
        HardwareToggleType.AUTO_ROTATE_DISPLAY -> {
            if (!Settings.System.canWrite(service)) {
                return AppResult.Err(GestureError.PermissionDenied("WRITE_SETTINGS"))
            }
            val current = Settings.System.getInt(
                service.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                0,
            )
            Settings.System.putInt(
                service.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (current == 1) 0 else 1,
            )
            AppResult.Ok(Unit)
        }
        HardwareToggleType.ONE_HANDED_OVERLAY -> executeGlobalAction(SystemAccessibilityAction.NOTIFICATIONS)
    }

    private fun performSemanticNodeAction(
        viewId: String,
        interaction: NodeInteractionType,
    ): AppResult<Unit, GestureError> {
        val root = service.rootInActiveWindow
            ?: return AppResult.Err(GestureError.PlatformFailure("NO_ACTIVE_WINDOW", "Root window unavailable"))
        // Enforce Android 14+ (API 34) accessibilityDataSensitive contract
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && root.isAccessibilityDataSensitive) {
            return AppResult.Err(GestureError.PermissionDenied("ACCESSIBILITY_DATA_SENSITIVE"))
        }
        val target = root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull { node ->
            !node.isPassword && (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                    !node.isAccessibilityDataSensitive
                )
        } ?: return AppResult.Err(GestureError.PlatformFailure("NODE_NOT_FOUND", viewId))

        val actionId = when (interaction) {
            NodeInteractionType.CLICK -> AccessibilityNodeInfo.ACTION_CLICK
            NodeInteractionType.LONG_CLICK -> AccessibilityNodeInfo.ACTION_LONG_CLICK
            NodeInteractionType.SCROLL_FORWARD -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            NodeInteractionType.SCROLL_BACKWARD -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            NodeInteractionType.FOCUS -> AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
        }
        return if (target.performAction(actionId)) {
            AppResult.Ok(Unit)
        } else {
            AppResult.Err(GestureError.PlatformFailure("NODE_ACTION_FAILED", "$viewId:$interaction"))
        }
    }
}
