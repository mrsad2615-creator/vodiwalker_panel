package com.neurox.games.read

import com.neurox.domain.engine.TrialKind
import com.neurox.domain.engine.TrialOutcome
import com.neurox.domain.exercise.ExerciseConfig
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.exercise.TypingLanguage
import com.neurox.games.content.ReadingLibrary
import com.neurox.games.content.ReadingText
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
 * Factory for the nine READ exercises.
 *
 * A reading engine never rewards raw speed: comprehension and recall trials carry
 * their own weight, and WPM is computed after the session, not inside trials.
 */
object ReadEngines {

    fun create(template: ExerciseTemplate, context: EngineContext): ExerciseEngine =
        when (template) {
            ExerciseTemplate.WORD_FLASH -> WordFlashEngine(context)
            ExerciseTemplate.RSVP_READER -> RsvpReaderEngine(context)
            ExerciseTemplate.PHRASE_READING -> PhraseReadingEngine(context)
            ExerciseTemplate.GUIDED_READING -> GuidedReadingEngine(context)
            ExerciseTemplate.VISUAL_SCANNING -> VisualScanningEngine(context)
            ExerciseTemplate.PERIPHERAL_READING -> PeripheralReadingEngine(context)
            ExerciseTemplate.READING_COMPREHENSION -> ReadingComprehensionEngine(context)
            ExerciseTemplate.RECALL_CHALLENGE -> RecallChallengeEngine(context)
            ExerciseTemplate.FOCUS_READING -> FocusReadingEngine(context)
            else -> throw IllegalArgumentException("${template.id} is not a READ exercise")
        }

    /** Language of a reading session, from the configuration. */
    internal fun languageOf(config: ExerciseConfig): String =
        if (config.typingLanguage == TypingLanguage.FA) "fa" else "en"
}

/** Common behaviour for reading engines. */
abstract class BaseReadEngine(
    protected val context: EngineContext,
) : ExerciseEngine, TextualEngine {

    protected val config: ExerciseConfig = context.config
    protected val difficulty: Int = context.difficulty
    protected val random = Random(context.seed)

    override val languageTag: String get() = ReadEngines.languageOf(config)
    override val textId: String? get() = config.readingTextId

    override val field: StimulusField get() = StimulusField(4, 4)
    override val requiresGrid: Boolean get() = false
    override val stimulusVisibleMillis: Int get() = Tuning.visibleMillis(config, difficulty)

    override fun answerHint(trial: EngineTrial): String =
        if (trial.stimulus.correctChoice >= 0) {
            "answer.choice.${trial.stimulus.correctChoice}"
        } else {
            "answer.reading_continue"
        }

    protected fun words(): List<String> = WordLists.forLanguage(languageTag)

    protected fun text(): ReadingText =
        config.readingTextId?.let { ReadingLibrary.byId(it) }
            ?: ReadingLibrary.pick(languageTag, difficulty, context.seed)

    protected fun window(factor: Double = 2.4): Int =
        Tuning.windowMillis(template, difficulty, factor)

    protected fun delay(random: Random): Int = Tuning.delayMillis(template, difficulty, random)

    /** Reading trials time the stimulus presentation, so visible time matters. */
    protected fun readingStimulus(text: String, visible: Int = stimulusVisibleMillis): Stimulus =
        Stimulus(text = text, visibleMillis = visible)
}

/**
 * WORD FLASH — one word appears for a short time. The user confirms the word, or
 * identifies it among alternatives at higher difficulty.
 */
class WordFlashEngine(context: EngineContext) : BaseReadEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.WORD_FLASH
    override val exerciseId: String = template.id

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val pool = words()
        val word = pool.random(random)
        val choices = if (difficulty >= 3) {
            (listOf(word) + pool.filter { it != word }.shuffled(random).take(2)).shuffled(random)
        } else {
            listOf(word)
        }
        val correctIndex = choices.indexOf(word)
        return EngineTrial(
            index = index,
            kind = if (difficulty >= 3) TrialKind.CHOICE else TrialKind.RECALL,
            expected = true,
            stimulus = readingStimulus(word).copy(choices = choices, correctChoice = correctIndex),
            delayMillis = delay(random),
            windowMillis = window(),
            responseMillis = null,
            outcome = TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean = when (input) {
        is ResponseInput.Choice -> input.index == trial.stimulus.correctChoice
        is ResponseInput.Tap -> trial.stimulus.choices.size == 1
        else -> false
    }
}

/**
 * RSVP READER — words are presented one at a time at the center.
 * The user confirms each word group; the reader measures comprehension afterwards.
 */
class RsvpReaderEngine(context: EngineContext) : BaseReadEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.RSVP_READER
    override val exerciseId: String = template.id

    private val body: ReadingText by lazy { text() }
    private val wordsList: List<String> by lazy { TextNormalizer.splitWords(body.body) }

    val wordCount: Int get() = wordsList.size

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val first = wordsList.firstOrNull() ?: ""
        return EngineTrial(
            index = index,
            kind = TrialKind.RECALL,
            expected = true,
            stimulus = readingStimulus(first).copy(
                text = first,
                choices = listOf("rsvp.continue"),
                correctChoice = 0,
            ),
            delayMillis = 600,
            windowMillis = 10_000,
            responseMillis = null,
            outcome = TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        input is ResponseInput.Tap || input is ResponseInput.Choice

    fun wordAt(index: Int): String? = wordsList.getOrNull(index)
}

/**
 * PHRASE READING — a short phrase appears once. The user then says whether a
 * second phrase is the same phrase.
 */
class PhraseReadingEngine(context: EngineContext) : BaseReadEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.PHRASE_READING
    override val exerciseId: String = template.id

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val pool = WordLists.phrases[languageTag] ?: WordLists.phrases.getValue("en")
        val phrase = pool.random(random)
        val same = random.nextBoolean()
        val second = if (same) {
            phrase
        } else {
            pool.filter { it != phrase }.random(random)
        }
        return EngineTrial(
            index = index,
            kind = TrialKind.CHOICE,
            expected = true,
            stimulus = readingStimulus(phrase).copy(
                choices = listOf(phrase, second),
                correctChoice = if (same) 0 else 1,
                text = phrase,
            ),
            delayMillis = delay(random),
            windowMillis = window(factor = 3.0),
            responseMillis = null,
            outcome = TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        input is ResponseInput.Choice && input.index == trial.stimulus.correctChoice
}

/**
 * GUIDED READING — text is shown with a highlight that moves word by word.
 * The user confirms completion; the reader measures effective WPM.
 */
class GuidedReadingEngine(context: EngineContext) : BaseReadEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.GUIDED_READING
    override val exerciseId: String = template.id

    private val body: ReadingText by lazy { text() }

    val textBody: String get() = body.body

    /** Highlight interval: faster guides as difficulty rises. */
    val stepMillis: Int get() = (420 - (difficulty - 3) * 45).coerceIn(180, 900)

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial = EngineTrial(
        index = index,
        kind = TrialKind.RECALL,
        expected = true,
        stimulus = readingStimulus(body.body).copy(text = body.body, correctChoice = 0),
        delayMillis = stepMillis,
        windowMillis = (body.characterCount * stepMillis / 5).coerceAtLeast(5_000),
        responseMillis = null,
        outcome = TrialOutcome.MISSED,
        input = null,
    )

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean = input is ResponseInput.Tap
}

/**
 * VISUAL SCANNING — a target word is announced; the user finds it in a grid of
 * words and taps its cell.
 */
class VisualScanningEngine(context: EngineContext) : BaseReadEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.VISUAL_SCANNING
    override val exerciseId: String = template.id

    override val field: StimulusField get() = StimulusField(useWideField(), 3)

    private fun useWideField(): Int = (4 + (difficulty - 3)).coerceIn(4, 8)

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val pool = words()
        val target = pool.random(random)
        val cellCount = field.cellCount
        val targetCell = random.nextInt(cellCount)
        val grid = (0 until cellCount).map { cell ->
            if (cell == targetCell) target else pool.filter { it != target }.random(random)
        }
        return EngineTrial(
            index = index,
            kind = TrialKind.CHOICE,
            expected = true,
            stimulus = readingStimulus(target, visible = 1_200).copy(
                targetCells = setOf(targetCell),
                text = target,
                choices = grid,
                correctChoice = targetCell,
            ),
            delayMillis = delay(random),
            windowMillis = window(factor = 3.2),
            responseMillis = null,
            outcome = TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean = when (input) {
        is ResponseInput.CellTap -> input.cell == trial.stimulus.correctChoice
        is ResponseInput.Choice -> input.index == trial.stimulus.correctChoice
        else -> false
    }
}

/**
 * PERIPHERAL READING — three words appear; the user reads the outer two and
 * ignores the center word, then identifies one of them.
 */
class PeripheralReadingEngine(context: EngineContext) : BaseReadEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.PERIPHERAL_READING
    override val exerciseId: String = template.id

    override val field: StimulusField get() = StimulusField(3, 1)

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val pool = words()
        val shown = (0 until 3).map { pool.random(random) }
        val askedIndex = listOf(0, 2).random(random)
        val asked = shown[askedIndex]
        val options = (listOf(asked) + pool.filter { it != asked }.shuffled(random).take(2)).shuffled(random)
        return EngineTrial(
            index = index,
            kind = TrialKind.CHOICE,
            expected = true,
            stimulus = readingStimulus(shown.joinToString(" "), visible = (stimulusVisibleMillis * 0.8).toInt()).copy(
                text = shown.joinToString(" "),
                choices = options,
                correctChoice = options.indexOf(asked),
                cueCell = askedIndex,
            ),
            delayMillis = delay(random),
            windowMillis = window(),
            responseMillis = null,
            outcome = TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        input is ResponseInput.Choice && input.index == trial.stimulus.correctChoice
}

/**
 * READING COMPREHENSION — a text is read, then questions about it are answered.
 * Correctness is the average of the question answers.
 */
class ReadingComprehensionEngine(context: EngineContext) : BaseReadEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.READING_COMPREHENSION
    override val exerciseId: String = template.id

    private val body: ReadingText by lazy { text() }

    val questions: List<com.neurox.games.content.ComprehensionQuestion> get() = body.questions

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val question = body.questions.getOrNull(index % body.questions.size.coerceAtLeast(1))
        return EngineTrial(
            index = index,
            kind = TrialKind.CHOICE,
            expected = true,
            stimulus = readingStimulus(body.body).copy(
                text = question?.prompt ?: body.body,
                choices = question?.options ?: emptyList(),
                correctChoice = question?.correctIndex ?: -1,
            ),
            delayMillis = delay(random),
            windowMillis = window(factor = 4.0),
            responseMillis = null,
            outcome = TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        input is ResponseInput.Choice && input.index == trial.stimulus.correctChoice

    override fun metricsOf(trial: EngineTrial): Map<String, Double> = mapOf(
        "comprehension_question_index" to trial.index.toDouble(),
        "question_count" to body.questions.size.toDouble(),
    )
}

/**
 * RECALL CHALLENGE — a short text is read, then a specific detail must be recalled
 * from memory without looking at the text.
 */
class RecallChallengeEngine(context: EngineContext) : BaseReadEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.RECALL_CHALLENGE
    override val exerciseId: String = template.id

    private val body: ReadingText by lazy { text() }

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val question = body.questions.getOrNull(index % body.questions.size.coerceAtLeast(1))
        val prompt = if (question != null) {
            question.prompt
        } else {
            // Without a question, recall is checked against the first sentence.
            body.body.split('.').firstOrNull()?.take(80) ?: body.body.take(80)
        }
        return EngineTrial(
            index = index,
            kind = TrialKind.RECALL,
            expected = true,
            stimulus = readingStimulus(text = body.body).copy(
                text = prompt,
                choices = question?.options ?: emptyList(),
                correctChoice = question?.correctIndex ?: -1,
            ),
            delayMillis = 1_000,
            windowMillis = window(factor = 4.5),
            responseMillis = null,
            outcome = TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean = when {
        trial.stimulus.correctChoice >= 0 -> input is ResponseInput.Choice && input.index == trial.stimulus.correctChoice
        else -> input is ResponseInput.Tap
    }
}

/**
 * FOCUS READING — a long text with no timer. Only a focus guide is shown.
 * The session measures sustained reading and comprehension, never speed alone.
 */
class FocusReadingEngine(context: EngineContext) : BaseReadEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.FOCUS_READING
    override val exerciseId: String = template.id

    private val body: ReadingText by lazy { text() }

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val question = body.questions.firstOrNull()
        return EngineTrial(
            index = index,
            kind = TrialKind.RECALL,
            expected = true,
            stimulus = readingStimulus(body.body, visible = config.displayDurationMillis).copy(
                text = question?.prompt ?: body.body,
                choices = question?.options ?: emptyList(),
                correctChoice = question?.correctIndex ?: -1,
            ),
            delayMillis = 500,
            windowMillis = (30_000).coerceAtMost(window(factor = 6.0)),
            responseMillis = null,
            outcome = TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean = when {
        trial.stimulus.correctChoice >= 0 -> input is ResponseInput.Choice && input.index == trial.stimulus.correctChoice
        else -> input is ResponseInput.Tap
    }
}
