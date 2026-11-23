package dev.hgallery.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.hgallery.app.R
import dev.hgallery.app.ui.components.HgChip
import dev.hgallery.app.ui.components.HgRemovableChip
import dev.hgallery.app.ui.theme.HgMetrics
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.core.Album
import dev.hgallery.core.DateBucket
import dev.hgallery.core.SearchChips
import dev.hgallery.core.SizeBucket

/**
 * The chip rows under the search field.
 *
 * Two rows, not one. The first answers "what kind of file", the second "which
 * album"; a single horizontally scrolling row would hide the second question off
 * the right edge of a phone, which is the screen the app is for. Only the album
 * row scrolls, because only it is unbounded.
 *
 * Per axis, one choice: pressing a lit chip clears the axis, and picking the other
 * value of an either/or axis replaces rather than adds. `SearchChips` stores the
 * result, so a contradictory query cannot be built here.
 */
@Composable
fun SearchChipRows(
    chips: SearchChips,
    albums: List<Album>,
    onChange: (SearchChips) -> Unit,
) {
    Column {
        // Each row says what question it answers. Without the labels the two rows
        // are visually identical runs of grey pills, and nothing on the screen says
        // why pressing one chip replaces the chip beside it while pressing another
        // adds to it: the first row is one choice per axis, the second is albums.
        RowLabel(stringResource(R.string.search_filters_label))
        KindChips(chips = chips, onChange = onChange)

        if (albums.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            RowLabel(stringResource(R.string.search_albums_label))
            AlbumChips(chips = chips, albums = albums, onChange = onChange)
        }

        Spacer(Modifier.height(4.dp))
    }
}

/** A small overline that names what the row beneath it filters on. */
@Composable
private fun RowLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = hgPalette.textTertiary,
        modifier = Modifier.padding(start = HgMetrics.Gutter, bottom = 2.dp),
    )
}

/**
 * The first row: kind, favourites, size and recency.
 *
 * Each chip toggles itself off, and the two kind chips are one either/or axis, so
 * choosing "Videos" while "Photos" is lit replaces rather than adds. That rule is
 * stated here once instead of in seven click handlers.
 */
@Composable
private fun KindChips(chips: SearchChips, onChange: (SearchChips) -> Unit) {
    // Resolved here: the semantics block below is not a composable scope, so it
    // cannot call stringResource itself.
    val scrollHint = stringResource(R.string.search_filters_scrollable)
    LazyRow(
        contentPadding = PaddingValues(horizontal = HgMetrics.Gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics {
            // A row of pills clipped at the right edge looks finished. Saying it
            // scrolls is the difference between a user finding "Large" and
            // concluding the app has no size filter at all.
            contentDescription = scrollHint
        },
    ) {
        item {
            HgChip(
                label = stringResource(R.string.filter_photos),
                selected = chips.imagesOnly,
                onClick = {
                    onChange(chips.copy(imagesOnly = !chips.imagesOnly, videosOnly = false))
                },
            )
        }
        item {
            HgChip(
                label = stringResource(R.string.filter_videos),
                selected = chips.videosOnly,
                onClick = {
                    onChange(chips.copy(videosOnly = !chips.videosOnly, imagesOnly = false))
                },
            )
        }
        item {
            HgChip(
                label = stringResource(R.string.filter_favourites),
                selected = chips.favouritesOnly,
                onClick = { onChange(chips.copy(favouritesOnly = !chips.favouritesOnly)) },
            )
        }
        item {
            HgChip(
                label = stringResource(R.string.filter_small),
                selected = chips.size == SizeBucket.SMALL,
                onClick = { onChange(chips.copy(size = chips.size.toggle(SizeBucket.SMALL))) },
            )
        }
        item {
            HgChip(
                label = stringResource(R.string.filter_large),
                selected = chips.size == SizeBucket.LARGE,
                onClick = { onChange(chips.copy(size = chips.size.toggle(SizeBucket.LARGE))) },
            )
        }
        item {
            HgChip(
                label = stringResource(R.string.filter_this_week),
                selected = chips.within == DateBucket.WEEK,
                onClick = { onChange(chips.copy(within = chips.within.toggle(DateBucket.WEEK))) },
            )
        }
        item {
            HgChip(
                label = stringResource(R.string.filter_this_year),
                selected = chips.within == DateBucket.YEAR,
                onClick = { onChange(chips.copy(within = chips.within.toggle(DateBucket.YEAR))) },
            )
        }
    }
}

/**
 * The album row.
 *
 * The chosen album is drawn as a removable chip because pressing a plain chip
 * again would be a guess: "Camera" pressed twice could mean select or clear. The
 * cross makes the only available action, clearing, visible on the control.
 */
@Composable
private fun AlbumChips(
    chips: SearchChips,
    albums: List<Album>,
    onChange: (SearchChips) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = HgMetrics.Gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(albums.take(MAX_ALBUM_CHIPS), key = { it.id }) { album ->
            if (album.id == chips.albumId) {
                HgRemovableChip(
                    label = album.name,
                    onRemove = { onChange(chips.copy(albumId = null)) },
                )
            } else {
                HgChip(
                    label = album.name,
                    selected = false,
                    onClick = { onChange(chips.copy(albumId = album.id)) },
                )
            }
        }
    }
}

/** Pressing a lit chip again clears the axis: one value per axis, and none is one. */
private fun <T> T?.toggle(value: T): T? = if (this == value) null else value

/** Past a dozen album chips the row is a scroll, not a choice. */
private const val MAX_ALBUM_CHIPS = 12
