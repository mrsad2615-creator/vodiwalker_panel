package com.neurox.games.vision

import com.neurox.domain.engine.TrialKind
import com.neurox.domain.exercise.ExerciseConfig
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.exercise.MovementDirection
import com.neurox.games.definition.EngineContext
import com.neurox.games.definition.EngineTrial
import com.neurox.games.definition.ExerciseEngine
import com.neurox.games.definition.Tuning
import com.neurox.games.stimulus.Directions
import com.neurox.games.stimulus.ResponseInput
import com.neurox.games.stimulus.Stimulus
import com.neurox.games.stimulus.StimulusColors
import com.neurox.games.stimulus.StimulusField
import com.neurox.games.stimulus.Symbols
import kotlin.random.Random

/**
 * Factory for the eight VISION exercises.
 *
 * Each exercise has its own timing rule, its own correctness rule and its own
 * difficulty curve. They share only the field model and the tuning helpers.
 */
object VisionEngines {

    fun create(template: ExerciseTemplate, context: EngineContext): ExerciseEngine =
        when (template) {
            ExerciseTemplate.FOCUS_LOCK -> FocusLockEngine(context)
            ExerciseTemplate.PERIPHERAL_SCAN -> PeripheralScanEngine(context)
            ExerciseTemplate.MULTI_TARGET_TRACKING -> MultiTargetTrackingEngine(context)
            ExerciseTemplate.VISUAL_SEARCH -> VisualSearchEngine(context)
            ExerciseTemplate.PATTERN_MATCH -> PatternMatchEngine(context)
            ExerciseTemplate.VISUAL_MEMORY -> VisualMemoryEngine(context)
            ExerciseTemplate.TARGET_IDENTIFICATION -> TargetIdentificationEngine(context)
            ExerciseTemplate.SPATIAL_ATTENTION -> SpatialAttentionEngine(context)
            else -> throw IllegalArgumentException("${template.id} is not a VISION exercise")
        }

    /** Field sizes per exercise: a search grid is wider than a memory board. */
    internal fun fieldFor(template: ExerciseTemplate, difficulty: Int): StimulusField = when (template) {
        ExerciseTemplate.FOCUS_LOCK -> StimulusField(Tuning.fieldColumns(difficulty, 5), Tuning.fieldRows(difficulty, 5))
        ExerciseTemplate.PERIPHERAL_SCAN -> StimulusField(8, 8)
        ExerciseTemplate.MULTI_TARGET_TRACKING -> StimulusField(6, 6)
        ExerciseTemplate.VISUAL_SEARCH -> StimulusField(Tuning.fieldColumns(difficulty, 6), Tuning.fieldRows(difficulty, 6))
        ExerciseTemplate.PATTERN_MATCH -> StimulusField(4, 4)
        ExerciseTemplate.VISUAL_MEMORY -> StimulusField(5, 5)
        ExerciseTemplate.TARGET_IDENTIFICATION -> StimulusField(4, 4)
        ExerciseTemplate.SPATIAL_ATTENTION -> StimulusField(7, 7)
        else -> StimulusField(6, 6)
    }
}

/**
 * Base class: plans one trial and measures the answer.
 *
 * [difficultyOf] is injectable so a test can pin the difficulty and assert exact
 * content. The session object itself is the one core domain object that times and
 * scores trials; engines never time anything.
 */
abstract class BaseVisionEngine(
    protected val context: EngineContext,
) : ExerciseEngine {

    protected val random = Random(context.seed)
    protected val config: ExerciseConfig = context.config
    protected val difficulty: Int = context.difficulty

    override val field: StimulusField
        get() = VisionEngines.fieldFor(template, difficulty)

    override val requiresGrid: Boolean get() = true
    override val stimulusVisibleMillis: Int
        get() = Tuning.visibleMillis(config, difficulty)

    protected fun baseStimulus(): Stimulus = Stimulus(
        visibleMillis = stimulusVisibleMillis,
        colorArgb = StimulusColors.CHAMPAGNE,
    )

    override fun answerHint(trial: EngineTrial): String =
        when {
            trial.stimulus.targetCells.isEmpty() -> "answer.tap_any"
            trial.stimulus.targetCells.size == 1 -> "answer.cell.${trial.stimulus.targetCells.first()}"
            else -> "answer.cells.${trial.stimulus.targetCells.sorted().joinToString(",")}"
        }

    /** Window derived from the exercise reference time and the difficulty. */
    protected fun window(): Int = Tuning.windowMillis(template, difficulty)

    /** Delay before the next stimulus appears. */
    protected fun delay(random: Random): Int = Tuning.delayMillis(template, difficulty, random)

    /** True when [input] hit any of the target cells. */
    protected fun tapsTarget(trial: EngineTrial, input: ResponseInput): Boolean {
        val cell = when (input) {
            is ResponseInput.CellTap -> input.cell
            is ResponseInput.MultiCellTap -> null
            else -> null
        }
        if (input is ResponseInput.MultiCellTap) {
            return input.cells.isNotEmpty() && input.cells.all { it in trial.stimulus.targetCells }
        }
        return cell != null && cell in trial.stimulus.targetCells
    }
}

/**
 * FOCUS LOCK — one small target appears; tap it.
 *
 * Measures selective attention and the ability to ignore a busy field. The target
 * gets smaller and appears closer to distractors as the difficulty rises.
 */
class FocusLockEngine(context: EngineContext) : BaseVisionEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.FOCUS_LOCK
    override val exerciseId: String = template.id

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val cells = field.sampleCells(random, count = 1)
        val distractors = field.sampleCellsAvoiding(
            random,
            count = Tuning.distractorCount(difficulty, 2).coerceAtMost(field.cellCount - 1),
            excluded = cells.toSet(),
        )
        return EngineTrial(
            index = index,
            kind = TrialKind.TARGET,
            expected = true,
            stimulus = baseStimulus().copy(
                targetCells = cells.toSet(),
                distractorCells = distractors.toSet(),
                targetSymbol = randomSymbol(random),
                distractorSymbols = distractors.map { Symbols.all.random(random) },
            ),
            delayMillis = delay(random),
            windowMillis = window(),
            responseMillis = null,
            outcome = com.neurox.domain.engine.TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean = tapsTarget(trial, input)
}

/**
 * PERIPHERAL SCAN — several targets appear on the outer ring.
 * All of them must be tapped, in any order, before the window closes.
 */
class PeripheralScanEngine(context: EngineContext) : BaseVisionEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.PERIPHERAL_SCAN
    override val exerciseId: String = template.id

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val count = (config.targetCount + (difficulty - 3)).coerceIn(2, 6)
        val targets = field.sampleCells(random, count.coerceAtMost(ringCells().size)) { field.isPeripheral(it) }.toSet()
        return EngineTrial(
            index = index,
            kind = TrialKind.CHOICE,
            expected = true,
            stimulus = baseStimulus().copy(
                targetCells = targets,
                distractorCells = emptySet(),
                targetSymbol = Symbols.CIRCLE,
            ),
            delayMillis = delay(random),
            windowMillis = (window() * 1.6).toInt(),
            responseMillis = null,
            outcome = com.neurox.domain.engine.TrialOutcome.MISSED,
            input = null,
        )
    }

    private fun ringCells(): List<Int> = (0 until field.cellCount).filter { field.isPeripheral(it) }

    /** A response is correct only when it names the whole target set. */
    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean = when (input) {
        is ResponseInput.MultiCellTap -> input.cells == trial.stimulus.targetCells
        is ResponseInput.CellTap -> trial.stimulus.targetCells == setOf(input.cell)
        else -> false
    }
}

/**
 * MULTI TARGET TRACKING — targets move on a fixed speed while distractors stay.
 * The user reports the final positions of the targets after they stop.
 */
class MultiTargetTrackingEngine(context: EngineContext) : BaseVisionEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.MULTI_TARGET_TRACKING
    override val exerciseId: String = template.id

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val count = config.targetCount.coerceIn(2, 5)
        val targets = field.sampleCells(random, count).toSet()
        val motion = MotionPath(
            targets = targets,
            steps = (config.targetSpeedDpPerSecond / 20).coerceIn(1, 6),
            direction = config.movementDirection,
            seed = random.nextLong(),
        )
        return EngineTrial(
            index = index,
            kind = TrialKind.CHOICE,
            expected = true,
            stimulus = baseStimulus().copy(
                targetCells = targets,
                targetSymbol = Symbols.DIAMOND,
                // The final positions are the answer; they travel in the metrics.
                direction = Directions.RIGHT, // travel axis; the path carries the motion
            ),
            delayMillis = delay(random),
            windowMillis = (window() * 2.0).toInt(),
            responseMillis = null,
            outcome = com.neurox.domain.engine.TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean = when (input) {
        is ResponseInput.MultiCellTap -> input.cells == trial.stimulus.targetCells
        is ResponseInput.CellTap -> input.cell in trial.stimulus.targetCells
        else -> false
    }
}

/** Deterministic motion description for tracking exercises. */
data class MotionPath(
    val targets: Set<Int>,
    val steps: Int,
    val direction: MovementDirection,
    val seed: Long,
)

/**
 * VISUAL SEARCH — one target symbol is shown; the user finds the same symbol
 * inside a field of similar symbols. The target symbol differs per trial.
 */
class VisualSearchEngine(context: EngineContext) : BaseVisionEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.VISUAL_SEARCH
    override val exerciseId: String = template.id

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val symbol = Symbols.all.random(random)
        val cells = field.sampleCells(random, count = 1)
        val distractors = field.sampleCellsAvoiding(
            random,
            count = Tuning.distractorCount(difficulty, 8).coerceAtMost(field.cellCount - 1),
            excluded = cells.toSet(),
        )
        // Distractors share the target's family, so shape must be discriminated.
        val similar = Symbols.all.filter { it != symbol }
        return EngineTrial(
            index = index,
            kind = TrialKind.TARGET,
            expected = true,
            stimulus = baseStimulus().copy(
                targetCells = cells.toSet(),
                distractorCells = distractors.toSet(),
                targetSymbol = symbol,
                distractorSymbols = distractors.map { similar.random(random) },
            ),
            delayMillis = delay(random),
            windowMillis = window(),
            responseMillis = null,
            outcome = com.neurox.domain.engine.TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean = tapsTarget(trial, input)
}

/**
 * PATTERN MATCH — a target pattern of cells appears briefly, then an identical
 * pattern must be found among two candidate patterns.
 */
class PatternMatchEngine(context: EngineContext) : BaseVisionEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.PATTERN_MATCH
    override val exerciseId: String = template.id

    init {
        require(field.cellCount >= 8)
    }

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val patternSize = (2 + (difficulty - 3).coerceAtLeast(0)).coerceIn(2, 5)
        val pattern = field.sampleCells(random, patternSize).toSet()
        // Two candidates: the true pattern, and a pattern that shares most cells.
        val decoy = (pattern.toList().dropLast(1) + field.sampleCellsAvoiding(random, 1, pattern)).toSet()
        val options = listOf(pattern, decoy).shuffled(random)
        val correctIndex = options.indexOf(pattern)
        return EngineTrial(
            index = index,
            kind = TrialKind.CHOICE,
            expected = true,
            stimulus = baseStimulus().copy(
                targetCells = pattern,
                choices = options.mapIndexed { i, cells -> "pattern.$i.${cells.sorted().joinToString("-")}" },
                correctChoice = correctIndex,
                // The decoy is carried in distractor cells so the UI can draw it.
                distractorCells = decoy,
            ),
            delayMillis = delay(random),
            windowMillis = window(),
            responseMillis = null,
            outcome = com.neurox.domain.engine.TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        input is ResponseInput.Choice && input.index == trial.stimulus.correctChoice
}

/**
 * VISUAL MEMORY — a set of cells lights up for a short time, disappears, then the
 * user taps the same cells. Recall is scored per cell through the multi-tap input.
 */
class VisualMemoryEngine(context: EngineContext) : BaseVisionEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.VISUAL_MEMORY
    override val exerciseId: String = template.id

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val count = (config.targetCount + (difficulty - 3)).coerceIn(3, 9)
        val targets = field.sampleCells(random, count.coerceAtMost(field.cellCount)).toSet()
        return EngineTrial(
            index = index,
            kind = TrialKind.RECALL,
            expected = true,
            stimulus = baseStimulus().copy(
                targetCells = targets,
                targetSymbol = Symbols.SQUARE,
            ),
            delayMillis = delay(random),
            // Recall needs more time than recognition.
            windowMillis = (window() * 2.5).toInt(),
            responseMillis = null,
            outcome = com.neurox.domain.engine.TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean = when (input) {
        is ResponseInput.MultiCellTap -> input.cells == trial.stimulus.targetCells
        is ResponseInput.CellTap -> trial.stimulus.targetCells == setOf(input.cell)
        else -> false
    }

    override fun metricsOf(trial: EngineTrial): Map<String, Double> {
        val answered = (trial.input as? ResponseInput.MultiCellTap)?.cells ?: emptySet()
        val recalled = answered.count { it in trial.stimulus.targetCells }
        val wrong = answered.count { it !in trial.stimulus.targetCells }
        return mapOf(
            "recall_correct" to recalled.toDouble(),
            "recall_wrong" to wrong.toDouble(),
            "recall_total" to trial.stimulus.targetCells.size.toDouble(),
        )
    }
}

/**
 * TARGET IDENTIFICATION — a named target appears with several alternatives and
 * must be identified by the symbol that matches the instruction.
 */
class TargetIdentificationEngine(context: EngineContext) : BaseVisionEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.TARGET_IDENTIFICATION
    override val exerciseId: String = template.id

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val options = Symbols.subset(if (difficulty >= 4) 4 else 3)
        val target = options.random(random)
        val cells = field.sampleCells(random, options.size)
        val correctIndex = cells.indices.first { options[it] == target }
        return EngineTrial(
            index = index,
            kind = TrialKind.CHOICE,
            expected = true,
            stimulus = baseStimulus().copy(
                targetCells = setOf(cells[correctIndex]),
                distractorCells = cells.filterIndexed { i, _ -> i != correctIndex }.toSet(),
                targetSymbol = target,
                distractorSymbols = cells.filterIndexed { i, _ -> i != correctIndex }.map { options[cells.indexOf(it)] },
                choices = cells.map { cell -> "cell.$cell.symbol.${Symbols.all[cell % Symbols.all.size]}" },
                correctChoice = correctIndex,
            ),
            delayMillis = delay(random),
            windowMillis = window(),
            responseMillis = null,
            outcome = com.neurox.domain.engine.TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean = when (input) {
        is ResponseInput.Choice -> input.index == trial.stimulus.correctChoice
        is ResponseInput.CellTap -> input.cell in trial.stimulus.targetCells
        else -> false
    }
}

/**
 * SPATIAL ATTENTION — a cue marks one position, then a target appears at a
 * different position. The user must answer whether the target matched the cue.
 * Half of the trials are valid, half are invalid.
 */
class SpatialAttentionEngine(context: EngineContext) : BaseVisionEngine(context) {
    override val template: ExerciseTemplate = ExerciseTemplate.SPATIAL_ATTENTION
    override val exerciseId: String = template.id

    override fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial {
        val cue = field.sampleCells(random, count = 1).first()
        val valid = random.nextBoolean()
        val target = if (valid) {
            cue
        } else {
            field.sampleCellsAvoiding(random, 1, setOf(cue)).first()
        }
        return EngineTrial(
            index = index,
            kind = TrialKind.CHOICE,
            expected = true,
            stimulus = baseStimulus().copy(
                targetCells = setOf(target),
                cueCell = cue,
                targetSymbol = Symbols.STAR,
                correctChoice = if (valid) 0 else 1,
                choices = listOf("answer.match", "answer.mismatch"),
            ),
            delayMillis = delay(random),
            windowMillis = window(),
            responseMillis = null,
            outcome = com.neurox.domain.engine.TrialOutcome.MISSED,
            input = null,
        )
    }

    override fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean =
        input is ResponseInput.Choice && input.index == trial.stimulus.correctChoice
}

/** Deterministic symbol choice so plans are reproducible in tests. */
internal fun randomSymbol(random: Random): Int = Symbols.all.random(random)
