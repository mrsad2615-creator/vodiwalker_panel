package com.neurox.feature.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
) {
    var range by remember { mutableStateOf(initialRange) }
    var state by remember { mutableStateOf<AnalyticsState?>(null) }

    LaunchedEffect(range) {
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
    }

    AnalyticsScreen(
        state = state ?: ScreenBuilders.analytics(
            records = emptyList(),
            range = range,
            rangeStartMillis = 0L,
            rangeEndMillis = 0L,
        ),
        strings = strings,
        onSelectRange = { range = it },
        onStartTraining = { },
    )
}
