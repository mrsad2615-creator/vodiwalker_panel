package dev.hgallery.app.ui.photos

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.widget.Toast
import dev.hgallery.app.ui.components.HgDeleteDialog
import kotlinx.coroutines.launch

/**
 * Everything the selection *does*, as one composable with no layout.
 *
 * Split from `PhotosScreen` so the screen itself contains only composition. This
 * composable owns the platform handshake:
 *
 *   app confirmation → (system trash dialog on API 30+) → store mutation → toast
 *
 * The two-dialog sequence is the part that is easy to get wrong: the app must not
 * apply the delete before the system returns, and a cancelled system dialog must
 * apply nothing at all. Both are consequences of the ordering here.
 */
@Composable
fun PhotosSelectionEffects(
    model: PhotosSelectionModel,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    suspend fun finish() {
        val outcome = model.applyDelete()
        Toast.makeText(context, outcome.message(context), Toast.LENGTH_SHORT).show()
        onDeleted()
    }

    val systemLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        // A cancelled system dialog applies nothing: the user said no.
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch { finish() }
        } else {
            model.cancelDelete()
        }
    }

    if (model.confirmingDelete) {
        HgDeleteDialog(
            count = model.selection.count,
            path = model.deletePath,
            onConfirm = {
                val sender = model.systemDeleteRequest()
                val launched = sender != null && runCatching {
                    systemLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }.isSuccess
                if (!launched) {
                    // No system dialog on this platform, or the sender was
                    // refused: the app's own confirmation is then the last word.
                    scope.launch { finish() }
                }
            },
            onDismiss = { model.cancelDelete() },
        )
    }
}

/**
 * Keeps the selection consistent with what still exists.
 *
 * Without this a deleted row stays in the selection and the toolbar offers an
 * action on something that is gone.
 */
@Composable
fun PruneSelection(model: PhotosSelectionModel, existing: Set<Long>) {
    LaunchedEffect(existing) { model.prune(existing) }
}
