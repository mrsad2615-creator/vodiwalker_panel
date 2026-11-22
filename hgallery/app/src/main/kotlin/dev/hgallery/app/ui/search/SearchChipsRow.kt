package dev.hgallery.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import dev.hgallery.app.ui.theme.hgPalette
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.hgallery.app.R
import dev.hgallery.app.ui.components.HgChip
import dev.hgallery.app.ui.components.HgRemovableChip
import dev.hgallery.app.ui.theme.HgMetrics
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
        KindChips(chips = chips, onChange = onChange)

        // The empty state tells the user to clear a filter. Every chip does clear
        // itself when pressed again, but that asks the user to work out which chip
        // is lit and that pressing it again goes backwards; one visible "Clear"
        // answers the sentence directly. It is absent until there is something to
        // clear, so it never takes space from a search that has no filters.
        if (chips.hasFilters) {
            ClearFilters(
                // A fresh SearchChips, not a copy with fields zeroed: adding a new
                // axis to the type then cannot leave it set after "Clear" without
                // the compiler noticing the constructor call.
                onClear = { onChange(SearchChips(text = chips.text)) },
            )
        }

        if (albums.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            AlbumChips(chips = chips, albums = albums, onChange = onChange)
        }

        Spacer(Modifier.height(4.dp))
    }
}

/**
 * "Clear" for the chips, not for the typed text.
 *
 * The text is the thing the user deliberately typed and can delete with the
 * keyboard's own backspace; the chips are what they cannot see all of. Clearing
 * keeps the text, so "clip" survives clearing and the results narrow back from
 * nothing to the two clips.
 */
@Composable
private fun ClearFilters(onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HgMetrics.Gutter, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = stringResource(R.string.search_clear_filters),
            style = MaterialTheme.typography.labelLarge,
            color = hgPalette.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onClear)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
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
    LazyRow(
        contentPadding = PaddingValues(horizontal = HgMetrics.Gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
