package ai.dana.ui

import ai.dana.R
import ai.dana.domain.ComparisonLimits
import ai.dana.domain.ProviderProfile
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

internal fun LazyListScope.comparison(state: DashboardState, dispatch: (DanaIntent) -> Unit) {
    val comparison = state.comparison
    val profiles = (state.profiles as? Reading.Ready)?.value.orEmpty()
    val selectedCount = profiles.count { it.id in comparison.selected }
    val offline = (state.offline as? Reading.Ready)?.value != false
    val canEdit = !comparison.running && !state.session.busy
    item {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(stringResource(R.string.compare_heading), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.compare_description), color = Titanium)
        }
    }
    if (offline) item {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.compare_offline), color = Ice)
            OutlinedButton(
                onClick = { dispatch(DanaIntent.Navigate(DashboardTab.SETTINGS)) },
                enabled = !state.session.busy,
            ) { Text(stringResource(R.string.tab_settings)) }
        }
    }
    val read = state.profiles
    if (read is Reading.Failed) item { ReadError(read.message) { dispatch(DanaIntent.Reload) } }
    if (read is Reading.Ready && profiles.isEmpty()) item {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.compare_no_profiles))
            OutlinedButton(
                onClick = { dispatch(DanaIntent.Navigate(DashboardTab.SETTINGS)) },
                enabled = !state.session.busy,
            ) { Text(stringResource(R.string.tab_settings)) }
        }
    }
    items(profiles, key = { "selection-${it.id}" }) { profile ->
        val checked = profile.id in comparison.selected
        EngineChoice(profile, checked, canEdit && (checked || selectedCount < ComparisonLimits.ENGINE_COUNT)) {
            dispatch(DanaIntent.ToggleEngine(profile.id))
        }
    }
    item {
        GlassCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = comparison.prompt,
                onValueChange = { dispatch(DanaIntent.ChangePrompt(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.compare_prompt)) },
                enabled = canEdit,
                minLines = 2,
                maxLines = 6,
            )
            comparison.problem?.let { ReadError(DanaErrorHandler.messageFor(it)) }
            if (comparison.running) {
                OutlinedButton(onClick = { dispatch(DanaIntent.StopComparison) }) {
                    Text(stringResource(R.string.compare_stop))
                }
            } else {
                Button(
                    onClick = { dispatch(DanaIntent.ReviewComparison) },
                    enabled = canEdit && !offline && selectedCount > 0 && comparison.prompt.isNotBlank(),
                ) { Text(stringResource(R.string.compare_review)) }
            }
        }
    }
    items(comparison.lanes, key = { "answer-${it.profile.id}" }) { lane -> EngineAnswer(lane) }
}

@Composable
private fun EngineChoice(profile: ProviderProfile, checked: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onSelect() })) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(profile.config.name, style = MaterialTheme.typography.titleMedium)
                TechnicalText(profile.config.model)
                Text(stringResource(protocolLabel(profile.config.protocol)), color = Titanium, style = MaterialTheme.typography.labelMedium)
            }
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

@Composable
private fun EngineAnswer(lane: EngineLane) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(lane.profile.config.name, style = MaterialTheme.typography.titleMedium)
        val label = when (lane.status) {
            LaneStatus.CONNECTING -> R.string.compare_connecting
            LaneStatus.STREAMING -> R.string.compare_streaming
            LaneStatus.COMPLETE -> R.string.compare_complete
            LaneStatus.FAILED -> R.string.compare_failed
            LaneStatus.STOPPED -> R.string.compare_stopped
        }
        Text(stringResource(label), color = if (lane.status == LaneStatus.COMPLETE) Mint else Titanium,
            style = MaterialTheme.typography.labelMedium)
        if (lane.answer.isNotEmpty()) {
            SelectionContainer { Text(lane.answer, style = MaterialTheme.typography.bodyLarge) }
        }
        lane.problem?.let { ReadError(DanaErrorHandler.messageFor(it)) }
    }
}
