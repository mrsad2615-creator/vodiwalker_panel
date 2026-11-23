package dev.hgallery.app.ui.highlights

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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hgallery.app.R
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.components.HgToolbar
import dev.hgallery.app.ui.components.MediaThumbnail
import dev.hgallery.app.ui.components.MessageState
import dev.hgallery.app.ui.theme.AlbumTitle
import dev.hgallery.app.ui.theme.CountLabel
import dev.hgallery.app.ui.theme.HgMetrics
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.app.ui.theme.hgShapes
import dev.hgallery.core.CalendarLabels
import dev.hgallery.core.HighlightsBuilder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Highlights tab.
 *
 * A highlight is one day in one place, drawn as a wide card with the newest photograph
 * behind a short caption. The set is a list, not a grid: every card carries a title and a
 * date, and two-line captions in a two-column grid are unreadable on a phone.
 *
 * Everything here comes from the library. When no day has enough items, the screen says
 * so; it never invents a card to fill the space.
 */
@Composable
fun HighlightsScreen(
    container: AppContainer,
    onOpenItem: (Long) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = hgPalette
    val items by container.mediaStore.items.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    // Fixed per composition of the screen. Reading the clock inside the card would let a
    // card change its own label on a recomposition that crossed midnight.
    val today = remember { LocalDate.now(zone) }
    val locale = remember { Locale.getDefault() }

    val highlights = remember(items, zone, today) { HighlightsBuilder.build(items, zone) }

    Column(modifier = modifier.fillMaxSize().background(palette.page)) {
        Spacer(Modifier.statusBarsPadding())
        HgToolbar(onSearch = onSearch, modifier = Modifier.padding(end = 6.dp))
        Text(
            text = stringResource(R.string.tab_highlights),
            style = MaterialTheme.typography.displaySmall,
            color = palette.textPrimary,
            modifier = Modifier.padding(start = HgMetrics.Gutter, end = HgMetrics.Gutter),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.highlights_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
            modifier = Modifier.padding(start = HgMetrics.Gutter, end = HgMetrics.Gutter),
        )
        Spacer(Modifier.height(16.dp))

        if (highlights.isEmpty()) {
            MessageState(
                title = stringResource(R.string.highlights_empty_title),
                detail = stringResource(R.string.highlights_empty_body),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = HgMetrics.Gutter,
                    end = HgMetrics.Gutter,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(HgTestTags.HIGHLIGHTS_LIST),
            ) {
                items(highlights, key = { it.key }) { highlight ->
                    HighlightCard(
                        highlight = highlight,
                        container = container,
                        locale = locale,
                        zone = zone,
                        onOpenItem = onOpenItem,
                    )
                }
            }
        }
    }
}

/**
 * One highlight: a wide cover, then the place, the date and the count.
 *
 * The card opens the newest item in its group, which is the photograph it shows. A card
 * that opened a group screen would promise a screen that does not exist yet.
 */
@Composable
private fun HighlightCard(
    highlight: HighlightsBuilder.Highlight,
    container: AppContainer,
    locale: Locale,
    zone: ZoneId,
    onOpenItem: (Long) -> Unit,
) {
    val palette = hgPalette
    val cover = highlight.cover
    val dateLabel = remember(highlight.key, locale) {
        val date = Instant.ofEpochMilli(highlight.dayMs).atZone(zone).toLocalDate()
        val calendar = CalendarLabels.calendarFor(locale.toLanguageTag())
        val sameYear = CalendarLabels.sameYear(date, LocalDate.now(zone), calendar)
        date.format(
            DateTimeFormatter.ofPattern(
                CalendarLabels.dayPattern(includeYear = !sameYear),
                CalendarLabels.localeFor(calendar, locale),
            ),
        )
    }
    val countLabel = pluralStringResource(R.plurals.highlight_count, highlight.itemCount, highlight.itemCount)

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(hgShapes.card))
                .background(palette.chipBackground)
                .testTag(HgTestTags.highlight(highlight.key))
                .clickable(enabled = cover != null) { cover?.let { onOpenItem(it.id) } },
        ) {
            if (cover != null) {
                MediaThumbnail(
                    item = cover,
                    store = container.thumbnails,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = highlight.title,
                    style = AlbumTitle,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(text = dateLabel, style = CountLabel, color = palette.textSecondary)
            }
            Text(text = countLabel, style = CountLabel, color = palette.textSecondary)
        }
    }
}
