package com.neurox.games.definition

import com.neurox.common.time.Clock
import com.neurox.common.time.TimingCalibration
import com.neurox.common.time.TimingMode
import com.neurox.domain.engine.TrialKind
import com.neurox.domain.engine.TrialOutcome
import com.neurox.domain.exercise.ExerciseConfig
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.model.SessionRecord
import com.neurox.games.stimulus.ResponseInput
import com.neurox.games.stimulus.StimulusColors
import com.neurox.games.stimulus.Symbols
import com.neurox.games.stimulus.Stimulus
import com.neurox.games.stimulus.StimulusField
import kotlin.random.Random

/**
 * One settled trial as the engine sees it. Maps 1:1 to the core trial result, but
 * carries the stimulus so the presentation layer can show why the answer was right
 * or wrong, and so tests can assert on content.
 */
data class EngineTrial(
    val index: Int,
    val kind: TrialKind,
    /** True when the trial expects a response (a Go trial). */
    val expected: Boolean,
    val stimulus: Stimulus,
    val delayMillis: Int,
    val windowMillis: Int,
    /** Reaction time in milliseconds, corrected for calibration, or null. */
    val responseMillis: Double?,
    val outcome: TrialOutcome,
    /** The input that settled the trial, or null on a timeout. */
    val input: ResponseInput?,
) {
    val correct: Boolean get() = outcome == TrialOutcome.CORRECT
}

/**
 * The public contract of a training engine.
 *
 * A feature module depends on this interface and on [EngineFactory]; it never
 * constructs an engine directly. This keeps difficulty rules, content and timing
 * inside the engine and out of the UI.
 */
interface ExerciseEngine {
    val exerciseId: String
    val template: ExerciseTemplate
    val field: StimulusField

    /** True when responses carry a location, so the UI must render a grid. */
    val requiresGrid: Boolean

    /** Milliseconds a stimulus stays on screen, from the configuration. */
    val stimulusVisibleMillis: Int

    /** Plans the next trial. Deterministic for a given seed and trial index. */
    fun planTrial(index: Int, random: Random, difficulty: Int): EngineTrial

    /**
     * Decides whether [input] is a correct answer for [trial].
     *
     * Engines that accept several valid answers (multi-target tracking) override
     * this; the default compares the primary target set.
     */
    fun isCorrect(trial: EngineTrial, input: ResponseInput): Boolean

    /** Short machine key for the correct answer, used by accessibility output. */
    fun answerHint(trial: EngineTrial): String

    /** Per-trial extra metrics, for engines that measure something specific. */
    fun metricsOf(trial: EngineTrial): Map<String, Double> = emptyMap()
}

/** Context that an engine needs to build a session. */
data class EngineContext(
    val seed: Long,
    val config: ExerciseConfig,
    val difficulty: Int,
    val profileId: String,
    val lastTypedText: String? = null,
)

/**
 * Factory for engines. One function, so the feature layer has a single entry point.
 */
object EngineFactory {

    fun create(template: ExerciseTemplate, context: EngineContext): ExerciseEngine =
        when (template.module) {
            com.neurox.domain.model.Module.VISION -> com.neurox.games.vision.VisionEngines.create(template, context)
            com.neurox.domain.model.Module.REFLEX -> com.neurox.games.reflex.ReflexEngines.create(template, context)
            com.neurox.domain.model.Module.READ -> com.neurox.games.read.ReadEngines.create(template, context)
            com.neurox.domain.model.Module.TYPE -> com.neurox.games.type.TypeEngines.create(template, context)
        }
}

/** Difficulty-scaled tuning used identically by all engines. */
object Tuning {

    /** Cell count for a difficulty. Higher levels use denser fields. */
    fun fieldColumns(difficulty: Int, base: Int = 6): Int =
        (base + (difficulty - 3)).coerceIn(4, 12)

    fun fieldRows(difficulty: Int, base: Int = 6): Int =
        (base + (difficulty - 3)).coerceIn(4, 12)

    /** Distractors grow with difficulty but never exceed the field. */
    fun distractorCount(difficulty: Int, base: Int): Int =
        (base + (difficulty - 3) * 2).coerceAtLeast(0)

    /** Ratio of inhibition (no-go) trials. Difficulty raises the proportion. */
    fun noGoRatio(difficulty: Int): Double = when (difficulty.coerceIn(1, 5)) {
        1 -> 0.15
        2 -> 0.20
        3 -> 0.25
        4 -> 0.32
        else -> 0.38
    }

    /** Response window for a difficulty, derived from the template reference. */
    fun windowMillis(template: ExerciseTemplate, difficulty: Int, factor: Double = 2.4): Int =
        (template.referenceMillis(difficulty) * factor).toInt().coerceIn(400, 6_000)

    /** Visible duration for a difficulty. Higher levels show the stimulus shorter. */
    fun visibleMillis(config: ExerciseConfig, difficulty: Int): Int {
        val shrink = ((difficulty - 3) * 0.10).coerceIn(-0.25, 0.25)
        val scaled = config.displayDurationMillis * (1.0 - shrink)
        return scaled.toInt().coerceIn(120, 10_000)
    }

    /** Random inter-trial delay for a difficulty. */
    fun delayMillis(template: ExerciseTemplate, difficulty: Int, random: Random): Int {
        val reference = template.referenceMillis(difficulty).toInt()
        val min = (reference * 0.9).toInt().coerceAtLeast(350)
        val max = (reference * 2.0).toInt().coerceAtLeast(min + 200)
        return random.nextInt(min, max + 1)
    }
}

/** Helpers shared by all engines. */
internal object EngineSupport {

    fun stimulusFor(
        config: ExerciseConfig,
        field: StimulusField,
        random: Random,
        symbolCount: Int = 2,
        colorCount: Int = 0,
    ): Stimulus {
        val targetCount = config.targetCount.coerceIn(1, field.cellCount / 2)
        val targets = field.sampleCells(random, targetCount).toSet()
        val distractors = field.sampleCellsAvoiding(
            random,
            Tuning.distractorCount(Tuning.fieldColumns(3), 3).coerceAtMost(field.cellCount - targetCount - 1),
            excluded = targets,
        )
        val symbols = Symbols.subset(symbolCount)
        val colors = if (colorCount > 0) StimulusColors.subset(colorCount) else emptyList()
        return Stimulus(
            targetCells = targets,
            distractorCells = distractors.toSet(),
            targetSymbol = symbols.random(random),
            distractorSymbols = distractors.map { Symbols.all.random(random) },
            colorArgb = colors.randomOrNull(random) ?: StimulusColors.CHAMPAGNE,
            visibleMillis = Tuning.visibleMillis(config, 3),
        )
    }

    fun sessionSeed(base: Long, exerciseId: String): Long =
        base xor exerciseId.hashCode().toLong() * 31L

    /** Reference time used to convert a summary into a scored record. */
    fun referenceFor(template: ExerciseTemplate, difficulty: Int): Double = template.referenceMillis(difficulty)

    /** Builds an idle engine for tests: no timers, no clock. */
    fun describeKind(expected: Boolean): TrialKind = if (expected) TrialKind.TARGET else TrialKind.NO_GO

    /** Timing mode an engine needs for calibrated measurements. */
    fun timingMode(template: ExerciseTemplate): TimingMode =
        if (template.module == com.neurox.domain.model.Module.REFLEX &&
            template in AUDIO_CAPABLE
        ) TimingMode.AUDIO else TimingMode.VISUAL

    private val AUDIO_CAPABLE = setOf(
        ExerciseTemplate.QUICK_TAP,
        ExerciseTemplate.GO_NO_GO,
        ExerciseTemplate.INHIBITION_TEST,
    )

    fun defaultCalibration(): TimingCalibration = TimingCalibration.NONE

    /** Deterministic id for a per-trial cache key. */
    fun trialKey(sessionId: String, index: Int): String = "$sessionId#$index"

    /** Guard for tests: a settled record must never carry negative time. */
    fun nonNegative(value: Double?): Double = (value ?: 0.0).coerceAtLeast(0.0)

    /** Utility to sum a metric across trials. */
    fun sum(trials: List<EngineTrial>, selector: (EngineTrial) -> Double): Double =
        trials.sumOf { selector(it) }

    /** Exposed for the presentation layer to label a trial. */
    fun summaryLabel(template: ExerciseTemplate, trial: EngineTrial): String =
        "${template.id}#${trial.index}:" +
            when (trial.outcome) {
                TrialOutcome.CORRECT -> "ok"
                TrialOutcome.INCORRECT -> "wrong"
                TrialOutcome.MISSED -> "miss"
                TrialOutcome.PREMATURE -> "early"
            }
}

/** Clock injection point for engines that need elapsed time outside the core. */
fun interface EngineClock {
    fun now(): Long
}

/** Adapter from the core [Clock] to [EngineClock]. */
fun Clock.asEngineClock(): EngineClock = EngineClock { wallClockMillis() }

/** Marker for engines whose content is text, used by the Library UI. */
interface TextualEngine {
    val languageTag: String
    val textId: String?
}

/** Convenience for tests and the UI: the number of trials the engine will plan. */
fun ExerciseEngine.plannedTrialCount(config: ExerciseConfig): Int = config.rounds.coerceIn(1, 99)

/** Definition of a scored extra metric with a stable key. */
data class MetricDefinition(
    val key: String,
    val value: Double,
    val unit: String,
)

/** Engine metadata consumed by the Library and the exercise detail screen. */
data class EngineDescriptor(
    val template: ExerciseTemplate,
    val requiresGrid: Boolean,
    val timingMode: TimingMode,
    val sessionRecordHint: String,
)
