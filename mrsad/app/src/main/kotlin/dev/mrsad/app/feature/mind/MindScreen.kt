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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
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
import dev.mrsad.app.ui.components.ObsidianIconBox
import dev.mrsad.app.ui.components.ObsidianSegmentedBar
import dev.mrsad.app.ui.components.SectionHeader
import dev.mrsad.app.ui.components.SegmentedBarItem
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.ObsidianSpectrum
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.formatInt
import dev.mrsad.app.ui.theme.formatNumber
import dev.mrsad.app.ui.theme.formatPercent
import kotlinx.coroutines.delay

/**
 * MODULE 04 — MRSAD BRAIN / MIND TRAINING (Master Prompt §4):
 * Primary Identity: Neural Magenta (`#D81E78`) & Luminous Pink (`#FF6BB5`).
 */
@Composable
fun MindScreen(
    container: AppContainer,
    settings: MrsadSettings,
) {
    val viewModel: MindViewModel = viewModel(key = "mind", factory = MindViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    AmbientBackground(PageEnvironment.MIND) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = LocalBottomBarClearance.current),
        ) {
            when (state.screen) {
                MindScreenState.LOBBY -> MindLobby(state, viewModel, settings)
                MindScreenState.PLAYING -> MindPlayer(state, viewModel, settings)
                MindScreenState.RESULT -> MindResult(state, viewModel, settings)
            }
        }
    }
}

@Composable
private fun MindLobby(
    state: MindUiState,
    viewModel: MindViewModel,
    settings: MrsadSettings,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val scrollState = rememberScrollState()
    val isCollapsed by remember { derivedStateOf { scrollState.value > 28 } }

    val filteredExercises = remember(state.selectedCategoryFilter) {
        when (state.selectedCategoryFilter) {
            "حافظه" -> MindExercise.entries.filter { it.categoryFa == "حافظه" }
            "توجه" -> MindExercise.entries.filter { it.categoryFa == "توجه" }
            "منطق" -> MindExercise.entries.filter { it.categoryFa == "پردازش" || it.categoryFa == "منطق" }
            "واکنش" -> MindExercise.entries.filter { it.categoryFa == "واکنش" }
            else -> MindExercise.entries
        }
    }

    Column(Modifier.fillMaxSize()) {
        ObsidianCollapsingTopBar(
            title = "تمرین ذهن و توان شناختی",
            subtitle = "حافظهٔ کاری، توجه انتخابی، منطق و سرعت واکنش عصبی",
            collapsed = isCollapsed,
            spectralColor = ObsidianSpectrum.Pink,
            leading = {
                ObsidianIconBox(
                    size = if (isCollapsed) 38.dp else 48.dp,
                    accent = ObsidianSpectrum.Pink,
                    elevated = true,
                ) {
                    ModuleSculpture3D(
                        type = ModuleSculptureType.BRAIN,
                        modifier = Modifier.size(if (isCollapsed) 28.dp else 36.dp),
                        animated = true,
                    )
                }
            },
            trailing = {
                MrsadChip(
                    text = if (state.practisedExercises == 0) {
                        "۷ تمرین علمی"
                    } else {
                        "${formatInt(state.practisedExercises, settings.persianNumerals)} مهارت فعال"
                    },
                    tint = ObsidianSpectrum.Pink,
                    compact = true,
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

            // Telemetry Summary Card
            MrsadSurface(
                Modifier.fillMaxWidth(),
                accent = ObsidianSpectrum.Pink,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    MetricValue(
                        value = formatInt(state.totalSessions, settings.persianNumerals),
                        label = "کل جلسات ذهن",
                        size = type.headline.fontSize,
                        accent = ObsidianSpectrum.Pink,
                    )
                    MetricValue(
                        value = if (state.totalSessions == 0) "—" else formatPercent(state.averageScore * 100, settings.persianNumerals),
                        label = "میانگین دقت شناختی",
                        size = type.headline.fontSize,
                        accent = ObsidianSpectrum.Aqua,
                    )
                    MetricValue(
                        value = formatInt(state.practisedExercises, settings.persianNumerals),
                        label = "مهارت‌های تمرین‌شده",
                        size = type.headline.fontSize,
                        accent = ObsidianSpectrum.Lavender,
                    )
                }
            }

            // Cognitive Domain Filter Bar
            MrsadSurface(
                Modifier.fillMaxWidth(),
                accent = ObsidianSpectrum.Magenta,
            ) {
                SectionHeader(title = "حوزهٔ شناختی و فهرست تمرین‌ها")
                ObsidianSegmentedBar(
                    items = listOf(
                        SegmentedBarItem("ALL", "همه (۷)", ObsidianSpectrum.Pink),
                        SegmentedBarItem("حافظه", "حافظه", ObsidianSpectrum.Pink),
                        SegmentedBarItem("توجه", "توجه", ObsidianSpectrum.Lavender),
                        SegmentedBarItem("منطق", "منطق و حساب", ObsidianSpectrum.BrightBlue),
                        SegmentedBarItem("واکنش", "واکنش", ObsidianSpectrum.Aqua),
                    ),
                    selectedId = state.selectedCategoryFilter,
                    onSelect = viewModel::setCategoryFilter,
                )

                Spacer(Modifier.height(MrsadTokens.Spacing.xxs))

                // Strict RTL Exercise Rows (48dp Icon Box on Right, Title/Subtitle in Center, Level Badge on Left)
                filteredExercises.forEach { ex ->
                    val best = state.bestScores[ex]
                    val level = state.levelOf(ex)
                    ExerciseRowCard(
                        exercise = ex,
                        level = level,
                        bestLabel = best?.let { "رکورد: ${formatPercent(it * 100, settings.persianNumerals)}" },
                        persianNumerals = settings.persianNumerals,
                        onClick = { viewModel.openExercise(ex) },
                    )
                }
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun ExerciseRowCard(
    exercise: MindExercise,
    level: Int,
    bestLabel: String?,
    persianNumerals: Boolean,
    onClick: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val shape = RoundedCornerShape(MrsadTokens.Radius.md)
    val domainAccent = when (exercise.categoryFa) {
        "حافظه" -> ObsidianSpectrum.Pink
        "توجه" -> ObsidianSpectrum.Lavender
        "پردازش", "منطق" -> ObsidianSpectrum.BrightBlue
        else -> ObsidianSpectrum.Aqua
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.backgroundElevated.copy(alpha = 0.72f))
            .border(1.dp, domainAccent.copy(alpha = 0.42f), shape)
            .clickable(onClick = onClick)
            .padding(MrsadTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Strict RTL Right Anchor: Fixed 48dp Icon Box
        ObsidianIconBox(
            size = 48.dp,
            accent = domainAccent,
            elevated = true,
        ) {
            MetalIcon(
                facets = exercise.facets,
                accentOverride = domainAccent,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(MrsadTokens.Spacing.xs))

        // Middle: Title, Subtitle & Personal Best
        Column(Modifier.weight(1f)) {
            Text(
                text = exercise.titleFa,
                style = type.body.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = exercise.subtitleFa,
                style = type.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${exercise.categoryFa} • ${bestLabel ?: "آمادهٔ اولین جلسه"}",
                style = type.caption,
                color = if (bestLabel != null) ObsidianSpectrum.Aqua else colors.textMuted,
                maxLines = 1,
            )
        }

        Spacer(Modifier.width(MrsadTokens.Spacing.xs))

        // Left Anchor: Adaptive Level Badge
        MrsadChip(
            text = "سطح ${formatInt(level + 1, persianNumerals)}",
            tint = domainAccent,
            compact = true,
        )
    }
}

@Composable
private fun MindPlayer(state: MindUiState, viewModel: MindViewModel, settings: MrsadSettings) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val scrollState = rememberScrollState()
    val isCollapsed by remember { derivedStateOf { scrollState.value > 28 } }

    Column(Modifier.fillMaxSize()) {
        ObsidianCollapsingTopBar(
            title = state.exercise?.titleFa ?: "تمرین شناختی",
            subtitle = state.exercise?.instructionFa ?: "",
            collapsed = isCollapsed,
            spectralColor = ObsidianSpectrum.Pink,
            onBack = { viewModel.abort() },
            leading = {
                ObsidianIconBox(
                    size = if (isCollapsed) 38.dp else 44.dp,
                    accent = ObsidianSpectrum.Pink,
                ) {
                    MetalIcon(
                        facets = state.exercise?.facets ?: MrsadIcons.Mind,
                        accentOverride = ObsidianSpectrum.Pink,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            trailing = {
                MrsadChip(
                    text = "سطح ${formatInt(state.level + 1, settings.persianNumerals)}",
                    tint = ObsidianSpectrum.Pink,
                    compact = true,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "پیشرفت جلسه",
                    style = type.caption,
                    color = colors.textSecondary,
                )
                Text(
                    "مرحله ${formatInt(state.questionIndex + 1, settings.persianNumerals)} از ${formatInt(state.questionCount, settings.persianNumerals)}",
                    style = type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = ObsidianSpectrum.Pink,
                )
            }
            MrsadProgressBar(
                progress = state.progress,
                height = 6.dp,
                tint = ObsidianSpectrum.Pink,
            )

            state.lastStageFeedbackFa?.let { feedback ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                        .background(ObsidianSpectrum.Pink.copy(alpha = 0.14f))
                        .border(1.dp, ObsidianSpectrum.Pink.copy(alpha = 0.45f), RoundedCornerShape(MrsadTokens.Radius.sm))
                        .padding(horizontal = MrsadTokens.Spacing.sm, vertical = 8.dp),
                ) {
                    Text(
                        text = feedback,
                        style = type.caption.copy(fontWeight = FontWeight.Medium),
                        color = colors.textPrimary,
                    )
                }
            }

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
    val stimulusDurationMs = (1800L + state.digits.length * 220L).coerceAtMost(4200L)

    LaunchedEffect(state.rememberTimer) {
        if (state.rememberTimer == 0L) return@LaunchedEffect
        delay(stimulusDurationMs)
        viewModel.revealDigits()
    }

    MrsadSurface(
        Modifier.fillMaxWidth(),
        elevated = true,
        accent = ObsidianSpectrum.Pink,
    ) {
        if (state.showingStimulus) {
            Text(
                "توالی ارقام زیر را از چپ به راست به خاطر بسپارید",
                style = type.caption,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    formatNumber(state.digits, settings.persianNumerals),
                    style = type.display.copy(fontSize = 48.sp, letterSpacing = 6.sp, fontWeight = FontWeight.Bold),
                    color = ObsidianSpectrum.Pink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = MrsadTokens.Spacing.md),
                )
            }
            Text(
                "طول توالی: ${formatInt(state.digits.length, settings.persianNumerals)} رقم",
                style = type.caption,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                "توالی ${formatInt(state.digits.length, settings.persianNumerals)} رقمی را به همان ترتیب (از چپ به راست) وارد کنید",
                style = type.caption,
                color = colors.textSecondary,
            )
            NumberEntryField(
                entry = state.entry,
                targetLength = state.digits.length,
                persianNumerals = settings.persianNumerals,
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
    targetLength: Int,
    persianNumerals: Boolean,
    onAppend: (Int) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MrsadTokens.Radius.md))
                .background(colors.background.copy(alpha = 0.72f))
                .border(1.dp, ObsidianSpectrum.Pink.copy(alpha = 0.45f), RoundedCornerShape(MrsadTokens.Radius.md))
                .padding(vertical = 12.dp, horizontal = MrsadTokens.Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (entry.isEmpty()) "—" else formatNumber(entry, persianNumerals),
                style = type.display.copy(fontSize = 38.sp, letterSpacing = 5.sp, fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
        }

        val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = MrsadTokens.Spacing.xs),
        ) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { digit ->
                        NumpadKey(
                            label = formatInt(digit, persianNumerals),
                            modifier = Modifier.weight(1f),
                            onClick = { onAppend(digit) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumpadKey(label = "حذف", modifier = Modifier.weight(1f), onClick = onDelete)
                NumpadKey(label = formatInt(0, persianNumerals), modifier = Modifier.weight(1f), onClick = { onAppend(0) })
                NumpadKey(
                    label = if (entry.length >= targetLength) "ثبت مرحله" else "ثبت",
                    modifier = Modifier.weight(1f),
                    highlight = true,
                    onClick = onSubmit,
                )
            }
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
                    ObsidianSpectrum.Magenta.copy(alpha = 0.38f)
                } else {
                    colors.surfaceHigh.copy(alpha = 0.72f)
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
        Text(label, style = type.body.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.showingStimulus) "الگوی خانه‌های روشن را به خاطر بسپارید" else "خانه‌های روشن را انتخاب کنید",
                style = type.body.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (!state.showingStimulus) {
                MrsadChip(
                    text = "${formatInt(state.selectedCells.size, settings.persianNumerals)} / ${formatInt(grid.litCells.size, settings.persianNumerals)}",
                    tint = ObsidianSpectrum.Pink,
                    compact = true,
                )
            }
        }
        val cells = (0 until grid.side * grid.side).toList()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = MrsadTokens.Spacing.xs)) {
            cells.chunked(grid.side).forEach { rowCells ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowCells.forEach { index ->
                        val lit = if (state.showingStimulus) index in grid.litCells else index in state.selectedCells
                        val tint = when {
                            !lit -> colors.surfaceHigh.copy(alpha = 0.6f)
                            state.showingStimulus -> ObsidianSpectrum.Pink
                            else -> ObsidianSpectrum.BrightBlue.copy(alpha = 0.55f)
                        }
                        val borderTint = when {
                            !lit -> colors.border
                            state.showingStimulus -> ObsidianSpectrum.SoftWhite
                            else -> ObsidianSpectrum.BrightBlue
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                                .background(tint)
                                .border(1.dp, borderTint, RoundedCornerShape(MrsadTokens.Radius.sm))
                                .then(if (!state.showingStimulus) Modifier.clickable { viewModel.toggleCell(index, settings.hapticsEnabled) } else Modifier),
                        )
                    }
                }
            }
        }
        if (!state.showingStimulus) {
            Spacer(Modifier.height(MrsadTokens.Spacing.sm))
            MrsadPrimaryButton(
                text = "ثبت الگوی انتخابی",
                enabled = state.selectedCells.isNotEmpty(),
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
                "همهٔ خانه‌های دارای نماد «${grid.targetSymbol}» را انتخاب کنید",
                style = type.body.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            MrsadChip(
                text = "${formatInt(state.selectedCells.size, settings.persianNumerals)} / ${formatInt(grid.targetIndices.size, settings.persianNumerals)}",
                tint = ObsidianSpectrum.Pink,
                compact = true,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = MrsadTokens.Spacing.xs)) {
            (0 until grid.side * grid.side).toList().chunked(grid.side).forEach { rowCells ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowCells.forEach { index ->
                        val selected = index in state.selectedCells
                        val tint = if (selected) {
                            ObsidianSpectrum.BrightBlue.copy(alpha = 0.45f)
                        } else {
                            colors.surfaceHigh.copy(alpha = 0.55f)
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                                .background(tint)
                                .border(1.dp, if (selected) ObsidianSpectrum.BrightBlue else colors.border, RoundedCornerShape(MrsadTokens.Radius.sm))
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
            text = "ثبت مرحله",
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
        Text("رنگِ جوهرِ نوشته را انتخاب کنید (نه معنی کلمه)", style = type.caption, color = colors.textSecondary)
        Text(
            item.promptFa,
            style = type.display.copy(fontSize = 54.sp, fontWeight = FontWeight.Bold),
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
                            Text(label, style = type.body.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
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
        Text("پاسخ درست عبارت ریاضی را در کمترین زمان انتخاب کنید", style = type.caption, color = colors.textSecondary)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                item.promptFa,
                style = type.display.copy(fontSize = 44.sp, fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = MrsadTokens.Spacing.md),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item.options.forEachIndexed { index, value ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                        .background(colors.surfaceHigh.copy(alpha = 0.72f))
                        .border(1.dp, ObsidianSpectrum.Pink.copy(alpha = 0.45f), RoundedCornerShape(MrsadTokens.Radius.sm))
                        .clickable { viewModel.answerArithmetic(index, settings.hapticsEnabled) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        formatInt(value, settings.persianNumerals),
                        style = type.title.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                    )
                }
            }
        }
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
        Text(sequence.hintFa, style = type.caption, color = ObsidianSpectrum.Lavender)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                sequence.shownFa,
                style = type.headline.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                modifier = Modifier.fillMaxWidth().padding(vertical = MrsadTokens.Spacing.md),
                textAlign = TextAlign.Center,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sequence.options.forEachIndexed { index, value ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                        .background(colors.surfaceHigh.copy(alpha = 0.72f))
                        .border(1.dp, ObsidianSpectrum.Pink.copy(alpha = 0.45f), RoundedCornerShape(MrsadTokens.Radius.sm))
                        .clickable { viewModel.answerLogic(index, settings.hapticsEnabled) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        formatInt(value, settings.persianNumerals),
                        style = type.title.copy(fontWeight = FontWeight.Bold),
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
        Spacer(Modifier.height(MrsadTokens.Spacing.md))
        MrsadSurface(
            Modifier.fillMaxWidth(),
            elevated = true,
            accent = ObsidianSpectrum.Pink,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ObsidianIconBox(size = 48.dp, accent = ObsidianSpectrum.Pink, elevated = true) {
                        MetalIcon(MrsadIcons.Trophy, accentOverride = ObsidianSpectrum.Pink, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                    Column {
                        Text(state.exercise?.titleFa ?: "نتیجهٔ تمرین شناختی", style = type.title.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                        Text(
                            if (state.saved) "نتیجه در آمار ثبت شد" else "در حال ذخیره…",
                            style = type.caption,
                            color = if (state.saved) ObsidianSpectrum.Aqua else colors.textMuted,
                        )
                    }
                }
                MrsadChip(
                    text = "سطح بعدی: ${formatInt(state.nextLevel + 1, settings.persianNumerals)}",
                    tint = ObsidianSpectrum.Pink,
                    compact = true,
                )
            }

            MetricValue(
                value = formatPercent((state.lastScore * 100.0).coerceIn(0.0, 100.0), settings.persianNumerals),
                label = "امتیاز کل این جلسه",
                size = type.display.fontSize,
                accent = if (state.lastScore >= 0.6) ObsidianSpectrum.Aqua else ObsidianSpectrum.Pink,
                modifier = Modifier.fillMaxWidth().padding(vertical = MrsadTokens.Spacing.xs),
            )
            MrsadDivider()
            Text(state.resultDetailFa, style = type.body, color = colors.textSecondary)
            if (state.levelAdjusted) {
                MrsadChip(
                    text = if (state.levelUp) "ارتقای سطح دشواری در نوبت بعد" else "تنظیم سطح دشواری برای تثبیت مهارت",
                    tint = if (state.levelUp) ObsidianSpectrum.Aqua else ObsidianSpectrum.Pink,
                    compact = true,
                )
            }
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
            "محض تغییر رنگ کادر به فیروزه‌ای، در کوتاه‌ترین زمان ممکن ضربه بزنید (ضربهٔ زودهنگام خطا محسوب می‌شود)",
            style = type.caption,
            color = colors.textSecondary,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(MrsadTokens.Radius.lg))
                .background(
                    if (state.reactionSignalArmed) {
                        ObsidianSpectrum.Aqua.copy(alpha = 0.25f)
                    } else {
                        colors.surfaceHigh
                    },
                )
                .border(
                    width = 1.5.dp,
                    color = if (state.reactionSignalArmed) ObsidianSpectrum.Aqua else ObsidianSpectrum.Pink,
                    shape = RoundedCornerShape(MrsadTokens.Radius.lg),
                )
                .clickable { viewModel.tapReaction() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ObsidianIconBox(
                    size = 56.dp,
                    accent = if (state.reactionSignalArmed) ObsidianSpectrum.Aqua else ObsidianSpectrum.Pink,
                    elevated = true,
                ) {
                    MetalIcon(
                        facets = if (state.reactionSignalArmed) MrsadIcons.Bolt else MrsadIcons.Target,
                        accentOverride = if (state.reactionSignalArmed) ObsidianSpectrum.Aqua else ObsidianSpectrum.Pink,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.height(MrsadTokens.Spacing.xs))
                Text(
                    if (state.reactionSignalArmed) "حالا ضربه بزنید!" else "منتظر سیگنال بمانید…",
                    style = type.headline.copy(fontWeight = FontWeight.Bold),
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
        MindExercise.NUMBER_MEMORY -> "توالی ارقام را در ۳ مرحله به خاطر بسپارید و بازنویسی کنید"
        MindExercise.PATTERN_MEMORY -> "خانه‌های روشن را در ۳ مرحلهٔ پیاپی بازسازی کنید"
        MindExercise.ATTENTION -> "همهٔ نمادهای هدف را در ۳ شبکهٔ متوالی پیدا کنید"
        MindExercise.STROOP -> "رنگ جوهر را مستقل از معنی واژه انتخاب کنید"
        MindExercise.ARITHMETIC -> "عبارت‌های ریاضی را با سرعت و دقت حل کنید"
        MindExercise.LOGIC -> "قاعدهٔ ریاضی دنباله را کشف و عدد بعدی را مشخص کنید"
        MindExercise.REACTION -> "به‌محض ظهور محرک فیروزه‌ای واکنش نشان دهید"
    }
