package com.neurox.domain.scoring

import com.neurox.domain.model.SessionRecord
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure scoring and statistics used by every engine.
 *
 * DESIGN RULES
 * 1. All functions are pure and deterministic.
 * 2. Accuracy weighs misses and errors; a fast but careless session cannot score
 *    higher than a correct and steady one of equal speed.
 * 3. Speed is normalized against the exercise's reference band so different
 *    exercises remain comparable within their own module only.
 */
object Scoring {

    /** Weight of correctness in the composite score. */
    const val ACCURACY_WEIGHT = 0.65

    /** Weight of speed in the composite score. */
    const val SPEED_WEIGHT = 0.35

    /**
     * Accuracy in 0..1.
     *
     * [correct] successes, [incorrect] wrong or premature answers, [missed]
     * targets that were never answered.
     */
    fun accuracy(correct: Int, incorrect: Int, missed: Int): Double {
        require(correct >= 0 && incorrect >= 0 && missed >= 0)
        val attempts = correct + incorrect + missed
        if (attempts == 0) return 0.0
        return correct.toDouble() / attempts.toDouble()
    }

    /**
     * Speed factor in 0..1 for a measured response time.
     *
     * [referenceMillis] is the "good" response time for the exercise and
     * difficulty. Times at or below it are excellent; four times slower is zero.
     */
    fun speedFactor(meanResponseMillis: Double, referenceMillis: Double): Double {
        require(referenceMillis > 0.0) { "referenceMillis must be positive" }
        if (meanResponseMillis <= 0.0) return 0.0
        val ceiling = referenceMillis * 4.0
        val clamped = meanResponseMillis.coerceIn(referenceMillis, ceiling)
        return 1.0 - (clamped - referenceMillis) / (ceiling - referenceMillis)
    }

    /**
     * Composite 0..100 score for one session.
     *
     * [completion] in 0..1 lets partial sessions score proportionally lower.
     *
     * SPEED IS GATED BY CORRECTNESS: the speed credit is multiplied by accuracy, so
     * a fast session with wrong answers cannot outscore a correct session. A
     * session with zero accuracy scores zero, whatever its reaction times.
     */
    fun compositeScore(
        accuracy: Double,
        speed: Double,
        completion: Double,
    ): Double {
        require(accuracy in 0.0..1.0) { "accuracy out of range" }
        require(speed in 0.0..1.0) { "speed out of range" }
        require(completion in 0.0..1.0) { "completion out of range" }
        val creditedSpeed = speed * accuracy
        val raw = ACCURACY_WEIGHT * accuracy + SPEED_WEIGHT * creditedSpeed
        return (raw * completion * 100.0).coerceIn(0.0, 100.0)
    }

    /**
     * Difficulty multiplier for score normalization. Harder levels earn more
     * score for the same accuracy and speed, with diminishing extra credit.
     */
    fun difficultyMultiplier(difficulty: Int): Double = when (difficulty.coerceIn(1, 5)) {
        1 -> 0.80
        2 -> 0.90
        3 -> 1.00
        4 -> 1.10
        else -> 1.20
    }

    /** Mean of a non-empty sample. Returns 0.0 for an empty sample. */
    fun mean(values: List<Double>): Double =
        if (values.isEmpty()) 0.0 else values.sum() / values.size

    /** Median of a sample. Returns 0.0 for an empty sample. */
    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }

    /** Population standard deviation. Returns 0.0 for fewer than two values. */
    fun stdev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val m = mean(values)
        val variance = values.sumOf { v -> (v - m) * (v - m) } / values.size.toDouble()
        return sqrt(variance)
    }

    /**
     * Consistency in 0..1. Lower relative spread means higher consistency.
     * A relative spread of 0.5 (stdev is half of the mean) maps to 0.
     */
    fun consistency(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val m = mean(values)
        if (m <= 0.0) return 0.0
        val relative = stdev(values) / m
        return (1.0 - relative / 0.5).coerceIn(0.0, 1.0)
    }

    /**
     * Robust trend of a series measured in samples, oldest first.
     *
     * Returns a slope-like value in the units of the samples per sample index.
     * Uses the difference of the first and last thirds to resist noise.
     */
    fun trend(values: List<Double>): Double {
        if (values.size < 3) return 0.0
        val third = (values.size / 3).coerceAtLeast(1)
        val head = mean(values.take(third))
        val tail = mean(values.takeLast(third))
        return tail - head
    }

    /** True when the series changes in a way that justifies an adaptation. */
    fun isMaterialChange(trend: Double, threshold: Double): Boolean =
        abs(trend) >= threshold && threshold > 0.0

    /**
     * Personal-record comparison.
     *
     * A record is only replaced when the new score is strictly greater. Equal
     * scores never replace records, so duplicate saves cannot inflate history.
     */
    fun isNewRecord(previousBest: Double?, candidate: Double): Boolean =
        previousBest == null || candidate > previousBest

    /** Total XP for a session; invalid (abandoned or empty) sessions earn zero. */
    fun xpFor(record: SessionRecord): Int {
        if (record.status != com.neurox.domain.model.SessionStatus.COMPLETED) return 0
        if (record.totalResponses == 0) return 0
        if (record.activeDurationMillis < MIN_XP_DURATION_MILLIS) return 0
        val base = record.score * XP_PER_SCORE_POINT
        val difficultyBonus = (record.difficulty - 3) * XP_DIFFICULTY_BONUS
        val accuracyBonus = if (record.accuracy >= 0.95) XP_ACCURACY_BONUS else 0.0
        val total = base + difficultyBonus + accuracyBonus
        return total.toInt().coerceIn(0, MAX_XP_PER_SESSION)
    }

    const val XP_PER_SCORE_POINT = 1.5
    const val XP_DIFFICULTY_BONUS = 8.0
    const val XP_ACCURACY_BONUS = 12.0
    const val MAX_XP_PER_SESSION = 400
    const val MIN_XP_DURATION_MILLIS = 5_000L
}

/**
 * Reference bands per module, expressed in milliseconds at difficulty 3.
 * Difficulty 1 is roughly 1.35x slower, difficulty 5 roughly 0.72x faster.
 *
 * These are product calibration values, not clinical norms.
 */
object ReferenceBands {
    const val VISUAL_REACTION_MILLIS = 420.0
    const val READING_STEP_MILLIS = 900.0
    const val TYPING_KEY_MILLIS = 260.0
    const val VISION_RESPONSE_MILLIS = 1100.0

    fun forDifficulty(base: Double, difficulty: Int): Double {
        val factor = when (difficulty.coerceIn(1, 5)) {
            1 -> 1.35
            2 -> 1.15
            3 -> 1.00
            4 -> 0.86
            else -> 0.72
        }
        return base * factor
    }
}

/** Words-per-minute definition used by the READ and TYPE engines. */
object Wpm {
    /** The single WPM definition of NEUROX: five characters equal one word. */
    const val CHARS_PER_WORD = 5.0

    /**
     * Gross WPM for typed or read text.
     *
     * [characters] is the count of characters in the text that was processed.
     * Time is active typing/reading time; paused time must be excluded.
     */
    fun fromCharacters(characters: Int, activeMillis: Long): Double {
        require(characters >= 0)
        require(activeMillis >= 0)
        if (activeMillis < MIN_MEASURED_MILLIS || characters == 0) return 0.0
        val minutes = activeMillis / 60_000.0
        return (characters / CHARS_PER_WORD) / minutes
    }

    /** Word-based WPM for reading content where word count is known exactly. */
    fun fromWords(words: Int, activeMillis: Long): Double {
        require(words >= 0)
        require(activeMillis >= 0)
        if (activeMillis < MIN_MEASURED_MILLIS || words == 0) return 0.0
        return words / (activeMillis / 60_000.0)
    }

    /**
     * Net WPM applies an error penalty so speed cannot hide mistakes.
     * [errorRatio] in 0..1 is incorrect characters divided by typed characters.
     */
    fun net(gross: Double, errorRatio: Double): Double {
        require(errorRatio in 0.0..1.0) { "errorRatio out of range" }
        return (gross * (1.0 - errorRatio)).coerceAtLeast(0.0)
    }

    const val MIN_MEASURED_MILLIS = 3_000L
}
