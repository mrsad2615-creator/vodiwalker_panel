package com.neurox.games.runner

import com.neurox.common.time.Clock
import com.neurox.common.time.SystemClock
import com.neurox.common.time.TimingCalibration
import com.neurox.common.time.TimingMode
import com.neurox.domain.engine.PauseReason
import com.neurox.domain.engine.SessionCore
import com.neurox.domain.engine.SessionEndReason
import com.neurox.domain.engine.SessionEvent
import com.neurox.domain.engine.SessionResults
import com.neurox.domain.engine.SessionSpec
import com.neurox.domain.engine.SessionSummary
import com.neurox.domain.engine.TrialOutcome
import com.neurox.domain.engine.TrialPlan
import com.neurox.domain.exercise.ExerciseConfig
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.model.SessionKind
import com.neurox.domain.model.SessionRecord
import com.neurox.domain.scoring.Scoring
import com.neurox.games.definition.EngineContext
import com.neurox.games.definition.EngineTrial
import com.neurox.games.definition.EngineFactory
import com.neurox.games.definition.ExerciseEngine
import com.neurox.games.stimulus.ResponseInput
import kotlin.random.Random

/**
 * A running training session: one engine, one plan, one timing core.
 *
 * RESPONSIBILITIES
 * - Build the trial plan from the engine (content is decided here, once).
 * - Time trials through [SessionCore] (monotonic clock, pause aware).
 * - Convert answers into engine verdicts and keep the settled trials.
 * - Produce the durable [SessionRecord] when the session ends.
 *
 * THREADING: single threaded, like the core. The presentation layer calls the
 * methods from the main thread and drives [tick] from a frame callback or a
 * coroutine loop. No method blocks.
 */
class EngineSession(
    val sessionId: String,
    val profileId: String,
    val template: ExerciseTemplate,
    val config: ExerciseConfig,
    val difficulty: Int,
    val kind: SessionKind = SessionKind.TRAINING,
    seed: Long = System.nanoTime(),
    private val clock: Clock = SystemClock,
    private val calibration: TimingCalibration = TimingCalibration.NONE,
    private val timingMode: TimingMode = TimingMode.VISUAL,
) {
    val engine: ExerciseEngine = EngineFactory.create(
        template,
        EngineContext(
            seed = seed xor template.id.hashCode().toLong(),
            config = config,
            difficulty = difficulty,
            profileId = profileId,
        ),
    )

    private val plannedTrials: List<EngineTrial> = run {
        val random = Random(seed)
        List(config.rounds.coerceIn(1, 99)) { index ->
            engine.planTrial(index, random, difficulty)
        }
    }

    private val core: SessionCore

    private val settled = mutableMapOf<Int, EngineTrial>()

    init {
        val plan = plannedTrials.mapIndexed { index, trial ->
            TrialPlan(
                index = index,
                kind = trial.kind,
                delayMillis = trial.delayMillis,
                windowMillis = trial.windowMillis,
                expectedResponse = trial.expected,
                slot = trial.stimulus.targetCells.firstOrNull() ?: 0,
                correctChoice = trial.stimulus.correctChoice.coerceAtLeast(0),
            )
        }
        core = SessionCore(
            spec = SessionSpec(
                sessionId = sessionId,
                profileId = profileId,
                exerciseId = template.id,
                module = template.module,
                kind = kind,
                difficulty = difficulty,
                config = config,
                plan = plan,
                calibration = calibration,
                timingMode = timingMode,
            ),
            clock = clock,
        )
    }

    val isFinished: Boolean get() = core.isFinished
    val isPaused: Boolean get() = core.isPaused

    /** The trial shown on screen, if any. */
    val currentTrial: EngineTrial?
        get() = core.currentTrial?.let { settledOrPlanned(it.index) }

    /** Number of trials already decided. */
    val settledCount: Int get() = settled.size

    val plannedCount: Int get() = plannedTrials.size

    /** Active elapsed time in milliseconds, pause aware. */
    fun elapsedMillis(): Long = core.activeMillis()

    /** Remaining time of the configured session duration in milliseconds. */
    fun remainingMillis(): Long = core.remainingMillis()

    /** Remaining time of the live trial in milliseconds, or 0 when idle. */
    fun trialRemainingMillis(): Long = core.trialRemainingMillis()

    /** Engine content for a trial index, used by the UI for previews. */
    fun plannedTrial(index: Int): EngineTrial = plannedTrials[index]

    private fun settledOrPlanned(index: Int): EngineTrial? =
        plannedTrials.getOrNull(index)

    /** Starts the session. Returns a summary only when the plan is empty. */
    fun start(): SessionSummary? = core.start()

    /** Advances timers. Safe at any frame rate. */
    fun tick(nowNanos: Long = clock.nanoTime()) = core.tick(nowNanos)

    /**
     * Submits an answer for the visible trial.
     * Returns the settled trial, or null when no trial was settled.
     */
    fun answer(input: ResponseInput, atNanos: Long = clock.nanoTime()): EngineTrial? {
        val visible = currentTrial ?: run {
            // No stimulus: record a premature response without content.
            core.respond(onTarget = false, atNanos = atNanos)
            return null
        }
        val correct = engine.isCorrect(visible, input)
        val coreTrial = core.currentTrial
        val result = core.respond(onTarget = correct, atNanos = atNanos) ?: return null
        val finalTrial = visible.copy(
            outcome = result.outcome,
            responseMillis = result.responseMillis,
            input = input,
        )
        // Content for a trial is only final once it settles, so store it then.
        settled[visible.index] = finalTrial
        @Suppress("UNUSED_EXPRESSION") coreTrial
        return finalTrial
    }

    /** Settles the visible trial as missed, used when the window expires. */
    fun expired(atNanos: Long = clock.nanoTime()) {
        tick(atNanos)
    }

    fun pause(reason: PauseReason) = core.pause(reason)

    fun resume() = core.resume()

    /** Ends early at the user's request. */
    fun abort(): SessionSummary = core.abort()

    /** Ends because the process was interrupted. */
    fun interrupt(): SessionSummary = core.interrupt()

    fun summary(): SessionSummary? = core.summary()

    fun events(): List<SessionEvent> = core.stableEvents

    /** Extra per-trial metrics, aggregated for analytics and the result screen. */
    fun aggregateMetrics(): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        for (trial in settled.values) {
            for ((key, value) in engine.metricsOf(trial)) {
                out[key] = (out[key] ?: 0.0) + value
            }
        }
        return out
    }

    /** All settled trials in index order, for the result detail list. */
    fun settledTrials(): List<EngineTrial> = settled.toSortedMap().values.toList()

    /**
     * Converts the finished session into a durable record.
     * Returns null while the session is still running.
     */
    fun toRecord(): SessionRecord? {
        val summary = core.summary() ?: return null
        val record = SessionResults.toRecord(
            summary = summary,
            profileId = profileId,
            kind = kind,
            referenceMillis = template.referenceMillis(difficulty),
        )
        return record
    }

    /**
     * Mean of the settled response times, or 0 when nothing was measured.
     * Exposed so the result screen can show the corrected value it will store.
     */
    fun meanResponseMillis(): Double {
        val times = settled.values.mapNotNull { it.responseMillis }
        return if (times.isEmpty()) 0.0 else times.average()
    }

    /** Consistency of this session's reaction times, in 0..1. */
    fun consistency(): Double =
        Scoring.consistency(settled.values.mapNotNull { it.responseMillis })

    /** True when the summary ended with all planned trials. */
    fun completedAllTrials(): Boolean = core.summary()?.endReason == SessionEndReason.ROUNDS_COMPLETE

    /** Human-readable count of the decisions, used in tests and logs. */
    fun outcomeCounts(): Map<TrialOutcome, Int> =
        settled.values.groupingBy { it.outcome }.eachCount()

    companion object {
        /** Builds a session with default timing for the production app. */
        fun create(
            sessionId: String,
            profileId: String,
            template: ExerciseTemplate,
            config: ExerciseConfig = template.defaultConfig(),
            difficulty: Int = 3,
            kind: SessionKind = SessionKind.TRAINING,
            seed: Long = System.nanoTime(),
        ): EngineSession = EngineSession(
            sessionId = sessionId,
            profileId = profileId,
            template = template,
            config = config,
            difficulty = difficulty,
            kind = kind,
            seed = seed,
        )
    }
}

/** A complete session that was played headlessly, used by tests and the demo mode. */
data class PlayedSession(
    val record: SessionRecord,
    val trials: List<EngineTrial>,
    val metrics: Map<String, Double>,
    val summary: SessionSummary,
)

/**
 * Runs a full session without a UI by answering after a fixed reaction latency.
 *
 * This is the harness behind the engine integration tests and the offline
 * self-check. It uses the same [EngineSession] as the application, so a bug in the
 * real path also fails here.
 */
object HeadlessSessionRunner {

    /**
     * Plays [session] to completion.
     *
     * @param latencyMillis reaction time applied to every trial.
     * @param accuracyRatio share of trials answered correctly (0..1).
     * @param stepMillis simulated frame duration.
     */
    fun play(
        session: EngineSession,
        latencyMillis: Long = 350,
        accuracyRatio: Double = 1.0,
        stepMillis: Long = 16,
        maxSteps: Int = 200_000,
    ): PlayedSession {
        require(accuracyRatio in 0.0..1.0)
        val clock = ManualTestClock()
        // Re-create the session against the manual clock for determinism.
        val deterministic = EngineSession(
            sessionId = session.sessionId,
            profileId = session.profileId,
            template = session.template,
            config = session.config,
            difficulty = session.difficulty,
            kind = session.kind,
            seed = session.sessionId.hashCode().toLong(),
            clock = clock,
            calibration = TimingCalibration.NONE,
            timingMode = TimingMode.VISUAL,
        )
        deterministic.start()
        val random = Random(session.sessionId.hashCode().toLong())
        var steps = 0
        while (!deterministic.isFinished && steps < maxSteps) {
            steps += 1
            clock.advanceMillis(stepMillis)
            deterministic.tick(clock.nanoTime())
            val trial = deterministic.currentTrial
            if (trial != null && !deterministic.isFinished) {
                clock.advanceMillis(latencyMillis)
                val submitCorrect = random.nextDouble() < accuracyRatio
                val answer = if (submitCorrect) {
                    correctAnswerFor(deterministic, trial, random)
                } else {
                    wrongAnswerFor(trial)
                }
                deterministic.answer(answer, atNanos = clock.nanoTime())
            }
        }
        val summary = deterministic.summary() ?: deterministic.abort()
        val record = SessionResults.toRecord(
            summary = summary,
            profileId = deterministic.profileId,
            kind = deterministic.kind,
            referenceMillis = deterministic.template.referenceMillis(deterministic.difficulty),
        )
        return PlayedSession(
            record = record,
            trials = deterministic.settledTrials(),
            metrics = deterministic.aggregateMetrics(),
            summary = summary,
        )
    }

    /**
     * Produces an answer that the engine itself accepts.
     *
     * The harness must not assume a grid: tap-only exercises reject cell taps, so
     * candidate inputs are tried against the engine's own contract.
     */
    private fun correctAnswerFor(
        session: EngineSession,
        trial: EngineTrial,
        random: Random,
    ): ResponseInput {
        val stimulus = trial.stimulus
        val candidates = buildList {
            if (!trial.expected) {
                // A withhold trial: the correct behavior is no input, which the
                // harness models by letting the window expire is not possible
                // here, so it submits nothing that the engine would accept.
                return@buildList
            }
            if (stimulus.correctChoice >= 0) add(ResponseInput.Choice(stimulus.correctChoice))
            if (stimulus.direction in 0..3) add(ResponseInput.DirectionAnswer(stimulus.direction))
            if (stimulus.targetCells.isNotEmpty()) {
                add(ResponseInput.CellTap(stimulus.targetCells.first()))
                add(ResponseInput.MultiCellTap(stimulus.targetCells))
                add(ResponseInput.Sequence(stimulus.targetCells.toList()))
            }
            add(ResponseInput.Tap)
        }
        val accepted = candidates.firstOrNull { session.engine.isCorrect(trial, it) }
        return accepted ?: candidates.firstOrNull() ?: ResponseInput.Tap
    }

    private fun wrongAnswerFor(trial: EngineTrial): ResponseInput = when {
        trial.stimulus.correctChoice >= 0 -> ResponseInput.Choice(
            (trial.stimulus.correctChoice + 1) % trial.stimulus.choices.size.coerceAtLeast(2),
        )
        trial.stimulus.targetCells.isNotEmpty() -> ResponseInput.CellTap(
            (trial.stimulus.targetCells.first() + 1) % 36,
        )
        else -> ResponseInput.Tap
    }
}

/** A monotonic clock for the harness, advanced by the runner. */
class ManualTestClock : Clock {
    private var nanos = 1_000_000_000L
    private var wall = 1_735_689_600_000L
    override fun nanoTime(): Long = nanos
    override fun wallClockMillis(): Long = wall

    fun advanceMillis(millis: Long) {
        nanos += millis * 1_000_000L
        wall += millis
    }
}
