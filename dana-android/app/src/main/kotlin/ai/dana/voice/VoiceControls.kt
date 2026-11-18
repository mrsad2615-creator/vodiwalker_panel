package ai.dana.voice

import ai.dana.R
import ai.dana.ui.Amber
import ai.dana.ui.Ice
import ai.dana.ui.ReadError
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
internal fun VoiceButton(voice: DanaVoice, enabled: Boolean, onText: (String) -> Unit) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onText)
    var consent by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.listen(latest) else voice.permissionDenied()
    }
    val state = voice.state
    TextButton(enabled = enabled, onClick = {
        when {
            state.listening -> voice.finishListening()
            state.speaking || state.initializing -> voice.stopPlayback()
            else -> consent = true
        }
    }) {
        Text(stringResource(when {
            state.listening -> R.string.voice_finish
            state.speaking || state.initializing -> R.string.voice_stop_reading
            else -> R.string.voice_record
        }))
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text(stringResource(R.string.voice_consent_title)) },
        text = { Text(stringResource(R.string.voice_consent_body)) },
        dismissButton = { TextButton({ consent = false }) { Text(stringResource(R.string.cancel)) } },
        confirmButton = {
            TextButton({
                consent = false
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voice.listen(latest)
                else permission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Text(stringResource(R.string.voice_record)) }
        })
}

@Composable
internal fun VoiceStatus(state: VoiceState) {
    if (state.listening || state.speaking || state.initializing || state.status != null) Column(Modifier.fillMaxWidth()) {
        when {
            state.listening -> {
                Text(state.partial.ifBlank { stringResource(R.string.voice_listening) }, color = Ice, style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { state.strength }, modifier = Modifier.fillMaxWidth())
            }
            state.speaking -> Text(stringResource(R.string.voice_playing), color = Ice, style = MaterialTheme.typography.bodySmall)
            state.initializing -> Text(stringResource(R.string.voice_initializing), color = Amber, style = MaterialTheme.typography.bodySmall)
        }
        state.status?.let { ReadError(it) }
    }
}
