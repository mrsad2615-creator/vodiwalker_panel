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
import android.app.TimePickerDialog
import java.time.LocalTime
import androidx.annotation.StringRes
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
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

internal data class PhoneState(
    val draft: String = "",
    val status: DeviceOutcome? = null,
    val pending: LocalAction? = null,
    val busy: Boolean = false,
)

@HiltViewModel
internal class PhoneViewModel @Inject constructor(private val notes: NotesRepository) : ViewModel() {
    private val mutable = MutableStateFlow(PhoneState())
    val state = mutable.asStateFlow()

    @MainThread fun draft(value: String) {
        if (!mutable.value.busy && value.length <= LocalText.NOTE_LIMIT) mutable.update { it.copy(draft = value, status = null) }
    }

    @MainThread fun submit(execute: (LocalAction) -> DeviceOutcome) {
        if (mutable.value.busy || mutable.value.pending != null) return
        val action = LocalCommands.parse(mutable.value.draft)
        if (action == null || action is LocalAction.WebSearch || action is LocalAction.Share) {
            result(DeviceOutcome(R.string.workspace_unrecognized, ok = false), false)
        } else if (action == LocalAction.Battery || action == LocalAction.MediaVolume) run(action, execute, clear = true)
        else mutable.update { it.copy(pending = action, status = null) }
    }

    @MainThread fun cancel() { mutable.update { it.copy(pending = null) } }

    @MainThread fun confirm(execute: (LocalAction) -> DeviceOutcome) {
        val action = mutable.value.pending ?: return
        cancel()
        run(action, execute, clear = true)
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
    val execute = { action: LocalAction -> DeviceActions.execute(context, action) }
    val run: (LocalAction) -> Unit = { action ->
        focus.clearFocus()
        voice.close()
        model.run(action, execute)
    }
    var access by remember { mutableStateOf(false) }
    var timer by remember { mutableStateOf(false) }
    var examples by remember { mutableStateOf(false) }
    val flash = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) }

    state.pending?.let { action ->
        AlertDialog(onDismissRequest = model::cancel, title = { Text(stringResource(R.string.chat_confirm_action)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(actionText(action))
                Text(stringResource(when (action) {
                    is LocalAction.Timer, is LocalAction.Alarm -> R.string.phone_clock_review
                    is LocalAction.Dial -> R.string.phone_dial_review
                    is LocalAction.SmsDraft -> R.string.phone_sms_review
                    is LocalAction.CalendarDraft -> R.string.phone_calendar_review
                    is LocalAction.Note -> R.string.chat_note_private
                    else -> R.string.phone_command_review
                }), color = Titanium)
            }
        }, dismissButton = { TextButton(model::cancel) { Text(stringResource(R.string.cancel)) } },
            confirmButton = { TextButton(onClick = { model.confirm(execute) }) { Text(stringResource(R.string.chat_execute)) } })
    }
    DisposableEffect(model) { onDispose { model.cancel() } }
    if (timer) TimerForm(dismiss = { timer = false }, submit = { timer = false; run(it) })

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionIntro(R.string.workspace_phone_title, R.string.workspace_phone_intro)
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.phone_quick_title), style = MaterialTheme.typography.titleMedium)
            PhoneRow(R.string.phone_battery, R.string.phone_media_volume, !state.busy,
                { run(LocalAction.Battery) }, { run(LocalAction.MediaVolume) })
            state.status?.takeUnless { !it.ok && state.draft.isNotBlank() }?.let { outcome ->
                if (!outcome.ok) ReadError(outcome.message) else Text(
                    outcome.percent?.let { stringResource(outcome.message, faDigits(it)) } ?: stringResource(outcome.message),
                    color = Mint, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            PhoneRow(R.string.phone_timer_button, R.string.phone_alarm_button, !state.busy,
                { focus.clearFocus(); voice.close(); timer = true },
                {
                    focus.clearFocus(); voice.close()
                    val now = LocalTime.now().plusMinutes(5)
                    TimePickerDialog(context, { _, hour, minute -> run(LocalAction.Alarm(hour, minute)) },
                        now.hour, now.minute, android.text.format.DateFormat.is24HourFormat(context)).show()
                })
            val newEvent = stringResource(R.string.phone_new_event)
            PhoneRow(R.string.phone_new_event, R.string.phone_open_clock, !state.busy,
                { run(LocalAction.CalendarDraft(newEvent)) }, { run(LocalAction.Clock) })
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.phone_command_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(state.draft, model::draft, Modifier.fillMaxWidth(), enabled = !state.busy, maxLines = 3,
                label = { Text(stringResource(R.string.workspace_phone_prompt)) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { focus.clearFocus(); voice.close(); model.submit(execute) },
                    enabled = !state.busy && !voice.state.listening && state.draft.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.workspace_run)) }
                VoiceButton(voice, !state.busy, useSystemSpeech = false, turnMode = false, compact = true, onText = model::draft)
            }
            VoiceStatus(voice.state)
            // Keep typed results close to the field as well; the first status card may have scrolled away.
            state.status?.takeIf { state.draft.isNotBlank() && !it.ok }?.let { ReadError(it.message) }
            TextButton(onClick = { examples = !examples }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.workspace_examples))
            }
            if (examples) listOf(R.string.cmd_timer, R.string.workspace_settings_example, R.string.cmd_event_timed,
                R.string.phone_sms_example, R.string.phone_dial_example).forEach { label ->
                val example = stringResource(label)
                FilterChip(selected = false, onClick = { model.draft(example) }, enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 48.dp), label = { Text(example) })
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.workspace_controls), style = MaterialTheme.typography.titleMedium)
            PhoneRow(R.string.workspace_quieter, R.string.workspace_louder, !state.busy,
                { run(LocalAction.VolumeStep.LOWER) }, { run(LocalAction.VolumeStep.RAISE) })
            if (flash) PhoneRow(R.string.phone_torch_on, R.string.phone_torch_off, !state.busy,
                { run(LocalAction.Torch(true)) }, { run(LocalAction.Torch(false)) })
            else Text(stringResource(R.string.workspace_no_flash), style = MaterialTheme.typography.bodySmall, color = Titanium)
            PhoneRow(R.string.phone_internet, R.string.chat_action_bluetooth, !state.busy,
                { run(LocalAction.Internet) }, { run(LocalAction.Bluetooth) })
            PhoneRow(R.string.chat_action_sound, R.string.phone_settings, !state.busy,
                { run(LocalAction.Sound) }, { run(LocalAction.Panel.SETTINGS) })
        }
        TextButton(onClick = { access = !access }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.workspace_access))
        }
        if (access) PhoneAccess()
    }
}

@Composable
private fun PhoneRow(@StringRes first: Int, @StringRes second: Int, enabled: Boolean, left: () -> Unit, right: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = left, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(first)) }
        OutlinedButton(onClick = right, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(second)) }
    }
}

@Composable
private fun TimerForm(dismiss: () -> Unit, submit: (LocalAction.Timer) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(true) }
    val command = LocalCommands.parse("تایمر $amount ${if (minutes) "دقیقه" else "ثانیه"}") as? LocalAction.Timer
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.phone_timer_button)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.phone_clock_review))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(minutes, onClick = { minutes = true }, label = { Text(stringResource(R.string.phone_minutes)) },
                    modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(!minutes, onClick = { minutes = false }, label = { Text(stringResource(R.string.phone_seconds)) },
                    modifier = Modifier.heightIn(min = 48.dp))
            }
            OutlinedTextField(amount, { if (it.length <= 5 && it.all { c -> c in '0'..'9' || c in '۰'..'۹' || c in '٠'..'٩' }) amount = it },
                Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.phone_duration)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = amount.isNotEmpty() && command == null,
                supportingText = { Text(stringResource(R.string.phone_timer_limit)) })
        }
    }, dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { TextButton(enabled = command != null, onClick = { command?.let(submit) }) { Text(stringResource(R.string.phone_submit_clock)) } })
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
