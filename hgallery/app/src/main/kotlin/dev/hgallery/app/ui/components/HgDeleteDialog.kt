package dev.hgallery.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    val body = when (path) {
        DeletePath.SYSTEM_TRASH -> stringResource(R.string.delete_trash_body)
        DeletePath.SYSTEM_DELETE_REQUEST -> stringResource(R.string.delete_system_body)
        DeletePath.DIRECT_DELETE -> stringResource(R.string.delete_permanent_body)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.page,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        title = {
            Text(
                text = stringResource(R.string.delete_selected_title, count),
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
