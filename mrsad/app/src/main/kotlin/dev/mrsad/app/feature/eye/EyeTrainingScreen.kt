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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import dev.mrsad.app.ui.components.MrsadDivider
import dev.mrsad.app.ui.components.MrsadIcons
import dev.mrsad.app.ui.components.MrsadPrimaryButton
import dev.mrsad.app.ui.components.MrsadProgressBar
import dev.mrsad.app.ui.components.MrsadSecondaryButton
import dev.mrsad.app.ui.components.MrsadSurface
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.components.SectionHeader
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.formatInt
import dev.mrsad.core.eye.EyeTrainingEngine
import dev.mrsad.core.eye.EyeTrainingEngine.DrillType

/**
 * MRSAD EYE — Visual Attention & Ocular Skill Training (Master Prompt §5).
 *
 * Non-medical visual attention, saccadic agility, smooth pursuit, and
 * peripheral span drills rendered in both OBSIDIAN (3D Ocular Aperture) and
 * NOIR (Swiss Monochrome Reticle).
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
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type

    AmbientBackground(PageEnvironment.EYE) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = LocalBottomBarClearance.current)
                .verticalScroll(rememberScrollState())
                .padding(MrsadTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    Box(
                        Modifier
                            .size(MrsadTokens.TouchTarget)
                            .clip(RoundedCornerShape(if (colors.isNoir) 2.dp else MrsadTokens.Radius.sm))
                            .clickable(role = Role.Button, onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "بازگشت",
                            tint = colors.textPrimary,
                        )
                    }
                    Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                }
                MetalIcon(MrsadIcons.Eye, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                Column(Modifier.weight(1f)) {
                    Text(
                        "MRSAD EYE",
                        style = type.headline.copy(letterSpacing = 1.sp),
                        color = colors.textPrimary,
                    )
                    Text(
                        "تمرین مهارت‌های دیداری، ساکاد و میدان توجه",
                        style = type.caption,
                        color = colors.textSecondary,
                    )
                }
                MrsadChip(
                    text = "${formatInt(state.totalEyeSessions, settings.persianNumerals)} جلسه",
                    tint = colors.accent,
                )
            }

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
    val isNoir = colors.isNoir

    // Hero Ocular 3D Sculpture (OBSIDIAN) / Technical Reticle Header (NOIR)
    MrsadSurface(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
        ) {
            ModuleSculpture3D(
                type = ModuleSculptureType.EYE,
                modifier = Modifier.size(92.dp),
                animated = true,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (isNoir) "پروتکل توجه دیداری // 03" else "مرکز چابکی نگاه و میدان دید",
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
            )
            MetricValue(
                value = if (state.averageAgilityScore > 0) "${formatInt(state.averageAgilityScore, persianNumerals)}٪" else "—",
                label = "میانگین چابکی دید",
                size = type.title.fontSize,
            )
            MetricValue(
                value = "سطح ${formatInt(state.difficultyLevel, persianNumerals)}",
                label = "درجهٔ دشواری",
                size = type.title.fontSize,
            )
        }
    }

    // 4 Interactive Drills Selection
    MrsadSurface(Modifier.fillMaxWidth()) {
        SectionHeader(title = "انتخاب نوع تمرین دیداری")
        DrillType.entries.forEach { drill ->
            val selected = drill == state.selectedDrill
            val itemShape = if (isNoir) RoundedCornerShape(2.dp) else RoundedCornerShape(MrsadTokens.Radius.md)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(itemShape)
                    .background(if (selected) colors.accentSoft else colors.surfaceHigh.copy(alpha = 0.45f))
                    .border(
                        width = 1.dp,
                        color = if (selected) colors.accent else colors.border,
                        shape = itemShape,
                    )
                    .heightIn(min = MrsadTokens.TouchTarget)
                    .clickable(role = Role.RadioButton) { onSelectDrill(drill) }
                    .padding(horizontal = MrsadTokens.Spacing.sm, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetalIcon(MrsadIcons.Eye, modifier = Modifier.size(20.dp))
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
                    Text("فعال", style = type.caption, color = colors.accent)
                }
            }
        }
    }

    // Speed, Difficulty & Duration Controls
    MrsadSurface(Modifier.fillMaxWidth()) {
        SectionHeader(title = "کنترل سرعت، دشواری و زمان")

        Text("سرعت حرکت محرک (۱ تا ۵)", style = type.label, color = colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
            (1..5).forEach { lvl ->
                LevelChoicePill(
                    label = formatInt(lvl, persianNumerals),
                    selected = state.speedLevel == lvl,
                    modifier = Modifier.weight(1f),
                    onClick = { onSpeed(lvl) },
                )
            }
        }

        Spacer(Modifier.height(MrsadTokens.Spacing.xxs))
        Text("سطح دشواری و فشردگی میدان دید (۱ تا ۵)", style = type.label, color = colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
            (1..5).forEach { lvl ->
                LevelChoicePill(
                    label = formatInt(lvl, persianNumerals),
                    selected = state.difficultyLevel == lvl,
                    modifier = Modifier.weight(1f),
                    onClick = { onDifficulty(lvl) },
                )
            }
        }

        Spacer(Modifier.height(MrsadTokens.Spacing.xxs))
        Text("مدت جلسه", style = type.label, color = colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
            listOf(30, 45, 60, 90).forEach { sec ->
                LevelChoicePill(
                    label = "${formatInt(sec, persianNumerals)} ثانیه",
                    selected = state.durationSeconds == sec,
                    modifier = Modifier.weight(1f),
                    onClick = { onDuration(sec) },
                )
            }
        }
    }

    // Non-medical scope notice
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
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LevelChoicePill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val shape = if (colors.isNoir) RoundedCornerShape(2.dp) else RoundedCornerShape(MrsadTokens.Radius.sm)
    Box(
        modifier = modifier
            .heightIn(min = MrsadTokens.TouchTarget)
            .clip(shape)
            .background(if (selected) colors.accentSoft else colors.surfaceHigh.copy(alpha = 0.5f))
            .border(1.dp, if (selected) colors.accent else colors.border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MrsadTheme.type.label,
            color = if (selected) colors.textPrimary else colors.textSecondary,
        )
    }
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

    MrsadSurface(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetricValue(
                value = "${formatInt(secondsLeft, persianNumerals)}s",
                label = "زمان باقی‌مانده",
                size = type.title.fontSize,
            )
            MetricValue(
                value = formatInt(state.hits, persianNumerals),
                label = "ثبت دقیق",
                size = type.title.fontSize,
            )
            MetricValue(
                value = "سطح ${formatInt(state.difficultyLevel, persianNumerals)}",
                label = state.selectedDrill.titleFa.substringBefore(" ("),
                size = type.label.fontSize,
            )
        }
        MrsadProgressBar(progress = state.progress)
        Text(
            state.selectedDrill.guideFa,
            style = type.caption,
            color = colors.textSecondary,
        )
    }

    if (state.selectedDrill == DrillType.PERIPHERAL_SPAN) {
        MrsadSurface(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("کانون دید را روی مرکز نگه دارید", style = type.caption, color = colors.textMuted)
                MrsadChip(
                    text = "عدد بعدی: ${formatInt(state.nextSchulteNumber, persianNumerals)}",
                    tint = colors.accent,
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
                            val cellShape = if (colors.isNoir) RoundedCornerShape(2.dp) else RoundedCornerShape(MrsadTokens.Radius.sm)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.15f)
                                    .clip(cellShape)
                                    .background(if (done) colors.accentSoft else colors.surfaceHigh)
                                    .border(1.dp, if (done) colors.accent else colors.border, cellShape)
                                    .clickable(role = Role.Button) { onSchulteTap(num) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    formatInt(num, persianNumerals),
                                    style = type.title,
                                    color = if (done) colors.textMuted else colors.textPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        MrsadSurface(Modifier.fillMaxWidth()) {
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

                    // Guide horizon & reticle lines
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

                    if (colors.isNoir) {
                        // NOIR: Monochrome calibrated reticle
                        drawCircle(
                            color = colors.textPrimary,
                            radius = 18.dp.toPx(),
                            center = Offset(tx, ty),
                            style = Stroke(width = 1.6.dp.toPx()),
                        )
                        if (state.stabilitySignalActive || state.selectedDrill != DrillType.GAZE_STABILITY) {
                            drawCircle(
                                color = colors.textPrimary,
                                radius = 5.dp.toPx(),
                                center = Offset(tx, ty),
                            )
                        }
                    } else {
                        // OBSIDIAN: 3D Metallic/Glass Ocular Target
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(colors.accent.copy(alpha = 0.35f), colors.surface.copy(alpha = 0f)),
                                center = Offset(tx, ty),
                                radius = 34.dp.toPx(),
                            ),
                            radius = 34.dp.toPx(),
                            center = Offset(tx, ty),
                        )
                        drawCircle(
                            color = colors.metalLight,
                            radius = 15.dp.toPx(),
                            center = Offset(tx, ty),
                            style = Stroke(width = 1.8.dp.toPx()),
                        )
                        drawCircle(
                            color = if (state.stabilitySignalActive) colors.textPrimary else colors.metalMid,
                            radius = 6.dp.toPx(),
                            center = Offset(tx, ty),
                        )
                    }
                }
            }
            MrsadPrimaryButton(
                text = "ثبت تمرکز روی هدف",
                onClick = onHit,
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

    MrsadSurface(Modifier.fillMaxWidth()) {
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
            )
            MetricValue(
                value = "${formatInt(report.accuracyPercent, persianNumerals)}٪",
                label = "دقت تعقیب و تمرکز",
                size = type.headline.fontSize,
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
            modifier = Modifier.weight(1f),
        )
        MrsadSecondaryButton(
            text = "تنظیمات تمرین",
            onClick = onBackToSetup,
            modifier = Modifier.weight(1f),
        )
    }
}
