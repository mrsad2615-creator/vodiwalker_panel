package com.neurox.domain

import java.time.DayOfWeek
import java.time.LocalTime

data class Performance(val difficulty: Int, val accuracy: Double, val responseMs: Double?, val deviationMs: Double?, val valid: Boolean, val baseline: Boolean = false)
enum class AdaptationReason { DISABLED, INSUFFICIENT_HISTORY, STABLE_SUCCESS, REPEATED_ERRORS, MAINTAIN }
data class Recommendation(val difficulty: Int, val rounds: Int, val reason: AdaptationReason)
object Adaptive {
    /** History must be newest first, for one profile and one exercise only. */
    fun recommend(current: Int, history: List<Performance>, enabled: Boolean = true): Recommendation {
        require(current in 1..5)
        if (!enabled) return Recommendation(current, 5, AdaptationReason.DISABLED)
        val recent = history.filter { it.valid && !it.baseline && it.difficulty == current && it.accuracy.isFinite() && it.accuracy in 0.0..1.0 }.take(5)
        if (recent.size < 3) return Recommendation(current, 5, AdaptationReason.INSUFFICIENT_HISTORY)
        if (recent.take(3).all { it.accuracy < 0.65 }) return Recommendation((current - 1).coerceAtLeast(1), 3, AdaptationReason.REPEATED_ERRORS)
        val stable = recent.all { p ->
            val mean = p.responseMs
            val deviation = p.deviationMs
            mean == null || (mean > 0 && deviation != null && deviation >= 0 && deviation / mean <= 0.25)
        }
        if (recent.size == 5 && recent.all { it.accuracy >= 0.9 } && stable && current < 5) {
            return Recommendation(current + 1, 5, AdaptationReason.STABLE_SUCCESS)
        }
        return Recommendation(current, 5, AdaptationReason.MAINTAIN)
    }
}

data class PlanSlot(val days: Set<DayOfWeek>, val start: LocalTime, val durationMinutes: Int, val restMinutes: Int) {
    fun validate(): Boolean = days.isNotEmpty() && durationMinutes in 1..120 && restMinutes in 0..60 &&
        start.toSecondOfDay() / 60 + durationMinutes + restMinutes <= 1440
    fun overlaps(other: PlanSlot): Boolean {
        require(validate() && other.validate())
        if (days.intersect(other.days).isEmpty()) return false
        val a = start.toSecondOfDay() / 60
        val b = other.start.toSecondOfDay() / 60
        return a < b + other.durationMinutes + other.restMinutes && b < a + durationMinutes + restMinutes
    }
}

object Progression {
    fun level(xp: Long): Long { require(xp >= 0); return xp / 250 + 1 }
    fun progressInLevel(xp: Long): Float { require(xp >= 0); return (xp % 250) / 250f }
}
