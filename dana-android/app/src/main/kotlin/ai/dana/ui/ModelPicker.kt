package ai.dana.ui

import ai.dana.R
import ai.dana.domain.ProviderProfile
import ai.dana.domain.ProviderProtocol
import ai.dana.domain.RouterFreeModel
import ai.dana.domain.acceptsCredential
import ai.dana.localai.DeviceModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ReadyModels(
    bundled: Set<DeviceModel>?, selected: ChatDestination?, enabled: Boolean,
    onSelect: (DeviceModel) -> Unit, onMore: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.workspace_chat_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.models_first_launch), style = MaterialTheme.typography.bodyMedium, color = Titanium)
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            DeviceModel.entries.forEach { model ->
                LocalModelRow(model, bundled, selected == ChatDestination.Local(model), enabled, onSelect)
            }
        }
        OutlinedButton(onClick = onMore, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.models_online_browse))
        }
    }
}

@Composable
internal fun ModelPicker(
    bundled: Set<DeviceModel>?, selected: ChatDestination?, profiles: List<ProviderProfile>,
    catalog: Reading<List<RouterFreeModel>>?, offline: Boolean,
    choose: (ChatDestination) -> Unit, signIn: (RouterFreeModel) -> Unit,
    fetch: () -> Unit, onSettings: () -> Unit, onTeam: () -> Unit, dismiss: () -> Unit,
) {
    val freeAccount = profiles.firstOrNull { it.config.protocol == ProviderProtocol.OPENROUTER_FREE && it.hasCredential }
    val otherProfiles = profiles.filter { it.config.protocol != ProviderProtocol.OPENROUTER_FREE && it.config.endpoint.acceptsCredential(it.hasCredential) }
    val models = listOf(RouterFreeModel.Automatic) + (catalog as? Reading.Ready)?.value.orEmpty()
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.models_picker)) }, text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 474.dp).testTag("model-picker-list").selectableGroup(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            item { GroupTitle(R.string.models_device) }
            items(DeviceModel.entries, key = { it.id }) { model ->
                LocalModelRow(model, bundled, selected == ChatDestination.Local(model), true) { choose(ChatDestination.Local(it)) }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 5.dp))
                GroupTitle(R.string.models_online_title)
                Text(stringResource(R.string.models_online_help), style = MaterialTheme.typography.bodySmall, color = Titanium)
            }
            items(models, key = { it.id }) { model ->
                val active = selected is ChatDestination.OnlineFree && selected.model.id == model.id
                val label = when {
                    freeAccount == null -> R.string.models_need_sign_in
                    offline -> R.string.models_network_off
                    else -> R.string.models_online_signed_in
                }
                ModelRow(model.name, stringResource(label), active, true, Modifier.testTag("free-${model.id}")) {
                    if (freeAccount != null) choose(ChatDestination.OnlineFree(freeAccount.id, model)) else signIn(model)
                }
            }
            item {
                if (catalog == Reading.Loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                else {
                    if (catalog is Reading.Failed) ReadError(catalog.message)
                    if (catalog is Reading.Ready && catalog.value.isEmpty()) Text(stringResource(R.string.models_empty_catalog), color = Titanium)
                    TextButton(fetch, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(if (catalog is Reading.Ready) R.string.models_reload_online else R.string.models_fetch_online))
                    }
                }
            }
            if (otherProfiles.isNotEmpty()) {
                item { GroupTitle(R.string.models_saved) }
                items(otherProfiles.take(60), key = { it.id }) { profile ->
                    val destination = ChatDestination.Saved(profile.id)
                    ModelRow(profile.config.name, profile.config.model.removePrefix("models/"), selected == destination, true,
                        Modifier.testTag("saved-${profile.config.protocol}-${profile.config.model}")) { choose(destination) }
                }
            }
            item {
                if (profiles.size >= 2 && !offline) TextButton(onClick = onTeam, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.chat_team_select))
                }
                TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.models_other_services))
                }
            }
        }
    }, confirmButton = { TextButton(dismiss) { Text(stringResource(R.string.chat_done)) } })
}

@Composable
private fun GroupTitle(label: Int) {
    Text(stringResource(label), color = Mint, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun LocalModelRow(model: DeviceModel, bundled: Set<DeviceModel>?, selected: Boolean, enabled: Boolean, select: (DeviceModel) -> Unit) {
    val detail = when {
        bundled == null -> R.string.status_checking
        model in bundled -> model.blurb
        else -> R.string.models_device_unsupported
    }
    ModelRow(model.title, stringResource(detail), selected, enabled && bundled?.contains(model) == true,
        Modifier.testTag("local-${model.id}")) { select(model) }
}

@Composable
private fun ModelRow(title: String, detail: String, selected: Boolean, enabled: Boolean, modifier: Modifier, select: () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 58.dp).clip(MaterialTheme.shapes.medium)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = select)
        .padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (selected) Mint else Ice, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Titanium)
        }
    }
}
