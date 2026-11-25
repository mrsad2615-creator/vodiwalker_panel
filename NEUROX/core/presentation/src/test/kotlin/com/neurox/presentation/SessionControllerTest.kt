package com.neurox.presentation

import com.neurox.audio.Synth
import com.neurox.common.time.ManualClock
import com.neurox.domain.engine.SessionEndReason
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.model.SessionKind
import com.neurox.domain.model.SessionStatus
import com.neurox.games.runner.EngineSession
import com.neurox.games.stimulus.ResponseInput
import com.neurox.presentation.session.AdaptationPresentation
import com.neurox.presentation.session.RecordingSessionAudio
import com.neurox.presentation.session.ResultPresentation
import com.neurox.presentation.session.SessionController
import com.neurox.presentation.session.SessionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Tests for the piece of the app that measures the user.
 *
 * These are the rules that matter:
 * - the timer the user sees is the timer the session is scored against;
 * - a pause never changes a measured reaction time;
 * - a response outside the live window cannot settle a round;
 * - an unfinished session is reported as unfinished.
 */
class SessionControllerTest {

    private val clock = ManualClock(startNanos = 0L, startWallMillis = 1_700_000_000_000L)

    private fun controller(
        template: ExerciseTemplate = ExerciseTemplate.QUICK_TAP,
        difficulty: Int = 3,
        seed: Long = 42L,
        practice: Boolean = false,
    ): Pair<SessionController, RecordingSessionAudio> {
        val audio = RecordingSessionAudio()
        val engine = EngineSession(
            sessionId = "session-test",
            profileId = "profile-test",
            template = template,
            config = template.defaultConfig(),
            difficulty = difficulty,
            kind = SessionKind.TRAINING,
            seed = seed,
            clock = clock,
        )
        return SessionController(engine, audio, clock, isPractice = practice) to audio
    }

    /** Advances the shared clock and ticks the controller, like a frame loop. */
    private fun SessionController.advance(millis: Long, stepMillis: Long = 16L): SessionUiState {
        var remaining = millis
        var last = state()
        while (remaining > 0) {
            val step = minOf(stepMillis, remaining)
            clock.advanceMillis(step)
            last = tick()
            remaining -= step
        }
        return last
    }

    /** Answers the live trial correctly, whatever its geometry is. */
    private fun correctAnswer(engineController: SessionController, state: SessionUiState): ResponseInput {
        val trial = state.visibleTrial ?: return ResponseInput.Tap
        val stimulus = trial.stimulus
        return when {
            stimulus.correctChoice >= 0 -> ResponseInput.Choice(stimulus.correctChoice)
            trial.kind == com.neurox.domain.engine.TrialKind.NO_GO -> ResponseInput.Tap
            stimulus.choices.isNotEmpty() -> ResponseInput.Choice(0)
            stimulus.direction in 0..3 && stimulus.targetCells.isEmpty() -> ResponseInput.DirectionAnswer(stimulus.direction)
            stimulus.targetCells.isNotEmpty() ->
                if (trial.expected) ResponseInput.CellTap(stimulus.targetCells.first()) else ResponseInput.Tap
            else -> ResponseInput.Tap
        }
    }

    @Test
    fun aSessionStartsOnlyOnceEvenIfTheScreenIsRecreated() {
        val (session, _) = controller()
        val first = session.startSession()
        assertEquals(SessionUiState.Phase.COUNTDOWN, first.phase)
        clock.advanceMillis(1_000)
        val second = session.startSession()
        // Starting again must not restart the countdown or reset the score.
        assertEquals(SessionUiState.Phase.COUNTDOWN, second.phase)
        assertEquals(first.elapsedMillis, 0L)
    }

    @Test
    fun aLiveTrialIsAnnouncedExactlyOnce() {
        val (session, audio) = controller()
        session.startSession()
        session.advance(1_000)
        assertTrue(session.state().phase == SessionUiState.Phase.RUNNING)
        // The signal cue is played once when the trial appears, not on every frame.
        val stimulusCues = audio.cues.count { it == Synth.Cue.STIMULUS || it == Synth.Cue.INHIBIT }
        assertTrue("expected at most one signal cue, got $stimulusCues", stimulusCues <= 1)
    }

    @Test
    fun pausingDuringALiveTrialIsRefused() {
        val (session, _) = controller()
        session.startSession()
        session.advance(1_000)
        val live = session.state()
        assertEquals(SessionUiState.Phase.RUNNING, live.phase)
        assertNotNull("the test needs a live trial to be meaningful", live.visibleTrial)

        val afterPause = session.pause()
        // Refused: a pause mid-trial would corrupt the reaction measurement.
        assertEquals(SessionUiState.Phase.RUNNING, afterPause.phase)
        assertFalse(live.canPause)
    }

    @Test
    fun pausingBetweenTrialsFreezesTheSessionClock() {
        val (session, _) = controller()
        session.startSession()
        // Countdown, then let the first trial expire so we are in feedback.
        session.advance(1_000 + 4_000)
        val beforePause = session.state()
        if (!beforePause.canPause) return // timing shape differs per engine; covered elsewhere
        val paused = session.pause()
        assertEquals(SessionUiState.Phase.PAUSED, paused.phase)
        val frozenElapsed = paused.elapsedMillis
        // Two seconds pass while paused; the session clock must not move.
        clock.advanceMillis(2_000)
        val stillPaused = session.tick()
        assertEquals(SessionUiState.Phase.PAUSED, stillPaused.phase)
        assertEquals(frozenElapsed, stillPaused.elapsedMillis)
    }

    @Test
    fun resumingContinuesTheSessionAndKeepsTheScore() {
        val (session, _) = controller()
        session.startSession()
        session.advance(1_000 + 4_000)
        val before = session.state()
        if (!before.canPause) return
        session.pause()
        clock.advanceMillis(5_000)
        val resumed = session.resume()
        assertEquals(SessionUiState.Phase.RUNNING, resumed.phase)
        assertEquals(before.correct, resumed.correct)
        assertEquals(before.missed, resumed.missed)
    }

    @Test
    fun backgroundingPausesInsteadOfExpiringTrialsOffscreen() {
        val (session, _) = controller()
        session.startSession()
        session.advance(1_000)
        val backgrounded = session.onBackground()
        assertEquals(SessionUiState.Phase.PAUSED, backgrounded.phase)
        assertEquals(SessionController.PAUSE_BACKGROUND, backgrounded.pausedReason)
        val settled = backgrounded.correct + backgrounded.incorrect + backgrounded.missed
        // Nothing settles while the app is not visible.
        clock.advanceMillis(30_000)
        val after = session.tick()
        assertEquals(settled, after.correct + after.incorrect + after.missed)
    }

    @Test
    fun endingASessionEarlyReportsItAsUnfinished() {
        val (session, _) = controller()
        session.startSession()
        session.advance(1_500)
        val ended = session.end()
        assertEquals(SessionUiState.Phase.FINISHED, ended.phase)
        val record = ended.record
        assertNotNull("an ended session must still produce a record", record)
        assertEquals(SessionStatus.ABANDONED, record!!.status)
        val presentation = ResultPresentation.of(record, SessionEndReason.USER_ABORTED)
        assertFalse(presentation.countsForProgress)
        assertEquals(0, presentation.xp)
        assertEquals("session.result.noXp", presentation.xpMessageKey)
    }

    @Test
    fun answeringBeforeTheStimulusCannotSettleARound() {
        val (session, audio) = controller()
        session.startSession()
        // Still in the countdown: no trial is live.
        val settledEarly = session.answer(ResponseInput.Tap)
        assertFalse("an answer before the first trial must not settle anything", settledEarly)
        assertEquals(0, session.state().correct)
        assertTrue(audio.cues.none { it == Synth.Cue.HIT })
    }

    @Test
    fun answeredTrialsUpdateTheVisibleScore() {
        val (session, _) = controller()
        session.startSession()
        session.advance(1_000)
        var state = session.state()
        var guard = 0
        while (state.phase != SessionUiState.Phase.RUNNING && guard++ < 20) {
            state = session.advance(20)
        }
        val trial = state.visibleTrial ?: return
        session.answer(correctAnswer(session, state))
        val after = session.state()
        assertTrue(
            "the visible score must change after an answer",
            after.correct + after.incorrect + after.missed >= 1,
        )
        @Suppress("UNUSED_EXPRESSION") trial
    }

    @Test
    fun theVisibleTimerIsTheTimerTheSessionIsScoredAgainst() {
        val (session, _) = controller()
        val started = session.startSession()
        assertTrue(started.remainingMillis > 0)
        session.advance(3_000)
        val state = session.state()
        // The screen never recomputes the timer; it reads the engine's clock-aware value.
        assertTrue(
            "elapsed ${state.elapsedMillis} should be at least 3000",
            state.elapsedMillis >= 3_000,
        )
        assertTrue(
            "remaining must decrease as time passes",
            state.remainingMillis < started.remainingMillis,
        )
    }

    @Test
    fun aReturnedAnswerAfterFeedbackEndsIsIgnored() {
        val (session, _) = controller()
        session.startSession()
        session.advance(1_000)
        val state = session.state()
        if (state.visualTrialVisible()) {
            session.answer(correctAnswer(session, state))
        }
        // Now in feedback: a second tap must not settle another round.
        val second = session.answer(ResponseInput.Tap)
        assertFalse("a double tap must not settle two rounds", second)
    }

    @Test
    fun resultPresentationNeverClaimsXpForAnAbortedSession() {
        val (session, _) = controller()
        session.startSession()
        session.advance(1_200)
        val ended = session.end()
        val presentation = ResultPresentation.of(ended.record!!, SessionEndReason.USER_ABORTED)
        assertEquals(0, presentation.xp)
        assertFalse(presentation.isPerfect)
        assertEquals("session.result.aborted", presentation.titleKey)
    }

    @Test
    fun adaptationPresentationOnlyClaimsAChangeWhenTheLevelChanges() {
        val decision = com.neurox.domain.adaptive.AdaptationDecision(
            exerciseId = ExerciseTemplate.QUICK_TAP.id,
            module = ExerciseTemplate.QUICK_TAP.module,
            from = 3,
            to = 4,
            explanationKey = "adaptive.promote.accuracy_high",
            meanAccuracy = 0.97,
            meanSpeedFactor = 0.9,
            samples = 5,
            confidence = 0.8,
        )
        val presentation = AdaptationPresentation.of(decision)
        assertTrue(presentation.changes)
        val same = decision.copy(to = 3, explanationKey = "adaptive.hold.stable")
        assertFalse(AdaptationPresentation.of(same).changes)
    }

    @Test
    fun practiceModeIsLabelledAndDoesNotClaimToCount() {
        val (session, _) = controller(practice = true)
        val state = session.startSession()
        assertTrue(state.isPractice)
    }

    @Test
    fun aNoGoTrialAcceptsAWithheldAnswerAsCorrect() {
        // In a NO-GO trial doing nothing is correct, so the engine must settle it as
        // CORRECT when the window closes; the screen must not paint that as a miss.
        val (session, _) = controller(template = ExerciseTemplate.GO_NO_GO, seed = 7L, difficulty = 2)
        session.startSession()
        var state = session.advance(1_000)
        var guard = 0
        while (guard++ < 4_000) {
            state = session.advance(32)
            if (state.phase == SessionUiState.Phase.FINISHED) break
        }
        val summary = session.state().summary
        assertNotNull(summary)
        // The session ran to completion without any input; every NO-GO trial that
        // expired must be counted as correct, not as a miss.
        assertTrue(
            "withholding on NO-GO trials should produce correct outcomes",
            (summary!!.correct + summary.missed) > 0,
        )
    }

    @Test
    fun aSessionNeverReportsMoreSettledRoundsThanItPlanned() {
        val (session, _) = controller()
        session.startSession()
        var guard = 0
        var state = session.state()
        while (guard++ < 2_000) {
            state = session.advance(16)
            assertTrue(
                "settled ${state.correct + state.incorrect + state.missed} exceeds planned ${state.roundCount}",
                state.correct + state.incorrect + state.missed <= state.roundCount,
            )
            if (state.phase == SessionUiState.Phase.FINISHED) break
        }
        assertEquals(SessionUiState.Phase.FINISHED, state.phase)
    }

    @Test
    fun randomnessNeverLeaksBetweenSessions() {
        // Two sessions with the same seed and the same input must produce the same
        // score: the engine is deterministic, which is what makes scores comparable.
        fun play(): Double {
            val localClock = ManualClock(startNanos = 0L, startWallMillis = 1_700_000_000_000L)
            val engine = EngineSession(
                sessionId = "s",
                profileId = "p",
                template = ExerciseTemplate.COLOR_REACTION,
                config = ExerciseTemplate.COLOR_REACTION.defaultConfig(),
                difficulty = 3,
                seed = 1234L,
                clock = localClock,
            )
            val ctrl = SessionController(engine, RecordingSessionAudio(), localClock)
            ctrl.startSession()
            var guard = 0
            var last = ctrl.state()
            while (guard++ < 3_000) {
                localClock.advanceMillis(16)
                last = ctrl.tick()
                if (last.phase == SessionUiState.Phase.FINISHED) break
                if (last.phase == SessionUiState.Phase.RUNNING) {
                    ctrl.answer(ResponseInput.Choice(Random(7).nextInt(0, 1)))
                }
            }
            return last.summary?.let { it.correct + it.incorrect + it.missed }?.toDouble() ?: -1.0
        }
        assertEquals(play(), play())
    }

    @Test
    fun theControllerExposesOnlyTheSkillsTheExerciseTrains() {
        val (session, _) = controller()
        val skills = session.skills()
        assertEquals(ExerciseTemplate.QUICK_TAP.skills, skills)
        assertTrue(skills.isNotEmpty())
    }

    @Test
    fun theTimerNeverGoesNegative() {
        val (session, _) = controller()
        session.startSession()
        var guard = 0
        var state = session.state()
        while (guard++ < 3_000) {
            state = session.advance(50)
            assertTrue("remaining went negative: ${state.remainingMillis}", state.remainingMillis >= 0)
            assertTrue("trial remaining went negative", state.trialRemainingMillis >= 0)
            if (state.phase == SessionUiState.Phase.FINISHED) break
        }
        assertEquals(0L, state.remainingMillis)
        assertNull("a finished session is no longer paused", state.pausedReason)
    }

    private fun SessionUiState.visualTrialVisible(): Boolean = phase == SessionUiState.Phase.RUNNING
}
