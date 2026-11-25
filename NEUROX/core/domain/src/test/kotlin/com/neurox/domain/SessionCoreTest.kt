package com.neurox.domain

import com.neurox.common.time.ManualClock
import com.neurox.common.time.TimingCalibration
import com.neurox.common.time.TimingMode
import com.neurox.domain.engine.PauseReason
import com.neurox.domain.engine.SessionCore
import com.neurox.domain.engine.SessionEndReason
import com.neurox.domain.engine.SessionResults
import com.neurox.domain.engine.SessionSpec
import com.neurox.domain.engine.TrialKind
import com.neurox.domain.engine.TrialOutcome
import com.neurox.domain.engine.TrialPlan
import com.neurox.domain.engine.TrialPlanner
import com.neurox.domain.exercise.ExerciseConfig
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.model.Module
import com.neurox.domain.model.SessionKind
import com.neurox.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic tests of the trial engine. Time is fully controlled, so these
 * tests are reproducible on any machine and any frame rate.
 */
class SessionCoreTest {

    private fun spec(
        seed: Long = 7L,
        rounds: Int = 4,
        difficulty: Int = 3,
        noGoRatio: Double = 0.0,
        windowMillis: Long = 1000,
        calibration: TimingCalibration = TimingCalibration.NONE,
        durationSeconds: Int = 120,
    ): SessionSpec {
        val plan = if (noGoRatio > 0.0) {
            TrialPlanner.generate(
                seed = seed,
                config = ExerciseConfig(rounds = rounds, targetCount = 1),
                referenceMillis = 400.0,
                noGoRatio = 1.0,
            )
        } else {
            List(rounds) { index ->
                TrialPlan(
                    index = index,
                    kind = TrialKind.TARGET,
                    delayMillis = 500,
                    windowMillis = windowMillis.toInt(),
                    expectedResponse = true,
                    slot = 0,
                )
            }
        }
        return SessionSpec(
            sessionId = "sess-$seed",
            profileId = "profile-a",
            exerciseId = ExerciseTemplate.QUICK_TAP.id,
            module = Module.REFLEX,
            kind = SessionKind.TRAINING,
            difficulty = difficulty,
            config = ExerciseConfig(rounds = rounds, sessionDurationSeconds = durationSeconds),
            plan = plan,
            calibration = calibration,
            timingMode = TimingMode.VISUAL,
        )
    }

    private fun correctResponse(core: SessionCore, clock: ManualClock, afterMillis: Long) {
        clock.advanceMillis(afterMillis)
        core.tick(clock.nanoTime())
        clock.advanceMillis(350)
        core.respond(onTarget = true, atNanos = clock.nanoTime())
    }

    @Test
    fun completeSessionCountsEveryTrialOnce() {
        val clock = ManualClock()
        val core = SessionCore(spec(rounds = 4), clock)
        core.start()
        repeat(4) { correctResponse(core, clock, 500) }
        core.tick(clock.nanoTime())
        val summary = core.summary()
        assertNotNull(summary)
        assertEquals(SessionEndReason.ROUNDS_COMPLETE, summary!!.endReason)
        assertEquals(4, summary.correct)
        assertEquals(0, summary.incorrect)
        assertEquals(0, summary.missed)
        assertEquals(4, summary.settledTrials)
        assertEquals(1.0, summary.completion, 1e-9)
    }

    @Test
    fun missingTheWindowCountsAsMissed() {
        val clock = ManualClock()
        val core = SessionCore(spec(rounds = 2), clock)
        core.start()
        clock.advanceMillis(500)
        core.tick(clock.nanoTime())
        clock.advanceMillis(2_000)
        core.tick(clock.nanoTime())
        clock.advanceMillis(2_000)
        core.tick(clock.nanoTime())
        val summary = core.summary()!!
        assertEquals(2, summary.missed)
        assertEquals(0, summary.correct)
    }

    @Test
    fun respondingToANoGoSignalIsIncorrect() {
        val clock = ManualClock()
        val core = SessionCore(spec(rounds = 3, noGoRatio = 1.0), clock)
        core.start()
        repeat(3) {
            // Advance in small steps until a stimulus is on screen.
            repeat(400) {
                if (core.currentTrial == null) {
                    clock.advanceMillis(50)
                    core.tick(clock.nanoTime())
                }
            }
            assertNotNull("a NO_GO stimulus must be visible", core.currentTrial)
            clock.advanceMillis(300)
            core.respond(onTarget = true, atNanos = clock.nanoTime())
        }
        core.tick(clock.nanoTime() + 30_000_000_000L)
        val summary = core.summary()!!
        assertEquals(0, summary.correct)
        assertEquals(3, summary.incorrect)
    }

    @Test
    fun earlyTapIsPrematureAndCountsAsAnError() {
        val clock = ManualClock()
        val core = SessionCore(spec(rounds = 2), clock)
        core.start()
        clock.advanceMillis(100)
        val result = core.respond(onTarget = true, atNanos = clock.nanoTime())
        assertNotNull(result)
        assertEquals(TrialOutcome.PREMATURE, result!!.outcome)
        repeat(2) { correctResponse(core, clock, 500) }
        core.tick(clock.nanoTime())
        val summary = core.summary()!!
        assertEquals(1, summary.premature)
        val record = SessionResults.toRecord(
            summary,
            profileId = "profile-a",
            kind = SessionKind.TRAINING,
            referenceMillis = 420.0,
        )
        // Premature responses are commission errors: accuracy must drop.
        assertTrue("accuracy must include the premature error", record.accuracy < 1.0)
        assertEquals(2.0 / 3.0, record.accuracy, 1e-9)
    }

    @Test
    fun pauseStopsTheClockAndExtendsDeadlines() {
        val clock = ManualClock()
        val core = SessionCore(spec(rounds = 2), clock)
        core.start()
        clock.advanceMillis(500)
        core.tick(clock.nanoTime())
        core.pause(PauseReason.APP_BACKGROUND)
        assertTrue(core.isPaused)
        // A long pause must not create a missed trial or a slow response.
        clock.advanceMillis(60_000)
        core.tick(clock.nanoTime())
        core.resume()
        clock.advanceMillis(300)
        val result = core.respond(onTarget = true, atNanos = clock.nanoTime())
        assertNotNull(result)
        assertEquals(TrialOutcome.CORRECT, result!!.outcome)
        assertEquals(300.0, result.responseMillis!!, 1e-9)
    }

    @Test
    fun pausedTimeIsExcludedFromActiveDuration() {
        val clock = ManualClock()
        val core = SessionCore(spec(rounds = 2, durationSeconds = 20), clock)
        core.start()
        clock.advanceMillis(400)
        core.pause(PauseReason.USER)
        clock.advanceMillis(30_000)
        core.resume()
        clock.advanceMillis(600)
        core.tick(clock.nanoTime())
        val summary = core.abort()
        assertTrue("active time must be ~1s, was ${summary.activeDurationMillis}", summary.activeDurationMillis in 900..1_200)
    }

    @Test
    fun timeLimitEndsTheSession() {
        val clock = ManualClock()
        val core = SessionCore(spec(rounds = 50, durationSeconds = 20), clock)
        core.start()
        clock.advanceMillis(21_000)
        core.tick(clock.nanoTime())
        val summary = core.summary()!!
        assertEquals(SessionEndReason.TIME_LIMIT, summary.endReason)
    }

    @Test
    fun abortIsNeverRecordedAsCompleted() {
        val clock = ManualClock()
        val core = SessionCore(spec(rounds = 10), clock)
        core.start()
        correctResponse(core, clock, 500)
        correctResponse(core, clock, 500)
        val summary = core.abort()
        assertEquals(SessionEndReason.USER_ABORTED, summary.endReason)
        val record = SessionResults.toRecord(summary, "profile-a", SessionKind.TRAINING, 420.0)
        assertEquals(SessionStatus.ABANDONED, record.status)
        assertEquals(0, record.xpAwarded)
    }

    @Test
    fun interruptionEndsTheSessionWithoutRewards() {
        val clock = ManualClock()
        val core = SessionCore(spec(rounds = 10), clock)
        core.start()
        repeat(5) { correctResponse(core, clock, 500) }
        val summary = core.interrupt()
        assertEquals(SessionEndReason.INTERRUPTED, summary.endReason)
        val record = SessionResults.toRecord(summary, "profile-a", SessionKind.TRAINING, 420.0)
        assertEquals(SessionStatus.ABANDONED, record.status)
        assertEquals(0, record.xpAwarded)
    }

    @Test
    fun summaryIsStableAndIdempotentAfterCompletion() {
        val clock = ManualClock()
        val core = SessionCore(spec(rounds = 2), clock)
        core.start()
        repeat(2) { correctResponse(core, clock, 500) }
        core.tick(clock.nanoTime())
        val first = core.summary()
        val second = core.summary()
        assertEquals(first, second)
        assertTrue(core.isFinished)
        // Further input after completion changes nothing.
        core.respond(onTarget = true, atNanos = clock.nanoTime())
        assertEquals(first, core.summary())
    }

    @Test
    fun duplicateResponseOnASettledTrialDoesNotCountTwice() {
        val clock = ManualClock()
        val core = SessionCore(spec(rounds = 2), clock)
        core.start()
        clock.advanceMillis(500)
        core.tick(clock.nanoTime())
        clock.advanceMillis(250)
        val first = core.respond(true, clock.nanoTime())
        assertNotNull(first)
        // Immediate second tap: no active stimulus, therefore a premature error,
        // never an extra settled trial.
        val second = core.respond(true, clock.nanoTime())
        assertEquals(TrialOutcome.PREMATURE, second!!.outcome)
        // summary() is null until the session ends, so settlement is checked via
        // the events instead of a finished summary.
        val settledEvents = core.stableEvents.count {
            it is com.neurox.domain.engine.SessionEvent.TrialSettled
        }
        assertEquals(1, settledEvents)
    }

    @Test
    fun calibrationIsAppliedToTheMeasuredResponse() {
        val clock = ManualClock()
        val calibration = TimingCalibration(inputLatencyMillis = 20.0, displayLatencyMillis = 10.0)
        val core = SessionCore(spec(rounds = 1, calibration = calibration), clock)
        core.start()
        clock.advanceMillis(500)
        core.tick(clock.nanoTime())
        clock.advanceMillis(400)
        val result = core.respond(true, clock.nanoTime())!!
        assertEquals(370.0, result.responseMillis!!, 1e-9)
    }

    @Test
    fun wallClockChangesDoNotAffectElapsedMeasurement() {
        val clock = ManualClock()
        val core = SessionCore(spec(rounds = 2), clock)
        core.start()
        clock.advanceMillis(500)
        core.tick(clock.nanoTime())
        // The user changes the system clock backwards by one hour.
        clock.advanceNanosOnly(300_000_000L)
        val result = core.respond(true, clock.nanoTime())!!
        assertEquals(300.0, result.responseMillis!!, 1e-9)
    }

    @Test
    fun tickIsSafeAtAnyFrameRate() {
        val clock = ManualClock()
        val core = SessionCore(spec(rounds = 40, durationSeconds = 10_000), clock)
        core.start()
        // 16 ms frames, respond on every stimulus exactly once.
        repeat(40) {
            var settled = false
            repeat(400) {
                clock.advanceMillis(16)
                core.tick(clock.nanoTime())
                if (!settled && core.currentTrial != null) {
                    // Simulate a 250 ms reaction, never before the frame that shows it.
                    clock.advanceMillis(250)
                    core.respond(true, clock.nanoTime())
                    settled = true
                }
                if (settled) return@repeat
            }
        }
        core.tick(clock.nanoTime() + 60_000_000_000L)
        val summary = core.summary()
        assertNotNull(summary)
        // A trial can settle once only, so settled work never exceeds the plan.
        assertTrue(summary!!.settledTrials <= 40)
        assertTrue(summary.correct in 1..40)
        assertEquals(40, summary.correct + summary.missed + summary.incorrect)
    }

    @Test
    fun emptyPlanFinishesImmediatelyWithoutRewards() {
        val clock = ManualClock()
        val core = SessionCore(spec(rounds = 1).copy(plan = emptyList()), clock)
        val summary = core.start()
        assertNotNull(summary)
        assertEquals(0, summary!!.settledTrials)
        val record = SessionResults.toRecord(summary, "profile-a", SessionKind.TRAINING, 420.0)
        assertEquals(SessionStatus.ABANDONED, record.status)
        assertEquals(0.0, record.score, 1e-9)
    }

    @Test
    fun everyStimulusEmissionIsOrderedAndCounted() {
        val clock = ManualClock()
        val core = SessionCore(spec(rounds = 6), clock)
        core.start()
        var emitted = 0
        repeat(6) {
            clock.advanceMillis(500)
            core.tick(clock.nanoTime())
            if (core.currentTrial != null) emitted += 1
            clock.advanceMillis(100)
            core.respond(true, clock.nanoTime())
        }
        assertEquals(6, emitted)
        val shown = core.stableEvents.count { it is com.neurox.domain.engine.SessionEvent.StimulusShown }
        assertEquals(6, shown)
    }

    @Test
    fun pausedSessionIgnoresInput() {
        val clock = ManualClock()
        val core = SessionCore(spec(rounds = 3), clock)
        core.start()
        clock.advanceMillis(500)
        core.tick(clock.nanoTime())
        core.pause(PauseReason.SYSTEM)
        assertNull(core.respond(true, clock.nanoTime()))
        core.resume()
        clock.advanceMillis(100)
        assertNotNull(core.respond(true, clock.nanoTime()))
    }

    @Test
    fun notStartedSessionProducesNoEvents() {
        val clock = ManualClock()
        val core = SessionCore(spec(rounds = 3), clock)
        clock.advanceMillis(5_000)
        core.tick(clock.nanoTime())
        assertNull(core.summary())
        assertFalse(core.isRunning)
    }
}

class SessionResultsTest {

    private fun summary(
        correct: Int = 8,
        incorrect: Int = 1,
        missed: Int = 1,
        times: List<Double> = List(8) { 300.0 },
        reason: SessionEndReason = SessionEndReason.ROUNDS_COMPLETE,
        completion: Double = 1.0,
        activeMillis: Long = 30_000,
    ) = com.neurox.domain.engine.SessionSummary(
        sessionId = "s1",
        exerciseId = ExerciseTemplate.QUICK_TAP.id,
        module = Module.REFLEX,
        difficulty = 3,
        endReason = reason,
        correct = correct,
        incorrect = incorrect,
        missed = missed,
        premature = 0,
        settledTrials = correct + incorrect + missed,
        plannedTrials = (correct + incorrect + missed).coerceAtLeast(1),
        responseTimesMillis = times,
        activeDurationMillis = activeMillis,
        startedAtWallMillis = 1_700_000_000_000,
        endedAtWallMillis = 1_700_000_030_000,
        completion = completion,
    )

    @Test
    fun completedSessionBecomesCountingRecord() {
        val record = SessionResults.toRecord(summary(), "p1", SessionKind.TRAINING, 420.0)
        assertEquals(SessionStatus.COMPLETED, record.status)
        assertTrue(record.accuracy in 0.7..0.9)
        assertTrue(record.score > 0.0)
        assertTrue(record.xpAwarded > 0)
        assertTrue(SessionResults.countsForAnalytics(record))
    }

    @Test
    fun timeLimitedSessionWithWorkIsCompletedButShortFragmentsAreNot() {
        val withWork = SessionResults.toRecord(
            summary(reason = SessionEndReason.TIME_LIMIT, completion = 0.6),
            "p1",
            SessionKind.TRAINING,
            420.0,
        )
        assertEquals(SessionStatus.COMPLETED, withWork.status)
        val fragment = SessionResults.toRecord(
            summary(correct = 1, incorrect = 0, missed = 0, reason = SessionEndReason.TIME_LIMIT, completion = 0.1),
            "p1",
            SessionKind.TRAINING,
            420.0,
        )
        assertEquals(SessionStatus.ABANDONED, fragment.status)
    }

    @Test
    fun baselineSessionsAreExcludedFromAnalytics() {
        val record = SessionResults.toRecord(summary(), "p1", SessionKind.BASELINE, 420.0)
        assertFalse(SessionResults.countsForAnalytics(record))
    }

    @Test
    fun xpRequiresEnoughActiveTimeAndResponses() {
        val tooShort = SessionResults.toRecord(summary(activeMillis = 1_000), "p1", SessionKind.TRAINING, 420.0)
        assertEquals(0, tooShort.xpAwarded)
        val empty = SessionResults.toRecord(summary(correct = 0, incorrect = 0, missed = 0), "p1", SessionKind.TRAINING, 420.0)
        assertEquals(0, empty.xpAwarded)
    }

    @Test
    fun comparableOnlyWithinSameExerciseAndKind() {
        val a = SessionResults.toRecord(summary(), "p1", SessionKind.TRAINING, 420.0)
        val b = a.copy(id = "s2", kind = SessionKind.CUSTOM)
        assertFalse(SessionResults.comparable(a, b))
        assertTrue(SessionResults.comparable(a, a.copy(id = "s3")))
    }
}
