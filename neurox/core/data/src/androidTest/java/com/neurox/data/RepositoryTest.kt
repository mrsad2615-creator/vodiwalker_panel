package com.neurox.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neurox.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: NeuroxDatabase
    private lateinit var repository: NeuroxRepository
    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, NeuroxDatabase::class.java).build()
        repository = NeuroxRepository(context, db)
        repository.initialize()
    }
    @After fun tearDown() { db.close() }
    private fun finished(config: TrainingConfig) = Checkpoint(config,
        List(config.rounds) { Attempt(Outcome.CORRECT, 400) }, 2000, true)

    @Test fun completionIsIdempotent() = runBlocking {
        val config = TrainingConfig(Exercise.FOCUS_LOCK, rounds = 2)
        val session = repository.startSession("guest", config)
        repository.saveSession(session.id, "guest", finished(config))
        repository.saveSession(session.id, "guest", finished(config))
        val values = repository.sessions("guest").first()
        assertEquals(1, values.size)
        assertEquals(30, values.single().xp)
        assertEquals("COMPLETE", values.single().status)
    }
    @Test fun historiesAreProfileScoped() = runBlocking {
        val id = repository.createProfile("Local user")
        repository.startSession(id, TrainingConfig(Exercise.QUICK_TAP))
        assertTrue(repository.sessions("guest").first().isEmpty())
        assertEquals(1, repository.sessions(id).first().size)
        assertNull(repository.activeSession("guest"))
    }
    @Test fun crossProfileSaveIsRejected() = runBlocking {
        val id = repository.createProfile("Other user")
        val config = TrainingConfig(Exercise.FOCUS_LOCK, rounds = 1)
        val session = repository.startSession(id, config)
        var rejected = false
        try { repository.saveSession(session.id, "guest", finished(config)) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertEquals("ACTIVE", repository.sessions(id).first().single().status)
    }
    @Test fun interruptedSessionDoesNotEarnRewards() = runBlocking {
        val config = TrainingConfig(Exercise.FOCUS_LOCK, rounds = 2)
        val session = repository.startSession("guest", config)
        repository.saveSession(session.id, "guest", Checkpoint(config, listOf(Attempt(Outcome.CORRECT, 400)), 1500, false), aborted = true)
        val value = repository.sessions("guest").first().single()
        assertEquals("ABORTED", value.status)
        assertEquals(0, value.xp)
        assertFalse(value.valid)
    }
    @Test fun duplicateActiveSessionIsRejected() = runBlocking {
        repository.startSession("guest", TrainingConfig(Exercise.FOCUS_LOCK))
        var rejected = false
        try { repository.startSession("guest", TrainingConfig(Exercise.QUICK_TAP)) } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
    }
    @Test fun profileDeletionCascadesWithoutTouchingGuest() = runBlocking {
        val id = repository.createProfile("Delete me")
        repository.startSession(id, TrainingConfig(Exercise.FOCUS_LOCK))
        repository.setFavorite(id, Exercise.FOCUS_LOCK, true)
        repository.saveTheme(id, "Personal", 0xFFD4B982, 0xFF765A28)
        repository.deleteProfile(id)
        assertTrue(repository.sessions(id).first().isEmpty())
        assertTrue(repository.favorites(id).first().isEmpty())
        assertTrue(repository.themes(id).first().isEmpty())
        assertNotNull(db.dao().profile("guest"))
    }
    @Test fun oldCheckpointCannotOverwriteNewProgress() = runBlocking {
        val config = TrainingConfig(Exercise.FOCUS_LOCK, rounds = 3)
        val session = repository.startSession("guest", config)
        val newer = Checkpoint(config, listOf(Attempt(Outcome.CORRECT, 300)), 1500, false)
        repository.saveSession(session.id, "guest", newer)
        repository.saveSession(session.id, "guest", Checkpoint(config, emptyList(), 0, false))
        assertEquals(newer, CheckpointCodec.decode(repository.sessions("guest").first().single().checkpoint))
    }
    @Test fun checkpointRoundTripAndVersionValidation() {
        val config = TrainingConfig(Exercise.KEY_PRECISION, language = Language.FA, rounds = 1)
        val value = Checkpoint(config, listOf(Attempt(Outcome.INCORRECT, 3000, 10, 1, weakKeys = mapOf("س" to 1))), 3000, true)
        assertEquals(value, CheckpointCodec.decode(CheckpointCodec.encode(value)))
        var rejected = false
        try { CheckpointCodec.decode("{\"version\":999}") } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }
    @Test fun themeCannotMoveBetweenProfiles() = runBlocking {
        val other = repository.createProfile("Other")
        repository.saveTheme("guest", "Guest theme", 0xFFD4B982, 0xFF765A28, "stable-theme-id")
        var rejected = false
        try { repository.saveTheme(other, "Stolen", 0xFFD4B982, 0xFF765A28, "stable-theme-id") }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertEquals("guest", db.dao().theme("stable-theme-id")?.profileId)
    }
    @Test fun sessionConfigurationIsImmutable() = runBlocking {
        val config = TrainingConfig(Exercise.FOCUS_LOCK, rounds = 2)
        val session = repository.startSession("guest", config)
        var rejected = false
        try { repository.saveSession(session.id, "guest", finished(config.copy(difficulty = 2))) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertEquals(1, repository.sessions("guest").first().single().difficulty)
    }

    @Test fun versionOneCheckpointsUpgradeWithoutDataLoss() {
        val legacy = """{"version":1,"exercise":"FOCUS_LOCK","language":"FA","difficulty":1,"rounds":2,"limit":5000,"baseline":false,"elapsed":1500,"completed":false,"attempts":[{"outcome":"CORRECT","response":400,"correctChars":0,"incorrectChars":0,"words":0,"readingMs":0,"weak":{}}]}"""
        val checkpoint = CheckpointCodec.decode(legacy)
        assertEquals(0L, checkpoint.config.stimulusSeed)
        assertEquals(1, checkpoint.attempts.size)
        assertEquals(1500L, checkpoint.elapsedMs)
        val encoded = CheckpointCodec.encode(checkpoint)
        assertEquals(3, org.json.JSONObject(encoded).getInt("version"))
        assertEquals(checkpoint, CheckpointCodec.decode(encoded))
    }
    @Test fun newExerciseConfigurationsAndSeedSurviveStorage() = runBlocking {
        for (exercise in listOf(Exercise.VISUAL_SEARCH, Exercise.GO_NO_GO, Exercise.PHRASE_READING, Exercise.WORD_SPRINT)) {
            val id = repository.createProfile(exercise.name)
            val config = TrainingConfig(exercise, stimulusSeed = Long.MAX_VALUE, rounds = 3)
            val session = repository.startSession(id, config)
            val saved = CheckpointCodec.decode(requireNotNull(repository.activeSession(id)).checkpoint)
            assertEquals(config, saved.config)
            repository.saveSession(session.id, id, saved, aborted = true)
            assertEquals(0, repository.sessions(id).first().single().xp)
        }
    }
    @Test fun noGoWithholdingSurvivesCheckpointRoundTrip() {
        val config = TrainingConfig(Exercise.GO_NO_GO, rounds = 3, stimulusSeed = 99)
        val attempts = Stimuli.goPlan(config).map { go -> if (go) Attempt(Outcome.CORRECT, 300) else Attempt(Outcome.WITHHELD, null) }
        val cp = Checkpoint(config, attempts, 20_000, true)
        val decoded = CheckpointCodec.decode(CheckpointCodec.encode(cp))
        assertEquals(cp, decoded)
        assertEquals(1, Scoring.summarize(decoded).withheld)
        assertEquals(300.0, requireNotNull(Scoring.summarize(decoded).meanResponseMs), 0.01)
    }

    @Test fun customExerciseCrudAndDuplicateKeepIndependentIdentity() = runBlocking {
        val bp = ExerciseBlueprint("My pattern", Exercise.PATTERN_MATCH, difficulty = 3, rounds = 7, targetAccuracyPercent = 90, errorAllowance = 1)
        val id = repository.saveCustomExercise("guest", bp)
        val original = repository.customExercise("guest", id)
        assertEquals(bp, original.blueprint())
        repository.saveCustomExercise("guest", bp.copy(name = "Renamed"), id)
        assertEquals(original.createdAt, repository.customExercise("guest", id).createdAt)
        val duplicate = repository.duplicateCustomExercise("guest", id)
        assertNotEquals(id, duplicate)
        assertEquals(2, repository.customExercises("guest").first().size)
        repository.setCustomFavorite("guest", id, true)
        repository.deleteCustomExercise("guest", id)
        assertEquals(1, repository.customExercises("guest").first().size)
        assertTrue(repository.favorites("guest").first().isEmpty())
        assertNotNull(repository.customExercise("guest", duplicate))
    }
    @Test fun customExerciseRejectsCrossProfileOverwriteAndRead() = runBlocking {
        val other = repository.createProfile("Other")
        val bp = ExerciseBlueprint("Local", Exercise.WORD_SPRINT)
        val id = repository.saveCustomExercise("guest", bp)
        var blocked = 0
        try { repository.saveCustomExercise(other, bp.copy(name = "Overwrite"), id) } catch (_: IllegalArgumentException) { blocked++ }
        try { repository.customExercise(other, id) } catch (_: IllegalArgumentException) { blocked++ }
        try { repository.duplicateCustomExercise(other, id) } catch (_: IllegalArgumentException) { blocked++ }
        assertEquals(3, blocked)
        assertEquals("Local", repository.customExercise("guest", id).name)
    }
    @Test fun customDeletionKeepsSessionSnapshotAndRewards() = runBlocking {
        val bp = ExerciseBlueprint("History snapshot", Exercise.FOCUS_LOCK, rounds = 1)
        val id = repository.saveCustomExercise("guest", bp)
        val config = bp.configuration(Language.EN, 42, id)
        val session = repository.startSession("guest", config)
        repository.saveSession(session.id, "guest", finished(config))
        repository.deleteCustomExercise("guest", id)
        val history = repository.sessions("guest").first().single()
        assertEquals("History snapshot", CheckpointCodec.decode(history.checkpoint).config.customExerciseName)
        assertEquals(30, history.xp)
    }
    @Test fun customTestRunsCannotEarnXp() = runBlocking {
        val config = ExerciseBlueprint("Try", Exercise.FOCUS_LOCK, rounds = 1).configuration(Language.EN, 42, practice = true)
        val session = repository.startSession("guest", config)
        repository.saveSession(session.id, "guest", finished(config))
        val result = repository.sessions("guest").first().single()
        assertEquals("COMPLETE", result.status)
        assertFalse(result.valid)
        assertEquals(0, result.xp)
    }
    @Test fun invalidBlueprintDoesNotReplaceExistingData() = runBlocking {
        val bp = ExerciseBlueprint("Safe", Exercise.FOCUS_LOCK)
        val id = repository.saveCustomExercise("guest", bp)
        var rejected = false
        try { repository.saveCustomExercise("guest", bp.copy(rounds = 0), id) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertEquals(bp, repository.customExercise("guest", id).blueprint())
    }
    @Test fun versionTwoCheckpointsReceiveSafeGoalDefaults() {
        val versionTwo = """{"version":2,"stimulusSeed":42,"exercise":"FOCUS_LOCK","language":"EN","difficulty":1,"rounds":2,"limit":5000,"baseline":false,"elapsed":0,"completed":false,"attempts":[]}"""
        val decoded = CheckpointCodec.decode(versionTwo)
        assertEquals(42L, decoded.config.stimulusSeed)
        assertEquals(80, decoded.config.targetAccuracyPercent)
        assertEquals(30, decoded.config.errorAllowance)
        assertFalse(decoded.config.practice)
        assertEquals(decoded, CheckpointCodec.decode(CheckpointCodec.encode(decoded)))
    }

    @Test fun appendCannotRewriteAlreadyRecordedAttempts() = runBlocking {
        val config = TrainingConfig(Exercise.FOCUS_LOCK, rounds = 3)
        val session = repository.startSession("guest", config)
        val previous = Checkpoint(config, listOf(Attempt(Outcome.INCORRECT, 300)), 1000, false)
        repository.saveSession(session.id, "guest", previous)
        val changed = previous.copy(attempts = listOf(Attempt(Outcome.CORRECT, 300), Attempt(Outcome.CORRECT, 200)), elapsedMs = 2000)
        var rejected = false
        try { repository.saveSession(session.id, "guest", changed) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertEquals(previous, CheckpointCodec.decode(repository.sessions("guest").first().single().checkpoint))
    }
    @Test fun abortUsesLatestDurableProgressAndCannotBeReactivated() = runBlocking {
        val config = TrainingConfig(Exercise.FOCUS_LOCK, rounds = 3)
        val session = repository.startSession("guest", config)
        // Simulates a queued write committing after a caller read the active row.
        val stale = requireNotNull(repository.activeSession("guest"))
        val latest = Checkpoint(config, listOf(Attempt(Outcome.CORRECT, 250)), 1000, false)
        repository.saveSession(session.id, "guest", latest)
        repository.abortSession(stale.id, "guest")
        repository.saveSession(session.id, "guest", latest.copy(elapsedMs = 2000))
        val saved = repository.sessions("guest").first().single()
        assertEquals("ABORTED", saved.status)
        assertEquals(latest, CheckpointCodec.decode(saved.checkpoint))
        assertEquals(0, saved.xp)
        assertFalse(saved.valid)
        assertNull(repository.activeSession("guest"))
        repository.startSession("guest", TrainingConfig(Exercise.QUICK_TAP))
    }
    @Test fun abortCannotChangeAnotherProfileOrCompletedSession() = runBlocking {
        val owner = repository.createProfile("Owner")
        val config = TrainingConfig(Exercise.FOCUS_LOCK, rounds = 1)
        val session = repository.startSession(owner, config)
        var rejected = false
        try { repository.abortSession(session.id, "guest") } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertEquals("ACTIVE", repository.activeSession(owner)?.status)
        repository.saveSession(session.id, owner, finished(config))
        val completed = repository.sessions(owner).first().single()
        repository.abortSession(session.id, owner)
        assertEquals(completed, repository.sessions(owner).first().single())
    }

}
