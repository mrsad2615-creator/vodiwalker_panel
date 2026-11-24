package com.neurox.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PreferencesTest {
    private lateinit var db: NeuroxDatabase
    private lateinit var file: File
    private lateinit var job: CompletableJob
    private lateinit var repository: NeuroxRepository
    private fun reopen(context: Context): NeuroxRepository {
        job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
        return NeuroxRepository(context, db, store)
    }
    @Before fun setUp(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NeuroxDatabase::class.java).build()
        file = File(context.cacheDir, "preferences-${java.util.UUID.randomUUID()}.preferences_pb")
        repository = reopen(context)
        repository.initialize()
    }
    @After fun tearDown(): Unit = runBlocking {
        job.cancelAndJoin()
        db.close()
        file.delete()
    }
    @Test fun freshInstallationNeedsIntroductionAndHasAnOptionalDefaultGoal(): Unit = runBlocking {
        assertFalse(repository.welcomeComplete.first())
        assertEquals(15, repository.dailyGoal("guest").first())
    }
    @Test fun welcomeAndGoalSurviveADataStoreReopen(): Unit = runBlocking {
        repository.completeWelcome()
        repository.setDailyGoal("guest", 25)
        job.cancelAndJoin()
        repository = reopen(ApplicationProvider.getApplicationContext())
        assertTrue(repository.welcomeComplete.first())
        assertEquals(25, repository.dailyGoal("guest").first())
    }
    @Test fun goalsAreProfileScopedAndInvalidValuesPreserveTheOriginal(): Unit = runBlocking {
        val other = repository.createProfile("Other")
        repository.setDailyGoal("guest", 30)
        repository.setDailyGoal(other, 10)
        for (bad in listOf(0, 4, 61, Int.MAX_VALUE)) {
            var rejected = false
            try { repository.setDailyGoal("guest", bad) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
        assertEquals(30, repository.dailyGoal("guest").first())
        assertEquals(10, repository.dailyGoal(other).first())
    }
    @Test fun missingProfileCannotCreateAGoalAndDeletedProfileLeavesNoGoal(): Unit = runBlocking {
        var rejected = false
        try { repository.setDailyGoal("missing", 10) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        val id = repository.createProfile("Disposable")
        repository.setDailyGoal(id, 45)
        repository.deleteProfile(id)
        assertEquals(15, repository.dailyGoal(id).first())
        assertNotNull(db.dao().profile("guest"))
    }
}
