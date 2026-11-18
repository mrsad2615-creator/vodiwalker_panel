package ai.dana.ui

import ai.dana.R
import ai.dana.domain.APPLE_MAC_MODEL
import ai.dana.domain.GOOGLE_GEMINI_BASE_URL
import ai.dana.domain.OPENROUTER_BASE_URL
import ai.dana.domain.OPENROUTER_FREE_MODEL
import ai.dana.domain.ApiCredential
import ai.dana.domain.PhotoSupport
import ai.dana.domain.LocalText
import ai.dana.domain.ProviderConfig
import ai.dana.domain.ProviderNetwork
import ai.dana.domain.ProviderProtocol
import ai.dana.domain.QuickService
import ai.dana.domain.RouterApproval
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalFocusManager
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
        is Editor.Provider -> if (editor.initial == ProviderProtocol.OPENROUTER_FREE) {
            OpenRouterEditor(editor, session, offline, dismiss, dispatch)
        } else ProviderEditor(editor, busy, error, session.directory, offline, dismiss, dispatch)
        is Editor.Quick -> QuickEditor(editor, session, dismiss, dispatch)
        is Editor.Test -> ProbeEditor(editor, session, dismiss, dispatch)
        is Editor.ConfirmComparison -> EditorDialog(
            title = R.string.compare_confirm_title,
            button = R.string.send_allow,
            enabled = !offline,
            busy = busy,
            error = error,
            secure = true,
            onDismiss = dismiss,
            onConfirm = { dispatch(DanaIntent.SendComparison) },
        ) {
            Text(stringResource(R.string.compare_confirm_body))
            Text(stringResource(taskLabel(editor.request.task)), color = Ice)
            SelectionContainer { Text(editor.request.prompt) }
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
private fun QuickEditor(editor: Editor.Quick, session: DashboardSession, dismiss: () -> Unit, dispatch: (DanaIntent) -> Unit) {
    val forImage = editor.after == DashboardTab.ART
    val options = if (forImage) QuickService.entries.filter { it.supportsImage }
        else listOf(QuickService.FREE, QuickService.AVALAI) + QuickService.entries.filter { it !in listOf(QuickService.FREE, QuickService.AVALAI) }
    var service by remember { mutableStateOf(editor.initial.takeIf { it in options } ?: options.first()) }
    var expanded by remember { mutableStateOf(false) }
    var secret by remember(service) { mutableStateOf("") }
    var imageOnly by remember { mutableStateOf(false) }
    var browserError by remember { mutableStateOf(false) }
    val browser = LocalUriHandler.current
    val focus = LocalFocusManager.current
    val busy = session.busy
    val selected = service
    val code = session.approval.takeIf { selected == QuickService.FREE }
    val valid = secret.isNotBlank() && (code == null || secret.length >= 8) &&
        (selected != QuickService.FREE || code != null || secret.startsWith("sk-or-v1-"))
    EditorDialog(R.string.workspace_connect, R.string.quick_connect, valid, busy, session.error, secure = true, onDismiss = dismiss,
        onConfirm = {
            focus.clearFocus()
            if (service == selected) dispatch(DanaIntent.Connect(selected, secret, code, imageOnly))
        }) {
        Column {
            OutlinedButton(onClick = { expanded = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.quick_provider, selected.title))
            }
            DropdownMenu(expanded, { expanded = false }) {
                options.forEach { candidate ->
                    DropdownMenuItem(text = { Text(candidate.title) }, onClick = {
                        service = candidate
                        expanded = false
                        imageOnly = false
                        browserError = false
                        dispatch(DanaIntent.SetupApproval(null))
                    })
                }
            }
        }
        if (selected == QuickService.FREE) {
            Text(stringResource(R.string.workspace_free_hint), style = MaterialTheme.typography.bodySmall, color = Titanium)
            OutlinedButton(onClick = {
                val next = RouterApproval.create()
                secret = ""
                dispatch(DanaIntent.SetupApproval(next))
                browserError = runCatching { browser.openUri(next.browserUrl) }.isFailure
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.quick_browser)) }
            if (code != null) {
                Text(stringResource(R.string.workspace_code_hint), color = Titanium, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { dispatch(DanaIntent.SetupApproval(null)); secret = ""; browserError = false }, enabled = !busy) {
                    Text(stringResource(R.string.quick_use_key))
                }
            }
        } else Text(stringResource(if (selected.supportsImage) R.string.workspace_same_key else R.string.workspace_credit),
            style = MaterialTheme.typography.bodySmall, color = Titanium)
        if (browserError) ReadError(R.string.quick_no_browser)
        if (code == null) SetupLink(R.string.workspace_get_key, selected.keyUrl, !busy)
        OutlinedTextField(secret, { if (it.length <= ApiCredential.LIMIT) secret = it.trim() }, Modifier.fillMaxWidth(),
            enabled = !busy, singleLine = true,
            label = { Text(stringResource(if (code == null) R.string.quick_key else R.string.quick_code)) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            visualTransformation = PasswordVisualTransformation())
        if (forImage) ToggleLine(R.string.workspace_image_only, imageOnly, !busy, { imageOnly = it })
        if (!imageOnly) TechnicalText(selected.models.first())
        Text(stringResource(if (imageOnly) R.string.workspace_image_probe else R.string.workspace_connect_consent),
            style = MaterialTheme.typography.bodySmall, color = Titanium)
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.quick_testing), style = MaterialTheme.typography.bodySmall, color = Mint)
            TextButton(onClick = { dispatch(DanaIntent.StopSetup) }) { Text(stringResource(R.string.compare_stop)) }
        }
        session.httpStatus?.let { TechnicalText("HTTP $it") }
        if (!busy) SetupLink(R.string.workspace_hoosha_web,
            if (forImage) "https://hoosha.com/image/generate" else "https://hoosha.com/ai/", true)
    }
}

@Composable
private fun ProbeEditor(editor: Editor.Test, session: DashboardSession, dismiss: () -> Unit, dispatch: (DanaIntent) -> Unit) {
    var direct by remember { mutableStateOf(true) }
    EditorDialog(R.string.quick_test_title, R.string.quick_connect, true, session.busy, session.error, secure = true,
        onDismiss = dismiss, onConfirm = { dispatch(DanaIntent.ActivateProfile(direct)) }) {
        Text(editor.profile.config.name, style = MaterialTheme.typography.titleMedium)
        TechnicalText(editor.profile.config.baseUrl, maxLines = 4)
        TechnicalText(editor.profile.config.model, maxLines = 3)
        if (editor.profile.config.protocol != ProviderProtocol.OPENROUTER_FREE) Text(stringResource(R.string.quick_paid), color = Amber)
        ProbeInfo(session.busy, session.httpStatus, direct, { direct = it }, { dispatch(DanaIntent.StopSetup) })
    }
}

@Composable
private fun ProbeInfo(busy: Boolean, status: Int?, direct: Boolean, onDirect: (Boolean) -> Unit, stop: () -> Unit) {
    Text(stringResource(R.string.quick_probe_help), style = MaterialTheme.typography.bodySmall, color = Titanium)
    ToggleLine(R.string.quick_direct, direct, !busy, onDirect)
    if (busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(stringResource(R.string.quick_testing), color = Mint)
        TextButton(onClick = stop) { Text(stringResource(R.string.compare_stop)) }
    }
    status?.let { TechnicalText("HTTP $it") }
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
    val cloud = editor.cloud
    val defaultName = cloud?.label ?: when (protocol) {
        ProviderProtocol.GEMINI_NATIVE -> "Gemini"
        ProviderProtocol.OPENROUTER_FREE -> "OpenRouter Free"
        ProviderProtocol.APPLE_MAC -> "Apple · Mac"
        ProviderProtocol.OPENAI_COMPATIBLE -> if (editor.lanPreset) "Ollama / LM Studio" else ""
    }
    var name by remember { mutableStateOf(previous?.name ?: defaultName) }
    var local by remember { mutableStateOf(apple || editor.lanPreset || previous?.network == ProviderNetwork.LOCAL_NETWORK) }
    var customGemini by remember { mutableStateOf(gemini && previous != null && previous.baseUrl != GOOGLE_GEMINI_BASE_URL) }
    var baseUrl by remember { mutableStateOf(previous?.baseUrl ?: cloud?.baseUrl ?: if (gemini) GOOGLE_GEMINI_BASE_URL else "") }
    var model by remember { mutableStateOf(previous?.model ?: if (apple) APPLE_MAC_MODEL else "") }
    // No rememberSaveable / SavedStateHandle, clipboard helper, or existing key readback.
    var key by remember { mutableStateOf("") }
    var removeKey by remember { mutableStateOf(false) }
    LaunchedEffect(directory) {
        if (model.isBlank()) {
            val found = (directory as? DirectoryState.Ready)?.result?.models.orEmpty()
            val recommended = cloud?.suggested?.firstOrNull { id -> found.any { it.id == id } }
            model = recommended ?: found.singleOrNull()?.id.orEmpty()
        }
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
            cloud != null -> R.string.engine_cloud_editor
            else -> presetLabel(protocol)
        },
        button = R.string.save,
        enabled = formEnabled && name.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank() &&
            (cloud == null || key.isNotBlank() || (hasKey && !removeKey)),
        busy = busy,
        error = error,
        secure = true,
        onDismiss = onDismiss,
        onConfirm = { dispatch(DanaIntent.SaveProvider(name, protocol, network, baseUrl, model, key, removeKey)) },
    ) {
        if (gemini) {
            Text(stringResource(R.string.engine_gemini_intro))
            Text(stringResource(R.string.engine_gemini_tier), color = Titanium, style = MaterialTheme.typography.bodySmall)
            SetupLink(R.string.engine_pricing_link, "https://ai.google.dev/gemini-api/docs/pricing", formEnabled)
            SetupLink(R.string.engine_gemini_key_link, "https://aistudio.google.com/api-keys", formEnabled)
        } else if (apple) {
            Text(stringResource(R.string.engine_apple_intro))
            Text(stringResource(R.string.apple_language_note), style = MaterialTheme.typography.bodySmall, color = Amber)
            SetupLink(R.string.engine_apple_guide, APPLE_GUIDE_URL, formEnabled)
        } else if (cloud != null) {
            Text(stringResource(R.string.engine_cloud_intro, cloud.label))
            SetupLink(R.string.engine_cloud_console, cloud.consoleUrl, formEnabled)
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
        if (cloud == null && !apple && !editor.lanPreset && (!gemini || customGemini || previous?.network == ProviderNetwork.LOCAL_NETWORK)) {
            ToggleLine(R.string.engine_lan, local, formEnabled) { local = it; clear() }
        }
        if (cloud != null) {
            TechnicalText(baseUrl, maxLines = 4)
        } else if (gemini && !customGemini) {
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
        if (hasKey && cloud == null) {
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
            label = { Text(stringResource(when { apple -> R.string.engine_apple_token; gemini -> R.string.engine_gemini_key; cloud != null -> R.string.engine_cloud_key; else -> R.string.engine_key })) },
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
            if (offline) TextButton(onClick = { dispatch(DanaIntent.SetOffline(false)) }, enabled = formEnabled) {
                Text(stringResource(R.string.studio_allow_network))
            }
            TextButton(
                enabled = formEnabled && !offline && baseUrl.isNotBlank() && ((!apple && !gemini && cloud == null) || keyAvailable),
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
                    val choices = remember(result, cloud) {
                        if (cloud == null) result.models else result.models.sortedWith(compareBy(
                            { cloud.suggested.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE },
                            { it.photoSupport != PhotoSupport.YES }, { it.id }))
                    }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                        items(choices, key = { it.id }) { item ->
                            TextButton(onClick = { model = item.id }, modifier = Modifier.fillMaxWidth(), enabled = formEnabled) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(item.label, style = technical, color = if (model == item.id) Mint else Ice)
                                    if (item.label != item.id) TechnicalText(item.id)
                                    if (item.photoSupport == PhotoSupport.YES) Text(stringResource(R.string.engine_vision),
                                        style = MaterialTheme.typography.labelSmall, color = Mint)
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

@Composable
private fun OpenRouterEditor(editor: Editor.Provider, session: DashboardSession, offline: Boolean, dismiss: () -> Unit, dispatch: (DanaIntent) -> Unit) {
    var key by remember { mutableStateOf("") }
    val hasKey = editor.previous?.hasCredential == true
    val checking = session.directory == DirectoryState.Loading
    val available = !session.busy && !checking
    val valid = (key.startsWith("sk-or-v1-") && key.length in 24..ApiCredential.LIMIT && key.all { it in '!'..'~' }) || (hasKey && key.isEmpty())
    EditorDialog(
        title = R.string.engine_add_free,
        button = R.string.engine_free_save,
        enabled = valid && available,
        busy = session.busy,
        error = session.error,
        secure = true,
        onDismiss = dismiss,
        onConfirm = { dispatch(DanaIntent.SaveProvider("OpenRouter Free", ProviderProtocol.OPENROUTER_FREE,
            ProviderNetwork.INTERNET, OPENROUTER_BASE_URL, OPENROUTER_FREE_MODEL, key, removeKey = false)) },
    ) {
        Text(stringResource(R.string.engine_free_intro))
        TechnicalText(OPENROUTER_FREE_MODEL)
        SetupLink(R.string.engine_openrouter_key_link, "https://openrouter.ai/settings/keys", available)
        OutlinedTextField(key, { if (it.length <= ApiCredential.LIMIT) { key = it.trim(); dispatch(DanaIntent.ClearDirectory) } },
            Modifier.fillMaxWidth(), enabled = available, singleLine = true,
            label = { Text(stringResource(R.string.engine_openrouter_key_label)) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            visualTransformation = PasswordVisualTransformation(),
            supportingText = if (hasKey) { { Text(stringResource(R.string.engine_keep_key)) } } else null)
        if (offline) {
            Text(stringResource(R.string.engine_free_offline), color = Titanium, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { dispatch(DanaIntent.SetOffline(false)) }, enabled = available) {
                Text(stringResource(R.string.studio_allow_network))
            }
        } else if (checking) {
            Text(stringResource(R.string.engine_lookup_loading))
            TextButton(onClick = { dispatch(DanaIntent.ClearDirectory) }) { Text(stringResource(R.string.engine_lookup_cancel)) }
        } else TextButton(onClick = { dispatch(DanaIntent.ReadModels(ProviderProtocol.OPENROUTER_FREE, ProviderNetwork.INTERNET,
            OPENROUTER_BASE_URL, key, removeKey = false)) }, enabled = available && valid) {
            Text(stringResource(R.string.engine_free_check))
        }
        when (val lookup = session.directory) {
            is DirectoryState.Ready -> Text(stringResource(R.string.engine_free_verified), color = Mint)
            is DirectoryState.Failed -> { ReadError(lookup.message); lookup.httpStatus?.let { TechnicalText("HTTP $it") } }
            else -> Unit
        }
    }
}

private const val APPLE_GUIDE_URL =
    "https://github.com/mrsad2615-creator/vodiwalker_panel/blob/arena/13014174-vodiwalker-panel/dana-android/docs/03-engine-guide-fa.md"

@Composable
internal fun SetupLink(label: Int, target: String, enabled: Boolean) {
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
    ProviderProtocol.OPENROUTER_FREE -> R.string.engine_add_free
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
            Button(onClick = onConfirm, enabled = enabled && !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (busy) R.string.saving else button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        },
    )
}
