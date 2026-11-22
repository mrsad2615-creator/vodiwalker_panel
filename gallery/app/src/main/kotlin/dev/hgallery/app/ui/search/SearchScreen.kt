package dev.hgallery.app.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.hgallery.app.R
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.ui.components.HgChip
import dev.hgallery.app.ui.components.HgIconButton
import dev.hgallery.app.ui.components.HgRemovableChip
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.photos.ThumbnailCell
import dev.hgallery.app.ui.theme.HgMetrics
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.core.Album
import dev.hgallery.core.DateBucket
import dev.hgallery.core.MediaItem
import dev.hgallery.core.SearchChips
import dev.hgallery.core.SearchFilters
import dev.hgallery.core.Selection
import dev.hgallery.core.SizeBucket

/**
 * Search over the whole library, in the shape the reference uses: a field in the
 * toolbar row, a chip row under it, then results in the ordinary photo grid.
 *
 * ## What this replaced
 *
 * The magnifier in the Photos toolbar was wired to an empty lambda. A control that
 * does nothing is the one thing this project must not ship, so the choice was
 * between removing the button and writing the screen. This is the screen.
 *
 * ## Why the chips are exclusive per axis
 *
 * "Photos" and "Videos" are one either/or axis rather than two checkboxes: a user
 * looking for a video does not want the stills mixed in. `SearchChips` enforces
 * that in its factory-style copy helpers below, so the screen cannot express a
 * contradictory query and `SearchFilters` never has to resolve one.
 *
 * ## Where the filtering happens
 *
 * In `core/SearchFilters`, which is unit-tested. This file only decides what to
 * draw and which chip is pressed; it never compares a date or a byte count.
 */
@Composable
fun SearchScreen(
    container: AppContainer,
    items: List<MediaItem>,
    albums: List<Album>,
    onClose: () -> Unit,
    onOpenItem: (Long) -> Unit,
) {
    val palette = hgPalette
    var chips by remember { mutableStateOf(SearchChips()) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // The screen exists to be typed into, so the field takes focus on arrival.
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    val results = remember(items, chips) {
        SearchFilters.apply(items, chips.toQuery(System.currentTimeMillis()))
    }
    val thumbnails = remember { container.thumbnails }

    Column(modifier = Modifier.fillMaxSize().background(palette.page)) {
        Spacer(Modifier.statusBarsPadding())

        SearchField(
            text = chips.text,
            onTextChange = { chips = chips.copy(text = it) },
            onSearch = { keyboard?.hide() },
            onClose = onClose,
            focus = focus,
        )

        FilterRow(chips = chips, albums = albums, onChange = { chips = it })

        Text(
            text = pluralStringResource(R.plurals.search_result_count, results.size, results.size),
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            modifier = Modifier.padding(start = HgMetrics.Gutter, top = 2.dp, bottom = 8.dp),
        )

        if (results.isEmpty()) {
            EmptyResults(chips)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(HgMetrics.PhotosColumns),
                horizontalArrangement = Arrangement.spacedBy(HgMetrics.GridGap),
                verticalArrangement = Arrangement.spacedBy(HgMetrics.GridGap),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.testTag(HgTestTags.SEARCH_RESULTS),
            ) {
                items(results, key = { it.id }) { item ->
                    ThumbnailCell(
                        item = item,
                        store = thumbnails,
                        selection = Selection(),
                        // Results are for finding one thing, so a tap opens it and
                        // a long press does not start a selection the search screen
                        // has no bar for.
                        onClick = { onOpenItem(item.id) },
                        onLongClick = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    text: String,
    onTextChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    focus: FocusRequester,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HgIconButton(
            icon = HgIcons.Back,
            contentDescription = stringResource(R.string.cancel),
            onClick = onClose,
        )
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = {
                Icon(
                    imageVector = HgIcons.Search,
                    contentDescription = null,
                    tint = hgPalette.textTertiary,
                    modifier = Modifier.size(20.dp),
                )
            },
            shape = RoundedCornerShape(22.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .testTag(HgTestTags.SEARCH_FIELD),
        )
    }
}

/**
 * The chip row.
 *
 * Two lines rather than one: the kind chips and the "what else" chips answer
 * different questions, and a single horizontally scrolling row hides the second
 * question off the edge of a phone. The second row scrolls because album chips are
 * unbounded in number.
 */
@Composable
private fun FilterRow(
    chips: SearchChips,
    albums: List<Album>,
    onChange: (SearchChips) -> Unit,
) {
    val mine = albums.firstOrNull { it.id == chips.albumId }

    LazyRow(
        contentPadding = PaddingValues(horizontal = HgMetrics.Gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            HgChip(
                label = stringResource(R.string.filter_photos),
                selected = chips.imagesOnly,
                onClick = { onChange(chips.copy(imagesOnly = !chips.imagesOnly, videosOnly = false)) },
            )
        }
        item {
            HgChip(
                label = stringResource(R.string.filter_videos),
                selected = chips.videosOnly,
                onClick = { onChange(chips.copy(videosOnly = !chips.videosOnly, imagesOnly = false)) },
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

    Spacer(Modifier.height(8.dp))

    if (albums.isNotEmpty()) {
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
        Spacer(Modifier.height(8.dp))
    }

    // `mine` is read only to keep the selected chip stable when the album list
    // changes underneath it; if the album vanished, the filter must go with it.
    if (chips.albumId != null && mine == null) {
        LaunchedEffect(chips.albumId) { onChange(chips.copy(albumId = null)) }
    }
}

/** Pressing a chip twice clears it: one value per axis, and none is a value. */
private fun <T> T?.toggle(value: T): T? = if (this == value) null else value

/**
 * What to say when nothing matched.
 *
 * The two cases are genuinely different advice. An empty query with filters set
 * means the filters are too narrow; a typed query with no filters means the
 * library has no such file. Telling them apart is the difference between "clear a
 * chip" and "look somewhere else".
 */
@Composable
private fun EmptyResults(chips: SearchChips) {
    val palette = hgPalette
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.padding(top = 64.dp, start = 40.dp, end = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = HgIcons.Search,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.search_no_results),
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    if (chips.hasFilters) R.string.search_no_results_filters
                    else R.string.search_no_results_body,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )
        }
    }
}

/** How many album chips are worth a scroll. Beyond this the chip row is unusable. */
private const val MAX_ALBUM_CHIPS = 12
