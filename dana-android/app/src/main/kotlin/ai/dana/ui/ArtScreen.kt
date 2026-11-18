package ai.dana.ui

import ai.dana.R
import ai.dana.domain.ArtModel
import ai.dana.domain.ArtShape
import ai.dana.domain.QuickService
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun ArtScreen(model: ArtViewModel, dashboard: DashboardState, dispatch: (DanaIntent) -> Unit) {
    val art by model.state.collectAsStateWithLifecycle()
    val profiles = (dashboard.profiles as? Reading.Ready)?.value.orEmpty()
    val offline = (dashboard.offline as? Reading.Ready)?.value != false
    val engines = remember(profiles, dashboard.preferredEngine) {
        ArtModel.entries.flatMap { m -> profiles.filter(m::matches).map { p -> m to p } }
            .sortedBy { (_, p) -> p.id != dashboard.preferredEngine }
    }
    val chosen = engines.firstOrNull { (m, p) -> m == art.model && p.id == art.engineId } ?: engines.firstOrNull()
    val newlyConnected = engines.firstOrNull { (_, p) -> p.id == dashboard.session.newImageProfile }
    LaunchedEffect(newlyConnected) {
        if (newlyConnected != null) model.select(newlyConnected.first, newlyConnected.second)
    }
    var menu by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionIntro(R.string.art_title, R.string.workspace_image_intro)
        if (art.preview != null) {
            val result = checkNotNull(art.preview)
            GlassCard(Modifier.fillMaxWidth()) {
                StudioPill(stringResource(R.string.art_ready))
                Image(result.bitmap.asImageBitmap(), contentDescription = stringResource(R.string.art_preview),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow), contentScale = ContentScale.Fit)
                Text(result.engine, style = MaterialTheme.typography.labelLarge)
                TechnicalText("${result.width} × ${result.height} · ${result.millis} ms")
                if (!art.running) {
                    ExportButton(R.string.art_save, result.id, result.image.mime, "mrsad-image.${result.image.extension}", result.image::writeTo)
                    TextButton(onClick = model::clearPreview) { Text(stringResource(R.string.art_clear)) }
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(art.prompt, model::prompt, Modifier.fillMaxWidth(), enabled = !art.running,
                label = { Text(stringResource(R.string.art_prompt)) },
                placeholder = { Text(stringResource(R.string.workspace_image_example)) }, minLines = 3, maxLines = 6)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ArtShape.entries.forEach { shape ->
                    FilterChip(selected = art.shape == shape, enabled = !art.running, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        onClick = { model.shape(shape) }, label = { Text(stringResource(when (shape) {
                            ArtShape.SQUARE -> R.string.art_square
                            ArtShape.LANDSCAPE -> R.string.art_landscape
                            ArtShape.PORTRAIT -> R.string.art_portrait
                        })) })
                }
            }
            if (chosen != null) {
                Column {
                    TextButton(onClick = { menu = true }, enabled = !art.running, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("${chosen.second.config.name} · ${chosen.first.title}", style = MaterialTheme.typography.labelLarge)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        engines.take(60).forEach { (m, p) ->
                            DropdownMenuItem(text = { Text("${p.config.name} · ${m.title}") }, onClick = { model.select(m, p); menu = false })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.art_connect)) }, onClick = {
                            menu = false
                            focus.clearFocus()
                            dispatch(DanaIntent.OpenQuick(QuickService.AVALAI, DashboardTab.ART))
                        })
                    }
                }
            }
            if (art.running) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.art_wait), color = Titanium)
                OutlinedButton(onClick = model::stop, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.compare_stop)) }
            } else if (chosen == null) {
                Text(stringResource(R.string.art_need_engine), color = Titanium, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { focus.clearFocus(); dispatch(DanaIntent.OpenQuick(QuickService.AVALAI, DashboardTab.ART)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = dashboard.profiles is Reading.Ready) {
                    Text(stringResource(R.string.art_connect))
                }
            } else if (offline) {
                Text(stringResource(R.string.art_offline), color = Titanium, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { focus.clearFocus(); dispatch(DanaIntent.SetOffline(false)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = dashboard.offline is Reading.Ready && !dashboard.session.busy) {
                    Text(stringResource(R.string.workspace_enable_ai))
                }
            } else {
                Text(stringResource(R.string.art_paid), style = MaterialTheme.typography.bodySmall, color = Amber)
                Button(onClick = { focus.clearFocus(); model.submit(chosen.first, chosen.second) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = art.prompt.isNotBlank()) {
                    Text(stringResource(R.string.art_generate))
                }
            }
            art.error?.let {
                ReadError(it)
                art.status?.let { status -> TechnicalText("HTTP $status") }
                if (art.status in setOf(401, 402, 403, 404, 429)) TextButton(onClick = {
                    dispatch(DanaIntent.OpenQuick(chosen?.first?.service ?: QuickService.AVALAI, DashboardTab.ART))
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.workspace_connect)) }
            }
        }
    }
    art.pending?.let { request ->
        AlertDialog(onDismissRequest = model::dismiss, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.art_allow_title)) }, text = {
                Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.art_allow_body))
                    Text(request.profile.config.name, style = MaterialTheme.typography.titleMedium)
                    TechnicalText(request.profile.config.baseUrl, maxLines = 3)
                    TechnicalText(request.model.id, maxLines = 2)
                }
            }, confirmButton = {
                Button(onClick = model::approve, enabled = !offline) { Text(stringResource(R.string.send_allow)) }
            }, dismissButton = {
                TextButton(onClick = model::dismiss) { Text(stringResource(R.string.cancel)) }
            })
    }
}
