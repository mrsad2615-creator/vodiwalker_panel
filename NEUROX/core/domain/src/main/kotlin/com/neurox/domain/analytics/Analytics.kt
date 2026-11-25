package com.neurox.domain.analytics

import com.neurox.domain.model.Module
import com.neurox.domain.model.SessionKind
import com.neurox.domain.model.SessionRecord
import com.neurox.domain.model.SessionStatus
import com.neurox.domain.scoring.Scoring

/** A single point of a time series. [bucketStartMillis] labels the bucket. */
data class SeriesPoint(val bucketStartMillis: Long, val value: Double, val sampleCount: Int)

/** One bucket of training activity. */
data class ActivityBucket(
    val bucketStartMillis: Long,
    val sessionCount: Int,
    val activeMinutes: Int,
    val xp: Int,
)

/** Everything Home and Analytics render. All values come from stored records. */
data class AnalyticsSnapshot(
    val rangeStartMillis: Long,
    val rangeEndMillis: Long,
    val sessionCount: Int,
    val completedCount: Int,
    val abandonedCount: Int,
    val activeMinutes: Int,
    val totalXp: Int,
    val meanAccuracy: Double,
    val meanScore: Double,
    val activity: List<ActivityBucket>,
    val accuracyByModule: Map<Module, Double>,
    val speedByModule: Map<Module, Double>,
    val perModule: List<ModuleAnalytics>,
    val dailySeries: List<SeriesPoint>,
)

data class ModuleAnalytics(
    val module: Module,
    val sessions: Int,
    val accuracy: Double,
    val meanResponseMillis: Double,
    val meanScore: Double,
    val bestScore: Double,
    val errorCount: Int,
    val lastTrainedMillis: Long?,
    val trend: Double,
)

/**
 * Pure analytics aggregation. The repository loads records; this object computes.
 *
 * COMPARABILITY RULE: responses of different modules measure different things,
 * so speed is only averaged inside one module and is never used to rank modules.
 */
object AnalyticsService {

    const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** UTC day index, used for stable bucketing independent of DST. */
    fun dayKey(millis: Long): Long = Math.floorDiv(millis, DAY_MILLIS)

    fun snapshot(
        records: List<SessionRecord>,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        dayMillis: Long = DAY_MILLIS,
    ): AnalyticsSnapshot {
        require(rangeEndMillis >= rangeStartMillis)
        val inRange = records.filter { it.startedAtMillis in rangeStartMillis..rangeEndMillis }
        val countable = inRange.filter { it.kind != SessionKind.BASELINE }
        val completed = countable.filter { it.status == SessionStatus.COMPLETED }
        val abandoned = countable.filter { it.status == SessionStatus.ABANDONED }

        val buckets = mutableListOf<ActivityBucket>()
        var cursor = Math.floorDiv(rangeStartMillis, dayMillis) * dayMillis
        while (cursor <= rangeEndMillis) {
            val next = cursor + dayMillis
            val dayRecords = countable.filter { it.startedAtMillis >= cursor && it.startedAtMillis < next }
            buckets += ActivityBucket(
                bucketStartMillis = cursor,
                sessionCount = dayRecords.size,
                activeMinutes = (dayRecords.sumOf { it.activeDurationMillis } / 60_000L).toInt(),
                xp = dayRecords.sumOf { it.xpAwarded },
            )
            cursor = next
        }

        val dailySeries = buckets.map { b ->
            val dayRecords = completed.filter {
                it.startedAtMillis >= b.bucketStartMillis && it.startedAtMillis < b.bucketStartMillis + dayMillis
            }
            SeriesPoint(
                bucketStartMillis = b.bucketStartMillis,
                value = if (dayRecords.isEmpty()) 0.0 else dayRecords.map { it.accuracy }.average(),
                sampleCount = dayRecords.size,
            )
        }

        val perModule = Module.entries.map { module ->
            moduleAnalytics(module, countable.filter { it.module == module })
        }

        val accuracyByModule = Module.entries.associateWith { module ->
            moduleAnalytics(module, countable.filter { it.module == module }).accuracy
        }
        val speedByModule = Module.entries.associateWith { module ->
            moduleAnalytics(module, countable.filter { it.module == module }).meanResponseMillis
        }

        return AnalyticsSnapshot(
            rangeStartMillis = rangeStartMillis,
            rangeEndMillis = rangeEndMillis,
            sessionCount = countable.size,
            completedCount = completed.size,
            abandonedCount = abandoned.size,
            activeMinutes = (countable.sumOf { it.activeDurationMillis } / 60_000L).toInt(),
            totalXp = countable.sumOf { it.xpAwarded },
            meanAccuracy = if (completed.isEmpty()) 0.0 else completed.map { it.accuracy }.average(),
            meanScore = if (completed.isEmpty()) 0.0 else completed.map { it.score }.average(),
            activity = buckets,
            accuracyByModule = accuracyByModule,
            speedByModule = speedByModule,
            perModule = perModule,
            dailySeries = dailySeries,
        )
    }

    private fun moduleAnalytics(module: Module, records: List<SessionRecord>): ModuleAnalytics {
        val completed = records.filter { it.status == SessionStatus.COMPLETED }
        val last = records.maxByOrNull { it.startedAtMillis }
        return ModuleAnalytics(
            module = module,
            sessions = records.size,
            accuracy = if (completed.isEmpty()) 0.0 else completed.map { it.accuracy }.average(),
            meanResponseMillis = if (completed.isEmpty()) {
                0.0
            } else {
                completed.map { it.responseTimeMeanMillis }.average()
            },
            meanScore = if (completed.isEmpty()) 0.0 else completed.map { it.score }.average(),
            bestScore = completed.maxOfOrNull { it.score } ?: 0.0,
            errorCount = records.sumOf { it.incorrectCount + it.missedCount + it.prematureCount },
            lastTrainedMillis = last?.startedAtMillis,
            trend = Scoring.trend(completed.map { it.score }),
        )
    }

    /**
     * Error analysis for one module: which exercises contribute most errors.
     */
    fun errorProfile(records: List<SessionRecord>, module: Module): List<Pair<String, Int>> =
        records.asSequence()
            .filter { it.module == module }
            .groupBy { it.exerciseId }
            .map { (exerciseId, list) ->
                exerciseId to list.sumOf { it.incorrectCount + it.missedCount + it.prematureCount }
            }
            .sortedByDescending { it.second }
            .toList()

    /**
     * Personal records for one exercise, newest-best first.
     */
    fun personalRecords(records: List<SessionRecord>): Map<String, SessionRecord> {
        val recordsByExercise = records.filter { it.status == SessionStatus.COMPLETED }.groupBy { it.exerciseId }
        return recordsByExercise.mapNotNull { (exerciseId, list) ->
            list.maxByOrNull { it.score }?.let { exerciseId to it }
        }.toMap()
    }

    /**
     * Week boundary (Monday 00:00 UTC) for a millis timestamp.
     *
     * Uses GregorianCalendar with an explicit UTC zone, so the result is
     * identical on every device and independent of the user locale.
     */
    fun weekStartMillis(millis: Long, dayMillis: Long = DAY_MILLIS): Long {
        val calendar = utcCalendar(millis)
        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK) // Sunday = 1
        val daysSinceMonday = (dayOfWeek + 5) % 7
        calendar.add(java.util.Calendar.DAY_OF_MONTH, -daysSinceMonday)
        truncateToDay(calendar)
        return calendar.timeInMillis
    }

    /** Month boundary (UTC) for a millis timestamp. */
    fun monthStartMillis(millis: Long, dayMillis: Long = DAY_MILLIS): Long {
        val calendar = utcCalendar(millis)
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        truncateToDay(calendar)
        return calendar.timeInMillis
    }

    /** Year boundary (UTC) for a millis timestamp. */
    fun yearStartMillis(millis: Long): Long {
        val calendar = utcCalendar(millis)
        calendar.set(java.util.Calendar.DAY_OF_YEAR, 1)
        truncateToDay(calendar)
        return calendar.timeInMillis
    }

    private fun utcCalendar(millis: Long): java.util.GregorianCalendar =
        java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = millis
        }

    private fun truncateToDay(calendar: java.util.GregorianCalendar) {
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
    }
}
