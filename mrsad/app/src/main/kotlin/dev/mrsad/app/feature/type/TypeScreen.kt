package dev.mrsad.app.feature.type

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.testing.MrsadTestHooks
import dev.mrsad.app.testing.MrsadTestTags
import dev.mrsad.app.ui.components.LocalBottomBarClearance
import dev.mrsad.app.ui.components.MetricValue
import dev.mrsad.app.ui.components.ModuleSculpture3D
import dev.mrsad.app.ui.components.ModuleSculptureType
import dev.mrsad.app.ui.components.MrsadChip
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
import dev.mrsad.app.ui.theme.formatNumber
import dev.mrsad.app.ui.theme.formatPercent
import kotlinx.coroutines.delay

/**
 * MODULE 01 — MRSAD TYPING (Master Prompt §4):
 * Primary Identity: Electric Blue (`#176ED4`) & Bright Blue (`#54B8FF`).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TypeScreen(
    container: AppContainer,
    settings: MrsadSettings,
) {
    val viewModel: TypeViewModel = viewModel(key = "type", factory = TypeViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboardInput = remember { KeyboardInputState() }
    val scrollState = rememberScrollState()
    val isCollapsed by remember { derivedStateOf { scrollState.value > 28 } }

    LaunchedEffect(state.phase) {
        if (state.phase == TypePhase.RUNNING && !MrsadTestHooks.isDeterministic) {
            delay(80)
            runCatching { focusRequester.requestFocus() }
        }
    }

    LaunchedEffect(Unit) {
        keyboardInput.events.collect { ch -> viewModel.onKey(ch) }
    }

    AmbientBackground(PageEnvironment.TYPE) {
        val bottomClearance = LocalBottomBarClearance.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomClearance),
        ) {
            ObsidianCollapsingTopBar(
                title = "MRSAD TYPE",
                subtitle = if (state.usePersian) "تمرین تایپ فارسی • WPM استاندارد" else "English typing practice • Standard WPM",
                collapsed = isCollapsed,
                spectralColor = ObsidianSpectrum.BrightBlue,
                leading = {
                    ModuleSculpture3D(
                        type = ModuleSculptureType.TYPING,
                        modifier = Modifier.size(if (isCollapsed) 36.dp else 48.dp),
                        animated = true,
                    )
                },
                trailing = {
                    MrsadChip(
                        text = if (state.usePersian) "فارسی" else "English",
                        tint = ObsidianSpectrum.BrightBlue,
                    )
                    MrsadChip(
                        text = "${formatInt(state.selectedDurationSeconds, settings.persianNumerals)} ثانیه",
                        tint = ObsidianSpectrum.ElectricBlue,
                        leadingIcon = Icons.Outlined.Timer,
                    )
                },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = MrsadTokens.Spacing.md)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
            ) {
                Spacer(Modifier.height(MrsadTokens.Spacing.xs))

                when (state.phase) {
                    TypePhase.SETUP -> SetupPanel(
                        state = state,
                        persianNumerals = settings.persianNumerals,
                        onLanguage = viewModel::setLanguage,
                        onDuration = viewModel::setDuration,
                        onSelectText = viewModel::selectTextById,
                        onStart = viewModel::start,
                    )
                    TypePhase.RUNNING, TypePhase.PAUSED -> RunningPanel(
                        state = state,
                        persianNumerals = settings.persianNumerals,
                        focusRequester = focusRequester,
                        keyboardInput = keyboardInput,
                        onKey = viewModel::onKey,
                        onBackspace = viewModel::onBackspace,
                        onPause = viewModel::pause,
                        onResume = viewModel::resume,
                        onRestart = viewModel::restart,
                        onFinishEarly = { viewModel.finish(completed = false) },
                    )
                    TypePhase.FINISHED -> ResultsPanel(
                        state = state,
                        persianNumerals = settings.persianNumerals,
                        onRestart = viewModel::resetToSetup,
                        onRetry = viewModel::restart,
                    )
                }
                Spacer(Modifier.height(MrsadTokens.Spacing.lg))
            }
        }
    }
}

@Composable
private fun SetupPanel(
    state: TypeUiState,
    persianNumerals: Boolean,
    onLanguage: (Boolean) -> Unit,
    onDuration: (Int) -> Unit,
    onSelectText: (String) -> Unit,
    onStart: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type

    MrsadSurface(
        Modifier
            .testTag(MrsadTestTags.TYPE_SETUP)
            .fillMaxWidth(),
        accent = ObsidianSpectrum.BrightBlue,
    ) {
        SectionHeader(title = "زبان تایپ")
        ObsidianSegmentedBar(
            items = listOf(
                SegmentedBarItem("fa", "فارسی", ObsidianSpectrum.BrightBlue),
                SegmentedBarItem("en", "English", ObsidianSpectrum.BrightBlue),
            ),
            selectedId = if (state.usePersian) "fa" else "en",
            onSelect = { onLanguage(it == "fa") },
        )

        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        SectionHeader(title = "مدت زمان جلسه")
        ObsidianSegmentedBar(
            items = durationPresets.map { seconds ->
                SegmentedBarItem(
                    id = seconds.toString(),
                    label = "${formatInt(seconds, persianNumerals)} ثانیه",
                    accent = ObsidianSpectrum.BrightBlue,
                )
            },
            selectedId = state.selectedDurationSeconds.toString(),
            onSelect = { id -> id.toIntOrNull()?.let(onDuration) },
        )

        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        SectionHeader(title = "متن تمرین")
        TypeTexts.forLanguage(state.usePersian).forEach { text ->
            val selected = text.id == state.selectedTextId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                    .background(
                        if (selected) {
                            ObsidianSpectrum.ElectricBlue.copy(alpha = 0.22f)
                        } else {
                            colors.backgroundElevated.copy(alpha = 0.55f)
                        },
                    )
                    .border(
                        1.dp,
                        if (selected) ObsidianSpectrum.BrightBlue else colors.border,
                        RoundedCornerShape(MrsadTokens.Radius.sm),
                    )
                    .clickable { onSelectText(text.id) }
                    .padding(MrsadTokens.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text.title, style = type.body.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
                    Text(
                        text.body.take(56) + "…",
                        style = type.caption,
                        color = colors.textMuted,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                MrsadChip(
                    text = "سطح ${formatInt(text.level, persianNumerals)}",
                    tint = if (selected) ObsidianSpectrum.BrightBlue else colors.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        MrsadPrimaryButton(
            text = "شروع تمرین",
            icon = Icons.Outlined.PlayArrow,
            onClick = onStart,
            accentColor = ObsidianSpectrum.BrightBlue,
            modifier = Modifier
                .testTag(MrsadTestTags.TYPE_START)
                .fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RunningPanel(
    state: TypeUiState,
    persianNumerals: Boolean,
    focusRequester: FocusRequester,
    keyboardInput: KeyboardInputState,
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onFinishEarly: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
        LiveMetric(
            modifier = Modifier.weight(1f),
            value = formatNumber(state.liveWpm.toInt().toString(), persianNumerals),
            label = "کلمه/دقیقه",
            highlight = true,
        )
        LiveMetric(
            modifier = Modifier.weight(1f),
            value = formatPercent(state.liveAccuracy, persianNumerals),
            label = "دقت",
            tint = ObsidianSpectrum.Aqua,
        )
        LiveMetric(
            modifier = Modifier.weight(1f),
            value = formatInt(state.errors, persianNumerals),
            label = "خطا",
            tint = if (state.errors > 0) colors.error else colors.textPrimary,
        )
        LiveMetric(
            modifier = Modifier.weight(1f),
            value = formatSeconds(state.remainingMillis, persianNumerals),
            label = "زمان",
        )
    }

    MrsadSurface(
        Modifier
            .testTag(MrsadTestTags.TYPE_RUNNING)
            .fillMaxWidth(),
        elevated = true,
        accent = ObsidianSpectrum.BrightBlue,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(state.title, style = type.caption, color = colors.textMuted, modifier = Modifier.weight(1f))
            Text(
                formatPercent(state.progress * 100.0, persianNumerals),
                style = type.caption,
                color = ObsidianSpectrum.BrightBlue,
            )
        }
        MrsadProgressBar(
            progress = state.progress,
            height = 4.dp,
            tint = ObsidianSpectrum.BrightBlue,
        )

        Spacer(Modifier.height(MrsadTokens.Spacing.sm))

        val annotated = buildTypedText(
            target = state.targetText,
            typedLength = state.typedPrefixLength,
            typedColor = colors.textPrimary,
            upcomingColor = colors.textMuted,
            nextColor = ObsidianSpectrum.BrightBlue,
            errorTint = if (state.mistakeTick > 0) colors.error else null,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(196.dp)
                .clip(RoundedCornerShape(MrsadTokens.Radius.md))
                .background(colors.background.copy(alpha = 0.72f))
                .border(1.dp, ObsidianSpectrum.ElectricBlue.copy(alpha = 0.42f), RoundedCornerShape(MrsadTokens.Radius.md))
                .padding(MrsadTokens.Spacing.md)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when {
                            event.key == Key.Backspace -> { onBackspace(); true }
                            event.utf16CodePoint != 0 -> {
                                val char = event.utf16CodePoint.toChar()
                                if (!char.isISOControl()) { onKey(char); true } else false
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .focusRequester(focusRequester),
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = keyboardInput.buffer,
                onValueChange = { newValue ->
                    keyboardInput.capture(newValue)
                },
                modifier = Modifier.fillMaxSize(),
                textStyle = type.reading.copy(color = Color.Transparent),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
            )
            Text(
                text = annotated,
                style = type.reading.copy(
                    textAlign = androidx.compose.ui.text.style.TextAlign.Justify,
                ),
                color = colors.textMuted,
            )
        }

        Spacer(Modifier.height(MrsadTokens.Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
            if (state.phase == TypePhase.RUNNING) {
                MrsadSecondaryButton(text = "توقف موقت", onClick = onPause, modifier = Modifier.weight(1f))
            } else {
                MrsadPrimaryButton(
                    text = "ادامه",
                    onClick = onResume,
                    accentColor = ObsidianSpectrum.BrightBlue,
                    modifier = Modifier.weight(1f),
                )
            }
            MrsadSecondaryButton(
                text = "پایان",
                onClick = onFinishEarly,
                modifier = Modifier
                    .testTag(MrsadTestTags.TYPE_FINISH)
                    .weight(1f),
            )
            MrsadSecondaryButton(
                text = "",
                icon = Icons.Outlined.Refresh,
                iconContentDescription = "شروع دوباره",
                onClick = onRestart,
                modifier = Modifier.testTag(MrsadTestTags.TYPE_RESTART),
            )
        }
        if (state.phase == TypePhase.PAUSED) {
            Text(
                "تمرین متوقف است. زمان‌سنج نیز متوقف می‌شود.",
                style = type.caption,
                color = colors.warning,
                modifier = Modifier.padding(top = MrsadTokens.Spacing.xs),
            )
        }
    }
}

@Composable
private fun LiveMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    tint: Color? = null,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
            .background(colors.surface)
            .border(
                1.dp,
                if (highlight) ObsidianSpectrum.BrightBlue.copy(alpha = 0.55f) else colors.border,
                RoundedCornerShape(MrsadTokens.Radius.sm),
            )
            .padding(vertical = MrsadTokens.Spacing.xs, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = type.metric.copy(fontSize = type.title.fontSize, lineHeight = type.title.lineHeight),
            color = tint ?: if (highlight) ObsidianSpectrum.BrightBlue else colors.textPrimary,
            maxLines = 1,
        )
        Text(label, style = type.caption, color = colors.textMuted, maxLines = 1)
    }
}

@Composable
private fun ResultsPanel(
    state: TypeUiState,
    persianNumerals: Boolean,
    onRestart: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val result = state.result ?: return

    MrsadSurface(
        Modifier
            .testTag(MrsadTestTags.TYPE_RESULTS)
            .fillMaxWidth(),
        elevated = true,
        accent = ObsidianSpectrum.BrightBlue,
    ) {
        Text(
            if (result.completed) "تمرین کامل شد" else "زمان تمام شد",
            style = type.headline,
            color = colors.textPrimary,
        )
        Text(
            "نتیجه به‌صورت خودکار در تحلیل‌ها ثبت شد" + if (state.savedSessionId == null && state.isSaving) " — در حال ذخیره…" else "",
            style = type.caption,
            color = if (state.savedSessionId != null) ObsidianSpectrum.Aqua else colors.textMuted,
        )

        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MetricValue(
                value = formatNumber(result.wordWpm.toInt().toString(), persianNumerals),
                label = "کلمه در دقیقه",
                accent = ObsidianSpectrum.BrightBlue,
            )
            MetricValue(
                value = formatNumber(result.cpm.toInt().toString(), persianNumerals),
                label = "نویسه در دقیقه",
            )
            MetricValue(
                value = formatPercent(result.accuracy, persianNumerals, decimals = 1),
                label = "دقت کل",
                accent = ObsidianSpectrum.Aqua,
            )
        }
        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MetricValue(
                value = formatInt(result.correctChars, persianNumerals),
                label = "نویسهٔ درست",
                size = type.title.fontSize,
            )
            MetricValue(
                value = formatInt(result.errorKeystrokes, persianNumerals),
                label = "خطا",
                size = type.title.fontSize,
            )
            MetricValue(
                value = formatSeconds(result.elapsedMillis, persianNumerals),
                label = "زمان",
                size = type.title.fontSize,
            )
        }

        val mistakes = result.topMistakes()
        if (mistakes.isNotEmpty()) {
            Spacer(Modifier.height(MrsadTokens.Spacing.xs))
            Text("دشوارترین حروف این جلسه", style = type.label, color = colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
                mistakes.forEach { (char, count) ->
                    MrsadChip(
                        text = "«$char» ×${formatInt(count, persianNumerals)}",
                        tint = colors.error,
                    )
                }
            }
        }

        Spacer(Modifier.height(MrsadTokens.Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
            MrsadPrimaryButton(
                text = "تمرین دوباره",
                icon = Icons.Outlined.Refresh,
                onClick = onRetry,
                accentColor = ObsidianSpectrum.BrightBlue,
                modifier = Modifier
                    .testTag(MrsadTestTags.TYPE_RETRY)
                    .weight(1f),
            )
            MrsadSecondaryButton(
                text = "انتخاب متن دیگر",
                onClick = onRestart,
                modifier = Modifier
                    .testTag(MrsadTestTags.TYPE_BACK_TO_SETUP)
                    .weight(1f),
            )
        }
    }
}

private fun buildTypedText(
    target: String,
    typedLength: Int,
    typedColor: Color,
    upcomingColor: Color,
    nextColor: Color,
    errorTint: Color?,
): AnnotatedString = buildAnnotatedString {
    val safeLength = typedLength.coerceIn(0, target.length)
    if (safeLength > 0) {
        withStyle(SpanStyle(color = typedColor, fontWeight = FontWeight.Medium)) {
            append(target.substring(0, safeLength))
        }
    }
    if (safeLength < target.length) {
        withStyle(
            SpanStyle(
                color = nextColor,
                background = (errorTint ?: nextColor).copy(alpha = 0.22f),
                fontWeight = FontWeight.SemiBold,
            ),
        ) {
            append(target[safeLength])
        }
        if (safeLength + 1 < target.length) {
            withStyle(SpanStyle(color = upcomingColor)) {
                append(target.substring(safeLength + 1))
            }
        }
    }
}

private fun formatSeconds(millis: Long, persianNumerals: Boolean): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    val minutes = seconds / 60
    val secs = seconds % 60
    val text = if (minutes > 0) "$minutes:${secs.toString().padStart(2, '0')}" else "$secs"
    return if (persianNumerals) dev.mrsad.core.text.PersianText.toPersianDigits(text) else text
}
