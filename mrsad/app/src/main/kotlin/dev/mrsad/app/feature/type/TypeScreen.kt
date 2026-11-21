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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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
import dev.mrsad.core.text.PersianText
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
                title = "سرعت و دقت تایپ",
                subtitle = if (state.usePersian) "تمرین دهانگشتی استاندارد ملی (ISIRI 9147)" else "Standard QWERTY Touch Typing",
                collapsed = isCollapsed,
                spectralColor = ObsidianSpectrum.BrightBlue,
                onBack = if (state.phase != TypePhase.SETUP) {
                    { viewModel.resetToSetup() }
                } else {
                    null
                },
                leading = {
                    ObsidianIconBox(
                        size = if (isCollapsed) 38.dp else 48.dp,
                        accent = ObsidianSpectrum.BrightBlue,
                        elevated = true,
                    ) {
                        ModuleSculpture3D(
                            type = ModuleSculptureType.TYPING,
                            modifier = Modifier.size(if (isCollapsed) 28.dp else 36.dp),
                            animated = true,
                        )
                    }
                },
                trailing = {
                    MrsadChip(
                        text = if (state.usePersian) "فارسی" else "EN",
                        tint = ObsidianSpectrum.BrightBlue,
                        compact = true,
                    )
                    MrsadChip(
                        text = "${formatInt(state.selectedDurationSeconds, settings.persianNumerals)} ثانیه",
                        tint = ObsidianSpectrum.ElectricBlue,
                        leadingIcon = Icons.Outlined.Timer,
                        compact = true,
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
                        onLevelFilter = viewModel::setLevelFilter,
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
    onLevelFilter: (Int) -> Unit,
    onDuration: (Int) -> Unit,
    onSelectText: (String) -> Unit,
    onStart: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val filteredTexts = TypeTexts.forLanguage(state.usePersian, state.selectedLevelFilter)

    MrsadSurface(
        Modifier
            .testTag(MrsadTestTags.TYPE_SETUP)
            .fillMaxWidth(),
        accent = ObsidianSpectrum.BrightBlue,
    ) {
        SectionHeader(title = "۱. زبان و چیدمان صفحه‌کلید")
        ObsidianSegmentedBar(
            items = listOf(
                SegmentedBarItem("fa", "فارسی (استاندارد)", ObsidianSpectrum.BrightBlue),
                SegmentedBarItem("en", "English (QWERTY)", ObsidianSpectrum.BrightBlue),
            ),
            selectedId = if (state.usePersian) "fa" else "en",
            onSelect = { onLanguage(it == "fa") },
        )

        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        SectionHeader(title = "۲. مدت زمان استاندارد جلسه")
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
        SectionHeader(title = "۳. سطح دشواری و متن تمرین")
        ObsidianSegmentedBar(
            items = listOf(0, 1, 2, 3, 4, 5).map { lvl ->
                SegmentedBarItem(
                    id = lvl.toString(),
                    label = if (lvl == 0) "همه" else "سطح ${formatInt(lvl, persianNumerals)}",
                    accent = ObsidianSpectrum.BrightBlue,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MrsadTokens.Radius.md))
                    .background(
                        if (selected) {
                            ObsidianSpectrum.ElectricBlue.copy(alpha = 0.22f)
                        } else {
                            colors.backgroundElevated.copy(alpha = 0.65f)
                        },
                    )
                    .border(
                        1.dp,
                        if (selected) ObsidianSpectrum.BrightBlue else colors.border,
                        RoundedCornerShape(MrsadTokens.Radius.md),
                    )
                    .clickable { onSelectText(text.id) }
                    .padding(MrsadTokens.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Strict RTL Right Anchor: 44dp Icon Box with Level Badge
                ObsidianIconBox(
                    size = 44.dp,
                    accent = if (selected) ObsidianSpectrum.BrightBlue else colors.borderStrong,
                    elevated = selected,
                ) {
                    Text(
                        text = formatInt(text.level, persianNumerals),
                        style = type.title.copy(fontWeight = FontWeight.Bold),
                        color = if (selected) ObsidianSpectrum.BrightBlue else colors.textSecondary,
                    )
                }

                Spacer(Modifier.width(MrsadTokens.Spacing.xs))

                // Middle: Title, Category & Preview
                Column(Modifier.weight(1f)) {
                    Text(
                        text = text.title,
                        style = type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${text.categoryFa} • ${formatInt(wordCount, persianNumerals)} کلمه",
                        style = type.caption,
                        color = ObsidianSpectrum.BrightBlue.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = text.body,
                        style = type.caption,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(MrsadTokens.Spacing.xs))

                // Left Anchor: Target WPM Benchmark Chip
                MrsadChip(
                    text = "هدف ${formatInt(text.targetWpm, persianNumerals)}",
                    tint = if (selected) ObsidianSpectrum.BrightBlue else colors.textSecondary,
                    compact = true,
                )
            }
        }

        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        MrsadPrimaryButton(
            text = "شروع تمرین تایپ",
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
    val keyboardController = LocalSoftwareKeyboardController.current
    val textScrollState = rememberScrollState()

    val annotated = remember(
        state.targetText,
        state.typedPrefixLength,
        state.mistakeTick,
        colors.textPrimary,
        colors.textMuted,
    ) {
        buildTargetAnnotatedString(
            target = state.targetText,
            cursor = state.typedPrefixLength,
            hasMistake = state.mistakeChar != null,
            correctColor = ObsidianSpectrum.Aqua,
            cursorColor = ObsidianSpectrum.BrightBlue,
            mistakeColor = ObsidianSpectrum.Pink,
            remainingColor = colors.textPrimary.copy(alpha = 0.78f),
        )
    }

    // Automatically scroll the passage viewport as the user progresses through longer texts
    LaunchedEffect(state.typedPrefixLength, textScrollState.maxValue) {
        if (textScrollState.maxValue > 0 && state.targetText.isNotEmpty()) {
            val fraction = (state.typedPrefixLength.toFloat() / state.targetText.length).coerceIn(0f, 1f)
            val targetScroll = ((fraction - 0.22f).coerceAtLeast(0f) * textScrollState.maxValue).toInt()
            textScrollState.animateScrollTo(targetScroll)
        }
    }

    // Live Telemetry Bar
    MrsadSurface(
        Modifier
            .testTag(MrsadTestTags.TYPE_RUNNING)
            .fillMaxWidth(),
        accent = ObsidianSpectrum.BrightBlue,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricValue(
                value = formatNumber("%.0f".format(state.liveWpm), persianNumerals),
                label = "سرعت (WPM)",
                size = type.headline.fontSize,
                accent = ObsidianSpectrum.BrightBlue,
            )
            MetricValue(
                value = formatPercent(state.liveAccuracy, persianNumerals, decimals = 0),
                label = "دقت کلیدها",
                size = type.headline.fontSize,
                accent = if (state.liveAccuracy >= 94.0) ObsidianSpectrum.Aqua else ObsidianSpectrum.Pink,
            )
            MetricValue(
                value = formatInt(state.errors, persianNumerals),
                label = "خطاها",
                size = type.headline.fontSize,
                accent = if (state.errors == 0) ObsidianSpectrum.Aqua else ObsidianSpectrum.Pink,
            )
            MetricValue(
                value = formatInt((state.remainingMillis / 1000L).toInt(), persianNumerals),
                label = "ثانیه مانده",
                size = type.headline.fontSize,
                accent = ObsidianSpectrum.Lavender,
            )
        }
        MrsadProgressBar(progress = state.progress, tint = ObsidianSpectrum.BrightBlue)
    }

    // Typing Viewport + Ergonomic Finger Guide
    MrsadSurface(
        modifier = Modifier.fillMaxWidth(),
        elevated = true,
        accent = ObsidianSpectrum.ElectricBlue,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ObsidianIconBox(size = 38.dp, accent = ObsidianSpectrum.BrightBlue) {
                    MetalIcon(
                        MrsadIcons.Type,
                        accentOverride = ObsidianSpectrum.BrightBlue,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                Column {
                    Text(
                        state.title,
                        style = type.label.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${TypeTexts.levelLabelFa(state.textLevel)} • هدف: ${formatInt(state.targetWpm, persianNumerals)} WPM",
                        style = type.caption,
                        color = colors.textSecondary,
                    )
                }
            }
            if (state.phase == TypePhase.PAUSED) {
                MrsadChip(text = "متوقف", tint = ObsidianSpectrum.Pink, compact = true)
            } else {
                MrsadChip(
                    text = "${formatInt(state.typedPrefixLength, persianNumerals)}/${formatInt(state.targetText.length, persianNumerals)}",
                    tint = ObsidianSpectrum.BrightBlue,
                    compact = true,
                )
            }
        }

        // Scrollable Target Text Box + Invisible Synchronized IME Input
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(208.dp)
                .clip(RoundedCornerShape(MrsadTokens.Radius.md))
                .background(colors.background.copy(alpha = 0.76f))
                .border(
                    width = 1.dp,
                    color = if (state.mistakeChar != null) ObsidianSpectrum.Pink else ObsidianSpectrum.BrightBlue.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(MrsadTokens.Radius.md),
                )
                .clickable {
                    runCatching {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                }
                .padding(MrsadTokens.Spacing.md),
        ) {
            BasicTextField(
                value = keyboardInput.buffer,
                onValueChange = { newValue ->
                    keyboardInput.capture(newValue)
                },
                textStyle = type.reading.copy(color = Color.Transparent),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.01f)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Backspace -> {
                                onBackspace()
                                true
                            }
                            else -> {
                                val cp = event.utf16CodePoint
                                if (cp != 0 && !Character.isISOControl(cp)) {
                                    onKey(cp.toChar())
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                    },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(textScrollState),
            ) {
                Text(
                    text = annotated,
                    style = type.bodyLarge.copy(
                        fontSize = 20.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }

        // Live Ergonomic Finger & Expected Key Guide Bar
        val expectedLabel = when (val exp = state.expectedChar) {
            null -> "پایان متن"
            ' ' -> "فاصله (Space)"
            PersianText.ZWNJ -> "نیم‌فاصله"
            else -> "«$exp»"
        }
        val fingerGuide = TypeTexts.fingerHintFa(state.expectedChar, state.usePersian)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                .background(
                    if (state.mistakeChar != null) {
                        ObsidianSpectrum.Pink.copy(alpha = 0.14f)
                    } else {
                        colors.backgroundElevated
                    },
                )
                .border(
                    1.dp,
                    if (state.mistakeChar != null) ObsidianSpectrum.Pink.copy(alpha = 0.55f) else colors.border,
                    RoundedCornerShape(MrsadTokens.Radius.sm),
                )
                .padding(horizontal = MrsadTokens.Spacing.sm, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (state.mistakeChar != null) {
                        "خطا در کلید «${state.mistakeChar}» — کلید درست: $expectedLabel"
                    } else {
                        "کلید بعدی: $expectedLabel"
                    },
                    style = type.label.copy(fontWeight = FontWeight.SemiBold),
                    color = if (state.mistakeChar != null) ObsidianSpectrum.Pink else ObsidianSpectrum.BrightBlue,
                )
                Text(
                    text = fingerGuide,
                    style = type.caption,
                    color = colors.textSecondary,
                )
            }
            MrsadChip(
                text = "کیبورد",
                tint = ObsidianSpectrum.BrightBlue,
                compact = true,
                onClick = {
                    runCatching {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
        ) {
            if (state.phase == TypePhase.RUNNING) {
                MrsadSecondaryButton(
                    text = "توقف موقت",
                    icon = Icons.Outlined.Pause,
                    onClick = onPause,
                    modifier = Modifier.weight(1f),
                )
            } else {
                MrsadPrimaryButton(
                    text = "ادامه",
                    icon = Icons.Outlined.PlayArrow,
                    onClick = onResume,
                    accentColor = ObsidianSpectrum.BrightBlue,
                    modifier = Modifier.weight(1f),
                )
            }
            MrsadSecondaryButton(
                text = "از نو",
                icon = Icons.Outlined.Refresh,
                onClick = onRestart,
                modifier = Modifier
                    .testTag(MrsadTestTags.TYPE_RESTART)
                    .weight(1f),
            )
            MrsadSecondaryButton(
                text = "پایان",
                onClick = onFinishEarly,
                modifier = Modifier
                    .testTag(MrsadTestTags.TYPE_FINISH)
                    .weight(1f),
            )
        }
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

    val gradeLabel = when {
        result.wpm >= state.targetWpm && result.accuracy >= 96.0 -> "استادکار (فراتر از هدف)"
        result.wpm >= state.targetWpm * 0.85 && result.accuracy >= 92.0 -> "پیشرفته و استاندارد"
        result.accuracy >= 90.0 -> "دقت خوب • نیازمند افزایش ریتم"
        else -> "نیازمند تثبیت دقت کلیدها"
    }

    MrsadSurface(
        Modifier
            .testTag(MrsadTestTags.TYPE_RESULTS)
            .fillMaxWidth(),
        elevated = true,
        accent = ObsidianSpectrum.BrightBlue,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ObsidianIconBox(size = 48.dp, accent = ObsidianSpectrum.BrightBlue, elevated = true) {
                    MetalIcon(MrsadIcons.Trophy, accentOverride = ObsidianSpectrum.BrightBlue, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                Column {
                    Text("گزارش تحلیلی جلسهٔ تایپ", style = type.title.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    Text(
                        when {
                            state.savedSessionId != null -> "در پایگاه دادهٔ آفلاین ثبت شد"
                            state.isSaving -> "در حال ذخیره…"
                            else -> "بدون ثبت کلید (ذخیره نشد)"
                        },
                        style = type.caption,
                        color = if (state.savedSessionId != null) ObsidianSpectrum.Aqua else colors.textMuted,
                    )
                }
            }
            MrsadChip(
                text = gradeLabel,
                tint = if (result.accuracy >= 92.0) ObsidianSpectrum.Aqua else ObsidianSpectrum.Pink,
                compact = true,
            )
        }

        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MetricValue(
                value = formatNumber("%.1f".format(result.wpm), persianNumerals),
                label = "سرعت استاندارد (WPM)",
                accent = ObsidianSpectrum.BrightBlue,
            )
            MetricValue(
                value = formatPercent(result.accuracy, persianNumerals, decimals = 1),
                label = "دقت کلیدها",
                accent = ObsidianSpectrum.Aqua,
            )
            MetricValue(
                value = formatNumber("%.0f".format(result.cpm), persianNumerals),
                label = "حرف بر دقیقه (CPM)",
                accent = ObsidianSpectrum.Lavender,
            )
        }

        MrsadDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MetricValue(
                value = formatInt(result.correctChars, persianNumerals),
                label = "حروف صحیح",
                size = type.title.fontSize,
            )
            MetricValue(
                value = formatInt(result.errorKeystrokes, persianNumerals),
                label = "خطاها",
                size = type.title.fontSize,
                accent = if (result.errorKeystrokes == 0) ObsidianSpectrum.Aqua else ObsidianSpectrum.Pink,
            )
            MetricValue(
                value = formatInt(state.targetWpm, persianNumerals),
                label = "سرعت هدف سطح",
                size = type.title.fontSize,
                accent = ObsidianSpectrum.BrightBlue,
            )
        }

        val topMistakes = result.topMistakes(5)
        if (topMistakes.isNotEmpty()) {
            MrsadDivider()
            Text("حروف نیازمند تمرین هدفمند", style = type.label.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
                topMistakes.forEach { (ch, count) ->
                    val label = if (ch == ' ') "فاصله" else ch.toString()
                    MrsadChip(
                        text = "$label × ${formatInt(count, persianNumerals)}",
                        tint = ObsidianSpectrum.Pink,
                        compact = true,
                    )
                }
            }
            Text(
                "توصیهٔ ارگونومیک: در نوبت بعدی، روی کلید «${topMistakes.first().first}» (${TypeTexts.fingerHintFa(topMistakes.first().first, state.usePersian)}) مکث کوتاه و ضربهٔ دقیق داشته باشید.",
                style = type.caption,
                color = colors.textSecondary,
            )
        }

        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
            MrsadPrimaryButton(
                text = "تکرار همین متن",
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

private val durationPresets = listOf(30, 60, 120, 180)

private fun buildTargetAnnotatedString(
    target: String,
    cursor: Int,
    hasMistake: Boolean,
    correctColor: Color,
    cursorColor: Color,
    mistakeColor: Color,
    remainingColor: Color,
) = buildAnnotatedString {
    val safeCursor = cursor.coerceIn(0, target.length)
    if (safeCursor > 0) {
        withStyle(SpanStyle(color = correctColor)) {
            append(target.substring(0, safeCursor))
        }
    }
    if (safeCursor < target.length) {
        val activeColor = if (hasMistake) mistakeColor else cursorColor
        withStyle(
            SpanStyle(
                color = activeColor,
                background = activeColor.copy(alpha = 0.28f),
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
            ),
        ) {
            append(target[safeCursor])
        }
        if (safeCursor + 1 < target.length) {
            withStyle(SpanStyle(color = remainingColor)) {
                append(target.substring(safeCursor + 1))
            }
        }
    }
}
