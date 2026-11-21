package dev.mrsad.app.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.db.ActivityDao
import dev.mrsad.app.data.db.DailyMetric
import dev.mrsad.app.data.db.DailyTotal
import dev.mrsad.app.data.db.TypingSessionDao
import dev.mrsad.app.util.MrsadClock
import dev.mrsad.core.analytics.AnalyticsMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class DailyRow(
    val dateIso: String,
    val labelFa: String,
    val minutes: Int,
    val sessions: Int,
)

data class AnalyticsState(
    val totalMinutes: Int = 0,
    val activeDays: Int = 0,
    val currentStreak: Int = 0,
    val minuteSeries: List<Double> = emptyList(),
    /**
     * The typing speed per day of the window.
     *
     * A day without a typing session is `null`, not zero: a missing
     * measurement must not be drawn as a session that never happened.
     */
    val typingSeries: List<Double?> = emptyList(),
    val averageWpm: Double = 0.0,
    val averageAccuracy: Double = 0.0,
    val bestWpm: Int? = null,
    val mindAverageScore: Double = 0.0,
    val moduleMinutes: Map<String, Int> = emptyMap(),
    val daily: List<DailyRow> = emptyList(),
    val wpmChange: Double = 0.0,
    val hasTrendChange: Boolean = false,
) {
    /** The number of days in the window that actually had a typing session. */
    val typingDays: Int get() = typingSeries.count { it != null }
}

private val moduleLabels = mapOf(
    "TYPE" to "تایپ",
    "READ" to "خواندن",
    "MIND" to "ذهن",
    "FOCUS" to "تمرکز",
)

/**
 * Seven-day analytics assembled exclusively from Room flows.
 *
 * The window start ("today − 6 days") re-emits on every foreground transition,
 * which also handles the midnight rollover. All aggregation happens on
 * Dispatchers.Default; nothing heavy touches the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(container: AppContainer) : ViewModel() {

    private val activityDao: ActivityDao = container.database.activityDao()
    private val typingDao: TypingSessionDao = container.database.typingSessionDao()
    private val typingRepository = container.typingRepository

    private val window: Flow<String> = flow {
        container.isForeground.collect { foreground ->
            if (foreground) emit(AnalyticsMath.lastDays(MrsadClock.todayIso(), 7).first())
        }
    }

    val state: StateFlow<AnalyticsState> = window.flatMapLatest { windowStart ->
        combine(
            activityDao.dailyTotalsSince(windowStart),
            activityDao.dailyMetricSince("TYPE", windowStart),
            activityDao.dailyMetricSince("MIND", windowStart),
            activityDao.moduleTotalsSince(windowStart),
            activityDao.activeDates(),
            typingDao.bestWordWpm(),
            typingDao.totalCount(),
        ) { values ->
            val dailyTotals = values[0] as List<DailyTotal>
            val typingByDay = values[1] as List<DailyMetric>
            val mindByDay = values[2] as List<DailyMetric>
            val moduleTotals = values[3] as List<dev.mrsad.app.data.db.ModuleTotal>
            val activeDates = values[4] as List<String>
            val bestWpm = values[5] as Double?
            buildState(windowStart, dailyTotals, typingByDay, mindByDay, moduleTotals, activeDates, bestWpm)
        }.flowOn(Dispatchers.Default)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())

    private suspend fun buildState(
        windowStart: String,
        dailyTotals: List<DailyTotal>,
        typingByDay: List<DailyMetric>,
        mindByDay: List<DailyMetric>,
        moduleTotals: List<dev.mrsad.app.data.db.ModuleTotal>,
        activeDates: List<String>,
        bestWpm: Double?,
    ): AnalyticsState {
        val now = MrsadClock.nowEpochMillis()
        val avgWpm = typingRepository.recentAverageWpm(now) ?: 0.0
        val prevWpm = typingRepository.previousAverageWpm(now) ?: 0.0
        val avgAccuracy = typingRepository.recentAverageAccuracy(now) ?: 0.0

        val days = AnalyticsMath.lastDays(MrsadClock.todayIso(), 7)
        val totalsByDate = dailyTotals.associateBy { it.dateIso }
        val daily = days.map { iso ->
            val total = totalsByDate[iso]
            DailyRow(
                dateIso = iso,
                labelFa = MrsadClock.persianWeekday(iso),
                minutes = total?.minutes ?: 0,
                sessions = total?.sessions ?: 0,
            )
        }
        val change = if (prevWpm > 0.0) AnalyticsMath.percentChange(prevWpm, avgWpm) else 0.0
        val mindAverage = if (mindByDay.isEmpty()) 0.0 else mindByDay.map { it.metric }.average()

        return AnalyticsState(
            totalMinutes = daily.sumOf { it.minutes },
            activeDays = daily.count { it.minutes > 0 || it.sessions > 0 },
            currentStreak = AnalyticsMath.currentStreak(activeDates.toSet(), MrsadClock.todayIso()),
            minuteSeries = daily.map { it.minutes.toDouble() },
            // Aligned to the same window as the day table, so the trend
            // line sits over the days it belongs to instead of being
            // stretched across the chart.
            typingSeries = AnalyticsMath.alignToWindow(
                days,
                typingByDay.associate { it.dateIso to it.metric },
            ),
            averageWpm = avgWpm,
            averageAccuracy = avgAccuracy,
            bestWpm = bestWpm?.toInt(),
            mindAverageScore = mindAverage,
            moduleMinutes = moduleTotals.associate { (moduleLabels[it.module] ?: it.module) to it.minutes },
            daily = daily,
            wpmChange = change,
            hasTrendChange = prevWpm > 0.0,
        )
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AnalyticsViewModel(container) as T
    }
}
