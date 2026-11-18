package com.vodiwalker.gesture.service.accessibility

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.vodiwalker.gesture.core.common.SyntheticGestureGuard
import com.vodiwalker.gesture.core.datastore.UserSettings
import com.vodiwalker.gesture.core.datastore.UserSettingsDataSource
import com.vodiwalker.gesture.core.model.ActionGraphRepository
import com.vodiwalker.gesture.core.model.AppCategory
import com.vodiwalker.gesture.core.model.AppPermission
import com.vodiwalker.gesture.core.model.AppTriggerRule
import com.vodiwalker.gesture.core.model.DevicePowerTelemetry
import com.vodiwalker.gesture.core.model.ExecutionEnvironment
import com.vodiwalker.gesture.core.model.ExecutionTelemetryRepository
import com.vodiwalker.gesture.core.model.GestureCapture
import com.vodiwalker.gesture.core.model.GesturePoint
import com.vodiwalker.gesture.core.model.GestureStroke
import com.vodiwalker.gesture.core.model.GestureTemplate
import com.vodiwalker.gesture.core.model.GestureTemplateRepository
import com.vodiwalker.gesture.core.model.HapticPreset
import com.vodiwalker.gesture.core.model.PowerExecutionBudget
import com.vodiwalker.gesture.core.model.PowerMode
import com.vodiwalker.gesture.core.model.PredictionContext
import com.vodiwalker.gesture.core.model.RecognitionOutcome
import com.vodiwalker.gesture.core.model.ThermalSeverity
import com.vodiwalker.gesture.core.model.TriggerRuleRepository
import com.vodiwalker.gesture.engine.gesture.HybridGestureEngine
import com.vodiwalker.gesture.engine.haptics.HapticChoreographer
import com.vodiwalker.gesture.engine.power.AdaptivePowerGovernor
import com.vodiwalker.gesture.engine.prediction.AiGesturePredictionEngine
import com.vodiwalker.gesture.engine.smartaction.AppTriggerResolver
import com.vodiwalker.gesture.engine.smartaction.SmartActionEngine
import com.vodiwalker.gesture.service.overlay.GlobalOverlayController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Android 14/15 Hardened Accessibility & Multi-Engine Gesture Automation Service.
 *
 * Security & Privacy Guarantees:
 * - Declared `android:isAccessibilityTool="true"` with Google Play Prominent Disclosure gating.
 * - Inspects `AccessibilityNodeInfo.isAccessibilityDataSensitive()` (API 34+), password fields,
 *   Keyguard lock state, and user-configured protected packages; automatically retracts overlay
 *   and drops recognition whenever a sensitive surface appears.
 * - Subscribes exclusively to `TYPE_WINDOW_STATE_CHANGED` (and drops to `0` during screen-off /
 *   Doze); never captures keystrokes, notifications text, or clipboard contents.
 * - Registers all dynamic broadcast receivers with `ContextCompat.RECEIVER_NOT_EXPORTED`.
 * - Acquires zero wake locks and zero network sockets.
 */
@AndroidEntryPoint
class VodiGestureAccessibilityService : AccessibilityService() {

    @Inject lateinit var templateRepository: GestureTemplateRepository
    @Inject lateinit var triggerRuleRepository: TriggerRuleRepository
    @Inject lateinit var actionGraphRepository: ActionGraphRepository
    @Inject lateinit var telemetryRepository: ExecutionTelemetryRepository
    @Inject lateinit var settingsDataSource: UserSettingsDataSource

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val syntheticGuard = SyntheticGestureGuard()
    private val gestureEngine = HybridGestureEngine(syntheticGuard = syntheticGuard)
    private val predictionEngine = AiGesturePredictionEngine()
    private val triggerResolver = AppTriggerResolver()
    private val hapticChoreographer = HapticChoreographer()
    private val powerGovernor = AdaptivePowerGovernor()

    private lateinit var hapticDriver: AndroidHapticDriver
    private lateinit var smartActionEngine: SmartActionEngine
    private lateinit var overlayController: GlobalOverlayController

    @Volatile private var cachedTemplates: List<GestureTemplate> = emptyList()
    @Volatile private var cachedRules: List<AppTriggerRule> = emptyList()
    @Volatile private var latestSettings: UserSettings? = null
    @Volatile private var currentBudget: PowerExecutionBudget = powerGovernor.budgetFor(PowerMode.BALANCED)
    @Volatile private var foregroundPackage: String? = null
    @Volatile private var foregroundActivity: String? = null
    @Volatile private var isSensitiveWindow: Boolean = false
    @Volatile private var currentThermal: ThermalSeverity = ThermalSeverity.NONE
    @Volatile private var lastGestureId: String? = null
    @Volatile private var lastGestureAtMillis: Long = 0L

    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var receiverRegistered = false

    private val powerStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            recomputePowerAndOverlayState()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isServiceConnected.value = true

        hapticDriver = AndroidHapticDriver(this, hapticChoreographer)
        val executor = AndroidPlatformActionExecutor(
            service = this,
            hapticDriver = hapticDriver,
            currentHapticGainProvider = {
                (latestSettings?.hapticIntensity ?: 0.85f) to currentBudget.hapticScaleMultiplier
            },
        )
        smartActionEngine = SmartActionEngine(executor = executor, syntheticGuard = syntheticGuard)
        overlayController = GlobalOverlayController(
            serviceContext = this,
            onEdgeActivated = {
                hapticDriver.playPreset(
                    preset = HapticPreset.EDGE_DETENT,
                    userIntensity = latestSettings?.hapticIntensity ?: 0.85f,
                    powerBudgetMultiplier = currentBudget.hapticScaleMultiplier,
                )
                publishMidStrokePrediction(emptyList())
            },
            onPointSampled = { pt -> handleStreamingPoint(pt) },
            onGestureCompleted = { points -> handleCompletedStroke(points) },
            onQuickPredictionSelected = { templateId -> executeMatchedGesture(templateId) },
        )

        registerPowerAndThermalObservers()
        hydrateCachesAndObserveSettings()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Ignore our own overlay window state transitions
        if (pkg == packageName) return

        foregroundPackage = pkg
        foregroundActivity = event.className?.toString()
        isSensitiveWindow = detectPrivacySensitiveSurface(pkg, event.isPassword)
        recomputePowerAndOverlayState()
    }

    override fun onGesture(gestureEvent: AccessibilityGestureEvent): Boolean {
        if (isSensitiveWindow || currentBudget.mode == PowerMode.DOZE_SUSPENDED) {
            return super.onGesture(gestureEvent)
        }
        return super.onGesture(gestureEvent)
    }

    override fun onInterrupt() {
        if (::overlayController.isInitialized) {
            overlayController.collapseToHandle()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        tearDownServiceResources()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        tearDownServiceResources()
        super.onDestroy()
    }

    private fun handleStreamingPoint(point: GesturePoint) {
        val active = overlayController.uiState.value.activePoints
        if (currentBudget.enablePrefixAiPrediction && active.size % 6 == 0) {
            publishMidStrokePrediction(active + point)
        }
    }

    private fun publishMidStrokePrediction(points: List<GesturePoint>) {
        if (!currentBudget.enablePrefixAiPrediction && points.isNotEmpty()) return
        val context = snapshotPredictionContext()
        val templates = cachedTemplates
        serviceScope.launch(Dispatchers.Default) {
            val ranked = predictionEngine.predict(points, templates, context, topK = 2)
            withContext(Dispatchers.Main.immediate) {
                overlayController.publishPredictions(ranked)
            }
        }
    }

    private fun handleCompletedStroke(points: List<GesturePoint>) {
        val (vw, vh) = overlayController.currentViewportSizePx()
        val capture = GestureCapture(
            strokes = listOf(GestureStroke(strokeId = 0, points = points)),
            viewportWidthPx = vw,
            viewportHeightPx = vh,
            foregroundPackage = foregroundPackage,
            foregroundActivity = foregroundActivity,
        )
        val templates = cachedTemplates
        val fullEnsemble = currentBudget.enableFullEnsembleRecognition
        val sensitive = isSensitiveWindow

        serviceScope.launch(Dispatchers.Default) {
            val outcome = gestureEngine.recognize(
                capture = capture,
                templates = templates,
                fullEnsemble = fullEnsemble,
                isPrivacySensitiveWindow = sensitive,
            )
            when (outcome) {
                is RecognitionOutcome.Matched -> {
                    withContext(Dispatchers.Main.immediate) {
                        overlayController.collapseToHandle(outcome.winner.templateName)
                    }
                    executeMatchedGesture(outcome.winner.templateId)
                }
                is RecognitionOutcome.Rejected -> {
                    hapticDriver.playPreset(
                        preset = HapticPreset.REJECT_DOUBLE_THUD,
                        userIntensity = latestSettings?.hapticIntensity ?: 0.85f,
                        powerBudgetMultiplier = currentBudget.hapticScaleMultiplier,
                    )
                    withContext(Dispatchers.Main.immediate) {
                        overlayController.collapseToHandle()
                    }
                }
            }
        }
    }

    private fun executeMatchedGesture(templateId: String) {
        serviceScope.launch(Dispatchers.IO) {
            val env = snapshotExecutionEnvironment()
            val rule = triggerResolver.resolve(templateId, cachedRules, env) ?: return@launch
            val graph = actionGraphRepository.getGraphById(rule.actionGraphId) ?: return@launch
            val template = cachedTemplates.firstOrNull { it.id == templateId }

            val hapticId = rule.hapticOverrideId ?: template?.hapticPatternId
            hapticDriver.playPreset(
                preset = hapticChoreographer.resolvePresetById(hapticId),
                userIntensity = latestSettings?.hapticIntensity ?: 0.85f,
                powerBudgetMultiplier = currentBudget.hapticScaleMultiplier,
            )

            val report = smartActionEngine.executeGraph(graph, env, triggerRuleId = rule.id)
            val now = System.currentTimeMillis()
            telemetryRepository.recordExecution(report, now)
            if (report.overallSuccess) {
                templateRepository.incrementUsageCount(templateId, now)
                predictionEngine.learnFromExecution(snapshotPredictionContext(), templateId, cachedTemplates)
                lastGestureId = templateId
                lastGestureAtMillis = now
            }
        }
    }

    private fun detectPrivacySensitiveSurface(pkg: String, isPasswordEvent: Boolean): Boolean {
        if (isPasswordEvent) return true
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) return true
        val excluded = latestSettings?.privacyExcludedPackages ?: UserSettingsDataSource.DEFAULT_PROTECTED_PACKAGES
        if (pkg in excluded) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val root = rootInActiveWindow
            if (root?.isAccessibilityDataSensitive == true) return true
        }
        return false
    }

    private fun recomputePowerAndOverlayState() {
        val pm = getSystemService(PowerManager::class.java)
        val bm = getSystemService(BatteryManager::class.java)
        val battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 80
        val charging = bm?.isCharging ?: false

        val telemetry = DevicePowerTelemetry(
            batteryPercent = battery,
            isCharging = charging,
            isSystemPowerSaveMode = pm?.isPowerSaveMode ?: false,
            isDeviceIdleDoze = pm?.isDeviceIdleMode ?: false,
            isScreenInteractive = pm?.isInteractive ?: true,
            thermalSeverity = currentThermal,
            isPrivacySensitiveWindow = isSensitiveWindow,
            recentGesturesPerMinute = 1.0f,
            userPreferredMode = latestSettings?.preferredPowerMode,
        )
        val nextBudget = powerGovernor.evaluate(telemetry)
        currentBudget = nextBudget
        _activePowerBudget.value = nextBudget

        // Dynamically throttle AccessibilityServiceInfo to eliminate idle binder wakeups
        serviceInfo?.let { info ->
            val desiredEvents = if (nextBudget.listenWindowStateChanges) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            } else {
                0
            }
            if (info.eventTypes != desiredEvents ||
                info.notificationTimeout != nextBudget.accessibilityNotificationTimeoutMillis
            ) {
                info.eventTypes = desiredEvents
                info.notificationTimeout = nextBudget.accessibilityNotificationTimeoutMillis
                serviceInfo = info
            }
        }

        val settings = latestSettings
        if (::overlayController.isInitialized) {
            if (nextBudget.enableOverlayRendering && settings?.prominentDisclosureAccepted == true) {
                if (!overlayController.uiState.value.isCanvasExpanded) {
                    overlayController.showDockedHandle(settings.overlayConfig)
                }
            } else {
                overlayController.detachOverlay()
            }
        }
    }

    private fun hydrateCachesAndObserveSettings() {
        serviceScope.launch(Dispatchers.IO) {
            cachedTemplates = templateRepository.getEnabledTemplates()
            cachedRules = triggerRuleRepository.getEnabledRules()
        }
        serviceScope.launch {
            settingsDataSource.settingsFlow.collectLatest { settings ->
                latestSettings = settings
                cachedTemplates = withContext(Dispatchers.IO) { templateRepository.getEnabledTemplates() }
                cachedRules = withContext(Dispatchers.IO) { triggerRuleRepository.getEnabledRules() }
                recomputePowerAndOverlayState()
            }
        }
    }

    private fun registerPowerAndThermalObservers() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            powerStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true

        val pm = getSystemService(PowerManager::class.java)
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            currentThermal = when (status) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalSeverity.NONE
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalSeverity.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalSeverity.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalSeverity.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalSeverity.CRITICAL
                else -> ThermalSeverity.EMERGENCY
            }
            recomputePowerAndOverlayState()
        }
        pm?.addThermalStatusListener(mainExecutor, listener)
        thermalListener = listener
    }

    private fun snapshotExecutionEnvironment(): ExecutionEnvironment {
        val bm = getSystemService(BatteryManager::class.java)
        val km = getSystemService(KeyguardManager::class.java)
        val now = LocalDateTime.now()
        val perms = buildSet {
            add(AppPermission.ACCESSIBILITY_SERVICE)
            add(AppPermission.CAMERA_TORCH_CONTROL)
            if (android.provider.Settings.System.canWrite(this@VodiGestureAccessibilityService)) {
                add(AppPermission.WRITE_SYSTEM_SETTINGS)
            }
        }
        return ExecutionEnvironment(
            foregroundPackage = foregroundPackage,
            foregroundActivity = foregroundActivity,
            appCategory = classifyAppCategory(foregroundPackage),
            batteryPercent = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 80,
            hourOfDay = now.hour,
            isDeviceUnlocked = km?.isDeviceLocked == false,
            isHeadsetConnected = isAudioHeadsetAttached(),
            isPrivacySensitiveWindow = isSensitiveWindow,
            grantedPermissions = perms,
        )
    }

    private fun snapshotPredictionContext(): PredictionContext {
        val now = LocalDateTime.now()
        val nowMs = System.currentTimeMillis()
        val bm = getSystemService(BatteryManager::class.java)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return PredictionContext(
            foregroundPackage = foregroundPackage,
            appCategory = if (isSensitiveWindow) {
                AppCategory.SENSITIVE_PROTECTED
            } else {
                classifyAppCategory(foregroundPackage)
            },
            hourOfDay = now.hour,
            dayOfWeek = now.dayOfWeek.value,
            isLandscape = isLandscape,
            isHeadsetConnected = isAudioHeadsetAttached(),
            batteryPercent = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 80,
            previousGestureId = lastGestureId,
            millisSincePreviousGesture = if (lastGestureAtMillis > 0L) nowMs - lastGestureAtMillis else 0L,
        )
    }

    private fun isAudioHeadsetAttached(): Boolean {
        val audio = getSystemService(AudioManager::class.java) ?: return false
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { dev ->
            dev.type in setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
            )
        }
    }

    private fun classifyAppCategory(pkg: String?): AppCategory {
        if (pkg == null) return AppCategory.OTHER
        val appInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(pkg, android.content.pm.PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                packageManager.getApplicationInfo(pkg, 0)
            }
        }.getOrNull() ?: return AppCategory.OTHER

        return when (appInfo.category) {
            ApplicationInfo.CATEGORY_AUDIO -> AppCategory.MUSIC_AUDIO
            ApplicationInfo.CATEGORY_VIDEO -> AppCategory.MEDIA_VIDEO
            ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMING
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL_COMMUNICATION
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
            ApplicationInfo.CATEGORY_IMAGE -> AppCategory.CAMERA_GALLERY
            ApplicationInfo.CATEGORY_NEWS -> AppCategory.READING
            else -> AppCategory.OTHER
        }
    }

    private fun tearDownServiceResources() {
        _isServiceConnected.value = false
        if (::overlayController.isInitialized) {
            overlayController.detachOverlay()
        }
        if (receiverRegistered) {
            runCatching { unregisterReceiver(powerStateReceiver) }
            receiverRegistered = false
        }
        thermalListener?.let { listener ->
            runCatching { getSystemService(PowerManager::class.java)?.removeThermalStatusListener(listener) }
            thermalListener = null
        }
        serviceScope.cancel()
    }

    companion object {
        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _activePowerBudget = MutableStateFlow<PowerExecutionBudget?>(null)
        val activePowerBudget: StateFlow<PowerExecutionBudget?> = _activePowerBudget.asStateFlow()
    }
}
