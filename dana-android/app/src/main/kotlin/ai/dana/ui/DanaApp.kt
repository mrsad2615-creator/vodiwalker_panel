package ai.dana.ui

import ai.dana.R
import ai.dana.domain.CloudEngine
import ai.dana.domain.ManagedKind
import ai.dana.domain.ManagedProblem
import ai.dana.domain.NetworkStatus
import ai.dana.domain.PhotoSupport
import ai.dana.domain.acceptsChat
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val rootTabs = listOf(
    DashboardTab.CHAT to Mark.CHAT,
    DashboardTab.ART to Mark.IMAGE,
    DashboardTab.PHONE to Mark.PHONE,
)

@Composable
internal fun DanaApp(
    model: DanaViewModel, chat: ChatViewModel, media: MediaViewModel,
    account: () -> AccountViewModel, art: () -> ArtViewModel, phone: () -> PhoneViewModel, managed: ManagedWorkspaceViewModel,
    openMessageReview: () -> Unit,
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val catalog by model.routerModels.collectAsStateWithLifecycle()
    val session = state.session
    val managedState by managed.state.collectAsStateWithLifecycle()
    val hosted = managed.configured
    val planner = managedState.catalog?.planner?.takeUnless { managedState.offline }
    var showLocal by rememberSaveable { mutableStateOf(false) }
    var afterLogin by rememberSaveable { mutableStateOf<DashboardTab?>(null) }
    val offline = (state.offline as? Reading.Ready)?.value != false
    val ime = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val secondary = rootTabs.none { it.first == session.tab }
    val dispatch: (DanaIntent) -> Unit = { intent ->
        if (intent is DanaIntent.Navigate && !session.busy) {
            if (session.tab != intent.tab) {
                when (session.tab) {
                    DashboardTab.CHAT -> { chat.background(); media.close(); if (hosted) managed.stop() }
                    DashboardTab.ACCOUNT -> { account().background(); afterLogin = null }
                    DashboardTab.ART -> if (hosted) managed.stop() else art().stop()
                    DashboardTab.PHONE -> phone().cancel()
                    else -> Unit
                }
            }
        }
        model.dispatch(intent)
    }
    val openSettings = { dispatch(DanaIntent.Navigate(DashboardTab.SETTINGS)) }
    val back = { dispatch(DanaIntent.Navigate(if (session.tab == DashboardTab.ACCOUNT && afterLogin != null) checkNotNull(afterLogin)
        else if (session.tab in setOf(DashboardTab.NOTES, DashboardTab.COMPARE, DashboardTab.ACCOUNT)) DashboardTab.SETTINGS else DashboardTab.CHAT)) }
    val allowNetwork = { dispatch(DanaIntent.SetOffline(false)) }
    val openLogin = {
        afterLogin = session.tab
        if (offline) allowNetwork() // Only from the explicitly online email button; no code is sent yet.
        dispatch(DanaIntent.Navigate(DashboardTab.ACCOUNT))
    }
    BackHandler(enabled = session.tab != DashboardTab.CHAT && session.editor == null && !session.busy && !ime, onBack = back)
    Scaffold(
        containerColor = Obsidian,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { WorkspaceHeader(session.tab, secondary, !session.busy, back, openSettings) },
        bottomBar = {
            if (!ime && !secondary) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                rootTabs.forEach { (tab, mark) ->
                    val selected = session.tab == tab
                    NavigationBarItem(
                        selected = selected, enabled = !session.busy,
                        onClick = { dispatch(DanaIntent.Navigate(tab)) },
                        icon = { StudioMark(mark, if (selected) Mint else Titanium) },
                        label = { Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(selectedTextColor = Mint, unselectedTextColor = Titanium,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
                NavigationBarItem(
                    selected = false, enabled = !session.busy, onClick = openMessageReview,
                    icon = { StudioMark(Mark.MESSAGE, Titanium) },
                    label = { Text(stringResource(R.string.tab_message), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = NavigationBarItemDefaults.colors(unselectedTextColor = Titanium),
                )
            }
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets), contentAlignment = Alignment.TopCenter) {
            key(session.tab) {
                if (session.tab == DashboardTab.CHAT) {
                    if (hosted && !showLocal) ManagedWorkspaceScreen(managed, managedState, ManagedKind.CHAT, openLogin, allowNetwork,
                        openLocal = { showLocal = true; managed.stop() }, modifier = Modifier.widthIn(max = 740.dp).imePadding())
                    else Column(Modifier.widthIn(max = 740.dp).imePadding().fillMaxSize()) {
                        if (hosted) TextButton(onClick = { chat.background(); media.close(); showLocal = false }) {
                            Text(stringResource(R.string.managed_open_online))
                        }
                        ChatScreen(chat, media, state, onSettings = openSettings, catalog = catalog, dispatch = dispatch,
                            onEnableNetwork = allowNetwork, modifier = Modifier.weight(1f), localOnly = hosted)
                    }
                } else if (session.tab == DashboardTab.ART) {
                    if (hosted) ManagedWorkspaceScreen(managed, managedState, ManagedKind.IMAGE, openLogin, allowNetwork,
                        modifier = Modifier.widthIn(max = 680.dp).imePadding())
                    else ArtScreen(art(), state, dispatch, Modifier.widthIn(max = 680.dp).imePadding())
                } else LazyColumn(
                    modifier = Modifier.widthIn(max = 680.dp).fillMaxSize().imePadding().testTag("workspace-list"),
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
                        DashboardTab.CHAT, DashboardTab.ART -> Unit
                        DashboardTab.COMPARE -> comparison(state, dispatch)
                        DashboardTab.NOTES -> notes(state, dispatch)
                        DashboardTab.SETTINGS -> if (hosted) managedSettings(state, dispatch) else settings(state, dispatch)
                        DashboardTab.PHONE -> item {
                            PhoneScreen(phone(), offline, planner, managedState.catalog?.dailyRemaining ?: 0,
                                login = openLogin.takeIf { hosted && (!managedState.resumed || managedState.catalogError == ManagedProblem.SIGN_IN) },
                                enableNetwork = allowNetwork.takeIf { hosted && managedState.resumed && managedState.offline },
                                refresh = managed::refresh)
                        }
                        DashboardTab.ACCOUNT -> item {
                            AccountScreen(account(), offline, !session.busy && state.offline is Reading.Ready, allowNetwork,
                                managed = hosted, onEntered = afterLogin?.let { tab -> { managed.refresh(); dispatch(DanaIntent.Navigate(tab)) } })
                        }
                    }
                }
            }
        }
    }
    if (session.tab == DashboardTab.PHONE) PhoneActionReview(phone(), planner?.takeIf { it.units <= (managedState.catalog?.dailyRemaining ?: 0) }, managed::refresh)
    session.editor?.let { editor ->
        key(editor) { DanaEditor(editor, session, offline, dispatch) }
    }
}

@Composable
private fun WorkspaceHeader(tab: DashboardTab, secondary: Boolean, enabled: Boolean, back: () -> Unit, settings: () -> Unit) {
    Surface(color = Obsidian) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 18.dp, vertical = 5.dp)
            .heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (secondary) {
                IconButton(onClick = back, enabled = enabled, modifier = Modifier.size(48.dp)) {
                    StudioMark(Mark.BACK, Ice, stringResource(R.string.studio_back))
                }
                Text(stringResource(if (tab == DashboardTab.SETTINGS) R.string.workspace_settings else tab.label),
                    Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            } else {
                Icon(painterResource(R.drawable.ic_dana), null, Modifier.size(34.dp), tint = Color.Unspecified)
                Text(stringResource(R.string.app_wordmark), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold, textDirection = TextDirection.Ltr), textAlign = TextAlign.Right, maxLines = 1)
                IconButton(onClick = settings, enabled = enabled, modifier = Modifier.size(48.dp)) {
                    StudioMark(Mark.SETTINGS, Titanium, stringResource(R.string.workspace_settings))
                }
            }
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

private fun LazyListScope.managedSettings(state: DashboardState, dispatch: (DanaIntent) -> Unit) {
    item { SectionIntro(R.string.workspace_settings, R.string.managed_email_help) }
    item {
        GlassCard(Modifier.fillMaxWidth()) {
            Button(onClick = { dispatch(DanaIntent.Navigate(DashboardTab.ACCOUNT)) }) { Text(stringResource(R.string.account_title)) }
            val offline = (state.offline as? Reading.Ready)?.value
            if (offline != null) ToggleLine(R.string.offline_title, offline, !state.session.busy) { dispatch(DanaIntent.SetOffline(it)) }
            TextButton(onClick = { dispatch(DanaIntent.Navigate(DashboardTab.NOTES)) }) { Text(stringResource(R.string.tab_notes)) }
        }
    }
}

private fun LazyListScope.settings(state: DashboardState, dispatch: (DanaIntent) -> Unit) {
    val enabled = !state.session.busy && state.profiles is Reading.Ready
    val preferred = (state.profiles as? Reading.Ready)?.value?.firstOrNull { it.id == state.preferredEngine }
    item { SectionIntro(R.string.workspace_connection, R.string.workspace_connection_help) }
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
                Button(onClick = { dispatch(DanaIntent.OpenQuick(ai.dana.domain.QuickService.AVALAI)) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.quick_title))
                }
            } else OutlinedButton(onClick = { dispatch(DanaIntent.OpenQuick(ai.dana.domain.QuickService.AVALAI)) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
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
            if (profiles.value.isNotEmpty()) item {
                var showProfiles by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                GlassCard(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { showProfiles = !showProfiles }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.workspace_saved_count, faDigits(profiles.value.size)))
                    }
                    if (showProfiles) profiles.value.forEach { profile -> key(profile.id) { ProfileCard(profile, state.session.busy, dispatch) } }
                }
            }
        }
    }
    item {
        GlassCard(Modifier.fillMaxWidth()) {
            var expanded by remember { mutableStateOf(false) }
            TextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.studio_advanced_hide else R.string.studio_advanced)) }
            if (expanded) {
                listOf(DashboardTab.NOTES, DashboardTab.COMPARE, DashboardTab.ACCOUNT).forEach { tab ->
                    TextButton(onClick = { dispatch(DanaIntent.Navigate(tab)) }, enabled = !state.session.busy) { Text(stringResource(tab.label)) }
                }
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
        if (profile.config.acceptsChat) OutlinedButton(onClick = { dispatch(DanaIntent.TestProfile(profile)) }, enabled = !busy) {
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
        Text(stringResource(message), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
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
