package ai.dana.ui

import ai.dana.R
import ai.dana.media.MediaReview
import ai.dana.media.MediaViewModel
import ai.dana.voice.VoiceButton
import ai.dana.voice.VoiceSetup
import ai.dana.voice.VoiceStatus
import ai.dana.voice.rememberDanaVoice
import ai.dana.domain.AssistantStyle
import ai.dana.domain.AssistantTask
import ai.dana.domain.ComparisonLimits
import ai.dana.domain.PhotoSupport
import ai.dana.domain.photoSupport
import ai.dana.domain.acceptsCredential
import ai.dana.domain.cloudEngine
import ai.dana.domain.LocalAction
import ai.dana.domain.LocalText
import ai.dana.domain.MessageRole
import ai.dana.domain.ProviderProfile
import ai.dana.domain.ProviderProtocol
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun ChatScreen(
    model: ChatViewModel,
    media: MediaViewModel,
    dashboard: DashboardState,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chat by model.state.collectAsStateWithLifecycle()
    val profiles = (dashboard.profiles as? Reading.Ready)?.value.orEmpty()
    val offline = (dashboard.offline as? Reading.Ready)?.value != false
    BackHandler(enabled = chat.page != ChatPage.CONVERSATION && chat.dialog == null && !chat.busy) {
        model.page(ChatPage.CONVERSATION)
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChatPage.entries.forEach { page ->
                FilterChip(
                    selected = page == chat.page,
                    enabled = !chat.busy,
                    onClick = { model.page(page) },
                    label = { Text(stringResource(when (page) {
                        ChatPage.CONVERSATION -> R.string.chat_room
                        ChatPage.HISTORY -> R.string.chat_history
                        ChatPage.MEMORY -> R.string.chat_memory
                        ChatPage.TOOLS -> R.string.chat_tools
                    })) },
                )
            }
        }
        if (chat.dialog == null) {
            chat.error?.let { ReadError(it) }
            chat.notice?.let { msg ->
                val text = chat.noticePercent?.let { stringResource(msg, faDigits(it)) } ?: stringResource(msg)
                Text(text, color = Mint, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        when (chat.page) {
            ChatPage.CONVERSATION -> Dialogue(chat, model, media::open, profiles, offline, onSettings)
            ChatPage.HISTORY -> Archive(chat, model)
            ChatPage.MEMORY -> MemoryVault(chat, model)
            ChatPage.TOOLS -> ToolPanel(chat, model, media::open)
        }
    }
    MediaReview(media, chat.busy, model::importText, model::photo)
    chat.dialog?.let { dialog -> key(dialog) { ChatOverlay(dialog, chat, model, offline, profiles) } }
}

@Composable
private fun Dialogue(
    chat: ChatState,
    model: ChatViewModel,
    onMedia: () -> Unit,
    profiles: List<ProviderProfile>,
    offline: Boolean,
    onSettings: () -> Unit,
) {
    val voice = rememberDanaVoice()
    val focus = LocalFocusManager.current
    var setupVoice by remember { mutableStateOf(false) }
    var systemSpeech by remember { mutableStateOf(false) }
    val useSystemSpeech = systemSpeech && !offline && chat.voiceOpen
    LaunchedEffect(offline, chat.voiceOpen) {
        if ((offline || !chat.voiceOpen) && systemSpeech) {
            systemSpeech = false
            voice.close()
        }
    }
    val notice = chat.notice?.takeIf { chat.speakNotice && chat.voiceOpen && chat.options.readReplies }
    val noticeText = notice?.let { res -> chat.noticePercent?.let { stringResource(res, faDigits(it)) } ?: stringResource(res) }
    val done = chat.turns.lastOrNull()?.takeIf { it.complete && it.readWhenDone && !chat.running }
    LaunchedEffect(done?.index) {
        if (done != null) {
            model.speechConsumed(done.index)
            voice.read(done.answer)
        }
    }
    LaunchedEffect(noticeText) {
        if (noticeText != null) {
            model.noticeSpoken()
            voice.read(noticeText)
        }
    }
    if (setupVoice) VoiceSetup(voice, offline, useSystemSpeech, onSystemSpeech = { systemSpeech = it }, onClose = { setupVoice = false })
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(chat.current?.title ?: stringResource(R.string.chat_heading), style = MaterialTheme.typography.titleLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(if (chat.options.autoHistory) R.string.chat_recording else R.string.chat_temporary),
                    style = MaterialTheme.typography.labelSmall, color = Titanium)
            }
            TextButton(onClick = model::newConversation, enabled = !chat.busy) { Text(stringResource(R.string.chat_new)) }
            TextButton(onClick = { model.dialog(ChatDialog.Options) }, enabled = !chat.busy) { Text(stringResource(R.string.chat_privacy)) }
        }
        val scroll = rememberLazyListState()
        val team = chat.team
        val itemCount = chat.turns.size + if (team == null) 0 else 2 + team.comparison.lanes.size
        LaunchedEffect(chat.turns.lastOrNull()?.index, team?.index, chat.voiceOpen) {
            if (team != null) scroll.scrollToItem(chat.turns.size + if (chat.voiceOpen) 1 else 0)
            else if (chat.voiceOpen && itemCount <= 1) scroll.scrollToItem(0)
            else if (itemCount > 0) scroll.scrollToItem(itemCount - 1 + if (chat.voiceOpen) 1 else 0)
        }
        LazyColumn(state = scroll, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)) {
            if (chat.voiceOpen) item(key = "voice") {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.voice_conversation), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Ice)
                        TextButton(onClick = { voice.close(); setupVoice = true }, enabled = !chat.busy) { Text(stringResource(R.string.voice_setup)) }
                    }
                    Text(stringResource(R.string.voice_turn_help), color = Titanium, style = MaterialTheme.typography.bodySmall)
                    ToggleLine(R.string.voice_read_replies, chat.options.readReplies, !chat.busy) { model.options(chat.options.copy(readReplies = it)) }
                }
            }
            if (chat.turns.isEmpty() && team == null && !chat.voiceOpen) {
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        StudioPill(stringResource(if (offline) R.string.studio_mode_offline else R.string.studio_mode_online),
                            color = if (offline) Titanium else Mint, dot = true)
                        Text(stringResource(if (offline) R.string.chat_offline_title else R.string.chat_start_title),
                            style = MaterialTheme.typography.titleLarge, color = Ice)
                        Text(stringResource(if (offline) R.string.chat_offline_help else if (profiles.isEmpty()) R.string.studio_chat_no_provider else R.string.chat_start_body),
                            style = MaterialTheme.typography.bodyMedium, color = Titanium)
                        if (offline || profiles.isEmpty()) TextButton(onClick = { focus.clearFocus(); onSettings() }) { Text(stringResource(R.string.chat_setup_engines)) }
                    }
                }
            }
            items(chat.turns, key = { it.index }) { turn ->
                DialogueTurn(turn, team == null && chat.running && chat.turns.lastOrNull()?.index == turn.index, !chat.busy,
                    onRead = { voice.stopPlayback(); voice.read(turn.answer) }, onMemory = { model.memoryFromReply(turn.answer) }, onShare = { model.dialog(ChatDialog.Action(LocalAction.Share(turn.answer), false)) })
            }
            if (team != null) {
                item(key = "team-${team.index}") {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.chat_team_results), style = MaterialTheme.typography.titleMedium)
                        SelectionContainer { Text(team.question) }
                        if (team.includedImage) Text(stringResource(R.string.chat_image_attached), color = Amber)
                        Text(stringResource(R.string.chat_team_continue_help), color = Titanium, style = MaterialTheme.typography.bodySmall)
                        team.comparison.problem?.let { ReadError(DanaErrorHandler.messageFor(it)) }
                    }
                }
                items(team.comparison.lanes, key = { "team-lane-${it.profile.id}" }) { lane ->
                    EngineAnswer(lane, canSave = !chat.busy && lane.status == LaneStatus.COMPLETE,
                        saveLabel = R.string.chat_team_continue) { model.keepTeamAnswer(lane.profile.id) }
                }
                item(key = "team-dismiss") {
                    if (!chat.running) TextButton(onClick = model::discardTeam, enabled = !chat.busy) { Text(stringResource(R.string.chat_team_discard)) }
                }
            }
        }
        Surface(Modifier.fillMaxWidth().padding(bottom = 7.dp), shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = chat.localOnly, enabled = !chat.busy && !voice.state.listening,
                        onClick = { model.localMode(!chat.localOnly) }, label = { Text(stringResource(R.string.phone_mode)) })
                    if (!chat.localOnly) {
                        EngineChooser(chat, profiles, model::engine) { model.dialog(ChatDialog.PickTeam) }
                        TaskChooser(chat, model::task)
                    }
                }
                VoiceStatus(voice.state)
                if (chat.photo != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.chat_image_attached), Modifier.weight(1f), color = Amber)
                        TextButton(onClick = { model.photo(null) }, enabled = !chat.busy) { Text(stringResource(R.string.chat_detach)) }
                    }
                }
                OutlinedTextField(chat.draft, model::draft, modifier = Modifier.fillMaxWidth(), enabled = !chat.busy,
                    label = { Text(stringResource(R.string.chat_prompt)) }, maxLines = 3)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { focus.clearFocus(); voice.close(); onMedia() }, enabled = !chat.busy) { Text(stringResource(R.string.media_short)) }
                        VoiceButton(voice, enabled = !chat.busy, useSystemSpeech = useSystemSpeech, turnMode = chat.voiceOpen, onText = { heard ->
                            if (chat.voiceOpen) {
                                model.importText(heard)
                                model.review(profiles, offline)
                            } else model.importText(listOf(chat.draft, heard).filter { it.isNotBlank() }.joinToString(" "))
                        })
                        TextButton(onClick = { focus.clearFocus(); voice.close(); model.toggleVoice() }, enabled = !chat.busy && !voice.state.listening) {
                            Text(stringResource(if (chat.voiceOpen) R.string.voice_exit else R.string.voice_conversation))
                        }
                    }
                    if (chat.running) OutlinedButton(model::stop, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.compare_stop)) }
                    else Button(onClick = { focus.clearFocus(); voice.stopPlayback(); model.review(profiles, offline) },
                        modifier = Modifier.heightIn(min = 48.dp), enabled = !chat.busy && !voice.state.listening && chat.draft.isNotBlank()) {
                        Text(stringResource(R.string.chat_review))
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineChooser(chat: ChatState, profiles: List<ProviderProfile>, choose: (String?) -> Unit, chooseTeam: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val name = profiles.firstOrNull { it.id == chat.manualId }?.config?.name
    Column {
        TextButton(onClick = { expanded = true }, enabled = !chat.busy) {
            Text(when {
                chat.teamIds.isNotEmpty() -> stringResource(R.string.chat_team_count, faDigits(chat.teamIds.size))
                chat.manualId == null -> stringResource(R.string.chat_auto)
                else -> name?.take(24) ?: stringResource(R.string.chat_no_engine_short)
            }, maxLines = 1)
        }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.chat_auto)) }, onClick = { choose(null); expanded = false })
            DropdownMenuItem(text = { Text(stringResource(R.string.chat_team_select)) }, onClick = { expanded = false; chooseTeam() })
            profiles.take(60).forEach { model -> DropdownMenuItem(text = { Text(model.config.name) }, onClick = { choose(model.id); expanded = false }) }
        }
    }
}

@Composable
private fun TaskChooser(chat: ChatState, choose: (AssistantTask) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = true }, enabled = !chat.busy) { Text(stringResource(taskLabel(chat.task))) }
        DropdownMenu(expanded, { expanded = false }) {
            AssistantTask.entries.forEach { task -> DropdownMenuItem(text = { Text(stringResource(taskLabel(task))) }, onClick = { choose(task); expanded = false }) }
        }
    }
}

@Composable
private fun DialogueTurn(turn: ChatTurn, running: Boolean, actionsEnabled: Boolean, onRead: () -> Unit, onMemory: () -> Unit, onShare: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.chat_you), color = Ice, style = MaterialTheme.typography.labelMedium)
            SelectionContainer { Text(turn.question, style = MaterialTheme.typography.bodyLarge) }
            if (turn.includedImage) Text(stringResource(R.string.chat_image_attached), color = Amber)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            turn.engine?.let { Text(it, color = Ice, style = MaterialTheme.typography.titleMedium) }
            when {
                running && turn.answer.isBlank() -> Text(stringResource(R.string.compare_connecting), color = Titanium)
                running -> Text(stringResource(R.string.compare_streaming), color = Titanium)
                turn.interrupted -> Text(stringResource(R.string.compare_stopped), color = Amber)
            }
            if (turn.answer.isNotBlank()) RichAnswer(turn.answer)
            for (attempt in turn.errors) {
                val reason = stringResource(DanaErrorHandler.messageFor(attempt.problem))
                Text("${attempt.name} · $reason", style = MaterialTheme.typography.bodySmall, color = Amber)
                attempt.status?.let { TechnicalText("HTTP $it") }
            }
            if (turn.firstMillis != null) {
                Text(stringResource(R.string.studio_first_text), color = Titanium, style = MaterialTheme.typography.labelSmall)
                TechnicalText("${turn.firstMillis} ms" + (turn.elapsedMillis?.let { " / $it ms" } ?: ""))
            }
            if (!running && turn.answer.isNotBlank()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    CopyReply(turn.answer)
                    TextButton(onClick = onRead, enabled = actionsEnabled) { Text(stringResource(R.string.voice_read)) }
                    TextButton(onClick = onShare, enabled = actionsEnabled) { Text(stringResource(R.string.chat_share)) }
                    if (turn.answer.length <= LocalText.MEMORY_LIMIT) TextButton(onClick = onMemory, enabled = actionsEnabled) {
                        Text(stringResource(R.string.chat_remember_reply))
                    }
                }
            }
        }
    }
}

@Composable
private fun Archive(chat: ChatState, model: ChatViewModel) {
    val archive by model.archive.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.chat_history_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(query, model::search, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.chat_search)) }, singleLine = true)
        when (val data = archive) {
            Reading.Loading -> CircularProgressIndicator()
            is Reading.Failed -> ReadError(data.message)
            is Reading.Ready -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (data.value.isEmpty()) item { Text(stringResource(R.string.chat_history_empty), color = Titanium) }
                if (data.value.isNotEmpty()) item {
                    TextButton(onClick = { model.dialog(ChatDialog.RemoveConversation(null)) }, enabled = !chat.busy) { Text(stringResource(R.string.chat_clear_history)) }
                }
                items(data.value, key = { it.id }) { logged ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(logged.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.fillMaxWidth()) {
                            TextButton({ model.open(logged) }, enabled = !chat.busy) { Text(stringResource(R.string.chat_open)) }
                            TextButton({ model.dialog(ChatDialog.RenameConversation(logged)) }, enabled = !chat.busy) { Text(stringResource(R.string.edit)) }
                            TextButton({ model.dialog(ChatDialog.RemoveConversation(logged.id)) }, enabled = !chat.busy) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryVault(chat: ChatState, model: ChatViewModel) {
    val facts by model.facts.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.chat_memory_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.chat_memory_help), style = MaterialTheme.typography.bodyMedium, color = Titanium)
        Button(onClick = { model.dialog(ChatDialog.EditMemory(null)) }, enabled = !chat.busy) { Text(stringResource(R.string.chat_memory_add)) }
        when (val data = facts) {
            Reading.Loading -> CircularProgressIndicator()
            is Reading.Failed -> ReadError(data.message)
            is Reading.Ready -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (data.value.isEmpty()) item { Text(stringResource(R.string.chat_memory_empty), color = Titanium) }
                if (data.value.isNotEmpty()) item {
                    TextButton(onClick = { model.dialog(ChatDialog.RemoveMemory(null)) }, enabled = !chat.busy) { Text(stringResource(R.string.chat_clear_memory)) }
                }
                items(data.value, key = { it.id }) { fact ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        SelectionContainer { Text(fact.fact) }
                        Row {
                            TextButton({ model.dialog(ChatDialog.EditMemory(fact)) }, enabled = !chat.busy) { Text(stringResource(R.string.edit)) }
                            TextButton({ model.dialog(ChatDialog.RemoveMemory(fact.id)) }, enabled = !chat.busy) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolPanel(chat: ChatState, model: ChatViewModel, onMedia: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.chat_tools_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.chat_tools_help), color = Titanium)
        Button(onClick = { model.assistant(local = true) }, enabled = !chat.busy) { Text(stringResource(R.string.phone_voice_commands)) }
        TextButton(onClick = { model.page(ChatPage.CONVERSATION); onMedia() }, enabled = !chat.busy) { Text(stringResource(R.string.media_title)) }
        AssistantSetup(enabled = !chat.busy)
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.phone_quick), style = MaterialTheme.typography.titleMedium)
            listOf(LocalAction.Battery, LocalAction.MediaVolume, LocalAction.Torch(true), LocalAction.Torch(false),
                LocalAction.Internet, LocalAction.Wifi, LocalAction.Bluetooth, LocalAction.Sound, LocalAction.Clock,
                LocalAction.Panel.DISPLAY, LocalAction.Panel.APPS, LocalAction.Panel.CAMERA, LocalAction.Panel.CALCULATOR,
                LocalAction.Panel.SETTINGS).forEach { action ->
                TextButton(onClick = { model.dialog(ChatDialog.Action(action, false)) }, enabled = !chat.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(actionText(action)) }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.chat_templates_title), style = MaterialTheme.typography.titleMedium)
            listOf(R.string.cmd_volume, R.string.cmd_timer, R.string.cmd_alarm, R.string.cmd_note, R.string.cmd_dial,
                R.string.cmd_event, R.string.cmd_web, R.string.cmd_sms).forEach { example ->
                val sample = stringResource(example)
                TextButton(onClick = { model.commandExample(sample) }, enabled = !chat.busy) { Text(sample) }
            }
        }
    }
}

@Composable
private fun TeamPicker(chat: ChatState, model: ChatViewModel, profiles: List<ProviderProfile>, dismiss: () -> Unit) {
    val eligible = profiles.filter { it.config.endpoint.acceptsCredential(it.hasCredential) && it.config.cloudEngine?.acceptsChat(it.config.model) != false &&
        (chat.photo == null || it.config.photoSupport != PhotoSupport.NO) }
    val visible = eligible.take(60)
    var choice by remember { mutableStateOf(chat.teamIds.intersect(visible.map { it.id }.toSet())) }
    ChatModal(R.string.chat_team_select, R.string.chat_team_apply, choice.size in 2..ComparisonLimits.ENGINE_COUNT, chat,
        dismiss, { model.chooseTeam(choice) }) {
        Text(stringResource(R.string.chat_team_choose_help), color = Titanium)
        if (eligible.size < 2) Text(stringResource(R.string.chat_team_need_two), color = Amber)
        visible.forEach { target ->
            val selected = target.id in choice
            val enabled = selected || choice.size < ComparisonLimits.ENGINE_COUNT
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(selected, enabled = enabled, role = Role.Checkbox) {
                    choice = if (selected) choice - target.id else choice + target.id
                }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(target.config.name, color = Ice)
                    TechnicalText(target.config.model)
                    if (target.config.photoSupport == PhotoSupport.YES) Text(stringResource(R.string.engine_vision), color = Mint,
                        style = MaterialTheme.typography.labelSmall)
                }
                Checkbox(selected, onCheckedChange = null, enabled = enabled)
            }
        }
    }
}

@Composable
private fun ChatOverlay(dialog: ChatDialog, chat: ChatState, model: ChatViewModel, offline: Boolean, profiles: List<ProviderProfile>) {
    val dismiss = { model.dialog(null) }
    when (dialog) {
        ChatDialog.PickTeam -> TeamPicker(chat, model, profiles, dismiss)
        ChatDialog.Options -> ChatModal(R.string.chat_privacy, R.string.chat_done, true, chat, dismiss, dismiss) {
            Text(stringResource(R.string.chat_privacy_help))
            ToggleLine(R.string.chat_history_switch, chat.options.autoHistory, !chat.busy) { model.options(chat.options.copy(autoHistory = it)) }
            ToggleLine(R.string.chat_context_switch, chat.options.includeContext, !chat.busy) { model.options(chat.options.copy(includeContext = it)) }
            ToggleLine(R.string.chat_memory_switch, chat.options.useMemory, !chat.busy) { model.options(chat.options.copy(useMemory = it)) }
            ToggleLine(R.string.chat_mask_switch, chat.options.mask, !chat.busy) { model.options(chat.options.copy(mask = it)) }
            Text(stringResource(R.string.chat_style), style = MaterialTheme.typography.titleSmall)
            AssistantStyle.entries.forEach { style ->
                FilterChip(selected = chat.options.style == style, enabled = !chat.busy,
                    onClick = { model.options(chat.options.copy(style = style)) }, label = { Text(stringResource(when (style) {
                        AssistantStyle.BALANCED -> R.string.style_balanced
                        AssistantStyle.CONCISE -> R.string.style_concise
                        AssistantStyle.DETAILED -> R.string.style_detailed
                    })) })
            }
        }
        is ChatDialog.Send -> ChatModal(R.string.chat_destinations, R.string.compare_send, !offline, chat, dismiss, { model.send(offline) }, secure = true) {
            val data = dialog.route.input
            val parallel = dialog.route.parallel
            Text(stringResource(if (parallel) R.string.chat_team_transfer else R.string.chat_transfer_notice))
            if (dialog.route.candidates.all { it.config.protocol == ProviderProtocol.OPENROUTER_FREE }) {
                Text(stringResource(R.string.engine_free_review), color = Mint)
            }
            dialog.route.candidates.forEachIndexed { index, target ->
                Text(stringResource(when {
                    parallel -> R.string.chat_team_target
                    index == 0 -> R.string.chat_preferred
                    else -> R.string.chat_fallback
                }, target.config.name), color = Ice)
                TechnicalText(target.config.baseUrl, maxLines = 4)
                TechnicalText(target.config.model)
            }
            if (dialog.route.candidates.any { it.config.protocol == ProviderProtocol.APPLE_MAC }) Text(stringResource(R.string.apple_language_note), color = Amber)
            Text(stringResource(R.string.chat_transaction_preview), style = MaterialTheme.typography.titleMedium)
            SelectionContainer { Text(data.prompt) }
            if (data.history.isNotEmpty()) {
                Text(stringResource(R.string.chat_history_included, data.history.size / 2), color = Ice)
                data.history.forEach { previous ->
                    Text(stringResource(if (previous.role == MessageRole.USER) R.string.chat_you else R.string.app_name), style = MaterialTheme.typography.labelSmall)
                    Text(previous.body, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (data.omittedPairs > 0) Text(stringResource(R.string.chat_history_limited), color = Titanium)
            if (data.facts.isNotEmpty()) Text(stringResource(R.string.chat_facts_included, data.facts.joinToString("\n")), color = Ice)
            if (data.maskedValues > 0) Text(stringResource(R.string.chat_masked), color = Mint)
            if (data.photo != null) {
                Text(stringResource(R.string.chat_image_notice), color = Amber)
                if (dialog.route.candidates.any { it.config.photoSupport == PhotoSupport.UNKNOWN }) {
                    Text(stringResource(R.string.chat_image_compatibility), color = Amber)
                }
            }
            if (offline) ReadError(R.string.chat_offline_help)
        }
        is ChatDialog.Action -> {
            val context = LocalContext.current
            val command = dialog.command
            val web = command is LocalAction.WebSearch
            ChatModal(R.string.chat_confirm_action, R.string.chat_execute, !web || !offline, chat, dismiss, {
                model.executeAction(offline) { DeviceActions.execute(context, it) }
            }, secure = true) {
                Text(actionText(command))
                Text(stringResource(when (command) {
                    is LocalAction.Note -> R.string.chat_note_private
                    is LocalAction.Dial -> R.string.phone_dial_review
                    is LocalAction.WebSearch -> if (offline) R.string.phone_web_offline else R.string.phone_web_review
                    is LocalAction.SetVolume, is LocalAction.VolumeStep, is LocalAction.Torch, LocalAction.Battery, LocalAction.MediaVolume -> R.string.phone_local_review
                    else -> R.string.chat_external_action
                }), color = Titanium)
            }
        }
        is ChatDialog.EditMemory -> ChatTextEditor(R.string.chat_memory_add, dialog.draft, LocalText.MEMORY_LIMIT, chat, dismiss, model::saveMemoryDraft)
        is ChatDialog.RenameConversation -> ChatTextEditor(R.string.chat_rename, dialog.item.title, LocalText.TITLE_LIMIT, chat, dismiss, model::rename)
        is ChatDialog.RemoveMemory, is ChatDialog.RemoveConversation -> ChatModal(R.string.chat_confirm_delete, R.string.delete, true, chat, dismiss, model::remove) {
            Text(stringResource(if (dialog is ChatDialog.RemoveConversation && dialog.id == null || dialog is ChatDialog.RemoveMemory && dialog.id == null)
                R.string.chat_delete_all_notice else R.string.chat_delete_notice))
        }
    }
}

@Composable
private fun ChatTextEditor(title: Int, initial: String, limit: Int, chat: ChatState, dismiss: () -> Unit, save: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    ChatModal(title, R.string.save, text.isNotBlank(), chat, dismiss, { save(text) }, secure = true) {
        OutlinedTextField(text, { if (it.length <= limit) text = it }, Modifier.fillMaxWidth(), enabled = !chat.busy,
            minLines = if (limit == LocalText.TITLE_LIMIT) 1 else 3, maxLines = 6)
    }
}

@Composable
internal fun ChatModal(
    title: Int, button: Int, enabled: Boolean, chat: ChatState, dismiss: () -> Unit, confirm: () -> Unit,
    secure: Boolean = false, content: @Composable () -> Unit,
) {
    AlertDialog(onDismissRequest = dismiss, properties = DialogProperties(
        dismissOnBackPress = !chat.busy, dismissOnClickOutside = !chat.busy,
        securePolicy = if (secure) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit,
    ), title = { Text(stringResource(title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chat.error?.let { ReadError(it) }
            content()
        }
    }, dismissButton = { TextButton(dismiss, enabled = !chat.busy) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { TextButton(confirm, enabled = enabled && !chat.busy) { Text(stringResource(button)) } })
}

@Composable
private fun actionText(action: LocalAction): String = when (action) {
    is LocalAction.Note -> stringResource(R.string.chat_action_note, action.text)
    is LocalAction.Timer -> stringResource(R.string.chat_action_timer, action.seconds)
    is LocalAction.Alarm -> stringResource(R.string.chat_action_alarm, action.hour, action.minute)
    is LocalAction.CalendarDraft -> stringResource(R.string.chat_action_calendar, action.title)
    is LocalAction.SmsDraft -> stringResource(R.string.chat_action_sms, action.number, action.text)
    is LocalAction.Share -> stringResource(R.string.chat_action_share, action.text)
    LocalAction.Wifi -> stringResource(R.string.chat_action_wifi)
    LocalAction.Bluetooth -> stringResource(R.string.chat_action_bluetooth)
    LocalAction.Sound -> stringResource(R.string.chat_action_sound)
    LocalAction.Clock -> stringResource(R.string.chat_action_clock)
    LocalAction.Internet -> stringResource(R.string.phone_internet)
    LocalAction.Battery -> stringResource(R.string.phone_battery)
    LocalAction.MediaVolume -> stringResource(R.string.phone_media_volume)
    is LocalAction.SetVolume -> stringResource(R.string.phone_set_volume, faDigits(action.percent))
    LocalAction.VolumeStep.LOWER -> stringResource(R.string.phone_volume_lower)
    LocalAction.VolumeStep.RAISE -> stringResource(R.string.phone_volume_raise)
    is LocalAction.Torch -> stringResource(if (action.on) R.string.phone_torch_on else R.string.phone_torch_off)
    is LocalAction.Dial -> stringResource(R.string.phone_dial, action.number)
    is LocalAction.WebSearch -> stringResource(R.string.phone_web, action.query)
    LocalAction.Panel.SETTINGS -> stringResource(R.string.phone_settings)
    LocalAction.Panel.DISPLAY -> stringResource(R.string.phone_display)
    LocalAction.Panel.APPS -> stringResource(R.string.phone_apps)
    LocalAction.Panel.CAMERA -> stringResource(R.string.phone_camera)
    LocalAction.Panel.CALCULATOR -> stringResource(R.string.phone_calculator)
}
