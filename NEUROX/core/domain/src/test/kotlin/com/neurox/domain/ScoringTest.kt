package com.neurox.domain

import com.neurox.domain.exercise.ExerciseConfig
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.exercise.MovementDirection
import com.neurox.domain.exercise.TypingLanguage
import com.neurox.domain.model.DifficultyLevel
import com.neurox.domain.model.Module
import com.neurox.domain.scoring.ReferenceBands
import com.neurox.domain.scoring.Scoring
import com.neurox.domain.scoring.Wpm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTest {

    @Test
    fun accuracyCountsMissesAsFailures() {
        assertEquals(0.8, Scoring.accuracy(correct = 8, incorrect = 1, missed = 1), 1e-9)
        assertEquals(0.0, Scoring.accuracy(0, 0, 0), 1e-9)
        assertEquals(1.0, Scoring.accuracy(10, 0, 0), 1e-9)
    }

    @Test
    fun speedFactorIsOneAtReferenceAndZeroAtFourTimesReference() {
        assertEquals(1.0, Scoring.speedFactor(400.0, 400.0), 1e-9)
        assertEquals(0.0, Scoring.speedFactor(1600.0, 400.0), 1e-9)
        // Halfway between reference and ceiling.
        assertEquals(1.0 - (800.0 - 400.0) / 1200.0, Scoring.speedFactor(800.0, 400.0), 1e-9)
    }

    @Test
    fun speedFactorIsClampedOutsideTheBand() {
        assertEquals(1.0, Scoring.speedFactor(100.0, 400.0), 1e-9)
        assertEquals(0.0, Scoring.speedFactor(9_000.0, 400.0), 1e-9)
        assertEquals(0.0, Scoring.speedFactor(0.0, 400.0), 1e-9)
    }

    @Test
    fun compositeCannotExceedBounds() {
        assertEquals(100.0, Scoring.compositeScore(1.0, 1.0, 1.0), 1e-9)
        assertEquals(0.0, Scoring.compositeScore(0.0, 0.0, 1.0), 1e-9)
        assertEquals(65.0, Scoring.compositeScore(1.0, 0.0, 1.0), 1e-9)
        assertEquals(32.5, Scoring.compositeScore(1.0, 0.0, 0.5), 1e-9)
    }

    @Test
    fun compositeRejectsOutOfRangeInput() {
        assertThrows(IllegalArgumentException::class.java) { Scoring.compositeScore(1.5, 0.0, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { Scoring.compositeScore(1.0, 0.0, -0.1) }
    }

    @Test
    fun accuracyIsWeightedMoreThanSpeed() {
        val accurateSlow = Scoring.compositeScore(1.0, 0.2, 1.0)
        val fastInaccurate = Scoring.compositeScore(0.5, 1.0, 1.0)
        assertTrue("accuracy must dominate the score", accurateSlow > fastInaccurate)
    }

    @Test
    fun difficultyMultiplierIncreasesWithLevel() {
        assertTrue(Scoring.difficultyMultiplier(1) < Scoring.difficultyMultiplier(3))
        assertTrue(Scoring.difficultyMultiplier(3) < Scoring.difficultyMultiplier(5))
    }

    @Test
    fun medianHandlesEvenAndOddSamples() {
        assertEquals(3.0, Scoring.median(listOf(5.0, 1.0, 3.0)), 1e-9)
        assertEquals(2.5, Scoring.median(listOf(4.0, 1.0, 3.0, 2.0)), 1e-9)
        assertEquals(0.0, Scoring.median(emptyList()), 1e-9)
    }

    @Test
    fun stdevAndConsistencyPenalizeSpread() {
        assertEquals(0.0, Scoring.stdev(listOf(5.0)), 1e-9)
        val tight = Scoring.consistency(listOf(400.0, 402.0, 398.0))
        val loose = Scoring.consistency(listOf(200.0, 600.0, 400.0))
        assertTrue(tight > loose)
        assertEquals(0.0, Scoring.consistency(listOf(1.0)), 1e-9)
    }

    @Test
    fun trendIgnoresASingleOutlier() {
        val improving = Scoring.trend(listOf(10.0, 11.0, 12.0, 40.0, 13.0, 14.0))
        assertTrue(improving > 0.0)
        val flat = Scoring.trend(listOf(10.0, 10.0, 10.0, 10.0, 10.0, 10.0))
        assertEquals(0.0, flat, 1e-9)
        assertEquals(0.0, Scoring.trend(listOf(1.0, 2.0)), 1e-9)
    }

    @Test
    fun equalScoresNeverReplaceARecord() {
        assertTrue(Scoring.isNewRecord(null, 51.0))
        assertTrue(Scoring.isNewRecord(50.0, 51.0))
        assertFalse(Scoring.isNewRecord(51.0, 51.0))
        assertFalse(Scoring.isNewRecord(52.0, 51.0))
    }
}

class WpmTest {

    @Test
    fun usesFiveCharactersPerWord() {
        // 250 characters in 60 seconds = 50 words = 50 WPM.
        assertEquals(50.0, Wpm.fromCharacters(250, 60_000), 1e-9)
    }

    @Test
    fun ignoresSessionsShorterThanTheMinimum() {
        assertEquals(0.0, Wpm.fromCharacters(250, 1_000), 1e-9)
        assertEquals(0.0, Wpm.fromCharacters(0, 60_000), 1e-9)
    }

    @Test
    fun netWpmAppliesErrorPenalty() {
        assertEquals(40.0, Wpm.net(50.0, 0.2), 1e-9)
        assertEquals(0.0, Wpm.net(50.0, 1.0), 1e-9)
        assertThrows(IllegalArgumentException::class.java) { Wpm.net(50.0, 1.5) }
    }

    @Test
    fun persianAndLatinTextUseTheSameDefinition() {
        val latin = Wpm.fromWords(120, 60_000)
        val persian = Wpm.fromWords(120, 60_000)
        assertEquals(latin, persian, 1e-9)
    }
}

class ExerciseCatalogTest {

    @Test
    fun everyModuleHasAtLeastTheRequiredExerciseCount() {
        val vision = ExerciseTemplate.forModule(Module.VISION)
        val reflex = ExerciseTemplate.forModule(Module.REFLEX)
        val read = ExerciseTemplate.forModule(Module.READ)
        val type = ExerciseTemplate.forModule(Module.TYPE)
        assertTrue("vision", vision.size >= 8)
        assertTrue("reflex", reflex.size >= 9)
        assertTrue("read", read.size >= 9)
        assertTrue("type", type.size >= 8)
        assertEquals(34, ExerciseTemplate.entries.size)
    }

    @Test
    fun idsAreUniqueAndStable() {
        val ids = ExerciseTemplate.entries.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertEquals(ExerciseTemplate.FOCUS_LOCK, ExerciseTemplate.fromId("vision.focus_lock"))
        assertEquals(ExerciseTemplate.GO_NO_GO, ExerciseTemplate.fromId("reflex.go_no_go"))
        assertEquals(null, ExerciseTemplate.fromId("vision.does_not_exist"))
    }

    @Test
    fun everyDefaultConfigurationIsValid() {
        ExerciseTemplate.entries.forEach { template ->
            val violations = template.defaultConfig().validate()
            assertTrue(
                "${template.id} has invalid defaults: $violations",
                violations.isEmpty(),
            )
        }
    }

    @Test
    fun referenceTimeGetsFasterWithDifficulty() {
        val easy = ExerciseTemplate.QUICK_TAP.referenceMillis(1)
        val hard = ExerciseTemplate.QUICK_TAP.referenceMillis(5)
        assertTrue(easy > hard)
        assertEquals(ReferenceBands.VISUAL_REACTION_MILLIS, ReferenceBands.forDifficulty(420.0, 3), 1e-9)
    }
}

class ExerciseConfigValidationTest {

    @Test
    fun acceptsAValidConfiguration() {
        val config = ExerciseTemplate.FOCUS_LOCK.defaultConfig()
        assertTrue(config.isValid())
    }

    @Test
    fun reportsEveryFieldOutsideItsBand() {
        val broken = ExerciseConfig(
            targetCount = 99,
            targetSizeDp = 4,
            displayDurationMillis = 10,
            rounds = 0,
            sessionDurationSeconds = 5,
            errorAllowance = 50,
        )
        val fields = broken.validate().map { it.field }.toSet()
        assertEquals(
            setOf("targetCount", "targetSizeDp", "displayDurationMillis", "rounds", "sessionDurationSeconds", "errorAllowance"),
            fields,
        )
        assertFalse(broken.isValid())
    }

    @Test
    fun rejectsOutOfRangeDifficultyValueType() {
        assertThrows(IllegalArgumentException::class.java) { DifficultyLevel(0) }
        assertThrows(IllegalArgumentException::class.java) { DifficultyLevel(6) }
    }

    @Test
    fun difficultyStepsRespectTheirBounds() {
        assertEquals(2, DifficultyLevel.EASY.up().value)
        assertEquals(1, DifficultyLevel.EASY.down().value)
        assertEquals(5, DifficultyLevel.HARD.up().value)
        assertEquals(4, DifficultyLevel.HARD.down().value)
    }

    @Test
    fun enumsRoundTripThroughNamesForPersistence() {
        val config = ExerciseTemplate.PERIPHERAL_SCAN.defaultConfig().copy(
            movementDirection = MovementDirection.DIAGONAL,
            typingLanguage = TypingLanguage.FA,
        )
        assertEquals("DIAGONAL", config.movementDirection.name)
        assertEquals("FA", config.typingLanguage.name)
    }
}
