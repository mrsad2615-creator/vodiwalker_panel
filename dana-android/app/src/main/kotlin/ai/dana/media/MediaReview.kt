package ai.dana.media

import ai.dana.R
import ai.dana.domain.VisionPhoto
import ai.dana.ui.Amber
import ai.dana.ui.CopyReply
import ai.dana.ui.Ice
import ai.dana.ui.ReadError
import ai.dana.ui.Titanium
import android.app.Activity
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun MediaReview(model: MediaViewModel, chatBusy: Boolean, useText: (String) -> Unit, usePhoto: (VisionPhoto) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    if (!state.open) return
    val ctx = LocalContext.current
    var consent by remember { mutableStateOf(false) }
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(model::uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap -> bitmap?.let(model::camera) }
    val grant = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val token = result.data
        if (result.resultCode == Activity.RESULT_OK && token != null) model.capture(token)
        else model.error(R.string.media_capture_denied)
    }
    val ready = !state.working && !state.capturing && !chatBusy
    AlertDialog(onDismissRequest = model::close,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.media_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.media_local_only), style = MaterialTheme.typography.bodySmall, color = Titanium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    TextButton(enabled = ready, onClick = {
                        runCatching { choose.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            .onFailure { model.error(R.string.media_load_error) }
                    }) { Text(stringResource(R.string.media_pick)) }
                    TextButton(enabled = ready, onClick = { runCatching { camera.launch(null) }.onFailure { model.error(R.string.media_camera_unavailable) } }) {
                        Text(stringResource(R.string.media_camera))
                    }
                    TextButton(enabled = ready, onClick = { consent = true }) { Text(stringResource(R.string.media_capture)) }
                }
                if (state.capturing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.media_capturing), color = Amber)
                    TextButton(model::cancelCapture) { Text(stringResource(R.string.cancel)) }
                } else if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { ReadError(it) }
                state.frame?.let { image ->
                    SelectCrop(image, state.crop, ready, model::crop)
                    Text(stringResource(R.string.media_crop_help), color = Titanium, style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { model.crop(CropBox.Full) }, enabled = ready) { Text(stringResource(R.string.media_whole)) }
                        TextButton(onClick = model::read, enabled = ready) { Text(stringResource(R.string.media_ocr)) }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        OcrLanguage.entries.forEach { lang ->
                            FilterChip(selected = state.language == lang, onClick = { model.language(lang) }, enabled = ready,
                                label = { Text(stringResource(when (lang) {
                                    OcrLanguage.MIXED -> R.string.media_lang_both
                                    OcrLanguage.PERSIAN -> R.string.media_lang_fa
                                    OcrLanguage.ENGLISH -> R.string.media_lang_en
                                })) })
                        }
                    }
                    if (state.recognized.isNotEmpty()) {
                        OutlinedTextField(state.recognized, model::recognized, Modifier.fillMaxWidth(), enabled = ready,
                            label = { Text(stringResource(R.string.media_extracted)) }, maxLines = 6)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            CopyReply(state.recognized)
                            TextButton(enabled = ready && state.recognized.isNotBlank(), onClick = { useText(state.recognized); model.close() }) {
                                Text(stringResource(R.string.media_use_text))
                            }
                        }
                    }
                }
            }
        }, dismissButton = { TextButton(model::close) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { TextButton(enabled = ready && state.frame != null, onClick = { model.attach(usePhoto) }) { Text(stringResource(R.string.media_attach)) } })
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text(stringResource(R.string.media_capture)) },
        text = { Text(stringResource(R.string.media_capture_explain)) },
        dismissButton = { TextButton({ consent = false }) { Text(stringResource(R.string.cancel)) } },
        confirmButton = {
            TextButton({
                consent = false
                try {
                    val manager = checkNotNull(ctx.getSystemService(MediaProjectionManager::class.java))
                    grant.launch(manager.createScreenCaptureIntent())
                } catch (_: RuntimeException) { model.error(R.string.media_capture_error) }
            }) { Text(stringResource(R.string.media_capture_request)) }
        })
}

@Composable
private fun SelectCrop(frame: MemoryFrame, selected: CropBox, enabled: Boolean, onCrop: (CropBox) -> Unit) {
    val photo = remember(frame) { frame.decode().asImageBitmap() }
    var selection by remember(frame) { mutableStateOf<CropBox?>(null) }
    var anchor by remember(frame) { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxWidth().heightIn(max = 260.dp).aspectRatio(frame.width.toFloat() / frame.height)
        .background(Color.Black.copy(alpha = 0.4f))) {
        Image(photo, contentDescription = stringResource(R.string.media_preview), modifier = Modifier.matchParentSize(), contentScale = ContentScale.Fit)
        Canvas(Modifier.matchParentSize().pointerInput(frame, enabled) {
            if (!enabled) return@pointerInput
            fun norm(position: Offset): Offset {
                val fit = inside(frame, size.width.toFloat(), size.height.toFloat())
                return Offset(((position.x - fit.left) / fit.width).coerceIn(0f, 1f), ((position.y - fit.top) / fit.height).coerceIn(0f, 1f))
            }
            detectDragGestures(
                onDragStart = { anchor = norm(it); selection = null },
                onDragEnd = { selection?.takeIf { it.usable }?.let(onCrop); selection = null },
                onDragCancel = { selection = null },
                onDrag = { change, _ ->
                    change.consume()
                    val p = norm(change.position)
                    selection = CropBox(min(anchor.x, p.x), min(anchor.y, p.y), max(anchor.x, p.x), max(anchor.y, p.y))
                },
            )
        }) {
            val rect = inside(frame, size.width, size.height)
            val chosen = selection ?: selected
            if (chosen != CropBox.Full) {
                val start = Offset(rect.left + chosen.left * rect.width, rect.top + chosen.top * rect.height)
                val shape = Size((chosen.right - chosen.left) * rect.width, (chosen.bottom - chosen.top) * rect.height)
                drawRect(Ice.copy(alpha = 0.14f), start, shape)
                drawRect(Ice, start, shape, style = Stroke(2.dp.toPx()))
            }
        }
    }
}

private fun inside(image: MemoryFrame, w: Float, h: Float): Rect {
    val ratio = min(w / image.width, h / image.height)
    val width = image.width * ratio
    val height = image.height * ratio
    return Rect((w - width) / 2f, (h - height) / 2f, (w + width) / 2f, (h + height) / 2f)
}
