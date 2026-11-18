package ai.dana.ui

import ai.dana.R
import ai.dana.domain.AssistantTask
import ai.dana.domain.ComparisonLimits
import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
            Text(stringResource(R.string.studio_task_label), style = MaterialTheme.typography.titleMedium)
            TaskChoices(comparison.task, canEdit) { dispatch(DanaIntent.ChooseTask(it)) }
            OutlinedTextField(
                value = comparison.prompt,
                onValueChange = { dispatch(DanaIntent.ChangePrompt(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(taskPrompt(comparison.task))) },
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
    items(comparison.lanes, key = { "answer-${it.profile.id}" }) { lane ->
        EngineAnswer(lane, canSave = !comparison.running && !state.session.busy) {
            dispatch(DanaIntent.NoteFromAnswer(lane.profile.id))
        }
    }
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
internal fun EngineAnswer(lane: EngineLane, canSave: Boolean, saveLabel: Int = R.string.studio_save_note, onSave: () -> Unit) {
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
        lane.firstTextMillis?.let {
            Text(stringResource(R.string.studio_first_text), style = MaterialTheme.typography.labelSmall, color = Titanium)
            TechnicalText("$it ms")
        }
        lane.elapsedMillis?.let {
            Text(stringResource(R.string.studio_elapsed), style = MaterialTheme.typography.labelSmall, color = Titanium)
            TechnicalText("$it ms")
        }
        if (lane.answer.isNotEmpty()) {
            SelectionContainer { Text(lane.answer, style = MaterialTheme.typography.bodyLarge) }
        }
        lane.problem?.let { ReadError(DanaErrorHandler.messageFor(it)) }
        lane.httpStatus?.let { TechnicalText("HTTP $it") }
        if (lane.answer.isNotBlank() && !lane.pending) {
            val clipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)
            val label = stringResource(R.string.app_name)
            var copied by remember(lane.answer) { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = clipboard != null, onClick = {
                    val clip = ClipData.newPlainText(label, lane.answer)
                    clip.description.extras = PersistableBundle().apply {
                        putBoolean("android.content.extra.IS_SENSITIVE", true)
                    }
                    clipboard?.setPrimaryClip(clip)
                    copied = true
                }) { Text(stringResource(if (copied) R.string.studio_copied else R.string.studio_copy)) }
                TextButton(enabled = canSave, onClick = onSave) { Text(stringResource(saveLabel)) }
            }
        }
    }
}


@Composable
internal fun TaskChoices(selected: AssistantTask?, enabled: Boolean, onChoose: (AssistantTask) -> Unit) {
    for (row in AssistantTask.entries.chunked(2)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (task in row) {
                FilterChip(
                    selected = task == selected,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(taskLabel(task))) },
                    onClick = { onChoose(task) },
                )
            }
        }
    }
}

internal fun taskLabel(task: AssistantTask): Int = when (task) {
    AssistantTask.ASK -> R.string.task_ask
    AssistantTask.SUMMARIZE -> R.string.task_summarize
    AssistantTask.TRANSLATE -> R.string.task_translate
    AssistantTask.DRAFT -> R.string.task_draft
}

private fun taskPrompt(task: AssistantTask): Int = when (task) {
    AssistantTask.ASK -> R.string.compare_prompt
    AssistantTask.SUMMARIZE -> R.string.task_summarize_input
    AssistantTask.TRANSLATE -> R.string.task_translate_input
    AssistantTask.DRAFT -> R.string.task_draft_input
}
