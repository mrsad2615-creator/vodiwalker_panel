package com.neurox.games.reflex

import com.neurox.domain.engine.TrialKind
import com.neurox.domain.engine.TrialOutcome
import com.neurox.domain.exercise.ExerciseConfig
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.exercise.StimulusMode
import com.neurox.games.definition.EngineContext
import com.neurox.games.definition.EngineTrial
import com.neurox.games.definition.ExerciseEngine
import com.neurox.games.definition.TextualEngine
import com.neurox.games.definition.Tuning
import com.neurox.games.stimulus.Directions
import com.neurox.games.stimulus.ResponseInput
import com.neurox.games.stimulus.Stimulus
import com.neurox.games.stimulus.StimulusField
import kotlin.random.Random

/**
 * Factory for the nine REFLEX exercises.
 *
 * Refex timing is intentionally conservative: every engine sets a response window
 * from the template reference time, and the core measures with a monotonic clock.
 */
object ReflexEngines {

    fun create(template: ExerciseTemplate, context: EngineContext): ExerciseEngine =
        when (template) {
            ExerciseTemplate.QUICK_TAP -> QuickTapEngine(context)
            ExerciseTemplate.COLOR_REACTION -> ColorReactionEngine(context)
            ExerciseTemplate.SHAPE_REACTION -> ShapeReactionEngine(context)
            ExerciseTemplate.DIRECTION_MATCH -> DirectionMatchEngine(context)
            ExerciseTemplate.DUAL_FOCUS -> DualFocusEngine(context)
            ExerciseTemplate.SEQUENCE_MEMORY -> SequenceMemoryEngine(context)
            ExerciseTemplate.INHIBITION_TEST -> InhibitionTestEngine(context)
            ExerciseTemplate.GO_NO_GO -> GoNoGoEngine(context)
            ExerciseTemplate.MULTI_STIMULUS -> MultiStimulusEngine(context)
            else -> throw IllegalArgumentException("${template.id} is not a REFLEX exercise")
        }

    internal fun fieldFor(template: ExerciseTemplate, difficulty: Int, targetCount: Int): StimulusField =
        when (template) {
            ExerciseTemplate.DUAL_FOCUS -> StimulusField(2, 2)
            ExerciseTemplate.MULTI_STIMULUS -> StimulusField(2, 3)
            ExerciseTemplate.DIRECTION_MATCH -> StimulusField(3, 3)
            else -> {
                val side = (2 + (difficulty - 3) / 2).coerceIn(2, 4)
                StimulusField(side, side)
            }
        }.let { if (targetCount > it.cellCount) StimulusField(4, 4) else it }
}

/** Common behaviour for reflex engines. */
abstract class BaseReflexEngine(
    protected val context: EngineContext,
) : ExerciseEngine {

    protected val config: ExerciseConfig = context.config
    protected val difficulty: Int = context.difficulty
    protected val random = Random(context.seed)

    override val field: StimulusField
        get() = ReflexEngines.fieldFor(template, difficulty, config.targetCount)

    override val requiresGrid: Boolean get() = true
    override val stimulusVisibleMillis: Int
        get() = Tuning.visibleMillis(config, difficulty)

    override fun answerHint(trial: EngineTrial): String = if (trial.expected) {
        when {
            trial.stimulus.correctChoice >= 0 -> "answer.choice.${trial.stimulus.correctChoice}"
            trial.stimulus.targetCells.isNotEmpty() -> "answer.cell.${trial.stimulus.targetCells.first()}"
            else -> "answer.tap_now"
        }
    } else {
        "answer.withhold"
    }

    protected fun window(factor: Double = 2.4): Int =
        Tuning.windowMillis(template, difficulty, factor)

    protected fun delay(random: Random): Int = Tuning.delayMillis(template, difficulty, random)

    protected fun stimulus(visible: Int = stimulusVisibleMillis): Stimulus = Stimulus(
        visibleMillis = visible,
        colorArgb = com.neurox.games.stimulus.StimulusColors.CHAMPAGNE,
    )

    /** A plain reaction trial: a single signal, a single answer. */
    protected fun reactionTrial(
        index: Int,
        random: Random,
        expected: Boolean,
        windowMillis: Int,
    ): EngineTrial {
        val cell = field.sampleCells(random, 1).first()
        return EngineTrial(
            index = index,
            kind = if (expected) TrialKind.TARGET else TrialKind.NO_GO,
            expected = expected,
            stimulus = stimulus().copy(targetCells = setOf(cell)),
            delayMillis = delay(random),
            windowMillis = windowMillis,
            responseMillis = null,
            outcome = TrialOutcome.MISSED,
            input = null,
        )
    }
}

/**
 * QUICK TAP — one signal, tap as fast as possible. No choices, no distractors.
 * This is the purest reaction measure in the product.
 */
class QuickTapEngine(context: EngineContext) : BaseReflexEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.QUICK_TAP
    override val exerciseId: String = template.id

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial =
        reactionTrial(index, random, expected = true, windowMillis = window())

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        trial.expected && input is ResponseInput.Tap
}

/**
 * COLOR REACTION — a signal appears in one color; only a specified color must be
 * tapped. The instruction color changes between sessions, never inside a session.
 */
class ColorReactionEngine(context: EngineContext) : BaseReflexEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.COLOR_REACTION
    override val exerciseId: String = template.id

    /** Instruction color: fixed for the session, chosen deterministically. */
    val goColor: Long = com.neurox.games.stimulus.StimulusColors.subset(2).first()

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val colors = com.neurox.games.stimulus.StimulusColors.subset(2 + (difficulty / 3))
        val shown = colors.random(random)
        val expected = shown == goColor
        val cell = field.sampleCells(random, 1).first()
        return EngineTrial(
            index = index,
            kind = if (expected) TrialKind.TARGET else TrialKind.NO_GO,
            expected = expected,
            stimulus = stimulus().copy(
                targetCells = setOf(cell),
                colorArgb = shown,
            ),
            delayMillis = delay(random),
            windowMillis = window(),
            responseMillis = null,
            outcome = TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        trial.expected && input is ResponseInput.Tap
}

/**
 * SHAPE REACTION — the target shape is announced before the session. The user
 * taps only when the shown shape matches.
 */
class ShapeReactionEngine(context: EngineContext) : BaseReflexEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.SHAPE_REACTION
    override val exerciseId: String = template.id

    val goShape: Int = com.neurox.games.stimulus.Symbols.subset(3).first()

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val shapes = com.neurox.games.stimulus.Symbols.subset(2 + (difficulty / 2))
        val shown = shapes.random(random)
        val expected = shown == goShape
        val cell = field.sampleCells(random, 1).first()
        return EngineTrial(
            index = index,
            kind = if (expected) TrialKind.TARGET else TrialKind.NO_GO,
            expected = expected,
            stimulus = stimulus().copy(targetCells = setOf(cell), targetSymbol = shown),
            delayMillis = delay(random),
            windowMillis = window(),
            responseMillis = null,
            outcome = TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        trial.expected && input is ResponseInput.Tap
}

/**
 * DIRECTION MATCH — an arrow appears; the user answers with the arrow's direction.
 * A mismatch of direction is an error, a withheld answer is a miss.
 */
class DirectionMatchEngine(context: EngineContext) : BaseReflexEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.DIRECTION_MATCH
    override val exerciseId: String = template.id

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val direction = Directions.all.random(random)
        return EngineTrial(
            index = index,
            kind = TrialKind.CHOICE,
            expected = true,
            stimulus = stimulus().copy(
                direction = direction,
                correctChoice = direction,
                choices = Directions.all.map { Directions.key(it) },
            ),
            delayMillis = delay(random),
            windowMillis = window(),
            responseMillis = null,
            outcome = TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        input is ResponseInput.DirectionAnswer && input.direction == trial.stimulus.direction
}

/**
 * DUAL FOCUS — two stimuli appear at once: a left field and a right field. The
 * answer combines them: tap only when both carry the same symbol.
 */
class DualFocusEngine(context: EngineContext) : BaseReflexEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.DUAL_FOCUS
    override val exerciseId: String = template.id

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val symbols = com.neurox.games.stimulus.Symbols.subset(2 + (difficulty / 2))
        val left = symbols.random(random)
        val match = random.nextBoolean()
        val right = if (match) left else symbols.filter { it != left }.random(random)
        val cells = field.sampleCells(random, 2).toSet()
        return EngineTrial(
            index = index,
            kind = TrialKind.CHOICE,
            expected = true,
            stimulus = stimulus().copy(
                targetCells = cells,
                targetSymbol = left,
                distractorSymbols = listOf(right),
                correctChoice = if (match) 0 else 1,
                choices = listOf("answer.match", "answer.mismatch"),
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
 * SEQUENCE MEMORY — a sequence of cells lights up one at a time. The user repeats
 * the sequence in the same order.
 */
class SequenceMemoryEngine(context: EngineContext) : BaseReflexEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.SEQUENCE_MEMORY
    override val exerciseId: String = template.id

    /** The planned sequence of this trial, exposed to the UI after presentation. */
    var currentSequence: List<Int> = emptyList()
        private set

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val length = (config.targetCount + (difficulty - 3)).coerceIn(2, 8)
        val sequence = (0 until length).map { field.sampleCells(random, 1).first() }
        currentSequence = sequence
        return EngineTrial(
            index = index,
            kind = TrialKind.RECALL,
            expected = true,
            stimulus = stimulus(visible = config.displayDurationMillis).copy(
                targetCells = sequence.toSet(),
                choices = sequence.map { "cell.$it" },
            ),
            delayMillis = delay(random),
            windowMillis = (window() * 3.0).toInt(),
            responseMillis = null,
            outcome = TrialOutcome.MISSED,
            input = null,
        )
    }

    /**
     * The sequence answer is a list of cells. The input carries them through
     * [ResponseInput.MultiCellTap], but order matters, so the UI submits one
     * sequence per trial and the engine compares it in order.
     * Because [ResponseInput.MultiCellTap] is a set, sequence checking uses the
     * ordered variant [SequenceInput] for correctness and the set for tap targets.
     */
    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean = when (input) {
        is ResponseInput.Sequence -> input.cells == expectedSequence(trial)
        is ResponseInput.MultiCellTap -> input.cells == trial.stimulus.targetCells
        else -> false
    }

    private fun expectedSequence(trial: EngineTrial): List<Int> = trial.stimulus.choices.map { it.removePrefix("cell.").toInt() }
}

/**
 * INHIBITION TEST — a signal appears; the user must answer when the signal is
 * neutral and withhold when it carries the stop symbol. The stop symbol is fixed
 * for the session, so no instruction has to be re-read.
 */
class InhibitionTestEngine(context: EngineContext) : BaseReflexEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.INHIBITION_TEST
    override val exerciseId: String = template.id

    val stopSymbol: Int = com.neurox.games.stimulus.Symbols.CIRCLE

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val stop = random.nextDouble() < Tuning.noGoRatio(difficulty)
        val symbol = if (stop) stopSymbol else com.neurox.games.stimulus.Symbols.SQUARE
        val cell = field.sampleCells(random, 1).first()
        return EngineTrial(
            index = index,
            kind = if (stop) TrialKind.NO_GO else TrialKind.TARGET,
            expected = !stop,
            stimulus = stimulus().copy(targetCells = setOf(cell), targetSymbol = symbol),
            delayMillis = delay(random),
            windowMillis = window(),
            responseMillis = null,
            outcome = TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        trial.expected && input is ResponseInput.Tap
}

/**
 * GO / NO-GO CHALLENGE — like inhibition, but each trial carries its own
 * instruction symbol, so the rule must be read from the stimulus, not memorized.
 */
class GoNoGoEngine(context: EngineContext) : BaseReflexEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.GO_NO_GO
    override val exerciseId: String = template.id

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val go = random.nextDouble() < Tuning.noGoRatio(difficulty) || random.nextBoolean()
        val cell = field.sampleCells(random, 1).first()
        return EngineTrial(
            index = index,
            kind = if (go) TrialKind.TARGET else TrialKind.NO_GO,
            expected = go,
            stimulus = stimulus().copy(
                targetCells = setOf(cell),
                targetSymbol = if (go) com.neurox.games.stimulus.Symbols.SQUARE else com.neurox.games.stimulus.Symbols.STAR,
                choices = listOf("symbol.go", "symbol.no_go"),
            ),
            delayMillis = delay(random),
            windowMillis = window(),
            responseMillis = null,
            outcome = TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        trial.expected && input is ResponseInput.Tap
}

/**
 * MULTI-STIMULUS REACTION — several signals appear in different cells; only the
 * one with the announced symbol may be tapped.
 */
class MultiStimulusEngine(context: EngineContext) : BaseReflexEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.MULTI_STIMULUS
    override val exerciseId: String = template.id

    val targetSymbol: Int = com.neurox.games.stimulus.Symbols.STAR

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val count = (config.targetCount + 1).coerceIn(2, field.cellCount)
        val cells = field.sampleCells(random, count)
        val others = com.neurox.games.stimulus.Symbols.all.filter { it != targetSymbol }
        return EngineTrial(
            index = index,
            kind = TrialKind.TARGET,
            expected = true,
            stimulus = stimulus().copy(
                targetCells = cells.toSet(),
                targetSymbol = targetSymbol,
                distractorSymbols = cells.drop(1).map { others.random(random) },
            ),
            delayMillis = delay(random),
            windowMillis = window(2.8),
            responseMillis = null,
            outcome = TrialOutcome.MISSED,
            input = null,
        )
    }

    /** Only one cell carries the announced symbol, and it is the first cell. */
    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        input is ResponseInput.CellTap && input.cell == trial.stimulus.targetCells.first()
}
