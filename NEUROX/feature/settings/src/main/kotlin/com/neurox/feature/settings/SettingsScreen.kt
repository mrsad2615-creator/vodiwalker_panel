package com.neurox.feature.settings

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.neurox.designsystem.components.ChipTone
import com.neurox.designsystem.components.NxChip
import com.neurox.designsystem.components.NxPrimaryButton
import com.neurox.designsystem.components.NxSecondaryButton
import com.neurox.designsystem.components.NxSectionHeader
import com.neurox.designsystem.components.NxSettingRow
import com.neurox.designsystem.tokens.NxSpacing
import com.neurox.designsystem.components.NxSurface
import com.neurox.designsystem.theme.NxTheme
import com.neurox.designsystem.theme.NxTypography
import com.neurox.domain.model.AudioChannel
import com.neurox.domain.model.AudioSettings
import com.neurox.domain.model.ThemeDefinition
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.screen.SettingsState

/**
 * Settings.
 *
 * GROUPING follows what the user is trying to do, not the data model: how it looks,
 * how training behaves, how it sounds, what happens to the data, and what the app
 * does and does not claim. The medical disclaimer and the timing limitation sit in
 * About, in plain language, where a person actually finds them.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    strings: Strings,
    onChangeLanguage: (String) -> Unit,
    onChangeTheme: (String) -> Unit,
    onChangeMotion: (String) -> Unit,
    onToggleReducedMotion: (Boolean) -> Unit,
    onChangeTextScale: (Float) -> Unit,
    onToggleAdaptive: (Boolean) -> Unit,
    onChangeAdaptiveSamples: (Int) -> Unit,
    onChangeAudio: (AudioSettings) -> Unit,
    onChangeInputLatency: (Float) -> Unit,
    onChangeDisplayLatency: (Float) -> Unit,
    onChangeWeeklyGoal: (Int) -> Unit,
    onToggleScheduledBackup: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NxSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NxSpacing.md),
    ) {
        Text(
            text = strings.raw("settings.title"),
            style = NxTypography.headline,
            color = NxTheme.colors.textPrimary,
            modifier = Modifier.padding(top = NxSpacing.lg),
        )

        // ---------------------------------------------------------- experience
        NxSectionHeader(title = strings.raw("settings.section.experience"))
        NxSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(NxSpacing.lg)) {
                Text(
                    text = strings.raw("settings.language"),
                    style = NxTypography.label,
                    color = NxTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(NxSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                    Strings.SUPPORTED_LANGUAGES.forEach { tag ->
                        NxChip(
                            text = strings.raw("language.$tag"),
                            selected = state.languageTag == tag,
                            tone = if (state.languageTag == tag) ChipTone.ACCENT else ChipTone.NEUTRAL,
                            onClick = { onChangeLanguage(tag) },
                        )
                    }
                }
                Spacer(Modifier.height(NxSpacing.lg))
                Text(
                    text = strings.raw("settings.theme"),
                    style = NxTypography.label,
                    color = NxTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(NxSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                    state.availableThemes.forEach { theme ->
                        ThemeChip(theme = theme, strings = strings, selected = state.themeId == theme.id) {
                            onChangeTheme(theme.id)
                        }
                    }
                }
            }
        }

        NxSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(NxSpacing.lg)) {
                Text(
                    text = strings.raw("settings.motion"),
                    style = NxTypography.label,
                    color = NxTheme.colors.textPrimary,
                )
                Text(
                    text = strings.raw("settings.motion.body"),
                    style = NxTypography.caption,
                    color = NxTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(NxSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                    listOf(
                        "CINEMATIC" to "settings.motion.cinematic",
                        "BALANCED" to "settings.motion.balanced",
                        "PERFORMANCE" to "settings.motion.performance",
                    ).forEach { (id, key) ->
                        NxChip(
                            text = strings.raw(key),
                            selected = state.motionMode == id,
                            tone = if (state.motionMode == id) ChipTone.ACCENT else ChipTone.NEUTRAL,
                            onClick = { onChangeMotion(id) },
                        )
                    }
                }
                Spacer(Modifier.height(NxSpacing.md))
                ToggleRow(
                    label = strings.raw("settings.reducedMotion"),
                    checked = state.reducedMotion,
                    onChange = onToggleReducedMotion,
                )
                Spacer(Modifier.height(NxSpacing.md))
                SliderRow(
                    label = strings.raw("settings.textScale"),
                    value = state.textScale,
                    valueRange = 0.8f..2.0f,
                    steps = 11,
                    valueText = strings.percent(state.textScale.toDouble() / 2.0),
                    onChange = onChangeTextScale,
                )
            }
        }

        // ------------------------------------------------------------ training
        NxSectionHeader(title = strings.raw("settings.section.training"))
        NxSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(NxSpacing.lg)) {
                ToggleRow(
                    label = strings.raw("settings.adaptive"),
                    checked = state.adaptiveEnabled,
                    onChange = onToggleAdaptive,
                )
                Text(
                    text = strings.raw("settings.adaptive.body"),
                    style = NxTypography.caption,
                    color = NxTheme.colors.textTertiary,
                )
                if (state.adaptiveEnabled) {
                    Spacer(Modifier.height(NxSpacing.md))
                    SliderRow(
                        label = strings.raw("settings.adaptive.minSamples"),
                        value = state.adaptiveMinSamples.toFloat(),
                        valueRange = 1f..10f,
                        steps = 8,
                        valueText = strings.decimal(state.adaptiveMinSamples.toLong()),
                        onChange = { onChangeAdaptiveSamples(it.toInt()) },
                    )
                }
                Spacer(Modifier.height(NxSpacing.md))
                SliderRow(
                    label = strings.raw("plan.goal"),
                    value = state.weeklyGoalMinutes.toFloat(),
                    valueRange = 0f..300f,
                    steps = 29,
                    valueText = strings.format("plan.goalMinutes", state.weeklyGoalMinutes),
                    onChange = { onChangeWeeklyGoal(it.toInt()) },
                )
            }
        }

        NxSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(NxSpacing.lg)) {
                Text(
                    text = strings.raw("settings.timing"),
                    style = NxTypography.label,
                    color = NxTheme.colors.textPrimary,
                )
                Text(
                    text = strings.raw("settings.timing.body"),
                    style = NxTypography.caption,
                    color = NxTheme.colors.textTertiary,
                )
                Spacer(Modifier.height(NxSpacing.md))
                SliderRow(
                    label = strings.raw("settings.timing.input"),
                    value = state.inputLatencyMillis,
                    valueRange = 0f..100f,
                    steps = 19,
                    valueText = strings.millis(state.inputLatencyMillis.toDouble()),
                    onChange = onChangeInputLatency,
                )
                SliderRow(
                    label = strings.raw("settings.timing.display"),
                    value = state.displayLatencyMillis,
                    valueRange = 0f..100f,
                    steps = 19,
                    valueText = strings.millis(state.displayLatencyMillis.toDouble()),
                    onChange = onChangeDisplayLatency,
                )
            }
        }

        // --------------------------------------------------------------- audio
        NxSectionHeader(title = strings.raw("settings.section.audio"))
        NxSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(NxSpacing.lg)) {
                AudioToggle(
                    label = strings.raw("settings.audio.training"),
                    enabled = state.audio.trainingCuesEnabled,
                    volume = state.audio.trainingCueVolume,
                    channel = AudioChannel.TRAINING_CUE,
                    strings = strings,
                    audio = state.audio,
                    onChange = onChangeAudio,
                )
                AudioToggle(
                    label = strings.raw("settings.audio.success"),
                    enabled = state.audio.successEnabled,
                    volume = state.audio.successVolume,
                    channel = AudioChannel.SUCCESS,
                    strings = strings,
                    audio = state.audio,
                    onChange = onChangeAudio,
                )
                AudioToggle(
                    label = strings.raw("settings.audio.error"),
                    enabled = state.audio.errorEnabled,
                    volume = state.audio.errorVolume,
                    channel = AudioChannel.ERROR,
                    strings = strings,
                    audio = state.audio,
                    onChange = onChangeAudio,
                )
                AudioToggle(
                    label = strings.raw("settings.audio.interface"),
                    enabled = state.audio.interfaceSoundsEnabled,
                    volume = state.audio.interfaceVolume,
                    channel = AudioChannel.INTERFACE,
                    strings = strings,
                    audio = state.audio,
                    onChange = onChangeAudio,
                )
                NxSettingRow(
                    label = strings.raw("settings.audio.music"),
                    supporting = strings.raw("settings.audio.focusBody"),
                    onClick = {
                        onChangeAudio(state.audio.copy(musicEnabled = !state.audio.musicEnabled))
                    },
                    value = strings.raw(if (state.audio.musicEnabled) "common.enabled" else "common.disabled"),
                )
                ToggleRow(
                    label = strings.raw("settings.audio.haptics"),
                    checked = state.audio.hapticsEnabled,
                    onChange = { onChangeAudio(state.audio.copy(hapticsEnabled = it)) },
                )
                SliderRow(
                    label = strings.raw("settings.audio.hapticsIntensity"),
                    value = state.audio.hapticIntensity,
                    valueRange = 0f..1f,
                    steps = 9,
                    valueText = strings.percent(state.audio.hapticIntensity.toDouble()),
                    onChange = { onChangeAudio(state.audio.copy(hapticIntensity = it)) },
                )
                Spacer(Modifier.height(NxSpacing.sm))
                ToggleRow(
                    label = strings.raw("settings.audio.focus"),
                    checked = state.audio.focusMode,
                    onChange = { onChangeAudio(state.audio.copy(focusMode = it)) },
                )
                Text(
                    text = strings.raw("settings.audio.silentBody"),
                    style = NxTypography.caption,
                    color = NxTheme.colors.textTertiary,
                )
                ToggleRow(
                    label = strings.raw("settings.audio.silent"),
                    checked = state.audio.silentMode,
                    onChange = { onChangeAudio(state.audio.copy(silentMode = it)) },
                )
            }
        }

        // ---------------------------------------------------------------- data
        NxSectionHeader(title = strings.raw("settings.section.data"))
        NxSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(NxSpacing.lg)) {
                Text(
                    text = strings.raw("settings.data.privacy"),
                    style = NxTypography.label,
                    color = NxTheme.colors.textPrimary,
                )
                Text(
                    text = strings.raw("settings.data.privacyBody"),
                    style = NxTypography.caption,
                    color = NxTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(NxSpacing.md))
                Text(
                    text = if (state.hasEverExported) {
                        strings.format("settings.data.lastExport", strings.decimal(state.lastExportAtMillis))
                    } else {
                        strings.raw("settings.data.neverExported")
                    },
                    style = NxTypography.caption,
                    color = NxTheme.colors.textTertiary,
                )
                Spacer(Modifier.height(NxSpacing.sm))
                ToggleRow(
                    label = strings.raw("settings.data.autoBackup"),
                    checked = state.scheduledBackup,
                    onChange = onToggleScheduledBackup,
                )
                Spacer(Modifier.height(NxSpacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                    NxPrimaryButton(label = strings.raw("settings.data.export"), onClick = onExport)
                    NxSecondaryButton(label = strings.raw("settings.data.import"), onClick = onImport)
                }
            }
        }

        // --------------------------------------------------------------- about
        NxSectionHeader(title = strings.raw("settings.section.about"))
        NxSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(NxSpacing.lg)) {
                NxSettingRow(
                    label = strings.raw("settings.about.version"),
                    value = state.appVersion,
                )
                // This row used to call `onOpenLicenses`, which the route passed as
                // `{ }` - a chevron and a ripple that led nowhere. There is no licence
                // screen in this build, so the row states the licence inline instead
                // of pretending to open one. The callback stays in the signature until
                // a screen exists to receive it.
                NxSettingRow(
                    label = strings.raw("settings.about.licenses"),
                    value = strings.raw("settings.about.licenseValue"),
                )
                Spacer(Modifier.height(NxSpacing.md))
                DisclaimerBlock(
                    title = strings.raw("settings.about.medical"),
                    body = strings.raw("settings.about.medicalBody"),
                )
                Spacer(Modifier.height(NxSpacing.md))
                DisclaimerBlock(
                    title = strings.raw("settings.about.method"),
                    body = strings.raw("settings.about.methodBody"),
                )
                Spacer(Modifier.height(NxSpacing.md))
                DisclaimerBlock(
                    title = strings.raw("settings.about.limitations"),
                    body = strings.raw("settings.about.limitationsBody"),
                )
            }
        }

        Spacer(Modifier.height(NxSpacing.xxl))
    }
}

@Composable
private fun DisclaimerBlock(title: String, body: String) {
    Column {
        Text(text = title, style = NxTypography.label, color = NxTheme.colors.warning)
        Text(text = body, style = NxTypography.caption, color = NxTheme.colors.textSecondary)
    }
}

@Composable
private fun ThemeChip(theme: ThemeDefinition, strings: Strings, selected: Boolean, onClick: () -> Unit) {
    NxChip(
        text = strings.raw(
            when (theme.id) {
                "neurox.obsidian" -> "settings.theme.obsidian"
                "neurox.ivory" -> "settings.theme.ivory"
                "neurox.contrast" -> "settings.theme.contrast"
                else -> "settings.theme"
            },
        ),
        selected = selected,
        tone = if (selected) ChipTone.ACCENT else ChipTone.NEUTRAL,
        onClick = onClick,
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NxSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = NxTypography.body,
            color = NxTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = NxSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = NxTypography.caption,
                color = NxTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(text = valueText, style = NxTypography.caption, color = NxTheme.colors.textPrimary)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Composable
private fun AudioToggle(
    label: String,
    enabled: Boolean,
    volume: Float,
    channel: AudioChannel,
    strings: Strings,
    audio: AudioSettings,
    onChange: (AudioSettings) -> Unit,
) {
    ToggleRow(
        label = label,
        checked = enabled,
        onChange = { onChange(audio.withChannel(channel, it)) },
    )
    if (enabled) {
        SliderRow(
            label = strings.raw("audio.${channel.id}.volume"),
            value = volume,
            valueRange = 0f..1f,
            steps = 9,
            valueText = strings.percent(volume.toDouble()),
            onChange = { onChange(audio.withVolume(channel, it)) },
        )
    }
}
