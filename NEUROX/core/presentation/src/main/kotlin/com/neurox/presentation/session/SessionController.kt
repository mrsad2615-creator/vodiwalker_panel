package com.neurox.presentation.session

import com.neurox.audio.AudioPolicy
import com.neurox.audio.Synth
import com.neurox.common.time.Clock
import com.neurox.domain.engine.PauseReason
import com.neurox.domain.engine.SessionEndReason
import com.neurox.domain.engine.SessionSummary
import com.neurox.domain.engine.TrialKind
import com.neurox.domain.engine.TrialOutcome
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.exercise.Skill
import com.neurox.domain.model.Module
import com.neurox.domain.model.SessionKind
import com.neurox.domain.model.SessionRecord
import com.neurox.games.definition.EngineTrial
import com.neurox.games.runner.EngineSession
import com.neurox.games.stimulus.ResponseInput
import com.neurox.games.stimulus.StimulusField

/**
 * Everything the session screen renders, recomputed from the engine on every frame.
 *
 * The screen is a pure function of this state. That is what keeps the UI honest:
 * there is no second copy of the score, the timer or the round number that could
 * disagree with the engine that measures them.
 */
data class SessionUiState(
    val phase: Phase,
    val template: ExerciseTemplate,
    val module: Module,
    val difficulty: Int,
    val roundIndex: Int,
    val roundCount: Int,
    val remainingMillis: Long,
    val elapsedMillis: Long,
    val trialRemainingMillis: Long,
    val stimulusVisible: Boolean,
    val visibleTrial: EngineTrial?,
    val fieldColumns: Int,
    val fieldRows: Int,
    val correct: Int,
    val incorrect: Int,
    val missed: Int,
    val accuracy: Double,
    val answeredCount: Int,
    val progress: Double,
    val statusMessageKey: String?,
    val lastAnswerCorrect: Boolean?,
    val summary: SessionSummary?,
    val record: SessionRecord?,
    val pausedReason: String?,
    val canPause: Boolean,
    val isPractice: Boolean,
) {
    enum class Phase {
        /** Waiting for the user to start. */
        READY,

        /** Short countdown before the first trial. */
        COUNTDOWN,

        /** A trial is live and accepting an answer. */
        RUNNING,

        /** Feedback for the last answer is showing. */
        FEEDBACK,

        /** Paused; nothing advances. */
        PAUSED,

        /** Finished; [summary] and [record] are populated. */
        FINISHED,
    }

    val isFinished: Boolean get() = phase == Phase.FINISHED

    /** 1-based round number for display. */
    val roundNumber: Int get() = (roundIndex + 1).coerceIn(1, roundCount.coerceAtLeast(1))

    val isAcceptingInput: Boolean get() = phase == Phase.RUNNING

    /** Field geometry as a value, for rendering and accessibility labels. */
    val field: StimulusField get() = StimulusField(fieldColumns, fieldRows)
}

/**
 * Drives one training session.
 *
 * WHY THIS IS NOT IN THE COMPOSE LAYER: a reaction time is a measurement. If the
 * measurement lived in a composable it could not be tested without a device, and a
 * regression in the timing path would be invisible until users complained. The
 * state machine is pure Kotlin, covered by unit tests; Compose only renders
 * [state] and forwards taps.
 *
 * RULES ENFORCED HERE
 * - Every timestamp comes from the injected [Clock]; the engine owns the monotonic
 *   timing. Frame time is never used as a measurement.
 * - Pausing during a live trial is refused, because the pause would add arbitrary
 *   time to that trial. The refusable pause is also why the screen offers "end"
 *   next to "pause" while a trial is live.
 * - Backgrounding the app pauses the session rather than letting trials expire
 *   while the user cannot see the screen.
 * - A session aborted by the user is saved but never counts for XP or records; that
 *   rule lives in the domain, and this class only reports it.
 */
class SessionController(
    private val engineSession: EngineSession,
    private val audio: SessionAudio,
    private val clock: Clock,
    private val feedbackMillis: Long = DEFAULT_FEEDBACK_MILLIS,
    private val countdownMillis: Long = DEFAULT_COUNTDOWN_MILLIS,
    val isPractice: Boolean = false,
) {

    private var phase = SessionUiState.Phase.READY
    private var countdownStartNanos: Long? = null
    private var feedbackStartNanos: Long? = null
    private var lastAnswerCorrect: Boolean? = null
    private var statusMessageKey: String? = null
    private var pausedReason: String? = null
    private var started = false
    private var finishedSummary: SessionSummary? = null
    private var finishedRecord: SessionRecord? = null
    private var announcedTrialIndex: Int = -1

    val template: ExerciseTemplate get() = engineSession.template
    val sessionKind: SessionKind get() = engineSession.kind

    /** Skills this exercise trains, shown on the ready screen. */
    fun skills(): List<Skill> = template.skills

    /**
     * Starts the countdown. Idempotent: a recreated screen must never start a
     * session twice, which would double-count it.
     */
    fun startSession(): SessionUiState {
        if (!started) {
            started = true
            engineSession.start()
            phase = SessionUiState.Phase.COUNTDOWN
            countdownStartNanos = clock.nanoTime()
            audio.play(Synth.Cue.START)
        }
        return state()
    }

    /**
     * Sends an answer for the live trial.
     *
     * Returns true when the answer settled a trial. An answer outside the running
     * phase is ignored, so a double tap during feedback cannot settle two rounds.
     */
    fun answer(input: ResponseInput): Boolean {
        if (phase != SessionUiState.Phase.RUNNING) return false
        val settled = engineSession.answer(input) ?: run {
            // A response with no live stimulus is premature. The engine counted it;
            // there is nothing to render as a settled trial.
            statusMessageKey = "session.result.premature"
            lastAnswerCorrect = false
            return false
        }
        onSettled(settled)
        return true
    }

    /**
     * Advances the clock and mirrors the engine's phase.
     *
     * Safe to call at any frame rate: the engine resolves every deadline that has
     * passed, and this method only reflects the result.
     */
    fun tick(): SessionUiState {
        when (phase) {
            SessionUiState.Phase.COUNTDOWN -> {
                val start = countdownStartNanos ?: 0L
                if (clock.nanoTime() - start >= countdownMillis * NANOS_PER_MILLI) {
                    phase = SessionUiState.Phase.RUNNING
                    engineSession.tick()
                    announceCurrentTrial()
                }
            }

            SessionUiState.Phase.RUNNING -> {
                val before = engineSession.settledCount
                engineSession.tick()
                val after = engineSession.settledCount
                if (after > before) {
                    // A trial expired while the user did nothing: the engine settled
                    // it as missed.
                    onExpired()
                } else if (!engineSession.isFinished) {
                    announceCurrentTrial()
                }
            }

            SessionUiState.Phase.FEEDBACK -> {
                val start = feedbackStartNanos ?: 0L
                if (clock.nanoTime() - start >= feedbackMillis * NANOS_PER_MILLI) {
                    if (engineSession.isFinished) {
                        finish()
                    } else {
                        phase = SessionUiState.Phase.RUNNING
                        engineSession.tick()
                        announceCurrentTrial()
                    }
                }
            }

            SessionUiState.Phase.PAUSED -> Unit

            SessionUiState.Phase.READY, SessionUiState.Phase.FINISHED -> Unit
        }
        if (engineSession.isFinished && phase != SessionUiState.Phase.FINISHED) finish()
        return state()
    }

    /**
     * Pauses the session.
     *
     * Refused while a trial is live: pausing mid-trial would make the measured
     * reaction time meaningless. Returns the unchanged state in that case.
     */
    fun pause(reason: String = PAUSE_USER): SessionUiState {
        if (phase != SessionUiState.Phase.RUNNING && phase != SessionUiState.Phase.FEEDBACK) {
            return state()
        }
        if (phase == SessionUiState.Phase.RUNNING && engineSession.currentTrial != null) {
            // Measurement integrity: a live trial cannot be paused.
            return state()
        }
        engineSession.pause(
            if (reason == PAUSE_BACKGROUND) PauseReason.APP_BACKGROUND else PauseReason.USER,
        )
        phase = SessionUiState.Phase.PAUSED
        pausedReason = reason
        return state()
    }

    /**
     * Resumes after a pause.
     *
     * The feedback window restarts from now, so the pause duration cannot leak into
     * a feedback timer, and the resumed trial is re-announced once.
     */
    fun resume(): SessionUiState {
        if (phase != SessionUiState.Phase.PAUSED) return state()
        engineSession.resume()
        pausedReason = null
        phase = SessionUiState.Phase.RUNNING
        feedbackStartNanos = null
        announceCurrentTrial()
        return state()
    }

    /** True when the app went to the background while a session was live. */
    fun onBackground(): SessionUiState {
        if (phase == SessionUiState.Phase.RUNNING || phase == SessionUiState.Phase.FEEDBACK) {
            engineSession.pause(PauseReason.APP_BACKGROUND)
            phase = SessionUiState.Phase.PAUSED
            pausedReason = PAUSE_BACKGROUND
        }
        return state()
    }

    /**
     * Ends the session intentionally.
     *
     * Partial results are kept as an unfinished session: the domain marks it
     * ABANDONED, so it never awards XP or sets a record.
     */
    fun end(): SessionUiState {
        if (phase == SessionUiState.Phase.FINISHED) return state()
        finishedSummary = engineSession.abort()
        phase = SessionUiState.Phase.FINISHED
        statusMessageKey = "session.result.aborted"
        finishedRecord = engineSession.toRecord()
        return state()
    }

    private fun finish() {
        finishedSummary = engineSession.summary()
        finishedRecord = engineSession.toRecord()
        phase = SessionUiState.Phase.FINISHED
        statusMessageKey = "session.result.title"
        audio.onSessionFinished(success = finishedSummary?.endReason == SessionEndReason.ROUNDS_COMPLETE)
    }

    private fun onSettled(trial: EngineTrial) {
        lastAnswerCorrect = trial.correct
        statusMessageKey = when (trial.outcome) {
            TrialOutcome.CORRECT -> null
            TrialOutcome.INCORRECT -> "session.result.answered"
            TrialOutcome.MISSED -> "session.result.timeout"
            TrialOutcome.PREMATURE -> "session.result.premature"
        }
        audio.play(if (trial.correct) Synth.Cue.HIT else Synth.Cue.MISS)
        if (engineSession.isFinished) {
            finish()
            return
        }
        feedbackStartNanos = clock.nanoTime()
        phase = SessionUiState.Phase.FEEDBACK
    }

    private fun onExpired() {
        lastAnswerCorrect = false
        statusMessageKey = "session.result.timeout"
        audio.play(Synth.Cue.MISS)
        if (engineSession.isFinished) {
            finish()
            return
        }
        feedbackStartNanos = clock.nanoTime()
        phase = SessionUiState.Phase.FEEDBACK
    }

    /** Plays the signal cue exactly once per trial, when that trial becomes live. */
    private fun announceCurrentTrial() {
        val trial = engineSession.currentTrial ?: return
        if (trial.index == announcedTrialIndex) return
        announcedTrialIndex = trial.index
        audio.play(
            cue = if (trial.kind == TrialKind.NO_GO) Synth.Cue.INHIBIT else Synth.Cue.STIMULUS,
            signal = true,
        )
    }

    fun state(): SessionUiState {
        val visible = engineSession.currentTrial
        val settled = engineSession.settledTrials()
        val correct = settled.count { it.outcome == TrialOutcome.CORRECT }
        val incorrect = settled.count { it.outcome == TrialOutcome.INCORRECT }
        val missed = settled.count { it.outcome == TrialOutcome.MISSED }
        val denominator = settled.size
        return SessionUiState(
            phase = phase,
            template = template,
            module = template.module,
            difficulty = engineSession.difficulty,
            roundIndex = (engineSession.settledCount - 1).coerceAtLeast(0),
            roundCount = engineSession.plannedCount,
            remainingMillis = engineSession.remainingMillis(),
            elapsedMillis = engineSession.elapsedMillis(),
            trialRemainingMillis = engineSession.trialRemainingMillis(),
            stimulusVisible = phase == SessionUiState.Phase.RUNNING && visible != null,
            visibleTrial = visible.takeIf { phase == SessionUiState.Phase.RUNNING },
            fieldColumns = engineSession.engine.field.columns,
            fieldRows = engineSession.engine.field.rows,
            correct = correct,
            incorrect = incorrect,
            missed = missed,
            accuracy = if (denominator == 0) 0.0 else correct.toDouble() / denominator.toDouble(),
            answeredCount = correct + incorrect,
            progress = if (engineSession.plannedCount == 0) {
                0.0
            } else {
                engineSession.settledCount.toDouble() / engineSession.plannedCount.toDouble()
            },
            statusMessageKey = statusMessageKey,
            lastAnswerCorrect = lastAnswerCorrect,
            summary = finishedSummary ?: engineSession.summary(),
            record = finishedRecord,
            pausedReason = pausedReason,
            // A pause during a live trial is refused by pause(), so the button is
            // only offered when pausing is actually possible.
            canPause = phase == SessionUiState.Phase.FEEDBACK ||
                (phase == SessionUiState.Phase.RUNNING && engineSession.currentTrial == null),
            isPractice = isPractice,
        )
    }

    companion object {
        const val DEFAULT_FEEDBACK_MILLIS = 420L
        const val DEFAULT_COUNTDOWN_MILLIS = 900L
        const val PAUSE_USER = "user"
        const val PAUSE_BACKGROUND = "background"
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}

/**
 * The audio surface the controller needs, as an interface so tests can assert that
 * exactly the right cue was requested without producing sound.
 */
interface SessionAudio {
    fun play(cue: Synth.Cue, signal: Boolean = false)
    fun onSessionFinished(success: Boolean)
}

/**
 * Production implementation: asks [AudioPolicy] whether the cue may play, forwards
 * the request to a sink supplied by the Android layer (which owns the AudioTrack),
 * and fires haptics at the strength the policy allows.
 */
class PolicySessionAudio(
    private val policy: AudioPolicy,
    private val request: (Synth.Cue, Float) -> Unit,
    private val haptic: (Float) -> Unit = {},
) : SessionAudio {
    override fun play(cue: Synth.Cue, signal: Boolean) {
        @Suppress("UNUSED_EXPRESSION") signal
        when (val decision = policy.decide(cue)) {
            is AudioPolicy.PlaybackDecision.Play -> {
                request(cue, decision.gain)
                policy.onPlayed(cue)
                val strength = policy.hapticStrength(cue)
                if (strength > 0f) haptic(strength)
            }
            is AudioPolicy.PlaybackDecision.Silent -> Unit
        }
    }

    override fun onSessionFinished(success: Boolean) {
        play(if (success) Synth.Cue.FINISH else Synth.Cue.MISS)
    }
}

/** Test double that records the cue sequence instead of making sound. */
class RecordingSessionAudio : SessionAudio {
    val cues = mutableListOf<Synth.Cue>()
    val finished = mutableListOf<Boolean>()

    override fun play(cue: Synth.Cue, signal: Boolean) {
        cues += cue
    }

    override fun onSessionFinished(success: Boolean) {
        finished += success
    }
}
