package com.vodiwalker.gesture.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vodiwalker.gesture.R
import com.vodiwalker.gesture.core.designsystem.component.VodiPrimaryButton
import com.vodiwalker.gesture.core.designsystem.component.VodiSection
import com.vodiwalker.gesture.core.designsystem.text.PersianText
import com.vodiwalker.gesture.core.model.PowerMode

/**
 * Settings, grouped into four short sections. Every row is a single concept with a single control;
 * nothing here is required to use the app, and nothing here is a developer diagnostic.
 *
 * The screen is driven by [DashboardViewModel] because the preference set is small enough that a
 * second ViewModel would add a lifecycle for no benefit.
 */
@Composable
fun SettingsRoute(
    viewModel: DashboardViewModel,
    appVersion: String,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        appVersion = appVersion,
        onToggleDarkMode = viewModel::setDarkAppearanceMode,
        onToggleDynamicColor = viewModel::setDynamicColor,
        onToggleHighContrast = viewModel::setForceHighContrast,
        onHapticIntensityChange = viewModel::updateHapticIntensity,
        onSelectPowerMode = viewModel::selectPowerMode,
        modifier = modifier,
    )
}

/**
 * Stateless settings surface.
 */
@Composable
fun SettingsScreen(
    state: DashboardUiState,
    appVersion: String,
    onToggleDarkMode: (Boolean) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleHighContrast: (Boolean) -> Unit,
    onHapticIntensityChange: (Float) -> Unit,
    onSelectPowerMode: (PowerMode?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
    ) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        item(key = "appearance") {
            VodiSection(title = stringResource(R.string.settings_section_appearance)) {
                SwitchRow(
                    title = stringResource(R.string.settings_dark_mode),
                    subtitle = stringResource(R.string.settings_dark_mode_desc),
                    checked = state.forceDarkTheme,
                    onCheckedChange = onToggleDarkMode,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_desc),
                    checked = state.dynamicColor,
                    onCheckedChange = onToggleDynamicColor,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_high_contrast),
                    subtitle = stringResource(R.string.settings_high_contrast_desc),
                    checked = state.forceHighContrast,
                    onCheckedChange = onToggleHighContrast,
                )
            }
        }

        item(key = "behaviour") {
            VodiSection(title = stringResource(R.string.settings_section_behavior)) {
                Text(
                    text = stringResource(
                        R.string.settings_haptic,
                    ) + " · " + stringResource(
                        R.string.settings_haptic_value,
                        PersianText.formatInteger((state.hapticIntensity * 100f).toLong()),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = state.hapticIntensity,
                    onValueChange = onHapticIntensityChange,
                    valueRange = 0f..1f,
                    modifier = Modifier.testTag("settings_haptic_slider"),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_battery_saver),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.settings_battery_saver_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        null to stringResource(R.string.action_on),
                        PowerMode.BALANCED to stringResource(R.string.status_ready),
                        PowerMode.ECO to stringResource(R.string.action_off),
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = state.preferredPowerMode == mode,
                            onClick = { onSelectPowerMode(mode) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }

        item(key = "privacy") {
            VodiSection(title = stringResource(R.string.settings_section_privacy)) {
                Text(
                    text = stringResource(R.string.settings_privacy_apps),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.settings_privacy_apps_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "• " + stringResource(R.string.settings_privacy_bank),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "• " + stringResource(R.string.settings_privacy_password),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item(key = "about") {
            VodiSection(title = stringResource(R.string.settings_section_about)) {
                Text(
                    text = stringResource(R.string.settings_version, appVersion),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_offline_badge) + " — " +
                        stringResource(R.string.settings_offline_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VodiPrimaryButton(
                    text = stringResource(R.string.action_close),
                    onClick = { /* handled by the host nav */ },
                )
            }
        }
    }
}

/**
 * A settings row: title, optional explanation, and a switch on the trailing edge.
 */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(0.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
