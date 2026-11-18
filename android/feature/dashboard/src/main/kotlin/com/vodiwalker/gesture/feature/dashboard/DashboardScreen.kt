package com.vodiwalker.gesture.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vodiwalker.gesture.core.datastore.UserSettingsDataSource
import com.vodiwalker.gesture.core.designsystem.component.ConfidenceArcGauge
import com.vodiwalker.gesture.core.designsystem.component.GlassmorphicCard
import com.vodiwalker.gesture.core.designsystem.component.SpringPressableCard
import com.vodiwalker.gesture.core.designsystem.theme.LocalExtendedColors
import com.vodiwalker.gesture.core.model.GestureTemplate
import com.vodiwalker.gesture.core.model.GestureTemplateRepository
import com.vodiwalker.gesture.core.model.PowerExecutionBudget
import com.vodiwalker.gesture.core.model.PowerMode
import com.vodiwalker.gesture.service.accessibility.VodiGestureAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Immutable dashboard snapshot: engine health, power budget, and registered templates.
 */
data class DashboardUiState(
    val serviceActive: Boolean = false,
    val powerBudget: PowerExecutionBudget? = null,
    val preferredPowerMode: PowerMode? = null,
    val hapticIntensity: Float = 0.85f,
    val templates: List<GestureTemplate> = emptyList(),
)

/**
 * Combines service state, power budget, and preferences into one UDF state stream.
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
 * Stateless Obsidian Cinema Material Design 3 Command Center displaying live Multi-Engine health,
 * Adaptive Power Governor controls, LRA Haptic gain, and registered gesture templates.
 */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onSelectPowerMode: (PowerMode?) -> Unit,
    onUpdateHapticIntensity: (Float) -> Unit,
    onCreateGesture: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = LocalExtendedColors.current

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateGesture,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Record Gesture") },
                modifier = Modifier.testTag("fab_record_gesture"),
            )
        },
        modifier = modifier.testTag("dashboard_screen"),
    ) { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            item(key = "hero_status") {
                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = if (state.serviceActive) "Multi-Engine Online" else "Service Inactive",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (state.serviceActive) {
                                    extended.statusActive
                                } else {
                                    extended.statusWarning
                                },
                            )
                            val budget = state.powerBudget
                            Text(
                                text = if (budget != null) {
                                    "${budget.mode.name} · ${budget.touchSampleRateHz}Hz · " +
                                        "~${budget.estimatedBatteryPercentPerHour}%/hr"
                                } else {
                                    "Tap to verify Android 14+ Accessibility setup"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ConfidenceArcGauge(
                            confidence = if (state.serviceActive) 0.98f else 0f,
                            label = "Health",
                            diameter = 74.dp,
                        )
                    }
                }
            }

            if (!state.serviceActive) {
                item(key = "permission_banner") {
                    SpringPressableCard(
                        onClick = onOpenPermissions,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Icon(Icons.Rounded.Bolt, contentDescription = null)
                            Text(
                                text = "Complete Accessibility Setup to arm Global Overlay",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }

            item(key = "power_and_haptics") {
                SpringPressableCard(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Rounded.BatterySaver, contentDescription = null)
                            Text("Adaptive Power Governor", style = MaterialTheme.typography.titleMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                null to "Auto",
                                PowerMode.PERFORMANCE to "120Hz",
                                PowerMode.BALANCED to "Balanced",
                                PowerMode.ECO to "Eco",
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = state.preferredPowerMode == mode,
                                    onClick = { onSelectPowerMode(mode) },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Text(
                            text = "LRA Haptic Intensity: ${(state.hapticIntensity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Slider(
                            value = state.hapticIntensity,
                            onValueChange = onUpdateHapticIntensity,
                            valueRange = 0f..1f,
                        )
                    }
                }
            }

            items(items = state.templates, key = { it.id }) { template ->
                SpringPressableCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Rounded.Gesture, contentDescription = null)
                            Column {
                                Text(template.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${template.category.name} · ${template.usageCount} runs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = "${(template.minConfidence * 100).toInt()}% min",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
