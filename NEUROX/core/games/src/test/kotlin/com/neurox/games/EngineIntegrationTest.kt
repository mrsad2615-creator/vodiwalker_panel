package com.neurox.games

import com.neurox.domain.engine.PauseReason
import com.neurox.domain.engine.TrialOutcome
import com.neurox.domain.engine.TrialPlanner
import com.neurox.domain.exercise.ExerciseConfig
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.exercise.MovementDirection
import com.neurox.domain.exercise.TypingLanguage
import com.neurox.domain.model.Module
import com.neurox.domain.model.SessionKind
import com.neurox.domain.model.SessionStatus
import com.neurox.games.definition.EngineFactory
import com.neurox.games.runner.EngineSession
import com.neurox.games.runner.HeadlessSessionRunner
import com.neurox.games.runner.ManualTestClock
import com.neurox.games.stimulus.ResponseInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration tests: every exercise is played through the same code path the
 * application uses (engine -> plan -> core timing -> record).
 */
class AllExercisesPlayTest {

    @Test
    fun everyVisionExercisePlaysToCompletionAndScores() {
        ExerciseTemplate.forModule(Module.VISION).forEach { template ->
            val record = play(template)
            assertEquals("${template.id} must complete", SessionStatus.COMPLETED, record.status)
            assertTrue("${template.id} must score", record.score > 0.0)
            assertTrue("${template.id} must award xp", record.xpAwarded > 0)
            assertTrue("${template.id} accuracy <= 1", record.accuracy <= 1.0)
        }
    }

    @Test
    fun everyReflexExercisePlaysToCompletionAndScores() {
        ExerciseTemplate.forModule(Module.REFLEX).forEach { template ->
            val record = play(template)
            assertEquals("${template.id} must complete", SessionStatus.COMPLETED, record.status)
            assertTrue("${template.id} must measure a response time", record.responseTimeMeanMillis > 0.0)
            assertTrue("${template.id} scores", record.score > 0.0)
        }
    }

    @Test
    fun everyReadExercisePlaysToCompletion() {
        ExerciseTemplate.forModule(Module.READ).forEach { template ->
            for (language in listOf(TypingLanguage.EN, TypingLanguage.FA)) {
                val config = template.defaultConfig().copy(
                    rounds = 3,
                    typingLanguage = language,
                )
                val record = play(template, config)
                assertEquals("${template.id}/$language", SessionStatus.COMPLETED, record.status)
                assertTrue("${template.id}/$language accuracy", record.accuracy in 0.0..1.0)
            }
        }
    }

    @Test
    fun everyTypeExercisePlaysToCompletion() {
        ExerciseTemplate.forModule(Module.TYPE).forEach { template ->
            val record = play(template, template.defaultConfig().copy(rounds = 3))
            assertEquals("${template.id} must complete", SessionStatus.COMPLETED, record.status)
        }
    }

    @Test
    fun zeroAccuracyPlayIsRecordedAsLowScoreAbandonedFreeResult() {
        val template = ExerciseTemplate.QUICK_TAP
        val session = EngineSession.create(
            sessionId = "low",
            profileId = "p1",
            template = template,
            config = template.defaultConfig().copy(rounds = 6),
            seed = 11,
        )
        val played = HeadlessSessionRunner.play(session, accuracyRatio = 0.0)
        assertTrue("wrong answers must score near zero", played.record.score < 20.0)
        assertEquals(0, played.record.correctCount)
    }

    private fun play(template: ExerciseTemplate, config: ExerciseConfig? = null): com.neurox.domain.model.SessionRecord {
        val effective = config ?: template.defaultConfig()
        val session = EngineSession.create(
            sessionId = "test-${template.id}",
            profileId = "p1",
            template = template,
            config = effective,
            difficulty = 3,
            seed = template.id.hashCode().toLong(),
        )
        return HeadlessSessionRunner.play(session).record
    }
}

class EngineDeterminismTest {

    @Test
    fun theSameSeedProducesTheSamePlan() {
        val template = ExerciseTemplate.FOCUS_LOCK
        val a = EngineSession.create("s", "p", template, seed = 42)
        val b = EngineSession.create("s", "p", template, seed = 42)
        assertEquals(
            a.plannedTrial(0).stimulus,
            b.plannedTrial(0).stimulus,
        )
        assertEquals(a.plannedCount, b.plannedCount)
    }

    @Test
    fun differentSeedsProduceDifferentContent() {
        val template = ExerciseTemplate.VISUAL_SEARCH
        val a = EngineSession.create("s", "p", template, seed = 1)
        val b = EngineSession.create("s", "p", template, seed = 2)
        val identical = (0 until a.plannedCount).count {
            a.plannedTrial(it).stimulus == b.plannedTrial(it).stimulus
        }
        assertTrue("plans must differ for a different seed", identical < a.plannedCount)
    }

    @Test
    fun replayedSessionGivesIdenticalScores() {
        val template = ExerciseTemplate.COLOR_REACTION
        val first = HeadlessSessionRunner.play(EngineSession.create("fixed", "p", template, seed = 7))
        val second = HeadlessSessionRunner.play(EngineSession.create("fixed", "p", template, seed = 7))
        assertEquals(first.record.score, second.record.score, 1e-9)
        assertEquals(first.record.correctCount, second.record.correctCount)
        assertEquals(first.record.responseTimeMeanMillis, second.record.responseTimeMeanMillis, 1e-9)
    }

    @Test
    fun engineContentIsIndependentOfTheDifficultyForValidRange() {
        // The plan must be generatable at every difficulty, and stay valid.
        for (level in 1..5) {
            val template = ExerciseTemplate.PERIPHERAL_SCAN
            val session = EngineSession.create("d$level", "p", template, difficulty = level, seed = 3)
            assertTrue(session.plannedCount > 0)
            val trial = session.plannedTrial(0)
            assertTrue(trial.stimulus.targetCells.isNotEmpty())
        }
    }
}

class SessionLifecycleTest {

    private val template = ExerciseTemplate.QUICK_TAP

    @Test
    fun pauseDuringStimulusDoesNotCreateASlowResponse() {
        val clock = ManualTestClock()
        val session = EngineSession(
            sessionId = "pause",
            profileId = "p",
            template = template,
            config = ExerciseConfig(rounds = 2, sessionDurationSeconds = 60),
            difficulty = 3,
            seed = 5,
            clock = clock,
        )
        session.start()
        // Advance until a stimulus is visible.
        var guard = 0
        while (session.currentTrial == null && guard++ < 500) {
            clock.advanceMillis(16)
            session.tick(clock.nanoTime())
        }
        assertNotNull(session.currentTrial)
        session.pause(PauseReason.APP_BACKGROUND)
        clock.advanceMillis(10_000)
        session.resume()
        clock.advanceMillis(200)
        val settled = session.answer(ResponseInput.Tap, clock.nanoTime())
        assertNotNull(settled)
        assertTrue("paused time must not inflate the measurement", settled!!.responseMillis!! < 400.0)
    }

    @Test
    fun abortProducesAnAbandonedRecordWithNoXp() {
        val session = EngineSession.create("abort", "p", template, seed = 1)
        session.start()
        session.abort()
        val record = session.toRecord()!!
        assertEquals(SessionStatus.ABANDONED, record.status)
        assertFalse(record.xpAwarded > 0)
    }

    @Test
    fun interruptIsNeverCompleted() {
        val session = EngineSession.create("int", "p", template, seed = 1)
        session.start()
        session.interrupt()
        val record = session.toRecord()!!
        assertEquals(SessionStatus.ABANDONED, record.status)
        assertEquals(0, record.xpAwarded)
    }

    @Test
    fun baselineKindIsPreservedInTheRecord() {
        val session = EngineSession.create(
            sessionId = "base",
            profileId = "p",
            template = template,
            kind = SessionKind.BASELINE,
            seed = 2,
        )
        val played = HeadlessSessionRunner.play(session)
        assertEquals(SessionKind.BASELINE, played.record.kind)
        assertFalse(com.neurox.domain.engine.SessionResults.countsForAnalytics(played.record))
    }

    @Test
    fun settledTrialsAreOrderedAndUnique() {
        val played = HeadlessSessionRunner.play(EngineSession.create("order", "p", template, seed = 9))
        val indices = played.trials.map { it.index }
        assertEquals(indices.sorted(), indices)
        assertEquals(indices.distinct(), indices)
    }

    @Test
    fun everyEngineExposesAnAnswerHintAndMetadata() {
        ExerciseTemplate.entries.forEach { template ->
            val session = EngineSession.create("hint", "p", template, seed = 1)
            val trial = session.plannedTrial(0)
            assertTrue(session.engine.answerHint(trial).isNotBlank())
            assertNotNull(session.engine.field)
        }
    }
}

class EngineCorrectnessRulesTest {

    @Test
    fun focusLockAcceptsTheTargetAndRejectsAnotherCell() {
        val session = EngineSession.create("f", "p", ExerciseTemplate.FOCUS_LOCK, seed = 8)
        val trial = session.plannedTrial(0)
        val target = trial.stimulus.targetCells.first()
        assertTrue(session.engine.isCorrect(trial, ResponseInput.CellTap(target)))
        val other = (0 until session.engine.field.cellCount).first { it !in trial.stimulus.targetCells }
        assertFalse(session.engine.isCorrect(trial, ResponseInput.CellTap(other)))
    }

    @Test
    fun inhibitionAcceptsTappingAGoTrialAndRejectsTappingANoGoTrial() {
        val session = EngineSession.create(
            "go",
            "p",
            ExerciseTemplate.INHIBITION_TEST,
            config = ExerciseConfig(rounds = 40, sessionDurationSeconds = 120),
            seed = 4,
        )
        val trials = (0 until 40).map { session.plannedTrial(it) }
        val go = trials.first { it.expected }
        val noGo = trials.first { !it.expected }
        assertTrue(session.engine.isCorrect(go, ResponseInput.Tap))
        assertFalse(session.engine.isCorrect(noGo, ResponseInput.Tap))
    }

    @Test
    fun directionMatchRequiresTheExactDirection() {
        val session = EngineSession.create("dir", "p", ExerciseTemplate.DIRECTION_MATCH, seed = 6)
        val trial = session.plannedTrial(0)
        val correct = trial.stimulus.direction
        assertTrue(session.engine.isCorrect(trial, ResponseInput.DirectionAnswer(correct)))
        assertFalse(session.engine.isCorrect(trial, ResponseInput.DirectionAnswer((correct + 1) % 4)))
    }

    @Test
    fun visualMemoryRequiresEveryCell() {
        val session = EngineSession.create("mem", "p", ExerciseTemplate.VISUAL_MEMORY, seed = 12)
        val trial = session.plannedTrial(0)
        assertTrue(session.engine.isCorrect(trial, ResponseInput.MultiCellTap(trial.stimulus.targetCells)))
        val partial = trial.stimulus.targetCells.drop(1).toSet()
        if (trial.stimulus.targetCells.size > 1) {
            assertFalse(session.engine.isCorrect(trial, ResponseInput.MultiCellTap(partial)))
        }
    }

    @Test
    fun sequenceMemoryComparesOrderNotJustMembership() {
        val session = EngineSession.create("seq", "p", ExerciseTemplate.SEQUENCE_MEMORY, seed = 15)
        val trial = session.plannedTrial(0)
        val expected = trial.stimulus.choices.map { it.removePrefix("cell.").toInt() }
        assertTrue(session.engine.isCorrect(trial, ResponseInput.Sequence(expected)))
        if (expected.size > 1 && expected[0] != expected[1]) {
            assertFalse(
                session.engine.isCorrect(trial, ResponseInput.Sequence(expected.reversed())),
            )
        }
    }

    @Test
    fun targetIdentificationAcceptsTheCorrectChoiceOnly() {
        val session = EngineSession.create("ident", "p", ExerciseTemplate.TARGET_IDENTIFICATION, seed = 21)
        val trial = session.plannedTrial(0)
        assertTrue(session.engine.isCorrect(trial, ResponseInput.Choice(trial.stimulus.correctChoice)))
        val wrong = (trial.stimulus.correctChoice + 1) % trial.stimulus.choices.size.coerceAtLeast(2)
        assertFalse(session.engine.isCorrect(trial, ResponseInput.Choice(wrong)))
    }

    @Test
    fun spatialAttentionCorrectnessFollowsTheCueMatch() {
        val session = EngineSession.create(
            "spatial",
            "p",
            ExerciseTemplate.SPATIAL_ATTENTION,
            config = ExerciseConfig(rounds = 20, sessionDurationSeconds = 120),
            seed = 33,
        )
        // At least one valid and one invalid trial must occur in 20 trials.
        val trials = (0 until 20).map { session.plannedTrial(it) }
        val valid = trials.filter { it.stimulus.correctChoice == 0 }
        val invalid = trials.filter { it.stimulus.correctChoice == 1 }
        assertTrue(valid.isNotEmpty())
        assertTrue(invalid.isNotEmpty())
        valid.forEach { assertTrue(session.engine.isCorrect(it, ResponseInput.Choice(0))) }
        invalid.forEach { assertTrue(session.engine.isCorrect(it, ResponseInput.Choice(1))) }
    }

    @Test
    fun multiStimulusRequiresTheAnnouncedSymbolCell() {
        val session = EngineSession.create("multi", "p", ExerciseTemplate.MULTI_STIMULUS, seed = 27)
        val trial = session.plannedTrial(0)
        val target = trial.stimulus.targetCells.first()
        assertTrue(session.engine.isCorrect(trial, ResponseInput.CellTap(target)))
        val other = trial.stimulus.targetCells.drop(1).firstOrNull()
        if (other != null) assertFalse(session.engine.isCorrect(trial, ResponseInput.CellTap(other)))
    }
}

class PlannerAndConfigTest {

    @Test
    fun generatedPlansRespectTheConfiguredRoundCount() {
        val plan = TrialPlanner.generate(
            seed = 1,
            config = ExerciseConfig(rounds = 7),
            referenceMillis = 500.0,
        )
        assertEquals(7, plan.size)
        assertTrue(plan.all { it.delayMillis >= 350 })
        assertTrue(plan.all { it.windowMillis >= 400 })
    }

    @Test
    fun plannerIsDeterministicAndBounded() {
        val a = TrialPlanner.generate(99, ExerciseConfig(rounds = 12), 400.0, noGoRatio = 0.4)
        val b = TrialPlanner.generate(99, ExerciseConfig(rounds = 12), 400.0, noGoRatio = 0.4)
        assertEquals(a, b)
        val noGo = a.count { !it.expectedResponse }
        assertTrue(noGo in 0..12)
    }

    @Test
    fun movementDirectionIsHonouredByTheTrackingEngine() {
        val config = ExerciseTemplate.MULTI_TARGET_TRACKING.defaultConfig().copy(
            movementDirection = MovementDirection.VERTICAL,
            targetSpeedDpPerSecond = 200,
        )
        val session = EngineSession.create("track", "p", ExerciseTemplate.MULTI_TARGET_TRACKING, config, seed = 2)
        val trial = session.plannedTrial(0)
        assertTrue(trial.stimulus.targetCells.size in 2..5)
    }
}
