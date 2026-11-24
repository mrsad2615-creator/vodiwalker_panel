package ai.neurox.app.domain

import kotlin.math.roundToInt
import kotlin.random.Random

/** Stable domain types; no Android or UI dependencies. */
enum class Module(val title: String, val subtitle: String) {
    VISION("VISION", "Visual attention"), REFLEX("REFLEX", "Reaction and coordination"),
    READ("READ", "Reading performance"), TYPE("TYPE", "Typing precision")
}

data class Prompt(val instruction: String, val stimulus: String, val answer: String, val choices: List<String>)
data class TrialResult(val correct: Boolean, val latencyMs: Long)
data class SessionSummary(val module: Module, val correct: Int, val total: Int, val accuracyPercent: Int, val durationMs: Long)

/** In-memory session state. A round is counted once, and the session becomes immutable at completion. */
class TrainingRun(private val rounds: Int = 10) {
    init { require(rounds > 0) }
    private val results = mutableListOf<TrialResult>()
    val count: Int get() = results.size
    val correct: Int get() = results.count { it.correct }
    val completed: Boolean get() = count >= rounds
    var difficulty: Int = 1
        private set
    val history: List<TrialResult> get() = results.toList()

    fun record(correct: Boolean, latencyMs: Long): TrialResult {
        check(!completed) { "Session is already complete" }
        require(latencyMs >= 0) { "Latency must not be negative" }
        return TrialResult(correct, latencyMs).also { result ->
            results += result
            if (results.size % 5 == 0) difficulty = Scoring.nextDifficulty(difficulty, results.takeLast(5))
        }
    }

    fun summary(module: Module, durationMs: Long): SessionSummary {
        check(completed) { "An incomplete session has no final summary" }
        require(durationMs >= 0)
        return SessionSummary(module, correct, rounds, Scoring.accuracy(correct, rounds), durationMs)
    }

    fun meanLatencyMs(): Long = if (results.isEmpty()) 0 else results.map { it.latencyMs }.average().toLong()
}

/** Deterministic scoring helpers. Duration and reaction measurements must be supplied from a monotonic clock. */
object Scoring {
    fun accuracy(correct: Int, total: Int): Int {
        require(correct >= 0 && total >= 0 && correct <= total)
        return if (total == 0) 0 else (correct * 100.0 / total).roundToInt()
    }

    fun typingAccuracy(correctCharacters: Int, incorrectCharacters: Int): Int {
        require(correctCharacters >= 0 && incorrectCharacters >= 0)
        return accuracy(correctCharacters, correctCharacters + incorrectCharacters)
    }

    /** Standard five-character word convention: gross WPM = characters / 5 / elapsed minutes. */
    fun wordsPerMinute(correctCharacters: Int, elapsedMs: Long): Int {
        require(correctCharacters >= 0 && elapsedMs >= 0)
        if (elapsedMs == 0L) return 0
        return (correctCharacters * 12_000.0 / elapsedMs).roundToInt()
    }

    /** Adaptive changes require a history window; a single lucky trial never changes difficulty. */
    fun nextDifficulty(current: Int, recent: List<TrialResult>): Int {
        require(current in 1..10)
        if (recent.size < 5) return current
        val accuracy = recent.count { it.correct }.toDouble() / recent.size
        return when {
            accuracy >= .85 -> (current + 1).coerceAtMost(10)
            accuracy < .55 -> (current - 1).coerceAtLeast(1)
            else -> current
        }
    }
}

object ExerciseFactory {
    private val wordsEn = listOf("focus", "steady", "observe", "practice", "attention", "clarity")
    private val wordsFa = listOf("ذهن", "تمرکز", "یادگیری", "پیشرفت", "توجه", "آگاهی")
    private val symbols = listOf("◆", "●", "▲", "■")
    fun create(module: Module, difficulty: Int, language: String = "en", random: Random = Random.Default): Prompt {
        require(difficulty in 1..10)
        require(language == "en" || language == "fa")
        val words = if (language == "fa") wordsFa else wordsEn
        return when (module) {
            Module.VISION -> {
                val target = symbols[random.nextInt(symbols.size)]
                val distractors = symbols.filterNot { it == target }.shuffled(random).take(3)
                Prompt("Find the target shape", "$target  ${distractors.joinToString("   ")}", target, (listOf(target) + distractors).shuffled(random))
            }
            Module.REFLEX -> {
                val colors = listOf("GOLD", "IVORY", "BLUE", "GREEN")
                val word = colors[random.nextInt(colors.size)]
                val inkColor = colors[random.nextInt(colors.size)]
                Prompt("Name the ink color, not the word", word, inkColor, colors.shuffled(random))
            }
            Module.READ -> {
                val word = words[random.nextInt(words.size)]
                Prompt("Read the word, then select it", word, word, (listOf(word) + words.filterNot { it == word }.shuffled(random).take(3)).shuffled(random))
            }
            Module.TYPE -> {
                val length = (2 + difficulty / 2).coerceAtMost(words.size)
                val text = words.shuffled(random).take(length).joinToString(" ")
                Prompt("Type the displayed text exactly", text, text, emptyList())
            }
        }
    }
}
