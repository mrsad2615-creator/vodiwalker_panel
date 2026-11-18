package ai.dana.ui

import ai.dana.R
import ai.dana.domain.APPLE_MAC_MODEL
import ai.dana.domain.GOOGLE_GEMINI_BASE_URL
import ai.dana.domain.ApiCredential
import ai.dana.domain.LocalText
import ai.dana.domain.ProviderConfig
import ai.dana.domain.ProviderNetwork
import ai.dana.domain.ProviderProtocol
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

@Composable
internal fun DanaEditor(editor: Editor, session: DashboardSession, offline: Boolean, dispatch: (DanaIntent) -> Unit) {
    val busy = session.busy
    val error = session.error
    val dismiss = { dispatch(DanaIntent.DismissEditor) }
    when (editor) {
        is Editor.Note -> NoteEditor(editor, busy, error, dismiss) { dispatch(DanaIntent.SaveNote(it)) }
        is Editor.Provider -> ProviderEditor(editor, busy, error, session.directory, offline, dismiss, dispatch)
        is Editor.ConfirmComparison -> EditorDialog(
            title = R.string.compare_confirm_title,
            button = R.string.compare_send,
            enabled = true,
            busy = busy,
            error = error,
            onDismiss = dismiss,
            onConfirm = { dispatch(DanaIntent.SendComparison) },
        ) {
            Text(stringResource(R.string.compare_confirm_body))
            Text(stringResource(taskLabel(editor.request.task)), color = Ice)
            if (editor.request.targets.any { it.config.protocol == ProviderProtocol.APPLE_MAC }) {
                Text(stringResource(R.string.apple_language_note), color = Amber)
            }
            for (profile in editor.request.targets) {
                Text(profile.config.name, style = MaterialTheme.typography.titleSmall)
                TechnicalText(profile.config.baseUrl, maxLines = Int.MAX_VALUE)
                TechnicalText(profile.config.model)
            }
        }
        is Editor.DeleteNote, is Editor.DeleteProvider -> {
            val note = editor is Editor.DeleteNote
            EditorDialog(
                title = if (note) R.string.note_delete_title else R.string.engine_delete_title,
                button = R.string.delete,
                enabled = true,
                busy = busy,
                error = error,
                onDismiss = dismiss,
                onConfirm = { dispatch(DanaIntent.ConfirmDeletion) },
            ) {
                Text(stringResource(if (note) R.string.note_delete_body else R.string.engine_delete_body))
            }
        }
    }
}

@Composable
private fun NoteEditor(editor: Editor.Note, busy: Boolean, error: Int?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var draft by remember { mutableStateOf(editor.draft) }
    EditorDialog(
        title = if (editor.previous == null) R.string.note_add else R.string.note_edit,
        button = R.string.save,
        enabled = draft.isNotBlank() && draft.length <= LocalText.NOTE_LIMIT,
        busy = busy,
        error = error,
        onDismiss = onDismiss,
        onConfirm = { onSave(draft) },
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { if (it.length <= LocalText.NOTE_LIMIT || it.length < draft.length) draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.note_input)) },
            enabled = !busy,
            minLines = 3,
            maxLines = 7,
            isError = draft.length > LocalText.NOTE_LIMIT,
            supportingText = if (draft.length > LocalText.NOTE_LIMIT) {
                { Text(stringResource(R.string.error_note_long)) }
            } else null,
        )
    }
}

@Composable
private fun ProviderEditor(
    editor: Editor.Provider,
    busy: Boolean,
    error: Int?,
    directory: DirectoryState?,
    offline: Boolean,
    onDismiss: () -> Unit,
    dispatch: (DanaIntent) -> Unit,
) {
    val previous = editor.previous?.config
    val protocol = editor.initial
    val apple = protocol == ProviderProtocol.APPLE_MAC
    val gemini = protocol == ProviderProtocol.GEMINI_NATIVE
    val hasKey = editor.previous?.hasCredential == true
    val defaultName = when (protocol) {
        ProviderProtocol.GEMINI_NATIVE -> "Gemini"
        ProviderProtocol.APPLE_MAC -> "Apple · Mac"
        ProviderProtocol.OPENAI_COMPATIBLE -> if (editor.lanPreset) "Ollama / LM Studio" else ""
    }
    var name by remember { mutableStateOf(previous?.name ?: defaultName) }
    var local by remember { mutableStateOf(apple || editor.lanPreset || previous?.network == ProviderNetwork.LOCAL_NETWORK) }
    var customGemini by remember { mutableStateOf(gemini && previous != null && previous.baseUrl != GOOGLE_GEMINI_BASE_URL) }
    var baseUrl by remember { mutableStateOf(previous?.baseUrl ?: if (gemini) GOOGLE_GEMINI_BASE_URL else "") }
    var model by remember { mutableStateOf(previous?.model ?: if (apple) APPLE_MAC_MODEL else "") }
    // No rememberSaveable / SavedStateHandle, clipboard helper, or existing key readback.
    var key by remember { mutableStateOf("") }
    var removeKey by remember { mutableStateOf(false) }
    LaunchedEffect(directory) {
        if (model.isBlank()) (directory as? DirectoryState.Ready)?.result?.models?.singleOrNull()?.let { model = it.id }
    }
    val lookup = directory == DirectoryState.Loading
    val formEnabled = !busy && !lookup
    val network = if (local) ProviderNetwork.LOCAL_NETWORK else ProviderNetwork.INTERNET
    val technical = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = FontFamily.Monospace,
        textDirection = TextDirection.Ltr,
        fontFeatureSettings = "tnum",
    )
    val clear = { dispatch(DanaIntent.ClearDirectory) }
    EditorDialog(
        title = when {
            previous != null -> R.string.engine_edit
            editor.lanPreset -> R.string.engine_add_lan
            else -> presetLabel(protocol)
        },
        button = R.string.save,
        enabled = formEnabled && name.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank(),
        busy = busy,
        error = error,
        secure = true,
        onDismiss = onDismiss,
        onConfirm = { dispatch(DanaIntent.SaveProvider(name, protocol, network, baseUrl, model, key, removeKey)) },
    ) {
        if (gemini) {
            Text(stringResource(R.string.engine_gemini_intro))
            SetupLink(R.string.engine_gemini_key_link, "https://aistudio.google.com/api-keys", formEnabled)
        } else if (apple) {
            Text(stringResource(R.string.engine_apple_intro))
            Text(stringResource(R.string.apple_language_note), style = MaterialTheme.typography.bodySmall, color = Amber)
            SetupLink(R.string.engine_apple_guide, APPLE_GUIDE_URL, formEnabled)
        } else if (editor.lanPreset) Text(stringResource(R.string.engine_lan_intro))
        OutlinedTextField(
            name, { if (it.length <= ProviderConfig.NAME_LIMIT) name = it }, Modifier.fillMaxWidth(),
            enabled = formEnabled, singleLine = true, label = { Text(stringResource(R.string.engine_name)) },
        )
        if (gemini && previous == null) {
            ToggleLine(R.string.engine_gemini_proxy, customGemini, formEnabled) {
                customGemini = it
                baseUrl = if (it) "" else GOOGLE_GEMINI_BASE_URL
                key = ""
                model = ""
                if (!it) local = false
                clear()
            }
        }
        if (!apple && !editor.lanPreset && (!gemini || customGemini || previous?.network == ProviderNetwork.LOCAL_NETWORK)) {
            ToggleLine(R.string.engine_lan, local, formEnabled) { local = it; clear() }
        }
        if (gemini && !customGemini) {
            // The turnkey preset cannot silently send a Google key to an edited host.
            TechnicalText(GOOGLE_GEMINI_BASE_URL)
        } else {
            OutlinedTextField(
                baseUrl, { if (it.length <= ProviderConfig.URL_LIMIT) { baseUrl = it; clear() } }, Modifier.fillMaxWidth(),
                enabled = formEnabled, singleLine = true, textStyle = technical,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                label = { Text(stringResource(R.string.engine_base_url)) },
                supportingText = { Text(stringResource(if (apple) R.string.engine_apple_url_tip else R.string.engine_url_tip)) },
            )
        }
        if (customGemini) Text(stringResource(R.string.engine_gemini_proxy_notice), color = Amber)
        if (hasKey) {
            ToggleLine(R.string.engine_remove_key, removeKey, formEnabled) {
                removeKey = it
                if (it) key = ""
                clear()
            }
        }
        OutlinedTextField(
            key, { if (it.length <= ApiCredential.LIMIT) { key = it; clear() } }, Modifier.fillMaxWidth(),
            enabled = formEnabled && !removeKey, singleLine = true, textStyle = technical,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            visualTransformation = PasswordVisualTransformation(),
            label = { Text(stringResource(when { apple -> R.string.engine_apple_token; gemini -> R.string.engine_gemini_key; else -> R.string.engine_key })) },
            supportingText = {
                if (hasKey) Text(stringResource(R.string.engine_keep_key))
                else if (!apple && !gemini) Text(stringResource(R.string.engine_key_hint))
            },
        )
        HorizontalDivider(color = Titanium.copy(alpha = 0.28f))
        Text(stringResource(if (offline) R.string.engine_lookup_offline else R.string.engine_lookup_privacy),
            style = MaterialTheme.typography.bodySmall, color = Titanium)
        val keyAvailable = key.isNotBlank() || (hasKey && !removeKey)
        if (lookup) {
            Text(stringResource(R.string.engine_lookup_loading), color = Ice)
            TextButton(onClick = clear) { Text(stringResource(R.string.engine_lookup_cancel)) }
        } else {
            TextButton(
                enabled = formEnabled && !offline && baseUrl.isNotBlank() && ((!apple && !gemini) || keyAvailable),
                onClick = { dispatch(DanaIntent.ReadModels(protocol, network, baseUrl, key, removeKey)) },
            ) { Text(stringResource(if (apple) R.string.engine_apple_check else R.string.engine_lookup)) }
        }
        when (directory) {
            is DirectoryState.Failed -> {
                ReadError(directory.message)
                directory.httpStatus?.let { TechnicalText("HTTP $it") }
            }
            is DirectoryState.Ready -> {
                val result = directory.result
                Text(stringResource(R.string.engine_lookup_elapsed), style = MaterialTheme.typography.labelMedium)
                TechnicalText("${result.elapsedMillis} ms")
                if (result.models.isEmpty()) Text(stringResource(R.string.engine_lookup_empty))
                else if (apple) Text(stringResource(R.string.engine_apple_ready), color = Mint)
                else {
                    Text(stringResource(R.string.engine_lookup_models), style = MaterialTheme.typography.labelMedium)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                        items(result.models, key = { it.id }) { item ->
                            TextButton(onClick = { model = item.id }, modifier = Modifier.fillMaxWidth(), enabled = formEnabled) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(item.label, style = technical, color = if (model == item.id) Mint else Ice)
                                    if (item.label != item.id) TechnicalText(item.id)
                                }
                            }
                        }
                    }
                }
            }
            else -> Unit
        }
        if (!apple) OutlinedTextField(
            model, { if (it.length <= ProviderConfig.MODEL_LIMIT) model = it }, Modifier.fillMaxWidth(),
            enabled = formEnabled, singleLine = true, textStyle = technical,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            label = { Text(stringResource(R.string.engine_model)) },
        )
    }
}

private const val APPLE_GUIDE_URL =
    "https://github.com/mrsad2615-creator/vodiwalker_panel/blob/arena/13014174-vodiwalker-panel/dana-android/docs/03-engine-guide-fa.md"

@Composable
private fun SetupLink(label: Int, target: String, enabled: Boolean) {
    val browser = LocalUriHandler.current
    var failed by remember(target) { mutableStateOf(false) }
    TextButton(onClick = { failed = runCatching { browser.openUri(target) }.isFailure }, enabled = enabled) {
        Text(stringResource(label))
    }
    if (failed) SelectionContainer { TechnicalText(target, maxLines = Int.MAX_VALUE) }
}

internal fun presetLabel(protocol: ProviderProtocol): Int = when (protocol) {
    ProviderProtocol.GEMINI_NATIVE -> R.string.engine_add_gemini
    ProviderProtocol.APPLE_MAC -> R.string.engine_add_apple
    ProviderProtocol.OPENAI_COMPATIBLE -> R.string.engine_add_custom
}

@Composable
internal fun ToggleLine(label: Int, checked: Boolean, enabled: Boolean, setChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = setChecked),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun EditorDialog(
    title: Int,
    button: Int,
    enabled: Boolean,
    busy: Boolean,
    error: Int?,
    secure: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    contents: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !busy,
            dismissOnClickOutside = !busy,
            securePolicy = if (secure) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit,
        ),
        title = { Text(stringResource(title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                error?.let { ReadError(it) }
                contents()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = enabled && !busy) {
                Text(stringResource(if (busy) R.string.saving else button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        },
    )
}
