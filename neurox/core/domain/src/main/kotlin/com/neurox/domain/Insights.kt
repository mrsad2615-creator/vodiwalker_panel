package com.neurox.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Profile-owned stored aggregates, not a new source of rewards or synthetic measurements. */
data class TrainingFact(
    val id: String,
    val exercise: Exercise,
    val startedAt: Long,
    val durationMs: Long,
    val accuracy: Double,
    val valid: Boolean,
    val completed: Boolean,
    val baseline: Boolean = false,
)
data class DailyActivity(val date: LocalDate, val sessions: Int, val activeMs: Long)
data class HistoryInsights(
    val sessions: Int,
    val activeMs: Long,
    val meanSessionAccuracy: Double?,
    val streak: Int,
    val days: List<DailyActivity>,
)
data class MilestoneProgress(val id: String, val current: Long, val target: Long, val earnedAt: Long?) {
    val unlocked: Boolean get() = earnedAt != null
    val fraction: Float get() = (current.toDouble() / target).coerceIn(0.0, 1.0).toFloat()
}

object TrainingInsights {
    private fun measured(records: List<TrainingFact>, nowMs: Long, includeBaselines: Boolean = false) = records
        .filter { it.valid && it.completed && (includeBaselines || !it.baseline) && it.startedAt in 0..nowMs &&
            it.durationMs in 1_000..86_400_000 && it.accuracy.isFinite() && it.accuracy in 0.0..1.0 }
        .distinctBy { it.id }

    fun summarize(records: List<TrainingFact>, nowMs: Long, zone: ZoneId, days: Int = 7, module: Module? = null, includeBaselines: Boolean = false): HistoryInsights {
        require(nowMs >= 0 && days in 1..3660)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val values = measured(records, nowMs, includeBaselines).filter { module == null || it.exercise.module == module }
        val grouped = values.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        var cursor = if (grouped.containsKey(today)) today else today.minusDays(1)
        var streak = 0
        while (grouped.containsKey(cursor)) { streak++; cursor = cursor.minusDays(1) }
        val timeline = (days - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val sessions = grouped[date].orEmpty()
            DailyActivity(date, sessions.size, sessions.sumOf { it.durationMs })
        }
        val start = timeline.first().date.atStartOfDay(zone).toInstant().toEpochMilli()
        val selected = values.filter { it.startedAt >= start }
        return HistoryInsights(selected.size, selected.sumOf { it.durationMs }, selected.takeIf { it.isNotEmpty() }?.map { it.accuracy }?.average(), streak, timeline)
    }

    /** Computed achievements do not mutate XP. Deleted profiles retain no achievements. */
    fun milestones(records: List<TrainingFact>, nowMs: Long, zone: ZoneId): List<MilestoneProgress> {
        require(nowMs >= 0)
        val values = measured(records, nowMs).sortedWith(compareBy<TrainingFact> { it.startedAt }.thenBy { it.id })
        val definitions = listOf("first_session" to 1L, "four_systems" to 4L, "ten_sessions" to 10L,
            "seven_days" to 7L, "one_hour" to 3_600_000L, "careful_practice" to 5L)
        val modules = mutableSetOf<Module>()
        val dates = mutableSetOf<LocalDate>()
        var activeMs = 0L
        var accurate = 0L
        val earned = mutableMapOf<String, Long>()
        var progress = listOf(0L, 0L, 0L, 0L, 0L, 0L)
        values.forEachIndexed { index, fact ->
            modules += fact.exercise.module
            dates += Instant.ofEpochMilli(fact.startedAt).atZone(zone).toLocalDate()
            activeMs += fact.durationMs
            if (fact.accuracy >= 0.95) accurate++
            progress = listOf(index + 1L, modules.size.toLong(), index + 1L, dates.size.toLong(), activeMs, accurate)
            definitions.forEachIndexed { i, (id, target) ->
                if (progress[i] >= target && id !in earned) earned[id] = fact.startedAt
            }
        }
        return definitions.mapIndexed { index, (id, target) -> MilestoneProgress(id, progress[index], target, earned[id]) }
    }

    /** Prefer an under-practiced built-in task. No efficacy or recovery estimate is implied. */
    fun nextExercise(records: List<TrainingFact>, nowMs: Long, module: Module): Exercise {
        val counts = measured(records, nowMs).groupingBy { it.exercise }.eachCount()
        return Catalogue.runnable.filter { it.exercise.module == module }.minBy { counts[it.exercise] ?: 0 }.exercise
    }
}
