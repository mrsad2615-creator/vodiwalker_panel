package dev.mrsad.app.feature.read

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import dev.mrsad.app.ui.theme.formatPercent
import dev.mrsad.core.text.PersianText

/**
 * MODULE 02 — MRSAD READING (Master Prompt §4):
 * Primary Identity: Deep Violet (`#5B2EFF`) & Luminous Lavender (`#B892FF`).
 */
@Composable
fun ReadScreen(
    container: AppContainer,
    settings: MrsadSettings,
) {
    val viewModel: ReadViewModel = viewModel(key = "read", factory = ReadViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val isCollapsed by remember { derivedStateOf { scrollState.value > 28 } }

    LaunchedEffect(state.phase, state.wpm) {
        if (state.phase == ReadPhase.READING) {
            viewModel.runPresentationClock()
        }
    }

    AmbientBackground(PageEnvironment.READ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = LocalBottomBarClearance.current),
        ) {
            ObsidianCollapsingTopBar(
                title = "تندخوانی و درک مطلب",
                subtitle = "نمایش متوالی سریع (RSVP) با نقطهٔ تشخیص بهینه (ORP)",
                collapsed = isCollapsed,
                spectralColor = ObsidianSpectrum.Lavender,
                onBack = if (state.phase != ReadPhase.SETUP) {
                    { viewModel.reset() }
                } else {
                    null
                },
                leading = {
                    ObsidianIconBox(
                        size = if (isCollapsed) 38.dp else 48.dp,
                        accent = ObsidianSpectrum.Lavender,
                        elevated = true,
                    ) {
                        ModuleSculpture3D(
                            type = ModuleSculptureType.READING,
                            modifier = Modifier.size(if (isCollapsed) 28.dp else 36.dp),
                            animated = true,
                        )
                    }
                },
                trailing = {
                    MrsadChip(
                        text = "${formatInt(state.wpm, settings.persianNumerals)} WPM",
                        tint = ObsidianSpectrum.Lavender,
                        compact = true,
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
                    ReadPhase.SETUP -> ReadSetup(
                        state = state,
                        persianNumerals = settings.persianNumerals,
                        onWpm = viewModel::setWpm,
                        onWordsPerDisplay = viewModel::setWordsPerDisplay,
                        onLevelFilter = viewModel::setLevelFilter,
                        onSelectText = viewModel::selectText,
                        onStart = viewModel::start,
                    )
                    ReadPhase.READING, ReadPhase.PAUSED -> ReadStage(
                        state = state,
                        persianNumerals = settings.persianNumerals,
                        onPause = viewModel::pause,
                        onResume = viewModel::resume,
                        onStep = viewModel::stepChunk,
                        onStop = viewModel::stopEarly,
                    )
                    ReadPhase.QUIZ -> ReadQuiz(
                        state = state,
                        persianNumerals = settings.persianNumerals,
                        onAnswer = viewModel::answer,
                        onSubmit = viewModel::submitQuiz,
                    )
                    ReadPhase.RESULT -> ReadResult(
                        state = state,
                        persianNumerals = settings.persianNumerals,
                        onRestart = viewModel::reset,
                    )
                }

                Spacer(Modifier.height(MrsadTokens.Spacing.lg))
            }
        }
    }
}

@Composable
private fun ReadSetup(
    state: ReadUiState,
    persianNumerals: Boolean,
    onWpm: (Int) -> Unit,
    onWordsPerDisplay: (Int) -> Unit,
    onLevelFilter: (Int) -> Unit,
    onSelectText: (String) -> Unit,
    onStart: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val filteredTexts = ReadTexts.forLevel(state.selectedLevelFilter)

    MrsadSurface(
        Modifier.fillMaxWidth(),
        accent = ObsidianSpectrum.Lavender,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(title = "۱. سرعت نمایش (کلمه در دقیقه)")
            MrsadChip(
                text = "${formatInt(state.wpm, persianNumerals)} WPM",
                tint = ObsidianSpectrum.Lavender,
                compact = true,
            )
        }
        Slider(
            value = state.wpm.toFloat(),
            onValueChange = { onWpm(it.toInt()) },
            valueRange = 140f..750f,
            steps = 11,
            colors = SliderDefaults.colors(
                thumbColor = ObsidianSpectrum.SoftWhite,
                activeTrackColor = ObsidianSpectrum.Lavender,
                inactiveTrackColor = colors.surfaceHigh,
            ),
        )

        Spacer(Modifier.height(MrsadTokens.Spacing.xxs))
        SectionHeader(title = "۲. عرض حوزهٔ دیداری در هر نمایش")
        ObsidianSegmentedBar(
            items = listOf(1, 2, 3).map { count ->
                SegmentedBarItem(
                    id = count.toString(),
                    label = when (count) {
                        1 -> "${formatInt(1, persianNumerals)} کلمه (کانونی)"
                        2 -> "${formatInt(2, persianNumerals)} کلمه (عبارتی)"
                        else -> "${formatInt(3, persianNumerals)} کلمه (گسترش دید)"
                    },
                    accent = ObsidianSpectrum.Lavender,
                )
            },
            selectedId = state.wordsPerDisplay.toString(),
            onSelect = { id -> id.toIntOrNull()?.let(onWordsPerDisplay) },
        )

        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        SectionHeader(title = "۳. سطح و متن علمی مطالعه")
        ObsidianSegmentedBar(
            items = listOf(0, 1, 2, 3).map { lvl ->
                SegmentedBarItem(
                    id = lvl.toString(),
                    label = if (lvl == 0) "همهٔ سطوح" else "سطح ${formatInt(lvl, persianNumerals)}",
                    accent = ObsidianSpectrum.Lavender,
                )
            },
            selectedId = state.selectedLevelFilter.toString(),
            onSelect = { id -> id.toIntOrNull()?.let(onLevelFilter) },
        )

        Spacer(Modifier.height(MrsadTokens.Spacing.xxs))
        filteredTexts.forEach { text ->
            val selected = text.id == state.selectedTextId
            val wordCount = PersianText.wordCount(text.body)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MrsadTokens.Radius.md))
                    .background(
                        if (selected) {
                            ObsidianSpectrum.DeepViolet.copy(alpha = 0.25f)
                        } else {
                            colors.backgroundElevated.copy(alpha = 0.65f)
                        },
                    )
                    .border(
                        1.dp,
                        if (selected) ObsidianSpectrum.Lavender else colors.border,
                        RoundedCornerShape(MrsadTokens.Radius.md),
                    )
                    .clickable { onSelectText(text.id) }
                    .padding(MrsadTokens.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Strict RTL Right Anchor: 48dp Icon Box
                ObsidianIconBox(
                    size = 48.dp,
                    accent = if (selected) ObsidianSpectrum.Lavender else colors.borderStrong,
                    elevated = selected,
                ) {
                    MetalIcon(
                        MrsadIcons.Read,
                        accentOverride = if (selected) ObsidianSpectrum.Lavender else colors.textSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(Modifier.width(MrsadTokens.Spacing.xs))

                // Middle: Title, Topic & Metadata
                Column(Modifier.weight(1f)) {
                    Text(
                        text = text.title,
                        style = type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${text.topic} • ${ReadTexts.levelLabelFa(text.level)}",
                        style = type.caption,
                        color = ObsidianSpectrum.Lavender,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${formatInt(wordCount, persianNumerals)} کلمه • ${formatInt(text.questions.size, persianNumerals)} پرسش درک مطلب",
                        style = type.caption,
                        color = colors.textMuted,
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.width(MrsadTokens.Spacing.xs))

                // Left Anchor: Recommended WPM Badge
                MrsadChip(
                    text = "${formatInt(text.recommendedWpm, persianNumerals)} WPM",
                    tint = if (selected) ObsidianSpectrum.Lavender else colors.textSecondary,
                    compact = true,
                )
            }
        }

        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        MrsadPrimaryButton(
            text = "شروع مطالعهٔ هدایت‌شده",
            icon = Icons.Outlined.PlayArrow,
            onClick = onStart,
            accentColor = ObsidianSpectrum.Lavender,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReadStage(
    state: ReadUiState,
    persianNumerals: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStep: (Int) -> Unit,
    onStop: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val paused = state.phase == ReadPhase.PAUSED

    val orpAnnotated = remember(state.currentChunkText, state.currentChunkPivotIndex, colors.textPrimary) {
        val txt = state.currentChunkText
        val pivot = state.currentChunkPivotIndex.coerceIn(0, (txt.length - 1).coerceAtLeast(0))
        buildAnnotatedString {
            if (txt.isEmpty()) return@buildAnnotatedString
            if (pivot > 0) {
                withStyle(SpanStyle(color = colors.textPrimary)) {
                    append(txt.substring(0, pivot))
                }
            }
            withStyle(
                SpanStyle(
                    color = ObsidianSpectrum.Aqua,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                ),
            ) {
                append(txt[pivot])
            }
            if (pivot + 1 < txt.length) {
                withStyle(SpanStyle(color = colors.textPrimary)) {
                    append(txt.substring(pivot + 1))
                }
            }
        }
    }

    MrsadSurface(
        Modifier.fillMaxWidth(),
        elevated = true,
        accent = ObsidianSpectrum.Lavender,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ObsidianIconBox(size = 40.dp, accent = ObsidianSpectrum.Lavender) {
                    MetalIcon(MrsadIcons.Read, accentOverride = ObsidianSpectrum.Lavender, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                Column {
                    Text(
                        state.selectedText.title,
                        style = type.label.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1,
                    )
                    Text(
                        "بخش ${formatInt(state.chunkIndex + 1, persianNumerals)} از ${formatInt(state.chunkCount, persianNumerals)}",
                        style = type.caption,
                        color = colors.textSecondary,
                    )
                }
            }
            MrsadChip(
                text = if (paused) "متوقف" else "${formatInt(state.wpm, persianNumerals)} WPM",
                tint = if (paused) ObsidianSpectrum.Pink else ObsidianSpectrum.Lavender,
                compact = true,
            )
        }

        MrsadProgressBar(progress = state.progress, tint = ObsidianSpectrum.Lavender)

        // Scientific ORP Foveal Reticle Box
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(MrsadTokens.Radius.lg))
                .background(colors.background.copy(alpha = 0.82f))
                .border(1.dp, ObsidianSpectrum.Lavender.copy(alpha = 0.48f), RoundedCornerShape(MrsadTokens.Radius.lg))
                .padding(horizontal = MrsadTokens.Spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            // Top & Bottom Foveal Alignment Tick Marks
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .width(2.dp)
                    .height(16.dp)
                    .background(ObsidianSpectrum.Aqua.copy(alpha = 0.75f)),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 34.dp)
                    .width(2.dp)
                    .height(16.dp)
                    .background(ObsidianSpectrum.Aqua.copy(alpha = 0.75f)),
            )

            Text(
                text = orpAnnotated,
                style = type.display.copy(fontSize = 34.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = if (paused) {
                    "مکث فعال — می‌توانید ۵ بخش به عقب یا جلو بروید"
                } else {
                    "نگاه را روی حرف فیروزه‌ای (نقطهٔ کانونی ORP) ثابت نگه دارید"
                },
                style = type.caption,
                color = if (paused) ObsidianSpectrum.Pink else colors.textMuted,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
            )
        }

        // Step backward / forward controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
        ) {
            MrsadSecondaryButton(
                text = "۵ بخش عقب",
                onClick = { onStep(-5) },
                modifier = Modifier.weight(1f),
            )
            MrsadSecondaryButton(
                text = "۵ بخش جلو",
                onClick = { onStep(5) },
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
            if (paused) {
                MrsadPrimaryButton(
                    text = "ادامهٔ مطالعه",
                    onClick = onResume,
                    accentColor = ObsidianSpectrum.Lavender,
                    modifier = Modifier.weight(1f),
                )
            } else {
                MrsadSecondaryButton(text = "توقف موقت", onClick = onPause, modifier = Modifier.weight(1f))
            }
            MrsadPrimaryButton(
                text = "ارزیابی درک مطلب",
                onClick = onStop,
                accentColor = ObsidianSpectrum.Aqua,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReadQuiz(
    state: ReadUiState,
    persianNumerals: Boolean,
    onAnswer: (Int) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val question = state.questions.getOrNull(state.questionIndex) ?: return

    MrsadSurface(
        Modifier.fillMaxWidth(),
        elevated = true,
        accent = ObsidianSpectrum.Lavender,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ObsidianIconBox(size = 44.dp, accent = ObsidianSpectrum.Lavender) {
                    MetalIcon(MrsadIcons.Target, accentOverride = ObsidianSpectrum.Lavender, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                Column {
                    Text("ارزیابی درک مطلب", style = type.title.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    Text(state.selectedText.title, style = type.caption, color = colors.textSecondary)
                }
            }
            MrsadChip(
                text = "پرسش ${formatInt(state.questionIndex + 1, persianNumerals)} از ${formatInt(state.questions.size, persianNumerals)}",
                tint = ObsidianSpectrum.Lavender,
                compact = true,
            )
        }

        MrsadProgressBar(
            progress = (state.questionIndex + 1).toFloat() / state.questions.size.coerceAtLeast(1),
            tint = ObsidianSpectrum.Lavender,
        )

        Spacer(Modifier.height(MrsadTokens.Spacing.xxs))
        Text(question.prompt, style = type.bodyLarge.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
        Spacer(Modifier.height(MrsadTokens.Spacing.xxs))

        question.options.forEachIndexed { index, option ->
            val selected = state.answers.getOrNull(state.questionIndex) == index
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                    .background(
                        if (selected) {
                            ObsidianSpectrum.DeepViolet.copy(alpha = 0.28f)
                        } else {
                            colors.surfaceHigh.copy(alpha = 0.55f)
                        },
                    )
                    .border(
                        1.dp,
                        if (selected) ObsidianSpectrum.Lavender else colors.border,
                        RoundedCornerShape(MrsadTokens.Radius.sm),
                    )
                    .clickable { onAnswer(index) }
                    .padding(MrsadTokens.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (selected) ObsidianSpectrum.Lavender else colors.surfaceHigh)
                        .border(1.dp, if (selected) ObsidianSpectrum.SoftWhite else colors.borderStrong, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = formatInt(index + 1, persianNumerals),
                        style = type.caption.copy(fontWeight = FontWeight.Bold),
                        color = if (selected) colors.background else colors.textSecondary,
                    )
                }
                Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                Text(option, style = type.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        MrsadPrimaryButton(
            text = if (state.questionIndex == state.questions.lastIndex) "ثبت نهایی و محاسبهٔ سرعت مؤثر" else "پرسش بعدی",
            onClick = onSubmit,
            enabled = state.answers.getOrNull(state.questionIndex) != null,
            accentColor = ObsidianSpectrum.Lavender,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReadResult(
    state: ReadUiState,
    persianNumerals: Boolean,
    onRestart: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val passed = state.comprehensionPercent >= 60

    MrsadSurface(
        Modifier.fillMaxWidth(),
        elevated = true,
        accent = ObsidianSpectrum.Lavender,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ObsidianIconBox(size = 48.dp, accent = ObsidianSpectrum.Lavender, elevated = true) {
                    MetalIcon(MrsadIcons.Trophy, accentOverride = ObsidianSpectrum.Lavender, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                Column {
                    Text("گزارش تندخوانی و درک مطلب", style = type.title.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    Text(
                        if (state.saved) "در تحلیل‌ها ثبت شد" else "در حال ذخیره…",
                        style = type.caption,
                        color = if (state.saved) ObsidianSpectrum.Aqua else colors.textMuted,
                    )
                }
            }
            MrsadChip(
                text = if (passed) "درک استاندارد" else "نیاز به مرور",
                tint = if (passed) ObsidianSpectrum.Aqua else ObsidianSpectrum.Pink,
                compact = true,
            )
        }

        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MetricValue(
                value = formatInt(state.rawWpm, persianNumerals),
                label = "سرعت خام (WPM)",
                accent = ObsidianSpectrum.Lavender,
            )
            MetricValue(
                value = formatPercent(state.comprehensionPercent.toDouble(), persianNumerals),
                label = "درک مطلب",
                accent = if (passed) ObsidianSpectrum.Aqua else ObsidianSpectrum.Pink,
            )
            MetricValue(
                value = formatInt(state.effectiveWpm, persianNumerals),
                label = "سرعت مؤثر (WPM)",
                accent = ObsidianSpectrum.BrightBlue,
            )
        }
        MrsadDivider()
        Text(
            "فرمول علمی سرعت مؤثر = سرعت خام × درصد درک مطلب. تندخوانی استاندارد تنها زمانی معتبر است که درک مطلب بالای ۶۰٪ حفظ شود.",
            style = type.caption,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        MrsadPrimaryButton(
            text = "جلسهٔ جدید تندخوانی",
            icon = Icons.Outlined.Refresh,
            onClick = onRestart,
            accentColor = ObsidianSpectrum.Lavender,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
