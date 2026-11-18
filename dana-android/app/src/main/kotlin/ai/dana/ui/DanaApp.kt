package ai.dana.ui

import ai.dana.R
import ai.dana.domain.CloudEngine
import ai.dana.domain.NetworkStatus
import ai.dana.domain.PhotoSupport
import ai.dana.domain.cloudEngine
import ai.dana.domain.photoSupport
import ai.dana.domain.ProviderNetwork
import ai.dana.domain.ProviderProfile
import ai.dana.domain.ProviderProtocol
import ai.dana.media.MediaViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val rootTabs = listOf(
    DashboardTab.HOME to Mark.HOME,
    DashboardTab.CHAT to Mark.CHAT,
    DashboardTab.SETTINGS to Mark.ENGINES,
    DashboardTab.ACCOUNT to Mark.ACCOUNT,
)

@Composable
internal fun DanaApp(model: DanaViewModel, chat: ChatViewModel, media: MediaViewModel, account: AccountViewModel, art: ArtViewModel) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val session = state.session
    val offline = (state.offline as? Reading.Ready)?.value != false
    val ime = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val secondary = session.tab in setOf(DashboardTab.COMPARE, DashboardTab.NOTES, DashboardTab.ART)
    val dispatch: (DanaIntent) -> Unit = { intent ->
        if (intent is DanaIntent.Navigate && !session.busy) {
            if (intent.tab != DashboardTab.CHAT) { chat.background(); media.close() }
            if (intent.tab != DashboardTab.ACCOUNT) account.background()
            if (intent.tab != DashboardTab.ART) art.stop()
        }
        model.dispatch(intent)
    }
    val goHome = { dispatch(DanaIntent.Navigate(DashboardTab.HOME)) }
    val allowNetwork = { dispatch(DanaIntent.SetOffline(false)) }
    BackHandler(enabled = session.tab != DashboardTab.HOME && session.editor == null && !session.busy && !ime, onBack = goHome)
    Scaffold(
        containerColor = Obsidian,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { StudioHeader(session.tab, offline, secondary, goHome) },
        bottomBar = {
            if (!ime) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                rootTabs.forEach { (tab, mark) ->
                    val selected = session.tab == tab || (tab == DashboardTab.HOME && secondary)
                    NavigationBarItem(
                        selected = selected, enabled = !session.busy,
                        onClick = { dispatch(DanaIntent.Navigate(tab)) },
                        icon = { StudioMark(mark, if (selected) Mint else Titanium) },
                        label = { Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(selectedTextColor = Mint, unselectedTextColor = Titanium,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets), contentAlignment = Alignment.TopCenter) {
            key(session.tab) {
                if (session.tab == DashboardTab.CHAT) {
                    ChatScreen(chat, media, state, onSettings = { dispatch(DanaIntent.Navigate(DashboardTab.SETTINGS)) },
                        modifier = Modifier.widthIn(max = 740.dp).imePadding())
                } else LazyColumn(
                    modifier = Modifier.widthIn(max = 680.dp).fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (session.editor == null) {
                        session.error?.let { message -> item { ReadError(message) } }
                        session.notice?.let { message -> item {
                            Text(stringResource(message), color = Mint,
                                modifier = Modifier.padding(8.dp).semantics { liveRegion = LiveRegionMode.Polite })
                        } }
                    }
                    when (session.tab) {
                        DashboardTab.CHAT -> Unit
                        DashboardTab.HOME -> home(state, dispatch, onMedia = {
                            chat.page(ChatPage.CONVERSATION)
                            dispatch(DanaIntent.Navigate(DashboardTab.CHAT))
                            media.open()
                        }, onVoice = {
                            chat.assistant()
                            dispatch(DanaIntent.Navigate(DashboardTab.CHAT))
                        }, onDevice = {
                            chat.page(ChatPage.TOOLS)
                            dispatch(DanaIntent.Navigate(DashboardTab.CHAT))
                        })
                        DashboardTab.COMPARE -> comparison(state, dispatch)
                        DashboardTab.NOTES -> notes(state, dispatch)
                        DashboardTab.SETTINGS -> settings(state, dispatch)
                        DashboardTab.ART -> item { ArtScreen(art, state, dispatch) }
                        DashboardTab.ACCOUNT -> item { AccountScreen(account, offline, !session.busy && state.offline is Reading.Ready, allowNetwork) }
                    }
                }
            }
        }
    }
    session.editor?.let { editor ->
        key(editor) { DanaEditor(editor, session, offline, dispatch) }
    }
}

private fun LazyListScope.home(
    state: DashboardState, dispatch: (DanaIntent) -> Unit, onMedia: () -> Unit, onVoice: () -> Unit, onDevice: () -> Unit,
) {
    fun open(tab: DashboardTab): () -> Unit = { dispatch(DanaIntent.Navigate(tab)) }
    item { StudioHero(open(DashboardTab.CHAT)) }
    item {
        Text(stringResource(R.string.hub_heading), style = MaterialTheme.typography.titleLarge)
    }
    item {
        ActionPair(
            StudioAction(R.string.quick_title, R.string.quick_tile, Mark.ENGINES, open(DashboardTab.SETTINGS)),
            StudioAction(R.string.art_title, R.string.art_tile, Mark.IMAGE, open(DashboardTab.ART)),
        )
    }
    item {
        ActionPair(
            StudioAction(R.string.tab_notes, R.string.studio_notes_hint, Mark.NOTES, open(DashboardTab.NOTES)),
            StudioAction(R.string.tab_compare, R.string.studio_compare_hint, Mark.COMPARE, open(DashboardTab.COMPARE)),
        )
    }
    item {
        ActionPair(
            StudioAction(R.string.voice_conversation, R.string.voice_home_hint, Mark.CHAT, onVoice),
            StudioAction(R.string.phone_mode, R.string.phone_home_hint, Mark.ENGINES, onDevice),
        )
    }
    item {
        ActionPair(
            StudioAction(R.string.studio_read_image, R.string.studio_read_image_hint, Mark.IMAGE, onMedia),
            StudioAction(R.string.tab_account, R.string.studio_account_hint, Mark.ACCOUNT, open(DashboardTab.ACCOUNT)),
        )
    }
    val configured = (state.profiles as? Reading.Ready)?.value?.size ?: 0
    item {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.studio_local_privacy), style = MaterialTheme.typography.bodySmall, color = Titanium, modifier = Modifier.weight(1f))
            StudioPill(stringResource(R.string.studio_engines_count, faDigits(configured)), Titanium)
        }
    }
}

private fun LazyListScope.notes(state: DashboardState, dispatch: (DanaIntent) -> Unit) {
    val busy = state.session.busy
    item {
        SectionIntro(R.string.tab_notes, R.string.notes_description)
        Button(
            onClick = { dispatch(DanaIntent.EditNote(null)) },
            enabled = !busy && state.notes is Reading.Ready,
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(stringResource(R.string.note_add)) }
    }
    when (val notes = state.notes) {
        Reading.Loading -> item { Loading() }
        is Reading.Failed -> item { ReadError(notes.message) { dispatch(DanaIntent.Reload) } }
        is Reading.Ready -> {
            if (notes.value.isEmpty()) item { EmptyCard(R.string.notes_empty) }
            items(notes.value, key = { it.id }) { note ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(note.text, maxLines = 6, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    RecordActions(
                        enabled = !busy,
                        onEdit = { dispatch(DanaIntent.EditNote(note)) },
                        onDelete = { dispatch(DanaIntent.RequestNoteDeletion(note.id)) },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.settings(state: DashboardState, dispatch: (DanaIntent) -> Unit) {
    val enabled = !state.session.busy && state.profiles is Reading.Ready
    val preferred = (state.profiles as? Reading.Ready)?.value?.firstOrNull { it.id == state.preferredEngine }
    item { SectionIntro(R.string.engines_title, R.string.quick_settings_help) }
    item {
        GlassCard(Modifier.fillMaxWidth()) {
            if (preferred != null) {
                StudioPill(stringResource(R.string.quick_default))
                Text(preferred.config.name, style = MaterialTheme.typography.titleLarge)
                TechnicalText(preferred.config.model)
                state.session.probeMillis?.let { TechnicalText("${it} ms") }
                Button(onClick = { dispatch(DanaIntent.Navigate(DashboardTab.CHAT)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = enabled) { Text(stringResource(R.string.hub_begin_chat)) }
            }
            if (preferred == null) {
                Button(onClick = { dispatch(DanaIntent.OpenQuick()) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.quick_title))
                }
            } else OutlinedButton(onClick = { dispatch(DanaIntent.OpenQuick()) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.quick_add_other))
            }
            Text(stringResource(R.string.quick_no_account), color = Titanium, style = MaterialTheme.typography.bodySmall)
        }
    }
    when (val mode = state.offline) {
        Reading.Loading -> item { Loading() }
        is Reading.Failed -> item { ReadError(mode.message) { dispatch(DanaIntent.Reload) } }
        is Reading.Ready -> item {
            GlassCard(Modifier.fillMaxWidth()) {
                ToggleLine(R.string.offline_title, mode.value, !state.session.busy) { dispatch(DanaIntent.SetOffline(it)) }
                if (mode.value) Text(stringResource(R.string.quick_offline_help), style = MaterialTheme.typography.bodySmall, color = Titanium)
                else NetworkLine(state.network)
                TextButton(onClick = { dispatch(DanaIntent.RevokeDirectSend) }, enabled = !state.session.busy) {
                    Text(stringResource(R.string.send_revoke))
                }
            }
        }
    }
    when (val profiles = state.profiles) {
        Reading.Loading -> item { Loading() }
        is Reading.Failed -> item { ReadError(profiles.message) { dispatch(DanaIntent.Reload) } }
        is Reading.Ready -> {
            item { Text(stringResource(R.string.studio_saved_engines), style = MaterialTheme.typography.titleLarge) }
            if (profiles.value.isEmpty()) item { EmptyCard(R.string.engines_empty) }
            items(profiles.value, key = { it.id }) { profile -> ProfileCard(profile, state.session.busy, dispatch) }
        }
    }
    item {
        GlassCard(Modifier.fillMaxWidth()) {
            var expanded by remember { mutableStateOf(false) }
            TextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.studio_advanced_hide else R.string.studio_advanced)) }
            if (expanded) {
                TextButton(onClick = { dispatch(DanaIntent.EditProvider(null, ProviderProtocol.OPENROUTER_FREE)) }, enabled = enabled) { Text(stringResource(R.string.engine_add_free)) }
                TextButton(onClick = { dispatch(DanaIntent.EditProvider(null, ProviderProtocol.GEMINI_NATIVE)) }, enabled = enabled) { Text(stringResource(R.string.engine_add_gemini)) }
                CloudEngine.entries.forEach { cloud ->
                    TextButton(onClick = { dispatch(DanaIntent.EditProvider(null, cloud = cloud)) }, enabled = enabled) {
                        Text(stringResource(R.string.engine_add_cloud, cloud.label))
                    }
                }
                TextButton(onClick = { dispatch(DanaIntent.EditProvider(null, lanPreset = true)) }, enabled = enabled) { Text(stringResource(R.string.engine_add_lan)) }
                TextButton(onClick = { dispatch(DanaIntent.EditProvider(null)) }, enabled = enabled) { Text(stringResource(R.string.engine_add_custom)) }
                TextButton(onClick = { dispatch(DanaIntent.EditProvider(null, ProviderProtocol.APPLE_MAC)) }, enabled = enabled) { Text(stringResource(R.string.engine_add_apple)) }
            }
        }
    }
    item {
        TextButton(onClick = { dispatch(DanaIntent.Navigate(DashboardTab.COMPARE)) }, enabled = !state.session.busy) {
            Text(stringResource(R.string.tab_compare))
        }
    }
}

@Composable
private fun ProfileCard(profile: ProviderProfile, busy: Boolean, dispatch: (DanaIntent) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(profile.config.name, style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(protocolLabel(profile.config.protocol)),
            style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
            color = Ice,
        )
        TechnicalText(profile.config.model)
        TechnicalText(profile.config.baseUrl)
        if (profile.config.photoSupport == PhotoSupport.YES) Text(stringResource(R.string.engine_vision), color = Mint,
            style = MaterialTheme.typography.labelMedium)
        val network = if (profile.config.network == ProviderNetwork.LOCAL_NETWORK) R.string.engine_lan else R.string.engine_internet
        Text(stringResource(network), style = MaterialTheme.typography.bodySmall, color = Titanium)
        Text(
            stringResource(if (profile.hasCredential) R.string.engine_has_key else R.string.engine_no_key),
            style = MaterialTheme.typography.labelMedium,
            color = if (profile.hasCredential) Mint else Titanium,
        )
        OutlinedButton(onClick = { dispatch(DanaIntent.TestProfile(profile)) }, enabled = !busy) {
            Text(stringResource(R.string.quick_test_title))
        }
        RecordActions(
            enabled = !busy,
            onEdit = { dispatch(DanaIntent.EditProvider(profile)) },
            onDelete = { dispatch(DanaIntent.RequestProviderDeletion(profile.id)) },
        )
    }
}

internal fun protocolLabel(protocol: ProviderProtocol): Int = when (protocol) {
    ProviderProtocol.OPENAI_COMPATIBLE -> R.string.engine_openai
    ProviderProtocol.OPENROUTER_FREE -> R.string.engine_openrouter
    ProviderProtocol.GEMINI_NATIVE -> R.string.engine_gemini
    ProviderProtocol.APPLE_MAC -> R.string.engine_apple
}

@Composable
internal fun TechnicalText(value: String, maxLines: Int = 2) {
    Text(
        value,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall.copy(
            textDirection = TextDirection.Ltr,
            fontFamily = FontFamily.Monospace,
            fontFeatureSettings = "tnum",
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        color = Titanium,
    )
}

@Composable
private fun RecordActions(enabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onEdit, enabled = enabled) { Text(stringResource(R.string.edit)) }
        TextButton(onClick = onDelete, enabled = enabled) { Text(stringResource(R.string.delete)) }
    }
}

@Composable
internal fun SectionIntro(title: Int, body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = Titanium)
    }
}

@Composable
private fun EmptyCard(message: Int) {
    GlassCard(Modifier.fillMaxWidth()) { Text(stringResource(message), color = Titanium) }
}

@Composable
internal fun Loading() {
    Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
    }
}

@Composable
internal fun ReadError(message: Int, onRetry: (() -> Unit)? = null) {
    Column {
        Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        if (onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun NetworkLine(status: NetworkStatus) {
    val (label, tint) = when (status) {
        NetworkStatus.CHECKING -> R.string.status_checking to Titanium
        NetworkStatus.NO_DEFAULT_NETWORK -> R.string.status_no_network to Titanium
        NetworkStatus.INTERNET_UNVALIDATED -> R.string.status_unvalidated to Amber
        NetworkStatus.INTERNET_VALIDATED -> R.string.status_validated to Mint
        NetworkStatus.UNAVAILABLE -> R.string.status_unavailable to Amber
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(tint, CircleShape))
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}
