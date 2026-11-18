package com.vodiwalker.gesture.feature.triggers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.vodiwalker.gesture.core.designsystem.component.GlassmorphicCard
import com.vodiwalker.gesture.core.designsystem.component.SpringPressableCard
import com.vodiwalker.gesture.core.model.ActionGraphRepository
import com.vodiwalker.gesture.core.model.AppTriggerRule
import com.vodiwalker.gesture.core.model.SmartActionGraph
import com.vodiwalker.gesture.core.model.TriggerRuleRepository
import com.vodiwalker.gesture.core.model.TriggerScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Immutable trigger matrix snapshot with resolved Smart-Action graphs.
 */
data class TriggersUiState(
    val rules: List<AppTriggerRule> = emptyList(),
    val graphsById: Map<String, SmartActionGraph> = emptyMap(),
)

/**
 * Loads trigger rules and joins them with their action graphs for display.
 */
@HiltViewModel
class AppTriggersViewModel @Inject constructor(
    private val ruleRepository: TriggerRuleRepository,
    private val graphRepository: ActionGraphRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TriggersUiState())
    val uiState: StateFlow<TriggersUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    /**
     * Reloads rules and graphs from the repositories.
     */
    fun reload() {
        viewModelScope.launch {
            val rules = ruleRepository.getEnabledRules()
            val graphs = graphRepository.getAllGraphs().associateBy { it.id }
            _uiState.update { TriggersUiState(rules = rules, graphsById = graphs) }
        }
    }

    /**
     * Enables/disables a rule and refreshes the matrix.
     */
    fun toggleRule(rule: AppTriggerRule, enabled: Boolean) {
        viewModelScope.launch {
            ruleRepository.upsertRule(rule.copy(isEnabled = enabled))
            reload()
        }
    }
}

/**
 * Stateful entry point wiring the trigger ViewModel to the stateless screen.
 */
@Composable
fun AppTriggersRoute(viewModel: AppTriggersViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AppTriggersScreen(state = state, onToggleRule = viewModel::toggleRule)
}

/**
 * App-Specific Trigger Matrix and Smart-Action DAG overview screen.
 */
@Composable
fun AppTriggersScreen(
    state: TriggersUiState,
    onToggleRule: (AppTriggerRule, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("app_triggers_screen"),
    ) {
        item(key = "header") {
            GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("App-Specific Triggers & Smart-Actions", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Specificity hierarchy: Activity > Package > Category > Global. " +
                            "Cyclic graphs are rejected at creation time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(items = state.rules, key = { it.id }) { rule ->
            val graph = state.graphsById[rule.actionGraphId]
            SpringPressableCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = graph?.name ?: rule.actionGraphId,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(formatScopeLabel(rule.scope)) },
                        )
                    }
                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = { onToggleRule(rule, it) },
                    )
                }
            }
        }
    }
}

private fun formatScopeLabel(scope: TriggerScope): String = when (scope) {
    TriggerScope.Global -> "Global (All Non-Sensitive Apps)"
    is TriggerScope.Category -> "Category: ${scope.category.name}"
    is TriggerScope.Package -> "App: ${scope.packageName}"
    is TriggerScope.Activity -> "Screen: ${scope.activityClassName.substringAfterLast('.')}"
}
