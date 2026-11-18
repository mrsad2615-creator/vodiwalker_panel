package ai.dana.ui

import ai.dana.R
import ai.dana.domain.LocalAction
import ai.dana.domain.LocalCommands
import ai.dana.domain.LocalText
import ai.dana.domain.NotesRepository
import ai.dana.voice.VoiceButton
import ai.dana.voice.VoiceStatus
import ai.dana.voice.rememberDanaVoice
import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.annotation.MainThread
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class PhoneState(val draft: String = "", val status: DeviceOutcome? = null, val busy: Boolean = false)

@HiltViewModel
internal class PhoneViewModel @Inject constructor(private val notes: NotesRepository) : ViewModel() {
    private val mutable = MutableStateFlow(PhoneState())
    val state = mutable.asStateFlow()

    @MainThread fun draft(value: String) {
        if (!mutable.value.busy && value.length <= LocalText.NOTE_LIMIT) mutable.update { it.copy(draft = value, status = null) }
    }

    @MainThread fun submit(execute: (LocalAction) -> DeviceOutcome) {
        if (mutable.value.busy) return
        val action = LocalCommands.parse(mutable.value.draft)
        if (action == null || action is LocalAction.WebSearch || action is LocalAction.Share) {
            result(DeviceOutcome(R.string.workspace_unrecognized, ok = false), false)
        } else run(action, execute, clear = true)
    }

    @MainThread fun run(action: LocalAction, execute: (LocalAction) -> DeviceOutcome, clear: Boolean = false) {
        if (mutable.value.busy) return
        if (action is LocalAction.Note) {
            mutable.update { it.copy(busy = true) }
            viewModelScope.launch {
                try {
                    notes.save(null, action.text)
                    result(DeviceOutcome(R.string.chat_note_saved), clear)
                } catch (canceled: CancellationException) { throw canceled }
                catch (error: Exception) { result(DeviceOutcome(DanaErrorHandler.messageFor(error), ok = false), false) }
                finally { mutable.update { it.copy(busy = false) } }
            }
        } else result(runCatching { execute(action) }.getOrElse { DeviceOutcome(R.string.chat_action_unavailable, ok = false) }, clear)
    }

    private fun result(outcome: DeviceOutcome, clear: Boolean) {
        mutable.update { it.copy(status = outcome, draft = if (outcome.ok && clear) "" else it.draft) }
    }
}

@Composable
internal fun PhoneScreen(model: PhoneViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val voice = rememberDanaVoice()
    val run = { action: LocalAction -> DeviceActions.execute(context, action) }
    var access by remember { mutableStateOf(false) }
    val flash = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionIntro(R.string.workspace_phone_title, R.string.workspace_phone_intro)
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { model.run(LocalAction.Battery, run) }, enabled = !state.busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.phone_battery))
                }
                OutlinedButton(onClick = { model.run(LocalAction.MediaVolume, run) }, enabled = !state.busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.phone_media_volume))
                }
            }
            state.status?.let { outcome ->
                if (!outcome.ok) ReadError(outcome.message) else Text(
                    outcome.percent?.let { stringResource(outcome.message, faDigits(it)) } ?: stringResource(outcome.message),
                    color = Mint, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            OutlinedTextField(state.draft, model::draft, Modifier.fillMaxWidth(), enabled = !state.busy, maxLines = 3,
                label = { Text(stringResource(R.string.workspace_phone_prompt)) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Button(onClick = { focus.clearFocus(); model.submit(run) }, enabled = !state.busy && !voice.state.listening && state.draft.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.workspace_run)) }
                VoiceButton(voice, !state.busy, useSystemSpeech = false, turnMode = false, compact = true, onText = model::draft)
            }
            VoiceStatus(voice.state)
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.workspace_examples), style = MaterialTheme.typography.labelLarge, color = Titanium)
                listOf(R.string.cmd_timer, R.string.cmd_open, R.string.cmd_event_timed).forEach { label ->
                    val example = stringResource(label)
                    FilterChip(selected = false, onClick = { model.draft(example) }, enabled = !state.busy, label = { Text(example) })
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.workspace_controls), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { model.run(LocalAction.VolumeStep.LOWER, run) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.workspace_quieter))
                }
                OutlinedButton(onClick = { model.run(LocalAction.VolumeStep.RAISE, run) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.workspace_louder))
                }
            }
            if (flash) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { model.run(LocalAction.Torch(true), run) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.phone_torch_on))
                }
                OutlinedButton(onClick = { model.run(LocalAction.Torch(false), run) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.phone_torch_off))
                }
            } else Text(stringResource(R.string.workspace_no_flash), style = MaterialTheme.typography.bodySmall, color = Titanium)
            TextButton(onClick = { model.run(LocalAction.Internet, run) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.phone_internet))
            }
        }
        TextButton(onClick = { access = !access }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.workspace_access)) }
        if (access) PhoneAccess()
    }
}

@Composable
private fun PhoneAccess() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun micAllowed() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    var mic by remember(context) { mutableStateOf(micAllowed()) }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(context, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) mic = micAllowed() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(if (mic) R.string.workspace_mic_on else R.string.workspace_mic_off), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.workspace_access_help), style = MaterialTheme.typography.bodySmall, color = Titanium)
        OutlinedButton(onClick = {
            failed = !openAppPermissions(context)
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.workspace_android_permissions)) }
        if (failed) ReadError(R.string.chat_action_unavailable)
    }
    AssistantSetup(enabled = true)
}

internal fun openAppPermissions(context: Context): Boolean = runCatching {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
}.isSuccess
