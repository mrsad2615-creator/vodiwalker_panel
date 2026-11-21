package dev.mrsad.app.feature.eye

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import dev.mrsad.core.eye.EyeTrainingEngine
import dev.mrsad.core.eye.EyeTrainingEngine.DrillType
import kotlin.math.cos
import kotlin.math.sin

/**
 * MODULE 03 — MRSAD EYE TRAINING (Master Prompt §4):
 * Primary Identity: Precision Teal (`#0FA3B1`) & Luminous Aqua (`#4EF2D2`).
 */
@Composable
fun EyeTrainingScreen(
    container: AppContainer,
    settings: MrsadSettings,
    onBack: (() -> Unit)? = null,
) {
    val viewModel: EyeTrainingViewModel = viewModel(
        key = "eye_training",
        factory = EyeTrainingViewModel.Factory(container),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val isCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 28
        }
    }

    AmbientBackground(PageEnvironment.EYE) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = LocalBottomBarClearance.current),
        ) {
            ObsidianCollapsingTopBar(
                title = "ورزش چشم و توجه دیداری",
                subtitle = "ساکاد، تعقیب نرم، جدول شولته و پایداری نگاه مرکزی",
                collapsed = isCollapsed,
                spectralColor = ObsidianSpectrum.Aqua,
                onBack = if (state.phase != EyePhase.SETUP) {
                    { viewModel.resetToSetup() }
                } else {
                    onBack
                },
                leading = {
                    ObsidianIconBox(
                        size = if (isCollapsed) 38.dp else 48.dp,
                        accent = ObsidianSpectrum.Aqua,
                        elevated = true,
                    ) {
                        ModuleSculpture3D(
                            type = ModuleSculptureType.EYE,
                            modifier = Modifier.size(if (isCollapsed) 28.dp else 36.dp),
                            animated = true,
                        )
                    }
                },
                trailing = {
                    MrsadChip(
                        text = if (state.bestAgilityScore > 0) {
                            "رکورد ${formatInt(state.bestAgilityScore, settings.persianNumerals)}"
                        } else {
                            "۴ پروتکل"
                        },
                        tint = ObsidianSpectrum.Aqua,
                        compact = true,
                    )
                },
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = MrsadTokens.Spacing.md),
                verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
                contentPadding = PaddingValues(
                    top = MrsadTokens.Spacing.xs,
                    bottom = MrsadTokens.Spacing.xl,
                ),
            ) {
                when (state.phase) {
                    EyePhase.SETUP -> {
                        item(key = "setup") {
                            EyeSetupPanel(
                                state = state,
                                persianNumerals = settings.persianNumerals,
                                onSelectDrill = viewModel::selectDrill,
                                onSpeedChange = viewModel::setSpeedLevel,
                                onDifficultyChange = viewModel::setDifficultyLevel,
                                onDurationChange = viewModel::setDurationSeconds,
                                onStart = viewModel::startSession,
                            )
                        }
                    }

                    EyePhase.RUNNING, EyePhase.PAUSED -> {
                        item(key = "running") {
                            EyeRunningPanel(
                                state = state,
                                persianNumerals = settings.persianNumerals,
                                onCanvasTap = viewModel::onCanvasTap,
                                onHit = viewModel::onTargetHit,
                                onSchulteTap = viewModel::onSchulteCellTap,
                                onStop = { viewModel.finishSession(completed = false) },
                            )
                        }
                    }

                    EyePhase.RESULT -> {
                        item(key = "result") {
                            EyeResultPanel(
                                state = state,
                                persianNumerals = settings.persianNumerals,
                                onRestart = viewModel::startSession,
                                onBackToSetup = viewModel::resetToSetup,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EyeSetupPanel(
    state: EyeUiState,
    persianNumerals: Boolean,
    onSelectDrill: (DrillType) -> Unit,
    onSpeedChange: (Int) -> Unit,
    onDifficultyChange: (Int) -> Unit,
    onDurationChange: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type

    Column(verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md)) {
        // Non-medical scientific scope notice
        MrsadSurface(
            modifier = Modifier.fillMaxWidth(),
            accent = ObsidianSpectrum.Teal,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
            ) {
                ObsidianIconBox(size = 44.dp, accent = ObsidianSpectrum.Aqua) {
                    MetalIcon(MrsadIcons.Eye, accentOverride = ObsidianSpectrum.Aqua, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "چارچوب علمی و غیرپزشکی تمرین دیداری",
                        style = type.label.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                    Text(
                        EyeTrainingEngine.DISCLAIMER_FA,
                        style = type.caption,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        // Drill selector with strict RTL layout
        MrsadSurface(
            modifier = Modifier.fillMaxWidth(),
            accent = ObsidianSpectrum.Aqua,
        ) {
            SectionHeader(title = "۱. انتخاب پروتکل تمرین دیداری")
            Column(verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
                DrillType.entries.forEach { drill ->
                    val selected = drill == state.selectedDrill
                    val shape = RoundedCornerShape(MrsadTokens.Radius.md)
                    val protocolBadge = when (drill) {
                        DrillType.SACCADE -> "پرش سریع"
                        DrillType.SMOOTH_PURSUIT -> "ردیابی موجی"
                        DrillType.PERIPHERAL_SPAN -> "جدول شولته"
                        DrillType.GAZE_STABILITY -> "مهار حواس‌پرتی"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(
                                if (selected) {
                                    ObsidianSpectrum.Teal.copy(alpha = 0.24f)
                                } else {
                                    colors.backgroundElevated.copy(alpha = 0.65f)
                                },
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) ObsidianSpectrum.Aqua else colors.border,
                                shape = shape,
                            )
                            .clickable { onSelectDrill(drill) }
                            .padding(MrsadTokens.Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Strict RTL Right Anchor: 48dp Icon Box
                        ObsidianIconBox(
                            size = 48.dp,
                            accent = if (selected) ObsidianSpectrum.Aqua else colors.borderStrong,
                            elevated = selected,
                        ) {
                            MetalIcon(
                                facets = when (drill) {
                                    DrillType.SACCADE -> MrsadIcons.Bolt
                                    DrillType.SMOOTH_PURSUIT -> MrsadIcons.Eye
                                    DrillType.PERIPHERAL_SPAN -> MrsadIcons.Target
                                    DrillType.GAZE_STABILITY -> MrsadIcons.Focus
                                },
                                accentOverride = if (selected) ObsidianSpectrum.Aqua else colors.textSecondary,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Spacer(Modifier.width(MrsadTokens.Spacing.xs))

                        // Middle: Title & Subtitle
                        Column(Modifier.weight(1f)) {
                            Text(
                                drill.titleFa,
                                style = type.body.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                drill.subtitleFa,
                                style = type.caption,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                drill.guideFa,
                                style = type.caption,
                                color = colors.textMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Spacer(Modifier.width(MrsadTokens.Spacing.xs))

                        // Left Anchor: Protocol Badge
                        MrsadChip(
                            text = protocolBadge,
                            tint = if (selected) ObsidianSpectrum.Aqua else colors.textSecondary,
                            compact = true,
                        )
                    }
                }
            }
        }

        // Calibration Controls
        MrsadSurface(
            modifier = Modifier.fillMaxWidth(),
            accent = ObsidianSpectrum.Teal,
        ) {
            SectionHeader(title = "۲. تنظیم سرعت، دشواری و زمان")

            Text("سرعت حرکت محرک (سطح ۱ تا ۵)", style = type.caption, color = colors.textSecondary)
            ObsidianSegmentedBar(
                items = (1..5).map { lvl ->
                    SegmentedBarItem(
                        id = lvl.toString(),
                        label = formatInt(lvl, persianNumerals),
                        accent = ObsidianSpectrum.Aqua,
                    )
                },
                selectedId = state.speedLevel.toString(),
                onSelect = { id -> id.toIntOrNull()?.let(onSpeedChange) },
            )

            Spacer(Modifier.height(MrsadTokens.Spacing.xxs))
            Text("دامنه و پیچیدگی میدان دید (سطح ۱ تا ۵)", style = type.caption, color = colors.textSecondary)
            ObsidianSegmentedBar(
                items = (1..5).map { lvl ->
                    SegmentedBarItem(
                        id = lvl.toString(),
                        label = formatInt(lvl, persianNumerals),
                        accent = ObsidianSpectrum.Teal,
                    )
                },
                selectedId = state.difficultyLevel.toString(),
                onSelect = { id -> id.toIntOrNull()?.let(onDifficultyChange) },
            )

            Spacer(Modifier.height(MrsadTokens.Spacing.xxs))
            Text("مدت زمان جلسه", style = type.caption, color = colors.textSecondary)
            ObsidianSegmentedBar(
                items = listOf(30, 60, 90, 120).map { sec ->
                    SegmentedBarItem(
                        id = sec.toString(),
                        label = "${formatInt(sec, persianNumerals)} ثانیه",
                        accent = ObsidianSpectrum.Aqua,
                    )
                },
                selectedId = state.durationSeconds.toString(),
                onSelect = { id -> id.toIntOrNull()?.let(onDurationChange) },
            )
        }

        MrsadPrimaryButton(
            text = "شروع تمرین: ${state.selectedDrill.titleFa}",
            onClick = onStart,
            accentColor = ObsidianSpectrum.Aqua,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EyeRunningPanel(
    state: EyeUiState,
    persianNumerals: Boolean,
    onCanvasTap: (Float, Float) -> Unit,
    onHit: () -> Unit,
    onSchulteTap: (Int) -> Unit,
    onStop: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val secLeft = ((state.remainingMillis + 999L) / 1000L).toInt()

    Column(verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md)) {
        // Live Telemetry Header
        MrsadSurface(
            modifier = Modifier.fillMaxWidth(),
            accent = ObsidianSpectrum.Aqua,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ObsidianIconBox(size = 40.dp, accent = ObsidianSpectrum.Aqua) {
                        MetalIcon(MrsadIcons.Eye, accentOverride = ObsidianSpectrum.Aqua, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                    Column {
                        Text(state.selectedDrill.titleFa, style = type.title.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                        Text(
                            "امتیاز صحیح: ${formatInt(state.hits, persianNumerals)} • خطا: ${formatInt(state.misses, persianNumerals)}",
                            style = type.caption,
                            color = colors.textSecondary,
                        )
                    }
                }
                MrsadChip(
                    text = "${formatInt(secLeft, persianNumerals)} ثانیه",
                    tint = ObsidianSpectrum.Aqua,
                    compact = true,
                )
            }
            MrsadProgressBar(
                progress = state.progress,
                height = 6.dp,
                tint = ObsidianSpectrum.Aqua,
            )
        }

        if (state.selectedDrill == DrillType.PERIPHERAL_SPAN) {
            // Schulte Peripheral Span Board with Central Foveal Anchor
            MrsadSurface(
                modifier = Modifier.fillMaxWidth(),
                elevated = true,
                accent = ObsidianSpectrum.Aqua,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("نگاه را روی مرکز جدول نگه دارید", style = type.label.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                        Text("با دید محیطی اعداد را به ترتیب پیدا کنید", style = type.caption, color = colors.textSecondary)
                    }
                    MrsadChip(
                        text = "عدد بعدی: ${formatInt(state.nextSchulteNumber, persianNumerals)}",
                        tint = ObsidianSpectrum.Aqua,
                        compact = true,
                    )
                }
                Spacer(Modifier.height(MrsadTokens.Spacing.xs))
                val side = state.schulteBoard.side
                val centerCellIndex = (side * side) / 2
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (r in 0 until side) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (c in 0 until side) {
                                val cellIdx = r * side + c
                                val num = state.schulteBoard.numbers.getOrElse(cellIdx) { 1 }
                                val alreadyFound = num < state.nextSchulteNumber
                                val isCenterAnchor = cellIdx == centerCellIndex
                                val shape = RoundedCornerShape(MrsadTokens.Radius.sm)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(shape)
                                        .background(
                                            when {
                                                alreadyFound -> ObsidianSpectrum.Teal.copy(alpha = 0.25f)
                                                isCenterAnchor -> ObsidianSpectrum.Aqua.copy(alpha = 0.14f)
                                                else -> colors.backgroundElevated
                                            },
                                        )
                                        .border(
                                            width = if (isCenterAnchor) 1.5.dp else 1.dp,
                                            color = when {
                                                alreadyFound -> ObsidianSpectrum.Aqua.copy(alpha = 0.55f)
                                                isCenterAnchor -> ObsidianSpectrum.Aqua
                                                else -> colors.border
                                            },
                                            shape = shape,
                                        )
                                        .clickable { onSchulteTap(num) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = formatInt(num, persianNumerals),
                                        style = type.title.copy(fontWeight = FontWeight.Bold),
                                        color = if (alreadyFound) ObsidianSpectrum.Aqua else colors.textPrimary,
                                    )
                                    if (isCenterAnchor) {
                                        Box(
                                            Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(top = 3.dp)
                                                .size(5.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(ObsidianSpectrum.Aqua),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Interactive Spatial Visual Target Canvas (Saccade, Smooth Pursuit, Gaze Stability)
            MrsadSurface(
                modifier = Modifier.fillMaxWidth(),
                elevated = true,
                accent = ObsidianSpectrum.Aqua,
            ) {
                Text(
                    text = when (state.selectedDrill) {
                        DrillType.SACCADE -> "بدون حرکت سر، با پرش سریع چشم دقیقاً روی دایرهٔ درخشان ضربه بزنید"
                        DrillType.SMOOTH_PURSUIT -> "مسیر پیوستهٔ هدف را دنبال کنید و روی هدف متحرک ضربه بزنید"
                        DrillType.GAZE_STABILITY -> "به حلقه‌های مزاحم اطراف توجه نکنید؛ فقط وقتی مرکز طلایی شد ضربه بزنید"
                        DrillType.PERIPHERAL_SPAN -> ""
                    },
                    style = type.caption,
                    color = colors.textSecondary,
                )

                val gridColor = colors.border.copy(alpha = 0.35f)
                val targetPrimary = if (state.selectedDrill == DrillType.GAZE_STABILITY && state.stabilitySignalActive) {
                    Color(0xFFF6C452)
                } else {
                    ObsidianSpectrum.Aqua
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(290.dp)
                        .clip(RoundedCornerShape(MrsadTokens.Radius.lg))
                        .background(colors.background.copy(alpha = 0.85f))
                        .border(1.dp, ObsidianSpectrum.Teal.copy(alpha = 0.48f), RoundedCornerShape(MrsadTokens.Radius.lg))
                        .pointerInput(state.selectedDrill) {
                            detectTapGestures { offset ->
                                val nx = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val ny = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                onCanvasTap(nx, ny)
                            }
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        // Subtle calibration crosshairs and concentric rings
                        drawLine(gridColor, Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 1.dp.toPx())
                        drawLine(gridColor, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1.dp.toPx())
                        drawCircle(
                            color = gridColor,
                            radius = size.minDimension * 0.34f,
                            center = Offset(w / 2f, h / 2f),
                            style = Stroke(width = 1.dp.toPx()),
                        )

                        // Draw peripheral distractors in GAZE_STABILITY mode
                        if (state.selectedDrill == DrillType.GAZE_STABILITY) {
                            val phase = state.distractorPhase
                            val distractors = listOf(
                                Offset(w * (0.20f + 0.05f * cos(phase * 2.3f)), h * (0.24f + 0.05f * sin(phase * 2.1f))),
                                Offset(w * (0.80f - 0.05f * sin(phase * 1.9f)), h * (0.26f + 0.04f * cos(phase * 2.4f))),
                                Offset(w * (0.22f + 0.04f * sin(phase * 2.6f)), h * (0.76f - 0.05f * cos(phase * 2.0f))),
                                Offset(w * (0.78f - 0.05f * cos(phase * 2.2f)), h * (0.74f + 0.05f * sin(phase * 2.5f))),
                            )
                            distractors.forEachIndexed { idx, dCenter ->
                                val pulse = 0.5f + 0.5f * sin(phase * 4f + idx * 1.5f)
                                drawCircle(
                                    color = ObsidianSpectrum.Pink.copy(alpha = 0.16f + 0.20f * pulse),
                                    radius = (12.dp + (6.dp * pulse)).toPx(),
                                    center = dCenter,
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                            }
                        }

                        val tx = (state.currentPoint.x * w).coerceIn(28.dp.toPx(), w - 28.dp.toPx())
                        val ty = (state.currentPoint.y * h).coerceIn(28.dp.toPx(), h - 28.dp.toPx())
                        val center = Offset(tx, ty)

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(targetPrimary.copy(alpha = 0.42f), Color.Transparent),
                                center = center,
                                radius = 38.dp.toPx(),
                            ),
                            radius = 38.dp.toPx(),
                            center = center,
                        )
                        drawCircle(
                            color = targetPrimary,
                            radius = 15.dp.toPx(),
                            center = center,
                            style = Stroke(width = 2.5.dp.toPx()),
                        )
                        drawCircle(
                            color = ObsidianSpectrum.SoftWhite,
                            radius = 5.5.dp.toPx(),
                            center = center,
                        )
                    }
                }

                if (state.selectedDrill == DrillType.GAZE_STABILITY || state.selectedDrill == DrillType.SMOOTH_PURSUIT) {
                    MrsadPrimaryButton(
                        text = if (state.selectedDrill == DrillType.GAZE_STABILITY) {
                            if (state.stabilitySignalActive) "ثبت سیگنال طلایی (حالا ضربه بزنید)" else "منتظر طلایی شدن نقطهٔ مرکزی بمانید…"
                        } else {
                            "ثبت قفل کانونی روی هدف متحرک"
                        },
                        onClick = onHit,
                        accentColor = if (state.stabilitySignalActive) Color(0xFFF6C452) else ObsidianSpectrum.Aqua,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Immediate Tap Feedback Banner
        state.lastTapFeedbackFa?.let { feedback ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                    .background(
                        if (state.lastTapWasHit) {
                            ObsidianSpectrum.Aqua.copy(alpha = 0.14f)
                        } else {
                            ObsidianSpectrum.Pink.copy(alpha = 0.16f)
                        },
                    )
                    .border(
                        1.dp,
                        if (state.lastTapWasHit) ObsidianSpectrum.Aqua.copy(alpha = 0.5f) else ObsidianSpectrum.Pink.copy(alpha = 0.55f),
                        RoundedCornerShape(MrsadTokens.Radius.sm),
                    )
                    .padding(horizontal = MrsadTokens.Spacing.sm, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = feedback,
                    style = type.caption.copy(fontWeight = FontWeight.Medium),
                    color = if (state.lastTapWasHit) ObsidianSpectrum.Aqua else ObsidianSpectrum.Pink,
                )
            }
        }

        MrsadSecondaryButton(
            text = "پایان و ثبت جلسه",
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EyeResultPanel(
    state: EyeUiState,
    persianNumerals: Boolean,
    onRestart: () -> Unit,
    onBackToSetup: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val report = state.report ?: return

    Column(verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md)) {
        MrsadSurface(
            modifier = Modifier.fillMaxWidth(),
            elevated = true,
            accent = ObsidianSpectrum.Aqua,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ObsidianIconBox(size = 48.dp, accent = ObsidianSpectrum.Aqua, elevated = true) {
                        MetalIcon(MrsadIcons.Trophy, accentOverride = ObsidianSpectrum.Aqua, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                    Column {
                        Text("نتیجهٔ تمرین دیداری", style = type.title.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                        Text(report.drill.titleFa, style = type.caption, color = colors.textSecondary)
                    }
                }
                MrsadChip(
                    text = "سطح ${formatInt(report.difficultyLevel, persianNumerals)}",
                    tint = ObsidianSpectrum.Aqua,
                    compact = true,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MrsadTokens.Spacing.sm),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MetricValue(
                    value = formatInt(report.agilityScore, persianNumerals),
                    label = "شاخص چابکی دیداری",
                    accent = ObsidianSpectrum.Aqua,
                )
                MetricValue(
                    value = "${formatInt(report.accuracyPercent, persianNumerals)}٪",
                    label = "دقت هدف‌گیری",
                    accent = ObsidianSpectrum.BrightBlue,
                )
                MetricValue(
                    value = formatInt(report.hits, persianNumerals),
                    label = "هدف‌های موفق",
                    accent = ObsidianSpectrum.Lavender,
                )
            }

            Text(
                report.feedbackFa,
                style = type.body,
                color = colors.textSecondary,
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
                text = "انتخاب تمرین دیگر",
                onClick = onBackToSetup,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
