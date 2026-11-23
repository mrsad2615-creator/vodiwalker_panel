package dev.hgallery.app.ui.video

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hgallery.app.R
import dev.hgallery.app.data.ThumbnailStore
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.components.MediaThumbnail
import dev.hgallery.core.MediaItem
import dev.hgallery.core.VideoQueue

/**
 * The video filmstrip.
 *
 * A horizontal row of every video, under the player. Tapping a cell plays that video
 * without leaving the player, and the row scrolls itself so the selected cell is always
 * visible — including after the queue advances on its own.
 *
 * ## Why this exists
 *
 * Without it, watching the next video means closing the player, finding the next tile in
 * a grid, and opening it. Every reference gallery keeps the strip on screen for exactly
 * that reason.
 *
 * ## Two modes
 *
 *  - **Compact:** 64 dp cells, one row. It costs the picture almost nothing.
 *  - **Expanded:** 96 dp cells with a name under each. Tapped from the handle, for
 *    choosing between clips that look alike.
 *
 * The mode is a control, not a hidden state, and it is announced with its action so a
 * screen-reader user can change it.
 */
@Composable
fun VideoFilmstrip(
    queue: List<MediaItem>,
    selectedId: Long,
    store: ThumbnailStore,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (queue.isEmpty()) return

    val listState = rememberLazyListState()
    val cellSize = if (expanded) 96.dp else 64.dp

    // Auto-scroll to the selection. Keyed on the id, so it runs when the queue advances and
    // also on the first frame, when the player may open in the middle of the queue.
    LaunchedEffect(selectedId, expanded) {
        val index = VideoQueue.indexOf(queue, selectedId)
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(FilmstripScrim)
            .padding(top = 6.dp, bottom = 4.dp)
            .testTag(HgTestTags.FILMSTRIP),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = filmstripCount(queue.size),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.weight(1f),
            )
            FilmstripHandle(expanded = expanded, onClick = onToggleExpanded)
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (expanded) cellSize + 26.dp else cellSize + 4.dp),
        ) {
            items(queue, key = { it.id }) { video ->
                FilmstripCell(
                    video = video,
                    selected = video.id == selectedId,
                    size = cellSize,
                    expanded = expanded,
                    store = store,
                    onClick = { onSelect(video.id) },
                )
            }
        }
    }
}

/** One cell: the frame, a selected outline, and the length. */
@Composable
private fun FilmstripCell(
    video: MediaItem,
    selected: Boolean,
    size: androidx.compose.ui.unit.Dp,
    expanded: Boolean,
    store: ThumbnailStore,
    onClick: () -> Unit,
) {
    // The outline is an animated width rather than a colour change alone: colour alone is
    // invisible to a user who cannot distinguish the two, and the width change reads at a
    // glance even on a busy frame.
    val border by animateDpAsState(
        targetValue = if (selected) 2.dp else 0.dp,
        animationSpec = tween(160),
        label = "filmstrip-border",
    )
    val fade by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.0f),
        animationSpec = tween(160),
        label = "filmstrip-fade",
    )
    val label = remember(video.id) {
        val seconds = (video.durationMs / 1000).coerceAtLeast(0)
        "${video.displayName}, ${seconds}s"
    }

    Column(
        modifier = Modifier
            .width(size)
            .semantics(mergeDescendants = true) {
                contentDescription = if (selected) "$label, playing" else label
            }
            .clickable(onClick = onClick)
            .testTag(HgTestTags.filmstripCell(video.id)),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            MediaThumbnail(
                item = video,
                store = store,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 8,
                // The cell supplies its own label, so the thumbnail must stay quiet.
                describeImage = false,
                showVideoBadge = false,
            )
            // Selected outline, drawn inside the cell so it cannot be clipped.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Transparent)
                    .then(
                        if (border > 0.dp) {
                            Modifier.border(border, fade, RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        },
                    ),
            )
            Text(
                text = VideoQueue.badge(video),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
        if (expanded) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = video.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The small grab handle that opens and closes the expanded strip. */
@Composable
private fun FilmstripHandle(expanded: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (expanded) "Hide video names" else "Show video names"
            }
            .testTag(HgTestTags.FILMSTRIP_HANDLE),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(width = 4.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (it == 1) Color.White else Color.White.copy(alpha = 0.45f),
                        ),
                )
            }
        }
    }
}

/** "12 videos", from resources so a language with other plural rules reads correctly. */
@Composable
private fun filmstripCount(count: Int): String =
    pluralStringResource(R.plurals.filmstrip_count, count, count)

/** A scrim, not a solid bar: the picture must still be visible behind the strip. */
private val FilmstripScrim = Color(0x99000000)

/** Height reserved by the player for each mode, in one place. */
val FilmstripCompactHeight = 74.dp
val FilmstripExpandedHeight = 130.dp

/** The height a caller should reserve for the current mode. */
fun filmstripHeight(expanded: Boolean): androidx.compose.ui.unit.Dp =
    if (expanded) FilmstripExpandedHeight else FilmstripCompactHeight
