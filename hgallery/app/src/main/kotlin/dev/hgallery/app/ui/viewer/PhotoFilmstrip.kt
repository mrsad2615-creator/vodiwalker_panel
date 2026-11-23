package dev.hgallery.app.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.hgallery.app.data.ThumbnailStore
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.components.MediaThumbnail
import dev.hgallery.app.ui.components.mediaDescription
import dev.hgallery.core.MediaItem

/**
 * The strip of thumbnails under the photograph in the viewer.
 *
 * It answers two questions at once: where is this photograph in the set, and what is next.
 * Swiping moves one item at a time, which is fine for two frames and tedious for forty;
 * the strip turns forty into one tap.
 *
 * The selected cell is full opacity and carries a border, the others are dimmed. Dimming
 * rather than tinting keeps the colours of the neighbours readable while still saying
 * which one is on screen — a coloured ring alone disappears against a bright frame.
 */
@Composable
fun PhotoFilmstrip(
    items: List<MediaItem>,
    selectedIndex: Int,
    store: ThumbnailStore,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.size < 2) return
    val listState = rememberLazyListState()

    // Keep the selection on screen. It changes from a swipe as well as from a tap, so the
    // strip has to follow the pager and not only its own clicks.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in items.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(vertical = 6.dp)
            .testTag(HgTestTags.PHOTO_FILMSTRIP),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val isSelected = index == selectedIndex
            val label = mediaDescription(item)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .alpha(if (isSelected) 1f else 0.55f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .semantics(mergeDescendants = true) {
                        contentDescription = label
                        this.selected = isSelected
                    }
                    .testTag(HgTestTags.filmstripThumb(item.id))
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                MediaThumbnail(
                    item = item,
                    store = store,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 6,
                    describeImage = false,
                    showVideoBadge = item.isVideo,
                )
            }
        }
    }
}
