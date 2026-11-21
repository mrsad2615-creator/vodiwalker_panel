package dev.mrsad.app.feature.mind

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.ui.components.IconFacet
import dev.mrsad.app.ui.components.LocalBottomBarClearance
import dev.mrsad.app.ui.components.MetalIcon
import dev.mrsad.app.ui.components.MetricValue
import dev.mrsad.app.ui.components.ModuleSculpture3D
import dev.mrsad.app.ui.components.ModuleSculptureType
import dev.mrsad.app.ui.components.MrsadChip
import dev.mrsad.app.ui.components.MrsadDivider
import dev.mrsad.app.ui.components.MrsadIcons
import dev.mrsad.app.ui.components.MrsadPrimaryButton
import dev.mrsad.app.ui.components.MrsadProgressBar
import dev.mrsad.app.ui.components.MrsadSecondaryButton
import dev.mrsad.app.ui.components.MrsadSurface
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.components.ObsidianCollapsingTopBar
import dev.mrsad.app.ui.components.SectionHeader
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.ObsidianSpectrum
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.formatInt
import dev.mrsad.app.ui.theme.formatPercent
import kotlinx.coroutines.delay

/**
 * MODULE 04 — MRSAD BRAIN TRAINING (Master Prompt §4):
 * Primary Identity: Magenta (`#B53D91`) & Pink (`#F18ACD`).
 */
@Composable
fun MindScreen(
    container: AppContainer,
    settings: MrsadSettings,
) {
    val viewModel: MindViewModel = viewModel(key = "mind", factory = MindViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    AmbientBackground(PageEnvironment.MIND) {
        when (state.screen) {
            MindScreenState.LOBBY -> MindLobby(state, viewModel, settings)
            MindScreenState.PLAYING -> MindPlayer(state, viewModel, settings)
            MindScreenState.RESULT -> MindResult(state, viewModel, settings)
        }
    }
}

@Composable
private fun MindLobby(state: MindUiState, viewModel: MindViewModel, settings: MrsadSettings) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val scrollState = rememberScrollState()
    val isCollapsed by remember { derivedStateOf { scrollState.value > 28 } }

    Column(
        Modifier
            .fillMaxSize()
            .padding(bottom = LocalBottomBarClearance.current),
    ) {
        ObsidianCollapsingTopBar(
            title = "MRSAD MIND",
            subtitle = "مرکز تمرین شناختی، حافظه، تمرکز و سرعت واکنش",
            collapsed = isCollapsed,
            spectralColor = ObsidianSpectrum.Pink,
            leading = {
                ModuleSculpture3D(
                    type = ModuleSculptureType.BRAIN,
                    modifier = Modifier.size(if (isCollapsed) 36.dp else 48.dp),
                    animated = true,
                )
            },
            trailing = {
                MrsadChip(
                    text = "${formatInt(state.totalSessions, settings.persianNumerals)} جلسه",
                    tint = ObsidianSpectrum.Pink,
                )
            },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = MrsadTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
        ) {
            Spacer(Modifier.height(MrsadTokens.Spacing.xs))

            MrsadSurface(
                Modifier.fillMaxWidth(),
                accent = ObsidianSpectrum.Pink,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MetricValue(
                        value = formatInt(state.totalSessions, settings.persianNumerals),
                        label = "جلسات انجام‌شده",
                        size = type.headline.fontSize,
                        accent = ObsidianSpectrum.Pink,
                    )
                    MetricValue(
                        value = formatPercent(state.averageScore, settings.persianNumerals),
                        label = "میانگین امتیاز",
                        size = type.headline.fontSize,
                        accent = ObsidianSpectrum.Lavender,
                    )
                    MetricValue(
                        value = formatInt(state.practisedExercises, settings.persianNumerals),
                        label = "تمرین‌های فعال",
                        size = type.headline.fontSize,
                    )
                }
                if (state.totalSessions == 0) {
                    Text(
                        "هنوز داده‌ای ثبت نشده است. با یک تمرین شروع کنید؛ سطح هر تمرین به‌صورت خودکار با نتایج خودِ همان تمرین تنظیم می‌شود.",
                        style = type.caption,
                        color = colors.textMuted,
                    )
                }
            }

            SectionHeader(title = "مجموعهٔ تمرین‌های شناختی")
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height((MindExercise.entries.size / 2 + MindExercise.entries.size % 2).let { (it * 148).dp }),
                horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
                userScrollEnabled = false,
            ) {
                itemsIndexed(MindExercise.entries) { _, exercise ->
                    ExerciseCard(
                        exercise = exercise,
                        levelLabel = "سطح ${formatInt(state.levelOf(exercise) + 1, settings.persianNumerals)}",
                        hasHistory = state.bestScores.containsKey(exercise),
                        bestLabel = state.bestScores[exercise]?.let {
                            "بهترین: ${formatPercent(it, settings.persianNumerals)}"
                        },
                        onClick = { viewModel.openExercise(exercise) },
                    )
                }
            }

            MrsadSurface(
                Modifier.fillMaxWidth(),
                accent = ObsidianSpectrum.Pink,
            ) {
                Text("موتور تطبیقی سطح دشواری", style = type.title, color = colors.textPrimary)
                Text(
                    "هر تمرین نردبان سختی مستقل خود را دارد؛ دو جلسهٔ پیاپی بالای ۸۵٪ یک سطح بالاتر و دو جلسهٔ پیاپی زیر ۵۰٪ یک سطح پایین‌تر می‌آورد. " +
                        "این تنظیم فقط بر داده‌های واقعی شما متکی است و هیچ ادعای پزشکی مطرح نمی‌کند.",
                    style = type.body,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(MrsadTokens.Spacing.lg))
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: MindExercise,
    levelLabel: String,
    hasHistory: Boolean,
    bestLabel: String?,
    onClick: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    MrsadSurface(
        modifier = Modifier.fillMaxWidth().height(142.dp),
        accent = ObsidianSpectrum.Pink,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            MetalIcon(
                exercise.facets,
                accentOverride = ObsidianSpectrum.Pink,
                modifier = Modifier.size(22.dp),
            )
            MrsadChip(
                text = levelLabel,
                tint = if (hasHistory) ObsidianSpectrum.Pink else colors.textMuted,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            exercise.titleFa,
            style = type.body.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
            maxLines = 1,
        )
        Text(exercise.subtitleFa, style = type.caption, color = colors.textMuted, maxLines = 2)
        Spacer(Modifier.weight(1f))
        Text(
            bestLabel ?: "بدون سابقه",
            style = type.caption,
            color = if (bestLabel != null) ObsidianSpectrum.Aqua else colors.textMuted,
        )
    }
}

@Composable
private fun MindPlayer(state: MindUiState, viewModel: MindViewModel, settings: MrsadSettings) {
    val scrollState = rememberScrollState()
    val isCollapsed by remember { derivedStateOf { scrollState.value > 28 } }

    Column(Modifier.fillMaxSize()) {
        ObsidianCollapsingTopBar(
            title = state.exercise?.titleFa ?: "تمرین",
            subtitle = state.exercise?.instructionFa ?: "",
            collapsed = isCollapsed,
            spectralColor = ObsidianSpectrum.Pink,
            onBack = { viewModel.abort() },
            trailing = {
                MrsadChip(
                    text = "سطح ${formatInt(state.level + 1, settings.persianNumerals)}",
                    tint = ObsidianSpectrum.Pink,
                )
            },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = MrsadTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
        ) {
            Spacer(Modifier.height(MrsadTokens.Spacing.xs))
            MrsadProgressBar(
                progress = state.progress,
                height = 5.dp,
                tint = ObsidianSpectrum.Pink,
            )

            when (state.exercise) {
                MindExercise.NUMBER_MEMORY -> NumberMemoryStage(state, viewModel, settings)
                MindExercise.PATTERN_MEMORY -> PatternMemoryStage(state, viewModel, settings)
                MindExercise.ATTENTION -> AttentionStage(state, viewModel, settings)
                MindExercise.STROOP -> StroopStage(state, viewModel, settings)
                MindExercise.ARITHMETIC -> ArithmeticStage(state, viewModel, settings)
                MindExercise.LOGIC -> LogicStage(state, viewModel, settings)
                MindExercise.REACTION -> ReactionStage(state, viewModel, settings)
                null -> Unit
            }
            Spacer(Modifier.height(MrsadTokens.Spacing.lg))
        }
    }
}

@Composable
private fun NumberMemoryStage(state: MindUiState, viewModel: MindViewModel, settings: MrsadSettings) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.rememberTimer) {
        if (state.rememberTimer == 0L) return@LaunchedEffect
        delay(2000)
        viewModel.revealDigits()
    }
    MrsadSurface(
        Modifier.fillMaxWidth(),
        elevated = true,
        accent = ObsidianSpectrum.Pink,
    ) {
        if (state.showingStimulus) {
            Text(
                "این اعداد را به خاطر بسپارید",
                style = type.caption,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                state.digits,
                style = type.display.copy(fontSize = 52.sp, letterSpacing = 6.sp),
                color = ObsidianSpectrum.Pink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = MrsadTokens.Spacing.md),
            )
        } else {
            Text("اعداد را وارد کنید", style = type.caption, color = colors.textMuted)
            NumberEntryField(
                entry = state.entry,
                onAppend = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.appendDigit(it, settings.hapticsEnabled)
                },
                onDelete = { viewModel.deleteDigit() },
                onSubmit = { viewModel.submitNumberMemory() },
            )
        }
    }
}

@Composable
private fun NumberEntryField(
    entry: String,
    onAppend: (Int) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Text(
        entry.ifEmpty { "—" },
        style = type.display.copy(fontSize = 40.sp, letterSpacing = 5.sp),
        color = colors.textPrimary,
        modifier = Modifier.fillMaxWidth().padding(vertical = MrsadTokens.Spacing.xs),
        textAlign = TextAlign.Center,
    )
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { digit ->
                    NumpadKey(
                        label = dev.mrsad.core.text.PersianText.toPersianDigits(digit.toString()),
                        modifier = Modifier.weight(1f),
                        onClick = { onAppend(digit) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumpadKey(label = "حذف", modifier = Modifier.weight(1f), onClick = onDelete)
            NumpadKey(label = "۰", modifier = Modifier.weight(1f), onClick = { onAppend(0) })
            NumpadKey(label = "ثبت", modifier = Modifier.weight(1f), highlight = true, onClick = onSubmit)
        }
    }
}

@Composable
private fun NumpadKey(label: String, modifier: Modifier = Modifier, highlight: Boolean = false, onClick: () -> Unit) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
            .background(
                if (highlight) {
                    ObsidianSpectrum.Magenta.copy(alpha = 0.35f)
                } else {
                    colors.surfaceHigh.copy(alpha = 0.7f)
                },
            )
            .border(
                1.dp,
                if (highlight) ObsidianSpectrum.Pink else colors.border,
                RoundedCornerShape(MrsadTokens.Radius.sm),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = type.body.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
    }
}

@Composable
private fun PatternMemoryStage(state: MindUiState, viewModel: MindViewModel, settings: MrsadSettings) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val grid = state.patternGrid ?: return
    LaunchedEffect(state.rememberTimer) {
        if (state.rememberTimer == 0L) return@LaunchedEffect
        delay(grid.showMillis.toLong())
        viewModel.beginPatternRecall()
    }
    MrsadSurface(
        Modifier.fillMaxWidth(),
        elevated = true,
        accent = ObsidianSpectrum.Pink,
    ) {
        Text(
            if (state.showingStimulus) "خانه‌های روشن را به خاطر بسپارید" else "خانه‌های روشن را انتخاب کنید",
            style = type.body,
            color = colors.textSecondary,
        )
        val cells = (0 until grid.side * grid.side).toList()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = MrsadTokens.Spacing.xs)) {
            cells.chunked(grid.side).forEach { rowCells ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowCells.forEach { index ->
                        val lit = if (state.showingStimulus) index in grid.litCells else index in state.selectedCells
                        val correct = state.showingStimulus || index in grid.litCells
                        val tint = when {
                            !lit -> colors.surfaceHigh.copy(alpha = 0.6f)
                            state.showingStimulus -> ObsidianSpectrum.Pink
                            correct -> ObsidianSpectrum.Aqua
                            else -> ObsidianSpectrum.Magenta
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                                .background(tint)
                                .border(1.dp, colors.border, RoundedCornerShape(MrsadTokens.Radius.sm))
                                .then(if (!state.showingStimulus) Modifier.clickable { viewModel.toggleCell(index, settings.hapticsEnabled) } else Modifier),
                        )
                    }
                }
            }
        }
        if (!state.showingStimulus) {
            Spacer(Modifier.height(MrsadTokens.Spacing.sm))
            MrsadPrimaryButton(
                text = "ثبت پاسخ",
                onClick = { viewModel.submitPatternMemory() },
                accentColor = ObsidianSpectrum.Pink,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AttentionStage(state: MindUiState, viewModel: MindViewModel, settings: MrsadSettings) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val grid = state.attentionGrid ?: return
    MrsadSurface(
        Modifier.fillMaxWidth(),
        elevated = true,
        accent = ObsidianSpectrum.Pink,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "همهٔ «${grid.targetSymbol}» را انتخاب کنید",
                style = type.body,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            MrsadChip(
                text = "${formatInt(state.selectedCells.size, settings.persianNumerals)} / ${formatInt(grid.targetIndices.size, settings.persianNumerals)}",
                tint = ObsidianSpectrum.Pink,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = MrsadTokens.Spacing.xs)) {
            (0 until grid.side * grid.side).toList().chunked(grid.side).forEach { rowCells ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowCells.forEach { index ->
                        val selected = index in state.selectedCells
                        val isTarget = index in grid.targetIndices
                        val tint = when {
                            !selected -> colors.surfaceHigh.copy(alpha = 0.55f)
                            isTarget -> ObsidianSpectrum.Aqua.copy(alpha = 0.55f)
                            else -> ObsidianSpectrum.Magenta.copy(alpha = 0.55f)
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                                .background(tint)
                                .border(1.dp, if (selected) ObsidianSpectrum.Pink else colors.border, RoundedCornerShape(MrsadTokens.Radius.sm))
                                .clickable { viewModel.toggleCell(index, settings.hapticsEnabled) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(grid.symbols[index], style = type.title, color = colors.textPrimary)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(MrsadTokens.Spacing.sm))
        MrsadPrimaryButton(
            text = "ثبت پاسخ",
            enabled = state.selectedCells.isNotEmpty(),
            onClick = { viewModel.submitAttention() },
            accentColor = ObsidianSpectrum.Pink,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StroopStage(state: MindUiState, viewModel: MindViewModel, settings: MrsadSettings) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val item = state.stroopItem ?: return
    val inkColor = stroopColor(item.inkColorIndex)
    MrsadSurface(
        Modifier.fillMaxWidth(),
        elevated = true,
        accent = ObsidianSpectrum.Pink,
    ) {
        Text("رنگِ جوهرِ کلمه را انتخاب کنید (نه معنی کلمه)", style = type.caption, color = colors.textMuted)
        Text(
            item.promptFa,
            style = type.display.copy(fontSize = 58.sp),
            color = inkColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = MrsadTokens.Spacing.lg),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item.colors.withIndex().chunked(3).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { (index, label) ->
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                                .background(stroopColor(index).copy(alpha = 0.18f))
                                .border(1.dp, stroopColor(index), RoundedCornerShape(MrsadTokens.Radius.sm))
                                .clickable { viewModel.answerStroop(index, settings.hapticsEnabled) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, style = type.body, color = colors.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

private fun stroopColor(index: Int): Color = when (index) {
    0 -> ObsidianSpectrum.Aqua
    1 -> ObsidianSpectrum.BrightBlue
    2 -> ObsidianSpectrum.Pink
    3 -> Color(0xFFE8C05D)
    4 -> ObsidianSpectrum.Lavender
    else -> Color(0xFFE89A5D)
}

@Composable
private fun ArithmeticStage(state: MindUiState, viewModel: MindViewModel, settings: MrsadSettings) {
    val type = MrsadTheme.type
    val colors = MrsadTheme.colors
    val item = state.arithmeticItem ?: return
    MrsadSurface(
        Modifier.fillMaxWidth(),
        elevated = true,
        accent = ObsidianSpectrum.Pink,
    ) {
        Text("پاسخ درست را انتخاب کنید", style = type.caption, color = colors.textMuted)
        Text(
            item.promptFa,
            style = type.display.copy(fontSize = 44.sp),
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = MrsadTokens.Spacing.md),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item.options.forEachIndexed { index, value ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                        .background(colors.surfaceHigh.copy(alpha = 0.7f))
                        .border(1.dp, ObsidianSpectrum.Pink.copy(alpha = 0.45f), RoundedCornerShape(MrsadTokens.Radius.sm))
                        .clickable { viewModel.answerArithmetic(index, settings.hapticsEnabled) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        dev.mrsad.core.text.PersianText.toPersianDigits(value.toString()),
                        style = type.title,
                        color = colors.textPrimary,
                    )
                }
            }
        }
        Text(
            "پرسش ${formatInt(state.questionIndex + 1, settings.persianNumerals)} از ${formatInt(state.questionCount, settings.persianNumerals)}",
            style = type.caption,
            color = colors.textMuted,
            modifier = Modifier.padding(top = MrsadTokens.Spacing.xs),
        )
    }
}

@Composable
private fun LogicStage(state: MindUiState, viewModel: MindViewModel, settings: MrsadSettings) {
    val type = MrsadTheme.type
    val colors = MrsadTheme.colors
    val sequence = state.logicSequence ?: return
    MrsadSurface(
        Modifier.fillMaxWidth(),
        elevated = true,
        accent = ObsidianSpectrum.Pink,
    ) {
        Text(sequence.hintFa, style = type.caption, color = colors.textMuted)
        Text(
            sequence.shownFa,
            style = type.headline.copy(fontSize = 26.sp),
            color = colors.textPrimary,
            modifier = Modifier.fillMaxWidth().padding(vertical = MrsadTokens.Spacing.md),
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sequence.options.forEachIndexed { index, value ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                        .background(colors.surfaceHigh.copy(alpha = 0.7f))
                        .border(1.dp, ObsidianSpectrum.Pink.copy(alpha = 0.45f), RoundedCornerShape(MrsadTokens.Radius.sm))
                        .clickable { viewModel.answerLogic(index, settings.hapticsEnabled) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        dev.mrsad.core.text.PersianText.toPersianDigits(value.toString()),
                        style = type.title,
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MindResult(state: MindUiState, viewModel: MindViewModel, settings: MrsadSettings) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MrsadTokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(MrsadTokens.Spacing.lg))
        Text(state.exercise?.titleFa ?: "", style = type.headline, color = colors.textPrimary)
        Text(
            if (state.saved) "نتیجه ذخیره شد" else "در حال ذخیره…",
            style = type.caption,
            color = if (state.saved) ObsidianSpectrum.Aqua else colors.textMuted,
        )
        MrsadSurface(
            Modifier.fillMaxWidth(),
            elevated = true,
            accent = ObsidianSpectrum.Pink,
        ) {
            MetricValue(
                value = formatPercent(state.lastScore * 100.0, settings.persianNumerals),
                label = "امتیاز این جلسه",
                size = type.display.fontSize,
                accent = if (state.lastScore >= 0.5) ObsidianSpectrum.Aqua else ObsidianSpectrum.Pink,
            )
            MrsadDivider()
            Text(state.resultDetailFa, style = type.body, color = colors.textSecondary)
            if (state.levelAdjusted) {
                MrsadChip(
                    text = if (state.levelUp) "سطح تمرین افزایش یافت" else "سطح تمرین کاهش یافت",
                    tint = if (state.levelUp) ObsidianSpectrum.Aqua else ObsidianSpectrum.Pink,
                )
            }
            Text(
                "سطح بعدی: ${formatInt(state.nextLevel + 1, settings.persianNumerals)}",
                style = type.caption,
                color = colors.textMuted,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
            MrsadPrimaryButton(
                text = "تمرین دوباره",
                icon = Icons.Outlined.Refresh,
                onClick = { viewModel.restartExercise() },
                accentColor = ObsidianSpectrum.Pink,
                modifier = Modifier.weight(1f),
            )
            MrsadSecondaryButton(
                text = "فهرست تمرین‌ها",
                onClick = { viewModel.backToLobby() },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(MrsadTokens.Spacing.lg))
    }
}

@Composable
private fun ReactionStage(state: MindUiState, viewModel: MindViewModel, settings: MrsadSettings) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val trial = state.reactionTrial
    val waitMillis = trial?.waitMillis ?: 1200L
    LaunchedEffect(state.rememberTimer, state.questionIndex) {
        if (state.reactionSignalArmed) return@LaunchedEffect
        delay(waitMillis)
        viewModel.armReactionSignal()
    }
    MrsadSurface(
        Modifier.fillMaxWidth(),
        elevated = true,
        accent = ObsidianSpectrum.Pink,
    ) {
        Text(
            "مرحله ${formatInt(state.questionIndex + 1, settings.persianNumerals)} از ${formatInt(state.questionCount, settings.persianNumerals)}",
            style = type.caption,
            color = colors.textMuted,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(MrsadTokens.Radius.lg))
                .background(
                    if (state.reactionSignalArmed) {
                        ObsidianSpectrum.Aqua.copy(alpha = 0.25f)
                    } else {
                        colors.surfaceHigh
                    },
                )
                .border(
                    width = 1.dp,
                    color = if (state.reactionSignalArmed) ObsidianSpectrum.Aqua else ObsidianSpectrum.Pink,
                    shape = RoundedCornerShape(MrsadTokens.Radius.lg),
                )
                .clickable { viewModel.tapReaction() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MetalIcon(
                    facets = if (state.reactionSignalArmed) MrsadIcons.Bolt else MrsadIcons.Target,
                    accentOverride = if (state.reactionSignalArmed) ObsidianSpectrum.Aqua else ObsidianSpectrum.Pink,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.height(MrsadTokens.Spacing.xs))
                Text(
                    if (state.reactionSignalArmed) "حالا ضربه بزنید!" else "منتظر سیگنال بمانید…",
                    style = type.headline,
                    color = if (state.reactionSignalArmed) ObsidianSpectrum.Aqua else colors.textSecondary,
                )
            }
        }
    }
}

private val MindExercise.facets: List<IconFacet>
    get() = when (this) {
        MindExercise.NUMBER_MEMORY -> MrsadIcons.Memory
        MindExercise.PATTERN_MEMORY -> MrsadIcons.Mind
        MindExercise.ATTENTION -> MrsadIcons.Target
        MindExercise.STROOP -> MrsadIcons.Eye
        MindExercise.ARITHMETIC -> MrsadIcons.Spark
        MindExercise.LOGIC -> MrsadIcons.Trophy
        MindExercise.REACTION -> MrsadIcons.Bolt
    }

private val MindExercise.instructionFa: String
    get() = when (this) {
        MindExercise.NUMBER_MEMORY -> "اعداد را ببینید، سپس آن‌ها را وارد کنید"
        MindExercise.PATTERN_MEMORY -> "خانه‌های روشن را در ذهن نگه دارید"
        MindExercise.ATTENTION -> "همهٔ نماد هدف را پیدا و انتخاب کنید"
        MindExercise.STROOP -> "رنگ جوهر را نه معنی کلمه را انتخاب کنید"
        MindExercise.ARITHMETIC -> "در کمترین زمان پاسخ درست را بزنید"
        MindExercise.LOGIC -> "الگوی عددی را کامل کنید"
        MindExercise.REACTION -> "به‌محض روشن شدن محرک واکنش نشان دهید"
    }
