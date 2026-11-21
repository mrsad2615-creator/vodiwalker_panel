package dev.mrsad.app.feature.eye

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.settings.MrsadSettings
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
import dev.mrsad.app.ui.components.ObsidianSegmentedBar
import dev.mrsad.app.ui.components.SectionHeader
import dev.mrsad.app.ui.components.SegmentedBarItem
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.ObsidianSpectrum
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.formatInt
import dev.mrsad.core.eye.EyeTrainingEngine
import dev.mrsad.core.eye.EyeTrainingEngine.DrillType

/**
 * MODULE 03 — MRSAD EYE TRAINING (Master Prompt §4):
 * Primary Identity: Turquoise / Teal (`#138F91`) & Aqua (`#67E7DA`).
 */
@Composable
fun EyeTrainingScreen(
    container: AppContainer,
    settings: MrsadSettings,
    onBack: (() -> Unit)? = null,
) {
    val viewModel: EyeTrainingViewModel = viewModel(
        key = "eye",
        factory = EyeTrainingViewModel.Factory(container),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val isCollapsed by remember { derivedStateOf { scrollState.value > 28 } }

    AmbientBackground(PageEnvironment.EYE) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = LocalBottomBarClearance.current),
        ) {
            ObsidianCollapsingTopBar(
                title = "MRSAD EYE",
                subtitle = "تمرین مهارت‌های دیداری، ساکاد و میدان توجه",
                collapsed = isCollapsed,
                spectralColor = ObsidianSpectrum.Aqua,
                onBack = onBack,
                leading = {
                    ModuleSculpture3D(
                        type = ModuleSculptureType.EYE,
                        modifier = Modifier.size(if (isCollapsed) 36.dp else 46.dp),
                        animated = true,
                    )
                },
                trailing = {
                    MrsadChip(
                        text = "${formatInt(state.totalEyeSessions, settings.persianNumerals)} جلسه",
                        tint = ObsidianSpectrum.Aqua,
                    )
                },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = MrsadTokens.Spacing.md),
                verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
            ) {
                Spacer(Modifier.height(MrsadTokens.Spacing.xs))

                when (state.phase) {
                    EyePhase.SETUP -> EyeSetupPanel(
                        state = state,
                        persianNumerals = settings.persianNumerals,
                        onSelectDrill = viewModel::selectDrill,
                        onSpeed = viewModel::setSpeed,
                        onDifficulty = viewModel::setDifficulty,
                        onDuration = viewModel::setDuration,
                        onStart = viewModel::startSession,
                    )
                    EyePhase.RUNNING -> EyeRunningPanel(
                        state = state,
                        persianNumerals = settings.persianNumerals,
                        onHit = viewModel::onTargetHit,
                        onSchulteTap = viewModel::onSchulteCellTap,
                        onFinishEarly = { viewModel.finishSession(completed = false) },
                    )
                    EyePhase.RESULT -> EyeResultPanel(
                        state = state,
                        persianNumerals = settings.persianNumerals,
                        onRestart = viewModel::startSession,
                        onBackToSetup = viewModel::resetToSetup,
                    )
                }

                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

@Composable
private fun EyeSetupPanel(
    state: EyeUiState,
    persianNumerals: Boolean,
    onSelectDrill: (DrillType) -> Unit,
    onSpeed: (Int) -> Unit,
    onDifficulty: (Int) -> Unit,
    onDuration: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type

    MrsadSurface(
        Modifier.fillMaxWidth(),
        accent = ObsidianSpectrum.Aqua,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
        ) {
            ModuleSculpture3D(
                type = ModuleSculptureType.EYE,
                modifier = Modifier.size(80.dp),
                animated = true,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "مرکز چابکی نگاه و میدان دید",
                    style = type.title,
                    color = colors.textPrimary,
                )
                Text(
                    state.selectedDrill.guideFa,
                    style = type.caption,
                    color = colors.textSecondary,
                )
            }
        }
        MrsadDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricValue(
                value = formatInt(state.totalEyeSessions, persianNumerals),
                label = "کل جلسات",
                size = type.title.fontSize,
                accent = ObsidianSpectrum.Aqua,
            )
            MetricValue(
                value = if (state.averageAgilityScore > 0) "${formatInt(state.averageAgilityScore, persianNumerals)}٪" else "—",
                label = "میانگین چابکی دید",
                size = type.title.fontSize,
                accent = ObsidianSpectrum.BrightBlue,
            )
            MetricValue(
                value = "سطح ${formatInt(state.difficultyLevel, persianNumerals)}",
                label = "درجهٔ دشواری",
                size = type.title.fontSize,
            )
        }
    }

    MrsadSurface(
        Modifier.fillMaxWidth(),
        accent = ObsidianSpectrum.Aqua,
    ) {
        SectionHeader(title = "انتخاب نوع تمرین دیداری")
        DrillType.entries.forEach { drill ->
            val selected = drill == state.selectedDrill
            val itemShape = RoundedCornerShape(MrsadTokens.Radius.md)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(itemShape)
                    .background(
                        if (selected) {
                            ObsidianSpectrum.Teal.copy(alpha = 0.25f)
                        } else {
                            colors.backgroundElevated.copy(alpha = 0.6f)
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) ObsidianSpectrum.Aqua else colors.border,
                        shape = itemShape,
                    )
                    .heightIn(min = MrsadTokens.TouchTarget)
                    .clickable(role = Role.RadioButton) { onSelectDrill(drill) }
                    .padding(horizontal = MrsadTokens.Spacing.sm, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetalIcon(
                    MrsadIcons.Eye,
                    accentOverride = ObsidianSpectrum.Aqua,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(MrsadTokens.Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        drill.titleFa,
                        style = type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                    Text(
                        drill.subtitleFa,
                        style = type.caption,
                        color = colors.textSecondary,
                    )
                }
                if (selected) {
                    MrsadChip(text = "فعال", tint = ObsidianSpectrum.Aqua)
                }
            }
        }
    }

    MrsadSurface(
        Modifier.fillMaxWidth(),
        accent = ObsidianSpectrum.Aqua,
    ) {
        SectionHeader(title = "کنترل سرعت، دشواری و زمان")

        Text("سرعت حرکت محرک (۱ تا ۵)", style = type.label, color = colors.textSecondary)
        ObsidianSegmentedBar(
            items = (1..5).map { lvl ->
                SegmentedBarItem(
                    id = lvl.toString(),
                    label = formatInt(lvl, persianNumerals),
                    accent = ObsidianSpectrum.Aqua,
                )
            },
            selectedId = state.speedLevel.toString(),
            onSelect = { id -> id.toIntOrNull()?.let(onSpeed) },
        )

        Spacer(Modifier.height(MrsadTokens.Spacing.xxs))
        Text("سطح دشواری و فشردگی میدان دید (۱ تا ۵)", style = type.label, color = colors.textSecondary)
        ObsidianSegmentedBar(
            items = (1..5).map { lvl ->
                SegmentedBarItem(
                    id = lvl.toString(),
                    label = formatInt(lvl, persianNumerals),
                    accent = ObsidianSpectrum.Aqua,
                )
            },
            selectedId = state.difficultyLevel.toString(),
            onSelect = { id -> id.toIntOrNull()?.let(onDifficulty) },
        )

        Spacer(Modifier.height(MrsadTokens.Spacing.xxs))
        Text("مدت جلسه", style = type.label, color = colors.textSecondary)
        ObsidianSegmentedBar(
            items = listOf(30, 45, 60, 90).map { sec ->
                SegmentedBarItem(
                    id = sec.toString(),
                    label = "${formatInt(sec, persianNumerals)} ثانیه",
                    accent = ObsidianSpectrum.Aqua,
                )
            },
            selectedId = state.durationSeconds.toString(),
            onSelect = { id -> id.toIntOrNull()?.let(onDuration) },
        )
    }

    MrsadSurface(Modifier.fillMaxWidth()) {
        Text("راهنمای علمی و دامنهٔ کاربرد", style = type.label, color = colors.textPrimary)
        Text(
            EyeTrainingEngine.DISCLAIMER_FA,
            style = type.caption,
            color = colors.textMuted,
        )
    }

    MrsadPrimaryButton(
        text = "شروع تمرین دیداری",
        onClick = onStart,
        accentColor = ObsidianSpectrum.Aqua,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EyeRunningPanel(
    state: EyeUiState,
    persianNumerals: Boolean,
    onHit: () -> Unit,
    onSchulteTap: (Int) -> Unit,
    onFinishEarly: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val secondsLeft = ((state.remainingMillis + 999L) / 1000L).toInt().coerceAtLeast(0)

    MrsadSurface(
        Modifier.fillMaxWidth(),
        accent = ObsidianSpectrum.Aqua,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetricValue(
                value = "${formatInt(secondsLeft, persianNumerals)}s",
                label = "زمان باقی‌مانده",
                size = type.title.fontSize,
                accent = ObsidianSpectrum.Aqua,
            )
            MetricValue(
                value = formatInt(state.hits, persianNumerals),
                label = "ثبت دقیق",
                size = type.title.fontSize,
                accent = ObsidianSpectrum.BrightBlue,
            )
            MetricValue(
                value = "سطح ${formatInt(state.difficultyLevel, persianNumerals)}",
                label = state.selectedDrill.titleFa.substringBefore(" ("),
                size = type.label.fontSize,
            )
        }
        MrsadProgressBar(progress = state.progress, tint = ObsidianSpectrum.Aqua)
        Text(
            state.selectedDrill.guideFa,
            style = type.caption,
            color = colors.textSecondary,
        )
    }

    if (state.selectedDrill == DrillType.PERIPHERAL_SPAN) {
        MrsadSurface(
            Modifier.fillMaxWidth(),
            accent = ObsidianSpectrum.Aqua,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("کانون دید را روی مرکز نگه دارید", style = type.caption, color = colors.textMuted)
                MrsadChip(
                    text = "عدد بعدی: ${formatInt(state.nextSchulteNumber, persianNumerals)}",
                    tint = ObsidianSpectrum.Aqua,
                )
            }
            val side = state.schulteBoard.side
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.schulteBoard.numbers.chunked(side).forEach { rowNums ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowNums.forEach { num ->
                            val done = num < state.nextSchulteNumber
                            val cellShape = RoundedCornerShape(MrsadTokens.Radius.sm)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.15f)
                                    .clip(cellShape)
                                    .background(
                                        if (done) {
                                            ObsidianSpectrum.Teal.copy(alpha = 0.25f)
                                        } else {
                                            colors.surfaceHigh
                                        },
                                    )
                                    .border(
                                        1.dp,
                                        if (done) ObsidianSpectrum.Aqua else colors.border,
                                        cellShape,
                                    )
                                    .clickable(role = Role.Button) { onSchulteTap(num) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    formatInt(num, persianNumerals),
                                    style = type.title,
                                    color = if (done) ObsidianSpectrum.Aqua else colors.textPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        MrsadSurface(
            Modifier.fillMaxWidth(),
            accent = ObsidianSpectrum.Aqua,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .semantics {
                        contentDescription = "میدان تمرین دیداری چشم — روی هدف فعال ضربه بزنید"
                    }
                    .pointerInput(state.selectedDrill) {
                        detectTapGestures { onHit() }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f

                    // Precision circular optical rings & horizon crosshairs
                    drawCircle(
                        color = ObsidianSpectrum.Teal.copy(alpha = 0.22f),
                        radius = size.minDimension * 0.36f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    drawLine(
                        color = colors.border,
                        start = Offset(16.dp.toPx(), cy),
                        end = Offset(size.width - 16.dp.toPx(), cy),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawLine(
                        color = colors.border,
                        start = Offset(cx, 16.dp.toPx()),
                        end = Offset(cx, size.height - 16.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )

                    val tx = if (state.selectedDrill == DrillType.GAZE_STABILITY) {
                        cx
                    } else {
                        (state.currentPoint.x * size.width).coerceIn(28.dp.toPx(), size.width - 28.dp.toPx())
                    }
                    val ty = if (state.selectedDrill == DrillType.GAZE_STABILITY) {
                        cy
                    } else {
                        (state.currentPoint.y * size.height).coerceIn(28.dp.toPx(), size.height - 28.dp.toPx())
                    }

                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(ObsidianSpectrum.Aqua.copy(alpha = 0.38f), colors.surface.copy(alpha = 0f)),
                            center = Offset(tx, ty),
                            radius = 36.dp.toPx(),
                        ),
                        radius = 36.dp.toPx(),
                        center = Offset(tx, ty),
                    )
                    drawCircle(
                        color = ObsidianSpectrum.Aqua,
                        radius = 16.dp.toPx(),
                        center = Offset(tx, ty),
                        style = Stroke(width = 1.8.dp.toPx()),
                    )
                    drawCircle(
                        color = if (state.stabilitySignalActive) ObsidianSpectrum.SoftWhite else ObsidianSpectrum.Aqua,
                        radius = 6.dp.toPx(),
                        center = Offset(tx, ty),
                    )
                }
            }
            MrsadPrimaryButton(
                text = "ثبت تمرکز روی هدف",
                onClick = onHit,
                accentColor = ObsidianSpectrum.Aqua,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    MrsadSecondaryButton(
        text = "پایان و ثبت گزارش جلسه",
        onClick = onFinishEarly,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EyeResultPanel(
    state: EyeUiState,
    persianNumerals: Boolean,
    onRestart: () -> Unit,
    onBackToSetup: () -> Unit,
) {
    val report = state.report ?: return
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type

    MrsadSurface(
        Modifier.fillMaxWidth(),
        accent = ObsidianSpectrum.Aqua,
    ) {
        SectionHeader(title = "گزارش جلسهٔ مهارت دیداری")
        Text(report.drill.titleFa, style = type.title, color = colors.textPrimary)
        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricValue(
                value = "${formatInt(report.agilityScore, persianNumerals)}٪",
                label = "شاخص چابکی دیداری",
                size = type.headline.fontSize,
                accent = ObsidianSpectrum.Aqua,
            )
            MetricValue(
                value = "${formatInt(report.accuracyPercent, persianNumerals)}٪",
                label = "دقت تعقیب و تمرکز",
                size = type.headline.fontSize,
                accent = ObsidianSpectrum.BrightBlue,
            )
            MetricValue(
                value = "${formatInt(report.durationSeconds, persianNumerals)}s",
                label = "زمان تمرین",
                size = type.headline.fontSize,
            )
        }
        MrsadDivider()
        Text(
            EyeTrainingEngine.DISCLAIMER_FA,
            style = type.caption,
            color = colors.textMuted,
            textAlign = TextAlign.Start,
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
        MrsadPrimaryButton(
            text = "تکرار تمرین",
            onClick = onRestart,
            accentColor = ObsidianSpectrum.Aqua,
            modifier = Modifier.weight(1f),
        )
        MrsadSecondaryButton(
            text = "تنظیمات تمرین",
            onClick = onBackToSetup,
            modifier = Modifier.weight(1f),
        )
    }
}
