package dev.mrsad.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.db.ActivityDao
import dev.mrsad.app.data.db.ActivityEventEntity
import dev.mrsad.app.data.db.TypingSessionDao
import dev.mrsad.app.util.MrsadClock
import dev.mrsad.core.analytics.AnalyticsMath
import dev.mrsad.core.coach.Coach
import dev.mrsad.core.coach.PlanItem
import dev.mrsad.core.coach.SkillSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.stateIn

data class HomeState(
    val todayMinutes: Int = 0,
    val todaySessions: Int = 0,
    val streakDays: Int = 0,
    val todayProgress: Float = 0f,
    val bestWpm: Int? = null,
    val bestAccuracy: Double? = null,
    val totalSessions: Int = 0,
    val plan: List<PlanItem> = emptyList(),
    val recentEvents: List<ActivityEventEntity> = emptyList(),
) {
    val hasAnyData: Boolean get() = totalSessions > 0 || recentEvents.isNotEmpty()

    /** Core energy = real goal progress today (25-minute daily target). */
    val energy: Float get() = (todayMinutes / 25f).coerceIn(0f, 1f)
}

/**
 * Home aggregates real database flows only.
 *
 * The "today" key re-emits whenever the app returns to the foreground, so a
 * dashboard left open overnight shows the new day instead of stale numbers.
 * Heavy aggregate reads run on [Dispatchers.Default].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(container: AppContainer) : ViewModel() {

    private val clock = MrsadClock
    private val activityDao: ActivityDao = container.database.activityDao()
    private val typingDao: TypingSessionDao = container.database.typingSessionDao()

    private val todayFlow: Flow<String> = flow {
        container.isForeground.collect { foreground ->
            if (foreground) emit(clock.todayIso())
        }
    }

    private val todayTotals = todayFlow.flatMapLatest { date ->
        combine(
            activityDao.minutesForDate(date),
            activityDao.sessionCountForDate(date),
        ) { minutes, sessions -> minutes to sessions }
    }

    private val dashboard: Flow<Dashboard> = combine(
        todayTotals,
        activityDao.activeDates(),
        typingDao.bestWordWpm(),
        typingDao.bestAccuracy(),
        typingDao.totalCount(),
    ) { totals, activeDates, bestWpm, bestAccuracy, total ->
        Dashboard(
            minutes = totals.first,
            sessions = totals.second,
            activeDates = activeDates,
            bestWpm = bestWpm,
            bestAccuracy = bestAccuracy,
            totalSessions = total,
        )
    }

    private val plan: Flow<Pair<List<PlanItem>, List<ActivityEventEntity>>> = todayFlow.flatMapLatest { date ->
        flow {
            emit(computePlan(date))
        }
    }

    private suspend fun computePlan(date: String): Pair<List<PlanItem>, List<ActivityEventEntity>> {
        val now = clock.nowEpochMillis()
        val activeDates = activityDao.activeDates().first()
        val typingSessions = typingDao.totalCount().first()
        val recentWpm = typingDao.averageWpmSince(now - 7 * DAY) ?: 0.0
        val previousWpm = typingDao.averageWpmBetween(now - 14 * DAY, now - 7 * DAY) ?: 0.0
        val accuracy = typingDao.averageAccuracySince(now - 7 * DAY) ?: 0.0
        val recent = activityDao.recentEvents(now - 30 * DAY, 20).first()
        val mistakes = decodeMistakes(typingDao.mistakesSince(now - 14 * DAY))

        val snapshot = SkillSnapshot(
            typingSessions = typingSessions,
            avgWpm = recentWpm,
            avgAccuracy = accuracy,
            recentWpm = recentWpm,
            previousWpm = previousWpm,
            topMistakes = mistakes,
            activeDaysLast7Days = AnalyticsMath.lastDays(date, 7).count { it in activeDates },
        )
        val items = if (typingSessions > 0 || activeDates.isNotEmpty()) {
            Coach.dailyPlan(snapshot, date)
        } else {
            emptyList()
        }
        return items to recent
    }

    val state: StateFlow<HomeState> = combine(dashboard, plan, todayFlow) { head, planData, date ->
        HomeState(
            todayMinutes = head.minutes,
            todaySessions = head.sessions,
            streakDays = AnalyticsMath.currentStreak(head.activeDates.toSet(), date),
            todayProgress = (head.minutes / 25f).coerceIn(0f, 1f),
            bestWpm = head.bestWpm?.toInt(),
            bestAccuracy = head.bestAccuracy,
            totalSessions = head.totalSessions,
            plan = planData.first,
            recentEvents = planData.second,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private data class Dashboard(
        val minutes: Int,
        val sessions: Int,
        val activeDates: List<String>,
        val bestWpm: Double?,
        val bestAccuracy: Double?,
        val totalSessions: Int,
    )

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(container) as T
    }

    private companion object {
        const val DAY = 86_400_000L

        /** Decodes the compact "charCode:count|…" mistake log into a heatmap. */
        fun decodeMistakes(encoded: List<String>): List<Pair<Char, Int>> =
            encoded.asSequence()
                .flatMap { it.split('|').asSequence() }
                .mapNotNull { part ->
                    val code = part.substringBefore(':', "").toIntOrNull()
                    val count = part.substringAfter(':', "").toIntOrNull()
                    if (code == null || count == null) null else code.toChar() to count
                }
                .groupingBy { it.first }
                .fold(0) { acc, pair -> acc + pair.second }
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key to it.value }
    }
}
