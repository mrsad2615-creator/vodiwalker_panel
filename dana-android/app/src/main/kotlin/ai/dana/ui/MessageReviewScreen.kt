package ai.dana.ui

import ai.dana.R
import ai.dana.domain.MessageReviewPlan
import ai.dana.domain.ProviderProfile
import ai.dana.domain.ReviewMessage
import ai.dana.localai.DeviceModels
import ai.dana.voice.VoiceStatus
import ai.dana.voice.rememberDanaVoice
import android.content.ClipboardManager
import android.content.ClipDescription
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun MessageReviewScreen(model: MessageReviewViewModel, onClose: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val profiles by model.profiles.collectAsStateWithLifecycle()
    val offline by model.offline.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val list = rememberLazyListState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val hideKeyboard = { focus.clearFocus(); keyboard?.hide() }
    val playingAllowed = state.draft.isNotBlank() && state.pending == null && state.active == null
    var choosingEngine by remember(state.revision, offline, profiles) { mutableStateOf(false) }
    val voice = key(state.revision) { rememberDanaVoice() }
    LaunchedEffect(state.revision) { list.requestScrollToItem(0) }
    val showAnalysis = state.active != null || state.signals != null || state.result != null || state.notice != null || state.analysisProblem != null
    LaunchedEffect(showAnalysis, state.result) {
        if (showAnalysis) list.requestScrollToItem(3)
    }
    Scaffold(containerColor = Obsidian, contentWindowInsets = WindowInsets.safeDrawing, topBar = {
        Surface(color = Obsidian) {
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 14.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { StudioMark(Mark.BACK, Ice, stringResource(R.string.studio_back)) }
                Text(stringResource(R.string.message_title), style = MaterialTheme.typography.titleLarge)
            }
        }
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally) {
            LazyColumn(state = list, modifier = Modifier.widthIn(max = 660.dp).fillMaxSize().testTag("message-review-list"),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { SectionIntro(R.string.message_heading, R.string.message_intro) }
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        OutlinedTextField(state.draft, { text -> voice.stopPlayback(); model.edit(text) }, Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.message_source)) }, minLines = 3, maxLines = 6,
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                            enabled = state.active == null && state.pending == null,
                            supportingText = { Text(stringResource(R.string.message_length, faDigits(state.draft.length), faDigits(ReviewMessage.MAX_CHARS))) })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { voice.close(); model.paste(readClipboardText(context)) },
                                enabled = state.active == null && state.pending == null) { Text(stringResource(R.string.message_paste)) }
                            TextButton(onClick = { voice.close(); hideKeyboard(); model.clear() }, enabled = state.draft.isNotEmpty()) {
                                Text(stringResource(R.string.message_clear))
                            }
                        }
                        state.inputProblem?.let { ReadError(it) }
                    }
                }
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        if (voice.state.speaking || voice.state.initializing) {
                            OutlinedButton(onClick = voice::stopPlayback, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.voice_stop_reading))
                            }
                        } else OutlinedButton(onClick = {
                            hideKeyboard()
                            ReviewMessage.create(state.draft)?.let { voice.read(it.text) }
                        }, enabled = playingAllowed, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.message_read))
                        }
                        VoiceStatus(voice.state)
                        Button(onClick = { voice.close(); hideKeyboard(); model.local() }, enabled = playingAllowed,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(if (DeviceModels.BUNDLED) R.string.message_local else R.string.message_compat_scan))
                        }
                        TextButton(onClick = { voice.close(); hideKeyboard(); choosingEngine = true },
                            enabled = playingAllowed && !offline && profiles.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.message_online))
                        }
                        if (offline || profiles.isEmpty()) Text(stringResource(if (offline) R.string.message_offline else R.string.message_no_provider),
                            color = Titanium, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item(key = "analysis") {
                    if (showAnalysis) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            state.active?.let {
                                TechnicalText(it, maxLines = 2)
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text(stringResource(state.phase ?: R.string.message_analyzing), color = Ice)
                                TextButton(onClick = model::stop) { Text(stringResource(R.string.message_stop)) }
                            }
                            state.signals?.let { signals ->
                                Text(stringResource(R.string.message_signals), style = MaterialTheme.typography.titleMedium)
                                if (signals.clockTimes.isNotEmpty()) Text(stringResource(R.string.message_clock, signals.clockTimes.joinToString("، ")))
                                if (signals.hasWebAddress) Text(stringResource(R.string.message_has_link), color = Amber)
                                if (signals.mentionsCredential) Text(stringResource(R.string.message_code_cue), color = Amber)
                                if (signals.clockTimes.isEmpty() && !signals.hasWebAddress && !signals.mentionsCredential) {
                                    Text(stringResource(R.string.message_no_cues), color = Titanium)
                                }
                            }
                            state.notice?.let { Text(stringResource(it), color = Titanium) }
                            state.result?.let {
                                Text(stringResource(R.string.message_result), style = MaterialTheme.typography.titleMedium)
                                TechnicalText(it.engine, maxLines = 2)
                                Text(it.text, modifier = Modifier.testTag("message-review-answer").semantics { liveRegion = LiveRegionMode.Polite })
                            }
                            if (state.signals != null || state.result != null) {
                                Text(stringResource(R.string.message_result_limit), color = Titanium, style = MaterialTheme.typography.bodySmall)
                            }
                            state.analysisProblem?.let { ReadError(it) }
                        }
                    }
                }
            }
        }
    }
    val pending = state.pending
    if (pending != null) MessageSendReview(pending, !offline && pending.profile in profiles, model::dismiss) { model.approve(pending) }
    else if (choosingEngine && playingAllowed && !offline && profiles.isNotEmpty()) {
        MessageEnginePicker(profiles, dismiss = { choosingEngine = false }, select = { profile ->
            choosingEngine = false
            model.reviewOnline(profile)
        })
    }
}

@Composable
private fun MessageEnginePicker(profiles: List<ProviderProfile>, dismiss: () -> Unit, select: (ProviderProfile) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.message_online)) }, text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profiles.forEach { profile ->
                    OutlinedButton(onClick = { select(profile) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("${profile.config.name} · ${profile.config.model}")
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun MessageSendReview(plan: MessageReviewPlan, enabled: Boolean, dismiss: () -> Unit, approve: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.message_send_title)) }, text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(plan.profile.config.name, style = MaterialTheme.typography.titleMedium)
                TechnicalText(plan.profile.config.baseUrl, maxLines = Int.MAX_VALUE)
                TechnicalText(plan.profile.config.model, maxLines = Int.MAX_VALUE)
                Text(stringResource(R.string.message_send_help), color = Amber)
                Text(stringResource(R.string.message_masked, faDigits(plan.input.maskedValues)), color = Titanium)
                Text(stringResource(R.string.message_outbound), style = MaterialTheme.typography.labelLarge)
                Text(plan.input.prompt, Modifier.testTag("message-outbound"))
                Text(stringResource(R.string.message_instruction), style = MaterialTheme.typography.labelLarge)
                Text(ReviewMessage.INSTRUCTIONS)
            }
        }, confirmButton = { Button(onClick = approve, enabled = enabled) { Text(stringResource(R.string.message_send_once)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}

/** Called only on a foreground tap, and never coerces a file/intent/URI into text. */
private fun readClipboardText(context: Context): CharSequence? = runCatching {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
    if (clip.itemCount != 1 || !clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) return null
    clip.getItemAt(0).text
}.getOrNull()
