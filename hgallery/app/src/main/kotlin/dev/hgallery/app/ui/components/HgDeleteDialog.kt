package dev.hgallery.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.hgallery.app.R
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.core.DeletePath

/**
 * Confirmation before anything is destroyed.
 *
 * The body text is chosen by [path], and that is the whole point of the
 * component: the app may only promise "this can be restored" on Android 11+,
 * where the items really do go to the system trash. On older platforms the same
 * dialog has to say the deletion is permanent. A single generic sentence would be
 * a lie on one of the two.
 */
@Composable
fun HgDeleteDialog(
    count: Int,
    path: DeletePath,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = hgPalette
    // Plural resources rather than concatenation: "Delete 1 items?" is the kind
    // of wording that was actually shipped in the first build of this dialog and
    // caught by reading its own screenshot.
    val body = when (path) {
        DeletePath.SYSTEM_TRASH -> pluralStringResource(R.plurals.delete_trash_body, count, count)
        // Below API 30 there is no trash on the device at all, so the dialog must
        // not mention restoring. This is the sentence that keeps the promise
        // truthful on an older phone.
        DeletePath.DIRECT_DELETE -> pluralStringResource(R.plurals.delete_permanent_body, count, count)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.page,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        title = {
            Text(
                text = pluralStringResource(R.plurals.delete_selected_title, count, count),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = palette.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = palette.accent)
            }
        },
    )
}
