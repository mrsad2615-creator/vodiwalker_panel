package dev.mrsad.app.feature.type

import android.os.Build
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.mrsad.app.ui.components.ModuleSculpture3D
import dev.mrsad.app.ui.components.ModuleSculptureType
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
import dev.mrsad.app.ui.theme.formatNumber
import dev.mrsad.app.ui.theme.formatPercent
import kotlinx.coroutines.delay

/**
 * MRSAD TYPE — Titanium Surface.
 *
 * The typing surface is a single focused column: live metrics, the target text
 * rendered as an annotated string (typed prefix in silver, next key highlighted)
 * and one hidden Compose text-input handler that accepts both hardware keys and
 * the Android virtual keyboard (including full IME composition for Persian).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TypeScreen(
    container: AppContainer,
    settings: MrsadSettings,
) {
    val viewModel: TypeViewModel = viewModel(key = "type", factory = TypeViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val focusRequester = remember { FocusRequester() }
    val keyboardInput = remember { KeyboardInputState() }

    // Auto-focus the hidden input when a run starts so hardware + soft keyboards work.
    //
    // Deterministic mode skips the focus request: a focused text field starts
    // a blinking-cursor job, and the deterministic suite must not depend on
    // any recurring UI work. The product behaviour is unchanged.
    LaunchedEffect(state.phase) {
        if (state.phase == TypePhase.RUNNING && !MrsadTestHooks.isDeterministic) {
            delay(80)
            runCatching { focusRequester.requestFocus() }
        }
    }

    // Drain the soft-keyboard buffer into the engine, in order.
    LaunchedEffect(Unit) {
        keyboardInput.events.collect { ch -> viewModel.onKey(ch) }
    }

    AmbientBackground(PageEnvironment.TYPE) {
        // The floating navigation bar is drawn over this screen, so the scroll
        // viewport stops above it: otherwise the "start" button can come to
        // rest underneath the bar and the tap would start a different screen.
        val bottomClearance = LocalBottomBarClearance.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MrsadTokens.Spacing.md)
                .padding(bottom = bottomClearance)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
        ) {
            Spacer(Modifier.height(MrsadTokens.Spacing.sm))
            HeaderRow(state = state, persianNumerals = settings.persianNumerals)

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
            // The scroll viewport already stops above the floating bar.
            Spacer(Modifier.height(MrsadTokens.Spacing.lg))
        }
    }
}

@Composable
private fun HeaderRow(state: TypeUiState, persianNumerals: Boolean) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ModuleSculpture3D(
            type = ModuleSculptureType.TYPING,
            modifier = Modifier.size(54.dp),
        )
        Spacer(Modifier.width(MrsadTokens.Spacing.xs))
        Column(Modifier.weight(1f)) {
            Text("MRSAD TYPE", style = type.headline.copy(letterSpacing = 1.sp), color = colors.textPrimary)
            Text(
                if (state.usePersian) "تمرین تایپ فارسی • WPM استاندارد" else "English typing practice • Standard WPM",
                style = type.caption,
                color = colors.textSecondary,
            )
        }
        MrsadChip(
            text = if (state.usePersian) "فارسی" else "English",
            tint = colors.metalMid,
        )
        Spacer(Modifier.width(MrsadTokens.Spacing.xxs))
        MrsadChip(
            text = "${formatInt(state.selectedDurationSeconds, persianNumerals)} ثانیه",
            tint = colors.textSecondary,
            leadingIcon = Icons.Outlined.Timer,
        )
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
    ) {
        SectionHeader(title = "زبان")
        Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
            SelectableChoice(
                text = "فارسی",
                selected = state.usePersian,
                modifier = Modifier.weight(1f),
                onClick = { onLanguage(true) },
            )
            SelectableChoice(
                text = "English",
                selected = !state.usePersian,
                modifier = Modifier.weight(1f),
                onClick = { onLanguage(false) },
            )
        }

        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        SectionHeader(title = "مدت زمان")
        Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
            durationPresets.forEach { seconds ->
                SelectableChoice(
                    text = "${formatInt(seconds, persianNumerals)} ثانیه",
                    selected = state.selectedDurationSeconds == seconds,
                    modifier = Modifier.weight(1f),
                    onClick = { onDuration(seconds) },
                )
            }
        }

        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        SectionHeader(title = "متن تمرین")
        TypeTexts.forLanguage(state.usePersian).forEach { text ->
            val selected = text.id == state.selectedTextId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                    .border(
                        1.dp,
                        if (selected) colors.borderStrong else Color.Transparent,
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
                    tint = colors.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        MrsadPrimaryButton(
            text = "شروع تمرین",
            icon = Icons.Outlined.PlayArrow,
            onClick = onStart,
            modifier = Modifier
                .testTag(MrsadTestTags.TYPE_START)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun SelectableChoice(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
            .background(if (selected) colors.accentSoft else colors.surfaceHigh.copy(alpha = 0.5f))
            .border(
                1.dp,
                if (selected) colors.borderStrong else colors.border,
                RoundedCornerShape(MrsadTokens.Radius.sm),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = type.label.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) colors.textPrimary else colors.textSecondary,
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

    // Live metrics strip.
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
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(state.title, style = type.caption, color = colors.textMuted, modifier = Modifier.weight(1f))
            Text(
                "${formatPercent(state.progress * 100.0, persianNumerals)}",
                style = type.caption,
                color = colors.metalMid,
            )
        }
        MrsadProgressBar(progress = state.progress, height = 3.dp)

        Spacer(Modifier.height(MrsadTokens.Spacing.sm))

        // The target text: typed prefix perfectly mirrored from the engine cursor.
        val annotated = buildTypedText(
            target = state.targetText,
            typedLength = state.typedPrefixLength,
            typedColor = colors.textPrimary,
            upcomingColor = colors.textMuted,
            nextColor = colors.accent,
            errorTint = if (state.mistakeTick > 0) colors.error else null,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(196.dp)
                .clip(RoundedCornerShape(MrsadTokens.Radius.md))
                .background(colors.background.copy(alpha = if (colors.isLight) 0.35f else 0.55f))
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
                    // The hidden field only feeds new characters into the engine and
                    // never displays or retains them: matching is ours alone.
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
                    // Persian shaping is handled by the platform; justify keeps
                    // long lines elegant on tablets.
                ),
                color = colors.textMuted,
            )
        }

        Spacer(Modifier.height(MrsadTokens.Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
            if (state.phase == TypePhase.RUNNING) {
                MrsadSecondaryButton(text = "توقف موقت", onClick = onPause, modifier = Modifier.weight(1f))
            } else {
                MrsadPrimaryButton(text = "ادامه", onClick = onResume, modifier = Modifier.weight(1f))
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
                // The button shows only a glyph, so it has to carry its own
                // name for screen readers.
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
            .background(colors.surface.copy(alpha = 0.6f))
            .padding(vertical = MrsadTokens.Spacing.xs, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = type.metric.copy(fontSize = type.title.fontSize, lineHeight = type.title.lineHeight),
            color = tint ?: if (highlight) colors.metalLight else colors.textPrimary,
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
    ) {
        Text(
            if (result.completed) "تمرین کامل شد" else "زمان تمام شد",
            style = type.headline,
            color = colors.textPrimary,
        )
        Text(
            "نتیجه به‌صورت خودکار در تحلیل‌ها ثبت شد" + if (state.savedSessionId == null && state.isSaving) " — در حال ذخیره…" else "",
            style = type.caption,
            color = if (state.savedSessionId != null) colors.success else colors.textMuted,
        )

        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MetricValue(
                value = formatNumber(result.wordWpm.toInt().toString(), persianNumerals),
                label = "کلمه در دقیقه",
            )
            MetricValue(
                value = formatNumber(result.cpm.toInt().toString(), persianNumerals),
                label = "نویسه در دقیقه",
            )
            MetricValue(
                value = formatPercent(result.accuracy, persianNumerals, decimals = 1),
                label = "دقت کل",
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
                        tint = colors.warning,
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

/**
 * Builds the annotated target: typed block (completed), next character
 * (highlighted), remainder (muted). Uses a real span structure so Persian
 * bidi shaping stays intact across the boundaries.
 */
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
                background = (errorTint ?: nextColor).copy(alpha = 0.14f),
                fontWeight = FontWeight.SemiBold,
            )
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
