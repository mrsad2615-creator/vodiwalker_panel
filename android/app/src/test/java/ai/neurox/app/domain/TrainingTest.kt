package ai.neurox.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random

class TrainingTest {
    @Test fun emptyAccuracyIsZeroAndValidAccuracyIsRounded() {
        assertEquals(0, Scoring.accuracy(0, 0))
        assertEquals(67, Scoring.accuracy(2, 3))
    }

    @Test fun accuracyRejectsInvalidCounts() {
        assertThrows(IllegalArgumentException::class.java) { Scoring.accuracy(2, 1) }
        assertThrows(IllegalArgumentException::class.java) { Scoring.accuracy(-1, 3) }
    }

    @Test fun typingWpmUsesFiveCharactersPerWordConvention() {
        assertEquals(60, Scoring.wordsPerMinute(25, 5_000))
        assertEquals(0, Scoring.wordsPerMinute(25, 0))
    }

    @Test fun adaptiveDifficultyNeedsSeveralTrialsAndUsesConservativeThresholds() {
        assertEquals(4, Scoring.nextDifficulty(4, listOf(TrialResult(true, 100))))
        assertEquals(5, Scoring.nextDifficulty(4, List(5) { TrialResult(true, 100) }))
        assertEquals(3, Scoring.nextDifficulty(4, List(5) { TrialResult(false, 100) }))
        assertEquals(1, Scoring.nextDifficulty(1, List(5) { TrialResult(false, 100) }))
    }

    @Test fun trainingRunRecordsResponseLatencyAndCompletesAtConfiguredRoundCount() {
        val run = TrainingRun(rounds = 5)
        repeat(4) { run.record(correct = it < 3, latencyMs = 120L + it) }
        assertEquals(false, run.completed)
        assertEquals(3, run.correct)
        assertEquals(4, run.count)
        run.record(correct = true, latencyMs = 124)
        assertEquals(true, run.completed)
        assertEquals(122, run.meanLatencyMs())
        val summary = run.summary(Module.REFLEX, durationMs = 1_000)
        assertEquals(4, summary.correct)
        assertEquals(5, summary.total)
        assertEquals(80, summary.accuracyPercent)
    }

    @Test fun readingAndTypingPromptsUseRequestedOfflineLanguage() {
        val english = ExerciseFactory.create(Module.TYPE, 2, language = "en", random = Random(2))
        val persian = ExerciseFactory.create(Module.TYPE, 2, language = "fa", random = Random(2))
        assert(english.answer.any { it in 'a'..'z' })
        assert(persian.answer.any { it in '\u0600'..'\u06FF' })
        val persianRead = ExerciseFactory.create(Module.READ, 2, language = "fa", random = Random(1))
        assert(persianRead.answer.any { it in '\u0600'..'\u06FF' })
    }

    @Test fun completedTrainingRunRejectsExtraInputAndNegativeLatency() {
        val run = TrainingRun(rounds = 1)
        assertThrows(IllegalArgumentException::class.java) { run.record(correct = true, latencyMs = -1) }
        run.record(correct = false, latencyMs = 1)
        assertThrows(IllegalStateException::class.java) { run.record(correct = true, latencyMs = 1) }
    }

    @Test fun incompleteRunCannotBeReportedAsCompleted() {
        val run = TrainingRun()
        assertThrows(IllegalStateException::class.java) { run.summary(Module.VISION, durationMs = 0) }
    }

    @Test fun exerciseFactoryCreatesUsablePromptsForEveryModule() {
        Module.entries.forEach { module ->
            val prompt = ExerciseFactory.create(module, difficulty = 3, random = Random(13))
            assertEquals(module == Module.TYPE, prompt.choices.isEmpty())
            assert(prompt.answer.isNotBlank())
            if (module != Module.TYPE) assert(prompt.choices.contains(prompt.answer))
        }
    }
}
