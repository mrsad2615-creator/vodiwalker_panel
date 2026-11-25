package com.neurox.feature.read

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.neurox.designsystem.components.ChipTone
import com.neurox.designsystem.components.NxChip
import com.neurox.designsystem.components.NxEmptyState
import com.neurox.designsystem.components.NxPrimaryButton
import com.neurox.designsystem.components.NxSectionHeader
import com.neurox.designsystem.tokens.NxSpacing
import com.neurox.designsystem.components.NxSurface
import com.neurox.designsystem.theme.NxTheme
import com.neurox.designsystem.theme.NxTypography
import com.neurox.games.content.ReadingText
import com.neurox.games.read.FocusGuide
import com.neurox.presentation.i18n.Strings

/**
 * Reading Studio.
 *
 * Settings first, then the text, then the pace. The one thing this screen must not
 * do is present a screen-read WPM as if it were the user's true reading speed:
 * line length, text size and script all change it. The note under the controls says
 * so, in both languages.
 */
@Composable
fun ReadingStudioScreen(
    passages: List<ReadingText>,
    selected: ReadingText?,
    strings: Strings,
    wpm: Int,
    fontSize: Int,
    lineHeight: Float,
    guide: FocusGuide,
    rtl: Boolean,
    positionFraction: Double,
    onSelectPassage: (ReadingText) -> Unit,
    onWpmChange: (Int) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onGuideChange: (FocusGuide) -> Unit,
    onToggleRtl: (Boolean) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NxSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NxSpacing.lg),
    ) {
        Column(modifier = Modifier.padding(top = NxSpacing.lg)) {
            Text(
                text = strings.raw("read.studio.title"),
                style = NxTypography.headline,
                color = NxTheme.colors.textPrimary,
            )
            Text(
                text = strings.raw("read.studio.body"),
                style = NxTypography.caption,
                color = NxTheme.colors.textTertiary,
            )
        }

        if (passages.isEmpty()) {
            NxEmptyState(
                title = strings.raw("read.choosePassage"),
                body = strings.raw("common.empty"),
            )
            return@Column
        }

        NxSectionHeader(title = strings.raw("read.choosePassage"))
        Column(verticalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
            passages.forEach { passage ->
                val isSelected = selected != null && passage.id == selected.id
                NxChip(
                    text = passage.title,
                    selected = isSelected,
                    tone = if (isSelected) ChipTone.ACCENT else ChipTone.NEUTRAL,
                    onClick = { onSelectPassage(passage) },
                )
            }
        }

        selected?.let { passage ->
            NxSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(NxSpacing.lg)) {
                    Text(
                        text = passage.title,
                        style = NxTypography.title,
                        color = NxTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.height(NxSpacing.sm))
                    // Text has no lineHeight parameter; it belongs to the style, and
                    // building it once keeps size and leading consistent.
                    val bodyStyle = NxTypography.body.copy(
                        fontSize = fontSize.toFloat().sp,
                        lineHeight = (fontSize * lineHeight).sp,
                    )
                    Text(
                        text = passage.body.take(READING_PREVIEW_CHARS) + "…",
                        style = bodyStyle,
                        color = NxTheme.colors.textSecondary,
                        textAlign = if (rtl) TextAlign.End else TextAlign.Start,
                    )
                    Spacer(Modifier.height(NxSpacing.sm))
                    Text(
                        text = strings.format("read.studio.position", (positionFraction * 100).toInt()),
                        style = NxTypography.caption,
                        color = NxTheme.colors.textTertiary,
                    )
                }
            }
        }

        NxSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(NxSpacing.lg)) {
                SliderRow(
                    label = strings.raw("read.rsvp.wpm"),
                    value = wpm.toFloat(),
                    min = 60f,
                    max = 900f,
                    valueText = strings.decimal(wpm.toLong(), localize = false),
                    onChange = { onWpmChange(it.toInt()) },
                )
                SliderRow(
                    label = strings.raw("read.fontSize"),
                    value = fontSize.toFloat(),
                    min = 12f,
                    max = 40f,
                    valueText = strings.decimal(fontSize.toLong(), localize = false),
                    onChange = { onFontSizeChange(it.toInt()) },
                )
                SliderRow(
                    label = strings.raw("read.lineHeight"),
                    value = lineHeight,
                    min = 1f,
                    max = 2.6f,
                    valueText = strings.percent(lineHeight.toDouble() / 2.6),
                    onChange = onLineHeightChange,
                )
                Spacer(Modifier.height(NxSpacing.sm))
                Text(
                    text = strings.raw("read.focusGuide"),
                    style = NxTypography.caption,
                    color = NxTheme.colors.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                    FocusingOptions.forEach { option ->
                        NxChip(
                            text = strings.raw(option.second),
                            selected = guide == option.first,
                            tone = if (guide == option.first) ChipTone.ACCENT else ChipTone.NEUTRAL,
                            onClick = { onGuideChange(option.first) },
                        )
                    }
                }
                Spacer(Modifier.height(NxSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                    NxChip(
                        text = strings.raw("read.studio.title"),
                        selected = rtl,
                        tone = if (rtl) ChipTone.ACCENT else ChipTone.NEUTRAL,
                        onClick = { onToggleRtl(!rtl) },
                    )
                }
                Spacer(Modifier.height(NxSpacing.md))
                Text(
                    text = strings.raw("read.source.estimateNote"),
                    style = NxTypography.caption,
                    color = NxTheme.colors.warning,
                )
            }
        }

        NxPrimaryButton(
            label = strings.raw("read.startReading"),
            onClick = onStart,
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(NxSpacing.xxl))
    }
}

private val FocusingOptions = listOf(
    FocusGuide.NONE to "read.focusGuide.none",
    FocusGuide.LINE to "read.focusGuide.line",
    FocusGuide.SPOTLIGHT to "read.focusGuide.spotlight",
    FocusGuide.MASK to "read.focusGuide.mask",
)

private const val READING_PREVIEW_CHARS = 280

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = NxSpacing.xs)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = label,
                style = NxTypography.caption,
                color = NxTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(text = valueText, style = NxTypography.caption, color = NxTheme.colors.textPrimary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = min..max)
    }
}
