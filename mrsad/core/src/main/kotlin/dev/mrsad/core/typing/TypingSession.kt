package dev.mrsad.core.typing

import dev.mrsad.core.text.PersianText

enum class TypingLanguage { PERSIAN, ENGLISH }

/**
 * Immutable result of one completed typing session.
 *
 * Metric definitions (applied identically in the trainer and in analytics):
 * - [cpm]       = correct characters ÷ elapsed minutes
 * - [wpm]       = (correct characters ÷ 5) ÷ elapsed minutes  (standard 5-char word, language-neutral)
 * - [wordWpm]   = completed correct words ÷ elapsed minutes   (word boundary based, Persian-aware)
 * - [accuracy]  = correct keystrokes ÷ total keystrokes × 100  (keystrokes never decrease on backspace)
 * - [errorRate] = incorrect keystrokes ÷ total keystrokes × 100
 */
data class TypingResult(
    val language: TypingLanguage,
    val mode: String,
    val targetText: String,
    val correctChars: Int,
    val totalKeystrokes: Int,
    val errorKeystrokes: Int,
    val correctWords: Int,
    val elapsedMillis: Long,
    val completed: Boolean,
    val errorHeatmap: Map<Char, Int>,
    val startedAtEpochMillis: Long,
) {
    val cpm: Double get() = perMinute(correctChars.toDouble())
    val wpm: Double get() = perMinute(correctChars / 5.0)
    val wordWpm: Double get() = perMinute(correctWords.toDouble())
    val accuracy: Double
        get() = if (totalKeystrokes == 0) 0.0 else correctChars.coerceAtMost(totalKeystrokes) * 100.0 / totalKeystrokes
    val errorRate: Double
        get() = if (totalKeystrokes == 0) 0.0 else errorKeystrokes * 100.0 / totalKeystrokes

    private fun perMinute(value: Double): Double =
        if (elapsedMillis <= 0L) 0.0 else value * 60_000.0 / elapsedMillis

    /** Most frequently mistyped expected characters, strongest first. */
    fun topMistakes(limit: Int = 5): List<Pair<Char, Int>> =
        errorHeatmap.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }
}

/**
 * A deterministic typing session state machine. UI-free and clock-free: the
 * caller supplies the current timestamp for every key event, which keeps the
 * engine fully unit-testable and makes "pause" trivial to reason about.
 *
 * Rules enforced here (so the UI cannot get them wrong):
 * - The first character typed starts the clock.
 * - Wrong keys never advance the cursor (standard typing-trainer behaviour).
 * - Backspace removes the last correct character; historical errors and total
 *   keystrokes are never erased (accuracy is honest).
 * - Space is accepted for a ZWNJ target character.
 * - The session completes when the whole target is typed.
 */
class TypingSession(
    val targetText: String,
    val language: TypingLanguage,
    val mode: String,
    val startedAtEpochMillis: Long,
    private val acceptSpaceForZwnj: Boolean = true,
) {
    private val target: String = PersianText.normalizeForComparison(targetText)

    var cursor: Int = 0
        private set
    var correctChars: Int = 0
        private set
    var totalKeystrokes: Int = 0
        private set
    var errorKeystrokes: Int = 0
        private set
    var correctWords: Int = 0
        private set
    private val errorHeatmap = LinkedHashMap<Char, Int>()

    var firstKeyAtMillis: Long? = null
        private set
    var finishedAtMillis: Long? = null
        private set

    val isFinished: Boolean get() = cursor >= target.length && target.isNotEmpty()
    val progress: Float
        get() = if (target.isEmpty()) 0f else cursor.toFloat() / target.length

    val expectedChar: Char? get() = target.getOrNull(cursor)

    /** Elapsed *typing* time in ms; zero until the first keystroke. */
    fun elapsedMillis(nowMillis: Long): Long {
        val start = firstKeyAtMillis ?: return 0L
        val end = finishedAtMillis ?: nowMillis
        return (end - start).coerceAtLeast(0L)
    }

    fun type(ch: Char, nowMillis: Long): KeyOutcome {
        if (isFinished) return KeyOutcome.IGNORED
        if (target.isEmpty()) return KeyOutcome.IGNORED
        if (firstKeyAtMillis == null) firstKeyAtMillis = nowMillis
        totalKeystrokes++

        val expected = target[cursor]
        val accepted = PersianText.matches(ch, expected, acceptSpaceForZwnj)
        if (!accepted) {
            errorKeystrokes++
            errorHeatmap.merge(expected, 1, Int::plus)
            return KeyOutcome.INCORRECT
        }

        val finishedWord = (expected == ' ' || cursor == target.lastIndex)
        cursor++
        correctChars++
        if (finishedWord) correctWords++
        if (isFinished) finishedAtMillis = nowMillis
        return KeyOutcome.CORRECT
    }

    /** Removes the last correctly typed character. Returns false when nothing to undo. */
    fun backspace(): Boolean {
        if (isFinished) return false
        if (cursor == 0) return false
        val removed = target[cursor - 1]
        cursor--
        correctChars--
        if (removed == ' ' || cursor == target.lastIndex) {
            correctWords = (correctWords - 1).coerceAtLeast(0)
        }
        return true
    }

    /** How many times [expected] was mistyped (for tests / error analysis UI). */
    fun mistakeCount(expected: Char): Int = errorHeatmap[expected] ?: 0

    fun liveWpm(nowMillis: Long): Double = liveSnapshot(nowMillis).wpm

    fun liveSnapshot(nowMillis: Long): LiveTypingSnapshot = LiveTypingSnapshot(
        cpm = perMinute(correctChars.toDouble(), elapsedMillis(nowMillis)),
        wpm = perMinute(correctChars / 5.0, elapsedMillis(nowMillis)),
        accuracy = if (totalKeystrokes == 0) 100.0
        else correctChars.coerceAtMost(totalKeystrokes) * 100.0 / totalKeystrokes,
        errors = errorKeystrokes,
        progress = progress,
        cursor = cursor,
    )

    fun result(nowMillis: Long, completed: Boolean = isFinished): TypingResult = TypingResult(
        language = language,
        mode = mode,
        targetText = targetText,
        correctChars = correctChars,
        totalKeystrokes = totalKeystrokes,
        errorKeystrokes = errorKeystrokes,
        correctWords = correctWords,
        elapsedMillis = elapsedMillis(nowMillis),
        completed = completed,
        errorHeatmap = errorHeatmap.toMap(),
        startedAtEpochMillis = startedAtEpochMillis,
    )

    private fun perMinute(value: Double, elapsed: Long): Double =
        if (elapsed <= 0L) 0.0 else value * 60_000.0 / elapsed

    enum class KeyOutcome { CORRECT, INCORRECT, IGNORED }
}

data class LiveTypingSnapshot(
    val cpm: Double,
    val wpm: Double,
    val accuracy: Double,
    val errors: Int,
    val progress: Float,
    val cursor: Int,
)

private fun LiveTypingSnapshot.wpm(@Suppress("UNUSED_PARAMETER") session: TypingSession): Double = wpm
