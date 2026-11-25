package com.neurox.games.type

import com.neurox.domain.engine.TrialKind
import com.neurox.domain.engine.TrialOutcome
import com.neurox.domain.exercise.ExerciseConfig
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.exercise.TypingLanguage
import com.neurox.games.content.WordLists
import com.neurox.games.definition.EngineContext
import com.neurox.games.definition.EngineTrial
import com.neurox.games.definition.ExerciseEngine
import com.neurox.games.definition.TextNormalizer
import com.neurox.games.definition.TextualEngine
import com.neurox.games.definition.Tuning
import com.neurox.games.stimulus.ResponseInput
import com.neurox.games.stimulus.Stimulus
import com.neurox.games.stimulus.StimulusField
import kotlin.random.Random

/**
 * Factory for the eight TYPE exercises.
 *
 * A typing engine owns its text, and correctness is decided per character with
 * normalization, so a Farsi Yeh typed on a Latin keyboard layout is accepted.
 */
object TypeEngines {

    fun create(template: ExerciseTemplate, context: EngineContext): ExerciseEngine =
        when (template) {
            ExerciseTemplate.KEY_PRECISION -> KeyPrecisionEngine(context)
            ExerciseTemplate.WORD_SPRINT -> WordSprintEngine(context)
            ExerciseTemplate.SENTENCE_FLOW -> SentenceFlowEngine(context)
            ExerciseTemplate.TIMED_CHALLENGE -> TimedChallengeEngine(context)
            ExerciseTemplate.WEAK_KEYS -> WeakKeysEngine(context)
            ExerciseTemplate.RHYTHM_TRAINING -> RhythmTrainingEngine(context)
            ExerciseTemplate.CODE_TYPING -> CodeTypingEngine(context)
            ExerciseTemplate.CUSTOM_TEXT -> CustomTextEngine(context)
            else -> throw IllegalArgumentException("${template.id} is not a TYPE exercise")
        }

    internal fun languageOf(config: ExerciseConfig): String =
        if (config.typingLanguage == TypingLanguage.FA) "fa" else "en"
}

/** Common behaviour for typing engines. */
abstract class BaseTypeEngine(
    protected val context: EngineContext,
) : ExerciseEngine, TextualEngine {

    protected val config: ExerciseConfig = context.config
    protected val difficulty: Int = context.difficulty
    protected val random = Random(context.seed)

    override val languageTag: String get() = TypeEngines.languageOf(config)
    override val textId: String? get() = config.typingTextId
    override val field: StimulusField get() = StimulusField(4, 4)
    override val requiresGrid: Boolean get() = false
    override val stimulusVisibleMillis: Int get() = Tuning.visibleMillis(config, difficulty)

    override fun answerHint(trial: EngineTrial): String = "answer.type_text"

    protected fun words(): List<String> = WordLists.forLanguage(languageTag)

    protected fun window(difficultyAdjusted: Boolean = true): Int {
        val base = Tuning.windowMillis(template, difficulty, factor = 8.0)
        return if (difficultyAdjusted) base else base
    }

    /**
     * Correctness for typing: the typed characters must equal the target text
     * character by character, after diacritics are ignored.
     */
    protected fun typedTextCorrect(target: String, typed: String): Boolean {
        if (typed.isEmpty()) return false
        if (typed.length > target.length) return false
        for (i in typed.indices) {
            if (!TextNormalizer.charactersEqual(target[i], typed[i])) return false
        }
        return true
    }

    protected fun typingTrial(index: Int, target: String, windowMillis: Int, delay: Int): EngineTrial = EngineTrial(
        index = index,
        kind = TrialKind.RECALL,
        expected = true,
        stimulus = Stimulus(text = target, visibleMillis = stimulusVisibleMillis),
        delayMillis = delay,
        windowMillis = windowMillis,
        responseMillis = null,
        outcome = TrialOutcome.MISSED,
        input = null,
    )
}

/**
 * KEY PRECISION — single characters are shown one at a time. The user types the
 * character. This isolates key accuracy from word-level speed.
 */
class KeyPrecisionEngine(context: EngineContext) : BaseTypeEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.KEY_PRECISION
    override val exerciseId: String = template.id

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val pool = if (languageTag == "fa") TextNormalizer.PERSIAN_DIGITS.map { it.toString() } +
            words().flatMap { it.toList() }.map { it.toString() }
        else {
            ('a'..'z').map { it.toString() } + ('0'..'9').map { it.toString() }
        }
        val target = pool.random(random)
        return typingTrial(index, target, windowMillis = window() / 3, delay = 400)
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean {
        val ch = (input as? ResponseInput.Character)?.value ?: return false
        val target = trial.stimulus.text ?: return false
        return target.isNotEmpty() && TextNormalizer.charactersEqual(target[0], ch)
    }

    override fun metricsOf(trial: EngineTrial): Map<String, Double> =
        mapOf("characters" to (trial.stimulus.text?.length?.toDouble() ?: 0.0))
}

/**
 * WORD SPRINT — a sequence of words is typed for a fixed time.
 * The engine exposes the full text and marks progress.
 */
class WordSprintEngine(context: EngineContext) : BaseTypeEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.WORD_SPRINT
    override val exerciseId: String = template.id

    val text: String by lazy {
        words().shuffled(random).take(20).joinToString(" ")
    }

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial =
        typingTrial(index, text, windowMillis = 60_000, delay = 300)

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean = when (input) {
        is ResponseInput.Character -> true // per-character correctness is scored by TextMetrics
        is ResponseInput.Tap -> false
        else -> false
    }

    override fun metricsOf(trial: EngineTrial): Map<String, Double> = mapOf(
        "expected_characters" to TextNormalizer.measurableCharacters(text).toDouble(),
        "expected_words" to TextNormalizer.words(text).toDouble(),
    )
}

/**
 * SENTENCE FLOW — a full sentence with punctuation is typed.
 * Capitalization and punctuation are part of the task at higher difficulty.
 */
class SentenceFlowEngine(context: EngineContext) : BaseTypeEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.SENTENCE_FLOW
    override val exerciseId: String = template.id

    val sentence: String by lazy {
        if (languageTag == "fa") {
            listOf(
                "ذهن آرام، تمرکز بهتر می‌سازد.",
                "هر روز کمی تمرین کن و پیشرفت را ببین.",
                "سرعت خوب است، اما دقت مهم‌تر است.",
            ).random(random)
        } else {
            listOf(
                "A quiet mind builds better focus.",
                "Train a little every day and watch the trend.",
                "Speed helps, but accuracy comes first.",
            ).random(random)
        }
    }

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial =
        typingTrial(index, sentence, windowMillis = 90_000, delay = 300)

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        input is ResponseInput.Character

    override fun metricsOf(trial: EngineTrial): Map<String, Double> = mapOf(
        "expected_characters" to sentence.length.toDouble(),
        "expected_words" to TextNormalizer.words(sentence).toDouble(),
        "punctuation_count" to sentence.count { !it.isLetterOrDigit() && !it.isWhitespace() }.toDouble(),
    )
}

/**
 * TIMED CHALLENGE — exactly sixty seconds. The score combines gross and net WPM.
 */
class TimedChallengeEngine(context: EngineContext) : BaseTypeEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.TIMED_CHALLENGE
    override val exerciseId: String = template.id

    val durationSeconds: Int = 60

    val text: String by lazy {
        words().shuffled(random).take(30).joinToString(" ")
    }

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial =
        typingTrial(index, text, windowMillis = durationSeconds * 1_000, delay = 300)

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        input is ResponseInput.Character
}

/**
 * WEAK KEYS — the engine generates a text that repeats the user's weakest keys.
 *
 * [weakKeys] comes from stored analytics; an empty list falls back to a balanced
 * set, so the exercise is usable from the first session.
 */
class WeakKeysEngine(
    context: EngineContext,
    private val weakKeys: List<Char> = emptyList(),
) : BaseTypeEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.WEAK_KEYS
    override val exerciseId: String = template.id

    val drillKeys: List<Char> = weakKeys.ifEmpty {
        if (languageTag == "fa") listOf('ک', 'گ', 'پ', 'ژ', 'چ') else listOf('g', 'h', 'j', 'k', 'l')
    }

    val text: String by lazy {
        val groups = (0 until 6).map {
            drillKeys.shuffled(random).joinToString("")
        }
        groups.joinToString(" ")
    }

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial =
        typingTrial(index, text, windowMillis = 90_000, delay = 300)

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        input is ResponseInput.Character

    override fun metricsOf(trial: EngineTrial): Map<String, Double> = mapOf(
        "drill_keys" to drillKeys.size.toDouble(),
        "expected_characters" to text.length.toDouble(),
    )
}

/**
 * RHYTHM TRAINING — the user types a fixed pattern and tries to keep an even
 * inter-key interval. The engine reports the rhythm consistency target.
 */
class RhythmTrainingEngine(context: EngineContext) : BaseTypeEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.RHYTHM_TRAINING
    override val exerciseId: String = template.id

    /** Target interval between keystrokes in milliseconds. */
    val targetIntervalMillis: Int = (300 - (difficulty - 3) * 30).coerceIn(150, 500)

    val pattern: String by lazy {
        if (languageTag == "fa") "سشسش سشسش سشسش" else "asdf asdf asdf"
    }

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial =
        typingTrial(index, pattern, windowMillis = 60_000, delay = 300)

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        input is ResponseInput.Character
}

/**
 * CODE TYPING — a snippet of code, with punctuation and indentation-sensitive
 * characters. The snippet has no meaning to the app; it is typed as text.
 */
class CodeTypingEngine(context: EngineContext) : BaseTypeEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.CODE_TYPING
    override val exerciseId: String = template.id

    val snippet: String by lazy {
        listOf(
            "fun score(x: Int) = x * 2",
            "val total = items.sumOf { it.xp }",
            "if (ready) start() else wait()",
            "data class Point(val x: Int, val y: Int)",
        ).random(random)
    }

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial =
        typingTrial(index, snippet, windowMillis = 120_000, delay = 300)

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        input is ResponseInput.Character

    override fun metricsOf(trial: EngineTrial): Map<String, Double> = mapOf(
        "symbols" to snippet.count { !it.isLetterOrDigit() && !it.isWhitespace() }.toDouble(),
        "expected_characters" to snippet.length.toDouble(),
    )
}

/**
 * CUSTOM TEXT — the user's own text from the Exercise Builder.
 *
 * SAFETY: the text is plain data. It is validated against a length limit and a
 * character allow-list before it reaches this engine, and it is never executed.
 */
class CustomTextEngine(context: EngineContext) : BaseTypeEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.CUSTOM_TEXT
    override val exerciseId: String = template.id

    val text: String = context.lastTypedText?.takeIf { it.isNotBlank() }
        ?: if (languageTag == "fa") "متن دلخواه خود را اینجا تایپ کنید" else "type your own text here"

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial =
        typingTrial(index, text, windowMillis = 120_000, delay = 300)

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        input is ResponseInput.Character

    override fun metricsOf(trial: EngineTrial): Map<String, Double> = mapOf(
        "expected_characters" to text.length.toDouble(),
        "custom" to 1.0,
    )
}

/** Validation for user-supplied custom text. */
object CustomTextValidator {
    const val MAX_LENGTH = 2_000

    /** Returns a machine key for the failure, or null when the text is accepted. */
    fun validate(text: String): String? {
        if (text.isBlank()) return "custom_text.empty"
        if (text.length > MAX_LENGTH) return "custom_text.too_long"
        for (ch in text) {
            val allowed = ch.isLetterOrDigit() || ch.isWhitespace() ||
                ch in ".,;:!?\"'()-–—/%&+*=<>#@[]{}" || ch in '\u0600'..'\u06FF'
            if (!allowed) return "custom_text.invalid_character"
        }
        return null
    }
}
