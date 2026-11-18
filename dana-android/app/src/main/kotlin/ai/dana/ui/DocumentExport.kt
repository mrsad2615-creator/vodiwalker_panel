package ai.dana.ui

import ai.dana.R
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun writeDocument(resolver: ContentResolver, uri: Uri, writer: (OutputStream) -> Unit) {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) throw IOException("Expected a document URI")
    resolver.openOutputStream(uri, "wt")?.use(writer) ?: throw IOException("Document could not be opened")
}

/** The payload never lives in a Bundle or cache. If it changed while the picker was open, do not write. */
@Composable
internal fun ExportButton(label: Int, identity: String, mime: String, fileName: String, writer: (OutputStream) -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var writing by remember { mutableStateOf(false) }
    var result by remember(identity) { mutableStateOf<Int?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { reply ->
        val requested = pending
        pending = null
        val uri = reply.data?.data ?: return@rememberLauncherForActivityResult
        if (reply.resultCode != Activity.RESULT_OK || requested != identity) return@rememberLauncherForActivityResult
        writing = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { writeDocument(resolver, uri, writer) }
                result = R.string.export_saved
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (_: Exception) {
                result = R.string.export_error
            } finally {
                writing = false
            }
        }
    }
    OutlinedButton(onClick = {
        pending = identity
        result = null
        val opened = runCatching {
            launcher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime)
                .putExtra(Intent.EXTRA_TITLE, fileName))
        }
        if (opened.isFailure) { pending = null; result = R.string.export_error }
    }, enabled = pending == null && !writing, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(if (writing) R.string.saving else label))
    }
    result?.let { if (it == R.string.export_error) ReadError(it) else Text(stringResource(it), color = Mint) }
}
