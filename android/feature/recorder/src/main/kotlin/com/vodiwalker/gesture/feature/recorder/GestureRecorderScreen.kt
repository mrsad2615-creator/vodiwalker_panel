package com.vodiwalker.gesture.feature.recorder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vodiwalker.gesture.core.common.AppResult
import com.vodiwalker.gesture.core.common.GestureError
import com.vodiwalker.gesture.core.designsystem.component.CinematicGestureCanvas
import com.vodiwalker.gesture.core.designsystem.component.GlassmorphicCard
import com.vodiwalker.gesture.core.model.GestureCapture
import com.vodiwalker.gesture.core.model.GestureCategory
import com.vodiwalker.gesture.core.model.GesturePoint
import com.vodiwalker.gesture.core.model.GestureStroke
import com.vodiwalker.gesture.core.model.GestureTemplateRepository
import com.vodiwalker.gesture.core.model.HapticPreset
import com.vodiwalker.gesture.engine.gesture.GestureGeometry
import com.vodiwalker.gesture.engine.gesture.HybridGestureEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Immutable Unidirectional Data Flow state for the 3-Sample Custom Gesture Calibration Studio.
 */
data class GestureRecorderUiState(
    val templateName: String = "",
    val category: GestureCategory = GestureCategory.AUTOMATION_MACRO,
    val orientationSensitive: Boolean = true,
    val selectedHaptic: HapticPreset = HapticPreset.CONFIRM_CINEMATIC,
    val livePoints: List<GesturePoint> = emptyList(),
    val currentStrokeId: Int = 0,
    val recordedSamples: List<GestureCapture> = emptyList(),
    val targetSampleCount: Int = 3,
    val feedbackMessage: String = "Draw sample 1 of 3 on the cinematic surface.",
    val isError: Boolean = false,
    val savedSuccessfully: Boolean = false,
) {
    val canSave: Boolean
        get() = templateName.isNotBlank() && recordedSamples.size >= targetSampleCount
}

/**
 * 3-sample calibration UDF state machine that persists through the repository port.
 */
@HiltViewModel
class GestureRecorderViewModel @Inject constructor(
    private val templateRepository: GestureTemplateRepository,
    private val gestureEngine: HybridGestureEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GestureRecorderUiState())
    val uiState: StateFlow<GestureRecorderUiState> = _uiState.asStateFlow()

    /**
     * Updates the template name (bounded to 40 characters).
     */
    fun updateName(name: String) {
        _uiState.update { it.copy(templateName = name.take(40), isError = false, savedSuccessfully = false) }
    }

    /**
     * Toggles directional strictness for the calibrated template.
     */
    fun setOrientationSensitive(enabled: Boolean) {
        _uiState.update { it.copy(orientationSensitive = enabled) }
    }

    /**
     * Selects the confirmation haptic emitted on a successful match.
     */
    fun selectHaptic(preset: HapticPreset) {
        _uiState.update { it.copy(selectedHaptic = preset) }
    }

    /**
     * Appends a live pointer sample to the in-progress stroke.
     */
    fun appendPoint(point: GesturePoint) {
        _uiState.update { state -> state.copy(livePoints = state.livePoints + point) }
    }

    /** Commits the current single- or multi-stroke utterance as one calibration sample. */
    fun commitCurrentSample() {
        val current = _uiState.value
        val len = GestureGeometry.pathLength(current.livePoints)
        if (current.livePoints.size < 5 || len < GestureGeometry.MIN_VALID_PATH_LENGTH_PX) {
            _uiState.update {
                it.copy(
                    livePoints = emptyList(),
                    currentStrokeId = 0,
                    isError = true,
                    feedbackMessage = "Stroke too short — draw a larger, distinct gesture.",
                )
            }
            return
        }

        val grouped = current.livePoints.groupBy { it.strokeId }
            .map { (id, pts) -> GestureStroke(id, pts) }
        val updatedSamples = (current.recordedSamples + GestureCapture(grouped))
            .take(current.targetSampleCount)
        val remaining = current.targetSampleCount - updatedSamples.size

        _uiState.update {
            it.copy(
                livePoints = emptyList(),
                currentStrokeId = 0,
                recordedSamples = updatedSamples,
                isError = false,
                feedbackMessage = if (remaining > 0) {
                    "Sample ${updatedSamples.size} locked. Draw $remaining more for precision averaging."
                } else {
                    "All ${current.targetSampleCount} calibration samples verified. Ready to save."
                },
            )
        }
    }

    /**
     * Opens the next stroke index for multi-stroke gestures.
     */
    fun advanceMultiStroke() {
        _uiState.update { it.copy(currentStrokeId = it.currentStrokeId + 1) }
    }

    /**
     * Discards all calibration samples and restarts at sample 1.
     */
    fun resetSamples() {
        _uiState.update {
            it.copy(
                livePoints = emptyList(),
                currentStrokeId = 0,
                recordedSamples = emptyList(),
                isError = false,
                savedSuccessfully = false,
                feedbackMessage = "Calibration cleared. Draw sample 1 of ${it.targetSampleCount}.",
            )
        }
    }

    /**
     * Calibrates against the collision gate and persists the template.
     */
    fun saveCalibratedGesture() {
        val snapshot = _uiState.value
        if (!snapshot.canSave) return

        viewModelScope.launch {
            val existing = templateRepository.getAllTemplates()
            val result = gestureEngine.createCalibratedTemplate(
                id = "gst_${UUID.randomUUID().toString().take(8)}",
                name = snapshot.templateName,
                category = snapshot.category,
                calibrationCaptures = snapshot.recordedSamples,
                existingTemplates = existing,
                orientationSensitive = snapshot.orientationSensitive,
                createdAtMillis = System.currentTimeMillis(),
            )
            when (result) {
                is AppResult.Ok -> {
                    val withHaptic = result.value.copy(hapticPatternId = snapshot.selectedHaptic.id)
                    templateRepository.upsertTemplate(withHaptic)
                    _uiState.update {
                        it.copy(
                            savedSuccessfully = true,
                            isError = false,
                            recordedSamples = emptyList(),
                            livePoints = emptyList(),
                            templateName = "",
                            feedbackMessage = "Saved '${withHaptic.name}' with zero collisions.",
                        )
                    }
                }
                is AppResult.Err -> {
                    val msg = when (val err = result.error) {
                        is GestureError.TemplateCollision ->
                            "Conflicts with existing gesture (${(err.similarity * 100).toInt()}% similar)."
                        is GestureError.InvalidInput -> err.message
                        else -> "Unable to calibrate template."
                    }
                    _uiState.update { it.copy(isError = true, feedbackMessage = msg) }
                }
            }
        }
    }
}

/**
 * Stateful entry point wiring the recorder ViewModel to the stateless screen.
 */
@Composable
fun GestureRecorderRoute(viewModel: GestureRecorderViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GestureRecorderScreen(
        state = state,
        onNameChanged = viewModel::updateName,
        onToggleOrientation = viewModel::setOrientationSensitive,
        onSelectHaptic = viewModel::selectHaptic,
        onPointSampled = viewModel::appendPoint,
        onStrokeFinished = { viewModel.advanceMultiStroke() },
        onCommitSample = viewModel::commitCurrentSample,
        onReset = viewModel::resetSamples,
        onSave = viewModel::saveCalibratedGesture,
    )
}

/**
 * Stateless M3 Custom Gesture Calibration Studio screen supporting single- and multi-stroke entry.
 */
@Composable
fun GestureRecorderScreen(
    state: GestureRecorderUiState,
    onNameChanged: (String) -> Unit,
    onToggleOrientation: (Boolean) -> Unit,
    onSelectHaptic: (HapticPreset) -> Unit,
    onPointSampled: (GesturePoint) -> Unit,
    onStrokeFinished: (Int) -> Unit,
    onCommitSample: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("gesture_recorder_screen"),
    ) {
        OutlinedTextField(
            value = state.templateName,
            onValueChange = onNameChanged,
            label = { Text("Gesture Name") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("gesture_name_input"),
        )

        LinearProgressIndicator(
            progress = { state.recordedSamples.size.toFloat() / state.targetSampleCount.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = state.feedbackMessage,
            style = MaterialTheme.typography.bodySmall,
            color = if (state.isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.testTag("recorder_feedback_text"),
        )

        GlassmorphicCard(
            contentPadding = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                CinematicGestureCanvas(
                    activePoints = state.livePoints,
                    ghostTrailPoints = emptyList(),
                    onPointSampled = onPointSampled,
                    onStrokeStarted = {},
                    onStrokeFinished = onStrokeFinished,
                    currentStrokeId = state.currentStrokeId,
                    accessibilityLabel = "Custom gesture calibration canvas",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Lock Direction / Orientation", style = MaterialTheme.typography.labelLarge)
            Switch(
                checked = state.orientationSensitive,
                onCheckedChange = onToggleOrientation,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                HapticPreset.CONFIRM_CRISP,
                HapticPreset.CONFIRM_CINEMATIC,
                HapticPreset.AI_LOCK_IN,
            ).forEach { preset ->
                FilterChip(
                    selected = state.selectedHaptic == preset,
                    onClick = { onSelectHaptic(preset) },
                    label = { Text(preset.displayName) },
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f),
            ) {
                Text("Reset")
            }
            OutlinedButton(
                onClick = onCommitSample,
                enabled = state.livePoints.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("lock_sample_button"),
            ) {
                Text("Lock Sample")
            }
            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier
                    .weight(1.2f)
                    .height(48.dp)
                    .testTag("save_gesture_button"),
            ) {
                Text("Save")
            }
        }
    }
}
