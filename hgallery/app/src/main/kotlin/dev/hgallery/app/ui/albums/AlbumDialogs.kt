package dev.hgallery.app.ui.albums

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.hgallery.app.R
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.core.AlbumRules
import dev.hgallery.core.CustomAlbum

/**
 * The name dialog, used for both a new album and a rename.
 *
 * One dialog for both jobs: the field, the check and the two buttons are identical, and
 * two copies would drift the moment one of them gained a rule. The differences are the
 * title and the confirm label.
 *
 * The confirm button is disabled while the typed name is not usable, so the user cannot
 * press a control that would do nothing. The reason is stated under the field rather than
 * only implied by the disabled state.
 */
@Composable
fun AlbumNameDialog(
    title: String,
    initial: String,
    existing: List<CustomAlbum>,
    editing: CustomAlbum?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var typed by remember { mutableStateOf(initial) }
    val cleaned = AlbumRules.clean(typed)
    val empty = !AlbumRules.isValidName(cleaned)
    val duplicate = !empty && AlbumRules.isTaken(cleaned, existing, exceptId = editing?.id)
    val usable = !empty && !duplicate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.album_name_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (usable) onConfirm(cleaned) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(HgTestTags.ALBUM_NAME_FIELD),
                )
                if (empty || duplicate) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            if (duplicate) R.string.album_name_taken else R.string.album_name_empty,
                        ),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = dev.hgallery.app.ui.theme.hgPalette.danger,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(cleaned) },
                enabled = usable,
                modifier = Modifier.testTag(HgTestTags.ALBUM_NAME_CONFIRM),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * The confirmation for deleting a custom album.
 *
 * The wording states what is *not* deleted, because deleting an album here removes the
 * collection, never the photographs. A dialog that said only "Delete?" would leave the
 * user expecting the files to go with it.
 */
@Composable
fun DeleteAlbumDialog(album: CustomAlbum, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.album_delete_title, album.name)) },
        text = { Text(stringResource(R.string.album_delete_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(HgTestTags.ALBUM_DELETE_CONFIRM),
            ) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * The menu for one custom album: rename, privacy, delete.
 *
 * The privacy row states what the lock does. It is offered only when the device can
 * authenticate: a switch that turns on a gate that cannot be satisfied would lock the
 * album for its owner too.
 */
@Composable
fun AlbumMenuDialog(
    album: CustomAlbum,
    canProtect: Boolean,
    onRename: () -> Unit,
    onTogglePrivate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(album.name) },
        text = {
            Column {
                MenuItem(
                    label = stringResource(R.string.album_menu_rename_label),
                    enabled = true,
                    onClick = onRename,
                    tag = HgTestTags.ALBUM_MENU_RENAME,
                )
                MenuItem(
                    label = stringResource(
                        if (album.isPrivate) R.string.album_menu_unlock
                        else R.string.album_menu_lock,
                    ),
                    enabled = canProtect,
                    onClick = onTogglePrivate,
                    tag = HgTestTags.ALBUM_MENU_PRIVATE,
                )
                Text(
                    // Said every time, not once: this is the sentence that keeps the
                    // lock from being mistaken for encryption.
                    text = stringResource(
                        if (canProtect) R.string.album_menu_lock_detail
                        else R.string.album_menu_lock_unavailable,
                    ),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = if (canProtect) {
                        dev.hgallery.app.ui.theme.hgPalette.textSecondary
                    } else {
                        dev.hgallery.app.ui.theme.hgPalette.danger
                    },
                )
                MenuItem(
                    label = stringResource(R.string.delete),
                    enabled = true,
                    onClick = onDelete,
                    tag = HgTestTags.ALBUM_MENU_DELETE,
                    destructive = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

/** One row of a dialog menu. A disabled row still announces itself as unavailable. */
@Composable
private fun MenuItem(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    destructive: Boolean = false,
) {
    val palette = dev.hgallery.app.ui.theme.hgPalette
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    ) {
        Text(
            text = if (enabled) label else "$label, unavailable",
            color = when {
                !enabled -> palette.textTertiary
                destructive -> palette.danger
                else -> palette.textPrimary
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
