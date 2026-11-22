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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hgallery.app.R
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.ui.components.HgIconButton
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.components.MediaThumbnail
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.theme.HgMetrics
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.core.Album
import dev.hgallery.core.AlbumBuilder
import dev.hgallery.core.MediaItem

/**
 * The contents of one album.
 *
 * The reference opens an album into the same seamless 4-column grid it uses for
 * the timeline, headed by the album's own name. It does *not* jump straight into
 * the full-screen viewer when a tile is tapped — that would be a different
 * application's behaviour.
 */
@Composable
fun AlbumDetailScreen(
    container: AppContainer,
    album: Album,
    items: List<MediaItem>,
    onBack: () -> Unit,
    onOpenItem: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = hgPalette
    val contents = remember(album.id, items) {
        AlbumBuilder.itemsOf(emptyList(), items, album.id).sortedByDescending { it.dateTakenMs }
    }

    Column(modifier = modifier.fillMaxSize().background(palette.page)) {
        Spacer(Modifier.statusBarsPadding())

        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                    text = album.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.photo_count, contents.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(HgMetrics.PhotosColumns),
            horizontalArrangement = Arrangement.spacedBy(HgMetrics.GridGap),
            verticalArrangement = Arrangement.spacedBy(HgMetrics.GridGap),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(contents, key = { it.id }) { item ->
                MediaThumbnail(
                    item = item,
                    store = container.thumbnails,
                    modifier = Modifier
                        .testTag(HgTestTags.thumbnail(item.id))
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(3.dp))
                        .clickable { onOpenItem(item.id) },
                )
            }
        }
    }
}
