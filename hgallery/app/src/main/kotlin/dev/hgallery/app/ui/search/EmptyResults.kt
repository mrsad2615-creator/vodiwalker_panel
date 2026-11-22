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
 * Three cases, because the useful advice differs in each:
 *
 *  - filters and no text: the filters are too narrow, so clear a chip;
 *  - text and no filters: the library has no such file, so check where it was saved;
 *  - text *and* filters: the name may be fine and the filters may be wrong, so the
 *    guidance has to mention the filters without asserting which one is at fault.
 *
 * The third case exists because the first two left it untold. A screenshot caught
 * "Nothing matches with these filters. Try clearing one." over a query that also
 * had a search term in it, and the sentence named only half of what the user typed.
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
                    when {
                        chips.hasFilters && chips.text.isNotBlank() ->
                            R.string.search_no_results_both
                        chips.hasFilters -> R.string.search_no_results_filters
                        else -> R.string.search_no_results_body
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
