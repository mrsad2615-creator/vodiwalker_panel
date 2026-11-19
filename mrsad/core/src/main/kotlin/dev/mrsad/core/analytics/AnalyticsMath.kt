package dev.mrsad.core.analytics

import kotlin.math.abs

/** One day of aggregated training activity — the unit analytics operate on. */
data class DayActivity(
    /** ISO date, "yyyy-MM-dd" (local time). */
    val date: String,
    val typingSessions: Int = 0,
    val typingWpm: Double = 0.0,
    val typingAccuracy: Double = 0.0,
    val readingSessions: Int = 0,
    val readingWpm: Int = 0,
    val readingComprehension: Int = 0,
    val mindSessions: Int = 0,
    val mindScore: Double = 0.0,
    val focusMinutes: Int = 0,
    val trainingMinutes: Int = 0,
) {
    val hasActivity: Boolean
        get() = typingSessions + readingSessions + mindSessions > 0 || focusMinutes > 0
}

data class TrendSummary(
    val slopePerDay: Double,
    val direction: Direction,
    val firstValue: Double,
    val lastValue: Double,
) {
    enum class Direction { UP, DOWN, FLAT }
}

object AnalyticsMath {

    /** Simple linear-regression slope over equally spaced points (oldest → newest). */
    fun trend(values: List<Double>): TrendSummary {
        if (values.isEmpty()) return TrendSummary(0.0, TrendSummary.Direction.FLAT, 0.0, 0.0)
        if (values.size == 1) {
            return TrendSummary(0.0, TrendSummary.Direction.FLAT, values[0], values[0])
        }
        val n = values.size
        val meanX = (n - 1) / 2.0
        val meanY = values.average()
        var num = 0.0
        var den = 0.0
        values.forEachIndexed { i, y ->
            val dx = i - meanX
            num += dx * (y - meanY)
            den += dx * dx
        }
        val slope = if (den == 0.0) 0.0 else num / den
        val first = values.first()
        val last = values.last()
        val threshold = maxOf(0.05, abs(meanY) * 0.02)
        val direction = when {
            slope > threshold -> TrendSummary.Direction.UP
            slope < -threshold -> TrendSummary.Direction.DOWN
            else -> TrendSummary.Direction.FLAT
        }
        return TrendSummary(slope, direction, first, last)
    }

    /**
     * Current consecutive-day streak counting backwards from [today].
     * A day without activity breaks the streak; today may still be empty.
     */
    fun currentStreak(activeDates: Set<String>, today: String): Int {
        var day = today
        var streak = 0
        // If today has no activity yet the streak is measured from yesterday.
        if (day !in activeDates) day = previousIso(day)
        while (day in activeDates) {
            streak++
            day = previousIso(day)
        }
        return streak
    }

    fun longestStreak(activeDates: Set<String>): Int {
        if (activeDates.isEmpty()) return 0
        var best = 0
        var run = 0
        var previous: String? = null
        for (date in activeDates.sorted()) {
            run = if (previous != null && nextIso(previous) == date) run + 1 else 1
            best = maxOf(best, run)
            previous = date
        }
        return best
    }

    /** Percent change between two values, safe for zero baselines. */
    fun percentChange(previous: Double, current: Double): Double =
        if (previous == 0.0) (if (current == 0.0) 0.0 else 100.0)
        else (current - previous) / previous * 100.0

    fun average(values: List<Double>): Double = if (values.isEmpty()) 0.0 else values.average()

    /** Weighted average where zero-weight entries are ignored. */
    fun weightedAverage(values: List<Double>, weights: List<Int>): Double {
        require(values.size == weights.size) { "values and weights must align" }
        var sum = 0.0
        var wsum = 0
        values.forEachIndexed { i, v ->
            val w = weights[i]
            if (w > 0) { sum += v * w; wsum += w }
        }
        return if (wsum == 0) 0.0 else sum / wsum
    }

    // --- Minimal ISO date arithmetic (no java.time, keeps the core pure) ---

    fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> error("invalid month $month")
    }

    fun nextIso(date: String): String {
        var (y, m, d) = parseIso(date)
        d++
        if (d > daysInMonth(y, m)) {
            d = 1; m++
            if (m > 12) { m = 1; y++ }
        }
        return formatIso(y, m, d)
    }

    fun previousIso(date: String): String {
        var (y, m, d) = parseIso(date)
        d--
        if (d == 0) {
            m--
            if (m == 0) { m = 12; y-- }
            d = daysInMonth(y, m)
        }
        return formatIso(y, m, d)
    }

    /** Dates for the last [days] days ending at [endDate] (oldest first). */
    fun lastDays(endDate: String, days: Int): List<String> {
        require(days > 0)
        val out = ArrayList<String>(days)
        var cursor = endDate
        repeat(days) {
            out += cursor
            cursor = previousIso(cursor)
        }
        return out.asReversed()
    }

    private fun parseIso(date: String): Triple<Int, Int, Int> {
        val parts = date.split('-')
        require(parts.size == 3) { "expected yyyy-MM-dd, got $date" }
        return Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    }

    private fun formatIso(y: Int, m: Int, d: Int): String =
        "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}
