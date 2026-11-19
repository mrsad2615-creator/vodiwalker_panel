package ai.dana.voice

import ai.dana.R
import ai.dana.ui.Amber
import ai.dana.ui.Ice
import ai.dana.ui.ReadError
import ai.dana.ui.Mark
import ai.dana.ui.StudioMark
import ai.dana.ui.Titanium
import ai.dana.ui.openAppPermissions
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun rememberDanaVoice(): DanaVoice {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val voice = remember(context) { DanaVoice(context) }
    DisposableEffect(lifecycle, voice) {
        lifecycle.addObserver(voice)
        onDispose { lifecycle.removeObserver(voice); voice.close() }
    }
    return voice
}

@Composable
internal fun VoiceButton(voice: DanaVoice, enabled: Boolean, useSystemSpeech: Boolean, turnMode: Boolean, compact: Boolean = false, onText: (String) -> Unit) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onText)
    val canStart by rememberUpdatedState(enabled)
    val local by rememberUpdatedState(!useSystemSpeech)
    var permissionLocal by remember { mutableStateOf(true) }
    var consent by remember { mutableStateOf(false) }
    val listen: () -> Unit = { if (canStart) voice.listen(local) { latest(it) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && permissionLocal == local) listen()
        else if (!granted) voice.permissionDenied()
    }
    val start = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
        else {
            permissionLocal = local
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val state = voice.state
    val press = {
        when {
            state.listening -> voice.finishListening()
            state.speaking || state.initializing -> voice.stopPlayback()
            !voice.canListen(local) -> voice.unavailable(local)
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> start()
            else -> consent = true
        }
    }
    val label = stringResource(when {
        state.listening -> R.string.voice_finish
        state.speaking || state.initializing -> R.string.voice_stop_reading
        useSystemSpeech -> R.string.voice_record_system
        turnMode -> R.string.voice_record_turn
        else -> R.string.voice_record
    })
    if (compact) IconButton(onClick = press, enabled = enabled, modifier = Modifier.size(48.dp)) {
        StudioMark(if (state.listening || state.speaking || state.initializing) Mark.STOP else Mark.MIC, Titanium, label)
    } else if (turnMode) Button(onClick = press, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
    else TextButton(onClick = press, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text(stringResource(R.string.voice_consent_title)) },
        text = { Text(stringResource(if (local) R.string.voice_consent_body else R.string.voice_system_notice)) },
        dismissButton = { TextButton({ consent = false }) { Text(stringResource(R.string.cancel)) } },
        confirmButton = {
            TextButton({
                consent = false
                start()
            }) { Text(stringResource(R.string.voice_record)) }
        })
}

@Composable
internal fun VoiceStatus(state: VoiceState) {
    if (state.listening || state.speaking || state.initializing || state.status != null) Column(Modifier.fillMaxWidth()) {
        when {
            state.listening -> {
                Text(state.partial.ifBlank { stringResource(if (state.systemSpeech) R.string.voice_listening_system else R.string.voice_listening) },
                    color = Ice, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                LinearProgressIndicator(progress = { state.strength }, modifier = Modifier.fillMaxWidth())
            }
            state.speaking -> Text(stringResource(R.string.voice_playing), color = Ice, style = MaterialTheme.typography.bodySmall)
            state.initializing -> Text(stringResource(R.string.voice_initializing), color = Amber, style = MaterialTheme.typography.bodySmall)
        }
        state.status?.let { ReadError(it) }
        if (state.status == R.string.voice_denied) {
            val context = LocalContext.current
            var settingsFailed by remember { mutableStateOf(false) }
            TextButton(onClick = { settingsFailed = !openAppPermissions(context) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.workspace_android_permissions))
            }
            if (settingsFailed) ReadError(R.string.chat_action_unavailable)
        }
    }
}

@Composable
internal fun VoiceSetup(voice: DanaVoice, offline: Boolean, systemSpeech: Boolean, onSystemSpeech: (Boolean) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val state = voice.state
    val sample = stringResource(R.string.voice_sample)
    var openFailed by remember { mutableStateOf(false) }
    val openSettings: (String) -> Unit = { action ->
        openFailed = runCatching { context.startActivity(Intent(action)) }.isFailure
    }
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.voice_setup)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(stringResource(R.string.voice_setup_help))
            TextButton(voice::checkPersian, enabled = !state.checking && !state.speaking) { Text(stringResource(R.string.voice_check)) }
            if (state.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.support?.let { Text(stringResource(it), color = Ice) }
            if (state.canDownload && !offline) TextButton(voice::requestPersianDownload) { Text(stringResource(R.string.voice_download)) }
            TextButton(onClick = { openSettings(Settings.ACTION_VOICE_INPUT_SETTINGS) }) { Text(stringResource(R.string.voice_android_settings)) }
            TextButton(onClick = { voice.read(sample) }, enabled = !state.speaking && !state.initializing) { Text(stringResource(R.string.voice_test_tts)) }
            TextButton(onClick = { openSettings(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA) }) { Text(stringResource(R.string.voice_install_tts)) }
            VoiceStatus(state)
            Text(stringResource(R.string.voice_system_notice), style = MaterialTheme.typography.bodySmall, color = Amber)
            TextButton(onClick = { onSystemSpeech(!systemSpeech); onClose() }, enabled = !offline) {
                Text(stringResource(if (systemSpeech) R.string.voice_back_local else R.string.voice_use_system))
            }
            if (offline) Text(stringResource(R.string.voice_system_blocked), style = MaterialTheme.typography.bodySmall)
            if (openFailed) ReadError(R.string.voice_settings_unavailable)
        }
    }, confirmButton = { TextButton(onClose) { Text(stringResource(R.string.chat_done)) } })
}
