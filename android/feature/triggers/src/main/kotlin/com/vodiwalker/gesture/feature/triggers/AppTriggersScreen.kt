package com.vodiwalker.gesture.feature.triggers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.vodiwalker.gesture.R
import com.vodiwalker.gesture.core.designsystem.component.VodiEmptyState
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
 * Immutable rule snapshot with the resolved Smart-Action graph for each rule.
 */
data class TriggersUiState(
    val rules: List<AppTriggerRule> = emptyList(),
    val graphsById: Map<String, SmartActionGraph> = emptyMap(),
) {
    /** True before the first load resolves, used to avoid flashing the empty state. */
    val isEmpty: Boolean get() = rules.isEmpty()
}

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
     * Enables or disables a rule and refreshes the list.
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
 * Rules screen: each row answers the three questions a rule encodes — which gesture, where, and
 * what it does — in plain Persian, with one switch to pause it.
 */
@Composable
fun AppTriggersScreen(
    state: TriggersUiState,
    onToggleRule: (AppTriggerRule, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("app_triggers_screen"),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.rules_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.rules_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.isEmpty) {
            item(key = "empty") {
                VodiEmptyState(
                    icon = Icons.Rounded.AccountTree,
                    title = stringResource(R.string.rules_empty_title),
                    body = stringResource(R.string.rules_empty_body),
                )
            }
        } else {
            items(items = state.rules, key = { it.id }) { rule ->
                RuleRow(
                    rule = rule,
                    graphName = state.graphsById[rule.actionGraphId]?.name,
                    onToggle = { enabled -> onToggleRule(rule, enabled) },
                )
            }
        }
    }
}

/**
 * One rule: action name on top, scope underneath, a switch on the trailing edge.
 *
 * The switch is placed with `Arrangement.SpaceBetween` on a Row, so it lands on the *end* side in
 * both LTR and RTL automatically — no `padding(end = …)` or `Alignment.End` guesswork.
 */
@Composable
private fun RuleRow(
    rule: AppTriggerRule,
    graphName: String?,
    onToggle: (Boolean) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Gesture,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = graphName ?: rule.actionGraphId,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = scopeLabel(rule.scope),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Switch(
                checked = rule.isEnabled,
                onCheckedChange = onToggle,
            )
        }
    }
}

/**
 * Localised scope label. Package names are inherently LTR, so they are isolated to stop the bidi
 * algorithm from reordering the surrounding Persian sentence.
 */
@Composable
private fun scopeLabel(scope: TriggerScope): String = when (scope) {
    TriggerScope.Global -> stringResource(R.string.rules_scope_everywhere)
    is TriggerScope.Category -> stringResource(R.string.rules_scope_app, scope.category.name)
    is TriggerScope.Package -> stringResource(
        R.string.rules_scope_app,
        com.vodiwalker.gesture.core.designsystem.text.PersianText.isolateLtr(scope.packageName),
    )
    is TriggerScope.Activity -> stringResource(
        R.string.rules_scope_app,
        com.vodiwalker.gesture.core.designsystem.text.PersianText.isolateLtr(
            scope.activityClassName.substringAfterLast('.'),
        ),
    )
}
