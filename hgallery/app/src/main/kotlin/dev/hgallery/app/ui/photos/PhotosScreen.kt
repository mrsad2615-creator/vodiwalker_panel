package dev.hgallery.app.ui.photos

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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.data.ThumbnailStore
import dev.hgallery.app.data.photosSubtitle
import dev.hgallery.app.ui.components.HgScreenTitle
import dev.hgallery.app.ui.components.HgToolbar
import dev.hgallery.app.ui.components.MediaThumbnail
import dev.hgallery.app.ui.theme.HgMetrics
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.core.MediaItem
import java.util.Locale

/**
 * The Photos timeline.
 *
 * Structure taken from reference R1:
 *  - toolbar (search, overflow) at the top right, below the status bar;
 *  - large "Photos" title with a centred "N photos and M videos" line;
 *  - day sections, each introduced by a small date header;
 *  - a seamless 4-column grid;
 *  - the title scrolls with the content, it is not a sticky bar.
 */
@Composable
fun PhotosScreen(
    container: AppContainer,
    onOpenItem: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = hgPalette
    val items by container.mediaStore.items.collectAsStateWithLifecycle()
    val loading by container.mediaStore.loading.collectAsStateWithLifecycle()
    val error by container.mediaStore.error.collectAsStateWithLifecycle()

    val locale = remember { Locale.getDefault() }
    val sections = remember(items, locale) { container.sections(items, locale) }
    val (photos, videos) = remember(items) {
        val v = items.count { it.isVideo }
        (items.size - v) to v
    }

    val cell = remember { 256 }
    val thumbnails = remember { container.thumbnails }

    // Flatten into one lazy grid so the whole timeline is a single recycler:
    // a lazy column of lazy grids would defeat recycling and stutter on scroll.
    val rows = remember(sections) { TimelineRowFactory.build(sections, HgMetrics.PhotosColumns) }

    Column(modifier = modifier.fillMaxSize().background(palette.page)) {
        Spacer(Modifier.statusBarsPadding())
        Column(Modifier.fillMaxWidth()) {
            HgToolbar(
                onSearch = {},
                onMore = {},
                modifier = Modifier.padding(end = 6.dp),
            )
            HgScreenTitle(
                title = "Photos",
                subtitle = if (items.isNotEmpty()) photosSubtitle(photos, videos) else null,
                subtitleCentred = true,
            )
            Spacer(Modifier.height(14.dp))
        }

        when {
            loading && items.isEmpty() -> LoadingState()
            error != null && items.isEmpty() -> MessageState("Could not read this device's media", error ?: "")
            items.isEmpty() -> MessageState("No photos yet", "Photos and videos on this device will appear here")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(HgMetrics.PhotosColumns),
                horizontalArrangement = Arrangement.spacedBy(HgMetrics.GridGap),
                verticalArrangement = Arrangement.spacedBy(HgMetrics.GridGap),
                contentPadding = PaddingValues(start = HgMetrics.Gutter, end = HgMetrics.Gutter, bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                rows.forEach { row ->
                    when (row) {
                        is TimelineRows.Header -> item(span = { GridItemSpan(maxLineSpan) }) {
                            DateHeader(row.label)
                        }
                        is TimelineRows.Cells -> items(row.items, key = { it.id }) { item ->
                            ThumbnailCell(item, thumbnails, cell) { onOpenItem(item.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbnailCell(
    item: MediaItem,
    store: ThumbnailStore,
    cellPx: Int,
    onClick: () -> Unit,
) {
    MediaThumbnail(
        item = item,
        store = store,
        cornerRadius = 3,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
            .clickable(onClick = onClick),
    )
}

@Composable
private fun DateHeader(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = hgPalette.textPrimary,
        )
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = hgPalette.accent, strokeWidth = 2.dp)
    }
}

@Composable
private fun MessageState(title: String, detail: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = hgPalette.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = hgPalette.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
