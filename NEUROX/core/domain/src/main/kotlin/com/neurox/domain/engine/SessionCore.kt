package com.neurox.domain.engine

import com.neurox.common.time.Clock
import com.neurox.common.time.TimingCalibration
import com.neurox.common.time.TimingMode
import com.neurox.domain.exercise.ExerciseConfig
import com.neurox.domain.model.Module
import com.neurox.domain.model.SessionKind

/** Immutable description of the session about to run. */
data class SessionSpec(
    val sessionId: String,
    val profileId: String,
    val exerciseId: String,
    val module: Module,
    val kind: SessionKind,
    val difficulty: Int,
    val config: ExerciseConfig,
    val plan: List<TrialPlan>,
    val calibration: TimingCalibration = TimingCalibration.NONE,
    val timingMode: TimingMode = TimingMode.VISUAL,
)

/** Events emitted by [SessionCore]. The UI renders these; tests assert on them. */
sealed interface SessionEvent {
    data class Started(val spec: SessionSpec) : SessionEvent
    data class StimulusShown(val plan: TrialPlan, val deadlineNanos: Long) : SessionEvent
    data class TrialSettled(val result: TrialResult, val settledCount: Int) : SessionEvent
    data class Paused(val reason: PauseReason) : SessionEvent
    data object Resumed : SessionEvent
    data class Finished(val summary: SessionSummary) : SessionEvent
}

/** Why a session paused. Recovery rules differ per reason. */
enum class PauseReason {
    USER,
    APP_BACKGROUND,
    AUDIO_INTERRUPTION,
    SYSTEM,
}

/** Terminal classification of a session. */
enum class SessionEndReason {
    /** All trials settled. */
    ROUNDS_COMPLETE,

    /** Configured duration elapsed. */
    TIME_LIMIT,

    /** User ended the session. Recorded as a partial session with no XP. */
    USER_ABORTED,

    /** Session was interrupted and must be treated as abandoned. */
    INTERRUPTED,
}

/**
 * Aggregated immutable outcome of a session. Presentation maps this to
 * `SessionRecord` through `SessionResults.toRecord`.
 */
data class SessionSummary(
    val sessionId: String,
    val exerciseId: String,
    val module: Module,
    val difficulty: Int,
    val endReason: SessionEndReason,
    val correct: Int,
    val incorrect: Int,
    val missed: Int,
    val premature: Int,
    val settledTrials: Int,
    val plannedTrials: Int,
    val responseTimesMillis: List<Double>,
    val activeDurationMillis: Long,
    val startedAtWallMillis: Long,
    val endedAtWallMillis: Long,
    val completion: Double,
) {
    val totalAnswered: Int get() = correct + incorrect + premature
}

/**
 * Deterministic, pause-aware, single-threaded trial engine for one session.
 *
 * LIFECYCLE
 * - Construct with a [SessionSpec] (plan already generated).
 * - Call [start] once.
 * - Call [tick] from a frame loop or a test harness; it advances the schedule.
 * - Call [respond] for user input.
 * - Call [pause] and [resume] around interruptions.
 * - Call [finish] or [abort] to end; [summary] is then stable and repeatable.
 *
 * INVARIANTS
 * - Monotonic time only for all durations.
 * - A settled trial can never settle twice.
 * - [summary] is idempotent: the same object is returned after completion.
 * - Paused time is never counted as active time.
 * - Interrupted sessions are never reported as completed.
 */
class SessionCore(
    private val spec: SessionSpec,
    private val clock: Clock,
) {
    private val events = mutableListOf<SessionEvent>()
    private val results = mutableListOf<TrialResult>()
    private val responseTimes = mutableListOf<Double>()
    private var finishedSummary: SessionSummary? = null

    private var started = false
    private var startedNanos = 0L
    private var startedWall = 0L
    private var endedNanos = 0L
    private var nextIndex = 0
    private var activePlan: TrialPlan? = null
    private var activeEmitNanos = 0L
    private var activeDeadlineNanos = 0L
    private var nextEmitNanos = 0L
    private var paused = false
    private var pauseStartedNanos = 0L
    private var pausedNanosTotal = 0L
    private var prematureCount = 0

    val isFinished: Boolean get() = finishedSummary != null
    val isPaused: Boolean get() = paused
    val isRunning: Boolean get() = started && !isFinished && !paused
    val stableEvents: List<SessionEvent> get() = events.toList()

    /** Identifies the stimulus a response can target; null when none is active. */
    val currentTrial: TrialPlan? get() = activePlan

    fun start(): SessionSummary? {
        if (started) return finishedSummary
        started = true
        startedNanos = clock.nanoTime()
        startedWall = clock.wallClockMillis()
        if (spec.plan.isEmpty()) {
            return finalize(SessionEndReason.ROUNDS_COMPLETE)
        }
        nextEmitNanos = startedNanos + spec.plan[0].delayMillis * NANOS_PER_MILLI
        events += SessionEvent.Started(spec)
        return null
    }

    /**
     * Advances the schedule to [nowNanos]. Safe to call at any frame rate and
     * safe to call repeatedly with the same time.
     */
    /**
     * Active time since start, excluding pauses, in milliseconds.
     *
     * Exposed for the session screen: the timer it shows must be the same number
     * the session is scored against, so it cannot be recomputed by the UI.
     */
    fun activeMillis(): Long = if (!started) 0L else activeMillisAt(clock.nanoTime())

    /**
     * Time left in the configured session duration, in milliseconds.
     *
     * Zero once the session is finished, even when it ended because every round was
     * played before the time limit. A finished screen showing "1:10 left" would be
     * a lie about the state of the session.
     */
    fun remainingMillis(): Long {
        if (isFinished) return 0L
        return (spec.config.sessionDurationSeconds * 1000L - activeMillis()).coerceAtLeast(0L)
    }

    /** Milliseconds from now until the live trial expires; 0 when no trial is live. */
    fun trialRemainingMillis(): Long {
        val plan = activePlan ?: return 0L
        @Suppress("UNUSED_EXPRESSION") plan
        if (isFinished || paused) return 0L
        return ((activeDeadlineNanos - clock.nanoTime()) / NANOS_PER_MILLI).coerceAtLeast(0L)
    }

    fun tick(nowNanos: Long = clock.nanoTime()) {
        if (!started || isFinished || paused) return
        // Resolve any elapsed deadline first, so time limits are not exceeded.
        var guard = 0
        while (guard++ < MAX_STEPS_PER_TICK && !isFinished) {
            val plan = activePlan
            if (plan == null) {
                if (nextIndex >= spec.plan.size) {
                    finalize(SessionEndReason.ROUNDS_COMPLETE)
                    break
                }
                if (nowNanos < nextEmitNanos) break
                emit(nowNanos)
            } else {
                if (nowNanos < activeDeadlineNanos) break
                settle(plan, if (plan.expectedResponse) TrialOutcome.MISSED else TrialOutcome.CORRECT, null, activeDeadlineNanos)
            }
        }
        if (!isFinished && activeMillisAt(nowNanos) >= spec.config.sessionDurationSeconds * 1000L) {
            finalize(SessionEndReason.TIME_LIMIT)
        }
    }

    private fun emit(nowNanos: Long) {
        val plan = spec.plan[nextIndex]
        nextIndex += 1
        activePlan = plan
        activeEmitNanos = nowNanos
        activeDeadlineNanos = nowNanos + plan.windowMillis * NANOS_PER_MILLI
        events += SessionEvent.StimulusShown(plan, activeDeadlineNanos)
    }

    /**
     * Registers a user response. [onTarget] tells whether the response matched
     * the intent of the current stimulus (correct choice, correct position...).
     *
     * Returns the settled [TrialResult], or null when nothing settled (for
     * example a duplicate tap on an already settled trial).
     */
    fun respond(onTarget: Boolean, atNanos: Long = clock.nanoTime()): TrialResult? {
        if (!started || isFinished || paused) return null
        val plan = activePlan
        if (plan == null) {
            // No stimulus is on screen: this is an early response.
            prematureCount += 1
            return TrialResult(
                plan = TrialPlan(-1, TrialKind.TARGET, 0, 0, true, 0),
                outcome = TrialOutcome.PREMATURE,
                responseMillis = null,
            )
        }
        if (atNanos > activeDeadlineNanos) {
            settle(plan, TrialOutcome.MISSED, null, activeDeadlineNanos)
            return results.last()
        }
        val outcome = if (!plan.expectedResponse) {
            TrialOutcome.INCORRECT // responding to a NO_GO signal
        } else if (onTarget) {
            TrialOutcome.CORRECT
        } else {
            TrialOutcome.INCORRECT
        }
        val rawMillis = (atNanos - activeEmitNanos) / NANOS_PER_MILLI.toDouble()
        return settle(plan, outcome, rawMillis, atNanos)
    }

    private fun settle(
        plan: TrialPlan,
        outcome: TrialOutcome,
        rawMillis: Double?,
        settledAtNanos: Long,
    ): TrialResult {
        val corrected = rawMillis?.let { spec.calibration.correctedMillis(it.toLong(), spec.timingMode) }
        val result = TrialResult(plan, outcome, corrected)
        results += result
        if (corrected != null) responseTimes += corrected
        activePlan = null
        activeEmitNanos = 0L
        activeDeadlineNanos = 0L
        if (nextIndex < spec.plan.size) {
            nextEmitNanos = settledAtNanos + plan.delayMillis * NANOS_PER_MILLI
        } else {
            nextEmitNanos = Long.MAX_VALUE
        }
        events += SessionEvent.TrialSettled(result, results.size)
        if (results.size == spec.plan.size) {
            finalize(SessionEndReason.ROUNDS_COMPLETE)
        }
        return result
    }

    /** Pauses the session. Deadlines are extended by the paused duration. */
    fun pause(reason: PauseReason) {
        if (!started || isFinished || paused) return
        paused = true
        pauseStartedNanos = clock.nanoTime()
        events += SessionEvent.Paused(reason)
    }

    /** Resumes after [pause]. Returns false when the session was not paused. */
    fun resume(): Boolean {
        if (!paused || isFinished) return false
        val now = clock.nanoTime()
        val delta = now - pauseStartedNanos
        pausedNanosTotal += delta
        if (activePlan != null) {
            activeDeadlineNanos += delta
            // Shift the stimulus origin too, so a pause in the middle of a
            // stimulus never appears as a slow response.
            activeEmitNanos += delta
        }
        if (nextEmitNanos != Long.MAX_VALUE) nextEmitNanos += delta
        paused = false
        events += SessionEvent.Resumed
        return true
    }

    /** Ends the session early at the user's request. */
    fun abort(): SessionSummary {
        if (isFinished) return finishedSummary!!
        return finalize(SessionEndReason.USER_ABORTED)
    }

    /**
     * Ends the session because the process or activity was interrupted.
     * The summary is marked [SessionEndReason.INTERRUPTED] and awards no XP.
     */
    fun interrupt(): SessionSummary {
        if (isFinished) return finishedSummary!!
        return finalize(SessionEndReason.INTERRUPTED)
    }

    /** Immutable summary; null while running. */
    fun summary(): SessionSummary? = finishedSummary

    private fun finalize(reason: SessionEndReason): SessionSummary {
        finishedSummary?.let { return it }
        endedNanos = clock.nanoTime()
        val endWall = clock.wallClockMillis()
        val settled = results.size
        val planned = spec.plan.size.coerceAtLeast(1)
        val correct = results.count { it.outcome == TrialOutcome.CORRECT }
        val incorrect = results.count { it.outcome == TrialOutcome.INCORRECT }
        val missed = results.count { it.outcome == TrialOutcome.MISSED }
        val premature = prematureCount + results.count { it.outcome == TrialOutcome.PREMATURE }
        val completion = (settled.toDouble() / planned.toDouble()).coerceIn(0.0, 1.0)
        val summary = SessionSummary(
            sessionId = spec.sessionId,
            exerciseId = spec.exerciseId,
            module = spec.module,
            difficulty = spec.difficulty,
            endReason = reason,
            correct = correct,
            incorrect = incorrect,
            missed = missed,
            premature = premature,
            settledTrials = settled,
            plannedTrials = planned,
            responseTimesMillis = responseTimes.toList(),
            activeDurationMillis = activeMillisAt(endedNanos),
            startedAtWallMillis = startedWall,
            endedAtWallMillis = endWall,
            completion = completion,
        )
        finishedSummary = summary
        events += SessionEvent.Finished(summary)
        return summary
    }

    private fun activeMillisAt(nowNanos: Long): Long {
        var elapsed = nowNanos - startedNanos - pausedNanosTotal
        if (paused && pauseStartedNanos > 0) elapsed -= (nowNanos - pauseStartedNanos)
        return (elapsed / NANOS_PER_MILLI).coerceAtLeast(0L)
    }

    companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        private const val MAX_STEPS_PER_TICK = 64
    }
}
