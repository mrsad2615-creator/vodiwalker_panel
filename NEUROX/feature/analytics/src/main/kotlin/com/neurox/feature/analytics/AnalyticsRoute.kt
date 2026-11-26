package com.neurox.feature.analytics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.neurox.designsystem.components.NxLoading
import com.neurox.presentation.di.AppServices
import com.neurox.domain.analytics.AnalyticsService
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.screen.AnalyticsState
import com.neurox.presentation.screen.ScreenBuilders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stateful entry point for Progress.
 *
 * The range is a piece of UI state, and changing it re-derives the snapshot from the
 * same stored records; nothing is cached across ranges, so the screen can never show
 * a week's numbers under a month's label.
 */
@Composable
fun AnalyticsRoute(
    services: AppServices,
    strings: Strings,
    initialRange: AnalyticsState.Range = AnalyticsState.Range.WEEK,
    /**
     * Where the empty state's "start training" button goes.
     *
     * It was previously a hard-coded `{ }`, so on a fresh profile - the only state
     * in which that button is even shown - tapping it did nothing at all.
     */
    onStartTraining: () -> Unit = {},
) {
    var range by remember { mutableStateOf(initialRange) }
    var state by remember { mutableStateOf<AnalyticsState?>(null) }
    // True until the first read for the current range completes. Rendering an empty
    // table during that window showed "no data" to a user who has plenty, which is
    // worse than a spinner: it is wrong rather than merely unfinished.
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(range) {
        loading = true
        state = withContext(Dispatchers.IO) {
            val active = services.profileRepository.getActiveProfile() ?: return@withContext null
            // ALL range starts at epoch: the builder filters by range itself, and a
            // fabricated "all time" start would hide a record older than the cutoff.
            val now = System.currentTimeMillis()
            val start = when (range) {
                AnalyticsState.Range.WEEK -> now - 7L * AnalyticsService.DAY_MILLIS
                AnalyticsState.Range.MONTH -> now - 30L * AnalyticsService.DAY_MILLIS
                AnalyticsState.Range.ALL -> 0L
            }
            val records = services.sessionRepository.listAll(active.id)
            ScreenBuilders.analytics(
                records = records,
                range = range,
                rangeStartMillis = start,
                rangeEndMillis = now,
                baseline = records.filter { it.kind == com.neurox.domain.model.SessionKind.BASELINE },
            )
        }
        loading = false
    }

    val current = state
    if (current == null || loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            NxLoading(message = strings.raw("common.loading"))
        }
        return
    }

    AnalyticsScreen(
        state = current,
        strings = strings,
        onSelectRange = { range = it },
        onStartTraining = onStartTraining,
    )
}
