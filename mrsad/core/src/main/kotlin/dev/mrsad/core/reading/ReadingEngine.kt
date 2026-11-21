package dev.mrsad.core.reading

import dev.mrsad.core.text.PersianText
import kotlin.math.roundToInt

/** One display unit in an RSVP / phrase reading session. */
data class ReadingChunk(
    val index: Int,
    val text: String,
    val wordCount: Int,
    /** Index inside the chunk to highlight as the optimal recognition point. */
    val pivotIndex: Int,
)

/**
 * Reading presentation engine. Pure functions only — the session timer lives
 * in the app layer, driven by real elapsed time (not accumulated frame time),
 * so a backgrounded app cannot distort measured reading speed.
 */
object ReadingEngine {

    /** Splits text into word-level chunks of [wordsPerDisplay] words. */
    fun chunk(text: String, wordsPerDisplay: Int = 1): List<ReadingChunk> {
        val normalized = PersianText.normalizeForDisplay(text)
        val words = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val per = wordsPerDisplay.coerceIn(1, 6)
        val chunks = ArrayList<ReadingChunk>(words.size / per + 1)
        var i = 0
        while (i < words.size) {
            val end = minOf(i + per, words.size)
            val chunkText = words.subList(i, end).joinToString(" ")
            chunks += ReadingChunk(
                index = chunks.size,
                text = chunkText,
                wordCount = end - i,
                pivotIndex = pivotFor(chunkText),
            )
            i = end
        }
        return chunks
    }

    /** Optimal recognition point: ~1/3 into the word, clamped to a letter. */
    fun pivotFor(text: String): Int {
        if (text.isEmpty()) return 0
        var idx = (text.length * 0.35).roundToInt().coerceIn(0, text.lastIndex)
        repeat(text.length) {
            if (idx > text.lastIndex) idx = text.lastIndex
            if (!text[idx].isWhitespace()) return idx
            idx++
        }
        return 0
    }

    /**
     * Milliseconds a chunk must stay visible so that the reader advances at
     * [wpm] words per minute. Phrase chunks scale with their word count.
     */
    fun chunkDurationMillis(wordsPerMinute: Int, wordCount: Int): Long {
        val wpm = wordsPerMinute.coerceIn(60, 2000)
        return (wordCount * 60_000.0 / wpm).roundToInt().toLong().coerceAtLeast(16L)
    }

    /**
     * Effective reading speed for a finished session. Only meaningful when a
     * comprehension score exists, so speed is never reported on its own.
     */
    fun wordsPerMinute(wordsRead: Int, elapsedMillis: Long): Int =
        if (elapsedMillis <= 0) 0 else (wordsRead * 60_000.0 / elapsedMillis).roundToInt()

    /**
     * Blended "effective speed": words per minute at the achieved comprehension
     * level. score 0..100. This deliberately rewards comprehension, not raw speed.
     */
    fun effectiveWpm(wordsRead: Int, elapsedMillis: Long, comprehensionPercent: Int): Int {
        val raw = wordsPerMinute(wordsRead, elapsedMillis)
        val factor = comprehensionPercent.coerceIn(0, 100) / 100.0
        return (raw * factor).roundToInt()
    }

    data class ComprehensionQuestion(
        val prompt: String,
        val options: List<String>,
        val correctIndex: Int,
    )

    data class ComprehensionResult(
        val correct: Int,
        val total: Int,
    ) {
        val percent: Int get() = if (total == 0) 0 else correct * 100 / total
        val passed: Boolean get() = total > 0 && percent >= 60
    }

    fun score(answers: List<Int?>, questions: List<ComprehensionQuestion>): ComprehensionResult {
        var correct = 0
        for (i in questions.indices) {
            if (answers.getOrNull(i) == questions[i].correctIndex) correct++
        }
        return ComprehensionResult(correct, questions.size)
    }
}
