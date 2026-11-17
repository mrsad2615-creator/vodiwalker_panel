package ai.dana.ui

import ai.dana.R
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
internal fun DanaEditor(editor: Editor, busy: Boolean, error: Int?, dispatch: (DanaIntent) -> Unit) {
    val dismiss = { dispatch(DanaIntent.DismissEditor) }
    when (editor) {
        is Editor.Note -> NoteEditor(editor, busy, error, dismiss) { dispatch(DanaIntent.SaveNote(it)) }
        is Editor.Provider -> ProviderEditor(editor, busy, error, dismiss, dispatch)
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
            for (profile in editor.request.targets) {
                Text(profile.config.name, style = MaterialTheme.typography.titleSmall)
                TechnicalText(profile.config.baseUrl)
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
    var draft by remember { mutableStateOf(editor.previous?.text.orEmpty()) }
    EditorDialog(
        title = if (editor.previous == null) R.string.note_add else R.string.note_edit,
        button = R.string.save,
        enabled = draft.isNotBlank(),
        busy = busy,
        error = error,
        onDismiss = onDismiss,
        onConfirm = { onSave(draft) },
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { if (it.length <= LocalText.NOTE_LIMIT) draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.note_input)) },
            enabled = !busy,
            minLines = 3,
            maxLines = 7,
        )
    }
}

@Composable
private fun ProviderEditor(
    editor: Editor.Provider,
    busy: Boolean,
    error: Int?,
    onDismiss: () -> Unit,
    dispatch: (DanaIntent) -> Unit,
) {
    val previous = editor.previous?.config
    val hasKey = editor.previous?.hasCredential == true
    var name by remember { mutableStateOf(previous?.name.orEmpty()) }
    var protocol by remember { mutableStateOf(previous?.protocol ?: ProviderProtocol.OPENAI_COMPATIBLE) }
    var local by remember { mutableStateOf(previous?.network == ProviderNetwork.LOCAL_NETWORK) }
    var baseUrl by remember { mutableStateOf(previous?.baseUrl.orEmpty()) }
    var model by remember { mutableStateOf(previous?.model.orEmpty()) }
    // No rememberSaveable / SavedStateHandle, clipboard helper, or existing key readback.
    var key by remember { mutableStateOf("") }
    var removeKey by remember { mutableStateOf(false) }
    val technical = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = FontFamily.Monospace,
        textDirection = TextDirection.Ltr,
        fontFeatureSettings = "tnum",
    )
    EditorDialog(
        title = if (previous == null) R.string.engine_add else R.string.engine_edit,
        button = R.string.save,
        enabled = name.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank(),
        busy = busy,
        error = error,
        secure = true,
        onDismiss = onDismiss,
        onConfirm = {
            dispatch(
                DanaIntent.SaveProvider(
                    name, protocol, if (local) ProviderNetwork.LOCAL_NETWORK else ProviderNetwork.INTERNET,
                    baseUrl, model, key, removeKey,
                ),
            )
        },
    ) {
        OutlinedTextField(
            name, { if (it.length <= ProviderConfig.NAME_LIMIT) name = it }, Modifier.fillMaxWidth(),
            enabled = !busy, singleLine = true, label = { Text(stringResource(R.string.engine_name)) },
        )
        Text(stringResource(R.string.engine_protocol), style = MaterialTheme.typography.labelLarge)
        ProviderProtocol.entries.forEach { option ->
            FilterChip(
                selected = option == protocol,
                onClick = { protocol = option },
                enabled = !busy,
                label = { Text(stringResource(protocolLabel(option)), style = technical) },
            )
        }
        ToggleLine(R.string.engine_lan, local, !busy) { local = it }
        OutlinedTextField(
            baseUrl, { if (it.length <= ProviderConfig.URL_LIMIT) baseUrl = it }, Modifier.fillMaxWidth(),
            enabled = !busy, singleLine = true, textStyle = technical,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            label = { Text(stringResource(R.string.engine_base_url)) },
            supportingText = { Text(stringResource(R.string.engine_url_tip)) },
        )
        OutlinedTextField(
            model, { if (it.length <= ProviderConfig.MODEL_LIMIT) model = it }, Modifier.fillMaxWidth(),
            enabled = !busy, singleLine = true, textStyle = technical,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            label = { Text(stringResource(R.string.engine_model)) },
        )
        if (hasKey) {
            ToggleLine(R.string.engine_remove_key, removeKey, !busy) {
                removeKey = it
                if (it) key = ""
            }
        }
        OutlinedTextField(
            key, { if (it.length <= ApiCredential.LIMIT) key = it }, Modifier.fillMaxWidth(),
            enabled = !busy && !removeKey, singleLine = true, textStyle = technical,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            visualTransformation = PasswordVisualTransformation(),
            label = { Text(stringResource(R.string.engine_key)) },
            supportingText = {
                Text(stringResource(if (hasKey) R.string.engine_keep_key else R.string.engine_key_hint))
            },
        )
    }
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
