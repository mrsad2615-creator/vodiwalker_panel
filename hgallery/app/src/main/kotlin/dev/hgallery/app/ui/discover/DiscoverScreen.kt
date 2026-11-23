package dev.hgallery.app.ui.discover

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hgallery.app.R
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.components.HgToolbar
import dev.hgallery.app.ui.components.MediaThumbnail
import dev.hgallery.app.ui.components.MessageState
import dev.hgallery.app.ui.theme.CountLabel
import dev.hgallery.app.ui.theme.HgMetrics
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.app.ui.theme.hgShapes
import dev.hgallery.core.DiscoverBuilder
import dev.hgallery.core.MediaItem
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Discover tab.
 *
 * Four questions about the library, in the order most users ask them: what happened on
 * this day, what did I mark, what years do I have, and what is taking my storage. Every
 * row is built from rows that exist on the device; the screen has no seed content.
 *
 * An empty section is not drawn. A heading with nothing under it reads as a load failure,
 * and it is the one thing that makes an offline screen look like it is trying to fetch
 * something.
 */
@Composable
fun DiscoverScreen(
    container: AppContainer,
    onOpenItem: (Long) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = hgPalette
    val items by container.mediaStore.items.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }

    val content = remember(items, zone, today) { DiscoverBuilder.build(items, today, zone) }

    Column(modifier = modifier.fillMaxSize().background(palette.page)) {
        Spacer(Modifier.statusBarsPadding())
        HgToolbar(onSearch = onSearch, modifier = Modifier.padding(end = 6.dp))
        Text(
            text = stringResource(R.string.tab_discover),
            style = MaterialTheme.typography.displaySmall,
            color = palette.textPrimary,
            modifier = Modifier.padding(start = HgMetrics.Gutter, end = HgMetrics.Gutter),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.discover_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
            modifier = Modifier.padding(start = HgMetrics.Gutter, end = HgMetrics.Gutter),
        )
        Spacer(Modifier.height(12.dp))

        if (content.isEmpty) {
            MessageState(
                title = stringResource(R.string.discover_empty_title),
                detail = stringResource(R.string.discover_empty_body),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(HgTestTags.DISCOVER_LIST),
            ) {
                if (content.onThisDay.isNotEmpty()) {
                    item {
                        DiscoverRow(
                            title = stringResource(R.string.discover_on_this_day),
                            items = content.onThisDay,
                            container = container,
                            onOpenItem = onOpenItem,
                            rowTag = HgTestTags.DISCOVER_ON_THIS_DAY,
                        )
                    }
                }
                if (content.favourites.isNotEmpty()) {
                    item {
                        DiscoverRow(
                            title = stringResource(R.string.discover_favourites),
                            items = content.favourites,
                            container = container,
                            onOpenItem = onOpenItem,
                            rowTag = HgTestTags.DISCOVER_FAVOURITES,
                        )
                    }
                }
                if (content.years.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.discover_years),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary,
                            modifier = Modifier.padding(
                                start = HgMetrics.Gutter,
                                end = HgMetrics.Gutter,
                            ),
                        )
                    }
                    items(content.years, key = { it.key }) { section ->
                        YearRow(
                            section = section,
                            container = container,
                            onOpenItem = onOpenItem,
                        )
                    }
                }
                if (content.largest.isNotEmpty()) {
                    item {
                        DiscoverRow(
                            title = stringResource(R.string.discover_largest),
                            items = content.largest,
                            container = container,
                            onOpenItem = onOpenItem,
                            rowTag = HgTestTags.DISCOVER_LARGEST,
                        )
                    }
                }
            }
        }
    }
}

/** A titled horizontal strip of thumbnails, which is the one shape all four sections use. */
@Composable
private fun DiscoverRow(
    title: String,
    items: List<MediaItem>,
    container: AppContainer,
    onOpenItem: (Long) -> Unit,
    rowTag: String,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = hgPalette.textPrimary,
            modifier = Modifier.padding(start = HgMetrics.Gutter, end = HgMetrics.Gutter),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(start = HgMetrics.Gutter, end = HgMetrics.Gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(rowTag),
        ) {
            items(items, key = { it.id }) { item ->
                Box(
                    modifier = Modifier
                        .width(132.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(hgShapes.tile))
                        .background(hgPalette.chipBackground)
                        .testTag(HgTestTags.discoverItem(item.id))
                        .clickable { onOpenItem(item.id) },
                ) {
                    MediaThumbnail(
                        item = item,
                        store = container.thumbnails,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** One year: its count, then a strip of the year's photographs. */
@Composable
private fun YearRow(
    section: DiscoverBuilder.Section,
    container: AppContainer,
    onOpenItem: (Long) -> Unit,
) {
    val countLabel = pluralStringResource(
        R.plurals.discover_year_count,
        section.itemCount,
        section.itemCount,
    )
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = HgMetrics.Gutter, end = HgMetrics.Gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // The key carries the year; the label is only the number, so a
                // language that writes years differently still reads correctly.
                text = section.key.removePrefix("year-"),
                style = MaterialTheme.typography.titleMedium,
                color = hgPalette.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(text = countLabel, style = CountLabel, color = hgPalette.textSecondary)
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(start = HgMetrics.Gutter, end = HgMetrics.Gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HgTestTags.discoverYear(section.key)),
        ) {
            items(section.items, key = { it.id }) { item ->
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(hgShapes.tile))
                        .background(hgPalette.chipBackground)
                        .testTag(HgTestTags.discoverItem(item.id))
                        .clickable { onOpenItem(item.id) },
                ) {
                    MediaThumbnail(
                        item = item,
                        store = container.thumbnails,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
