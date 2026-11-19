package com.vodiwalker.gesture.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vodiwalker.gesture.core.designsystem.R
import com.vodiwalker.gesture.core.datastore.DarkThemeMode
import com.vodiwalker.gesture.core.datastore.UserSettingsDataSource
import com.vodiwalker.gesture.core.designsystem.component.StatusTone
import com.vodiwalker.gesture.core.designsystem.component.VodiEmptyState
import com.vodiwalker.gesture.core.designsystem.component.VodiPrimaryButton
import com.vodiwalker.gesture.core.designsystem.component.VodiSecondaryButton
import com.vodiwalker.gesture.core.designsystem.component.VodiSection
import com.vodiwalker.gesture.core.designsystem.component.VodiStat
import com.vodiwalker.gesture.core.designsystem.component.VodiStatusPill
import com.vodiwalker.gesture.core.designsystem.text.PersianText
import com.vodiwalker.gesture.core.model.GestureTemplate
import com.vodiwalker.gesture.core.model.GestureTemplateRepository
import com.vodiwalker.gesture.core.model.PowerExecutionBudget
import com.vodiwalker.gesture.core.model.PowerMode
import com.vodiwalker.gesture.service.accessibility.VodiGestureAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Immutable home snapshot: activation status, the gesture library, and today's activity.
 */
data class DashboardUiState(
    val serviceActive: Boolean = false,
    val powerBudget: PowerExecutionBudget? = null,
    val preferredPowerMode: PowerMode? = null,
    val hapticIntensity: Float = 0.85f,
    val templates: List<GestureTemplate> = emptyList(),
    val dynamicColor: Boolean = true,
    val forceHighContrast: Boolean = false,
    val forceDarkTheme: Boolean = false,
) {
    /** True when the edge handle can actually be used right now. */
    val isArmed: Boolean get() = serviceActive

    /** Number of enabled gestures, which is what the home headline reports. */
    val enabledGestureCount: Int get() = templates.count { it.isEnabled }

    /** Total successful executions, shown as the single activity metric. */
    val totalRuns: Long get() = templates.sumOf { it.usageCount }
}

/**
 * Combines service state with the persisted library into one UDF stream.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val templateRepository: GestureTemplateRepository,
    private val settingsDataSource: UserSettingsDataSource,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        VodiGestureAccessibilityService.isServiceConnected,
        VodiGestureAccessibilityService.activePowerBudget,
        settingsDataSource.settingsFlow,
    ) { connected, budget, settings ->
        DashboardUiState(
            serviceActive = connected,
            powerBudget = budget,
            preferredPowerMode = settings.preferredPowerMode,
            hapticIntensity = settings.hapticIntensity,
            templates = templateRepository.getAllTemplates(),
            dynamicColor = settings.dynamicMaterialYouColor,
            forceHighContrast = settings.forceHighContrast,
            forceDarkTheme = settings.darkThemeMode == DarkThemeMode.AlwaysDark,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = DashboardUiState(),
    )

    /**
     * Pins a power mode, or null to follow the adaptive governor.
     */
    fun selectPowerMode(mode: PowerMode?) {
        viewModelScope.launch { settingsDataSource.setPreferredPowerMode(mode) }
    }

    /**
     * Persists the haptic gain slider value.
     */
    fun updateHapticIntensity(value: Float) {
        viewModelScope.launch { settingsDataSource.setHapticIntensity(value) }
    }

    /**
     * Switches between the dark and light scheme, mirroring the two-state switch in Settings.
     */
    fun setDarkAppearanceMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataSource.setDarkThemeMode(
                if (enabled) DarkThemeMode.AlwaysDark else DarkThemeMode.AlwaysLight,
            )
        }
    }

    /**
     * Toggles Material You dynamic colour.
     */
    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsDataSource.setDynamicMaterialYouColor(enabled) }
    }

    /**
     * Toggles the high-contrast palette.
     */
    fun setForceHighContrast(enabled: Boolean) {
        viewModelScope.launch { settingsDataSource.setForceHighContrast(enabled) }
    }
}

/**
 * Stateful entry point wiring the ViewModel to the stateless screen.
 */
@Composable
fun DashboardRoute(
    onNavigateToRecorder: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    viewModel: DashboardViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        onSelectPowerMode = viewModel::selectPowerMode,
        onUpdateHapticIntensity = viewModel::updateHapticIntensity,
        onCreateGesture = onNavigateToRecorder,
        onOpenPermissions = onNavigateToPermissions,
    )
}

/**
 * Home: a single status sentence, three quick actions, the gesture library, and one activity
 * number. No telemetry jargon, no engine names, no configuration on the first screen.
 */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onCreateGesture: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectPowerMode: (PowerMode?) -> Unit = {},
    onUpdateHapticIntensity: (Float) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "status") {
            StatusHeadline(
                state = state,
                onOpenPermissions = onOpenPermissions,
            )
        }

        if (state.templates.isEmpty()) {
            item(key = "first_run") {
                VerdantFirstRun(
                    onCreateGesture = onCreateGesture,
                )
            }
        } else {
            item(key = "quick_actions") {
                VodiSection(title = stringResource(R.string.home_section_quick)) {
                    QuickActionRow(
                        onCreateGesture = onCreateGesture,
                        onOpenPermissions = onOpenPermissions,
                        armed = state.isArmed,
                    )
                }
            }

            item(key = "library_header") {
                Text(
                    text = stringResource(R.string.home_section_your_gestures),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(items = state.templates.take(4), key = { it.id }) { template ->
                GestureRow(template = template)
            }

            if (state.templates.size > 4) {
                item(key = "all_gestures") {
                    TextButton(onClick = onCreateGesture) {
                        Text(stringResource(R.string.nav_gestures))
                    }
                }
            }
        }

        item(key = "activity") {
            ActivitySummary(
                runs = state.totalRuns,
                gestureCount = state.enabledGestureCount,
            )
        }

        item(key = "privacy_footer") {
            PrivacyFooter()
        }
    }
}

/**
 * The whole screen reduced to one status sentence, one pill and — when action is needed — one
 * button. This replaces the earlier gauge/power/haptic block.
 */
@Composable
private fun StatusHeadline(
    state: DashboardUiState,
    onOpenPermissions: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            VodiStatusPill(
                label = if (state.isArmed) {
                    stringResource(R.string.status_ready)
                } else {
                    stringResource(R.string.status_needs_setup)
                },
                tone = if (state.isArmed) StatusTone.Ready else StatusTone.Attention,
            )
        }
        Text(
            text = if (state.isArmed) {
                stringResource(R.string.home_ready_body)
            } else {
                stringResource(R.string.home_setup_body)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!state.isArmed) {
            VodiPrimaryButton(
                text = stringResource(R.string.home_setup_cta),
                onClick = onOpenPermissions,
                modifier = Modifier.testTag("dashboard_setup_cta"),
            )
        }
    }
}

/**
 * Zero-state: the first thing a new user sees. Two choices, both one tap away.
 */
@Composable
private fun VerdantFirstRun(onCreateGesture: () -> Unit) {
    VodiSection {
        VodiEmptyState(
            icon = Icons.Rounded.Gesture,
            title = stringResource(R.string.home_empty_gestures_title),
            body = stringResource(R.string.home_empty_gestures_body),
            actionLabel = stringResource(R.string.home_empty_gestures_cta),
            onAction = onCreateGesture,
        )
    }
}

/**
 * Three quick actions: record, test, and privacy. Each is a icon + label pair with a 56dp target.
 */
@Composable
private fun QuickActionRow(
    onCreateGesture: () -> Unit,
    onOpenPermissions: () -> Unit,
    armed: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickAction(
            icon = Icons.Rounded.Add,
            label = stringResource(R.string.gestures_new_cta),
            onClick = onCreateGesture,
            modifier = Modifier.weight(1f),
        )
        QuickAction(
            icon = Icons.Rounded.PlayArrow,
            label = stringResource(R.string.home_try_now_cta),
            onClick = onCreateGesture,
            modifier = Modifier.weight(1f),
            enabled = armed,
        )
        QuickAction(
            icon = Icons.Rounded.Shield,
            label = stringResource(R.string.settings_section_privacy),
            onClick = onOpenPermissions,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.height(84.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One gesture, one line: name, where it applies, and how often it has run.
 */
@Composable
private fun GestureRow(template: GestureTemplate) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Gesture,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.gestures_usage_count,
                        PersianText.formatInteger(template.usageCount),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!template.isEnabled) {
                Text(
                    text = stringResource(R.string.gestures_disabled),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Today's activity as two plain numbers. Persian digits only.
 */
@Composable
private fun ActivitySummary(runs: Long, gestureCount: Int) {
    VodiSection(title = stringResource(R.string.home_section_activity)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            VodiStat(
                value = PersianText.formatInteger(runs),
                label = stringResource(R.string.nav_gestures),
            )
            VodiStat(
                value = PersianText.formatInteger(gestureCount.toLong()),
                label = stringResource(R.string.nav_gestures),
            )
        }
        if (runs == 0L) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_activity_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One-line restatement of the offline guarantee, so privacy is visible without opening settings.
 */
@Composable
private fun PrivacyFooter() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Layers,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.settings_offline_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
