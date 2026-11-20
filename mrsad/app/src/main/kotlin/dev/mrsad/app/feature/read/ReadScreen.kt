package dev.mrsad.app.feature.read

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import dev.mrsad.app.ui.components.MrsadChip
import dev.mrsad.app.ui.components.MrsadPrimaryButton
import dev.mrsad.app.ui.components.MrsadProgressBar
import dev.mrsad.app.ui.components.MrsadSecondaryButton
import dev.mrsad.app.ui.components.MrsadSurface
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.components.MetricValue
import dev.mrsad.app.ui.components.SectionHeader
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.formatInt
import dev.mrsad.app.ui.theme.formatPercent

/**
 * MRSAD READ — Flow Space.
 *
 * RSVP reading whose speed is measured from real elapsed time (monotonic
 * clock, pauses excluded) and always paired with a comprehension check.
 * A speed record without comprehension is never accepted as progress.
 */
@Composable
fun ReadScreen(
    container: AppContainer,
    settings: MrsadSettings,
) {
    val viewModel: ReadViewModel = viewModel(key = "read", factory = ReadViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type

    // The presentation ticker is driven by the ViewModel; the UI only reflects.
    LaunchedEffect(state.phase) {
        if (state.phase == ReadPhase.READING) viewModel.runPresentationClock()
    }

    AmbientBackground(PageEnvironment.READ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MrsadTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
        ) {
            Text("MRSAD READ", style = type.headline.copy(letterSpacing = 1.sp), color = colors.textPrimary)
            Text("خواندن سریع همراه با درک مطلب", style = type.caption, color = colors.textSecondary)

            when (state.phase) {
                ReadPhase.SETUP -> ReadSetup(
                    state = state,
                    persianNumerals = settings.persianNumerals,
                    onWpm = viewModel::setWpm,
                    onWords = viewModel::setWordsPerDisplay,
                    onText = viewModel::selectText,
                    onStart = viewModel::start,
                )
                ReadPhase.READING -> ReadStage(state = state, persianNumerals = settings.persianNumerals, onPause = viewModel::pause, onResume = viewModel::resume, onStop = viewModel::stopEarly)
                ReadPhase.PAUSED -> ReadStage(state = state, persianNumerals = settings.persianNumerals, onPause = viewModel::pause, onResume = viewModel::resume, onStop = viewModel::stopEarly)
                ReadPhase.QUIZ -> ReadQuiz(state = state, persianNumerals = settings.persianNumerals, onAnswer = viewModel::answer, onSubmit = viewModel::submitQuiz)
                ReadPhase.RESULT -> ReadResult(state = state, persianNumerals = settings.persianNumerals, onRestart = viewModel::reset)
            }
            Spacer(Modifier.height(120.dp))
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

    MrsadSurface(Modifier.fillMaxWidth()) {
        SectionHeader(title = "متن")
        // Grouped by difficulty tier so the corpus reads as a progression
        // rather than a flat list.
        ReadTexts.all.groupBy { it.level }.toSortedMap().forEach { (level, texts) ->
            Text(
                text = when (level) {
                    1 -> "آسان — جمله‌های کوتاه و ساختار روشن"
                    2 -> "متوسط — جمله‌های ترکیبی"
                    else -> "دشوار — متن متراکم با وابسته‌های پیاپی"
                },
                style = type.caption,
                color = colors.textMuted,
                modifier = Modifier.padding(
                    top = MrsadTokens.Spacing.xs,
                    bottom = MrsadTokens.Spacing.xxs,
                ),
            )
            texts.forEach { text ->
                val selected = text.id == state.selectedTextId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                        .background(if (selected) colors.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(MrsadTokens.Spacing.sm)
                        .clip(RoundedCornerShape(MrsadTokens.Radius.sm)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.MenuBook, null, tint = colors.metalMid, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                            .clickable { onText(text.id) }
                    ) {
                        Text(text.title, style = type.body, color = colors.textPrimary, fontWeight = FontWeight.Medium)
                        Text(
                            "${formatInt(text.wordCount, persianNumerals)} کلمه — ${text.category}",
                            style = type.caption,
                            color = colors.textMuted,
                        )
                    }
                    if (selected) MrsadChip(text = "انتخاب‌شده", tint = colors.accent)
                }
            }
        }
    }

    MrsadSurface(Modifier.fillMaxWidth()) {
        SectionHeader(title = "سرعت خواندن")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Speed, null, tint = colors.metalMid, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(MrsadTokens.Spacing.xs))
            Text(
                "${formatInt(state.wpm, persianNumerals)} کلمه در دقیقه",
                style = type.body,
                color = colors.textPrimary,
            )
        }
        Slider(
            value = state.wpm.toFloat(),
            onValueChange = { onWpm(it.toInt()) },
            valueRange = 120f..900f,
            steps = 25,
            colors = SliderDefaults.colors(
                thumbColor = colors.metalLight,
                activeTrackColor = colors.metalMid,
                inactiveTrackColor = colors.surfaceHigh,
            ),
        )
        Text("تعداد کلمات در هر نمایش", style = type.label, color = colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
            listOf(1, 2, 3).forEach { words ->
                val selected = state.wordsPerDisplay == words
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                        .background(if (selected) colors.accentSoft else colors.surfaceHigh.copy(alpha = 0.5f))
                        .clickable { onWords(words) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${formatInt(words, persianNumerals)} کلمه",
                        style = type.label,
                        color = if (selected) colors.textPrimary else colors.textSecondary,
                    )
                }
            }
        }
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

    MrsadSurface(Modifier.fillMaxWidth(), elevated = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MrsadChip(text = "${formatInt(state.wpm, persianNumerals)} کلمه/دقیقه", tint = colors.metalMid)
            Spacer(Modifier.weight(1f))
            MrsadChip(text = "کلمهٔ ${formatInt(state.chunkIndex + 1, persianNumerals)} از ${formatInt(state.chunkCount, persianNumerals)}", tint = colors.textSecondary)
        }
        MrsadProgressBar(progress = state.progress, height = 3.dp)
        Spacer(Modifier.height(MrsadTokens.Spacing.md))

        // RSVP focus stage with pivot alignment.
        Box(
            Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(MrsadTokens.Radius.lg))
                .background(colors.background.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // pivot guide lines: the ORP letter sits exactly between them
                    Box(
                        Modifier
                            .width(0.5.dp)
                            .height(44.dp)
                            .background(colors.border)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = state.currentChunkText,
                        style = type.display.copy(fontSize = 36.sp, lineHeight = 52.sp),
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.width(2.dp))
                    Box(
                        Modifier
                            .width(0.5.dp)
                            .height(44.dp)
                            .background(colors.border)
                    )
                }
                Spacer(Modifier.height(MrsadTokens.Spacing.xs))
                Text(
                    if (paused) "مکث — زمان‌سنج متوقف است" else "با چشم، بدون حرکت لب، بخوانید",
                    style = type.caption,
                    color = if (paused) colors.warning else colors.textMuted,
                )
            }
        }

        Spacer(Modifier.height(MrsadTokens.Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
            if (paused) {
                MrsadPrimaryButton(text = "ادامه", onClick = onResume, modifier = Modifier.weight(1f))
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

    MrsadSurface(Modifier.fillMaxWidth(), elevated = true) {
        Text("ارزیابی درک مطلب", style = type.title, color = colors.textPrimary)
        Text(
            "پرسش ${formatInt(state.questionIndex + 1, persianNumerals)} از ${formatInt(state.questions.size, persianNumerals)}",
            style = type.caption,
            color = colors.textMuted,
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
                    .background(if (selected) colors.accentSoft else colors.surfaceHigh.copy(alpha = 0.4f))
                    .clickable { onAnswer(index) }
                    .padding(MrsadTokens.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (selected) colors.metalLight else colors.surfaceHigh)
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
    MrsadSurface(Modifier.fillMaxWidth(), elevated = true) {
        Text("نتیجهٔ خواندن", style = type.headline, color = colors.textPrimary)
        Text(
            if (state.saved) "در تحلیل‌ها ثبت شد" else "در حال ذخیره…",
            style = type.caption,
            color = if (state.saved) colors.success else colors.textMuted,
        )
        Spacer(Modifier.height(MrsadTokens.Spacing.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MetricValue(value = formatInt(state.rawWpm, persianNumerals), label = "سرعت خام (کلمه/دقیقه)")
            MetricValue(value = formatPercent(state.comprehensionPercent.toDouble(), persianNumerals), label = "درک مطلب")
            MetricValue(value = formatInt(state.effectiveWpm, persianNumerals), label = "سرعت مؤثر")
        }
        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        Text(
            "سرعت مؤثر = سرعت خام × درصد درک مطلب. این معیار فقط وقتی معتبر است که درک مطلب کافی باشد.",
            style = type.caption,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(MrsadTokens.Spacing.sm))
        MrsadSecondaryButton(text = "جلسهٔ جدید", icon = Icons.Outlined.Refresh, onClick = onRestart, modifier = Modifier.fillMaxWidth())
    }
}
