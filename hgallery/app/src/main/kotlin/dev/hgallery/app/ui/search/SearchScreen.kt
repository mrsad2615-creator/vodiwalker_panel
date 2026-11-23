package dev.hgallery.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.hgallery.app.R
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.ui.components.HgIconButton
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.photos.ThumbnailCell
import dev.hgallery.app.ui.theme.HgMetrics
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.core.Album
import dev.hgallery.core.MediaItem
import dev.hgallery.core.SearchChips
import dev.hgallery.core.SearchFilters
import dev.hgallery.core.Selection

/**
 * Search over the whole library, in the shape the reference uses: a field in the
 * toolbar row, chip rows beneath it, then results in the ordinary photo grid.
 *
 * ## What this replaced
 *
 * The magnifier in the Photos toolbar was wired to an empty lambda. A control that
 * does nothing is the one thing this project must not ship, so the choice was
 * between removing the button and writing the screen. This is the screen.
 *
 * ## Where the filtering happens
 *
 * In `core/SearchFilters`, which is unit-tested, and in `core/SearchChips`, which
 * turns chip choices into a query. This file decides only what to draw and which
 * chip is pressed; it never compares a date or a byte count itself.
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

    // The album filter must not survive the album disappearing underneath it.
    LaunchedEffect(albums) {
        if (chips.albumId != null && albums.none { it.id == chips.albumId }) {
            chips = chips.copy(albumId = null)
        }
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

        ResultRow(
            count = results.size,
            showClear = chips.hasFilters,
            // A fresh SearchChips, not a copy with fields zeroed: adding an axis to
            // the type then cannot leave it set after "Clear" without the compiler
            // noticing this constructor call.
            onClear = { chips = SearchChips(text = chips.text) },
        )

        SearchChipRows(chips = chips, albums = albums, onChange = { chips = it })

        if (results.isEmpty()) {
            EmptyResults(chips)
        } else {
            SearchResults(
                results = results,
                thumbnails = thumbnails,
                onOpenItem = onOpenItem,
            )
        }
    }
}

/**
 * How many results, and the way out of a filter that produced none.
 *
 * These are one row because they answer the same question. The clear control used
 * to sit between the two chip rows, where it looked like a third row and belonged
 * to whichever row happened to be below it; the empty state says "try clearing
 * one" and the thing that does it is now on the same line as the count it changes.
 *
 * The count is hidden when nothing matches: the empty state already says so in
 * larger type, and "0 results" above "No matches" is the same fact twice.
 */
@Composable
private fun ResultRow(count: Int, showClear: Boolean, onClear: () -> Unit) {
    val palette = hgPalette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = HgMetrics.Gutter, end = 12.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (count == 0) {
                ""
            } else {
                pluralStringResource(R.plurals.search_result_count, count, count)
            },
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            modifier = Modifier.weight(1f),
        )
        if (showClear) {
            Text(
                text = stringResource(R.string.search_clear_filters),
                style = MaterialTheme.typography.labelLarge,
                color = palette.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onClear)
                    // 48dp target: this is a text link by design, but it is still a
                    // control and must be reachable without aiming.
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }
    }
}

/** The toolbar row: back out, then the field itself. */
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
            contentDescription = stringResource(R.string.search_close),
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

/** One grid, so the results and the Photos tab cannot drift apart in density. */
@Composable
private fun SearchResults(
    results: List<MediaItem>,
    thumbnails: dev.hgallery.app.data.ThumbnailStore,
    onOpenItem: (Long) -> Unit,
) {
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
                // Results exist to find one thing, so a tap opens it and a long
                // press does nothing: this screen has no selection bar to drive.
                onClick = { onOpenItem(item.id) },
                onLongClick = {},
            )
        }
    }
}
