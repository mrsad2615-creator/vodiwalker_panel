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
import ai.dana.domain.acceptsChat
import ai.dana.domain.SendLane
import ai.dana.domain.LocalAction
import ai.dana.domain.LocalText
import ai.dana.domain.MessageRole
import ai.dana.domain.ProviderProfile
import ai.dana.domain.ProviderProtocol
import ai.dana.domain.RouterFreeModel
import ai.dana.localai.DeviceModel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
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
    catalog: Reading<List<RouterFreeModel>>?,
    dispatch: (DanaIntent) -> Unit,
    onEnableNetwork: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chat by model.state.collectAsStateWithLifecycle()
    val bundled by model.deviceChoices.collectAsStateWithLifecycle()
    val profiles = remember(dashboard.profiles) { (dashboard.profiles as? Reading.Ready)?.value.orEmpty().filter { it.config.acceptsChat } }
    val offline = (dashboard.offline as? Reading.Ready)?.value != false
    BackHandler(enabled = chat.page != ChatPage.CONVERSATION && chat.dialog == null && !chat.busy) {
        model.page(ChatPage.CONVERSATION)
    }
    val newDestination = dashboard.session.newChatDestination
    LaunchedEffect(newDestination, chat.busy) {
        if (newDestination != null && !chat.busy) {
            model.select(newDestination)
            dispatch(DanaIntent.ConsumeChatDestination)
        }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (chat.page != ChatPage.CONVERSATION) TextButton(onClick = { model.page(ChatPage.CONVERSATION) }, enabled = !chat.busy) {
            Text(stringResource(R.string.workspace_back_chat))
        }
        if (chat.dialog == null) {
            chat.error?.let { ReadError(it) }
            (chat.preparation ?: chat.notice ?: dashboard.session.notice)?.let { msg ->
                Text(stringResource(msg), color = Mint, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        when (chat.page) {
            ChatPage.CONVERSATION -> Dialogue(chat, model, media::open, profiles, offline, bundled, catalog,
                onSettings, dispatch, onEnableNetwork)
            ChatPage.HISTORY -> Archive(chat, model)
            ChatPage.MEMORY -> MemoryVault(chat, model)
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
    bundled: Set<DeviceModel>?,
    catalog: Reading<List<RouterFreeModel>>?,
    onSettings: () -> Unit,
    dispatch: (DanaIntent) -> Unit,
    onEnableNetwork: () -> Unit,
) {
    val voice = rememberDanaVoice()
    val focus = LocalFocusManager.current
    var setupVoice by remember { mutableStateOf(false) }
    var systemSpeech by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val isTeam = chat.teamIds.isNotEmpty()
    val deviceModel = (chat.destination as? ChatDestination.Local)?.model?.takeUnless { isTeam }
    val profileId = when (val destination = chat.destination) {
        is ChatDestination.Local -> null
        is ChatDestination.Saved -> destination.profileId
        is ChatDestination.OnlineFree -> destination.profileId
    }
    val profile = profiles.firstOrNull { it.id == profileId && it.config.endpoint.acceptsCredential(it.hasCredential) }
    val selectedName = if (isTeam) stringResource(R.string.chat_team_count, faDigits(chat.teamIds.size))
        else deviceModel?.title ?: (chat.destination as? ChatDestination.OnlineFree)?.model?.name ?: profile?.config?.name.orEmpty()
    val canSend = if (deviceModel != null) bundled?.contains(deviceModel) == true
        else !offline && (isTeam || profile != null)
    val dismissPicker = { picking = false; dispatch(DanaIntent.CancelFreeModels) }
    val chooseModel = {
        focus.clearFocus()
        voice.close()
        picking = true
    }
    if (picking) ModelPicker(bundled, chat.destination.takeUnless { isTeam }, profiles, catalog, offline,
        choose = { model.select(it); dismissPicker() },
        signIn = { dismissPicker(); dispatch(DanaIntent.OpenRouterSignIn(it)) },
        fetch = { dispatch(DanaIntent.ReadFreeModels) }, onSettings = { dismissPicker(); onSettings() },
        onTeam = { dismissPicker(); model.dialog(ChatDialog.PickTeam) }, dismiss = dismissPicker)
    val useSystemSpeech = deviceModel == null && systemSpeech && !offline && chat.voiceOpen
    LaunchedEffect(offline, chat.voiceOpen, deviceModel) {
        if ((deviceModel != null || offline || !chat.voiceOpen) && systemSpeech) {
            systemSpeech = false
            voice.close()
        }
    }
    val done = chat.turns.lastOrNull()?.takeIf { it.complete && it.readWhenDone && !chat.running }
    LaunchedEffect(done?.index) {
        if (done != null) {
            model.speechConsumed(done.index)
            voice.read(done.answer)
        }
    }
    if (setupVoice) VoiceSetup(voice, offline || deviceModel != null, useSystemSpeech, onSystemSpeech = { systemSpeech = it }, onClose = { setupVoice = false })
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var menu by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = chooseModel, enabled = !chat.busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.models_picker), color = Titanium, style = MaterialTheme.typography.labelSmall)
                    if (selectedName.isNotBlank()) Text(selectedName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TextButton(onClick = model::newConversation, enabled = !chat.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.chat_new))
            }
            Box {
                IconButton(onClick = { menu = true }, enabled = !chat.busy, modifier = Modifier.size(48.dp)) {
                    StudioMark(Mark.MORE, Titanium, stringResource(R.string.workspace_chat_options))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf(ChatPage.HISTORY to R.string.chat_history, ChatPage.MEMORY to R.string.chat_memory).forEach { (page, label) ->
                        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { menu = false; model.page(page) })
                    }
                    DropdownMenuItem(text = { Text(stringResource(if (chat.voiceOpen) R.string.voice_exit else R.string.voice_conversation)) },
                        onClick = { menu = false; focus.clearFocus(); voice.close(); model.toggleVoice() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.chat_privacy)) }, onClick = { menu = false; model.dialog(ChatDialog.Options) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.workspace_settings)) }, onClick = { menu = false; onSettings() })
                }
            }
        }
        if (deviceModel == null && !canSend && offline) {
            TextButton(onClick = onEnableNetwork, enabled = !chat.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.workspace_enable_ai))
            }
        } else if (deviceModel == null && canSend && chat.turns.isEmpty()) {
            StudioPill(stringResource(R.string.workspace_ai_ready), color = Mint, dot = true)
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
            if (chat.turns.isEmpty() && team == null && !chat.voiceOpen) item(key = "welcome") {
                ReadyModels(bundled, chat.destination.takeUnless { isTeam }, !chat.busy,
                    onSelect = { model.select(ChatDestination.Local(it)) }, onMore = chooseModel)
                if (canSend && !chat.busy && chat.draft.isBlank()) {
                    val example = stringResource(if (deviceModel == DeviceModel.SMOL) R.string.models_smol_example else R.string.workspace_example_chat)
                    TextButton(onClick = { model.draft(example) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(example) }
                }
            }
            items(chat.turns, key = { it.index }) { turn ->
                DialogueTurn(turn, team == null && chat.running && chat.turns.lastOrNull()?.index == turn.index, !chat.busy,
                    onRead = { voice.stopPlayback(); voice.read(turn.answer) }, onMemory = { model.memoryFromReply(turn.answer) }, onShare = { model.dialog(ChatDialog.ShareReply(turn.answer)) })
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
                        IconButton(onClick = { focus.clearFocus(); voice.close(); onMedia() }, enabled = !chat.busy,
                            modifier = Modifier.size(48.dp)) {
                            StudioMark(Mark.ADD, Titanium, stringResource(R.string.media_short))
                        }
                        if (chat.voiceOpen) VoiceButton(voice, enabled = !chat.busy, useSystemSpeech = useSystemSpeech, turnMode = true,
                            onText = model::importText)
                        else {
                            VoiceButton(voice, enabled = !chat.busy, useSystemSpeech = false, turnMode = false, compact = true,
                                onText = { heard -> model.importText(listOf(chat.draft, heard).filter { it.isNotBlank() }.joinToString(" ")) })
                            if (deviceModel == null) FilterChip(selected = chat.options.lane == SendLane.FAST, enabled = !chat.busy,
                                modifier = Modifier.heightIn(min = 48.dp),
                                onClick = { model.options(chat.options.copy(lane = if (chat.options.lane == SendLane.FAST) SendLane.STANDARD else SendLane.FAST)) },
                                label = { Text(stringResource(R.string.workspace_fast)) }, leadingIcon = { StudioMark(Mark.ACTION, Mint) })
                        }
                    }
                    if (chat.running) OutlinedButton(model::stop, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.compare_stop)) }
                    else Button(onClick = {
                        focus.clearFocus()
                        voice.stopPlayback()
                        model.submit(profiles, offline)
                    },
                        modifier = Modifier.heightIn(min = 48.dp), enabled = canSend && !chat.busy && !voice.state.listening && chat.draft.isNotBlank()) {
                        Text(stringResource(R.string.workspace_send))
                    }
                }
            }
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
            if (turn.answer.isNotBlank()) {
                Column(Modifier.testTag(if (turn.complete && !running) "completed-reply" else "partial-reply")) {
                    if (running) Text(turn.answer, style = MaterialTheme.typography.bodyLarge) else RichAnswer(turn.answer)
                }
            }
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
private fun TeamPicker(chat: ChatState, model: ChatViewModel, profiles: List<ProviderProfile>, dismiss: () -> Unit) {
    val eligible = profiles.filter { it.config.endpoint.acceptsCredential(it.hasCredential) && it.config.acceptsChat &&
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
            val local = chat.teamIds.isEmpty() && chat.destination is ChatDestination.Local
            if (!local) TaskChooser(chat, model::task)
            Text(stringResource(if (local) R.string.models_local_privacy else R.string.chat_privacy_help))
            ToggleLine(R.string.chat_history_switch, chat.options.autoHistory, !chat.busy) { model.options(chat.options.copy(autoHistory = it)) }
            ToggleLine(R.string.chat_context_switch, chat.options.includeContext, !chat.busy) { model.options(chat.options.copy(includeContext = it)) }
            if (!local) {
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
        }
        is ChatDialog.Send -> ChatModal(R.string.chat_destinations, R.string.send_allow, !offline, chat, dismiss, { model.approve(offline) }, secure = true) {
            val data = dialog.route.input
            val parallel = dialog.route.parallel
            Text(stringResource(if (parallel) R.string.chat_team_transfer else R.string.chat_transfer_notice))
            if (dialog.route.candidates.all { it.config.protocol == ProviderProtocol.OPENROUTER_FREE }) {
                Text(stringResource(R.string.engine_free_review), color = Mint)
            }
            dialog.route.destinations.forEach { target ->
                Text(stringResource(if (parallel) R.string.chat_team_target else R.string.chat_preferred, target.config.name), color = Ice)
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
            if (chat.options.includeContext) Text(stringResource(R.string.chat_context_switch), color = Titanium)
            if (chat.options.useMemory) Text(stringResource(R.string.chat_memory_switch), color = Titanium)
            if (data.photo != null) {
                Text(stringResource(R.string.chat_image_notice), color = Amber)
                if (dialog.route.candidates.any { it.config.photoSupport == PhotoSupport.UNKNOWN }) {
                    Text(stringResource(R.string.chat_image_compatibility), color = Amber)
                }
            }
            if (offline) ReadError(R.string.chat_offline_help)
        }
        is ChatDialog.ShareReply -> {
            val context = LocalContext.current
            ChatModal(R.string.chat_confirm_action, R.string.chat_execute, true, chat, dismiss, {
                model.shareReply { DeviceActions.execute(context, LocalAction.Share(it)) }
            }, secure = true) {
                Text(stringResource(R.string.chat_action_share, dialog.text))
                Text(stringResource(R.string.chat_external_action), color = Titanium)
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
internal fun actionText(action: LocalAction): String = when (action) {
    is LocalAction.Note -> stringResource(R.string.chat_action_note, action.text)
    is LocalAction.Timer -> stringResource(R.string.chat_action_timer, action.seconds)
    is LocalAction.Alarm -> stringResource(R.string.chat_action_alarm, action.hour, action.minute)
    is LocalAction.CalendarDraft -> action.start?.let { start ->
        stringResource(R.string.phone_calendar_time, action.title,
            stringResource(if (start.daysFromToday == 0) R.string.phone_today else R.string.phone_tomorrow),
            start.hour, start.minute)
    } ?: stringResource(R.string.chat_action_calendar, action.title)
    is LocalAction.LaunchApp -> stringResource(R.string.phone_app_open, action.label)
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
