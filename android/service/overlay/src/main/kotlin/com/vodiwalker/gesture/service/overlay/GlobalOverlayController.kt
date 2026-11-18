package com.vodiwalker.gesture.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.vodiwalker.gesture.core.designsystem.component.CinematicGestureCanvas
import com.vodiwalker.gesture.core.designsystem.component.ConfidenceArcGauge
import com.vodiwalker.gesture.core.designsystem.component.GlassmorphicCard
import com.vodiwalker.gesture.core.designsystem.theme.LocalExtendedColors
import com.vodiwalker.gesture.core.designsystem.theme.VodiGestureTheme
import com.vodiwalker.gesture.core.designsystem.theme.VodiMotion
import com.vodiwalker.gesture.core.model.DockEdge
import com.vodiwalker.gesture.core.model.GesturePoint
import com.vodiwalker.gesture.core.model.OverlayUiConfig
import com.vodiwalker.gesture.core.model.PredictionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

/**
 * Immutable UI state rendered by the Global Glassmorphic Accessibility Overlay.
 */
data class GlobalOverlayUiState(
    val isVisible: Boolean = false,
    val isCanvasExpanded: Boolean = false,
    val config: OverlayUiConfig = OverlayUiConfig(),
    val activePoints: List<GesturePoint> = emptyList(),
    val currentStrokeId: Int = 0,
    val topPredictions: List<PredictionResult> = emptyList(),
    val statusBanner: String? = null,
)

/**
 * Self-contained [LifecycleOwner] + [SavedStateRegistryOwner] + [ViewModelStoreOwner] allowing
 * Jetpack Compose to run cleanly inside an AccessibilityService `TYPE_ACCESSIBILITY_OVERLAY`
 * window without any memory leaks or orphaned recomposers.
 */
internal class OverlayComposeLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    /**
     * Registers lifecycle observers and attaches the overlay window.
     */
    fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    /**
     * Marks the overlay composition active so it renders on resume.
     */
    fun onResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /**
     * Pauses rendering without removing the window (cheap re-entry).
     */
    fun onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    /**
     * Releases the composition and removes the overlay window from WindowManager.
     */
    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

/**
 * Global Overlay Controller hosted exclusively via `TYPE_ACCESSIBILITY_OVERLAY` (requires zero
 * `SYSTEM_ALERT_WINDOW` permission and is automatically hidden by Android 12+ over activities
 * declaring `Window.setHideOverlayWindows(true)`).
 *
 * Transitions seamlessly between a minimal non-blocking magnetic edge pill (`FLAG_NOT_FOCUSABLE`)
 * and a hardware-blurred (`FLAG_BLUR_BEHIND`, API 31+) full-screen gesture capture theater.
 */
class GlobalOverlayController(
    private val serviceContext: Context,
    private val onEdgeActivated: () -> Unit,
    private val onPointSampled: (GesturePoint) -> Unit,
    private val onGestureCompleted: (List<GesturePoint>) -> Unit,
    private val onQuickPredictionSelected: (String) -> Unit,
) {
    private val windowManager = serviceContext.getSystemService(WindowManager::class.java)
    private var lifecycleOwner: OverlayComposeLifecycleOwner? = null
    private var composeView: ComposeView? = null
    private var isAttached = false

    private val _uiState = MutableStateFlow(GlobalOverlayUiState())
    val uiState: StateFlow<GlobalOverlayUiState> = _uiState.asStateFlow()

    /** Attaches or updates the docked edge pill when power & privacy governors permit rendering. */
    fun showDockedHandle(config: OverlayUiConfig) {
        _uiState.update {
            it.copy(
                isVisible = true,
                isCanvasExpanded = false,
                config = config,
                activePoints = emptyList(),
                topPredictions = emptyList(),
            )
        }
        val params = buildHandleLayoutParams(config)
        attachOrUpdateWindow(params)
    }

    /** Morphs the docked edge pill into the full-screen glassmorphic gesture studio overlay. */
    fun expandCaptureCanvas() {
        val current = _uiState.value
        if (!current.isVisible) return
        _uiState.update {
            it.copy(
                isCanvasExpanded = true,
                activePoints = emptyList(),
                currentStrokeId = 0,
                statusBanner = null,
            )
        }
        onEdgeActivated()
        attachOrUpdateWindow(buildExpandedCanvasLayoutParams(current.config))
    }

    /** Updates mid-stroke AI predictions and ghost completion coordinates in real time. */
    fun publishPredictions(predictions: List<PredictionResult>) {
        _uiState.update { it.copy(topPredictions = predictions) }
    }

    /** Collapses back to the minimal edge handle without destroying the Compose hierarchy. */
    fun collapseToHandle(feedbackMessage: String? = null) {
        val cfg = _uiState.value.config
        _uiState.update {
            it.copy(
                isCanvasExpanded = false,
                activePoints = emptyList(),
                topPredictions = emptyList(),
                statusBanner = feedbackMessage,
            )
        }
        if (isAttached) {
            attachOrUpdateWindow(buildHandleLayoutParams(cfg))
        }
    }

    /** Immediately removes overlay during Doze, screen-off, or Android 14+ privacy-shield windows. */
    fun detachOverlay() {
        _uiState.update { it.copy(isVisible = false, isCanvasExpanded = false, activePoints = emptyList()) }
        val view = composeView
        if (isAttached && view != null) {
            runCatching { windowManager?.removeViewImmediate(view) }
        }
        isAttached = false
        composeView = null
        lifecycleOwner?.onPause()
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }

    /** Returns active viewport dimensions using non-deprecated API 30+ `currentWindowMetrics`. */
    fun currentViewportSizePx(): Pair<Int, Int> {
        val wm = windowManager ?: return 1080 to 2400
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            val w = (metrics.bounds.width() - insets.left - insets.right).coerceAtLeast(320)
            val h = (metrics.bounds.height() - insets.top - insets.bottom).coerceAtLeast(480)
            w to h
        } else {
            val dm = serviceContext.resources.displayMetrics
            dm.widthPixels.coerceAtLeast(320) to dm.heightPixels.coerceAtLeast(480)
        }
    }

    private fun attachOrUpdateWindow(params: WindowManager.LayoutParams) {
        val wm = windowManager ?: return
        val existing = composeView
        if (isAttached && existing != null) {
            runCatching { wm.updateViewLayout(existing, params) }
            return
        }

        val owner = OverlayComposeLifecycleOwner().also {
            it.onCreate()
            it.onResume()
        }
        val view = ComposeView(serviceContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                VodiGestureTheme(darkTheme = true) {
                    val state by _uiState.collectAsState()
                    GlobalOverlayRoot(
                        state = state,
                        onExpand = ::expandCaptureCanvas,
                        onPoint = { pt ->
                            _uiState.update { s -> s.copy(activePoints = s.activePoints + pt) }
                            onPointSampled(pt)
                        },
                        onStrokeFinished = {
                            val captured = _uiState.value.activePoints
                            onGestureCompleted(captured)
                        },
                        onPickPrediction = { templateId ->
                            onQuickPredictionSelected(templateId)
                            collapseToHandle()
                        },
                        onDismiss = { collapseToHandle() },
                    )
                }
            }
        }

        runCatching {
            wm.addView(view, params)
            lifecycleOwner = owner
            composeView = view
            isAttached = true
        }.onFailure {
            owner.onDestroy()
        }
    }

    private fun buildHandleLayoutParams(config: OverlayUiConfig): WindowManager.LayoutParams {
        val density = serviceContext.resources.displayMetrics.density
        val (_, viewportH) = currentViewportSizePx()
        // Touch target width >= 24dp docked edge zone while visual pill remains sleek 8dp
        val widthPx = (24 * density).roundToInt()
        val heightPx = (config.handleHeightDp * density).roundToInt()
        val yOffsetPx = ((config.verticalFraction.coerceIn(0.15f, 0.85f) - 0.5f) * viewportH).roundToInt()

        return WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or
                if (config.dockEdge == DockEdge.START) Gravity.START else Gravity.END
            y = yOffsetPx
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title = "VodiGestureEdgeHandle"
        }
    }

    private fun buildExpandedCanvasLayoutParams(config: OverlayUiConfig): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (config.enableGlassBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (config.enableGlassBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurBehindRadius = 36
            }
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title = "VodiGestureCinematicCanvas"
        }
    }
}

/**
 * Composable root shared by the Global Accessibility Overlay and instrumentation UI tests.
 */
@Composable
fun GlobalOverlayRoot(
    state: GlobalOverlayUiState,
    onExpand: () -> Unit,
    onPoint: (GesturePoint) -> Unit,
    onStrokeFinished: () -> Unit,
    onPickPrediction: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = LocalExtendedColors.current
    if (!state.isCanvasExpanded) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxSize()
                .testTag("overlay_edge_handle")
                .semantics { contentDescription = "Open VodiGesture automation overlay" }
                .clickable(onClick = onExpand),
        ) {
            Box(
                modifier = Modifier
                    .width(state.config.handleThicknessDp.dp)
                    .height(state.config.handleHeightDp.dp)
                    .clip(CircleShape)
                    .background(extended.gestureRibbonStart.copy(alpha = state.config.idleOpacity)),
            )
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(extended.scrimCinematic),
    ) {
        val topPred = state.topPredictions.firstOrNull()
        val ghostPoints = if (state.config.showAiGhostTrail) {
            topPred?.ghostCompletionTrail.orEmpty()
        } else {
            emptyList()
        }

        CinematicGestureCanvas(
            activePoints = state.activePoints,
            ghostTrailPoints = ghostPoints,
            onPointSampled = onPoint,
            onStrokeStarted = {},
            onStrokeFinished = { onStrokeFinished() },
            currentStrokeId = state.currentStrokeId,
            accessibilityLabel = "Global gesture automation canvas",
            modifier = Modifier.fillMaxSize(),
        )

        // Top glassmorphic AI Prediction HUD
        AnimatedVisibility(
            visible = state.topPredictions.isNotEmpty(),
            enter = fadeIn() + scaleIn(animationSpec = VodiMotion.spatialFastSpring()),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
        ) {
            GlassmorphicCard(contentPadding = 14.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (topPred != null) {
                        ConfidenceArcGauge(
                            confidence = topPred.fusedProbability,
                            label = "AI",
                            diameter = 64.dp,
                        )
                    }
                    Column {
                        Text(
                            text = "Predicted Smart-Action",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.topPredictions.take(2).forEach { pred ->
                                AssistChip(
                                    onClick = { onPickPrediction(pred.templateId) },
                                    label = {
                                        Text(
                                            text = "${pred.templateName} ${(pred.fusedProbability * 100).roundToInt()}%",
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        FilledTonalButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .testTag("overlay_dismiss_button"),
        ) {
            Text(text = "Close Overlay")
        }
    }
}
