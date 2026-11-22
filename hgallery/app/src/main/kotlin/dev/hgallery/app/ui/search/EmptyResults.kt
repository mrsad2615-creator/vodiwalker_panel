package dev.hgallery.app.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hgallery.app.R
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.core.SearchChips

/**
 * What to say when nothing matched.
 *
 * The two cases carry different advice, so they get different sentences. Filters
 * with no text means the filters are too narrow, and the answer is to clear a chip.
 * Text with no filters means the library has no such file, and the answer is to
 * check where it was saved. One generic "no results" would tell the user neither.
 */
@Composable
fun EmptyResults(chips: SearchChips) {
    val palette = hgPalette
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.padding(top = 56.dp, start = 44.dp, end = 44.dp),
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
                    if (chips.hasFilters) {
                        R.string.search_no_results_filters
                    } else {
                        R.string.search_no_results_body
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
