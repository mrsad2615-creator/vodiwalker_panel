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
import dev.mrsad.core.progression.ProgressionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeState(
    val todayMinutes: Int = 0,
    val todaySessions: Int = 0,
    val streakDays: Int = 0,
    val todayProgress: Float = 0f,
    val bestWpm: Int? = null,
    val bestAccuracy: Double? = null,
    val bestReadingWpm: Int? = null,
    val totalSessions: Int = 0,
    val readSessions: Int = 0,
    val eyeSessions: Int = 0,
    val mindSessions: Int = 0,
    val avgEyeScore: Int = 0,
    val avgMindScore: Int = 0,
    val weeklyMinutesSeries: List<Double?> = emptyList(),
    val plan: List<PlanItem> = emptyList(),
    val recentEvents: List<ActivityEventEntity> = emptyList(),
    val progression: ProgressionEngine.ProgressionSummary? = null,
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

    private val recentEventsFlow: Flow<List<ActivityEventEntity>> = todayFlow.flatMapLatest {
        val now = clock.nowEpochMillis()
        activityDao.recentEvents(now - 90 * DAY, 300)
    }

    private val coachInputsFlow: Flow<CoachSnapshotInputs> = todayFlow.flatMapLatest { date ->
        recentEventsFlow.map { events ->
            val now = clock.nowEpochMillis()
            val recentWpm = typingDao.averageWpmSince(now - 7 * DAY) ?: 0.0
            val previousWpm = typingDao.averageWpmBetween(now - 14 * DAY, now - 7 * DAY) ?: 0.0
            val accuracy = typingDao.averageAccuracySince(now - 7 * DAY) ?: 0.0
            val mistakes = decodeMistakes(typingDao.mistakesSince(now - 14 * DAY))
            CoachSnapshotInputs(
                dateIso = date,
                recentWpm = recentWpm,
                previousWpm = previousWpm,
                accuracy = accuracy,
                mistakes = mistakes,
                events = events,
            )
        }
    }

    val state: StateFlow<HomeState> = combine(dashboard, coachInputsFlow) { head, inputs ->
        val date = inputs.dateIso
        val events = inputs.events
        val activeSet = head.activeDates.toSet()
        val streak = AnalyticsMath.currentStreak(activeSet, date)

        val readEvents = events.filter { it.module == "READ" }
        val eyeEvents = events.filter { it.module == "EYE" }
        val mindEvents = events.filter { it.module == "MIND" }
        val focusEvents = events.filter { it.module == "FOCUS" }
        val aiEvents = events.filter { it.module == "AI" }

        val bestReadWpm = readEvents.maxOfOrNull { it.primaryMetric.toInt() }
        val avgEye = if (eyeEvents.isEmpty()) 0.0 else eyeEvents.map { it.primaryMetric }.average()
        val avgMind = if (mindEvents.isEmpty()) 0.0 else mindEvents.map { it.primaryMetric }.average()
        val totalMinutes = events.sumOf { it.minutes }

        val last7Days = AnalyticsMath.lastDays(date, 7)
        val minutesByDate = events.groupBy { it.dateIso }.mapValues { (_, list) -> list.sumOf { it.minutes } }
        val weeklySeries: List<Double?> = last7Days.map { d ->
            val m = minutesByDate[d]
            if (m != null && m > 0) m.toDouble() else null
        }
        val weeklyMinutesSum = last7Days.sumOf { minutesByDate[it] ?: 0 }

        val snapshot = SkillSnapshot(
            typingSessions = head.totalSessions,
            avgWpm = inputs.recentWpm,
            avgAccuracy = inputs.accuracy,
            recentWpm = inputs.recentWpm,
            previousWpm = inputs.previousWpm,
            topMistakes = inputs.mistakes,
            readingSessions = readEvents.size,
            avgReadingWpm = readEvents.map { it.primaryMetric }.average().takeIf { !it.isNaN() }?.toInt() ?: 0,
            avgComprehension = readEvents.map { it.secondaryMetric }.average().takeIf { !it.isNaN() }?.toInt() ?: 0,
            mindSessions = mindEvents.size,
            avgMindScore = avgMind,
            eyeSessions = eyeEvents.size,
            avgEyeScore = avgEye,
            focusMinutesLast7Days = focusEvents.sumOf { it.minutes },
            activeDaysLast7Days = last7Days.count { it in activeSet },
        )

        val planItems = if (head.totalSessions > 0 || activeSet.isNotEmpty() || events.isNotEmpty()) {
            Coach.dailyPlan(snapshot, date)
        } else {
            emptyList()
        }

        val progression = ProgressionEngine.summarize(
            todayIso = date,
            activeDates = activeSet,
            streakDays = streak,
            typingSessions = head.totalSessions,
            bestWpm = head.bestWpm ?: 0.0,
            bestAccuracy = head.bestAccuracy ?: 0.0,
            readingSessions = readEvents.size,
            bestReadingWpm = (bestReadWpm ?: 0).toDouble(),
            eyeSessions = eyeEvents.size,
            bestEyeScore = eyeEvents.maxOfOrNull { it.primaryMetric.toInt() } ?: 0,
            mindSessions = mindEvents.size,
            bestMindScore = mindEvents.maxOfOrNull { it.primaryMetric.toInt() } ?: 0,
            focusMinutes = focusEvents.sumOf { it.minutes },
            aiSessions = aiEvents.size,
            totalMinutes = totalMinutes,
            weeklyMinutes = weeklyMinutesSum,
        )

        HomeState(
            todayMinutes = head.minutes,
            todaySessions = head.sessions,
            streakDays = streak,
            todayProgress = (head.minutes / 25f).coerceIn(0f, 1f),
            bestWpm = head.bestWpm?.toInt(),
            bestAccuracy = head.bestAccuracy,
            bestReadingWpm = bestReadWpm,
            totalSessions = head.totalSessions,
            readSessions = readEvents.size,
            eyeSessions = eyeEvents.size,
            mindSessions = mindEvents.size,
            avgEyeScore = avgEye.toInt(),
            avgMindScore = avgMind.toInt(),
            weeklyMinutesSeries = weeklySeries,
            plan = planItems,
            recentEvents = events.take(20),
            progression = progression,
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

    private data class CoachSnapshotInputs(
        val dateIso: String,
        val recentWpm: Double,
        val previousWpm: Double,
        val accuracy: Double,
        val mistakes: List<Pair<Char, Int>>,
        val events: List<ActivityEventEntity>,
    )

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(container) as T
    }

    private companion object {
        const val DAY = 86_400_000L

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
