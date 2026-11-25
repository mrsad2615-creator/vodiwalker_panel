package com.neurox.games.read

import com.neurox.domain.engine.SessionSummary
import com.neurox.domain.scoring.Wpm
import com.neurox.games.content.ReadingText
import com.neurox.games.definition.TextNormalizer

/**
 * Reading Studio: the presentation and measurement rules of the reader.
 *
 * The studio is pure data plus pure functions. The Compose screen renders it and
 * forwards user commands; no measurement happens inside the UI.
 */
data class ReadingStudioSettings(
    val wordsPerMinute: Int = 240,
    val fontSizeSp: Int = 19,
    val fontFamilyId: String = "vazirmatn",
    val lineHeightMultiplier: Double = 1.6,
    val paragraphSpacingMultiplier: Double = 1.0,
    val rtl: Boolean = false,
    val focusGuide: FocusGuide = FocusGuide.NONE,
    val showProgress: Boolean = true,
) {
    init {
        require(wordsPerMinute in 60..900) { "wordsPerMinute must be in 60..900" }
        require(fontSizeSp in 12..40) { "fontSizeSp must be in 12..40" }
        require(lineHeightMultiplier in 1.0..2.6)
        require(paragraphSpacingMultiplier in 0.5..2.5)
    }

    /** Milliseconds each RSVP chunk stays visible at the configured rate. */
    fun stepMillis(): Int = (60_000.0 / wordsPerMinute.toDouble()).toInt().coerceIn(60, 2_000)

    companion object {
        /** Builds settings for a text, with the direction taken from the content. */
        fun forText(text: ReadingText): ReadingStudioSettings = ReadingStudioSettings(
            rtl = TextNormalizer.baseDirection(text.body) == com.neurox.games.definition.TextDirection.RTL,
        )

        /** Suggested reading rate for a difficulty: harder text, slower pace. */
        fun suggestedWpm(difficulty: Int): Int = (300 - (difficulty - 1) * 35).coerceIn(120, 420)
    }
}

/** Optional reading aids. */
enum class FocusGuide {
    NONE,
    LINE,
    SPOTLIGHT,
    MASK,
}

/** Playback state of the reader. Paused time never counts as reading time. */
data class ReadingPosition(
    val wordIndex: Int = 0,
    val paragraphIndex: Int = 0,
    val paused: Boolean = false,
    val elapsedActiveMillis: Long = 0,
) {
    init {
        require(wordIndex >= 0)
        require(paragraphIndex >= 0)
        require(elapsedActiveMillis >= 0)
    }
}

/**
 * A complete reading session over one text.
 *
 * [advance] moves through the RSVP chunks or through the guide; it returns the new
 * position. Pausing excludes time from the WPM denominator.
 */
class ReadingStudioSession(
    val text: ReadingText,
    val settings: ReadingStudioSettings,
    initial: ReadingPosition = ReadingPosition(),
) {
    private val words: List<String> = TextNormalizer.splitWords(text.body)

    val wordCount: Int get() = words.size

    var position: ReadingPosition = initial
        private set

    val progress: Double
        get() = if (wordCount == 0) 1.0 else (position.wordIndex.toDouble() / wordCount.toDouble()).coerceIn(0.0, 1.0)

    val currentWord: String? get() = words.getOrNull(position.wordIndex)

    /** Pivot index for the RSVP view, so the eye stays at one point. */
    val currentPivot: Int get() = TextNormalizer.pivotIndex(currentWord ?: "")

    val isComplete: Boolean get() = position.wordIndex >= wordCount

    /** Pause excludes time from the reading rate. */
    fun pause() {
        position = position.copy(paused = true)
    }

    fun resume() {
        position = position.copy(paused = false)
    }

    /**
     * Advances by [activeMillis] of reading time.
     * Returns false when the session is paused or already complete.
     */
    fun advance(activeMillis: Long = settings.stepMillis().toLong()): Boolean {
        if (position.paused || isComplete) return false
        val step = activeMillis.coerceAtLeast(0)
        position = position.copy(
            wordIndex = (position.wordIndex + 1).coerceAtMost(wordCount),
            elapsedActiveMillis = position.elapsedActiveMillis + step,
        )
        return true
    }

    /** Jumps to a word, used by the text navigation controls. */
    fun seek(wordIndex: Int) {
        position = position.copy(wordIndex = wordIndex.coerceIn(0, wordCount))
    }

    /** Effective reading rate for the progress already done. */
    fun currentWpm(): Double = Wpm.fromCharacters(
        characters = words.take(position.wordIndex).sumOf { it.length },
        activeMillis = position.elapsedActiveMillis,
    )

    /** Snapshot that the data layer can persist to resume later. */
    fun snapshot(): ReadingPosition = position
}

/**
 * Result of a reading session, ready for storage.
 *
 * COMPREHENSION FIRST: the effective score is the comprehension share multiplied
 * by a bounded speed factor, so reading fast without understanding scores low.
 */
data class ReadingResult(
    val textId: String,
    val wpm: Double,
    val grossWpm: Double,
    val comprehension: Double,
    val recall: Double,
    val activeMillis: Long,
    val wordsRead: Int,
    val completion: Double,
    val score: Double,
) {
    companion object {

        fun from(
            text: ReadingText,
            wordsRead: Int,
            activeMillis: Long,
            comprehensionAnswers: List<Boolean>,
            recallAnswers: List<Boolean>,
            targetWpm: Int,
        ): ReadingResult {
            val gross = Wpm.fromWords(wordsRead.coerceAtMost(text.wordCount), activeMillis)
            val comprehension = if (comprehensionAnswers.isEmpty()) 1.0 else {
                comprehensionAnswers.count { it }.toDouble() / comprehensionAnswers.size
            }
            val recall = if (recallAnswers.isEmpty()) 1.0 else {
                recallAnswers.count { it }.toDouble() / recallAnswers.size
            }
            val completion = if (text.wordCount == 0) 1.0 else {
                (wordsRead.toDouble() / text.wordCount).coerceIn(0.0, 1.0)
            }
            // Speed credit saturates at the target, so extreme speed cannot replace
            // understanding, and a very slow but full read still earns credit.
            val speedFactor = if (targetWpm <= 0) 0.0 else (gross / targetWpm.toDouble()).coerceIn(0.0, 1.0)
            val score = (
                100.0 * completion *
                    (0.5 * comprehension + 0.2 * recall + 0.3 * speedFactor)
                ).coerceIn(0.0, 100.0)
            return ReadingResult(
                textId = text.id,
                wpm = gross,
                grossWpm = gross,
                comprehension = comprehension,
                recall = recall,
                activeMillis = activeMillis,
                wordsRead = wordsRead,
                completion = completion,
                score = score,
            )
        }

        /** Builds a reading result from a finished engine summary. */
        fun fromSummary(
            text: ReadingText,
            summary: SessionSummary,
            targetWpm: Int,
        ): ReadingResult {
            val comprehensionAnswers = summary.correct.let { correct ->
                val attempts = summary.correct + summary.incorrect + summary.missed
                List(attempts) { index -> index < correct }
            }
            return from(
                text = text,
                wordsRead = text.wordCount,
                activeMillis = summary.activeDurationMillis,
                comprehensionAnswers = comprehensionAnswers,
                recallAnswers = emptyList(),
                targetWpm = targetWpm,
            )
        }
    }
}
