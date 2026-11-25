package com.neurox.feature.session

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neurox.designsystem.tokens.NxMotion
import com.neurox.designsystem.components.NxPrimaryButton
import com.neurox.designsystem.tokens.NxRadius
import com.neurox.designsystem.components.NxSecondaryButton
import com.neurox.designsystem.components.NxSectionHeader
import com.neurox.designsystem.tokens.NxSize
import com.neurox.designsystem.tokens.NxSpacing
import com.neurox.designsystem.components.NxStat
import com.neurox.designsystem.components.NxSurface
import com.neurox.designsystem.theme.NxTheme
import com.neurox.designsystem.tokens.NxType
import com.neurox.designsystem.theme.NxTypography
import com.neurox.domain.engine.TrialKind
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.model.Module
import com.neurox.games.stimulus.ResponseInput
import com.neurox.games.stimulus.Stimulus
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.session.ResultPresentation
import com.neurox.presentation.session.SessionUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * The training surface.
 *
 * DESIGN INTENT: everything that is not the stimulus gets out of the way. The
 * session chrome is a thin, low-contrast band; the field is the only bright object.
 * That is a measurement decision as much as an aesthetic one: a decorated border
 * competes with peripheral stimuli.
 *
 * IMPLEMENTATION NOTES
 * - The frame loop is a coroutine that calls [onTick]; the controller (pure Kotlin,
 *   tested) owns all timing and state. Nothing here computes a score or a duration.
 * - The whole stage is one tap target for reflex exercises, and the field is a grid
 *   of separate targets for vision exercises. Both are described for accessibility.
 */
@Composable
fun SessionScreen(
    state: SessionUiState,
    strings: Strings,
    onTick: () -> Unit,
    onAnswer: (ResponseInput) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onDone: (ResultPresentation) -> Unit,
    result: ResultPresentation?,
    modifier: Modifier = Modifier,
) {
    // One frame loop for the whole screen. It stops when the session finishes.
    LaunchedEffect(state.phase) {
        if (state.phase == SessionUiState.Phase.FINISHED) return@LaunchedEffect
        while (isActive) {
            onTick()
            delay(FRAME_MILLIS)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        SessionHeader(state = state, strings = strings, onPause = onPause, onEnd = onEnd)
        Box(modifier = Modifier.weight(1f)) {
            when (state.phase) {
                SessionUiState.Phase.READY -> ReadyPanel(state, strings) { onTick() }
                SessionUiState.Phase.COUNTDOWN -> CountdownPanel(state, strings)
                SessionUiState.Phase.RUNNING,
                SessionUiState.Phase.FEEDBACK,
                -> StimulusStage(state = state, strings = strings, onAnswer = onAnswer)
                SessionUiState.Phase.PAUSED -> PausedPanel(state, strings, onResume = onResume, onEnd = onEnd)
                SessionUiState.Phase.FINISHED -> ResultPanel(
                    strings = strings,
                    result = result,
                    onDone = onDone,
                )
            }
        }
    }
}

private const val FRAME_MILLIS = 16L

@Composable
private fun SessionHeader(
    state: SessionUiState,
    strings: Strings,
    onPause: () -> Unit,
    onEnd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NxSpacing.lg, vertical = NxSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NxSpacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = strings.raw(state.template.displayKey),
                style = NxTypography.title,
                color = NxTheme.colors.textPrimary,
            )
            Text(
                text = strings.format("session.round", state.roundNumber, state.roundCount),
                style = NxTypography.caption,
                color = NxTheme.colors.textTertiary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
            if (state.phase == SessionUiState.Phase.RUNNING || state.phase == SessionUiState.Phase.FEEDBACK) {
                SessionIconButton(
                    label = strings.raw("session.pause"),
                    enabled = state.canPause,
                    onClick = onPause,
                )
            }
            SessionIconButton(
                label = strings.raw("session.end"),
                enabled = state.phase != SessionUiState.Phase.FINISHED,
                onClick = onEnd,
            )
        }
    }
    NxProgressBarLite(progress = state.progress.toFloat())
}

/**
 * A text button sized as an icon target.
 *
 * The label is always present as contentDescription, so a screen reader announces
 * "Pause" instead of "button".
 */
@Composable
private fun SessionIconButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(NxSize.minTouchTarget)
            .clip(CircleShape)
            .border(1.dp, NxTheme.colors.border, CircleShape)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures { onClick() }
                }
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = NxTypography.caption,
            color = if (enabled) NxTheme.colors.textSecondary else NxTheme.colors.disabled,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NxProgressBarLite(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(NxTheme.colors.surfaceSunken),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.dp)
                .background(NxTheme.colors.accent),
        )
    }
}

@Composable
private fun ReadyPanel(state: SessionUiState, strings: Strings, onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NxSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(NxSpacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = strings.raw(state.template.displayKey),
            style = NxTypography.display,
            color = NxTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = strings.raw(state.template.descriptionKey),
            style = NxTypography.body,
            color = NxTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
            NxChipFilled(strings.raw("difficulty.${state.difficulty}"))
            NxChipFilled(strings.format("home.minutes", (state.remainingMillis / 60_000L).toInt()))
        }
        if (state.isPractice) {
            Text(
                text = strings.raw("session.practiceBody"),
                style = NxTypography.caption,
                color = NxTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
        NxPrimaryButton(
            label = strings.raw("session.tapToStart"),
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NxChipFilled(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(NxRadius.pill))
            .background(NxTheme.colors.surfaceElevated)
            .border(1.dp, NxTheme.colors.border, RoundedCornerShape(NxRadius.pill))
            .padding(horizontal = NxSpacing.md, vertical = NxSpacing.xs),
    ) {
        Text(text = text, style = NxTypography.caption, color = NxTheme.colors.textSecondary)
    }
}

@Composable
private fun CountdownPanel(state: SessionUiState, strings: Strings) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(96.dp),
                color = NxTheme.colors.accent,
                strokeWidth = 3.dp,
            )
            Spacer(Modifier.height(NxSpacing.lg))
            Text(
                text = strings.raw("session.starting"),
                style = NxTypography.title,
                color = NxTheme.colors.textSecondary,
            )
        }
    }
}

/**
 * The stage. Two interaction models:
 * - a grid field: each cell is its own target, so vision exercises can be answered
 *   by position;
 * - a single stage: the whole area is one target, which is what reflex exercises
 *   measure (a finger that has to find a target would add a second skill).
 */
@Composable
private fun StimulusStage(
    state: SessionUiState,
    strings: Strings,
    onAnswer: (ResponseInput) -> Unit,
) {
    val trial = state.visibleTrial
    val stimulus = trial?.stimulus ?: Stimulus()
    val isGrid = stimulus.targetCells.isNotEmpty() || stimulus.distractorCells.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NxSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NxSpacing.md),
    ) {
        StimulusReadout(state = state, strings = strings, stimulus = stimulus)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (state.stimulusVisible && trial != null) {
                if (isGrid) {
                    StimulusGrid(
                        state = state,
                        stimulus = stimulus,
                        strings = strings,
                        onAnswer = onAnswer,
                    )
                } else {
                    SingleTargetStage(
                        state = state,
                        stimulus = stimulus,
                        strings = strings,
                        onAnswer = onAnswer,
                    )
                }
            } else {
                // Between trials the stage stays empty on purpose: a placeholder
                // shape would be mistaken for the next stimulus.
                Text(
                    text = strings.raw("session.hold"),
                    style = NxTypography.caption,
                    color = NxTheme.colors.textTertiary,
                )
            }
        }
        SessionFooter(state = state, strings = strings)
    }
}

/**
 * Above the stimulus: only what the current trial actually asks, plus a
 * trial-window bar. No running score is shown during a session, because watching a
 * score is a second task and would change what is measured.
 */
@Composable
private fun StimulusReadout(state: SessionUiState, strings: Strings, stimulus: Stimulus) {
    Column(verticalArrangement = Arrangement.spacedBy(NxSpacing.xs)) {
        val cue = when {
            stimulus.choices.isNotEmpty() -> strings.raw("vision.findTarget")
            stimulus.direction in 0..3 -> strings.raw("reflex.tap")
            state.module == Module.READ -> strings.raw("read.startReading")
            state.module == Module.TYPE -> strings.raw("session.typeHere")
            else -> strings.raw("session.cue")
        }
        Text(text = cue, style = NxTypography.label, color = NxTheme.colors.textSecondary)
        if (stimulus.choices.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                stimulus.choices.forEach { choice ->
                    NxChipFilled(choice)
                }
            }
        }
    }
}

@Composable
private fun StimulusGrid(
    state: SessionUiState,
    stimulus: Stimulus,
    strings: Strings,
    onAnswer: (ResponseInput) -> Unit,
) {
    val columns = state.fieldColumns
    val rows = state.fieldRows
    // weight() is only available inside Row/Column content, so the cell fills its
    // slot instead of claiming a weight it cannot have at function scope.
    val cellModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .padding(NxSpacing.xxs)

    Column(modifier = Modifier.fillMaxWidth()) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until columns) {
                    val cell = row * columns + column
                    val isTarget = cell in stimulus.targetCells
                    val isDistractor = cell in stimulus.distractorCells
                    val symbolIndex = stimulus.distractorCells.toList().indexOf(cell)
                    val symbol = when {
                        isTarget -> stimulus.targetSymbol
                        isDistractor && symbolIndex in stimulus.distractorSymbols.indices ->
                            stimulus.distractorSymbols[symbolIndex]
                        else -> -1
                    }
                    StimulusCell(
                        cell = cell,
                        symbol = symbol,
                        accent = if (isTarget) state.module.accentColor() else NxTheme.colors.textTertiary,
                        isTarget = isTarget,
                        description = strings.format("vision.cellTap", cell + 1),
                        modifier = cellModifier,
                        onClick = { onAnswer(ResponseInput.CellTap(cell)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StimulusCell(
    cell: Int,
    symbol: Int,
    accent: Color,
    isTarget: Boolean,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(NxRadius.sm))
            .background(NxTheme.colors.surfaceElevated)
            .border(
                width = if (isTarget) 1.5.dp else 1.dp,
                color = if (isTarget) accent else NxTheme.colors.border,
                shape = RoundedCornerShape(NxRadius.sm),
            )
            .pointerInput(cell) {
                detectTapGestures(onTap = { onClick() })
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (symbol >= 0) {
            StimulusGlyph(symbol = symbol, color = accent, size = 20.dp)
        }
    }
}

/**
 * Original geometric glyphs, drawn from primitives.
 *
 * No third-party icon set is bundled: the shapes are simple geometry, so there is
 * no attribution requirement and no license exposure. Each shape is also distinct
 * at small sizes and distinguishable without color, which matters for the
 * color-reaction exercise where color carries the meaning.
 */
@Composable
fun StimulusGlyph(symbol: Int, color: Color, size: Dp, modifier: Modifier = Modifier) {
    val shape = when (symbol % 5) {
        0 -> CircleShape
        1 -> RoundedCornerShape(2.dp)
        2 -> RoundedCornerShape(percent = 50)
        3 -> RoundedCornerShape(topStartPercent = 0, topEndPercent = 50, bottomStartPercent = 0, bottomEndPercent = 0)
        else -> RoundedCornerShape(percent = 15)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(color),
    )
}

@Composable
private fun SingleTargetStage(
    state: SessionUiState,
    stimulus: Stimulus,
    strings: Strings,
    onAnswer: (ResponseInput) -> Unit,
) {
    val accent = state.module.accentColor()
    val isNoGo = state.visibleTrial?.kind == TrialKind.NO_GO
    val targetSize = animateFloatAsState(
        targetValue = if (state.stimulusVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = NxMotion.TAP_MILLIS,
            easing = NxMotion.decelerate,
        ),
        label = "stimulus",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(NxRadius.lg))
            .background(if (isNoGo) NxTheme.colors.surfaceSunken else NxTheme.colors.surface)
            .border(
                width = 1.dp,
                color = if (isNoGo) NxTheme.colors.borderStrong else accent.copy(alpha = 0.5f),
                shape = RoundedCornerShape(NxRadius.lg),
            )
            .pointerInput(state.roundIndex, state.phase) {
                detectTapGestures(onTap = { onAnswer(ResponseInput.Tap) })
            }
            .semantics {
                contentDescription = strings.raw(if (isNoGo) "reflex.noGo" else "reflex.tap")
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (stimulus.choices.isEmpty() && stimulus.direction !in 0..3) {
                StimulusGlyph(
                    symbol = stimulus.targetSymbol,
                    color = if (isNoGo) NxTheme.colors.textTertiary else accent,
                    size = (64.dp * (0.6f + 0.4f * targetSize.value)),
                )
            }
            if (isNoGo) {
                Spacer(Modifier.height(NxSpacing.md))
                Text(
                    text = strings.raw("reflex.noGo"),
                    style = NxTypography.title,
                    color = NxTheme.colors.textSecondary,
                )
            }
            if (stimulus.direction in 0..3) {
                Spacer(Modifier.height(NxSpacing.md))
                DirectionRow(strings = strings, onAnswer = onAnswer)
            }
            if (stimulus.choices.isNotEmpty()) {
                Spacer(Modifier.height(NxSpacing.md))
                ChoiceColumn(strings = strings, choices = stimulus.choices, onAnswer = onAnswer)
            }
        }
    }
}

@Composable
private fun DirectionRow(strings: Strings, onAnswer: (ResponseInput) -> Unit) {
    // Direction is mapped to four large targets. The labels are the direction names
    // in the active language, not arrow glyphs, so a screen reader can announce them.
    val labels = listOf("direction.up", "direction.right", "direction.down", "direction.left")
    Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.md)) {
        labels.forEachIndexed { index, key ->
            Box(
                modifier = Modifier
                    .size(NxSize.minTouchTarget * 1.4f)
                    .clip(RoundedCornerShape(NxRadius.md))
                    .background(NxTheme.colors.surfaceElevated)
                    .border(1.dp, NxTheme.colors.border, RoundedCornerShape(NxRadius.md))
                    .pointerInput(index) {
                        detectTapGestures { onAnswer(ResponseInput.DirectionAnswer(index)) }
                    }
                    .semantics { contentDescription = strings.raw(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = strings.raw(key), style = NxTypography.caption, color = NxTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun ChoiceColumn(strings: Strings, choices: List<String>, onAnswer: (ResponseInput) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
        choices.forEachIndexed { index, choice ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NxSize.minTouchTarget)
                    .clip(RoundedCornerShape(NxRadius.sm))
                    .background(NxTheme.colors.surfaceElevated)
                    .border(1.dp, NxTheme.colors.border, RoundedCornerShape(NxRadius.sm))
                    .pointerInput(index) {
                        detectTapGestures { onAnswer(ResponseInput.Choice(index)) }
                    }
                    .semantics { contentDescription = strings.format("a11y.exerciseCard", choice, "") },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = choice, style = NxTypography.body, color = NxTheme.colors.textPrimary)
            }
        }
    }
}

@Composable
private fun SessionFooter(state: SessionUiState, strings: Strings) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings.format("a11y.timer", strings.decimal(state.remainingMillis / 1000L)),
            style = NxTypography.caption,
            color = NxTheme.colors.textTertiary,
        )
        // Feedback is a word, never only a color: color-only feedback is invisible to
        // a color-blind user and to a screen reader.
        // A property cannot be smart-cast across module boundaries; capture it once.
        val messageKey = state.statusMessageKey
        val feedback = when {
            state.lastAnswerCorrect == true -> strings.raw("session.result.answered")
            messageKey != null -> strings.raw(messageKey)
            else -> ""
        }
        Text(
            text = feedback,
            style = NxTypography.caption,
            color = when (state.lastAnswerCorrect) {
                true -> NxTheme.colors.success
                false -> NxTheme.colors.warning
                null -> NxTheme.colors.textTertiary
            },
        )
    }
}

@Composable
private fun PausedPanel(
    state: SessionUiState,
    strings: Strings,
    onResume: () -> Unit,
    onEnd: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NxSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(NxSpacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = strings.raw("session.paused"),
            style = NxTypography.headline,
            color = NxTheme.colors.textPrimary,
        )
        Text(
            text = strings.raw("session.paused.body"),
            style = NxTypography.body,
            color = NxTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        NxPrimaryButton(
            label = strings.raw("session.resume"),
            onClick = onResume,
            modifier = Modifier.fillMaxWidth(),
        )
        NxSecondaryButton(
            label = strings.raw("session.end"),
            onClick = onEnd,
            modifier = Modifier.fillMaxWidth(),
            destructive = true,
        )
    }
}

@Composable
private fun ResultPanel(
    strings: Strings,
    result: ResultPresentation?,
    onDone: (ResultPresentation) -> Unit,
) {
    if (result == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = strings.raw("common.loading"), style = NxTypography.body, color = NxTheme.colors.textSecondary)
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NxSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(NxSpacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = strings.raw(result.titleKey),
            style = NxTypography.headline,
            color = NxTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.xl)) {
            NxStat(
                label = strings.raw("session.accuracy"),
                value = strings.percent(result.accuracy),
            )
            NxStat(
                label = strings.raw("session.score"),
                value = strings.decimal(result.score.toLong()),
            )
            if (result.hasReactionData) {
                NxStat(
                    label = strings.raw("session.reaction"),
                    value = strings.millis(result.meanResponseMillis),
                )
            }
        }
        // XP is stated as earned, or explicitly not earned. Never a silent zero.
        Text(
            text = if (result.countsForProgress) {
                strings.format("session.result.xp", result.xp)
            } else {
                strings.raw("session.result.noXp")
            },
            style = NxTypography.label,
            color = if (result.countsForProgress) NxTheme.colors.accent else NxTheme.colors.textTertiary,
        )
        if (result.isPerfect) {
            Text(
                text = strings.raw("session.result.perfect"),
                style = NxTypography.title,
                color = NxTheme.colors.success,
            )
        }
        result.adaptation?.let { adaptation ->
            NxSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(NxSpacing.lg)) {
                    NxSectionHeader(title = strings.raw("session.result.adaptation"))
                    Text(
                        text = strings.raw(adaptation.explanationKey),
                        style = NxTypography.caption,
                        color = NxTheme.colors.textSecondary,
                    )
                    if (adaptation.changes) {
                        Spacer(Modifier.height(NxSpacing.xs))
                        Text(
                            text = strings.format(
                                "adaptive.change",
                                adaptation.fromDifficulty,
                                adaptation.toDifficulty,
                            ),
                            style = NxTypography.label,
                            color = NxTheme.colors.accent,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NxSpacing.md),
        ) {
            NxSecondaryButton(
                label = strings.raw("session.result.details"),
                onClick = { onDone(result) },
                modifier = Modifier.weight(1f),
            )
            NxPrimaryButton(
                label = strings.raw("session.result.home"),
                onClick = { onDone(result) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Module accent color, resolved from the theme rather than hard-coded. */
@Composable
fun Module.accentColor(): Color = when (this) {
    Module.VISION -> NxTheme.colors.vision
    Module.REFLEX -> NxTheme.colors.reflex
    Module.READ -> NxTheme.colors.read
    Module.TYPE -> NxTheme.colors.type
}

