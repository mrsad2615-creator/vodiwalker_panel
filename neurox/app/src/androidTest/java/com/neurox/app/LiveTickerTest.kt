package com.neurox.app

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neurox.data.CheckpointCodec
import com.neurox.data.NeuroxDatabase
import com.neurox.data.NeuroxRepository
import com.neurox.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real SystemClock, real main-thread ticker and Room. No Compose idling or fake time. */
@RunWith(AndroidJUnit4::class)
class LiveTickerTest {
    private suspend fun exerciseTimeout(exercise: Exercise) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NeuroxDatabase::class.java).build()
        val repository = NeuroxRepository(context, db)
        val store = ViewModelStore()
        try {
            repository.initialize(); repository.selectProfile("guest")
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repository, SavedStateHandle()) as T
            }
            val model = withContext(Dispatchers.Main) { ViewModelProvider(store, factory)[AppViewModel::class.java] }
            withTimeout(10_000) { model.state.first { it.profile != null && !it.loading } }
            withContext(Dispatchers.Main) { model.start(exercise, 1, 5) }
            val waiting = withTimeout(10_000) { model.training.first { it?.phase == Phase.WAITING }!! }
            assertNull(waiting.reactionColor); assertNull(waiting.directionTrial)
            val active = withTimeout(10_000) { model.training.first { it?.phase == Phase.ACTIVE }!! }
            if (exercise == Exercise.COLOR_REACTION) assertNotNull(active.reactionColor)
            else assertNotNull(active.directionTrial)
            val saved = withTimeout(15_000) { repository.sessions("guest").first { it.singleOrNull()?.status == "COMPLETE" }.single() }
            val checkpoint = CheckpointCodec.decode(saved.checkpoint)
            assertEquals(Outcome.MISSED, checkpoint.attempts.single().outcome)
            assertNull(checkpoint.attempts.single().responseMs)
            assertTrue(checkpoint.elapsedMs >= checkpoint.config.effectiveResponseLimitMs)
            assertEquals(0, saved.xp)
            assertFalse(saved.valid)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            db.close()
        }
    }
    @Test fun colorUsesProductionTickerAndRecordsAnUnansweredTarget(): Unit = runBlocking {
        exerciseTimeout(Exercise.COLOR_REACTION)
    }
    @Test fun directionUsesProductionTickerAndRecordsAnUnansweredTarget(): Unit = runBlocking {
        exerciseTimeout(Exercise.DIRECTION_MATCH)
    }
}
