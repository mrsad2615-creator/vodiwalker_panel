package com.vodiwalker.gesture.feature.recorder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vodiwalker.gesture.core.designsystem.R
import com.vodiwalker.gesture.core.common.AppResult
import com.vodiwalker.gesture.core.common.GestureError
import com.vodiwalker.gesture.core.designsystem.component.CinematicGestureCanvas
import com.vodiwalker.gesture.core.designsystem.component.VodiPrimaryButton
import com.vodiwalker.gesture.core.designsystem.component.VodiSecondaryButton
import com.vodiwalker.gesture.core.designsystem.text.PersianText
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
    val feedback: RecorderFeedback = RecorderFeedback.DrawSample,
    val isError: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val collisionWithName: String? = null,
    val collisionSimilarityPercent: Int = 0,
    val invalidInputDetail: String? = null,
) {
    val canSave: Boolean
        get() = templateName.isNotBlank() && recordedSamples.size >= targetSampleCount
}

/**
 * Semantic recorder feedback. The ViewModel never emits user-facing prose, so every message is
 * localised in the composable (Persian first) and the state machine stays testable.
 */
enum class RecorderFeedback {
    /** Waiting for the user to draw the next calibration sample. */
    DrawSample,

    /** A sample was captured; another one is requested for confidence. */
    SampleCaptured,

    /** Every calibration sample is verified and the gesture can be saved. */
    ReadyToSave,

    /** The stroke was too short or had too few samples. */
    StrokeTooShort,

    /** The shape collides with an existing gesture. */
    Collision,

    /** Input was rejected by the engine for another reason. */
    InvalidInput,

    /** Persisted successfully. */
    Saved,
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
                    feedback = RecorderFeedback.StrokeTooShort,
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
                feedback = if (remaining > 0) {
                    RecorderFeedback.SampleCaptured
                } else {
                    RecorderFeedback.ReadyToSave
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
                feedback = RecorderFeedback.DrawSample,
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
                            feedback = RecorderFeedback.Saved,
                        )
                    }
                }
                is AppResult.Err -> {
                    when (val error = result.error) {
                        is GestureError.TemplateCollision -> _uiState.update {
                            it.copy(
                                isError = true,
                                feedback = RecorderFeedback.Collision,
                                collisionWithName = existing
                                    .firstOrNull { t -> t.id == error.conflictingTemplateId }
                                    ?.name,
                                collisionSimilarityPercent = (error.similarity * 100).toInt(),
                            )
                        }
                        is GestureError.InvalidInput -> _uiState.update {
                            it.copy(
                                isError = true,
                                feedback = RecorderFeedback.InvalidInput,
                                invalidInputDetail = error.message,
                            )
                        }
                        else -> _uiState.update {
                            it.copy(isError = true, feedback = RecorderFeedback.InvalidInput)
                        }
                    }
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
 * Stateless recording surface, reduced to the three decisions a person actually makes:
 * draw the shape, name it, save it. Advanced options (direction lock, haptic flavour) sit behind
 * a single optional row, and never block the primary action.
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
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("gesture_recorder_screen"),
    ) {
        // 1 — the instruction, in one line, plus shape progress as text.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.recorder_instruction),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = stringResource(
                        R.string.recorder_hint_sample,
                        PersianText.formatInteger(state.recordedSamples.size.toLong()),
                        PersianText.formatInteger(state.targetSampleCount.toLong()),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        Text(
            text = recorderFeedbackText(state),
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.testTag("recorder_feedback_text"),
        )

        // 2 — the drawing surface, the largest element on screen.
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                1.dp,
                if (state.livePoints.isEmpty()) {
                    MaterialTheme.colorScheme.outlineVariant
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("recorder_surface"),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                CinematicGestureCanvas(
                    activePoints = state.livePoints,
                    ghostTrailPoints = emptyList(),
                    onPointSampled = onPointSampled,
                    onStrokeStarted = {},
                    onStrokeFinished = onStrokeFinished,
                    currentStrokeId = state.currentStrokeId,
                    accessibilityLabel = stringResource(R.string.recorder_instruction),
                    modifier = Modifier.fillMaxSize(),
                )
                if (state.livePoints.isEmpty()) {
                    Text(
                        text = stringResource(R.string.recorder_instruction),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        // 3 — name it. Prefilled with a sensible default so the field is never a blocker.
        OutlinedTextField(
            value = state.templateName,
            onValueChange = onNameChanged,
            label = { Text(stringResource(R.string.recorder_name_label)) },
            placeholder = { Text(stringResource(R.string.recorder_name_placeholder)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("gesture_name_input"),
        )

        // 4 — actions: clear, lock the sample, save.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            VodiSecondaryButton(
                text = stringResource(R.string.recorder_clear),
                onClick = onReset,
                modifier = Modifier.weight(1f),
            )
            VodiPrimaryButton(
                text = if (state.recordedSamples.size >= state.targetSampleCount) {
                    stringResource(R.string.recorder_save)
                } else {
                    stringResource(R.string.recorder_hint_more)
                },
                onClick = if (state.canSave) onSave else onCommitSample,
                enabled = state.canSave || state.livePoints.isNotEmpty(),
                modifier = Modifier
                    .weight(1.6f)
                    .testTag("save_gesture_button"),
            )
        }

        // Optional behaviour, collapsed by default to keep the first run to three taps.
        TextButton(
            onClick = { showAdvanced = !showAdvanced },
            modifier = Modifier.testTag("recorder_advanced_toggle"),
        ) {
            Text(
                text = if (showAdvanced) {
                    stringResource(R.string.action_close)
                } else {
                    stringResource(R.string.recorder_category_label)
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }

        AnimatedVisibility(visible = showAdvanced) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.recorder_category_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
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
            }
        }
    }
}

/**
 * Maps the semantic recorder feedback to localised copy. Persian copy formats its own digits, so a
 * single call site owns every number the user sees on this screen.
 */
@Composable
private fun recorderFeedbackText(state: GestureRecorderUiState): String = when (state.feedback) {
    RecorderFeedback.DrawSample -> stringResource(
        R.string.recorder_hint_sample,
        PersianText.formatInteger(state.recordedSamples.size.toLong() + 1),
        PersianText.formatInteger(state.targetSampleCount.toLong()),
    )
    RecorderFeedback.SampleCaptured -> stringResource(
        R.string.recorder_hint_sample,
        PersianText.formatInteger(state.recordedSamples.size.toLong() + 1),
        PersianText.formatInteger(state.targetSampleCount.toLong()),
    )
    RecorderFeedback.ReadyToSave -> stringResource(R.string.recorder_hint_more)
    RecorderFeedback.StrokeTooShort -> stringResource(R.string.recorder_short_error)
    RecorderFeedback.Collision -> stringResource(
        R.string.recorder_collision,
        state.collisionWithName ?: "",
        PersianText.formatInteger(state.collisionSimilarityPercent.toLong()),
    )
    RecorderFeedback.InvalidInput -> state.invalidInputDetail
        ?: stringResource(R.string.recorder_short_error)
    RecorderFeedback.Saved -> stringResource(R.string.recorder_saved)
}
