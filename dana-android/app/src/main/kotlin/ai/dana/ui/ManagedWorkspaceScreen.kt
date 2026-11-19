package ai.dana.ui

import ai.dana.R
import ai.dana.domain.ArtShape
import ai.dana.domain.ManagedKind
import ai.dana.domain.ManagedModel
import ai.dana.domain.ManagedProblem
import ai.dana.domain.MessageRole
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

@Composable
internal fun ManagedWorkspaceScreen(
    model: ManagedWorkspaceViewModel,
    state: ManagedWorkspaceState,
    kind: ManagedKind,
    openAccount: () -> Unit,
    enableNetwork: () -> Unit,
    openLocal: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val chat = kind == ManagedKind.CHAT
    val selected = state.selected(kind)
    val busy = state.running != null
    val choices = state.available(kind)
    val ready = state.resumed && !state.offline && state.catalogError == null && choices.isNotEmpty()
    val remaining = state.catalog?.dailyRemaining ?: 0
    val hasUnits = selected != null && selected.units <= remaining
    val prompt = if (chat) state.chatDraft else state.imageDraft
    val list = rememberLazyListState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(state.turns.lastOrNull(), state.preview?.id) {
        if (chat && state.turns.isNotEmpty()) list.requestScrollToItem(state.turns.size)
        else if (!chat && state.preview != null) list.requestScrollToItem(0)
    }
    Column(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("managed-workspace-list"), state = list,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionIntro(if (chat) R.string.managed_chat_title else R.string.art_title, R.string.managed_intro)
                openLocal?.let { TextButton(onClick = it, enabled = !busy) { Text(stringResource(R.string.managed_open_local)) } }
            }
            when {
                !state.resumed -> item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.managed_email_help), color = Titanium)
                        Button(openAccount, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.managed_login)) }
                    }
                }
                state.offline -> item { OfflinePanel(R.string.managed_offline, true, enableNetwork) }
                state.catalogError != null -> item {
                    ReadError(managedError(state.catalogError))
                    if (state.catalogError == ManagedProblem.SIGN_IN) TextButton(openAccount) { Text(stringResource(R.string.managed_login)) }
                    else TextButton(model::refresh) { Text(stringResource(R.string.retry)) }
                }
                state.loading && state.catalog == null -> item { Loading() }
                else -> {
                    if (chat) items(state.turns) { turn ->
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text(turn.question, style = MaterialTheme.typography.titleMedium)
                            Text("${turn.model.provider} · ${turn.model.title}", color = Mint, style = MaterialTheme.typography.labelLarge)
                            SelectionContainer { Text(turn.answer) }
                        }
                    }
                    if (!chat) state.preview?.let { result -> item(key = result.id) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            StudioPill(stringResource(R.string.art_ready))
                            Image(result.bitmap.asImageBitmap(), stringResource(R.string.art_preview), Modifier.fillMaxWidth().heightIn(max = 400.dp),
                                contentScale = ContentScale.Fit)
                            Text(result.engine, style = MaterialTheme.typography.labelLarge)
                            ExportButton(R.string.art_save, result.id, result.image.mime, "mrsad-image.${result.image.extension}", result.image::writeTo)
                        }
                    } }
                    item {
                        GlassCard(Modifier.fillMaxWidth()) {
                            val catalog = state.catalog
                            if (choices.isEmpty()) Text(stringResource(R.string.managed_no_models), color = Titanium)
                            else {
                                OutlinedButton(onClick = { focus.clearFocus(); keyboard?.hide(); picking = true }, enabled = !busy && !state.loading,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("managed-picker")) {
                                    Text(selected?.let { "${it.provider} · ${it.title}" } ?: stringResource(R.string.models_picker))
                                }
                                if (chat && state.turns.isNotEmpty()) TextButton(model::newChat, enabled = !busy) { Text(stringResource(R.string.chat_new)) }
                                if (!chat && selected != null) {
                                    selected.quality?.let { Text(stringResource(R.string.managed_image_quality, it), color = Mint) }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        ArtShape.entries.forEach { shape ->
                                            FilterChip(selected = state.shape == shape, onClick = { model.shape(shape) }, enabled = !busy,
                                                modifier = Modifier.weight(1f), label = { Text(stringResource(shape.label)) })
                                        }
                                    }
                                }
                                OutlinedTextField(prompt, { model.draft(kind, it) }, Modifier.fillMaxWidth(), enabled = !busy,
                                    label = { Text(stringResource(if (chat) R.string.chat_prompt else R.string.art_prompt)) }, minLines = 2, maxLines = 5)
                                catalog?.let { Text(stringResource(R.string.managed_remaining, faDigits(it.dailyRemaining)), color = Titanium) }
                                state.error?.let { ReadError(it) }
                                if (state.error == R.string.managed_login_again) TextButton(openAccount) { Text(stringResource(R.string.managed_login)) }
                                if (selected != null && !hasUnits && !busy) {
                                    Text(stringResource(R.string.managed_quota), color = Amber)
                                    TextButton(model::refresh, enabled = !state.loading) { Text(stringResource(R.string.managed_refresh_catalog)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Keep the action above the keyboard on small screens, rather than under the model form.
        if (ready) Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp)) {
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(model::stop, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.compare_stop)) }
            } else {
                Button(onClick = { focus.clearFocus(); keyboard?.hide(); model.propose(kind) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = hasUnits && !state.loading && prompt.isNotBlank()) {
                    Text(stringResource(if (chat) R.string.workspace_send else R.string.art_generate))
                }
            }
        }
    }
    if (picking) ManagedPicker(choices, model::select) { picking = false }
    state.approval?.takeIf { it.model.kind == kind }?.let { plan ->
        AlertDialog(onDismissRequest = model::dismiss, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.managed_preview_title)) }, text = {
                Column(Modifier.heightIn(max = 384.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${plan.model.provider} · ${plan.model.title}", style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.managed_preview_help, faDigits(plan.model.units)), color = Titanium)
                    if (plan.input == null) Text("${plan.model.quality} · ${stringResource(plan.shape.label)}", color = Mint)
                    val history = plan.input?.history.orEmpty()
                    if (history.isNotEmpty()) {
                        Text(stringResource(R.string.managed_history_review), style = MaterialTheme.typography.labelLarge)
                        for (turn in history) {
                            Text(stringResource(if (turn.role == MessageRole.USER) R.string.managed_you else R.string.managed_answer), color = Titanium)
                            Text(turn.body)
                        }
                    }
                    Text(stringResource(R.string.managed_exact), style = MaterialTheme.typography.labelLarge)
                    Text(plan.outbound, Modifier.testTag("managed-outbound"))
                }
            }, confirmButton = { Button(onClick = { model.approve(plan) }, enabled = !state.offline && !state.loading) { Text(stringResource(R.string.message_send_once)) } },
            dismissButton = { TextButton(model::dismiss) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun ManagedPicker(models: List<ManagedModel>, select: (ManagedModel) -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.managed_select)) }, text = {
        LazyColumn(Modifier.heightIn(max = 370.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(models, key = { it.id }) { model ->
                OutlinedButton(onClick = { select(model); dismiss() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("${model.provider} · ${model.title}")
                }
            }
        }
    }, confirmButton = { TextButton(dismiss) { Text(stringResource(R.string.chat_done)) } })
}

private val ArtShape.label: Int get() = when (this) {
    ArtShape.SQUARE -> R.string.art_square
    ArtShape.LANDSCAPE -> R.string.art_landscape
    ArtShape.PORTRAIT -> R.string.art_portrait
}
