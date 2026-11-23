package dev.hgallery.app.ui.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hgallery.app.R
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.core.CustomAlbum

/**
 * "Add to album", from a running selection.
 *
 * The sheet lists the albums that already exist and offers a new one at the bottom, which
 * is the same shape a user meets in every gallery. Picking an album adds every selected
 * item to it and leaves selection mode: the action is finished, and staying in selection
 * would invite a second add to the same album by accident.
 *
 * [onCreate] reports success so the sheet can keep itself open with the typed name still
 * there when the name is refused, instead of closing as though the album existed.
 */
@Composable
fun AddToAlbumSheet(
    albums: List<CustomAlbum>,
    onDismiss: () -> Unit,
    onPick: (CustomAlbum) -> Unit,
    onCreate: (String, (Boolean) -> Unit) -> Unit,
) {
    val palette = hgPalette
    var creating by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }

    if (creating) {
        AlbumNameDialog(
            title = stringResource(R.string.album_new_title),
            initial = "",
            existing = albums,
            editing = null,
            onDismiss = { creating = false },
            onConfirm = { name ->
                onCreate(name) { ok ->
                    if (ok) creating = false else failure = true
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_album)) },
        text = {
            Column {
                if (albums.isEmpty()) {
                    Text(
                        text = stringResource(R.string.add_to_album_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(albums, key = { it.id }) { album ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable { onPick(album) }
                                    .padding(vertical = 10.dp)
                                    .testTag(HgTestTags.addToAlbumRow(album.id)),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = album.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = album.count.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary,
                                )
                            }
                        }
                    }
                }
                if (failure) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.album_name_taken),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.danger,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { failure = false; creating = true },
                modifier = Modifier.testTag(HgTestTags.SELECTION_NEW_ALBUM),
            ) {
                Text(stringResource(R.string.album_new_title))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
