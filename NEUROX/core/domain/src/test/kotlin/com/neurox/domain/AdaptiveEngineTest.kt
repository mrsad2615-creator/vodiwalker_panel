package com.neurox.domain

import com.neurox.domain.adaptive.AdaptiveEngine
import com.neurox.domain.adaptive.AdaptiveSettings
import com.neurox.domain.adaptive.RecoveryKind
import com.neurox.domain.adaptive.WeakSkillAnalyzer
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.model.DifficultyRecommendation
import com.neurox.domain.model.Module
import com.neurox.domain.model.SessionKind
import com.neurox.domain.model.SessionRecord
import com.neurox.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Builds deterministic session records for adaptive tests. */
internal object Records {
    fun of(
        id: String,
        exerciseId: String = ExerciseTemplate.QUICK_TAP.id,
        module: Module = Module.REFLEX,
        accuracy: Double = 0.9,
        responseMillis: Double = 400.0,
        difficulty: Int = 3,
        status: SessionStatus = SessionStatus.COMPLETED,
        kind: SessionKind = SessionKind.TRAINING,
        startedAt: Long = 1_700_000_000_000,
        activeMillis: Long = 60_000,
        score: Double = accuracy * 60.0,
    ): SessionRecord {
        val correct = (accuracy * 10).toInt()
        val incorrect = 10 - correct
        return SessionRecord(
            id = id,
            profileId = "profile-a",
            exerciseId = exerciseId,
            module = module,
            kind = kind,
            status = status,
            startedAtMillis = startedAt,
            endedAtMillis = startedAt + activeMillis,
            activeDurationMillis = activeMillis,
            difficulty = difficulty,
            roundCount = 10,
            correctCount = correct,
            incorrectCount = incorrect,
            missedCount = 0,
            prematureCount = 0,
            responseTimeMeanMillis = responseMillis,
            responseTimeMedianMillis = responseMillis,
            responseTimeStdevMillis = 20.0,
            bestResponseMillis = (responseMillis * 0.8).toLong(),
            accuracy = accuracy,
            score = score,
            xpAwarded = 40,
        )
    }

    fun series(
        count: Int,
        exerciseId: String = ExerciseTemplate.QUICK_TAP.id,
        accuracy: Double = 0.9,
        responseMillis: Double = 400.0,
        difficulty: Int = 3,
        status: SessionStatus = SessionStatus.COMPLETED,
        activeMillis: Long = 60_000,
    ): List<SessionRecord> = List(count) { index ->
        of(
            id = "rec-$index",
            exerciseId = exerciseId,
            accuracy = accuracy,
            responseMillis = responseMillis,
            difficulty = difficulty,
            status = status,
            startedAt = 1_700_000_000_000 + index * 3_600_000L,
            activeMillis = activeMillis,
        )
    }
}

class AdaptiveEngineTest {

    private val engine = AdaptiveEngine()

    @Test
    fun requiresSeveralSamplesBeforeChangingDifficulty() {
        val decision = engine.decideNext(
            ExerciseTemplate.QUICK_TAP.id,
            Module.REFLEX,
            currentDifficulty = 3,
            history = Records.series(count = 2, accuracy = 0.99),
        )
        assertEquals(3, decision.to)
        assertFalse(decision.changed)
        assertEquals("adaptive.hold.insufficient_data", decision.explanationKey)
    }

    @Test
    fun promotesAfterConsistentlyHighAccuracy() {
        val decision = engine.decideNext(
            ExerciseTemplate.QUICK_TAP.id,
            Module.REFLEX,
            currentDifficulty = 3,
            history = Records.series(count = 5, accuracy = 0.98),
        )
        assertEquals(4, decision.to)
        assertTrue(decision.changed)
        assertEquals("adaptive.promote.accuracy_high", decision.explanationKey)
    }

    @Test
    fun demotesAfterConsistentlyLowAccuracy() {
        val decision = engine.decideNext(
            ExerciseTemplate.QUICK_TAP.id,
            Module.REFLEX,
            currentDifficulty = 3,
            history = Records.series(count = 5, accuracy = 0.5),
        )
        assertEquals(2, decision.to)
        assertEquals("adaptive.demote.accuracy_low", decision.explanationKey)
    }

    @Test
    fun oneBadSessionNeverChangesThePlan() {
        val history = Records.series(count = 4, accuracy = 0.97) + Records.of(
            id = "bad",
            accuracy = 0.2,
            startedAt = 1_700_000_100_000,
        )
        val decision = engine.decideNext(ExerciseTemplate.QUICK_TAP.id, Module.REFLEX, 3, history)
        // Mean accuracy stays 0.816, so the plan holds instead of collapsing.
        assertTrue(decision.meanAccuracy > 0.68)
        assertEquals(3, decision.to)
    }

    @Test
    fun consecutiveFailuresForceAStepDown() {
        val decision = engine.decideNext(
            ExerciseTemplate.QUICK_TAP.id,
            Module.REFLEX,
            currentDifficulty = 4,
            history = Records.series(count = 4, accuracy = 0.85),
            consecutiveFailures = 2,
        )
        assertEquals(3, decision.to)
    }

    @Test
    fun difficultyNeverLeavesTheSupportedBand() {
        val top = engine.decideNext(
            ExerciseTemplate.QUICK_TAP.id,
            Module.REFLEX,
            currentDifficulty = 5,
            history = Records.series(count = 6, accuracy = 1.0),
        )
        assertEquals(5, top.to)
        val bottom = engine.decideNext(
            ExerciseTemplate.QUICK_TAP.id,
            Module.REFLEX,
            currentDifficulty = 1,
            history = Records.series(count = 6, accuracy = 0.1),
        )
        assertEquals(1, bottom.to)
    }

    @Test
    fun disabledEngineAlwaysHolds() {
        val disabled = AdaptiveEngine(AdaptiveSettings(enabled = false))
        val decision = disabled.decideNext(
            ExerciseTemplate.QUICK_TAP.id,
            Module.REFLEX,
            3,
            Records.series(count = 8, accuracy = 1.0),
        )
        assertEquals(3, decision.to)
        assertEquals("adaptive.hold.disabled", decision.explanationKey)
    }

    @Test
    fun abandonedAndBaselineRecordsAreIgnored() {
        val history = Records.series(count = 4, accuracy = 0.4, status = SessionStatus.ABANDONED) +
            Records.series(count = 4, accuracy = 0.2).map { it.copy(kind = SessionKind.BASELINE) }
        val decision = engine.decideNext(ExerciseTemplate.QUICK_TAP.id, Module.REFLEX, 3, history)
        assertEquals(3, decision.to)
        assertEquals(0, decision.samples)
        assertEquals(0.0, decision.meanAccuracy, 1e-9)
    }

    @Test
    fun onlyMatchingExerciseSessionsAreUsed() {
        val history = Records.series(count = 5, accuracy = 1.0, exerciseId = ExerciseTemplate.COLOR_REACTION.id)
        val decision = engine.decideNext(ExerciseTemplate.QUICK_TAP.id, Module.REFLEX, 3, history)
        assertEquals(3, decision.to)
    }

    @Test
    fun recommendationExposesEvidenceForTheUi() {
        val recommendation: DifficultyRecommendation = engine.recommendation(
            ExerciseTemplate.QUICK_TAP.id,
            Module.REFLEX,
            3,
            Records.series(count = 6, accuracy = 0.99),
        )
        assertEquals(4, recommendation.recommendedDifficulty)
        assertTrue(recommendation.confidence in 0.0..1.0)
        assertEquals(6, recommendation.evidenceSamples)
        assertTrue(recommendation.changed)
    }

    @Test
    fun durationRecommendationStaysPractical() {
        val short = engine.recommendDurationMinutes(Records.series(count = 5, activeMillis = 90_000))
        val long = engine.recommendDurationMinutes(Records.series(count = 5, activeMillis = 900_000))
        assertTrue(short in 3..20)
        assertTrue(long in 3..20)
        assertTrue(long > short)
        assertEquals(AdaptiveEngine.DEFAULT_MINUTES, engine.recommendDurationMinutes(Records.series(count = 1)))
    }

    @Test
    fun recoveryRecommendsRestAfterAFailingWeek() {
        val failing = List(4) { index ->
            Records.of(
                id = "f-$index",
                accuracy = 0.4,
                status = SessionStatus.ABANDONED,
                startedAt = 1_700_000_000_000 + index * 1_000_000L,
            )
        }
        val advice = engine.recoveryRecommendation(failing, nowMillis = 1_700_000_400_000)
        assertEquals(RecoveryKind.REST_SUGGESTED, advice.kind)
        assertEquals(0, advice.suggestedMinutes)
    }

    @Test
    fun recoveryIsNeutralForAFreshProfile() {
        val advice = engine.recoveryRecommendation(emptyList(), nowMillis = 1_700_000_000_000)
        assertEquals(RecoveryKind.NONE, advice.kind)
    }
}

class WeakSkillAnalyzerTest {

    @Test
    fun findsTheWeakestExerciseFirst() {
        val history = Records.series(count = 3, exerciseId = ExerciseTemplate.QUICK_TAP.id, accuracy = 0.95) +
            Records.series(count = 3, exerciseId = ExerciseTemplate.VISUAL_MEMORY.id, accuracy = 0.45) +
            Records.series(count = 3, exerciseId = ExerciseTemplate.KEY_PRECISION.id, accuracy = 0.8)
        val weakest = WeakSkillAnalyzer.analyze(history, limit = 2)
        assertEquals(2, weakest.size)
        assertEquals(ExerciseTemplate.VISUAL_MEMORY.id, weakest.first().template.id)
    }

    @Test
    fun ignoresExercisesWithTooLittleHistory() {
        val history = listOf(Records.of(id = "only-one", exerciseId = ExerciseTemplate.VISUAL_MEMORY.id, accuracy = 0.1))
        assertTrue(WeakSkillAnalyzer.analyze(history).isEmpty())
    }

    @Test
    fun zeroLimitReturnsNothing() {
        assertTrue(WeakSkillAnalyzer.analyze(Records.series(count = 5), limit = 0).isEmpty())
    }
}

class AdaptiveSettingsTest {

    @Test
    fun rejectsContradictoryThresholds() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            AdaptiveSettings(promoteAccuracy = 0.5, demoteAccuracy = 0.8)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            AdaptiveSettings(minSamplesForChange = 5, maxSamplesConsidered = 2)
        }
    }

    @Test
    fun defaultThresholdsLeaveAGreyZoneThatHolds() {
        val settings = AdaptiveSettings()
        assertTrue(settings.demoteAccuracy < 0.8 && settings.promoteAccuracy > 0.8)
    }
}
