package ai.dana.ui

import ai.dana.R
import ai.dana.domain.DurationTarget
import ai.dana.domain.LocalAction
import ai.dana.domain.LocalCommands
import ai.dana.domain.LocalText
import ai.dana.domain.ManagedCloud
import ai.dana.domain.ManagedFailure
import ai.dana.domain.ManagedModel
import ai.dana.domain.ReviewMessage
import ai.dana.domain.NotesRepository
import ai.dana.domain.PhoneIntent
import ai.dana.domain.PhoneUtterances
import ai.dana.domain.PrivateText
import ai.dana.voice.VoiceButton
import ai.dana.voice.VoiceStatus
import ai.dana.voice.VoiceSetup
import ai.dana.voice.rememberDanaVoice
import android.content.Context
import android.Manifest
import android.app.TimePickerDialog
import android.os.SystemClock
import java.time.LocalTime
import java.util.UUID
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class PhoneApproval(val action: LocalAction, val expiresAtMs: Long)
internal class CloudPhoneReview(val model: ManagedModel, val prompt: String, val expiresAtMs: Long, val id: String = UUID.randomUUID().toString())

internal data class PhoneState(
    val draft: String = "",
    val status: DeviceOutcome? = null,
    val pending: PhoneApproval? = null,
    val durationTarget: DurationTarget? = null,
    val busy: Boolean = false,
    val offerCloud: Boolean = false,
    val cloudReview: CloudPhoneReview? = null,
    val planning: CloudPhoneReview? = null,
) {
    val working get() = busy || planning != null
    override fun toString() = "PhoneState(pending=${pending != null}, busy=$busy)"
}

@HiltViewModel
internal class PhoneViewModel @Inject constructor(private val notes: NotesRepository, private val cloud: ManagedCloud) : ViewModel() {
    private val mutable = MutableStateFlow(PhoneState())
    val state = mutable.asStateFlow()
    private var planningJob: Job? = null

    @MainThread fun draft(value: String) {
        if (!mutable.value.working && value.length <= LocalText.NOTE_LIMIT) {
            mutable.update { it.copy(draft = value, status = null, pending = null, offerCloud = false, cloudReview = null) }
        }
    }

    @MainThread fun speech(value: String, execute: (LocalAction) -> DeviceOutcome) {
        if (value.isBlank() || value.length > LocalText.NOTE_LIMIT || mutable.value.working || mutable.value.pending != null || mutable.value.cloudReview != null) return
        draft(value)
        submit(execute)
    }

    @MainThread fun submit(execute: (LocalAction) -> DeviceOutcome) {
        val current = mutable.value
        if (current.working || current.pending != null || current.cloudReview != null || current.draft.isBlank()) return
        val withDuration = current.durationTarget?.let { target -> PhoneUtterances.duration(current.draft)?.let(target::action) }
        val intent = withDuration?.let(PhoneIntent::Propose) ?: PhoneUtterances.interpret(current.draft)
        when (intent) {
            is PhoneIntent.Propose -> {
                mutable.update { it.copy(durationTarget = null, offerCloud = false) }
                val action = intent.action
                if (action == LocalAction.Battery || action == LocalAction.MediaVolume) run(action, execute, clear = true)
                else propose(action)
            }
            is PhoneIntent.NeedDuration -> askDuration(intent.target)
            PhoneIntent.Unknown -> mutable.update { it.copy(status = DeviceOutcome(if (current.durationTarget == null) R.string.workspace_unrecognized
                else R.string.phone_duration_clarify, ok = false), offerCloud = cloud.configured && current.durationTarget == null &&
                ReviewMessage.acceptsDraft(current.draft) && current.draft.length <= ManagedCloud.COMMAND_LIMIT) }
        }
    }

    private fun propose(action: LocalAction) {
        mutable.update { it.copy(pending = PhoneApproval(action, SystemClock.elapsedRealtime() + 45_000L), status = null) }
    }

    private fun askDuration(target: DurationTarget) {
        mutable.update { it.copy(durationTarget = target, draft = "", status = null, offerCloud = false) }
    }

    @MainThread fun reviewOnline(model: ManagedModel) {
        val state = mutable.value
        if (!cloud.configured || !state.offerCloud || state.working || state.pending != null || state.cloudReview != null) return
        val prompt = PrivateText.mask(state.draft.trim()).text.takeIf {
            it.isNotBlank() && it.length <= ManagedCloud.COMMAND_LIMIT && ReviewMessage.acceptsDraft(it)
        } ?: run { result(DeviceOutcome(R.string.managed_invalid_text, ok = false), false); return }
        mutable.update { it.copy(cloudReview = CloudPhoneReview(model, prompt, SystemClock.elapsedRealtime() + 45_000L)) }
    }

    @MainThread fun dismissOnline() { mutable.update { it.copy(cloudReview = null) } }

    @MainThread fun interpretOnline(review: CloudPhoneReview, currentModel: ManagedModel?, refresh: () -> Unit) {
        val state = mutable.value
        if (state.cloudReview !== review || state.working || review.model != currentModel) return
        dismissOnline()
        if (SystemClock.elapsedRealtime() >= review.expiresAtMs) {
            result(DeviceOutcome(R.string.phone_approval_expired, ok = false), false)
            return
        }
        mutable.update { it.copy(planning = review, status = null, offerCloud = false) }
        planningJob = viewModelScope.launch {
            try {
                val answer = cloud.command(review.model, review.prompt, review.id)
                if (mutable.value.planning !== review) return@launch
                when (answer) {
                    is PhoneIntent.Propose -> propose(answer.action) // Even an online read is presented for confirmation.
                    is PhoneIntent.NeedDuration -> askDuration(answer.target)
                    PhoneIntent.Unknown -> result(DeviceOutcome(R.string.workspace_unrecognized, ok = false), false)
                }
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (error: Exception) {
                if (mutable.value.planning === review) result(DeviceOutcome(
                    (error as? ManagedFailure)?.problem?.let(::managedError) ?: R.string.managed_network_error, ok = false), false)
            } finally {
                if (mutable.value.planning === review) mutable.update { it.copy(planning = null) }
                refresh() // A canceled upstream attempt can still count toward the daily limit.
            }
        }
    }

    @MainThread fun cancel() {
        // Retain only the draft and its (unapproved) online offer, including the return from email sign-in.
        mutable.update { it.copy(pending = null, durationTarget = null, planning = null, cloudReview = null) }
        planningJob?.cancel()
        planningJob = null
    }

    @MainThread fun confirm(approval: PhoneApproval, execute: (LocalAction) -> DeviceOutcome) {
        if (mutable.value.pending !== approval) return
        cancel() // Consume before the effect. A stale/repeated callback cannot run anything.
        if (SystemClock.elapsedRealtime() >= approval.expiresAtMs) {
            result(DeviceOutcome(R.string.phone_approval_expired, ok = false), false)
        } else run(approval.action, execute, clear = true)
    }

    @MainThread fun run(action: LocalAction, execute: (LocalAction) -> DeviceOutcome, clear: Boolean = false) {
        if (mutable.value.working) return
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
internal fun PhoneScreen(
    model: PhoneViewModel, offline: Boolean, planner: ManagedModel?, remaining: Int, login: (() -> Unit)?,
    enableNetwork: (() -> Unit)?, refresh: () -> Unit,
) {
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
    var voiceSetup by remember { mutableStateOf(false) }
    var systemSpeech by remember { mutableStateOf(false) }
    LaunchedEffect(offline) {
        if (offline && systemSpeech) { systemSpeech = false; voice.close() }
    }
    if (voiceSetup) VoiceSetup(voice, offline, systemSpeech && !offline,
        onSystemSpeech = { voice.close(); systemSpeech = it }, onClose = { voiceSetup = false })
    val flash = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) }

    if (timer) TimerForm(dismiss = { timer = false }, submit = { timer = false; run(it) })

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionIntro(R.string.workspace_phone_title, R.string.workspace_phone_intro)
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.phone_quick_title), style = MaterialTheme.typography.titleMedium)
            PhoneRow(R.string.phone_battery, R.string.phone_media_volume, !state.working,
                { run(LocalAction.Battery) }, { run(LocalAction.MediaVolume) })
            state.status?.takeUnless { !it.ok && state.draft.isNotBlank() }?.let { outcome ->
                if (!outcome.ok) ReadError(outcome.message) else Text(
                    outcome.percent?.let { stringResource(outcome.message, faDigits(it)) } ?: stringResource(outcome.message),
                    color = Mint, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            PhoneRow(R.string.phone_timer_button, R.string.phone_alarm_button, !state.working,
                { focus.clearFocus(); voice.close(); timer = true },
                {
                    focus.clearFocus(); voice.close()
                    val now = LocalTime.now().plusMinutes(5)
                    TimePickerDialog(context, { _, hour, minute -> run(LocalAction.Alarm(hour, minute)) },
                        now.hour, now.minute, android.text.format.DateFormat.is24HourFormat(context)).show()
                })
            val newEvent = stringResource(R.string.phone_new_event)
            PhoneRow(R.string.phone_new_event, R.string.phone_open_clock, !state.working,
                { run(LocalAction.CalendarDraft(newEvent)) }, { run(LocalAction.Clock) })
        }
        AssistantSetup(enabled = !state.working && state.pending == null, beforeLaunch = { focus.clearFocus(); voice.close(); model.cancel() })
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.phone_command_title), style = MaterialTheme.typography.titleMedium)
            state.durationTarget?.let { target ->
                Text(stringResource(when (target) {
                    DurationTarget.TIMER -> R.string.phone_ask_timer_duration
                    DurationTarget.WIFI_OFF -> R.string.phone_ask_wifi_off_duration
                    DurationTarget.WIFI_ON -> R.string.phone_ask_wifi_on_duration
                }), color = Ice, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                TextButton(onClick = model::cancel) { Text(stringResource(R.string.cancel)) }
            }
            OutlinedTextField(state.draft, model::draft, Modifier.fillMaxWidth(), enabled = !state.working, maxLines = 3,
                label = { Text(stringResource(R.string.workspace_phone_prompt)) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { focus.clearFocus(); voice.close(); model.submit(execute) },
                    enabled = !state.working && !voice.state.listening && state.draft.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.workspace_run)) }
                VoiceButton(voice, !state.working && state.pending == null && state.cloudReview == null,
                    useSystemSpeech = systemSpeech && !offline, turnMode = false, compact = true, onText = { finalText ->
                    model.speech(finalText, execute)
                })
            }
            VoiceStatus(voice.state)
            TextButton(onClick = { focus.clearFocus(); voice.close(); voiceSetup = true }, enabled = !state.working,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.voice_setup)) }
            // Keep typed results close to the field as well; the first status card may have scrolled away.
            state.status?.takeIf { state.draft.isNotBlank() && !it.ok }?.let { ReadError(it.message) }
            if (state.planning != null) {
                Text(stringResource(R.string.managed_command_running), color = Titanium)
                TextButton(model::cancel) { Text(stringResource(R.string.compare_stop)) }
            } else if (state.offerCloud) {
                when {
                    planner != null && planner.units <= remaining -> {
                        Text(stringResource(R.string.managed_command_help), color = Titanium)
                        OutlinedButton(onClick = { focus.clearFocus(); voice.close(); model.reviewOnline(planner) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.managed_command_button))
                        }
                    }
                    planner != null -> {
                        Text(stringResource(R.string.managed_quota), color = Amber)
                        TextButton(refresh) { Text(stringResource(R.string.managed_refresh_catalog)) }
                    }
                    login != null -> TextButton(onClick = login) { Text(stringResource(R.string.managed_command_login)) }
                    enableNetwork != null -> OfflinePanel(R.string.managed_offline, true, enableNetwork)
                }
            }
            TextButton(onClick = { examples = !examples }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.workspace_examples))
            }
            if (examples) listOf(R.string.phone_example_timer_natural, R.string.phone_example_wifi_natural, R.string.cmd_event_timed,
                R.string.phone_sms_example, R.string.phone_dial_example).forEach { label ->
                val example = stringResource(label)
                FilterChip(selected = false, onClick = { model.draft(example) }, enabled = !state.working,
                    modifier = Modifier.heightIn(min = 48.dp), label = { Text(example) })
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.workspace_controls), style = MaterialTheme.typography.titleMedium)
            PhoneRow(R.string.workspace_quieter, R.string.workspace_louder, !state.working,
                { run(LocalAction.VolumeStep.LOWER) }, { run(LocalAction.VolumeStep.RAISE) })
            if (flash) PhoneRow(R.string.phone_torch_on, R.string.phone_torch_off, !state.working,
                { run(LocalAction.Torch(true)) }, { run(LocalAction.Torch(false)) })
            else Text(stringResource(R.string.workspace_no_flash), style = MaterialTheme.typography.bodySmall, color = Titanium)
            PhoneRow(R.string.phone_internet, R.string.chat_action_bluetooth, !state.working,
                { run(LocalAction.Internet) }, { run(LocalAction.Bluetooth) })
            PhoneRow(R.string.chat_action_sound, R.string.phone_settings, !state.working,
                { run(LocalAction.Sound) }, { run(LocalAction.Panel.SETTINGS) })
        }
        TextButton(onClick = { access = !access }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.workspace_access))
        }
        if (access) PhoneAccess()
    }
}

/** Stable dialog host, outside the scrolling content that can be recycled during IME transitions. */
@Composable
internal fun PhoneActionReview(model: PhoneViewModel, planner: ManagedModel?, refresh: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    state.pending?.let { approval ->
        val action = approval.action
        AlertDialog(onDismissRequest = model::cancel, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.chat_confirm_action)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(actionText(action))
                Text(stringResource(when (action) {
                    is LocalAction.Timer, is LocalAction.Alarm -> R.string.phone_clock_review
                    is LocalAction.WifiChange -> R.string.phone_wifi_manual_review
                    is LocalAction.WifiReminder -> R.string.phone_wifi_reminder_review
                    is LocalAction.Dial -> R.string.phone_dial_review
                    is LocalAction.SmsDraft -> R.string.phone_sms_review
                    is LocalAction.CalendarDraft -> R.string.phone_calendar_review
                    is LocalAction.Note -> R.string.chat_note_private
                    else -> R.string.phone_command_review
                }), color = Titanium)
            }
        }, dismissButton = { TextButton(model::cancel) { Text(stringResource(R.string.cancel)) } },
            confirmButton = { TextButton(onClick = { model.confirm(approval) { DeviceActions.execute(context, it) } }) {
                Text(stringResource(when (action) {
                    is LocalAction.WifiChange -> R.string.chat_action_wifi
                    is LocalAction.WifiReminder, is LocalAction.Timer, is LocalAction.Alarm -> R.string.phone_submit_clock
                    else -> R.string.chat_execute
                }))
            } })
    }
    state.cloudReview?.let { review ->
        AlertDialog(onDismissRequest = model::dismissOnline, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.managed_command_button)) }, text = {
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${review.model.provider} · ${review.model.title}", style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.managed_preview_help, faDigits(review.model.units)), color = Titanium)
                    Text(stringResource(R.string.managed_command_review), color = Titanium)
                    Text(review.prompt, Modifier.testTag("managed-command-outbound"))
                }
            }, dismissButton = { TextButton(model::dismissOnline) { Text(stringResource(R.string.cancel)) } },
            confirmButton = { TextButton(enabled = review.model == planner, onClick = { model.interpretOnline(review, planner, refresh) }) {
                Text(stringResource(R.string.message_send_once))
            } })
    }
    DisposableEffect(model) { onDispose { model.cancel() } }
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
}

@Composable
internal fun durationText(seconds: Int): String {
    val units = listOf(3_600 to R.string.phone_duration_hours_unit, 60 to R.string.phone_duration_minutes_unit, 1 to R.string.phone_duration_seconds_unit)
    var rest = seconds
    val parts = mutableListOf<String>()
    for ((factor, label) in units) {
        val amount = rest / factor
        rest %= factor
        if (amount > 0) parts += stringResource(label, faDigits(amount))
    }
    return parts.joinToString(" و ")
}

internal fun openAppPermissions(context: Context): Boolean = runCatching {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
}.isSuccess
