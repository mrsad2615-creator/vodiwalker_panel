package com.neurox.games.metrics

import com.neurox.domain.scoring.Wpm
import com.neurox.games.definition.TextMetrics
import com.neurox.games.definition.TextNormalizer

/**
 * Complete measurement of one typing session.
 *
 * ONE DEFINITION: NEUROX reports WPM as five characters per word, gross and net,
 * for both English and Persian. The numbers are comparable between languages.
 */
data class TypingMetrics(
    val expectedCharacters: Int,
    val typedCharacters: Int,
    val correctCharacters: Int,
    val incorrectCharacters: Int,
    val grossWpm: Double,
    val netWpm: Double,
    val accuracy: Double,
    val activeMillis: Long,
    val weakKeys: Map<Char, Int>,
    /** Consistency of inter-key intervals in 0..1; zero when not measured. */
    val rhythmConsistency: Double,
    /** Mean interval between keystrokes, or zero when not measured. */
    val meanIntervalMillis: Double,
) {
    companion object {

        /**
         * Computes metrics for a finished text.
         *
         * [keystrokeNanos] holds the monotonic time of each accepted character,
         * so rhythm and speed come from the same source and cannot disagree.
         */
        fun compute(
            expected: String,
            typed: String,
            activeMillis: Long,
            keystrokeNanos: List<Long> = emptyList(),
        ): TypingMetrics {
            val correct = TextMetrics.correctCharacters(expected, typed)
            val incorrect = TextMetrics.incorrectCharacters(expected, typed)
            val accuracy = if (typed.isEmpty()) 0.0 else {
                correct.toDouble() / typed.length.toDouble()
            }
            val gross = Wpm.fromCharacters(typed.length, activeMillis)
            val net = Wpm.net(gross, TextMetrics.errorRatio(expected, typed))
            val intervals = interKeyIntervals(keystrokeNanos)
            return TypingMetrics(
                expectedCharacters = expected.length,
                typedCharacters = typed.length,
                correctCharacters = correct,
                incorrectCharacters = incorrect,
                grossWpm = gross,
                netWpm = net,
                accuracy = accuracy.coerceIn(0.0, 1.0),
                activeMillis = activeMillis,
                weakKeys = TextMetrics.weakKeys(expected, typed),
                rhythmConsistency = consistency(intervals),
                meanIntervalMillis = if (intervals.isEmpty()) 0.0 else intervals.average(),
            )
        }

        /** Intervals in milliseconds between consecutive keystrokes. */
        fun interKeyIntervals(keystrokeNanos: List<Long>): List<Double> {
            if (keystrokeNanos.size < 2) return emptyList()
            return keystrokeNanos.zipWithNext { a, b -> (b - a) / 1_000_000.0 }
                .filter { it >= 0.0 }
        }

        /**
         * Rhythm consistency: 1 minus the coefficient of variation, clamped.
         * A perfectly even rhythm is 1; a wildly uneven rhythm is 0.
         */
        fun consistency(intervals: List<Double>): Double {
            if (intervals.size < 3) return 0.0
            val mean = intervals.average()
            if (mean <= 0.0) return 0.0
            val variance = intervals.sumOf { (it - mean) * (it - mean) } / intervals.size
            val cv = kotlin.math.sqrt(variance) / mean
            return (1.0 - cv).coerceIn(0.0, 1.0)
        }

        /**
         * Ranks the weakest keys from several sessions.
         * Only keys with at least one recorded error appear.
         */
        fun weakestKeys(sessions: List<TypingMetrics>, limit: Int = 5): List<Pair<Char, Int>> =
            sessions
                .flatMap { it.weakKeys.entries }
                .groupBy({ it.key }, { it.value })
                .map { (key, values) -> key to values.sum() }
                .sortedWith(compareByDescending<Pair<Char, Int>> { it.second }.thenBy { it.first })
                .take(limit)

        /** Builds a drill text from weak keys, respecting the language. */
        fun drillText(weakKeys: List<Char>, groups: Int = 6, seed: Long = 0L): String {
            if (weakKeys.isEmpty()) return ""
            val random = kotlin.random.Random(seed)
            return (0 until groups).joinToString(" ") {
                weakKeys.shuffled(random).joinToString("")
            }
        }
    }
}

/**
 * A live typing session: accepts characters, rejects overflow, and records
 * monotonic keystroke times.
 */
class TypingSessionCore(
    val expectedText: String,
    private val clock: () -> Long,
) {
    private val keystrokeNanos = mutableListOf<Long>()
    private var typedBuffer = StringBuilder()
    private var startedAtNanos: Long? = null

    val typed: String get() = typedBuffer.toString()
    val isComplete: Boolean get() = typedBuffer.length >= expectedText.length

    /** Position of the next expected character. */
    val cursor: Int get() = typedBuffer.length

    val nextExpected: Char? get() = expectedText.getOrNull(cursor)

    /**
     * Accepts one character. Returns true when it matched the expected character.
     * Extra characters beyond the text are rejected, so a fast typist cannot
     * inflate the count.
     */
    fun type(character: Char): Boolean {
        if (isComplete) return false
        if (startedAtNanos == null) startedAtNanos = clock()
        typedBuffer.append(character)
        keystrokeNanos += clock()
        val expected = expectedText[cursor - 1]
        return TextNormalizer.charactersEqual(expected, character)
    }

    /** Removes the last character. Backspace is part of real typing. */
    fun backspace(): Boolean {
        if (typedBuffer.isEmpty()) return false
        typedBuffer.deleteCharAt(typedBuffer.length - 1)
        if (keystrokeNanos.isNotEmpty()) keystrokeNanos.removeAt(keystrokeNanos.size - 1)
        return true
    }

    /** Elapsed time since the first keystroke, using monotonic time only. */
    fun activeMillis(): Long {
        val start = startedAtNanos ?: return 0L
        return ((clock() - start) / 1_000_000L).coerceAtLeast(0L)
    }

    fun metrics(): TypingMetrics = TypingMetrics.compute(
        expected = expectedText,
        typed = typedBuffer.toString(),
        activeMillis = activeMillis(),
        keystrokeNanos = keystrokeNanos.toList(),
    )

    fun reset() {
        keystrokeNanos.clear()
        typedBuffer = StringBuilder()
        startedAtNanos = null
    }
}
