package ai.dana.ui

import ai.dana.R
import ai.dana.media.MediaViewModel
import ai.dana.domain.NetworkStatus
import ai.dana.domain.ProviderNetwork
import ai.dana.domain.ProviderProfile
import ai.dana.domain.ProviderProtocol
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun DanaApp(model: DanaViewModel, chat: ChatViewModel, media: MediaViewModel) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val session = state.session
    val dispatch: (DanaIntent) -> Unit = { intent ->
        if (intent is DanaIntent.Navigate && intent.tab != DashboardTab.CHAT) { chat.background(); media.close() }
        model.dispatch(intent)
    }

    BackHandler(enabled = session.tab != DashboardTab.HOME && session.editor == null && !session.busy) {
        dispatch(DanaIntent.Navigate(DashboardTab.HOME))
    }
    Scaffold(
        containerColor = Obsidian,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF101522)) {
                DashboardTab.entries.forEach { tab ->
                    val selected = session.tab == tab
                    NavigationBarItem(
                        selected = selected,
                        enabled = !session.busy,
                        onClick = { dispatch(DanaIntent.Navigate(tab)) },
                        icon = { TabGlyph(tab, if (selected) Ice else Titanium) },
                        label = { Text(stringResource(tab.label)) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFF253957)),
                    )
                }
            }
        },
    ) { insets ->
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF111A2D), Obsidian, Obsidian)))
                .padding(insets)
                .consumeWindowInsets(insets),
            contentAlignment = Alignment.TopCenter,
        ) {
            key(session.tab) {
                if (session.tab == DashboardTab.CHAT) {
                    ChatScreen(chat, media, state, onSettings = { dispatch(DanaIntent.Navigate(DashboardTab.SETTINGS)) },
                        modifier = Modifier.widthIn(max = 720.dp).imePadding())
                } else LazyColumn(
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item { DanaWordmark() }
                    if (session.editor == null) {
                        session.error?.let { message -> item { ReadError(message) } }
                    }
                    when (session.tab) {
                        DashboardTab.CHAT -> Unit // Chat owns its scroll and fixed composer.
                        DashboardTab.HOME -> home(state, dispatch)
                        DashboardTab.COMPARE -> comparison(state, dispatch)
                        DashboardTab.NOTES -> notes(state, dispatch)
                        DashboardTab.SETTINGS -> settings(state, dispatch)
                    }
                }
            }
        }
    }
    session.editor?.let { editor ->
        key(editor) { DanaEditor(editor, session, (state.offline as? Reading.Ready)?.value != false, dispatch) }
    }
}

@Composable
private fun DanaWordmark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(R.drawable.ic_dana), null, Modifier.size(45.dp), tint = Color.Unspecified)
        Column {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, color = Ice)
            Text(
                stringResource(R.string.app_wordmark),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.8.sp, textDirection = TextDirection.Ltr),
                color = Titanium,
            )
        }
    }
}

private fun LazyListScope.home(state: DashboardState, dispatch: (DanaIntent) -> Unit) {
    val offline = (state.offline as? Reading.Ready)?.value != false
    val connected = state.network == NetworkStatus.INTERNET_VALIDATED
    item {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LiquidOrb(animated = connected && !offline)
            Text(stringResource(R.string.hub_local), style = MaterialTheme.typography.labelMedium, color = Ice)
            Text(
                stringResource(R.string.hub_heading),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.hub_description),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = Titanium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { dispatch(DanaIntent.Navigate(DashboardTab.CHAT)) }, enabled = !state.session.busy) {
                    Text(stringResource(R.string.hub_begin_chat))
                }
                OutlinedButton(onClick = { dispatch(DanaIntent.Navigate(DashboardTab.COMPARE)) }, enabled = !state.session.busy) {
                    Text(stringResource(R.string.tab_compare))
                }
            }
        }
    }
    item {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.studio_quick_tasks), style = MaterialTheme.typography.titleMedium)
            TaskChoices(null, !state.session.busy) {
                dispatch(DanaIntent.ChooseTask(it, openWorkspace = true))
            }
        }
    }
    item {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.status_heading), style = MaterialTheme.typography.titleMedium)
            NetworkLine(state.network)
            if (offline) {
                Text(stringResource(R.string.offline_active), style = MaterialTheme.typography.labelMedium, color = Ice)
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

private fun LazyListScope.settings(state: DashboardState, dispatch: (DanaIntent) -> Unit) {
    val busy = state.session.busy
    item {
        GlassCard(Modifier.fillMaxWidth()) {
            when (val offline = state.offline) {
                Reading.Loading -> Loading()
                is Reading.Failed -> ReadError(offline.message) { dispatch(DanaIntent.Reload) }
                is Reading.Ready -> ToggleLine(R.string.offline_title, offline.value, !busy) {
                    dispatch(DanaIntent.SetOffline(it))
                }
            }
            Text(stringResource(R.string.offline_description), style = MaterialTheme.typography.bodyMedium, color = Titanium)
            NetworkLine(state.network)
            Text(stringResource(R.string.status_disclaimer), style = MaterialTheme.typography.bodySmall, color = Titanium)
        }
    }
    item {
        SectionIntro(R.string.engines_title, R.string.engines_description)
    }
    item {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.engine_presets), style = MaterialTheme.typography.titleMedium, color = Ice)
            val enabled = !busy && state.profiles is Reading.Ready
            Button(
                onClick = { dispatch(DanaIntent.EditProvider(null, ProviderProtocol.GEMINI_NATIVE)) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.engine_add_gemini)) }
            OutlinedButton(
                onClick = { dispatch(DanaIntent.EditProvider(null, ProviderProtocol.APPLE_MAC)) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.engine_add_apple)) }
            OutlinedButton(
                onClick = { dispatch(DanaIntent.EditProvider(null, lanPreset = true)) },
                enabled = enabled, modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.engine_add_lan)) }
            TextButton(
                onClick = { dispatch(DanaIntent.EditProvider(null)) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.engine_add_custom)) }
        }
    }
    when (val profiles = state.profiles) {
        Reading.Loading -> item { Loading() }
        is Reading.Failed -> item { ReadError(profiles.message) { dispatch(DanaIntent.Reload) } }
        is Reading.Ready -> {
            if (profiles.value.isEmpty()) item { EmptyCard(R.string.engines_empty) }
            items(profiles.value, key = { it.id }) { profile ->
                ProfileCard(profile, busy, dispatch)
            }
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
        val network = if (profile.config.network == ProviderNetwork.LOCAL_NETWORK) R.string.engine_lan else R.string.engine_internet
        Text(stringResource(network), style = MaterialTheme.typography.bodySmall, color = Titanium)
        Text(
            stringResource(if (profile.hasCredential) R.string.engine_has_key else R.string.engine_no_key),
            style = MaterialTheme.typography.labelMedium,
            color = if (profile.hasCredential) Mint else Titanium,
        )
        RecordActions(
            enabled = !busy,
            onEdit = { dispatch(DanaIntent.EditProvider(profile)) },
            onDelete = { dispatch(DanaIntent.RequestProviderDeletion(profile.id)) },
        )
    }
}

internal fun protocolLabel(protocol: ProviderProtocol): Int = when (protocol) {
    ProviderProtocol.OPENAI_COMPATIBLE -> R.string.engine_openai
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
private fun SectionIntro(title: Int, body: Int) {
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
private fun Loading() {
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

@Composable
private fun LiquidOrb(animated: Boolean) {
    val shimmer = if (animated) {
        rememberInfiniteTransition(label = "Dana orbit").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2_800), RepeatMode.Reverse),
            label = "Ambient light",
        )
    } else null
    Canvas(Modifier.size(208.dp)) {
        val pulse = shimmer?.value ?: 0.2f
        val r = size.minDimension * 0.335f
        val focus = center
        val rim = Color(0xFF87B9FF)
        drawCircle(Brush.radialGradient(listOf(rim.copy(alpha = 0.22f + pulse * 0.08f), Color.Transparent), focus, r * 1.48f), r * 1.48f)
        drawCircle(rim.copy(alpha = 0.23f), r * 1.21f, style = Stroke(1.dp.toPx()))
        drawCircle(rim.copy(alpha = 0.14f + pulse * 0.1f), r * 1.35f, style = Stroke(0.75.dp.toPx()))
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFF415D97), 0.42f to Color(0xFF252F5A), 0.76f to Color(0xFF121B38), 1f to Color(0xFF526C9D),
                center = focus + Offset(-r * 0.12f, -r * 0.16f), radius = r * 1.26f,
            ),
            radius = r,
        )
        drawCircle(
            brush = Brush.sweepGradient(listOf(rim.copy(alpha = 0.18f), Ice, rim.copy(alpha = 0.34f), Color.White, rim.copy(alpha = 0.18f)), focus),
            radius = r,
            style = Stroke(1.5.dp.toPx()),
        )
        val arm = r * (0.33f + 0.035f * pulse)
        val spark = Path().apply {
            moveTo(focus.x, focus.y - arm)
            quadraticTo(focus.x + arm * 0.15f, focus.y - arm * 0.15f, focus.x + arm, focus.y)
            quadraticTo(focus.x + arm * 0.15f, focus.y + arm * 0.15f, focus.x, focus.y + arm)
            quadraticTo(focus.x - arm * 0.15f, focus.y + arm * 0.15f, focus.x - arm, focus.y)
            quadraticTo(focus.x - arm * 0.15f, focus.y - arm * 0.15f, focus.x, focus.y - arm)
            close()
        }
        drawCircle(Brush.radialGradient(listOf(Ice.copy(alpha = 0.55f), Color.Transparent), focus, r * 0.67f), r * 0.67f)
        drawPath(spark, Ice)
    }
}

@Composable
private fun TabGlyph(tab: DashboardTab, tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val t = size.minDimension / 24f
        val stroke = 1.7f * t
        fun point(x: Float, y: Float) = Offset(x * t, y * t)
        fun line(x: Float, y: Float, ex: Float, ey: Float) = drawLine(tint, point(x, y), point(ex, ey), stroke, StrokeCap.Round)
        when (tab) {
            DashboardTab.HOME -> {
                val outline = Path().apply {
                    moveTo(3 * t, 11 * t)
                    lineTo(12 * t, 3 * t)
                    lineTo(21 * t, 11 * t)
                    lineTo(21 * t, 20 * t)
                    lineTo(3 * t, 20 * t)
                    close()
                }
                drawPath(outline, tint, style = Stroke(stroke))
                drawCircle(tint, 1.5f * t, point(12f, 12f))
            }
            DashboardTab.CHAT -> {
                drawRoundRect(tint, point(3f, 4f), Size(18f * t, 13f * t), CornerRadius(4 * t), style = Stroke(stroke))
                line(8f, 17f, 8f, 21f)
                line(8f, 21f, 12f, 17f)
                line(8f, 10f, 16f, 10f)
            }
            DashboardTab.COMPARE -> {
                drawRoundRect(tint, point(2f, 5f), Size(9f * t, 13f * t), CornerRadius(2 * t), style = Stroke(stroke))
                drawRoundRect(tint, point(13f, 5f), Size(9f * t, 13f * t), CornerRadius(2 * t), style = Stroke(stroke))
                line(4.5f, 10f, 8.5f, 10f)
                line(15.5f, 10f, 19.5f, 10f)
            }
            DashboardTab.NOTES -> {
                drawRoundRect(tint, point(5f, 3f), Size(14f * t, 18f * t), CornerRadius(2 * t), style = Stroke(stroke))
                line(8f, 9f, 16f, 9f)
                line(8f, 14f, 14f, 14f)
            }
            DashboardTab.SETTINGS -> {
                line(3f, 6f, 21f, 6f)
                line(3f, 12f, 21f, 12f)
                line(3f, 18f, 21f, 18f)
                drawCircle(tint, 2.5f * t, point(8f, 6f))
                drawCircle(tint, 2.5f * t, point(15f, 12f))
                drawCircle(tint, 2.5f * t, point(10f, 18f))
            }
        }
    }
}
