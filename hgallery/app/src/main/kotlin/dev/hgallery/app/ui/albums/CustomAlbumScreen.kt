package dev.hgallery.app.ui.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hgallery.app.R
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.ui.components.HgIconButton
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.components.MediaThumbnail
import dev.hgallery.app.ui.components.mediaDescription
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.theme.HgMetrics
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.core.CustomAlbum
import dev.hgallery.core.MediaItem

/**
 * The contents of one custom album.
 *
 * The album is read by id from the store, not taken as a value. Its membership and its
 * name can both change while this screen is open, and a captured copy would show whatever
 * was true when the tile was tapped.
 *
 * Items are ordered newest first, the same as every other grid in the app. An item that
 * has been deleted since it was added is skipped rather than shown as a hole: the store
 * prunes on delete, and this is the second guard for the frame in which the prune has not
 * reached the flow yet.
 */
@Composable
fun CustomAlbumScreen(
    container: AppContainer,
    albumId: Long,
    items: List<MediaItem>,
    onBack: () -> Unit,
    onOpenItem: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = hgPalette
    val albums by container.customAlbums.albums.collectAsStateWithLifecycle(initialValue = emptyList())
    val album: CustomAlbum? = remember(albums, albumId) { albums.firstOrNull { it.id == albumId } }

    val contents = remember(album, items) {
        val byId = items.associateBy { it.id }
        album?.itemIds?.mapNotNull { byId[it] }
            ?.sortedByDescending { it.dateTakenMs }
            .orEmpty()
    }

    Column(modifier = modifier.fillMaxSize().background(palette.page)) {
        Spacer(Modifier.statusBarsPadding())

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HgMetrics.MinTouchTarget)
                .padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HgIconButton(
                icon = HgIcons.Back,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = album?.name ?: stringResource(R.string.section_my_albums),
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.photo_count,
                        contents.size,
                        contents.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
            }
            if (album?.isPrivate == true) {
                Text(
                    text = stringResource(R.string.private_row_title, 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (contents.isEmpty()) {
            dev.hgallery.app.ui.components.MessageState(
                title = stringResource(R.string.custom_album_empty),
                detail = stringResource(R.string.album_add_hint),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(HgMetrics.PhotosColumns),
                horizontalArrangement = Arrangement.spacedBy(HgMetrics.GridGap),
                verticalArrangement = Arrangement.spacedBy(HgMetrics.GridGap),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(HgTestTags.customAlbum(albumId)),
            ) {
                items(contents, key = { it.id }) { item ->
                    val label = mediaDescription(item)
                    MediaThumbnail(
                        item = item,
                        store = container.thumbnails,
                        describeImage = false,
                        modifier = Modifier
                            .testTag(HgTestTags.thumbnail(item.id))
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .semantics { contentDescription = label }
                            .clickable { onOpenItem(item.id) },
                    )
                }
            }
        }
    }
}
