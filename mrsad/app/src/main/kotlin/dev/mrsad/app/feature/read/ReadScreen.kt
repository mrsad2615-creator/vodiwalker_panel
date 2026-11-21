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
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import dev.mrsad.app.ui.theme.formatPercent

/**
 * MODULE 02 — MRSAD READING (Master Prompt §4):
 * Primary Identity: Deep Violet (`#7040B9`) & Lavender (`#C69CFF`).
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

    LaunchedEffect(state.phase) {
        if (state.phase == ReadPhase.READING) viewModel.runPresentationClock()
    }

    AmbientBackground(PageEnvironment.READ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = LocalBottomBarClearance.current),
        ) {
            ObsidianCollapsingTopBar(
                title = "MRSAD READ",
                subtitle = "خواندن سریع همراه با درک مطلب و RSVP",
                collapsed = isCollapsed,
                spectralColor = ObsidianSpectrum.Lavender,
                leading = {
                    ModuleSculpture3D(
                        type = ModuleSculptureType.READING,
                        modifier = Modifier.size(if (isCollapsed) 36.dp else 48.dp),
                        animated = true,
                    )
                },
                trailing = {
                    MrsadChip(
                        text = "${formatInt(state.wpm, settings.persianNumerals)} WPM",
                        tint = ObsidianSpectrum.Lavender,
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

                when (state.phase) {
                    ReadPhase.SETUP -> ReadSetup(
                        state = state,
                        persianNumerals = settings.persianNumerals,
                        onWpm = viewModel::setWpm,
                        onWords = viewModel::setWordsPerDisplay,
                        onText = viewModel::selectText,
                        onStart = viewModel::start,
                    )
                    ReadPhase.READING, ReadPhase.PAUSED -> ReadStage(
                        state = state,
                        persianNumerals = settings.persianNumerals,
                        onPause = viewModel::pause,
                        onResume = viewModel::resume,
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
                Spacer(Modifier.height(MrsadTokens.Spacing.xl))
            }
        }
    }
}

@Composable
private fun ReadSetup(
    state: ReadUiState,
    persianNumerals: Boolean,
    onWpm: (Int) -> Unit,
    onWords: (Int) -> Unit,
    onText: (String) -> Unit,
    onStart: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val selectedTextLevel = ReadTexts.all.firstOrNull { it.id == state.selectedTextId }?.level ?: 1
    var selectedTier by rememberSaveable { mutableStateOf(selectedTextLevel.toString()) }

    MrsadSurface(
        Modifier.fillMaxWidth(),
        accent = ObsidianSpectrum.Lavender,
    ) {
        SectionHeader(title = "کتابخانهٔ متون بر اساس سطح")
        ObsidianSegmentedBar(
            items = listOf(
                SegmentedBarItem("1", "سطح ۱ · آسان", ObsidianSpectrum.Lavender),
                SegmentedBarItem("2", "سطح ۲ · متوسط", ObsidianSpectrum.Lavender),
                SegmentedBarItem("3", "سطح ۳ · پیشرفته", ObsidianSpectrum.Lavender),
            ),
            selectedId = selectedTier,
            onSelect = { tier ->
                selectedTier = tier
                val level = tier.toIntOrNull() ?: 1
                ReadTexts.all.firstOrNull { it.level == level }?.let { onText(it.id) }
            },
        )

        val currentLevel = selectedTier.toIntOrNull() ?: 1
        val filteredTexts = ReadTexts.all.filter { it.level == currentLevel }
        filteredTexts.forEach { text ->
            val selected = text.id == state.selectedTextId
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                    .background(
                        if (selected) {
                            ObsidianSpectrum.DeepViolet.copy(alpha = 0.26f)
                        } else {
                            colors.backgroundElevated.copy(alpha = 0.55f)
                        },
                    )
                    .border(
                        1.dp,
                        if (selected) ObsidianSpectrum.Lavender else colors.border,
                        RoundedCornerShape(MrsadTokens.Radius.sm),
                    )
                    .clickable { onText(text.id) }
                    .padding(MrsadTokens.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetalIcon(
                    MrsadIcons.Read,
                    accentOverride = ObsidianSpectrum.Lavender,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                Column(Modifier.weight(1f)) {
                    Text(text.title, style = type.body, color = colors.textPrimary, fontWeight = FontWeight.Medium)
                    Text(
                        "${formatInt(text.wordCount, persianNumerals)} کلمه — ${text.category}",
                        style = type.caption,
                        color = colors.textMuted,
                    )
                }
                if (selected) MrsadChip(text = "انتخاب‌شده", tint = ObsidianSpectrum.Lavender)
            }
        }
    }

    MrsadSurface(
        Modifier.fillMaxWidth(),
        accent = ObsidianSpectrum.Lavender,
    ) {
        SectionHeader(title = "سرعت و چیدمان نمایش")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Speed, null, tint = ObsidianSpectrum.Lavender, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(MrsadTokens.Spacing.xs))
            Text(
                "${formatInt(state.wpm, persianNumerals)} کلمه در دقیقه",
                style = type.body.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
        }
        Slider(
            value = state.wpm.toFloat(),
            onValueChange = { onWpm(it.toInt()) },
            valueRange = 120f..900f,
            steps = 25,
            colors = SliderDefaults.colors(
                thumbColor = ObsidianSpectrum.SoftWhite,
                activeTrackColor = ObsidianSpectrum.Lavender,
                inactiveTrackColor = colors.surfaceHigh,
            ),
        )
        Text("تعداد کلمات در هر نمایش", style = type.label, color = colors.textSecondary)
        ObsidianSegmentedBar(
            items = listOf(1, 2, 3).map { words ->
                SegmentedBarItem(
                    id = words.toString(),
                    label = "${formatInt(words, persianNumerals)} کلمه",
                    accent = ObsidianSpectrum.Lavender,
                )
            },
            selectedId = state.wordsPerDisplay.toString(),
            onSelect = { id -> id.toIntOrNull()?.let(onWords) },
        )
        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        Text(
            "پس از خواندن، یک ارزیابی کوتاه درک مطلب انجام می‌شود؛ سرعت بدون درک مطلب به‌عنوان پیشرفت ثبت نمی‌شود.",
            style = type.caption,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(MrsadTokens.Spacing.sm))
        MrsadPrimaryButton(
            text = "شروع خواندن",
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
    onStop: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val paused = state.phase == ReadPhase.PAUSED

    MrsadSurface(
        Modifier.fillMaxWidth(),
        elevated = true,
        accent = ObsidianSpectrum.Lavender,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MrsadChip(text = "${formatInt(state.wpm, persianNumerals)} کلمه/دقیقه", tint = ObsidianSpectrum.Lavender)
            Spacer(Modifier.weight(1f))
            MrsadChip(
                text = "کلمهٔ ${formatInt(state.chunkIndex + 1, persianNumerals)} از ${formatInt(state.chunkCount, persianNumerals)}",
                tint = ObsidianSpectrum.DeepViolet,
            )
        }
        MrsadProgressBar(progress = state.progress, height = 4.dp, tint = ObsidianSpectrum.Lavender)
        Spacer(Modifier.height(MrsadTokens.Spacing.md))

        Box(
            Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(MrsadTokens.Radius.lg))
                .background(colors.background.copy(alpha = 0.72f))
                .border(1.dp, ObsidianSpectrum.DeepViolet.copy(alpha = 0.45f), RoundedCornerShape(MrsadTokens.Radius.lg)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(44.dp)
                            .background(ObsidianSpectrum.Lavender.copy(alpha = 0.65f)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = state.currentChunkText,
                        style = type.display.copy(fontSize = 36.sp, lineHeight = 52.sp),
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(44.dp)
                            .background(ObsidianSpectrum.Lavender.copy(alpha = 0.65f)),
                    )
                }
                Spacer(Modifier.height(MrsadTokens.Spacing.xs))
                Text(
                    if (paused) "مکث — زمان‌سنج متوقف است" else "با چشم، بدون حرکت لب، بخوانید",
                    style = type.caption,
                    color = if (paused) ObsidianSpectrum.Pink else colors.textMuted,
                )
            }
        }

        Spacer(Modifier.height(MrsadTokens.Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
            if (paused) {
                MrsadPrimaryButton(
                    text = "ادامه",
                    onClick = onResume,
                    accentColor = ObsidianSpectrum.Lavender,
                    modifier = Modifier.weight(1f),
                )
            } else {
                MrsadSecondaryButton(text = "توقف موقت", onClick = onPause, modifier = Modifier.weight(1f))
            }
            MrsadSecondaryButton(text = "پایان و ارزیابی", onClick = onStop, modifier = Modifier.weight(1f))
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
        Text("ارزیابی درک مطلب", style = type.title, color = colors.textPrimary)
        Text(
            "پرسش ${formatInt(state.questionIndex + 1, persianNumerals)} از ${formatInt(state.questions.size, persianNumerals)}",
            style = type.caption,
            color = ObsidianSpectrum.Lavender,
        )
        Spacer(Modifier.height(MrsadTokens.Spacing.sm))
        Text(question.prompt, style = type.bodyLarge, color = colors.textPrimary)
        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
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
                            colors.surfaceHigh.copy(alpha = 0.5f)
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
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (selected) ObsidianSpectrum.Lavender else colors.surfaceHigh),
                )
                Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                Text(option, style = type.body, color = colors.textPrimary)
            }
        }
        Spacer(Modifier.height(MrsadTokens.Spacing.sm))
        MrsadPrimaryButton(
            text = if (state.questionIndex == state.questions.lastIndex) "ثبت نتیجه" else "پرسش بعدی",
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
    MrsadSurface(
        Modifier.fillMaxWidth(),
        elevated = true,
        accent = ObsidianSpectrum.Lavender,
    ) {
        Text("نتیجهٔ خواندن", style = type.headline, color = colors.textPrimary)
        Text(
            if (state.saved) "در تحلیل‌ها ثبت شد" else "در حال ذخیره…",
            style = type.caption,
            color = if (state.saved) ObsidianSpectrum.Aqua else colors.textMuted,
        )
        Spacer(Modifier.height(MrsadTokens.Spacing.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MetricValue(
                value = formatInt(state.rawWpm, persianNumerals),
                label = "سرعت خام (WPM)",
                accent = ObsidianSpectrum.Lavender,
            )
            MetricValue(
                value = formatPercent(state.comprehensionPercent.toDouble(), persianNumerals),
                label = "درک مطلب",
                accent = ObsidianSpectrum.Aqua,
            )
            MetricValue(
                value = formatInt(state.effectiveWpm, persianNumerals),
                label = "سرعت مؤثر",
                accent = ObsidianSpectrum.BrightBlue,
            )
        }
        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        Text(
            "سرعت مؤثر = سرعت خام × درصد درک مطلب. این معیار فقط وقتی معتبر است که درک مطلب کافی باشد.",
            style = type.caption,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(MrsadTokens.Spacing.sm))
        MrsadSecondaryButton(
            text = "جلسهٔ جدید",
            icon = Icons.Outlined.Refresh,
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
