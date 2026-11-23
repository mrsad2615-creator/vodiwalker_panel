package dev.hgallery.app.ui.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.ui.components.HgScreenTitle
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.components.HgSectionHeader
import dev.hgallery.app.ui.components.HgToolbar
import dev.hgallery.app.ui.components.MediaThumbnail
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.theme.AlbumTitle
import dev.hgallery.app.ui.theme.CountLabel
import dev.hgallery.app.ui.theme.HgMetrics
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.core.Album
import dev.hgallery.core.AlbumBuilder
import dev.hgallery.core.AlbumSort
import dev.hgallery.core.MediaItem
import androidx.compose.ui.res.stringResource
import dev.hgallery.app.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The Albums tab, built to reference R2.
 *
 * Observed structure: a "smart" row of three synthetic albums (All photos,
 * Videos, Camera) whose covers are mini 4-cell mosaics, then "MORE ALBUMS" with
 * an "ADD ALBUM" link, then the device folders. Name and count sit *under* the
 * tile, left aligned — not overlaid on the image.
 */
@Composable
fun AlbumsScreen(
    container: AppContainer,
    onOpenAlbum: (Album) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = hgPalette
    val items by container.mediaStore.items.collectAsStateWithLifecycle()

    val albums = remember(items) {
        AlbumBuilder.visible(
            AlbumBuilder.sort(AlbumBuilder.fromItems(items), AlbumSort.DATE),
        )
    }
    val smart = albums.filter { it.isSynthetic }
    val folders = albums.filterNot { it.isSynthetic }

    Column(modifier = modifier.fillMaxSize().background(palette.page)) {
        Spacer(Modifier.statusBarsPadding())
        // Settings is behind the overflow slot. It was left empty while it had no
        // destination; it has one now, so the slot is used.
        HgToolbar(
            onSearch = onSearch,
            onMore = onOpenSettings,
            modifier = Modifier.padding(end = 6.dp),
        )
        HgScreenTitle(title = stringResource(R.string.tab_albums))
        Spacer(Modifier.height(18.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(HgMetrics.AlbumColumns),
            horizontalArrangement = Arrangement.spacedBy(HgMetrics.AlbumGap),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(
                start = HgMetrics.Gutter,
                end = HgMetrics.Gutter,
                bottom = 28.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(smart, key = { it.id }) { album ->
                AlbumTile(album, items, container) { onOpenAlbum(album) }
            }

            if (folders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HgSectionHeader(
                        label = stringResource(R.string.section_more_albums),
                        // Custom albums are not built. The reference has this link,
                        // so it stays drawn, but greyed and announced as unavailable
                        // rather than accent-blue and inert.
                        action = stringResource(R.string.action_add_album),
                        onAction = {},
                        enabled = false,
                    )
                }
                items(folders, key = { it.id }) { album ->
                    AlbumTile(album, items, container) { onOpenAlbum(album) }
                }
            }
        }
    }
}

/**
 * A mosaic of up to four covers, which is how the reference fills a smart
 * album's tile. A single cover is used for ordinary folders.
 */
@Composable
private fun AlbumTile(
    album: Album,
    allItems: List<MediaItem>,
    container: AppContainer,
    onClick: () -> Unit,
) {
    val palette = hgPalette
    val mosaic = remember(album.id, allItems) {
        if (!album.isSynthetic) emptyList()
        else AlbumBuilder.itemsOf(allItems, album.id)
            .sortedByDescending { it.dateTakenMs }
            .take(4)
    }

    Column(
        modifier = Modifier
            .testTag(HgTestTags.album(album.name))
            // The tile's own name is its accessible label. Without it the tile is
            // an unlabelled clickable box and the only text inside it is a count,
            // which tells a screen-reader user nothing about which album it opens.
            .semantics(mergeDescendants = true) {
                contentDescription = "${album.name}, ${album.count}"
            }
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(HgMetrics.AlbumCorner))
                .background(palette.chipBackground),
        ) {
            when {
                mosaic.size >= 2 -> {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.weight(1f)) {
                            MosaicCell(mosaic.getOrNull(0), container, Modifier.weight(1f))
                            Spacer(Modifier.width(1.5.dp))
                            MosaicCell(mosaic.getOrNull(1), container, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(1.5.dp))
                        Row(Modifier.weight(1f)) {
                            MosaicCell(mosaic.getOrNull(2), container, Modifier.weight(1f))
                            Spacer(Modifier.width(1.5.dp))
                            MosaicCell(mosaic.getOrNull(3), container, Modifier.weight(1f))
                        }
                    }
                }
                mosaic.size == 1 -> MediaThumbnail(
                    item = mosaic.first(),
                    store = container.thumbnails,
                    modifier = Modifier.fillMaxSize(),
                )
                album.coverUri != null -> {
                    val cover = allItems.firstOrNull { it.uri == album.coverUri }
                    if (cover != null) {
                        MediaThumbnail(item = cover, store = container.thumbnails, modifier = Modifier.fillMaxSize())
                    } else {
                        EmptyFolder()
                    }
                }
                else -> EmptyFolder()
            }
        }

        Spacer(Modifier.height(8.dp))
        // The tile is 4:3 and three-up, which leaves ~100 dp of width: the
        // reference's 14/12 sp fits comfortably and keeps name and count on
        // their own two lines like the original.
        Text(
            text = album.name,
            style = AlbumTitle,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = album.count.toString(),
            style = CountLabel,
            color = palette.textSecondary,
        )
    }
}

@Composable
private fun MosaicCell(item: MediaItem?, container: AppContainer, modifier: Modifier) {
    if (item == null) {
        Box(modifier.background(hgPalette.chipBackground))
    } else {
        MediaThumbnail(item = item, store = container.thumbnails, modifier = modifier, showVideoBadge = false)
    }
}

@Composable
private fun EmptyFolder() {
    val palette = hgPalette
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = HgIcons.Folder,
            contentDescription = null,
            tint = palette.textTertiary,
            modifier = Modifier.size(28.dp),
        )
    }
}
