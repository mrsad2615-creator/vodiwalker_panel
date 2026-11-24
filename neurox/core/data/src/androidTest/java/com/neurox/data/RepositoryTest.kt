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

}
