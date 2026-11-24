package com.neurox.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.neurox.data.NeuroxDatabase
import com.neurox.data.NeuroxRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule

/** Isolated database and ViewModel. Timed publication is tested separately without
 * Compose/Espresso's idle synchronization, which can drain the fast reflex timer. */
abstract class NeuroxUiFixture {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: NeuroxDatabase
    protected lateinit var model: AppViewModel
    private val store = ViewModelStore()

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, NeuroxDatabase::class.java).build()
        val repository = NeuroxRepository(context, db)
        runBlocking { repository.initialize(); repository.selectProfile("guest") }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repository, SavedStateHandle(), automaticTicks = false) as T
        }
        compose.runOnUiThread { model = ViewModelProvider(store, factory)[AppViewModel::class.java] }
        compose.setContent { NeuroxApp(model) }
        compose.waitUntil(10_000) { model.state.value.profile != null && !model.state.value.loading }
    }
    @After fun tearDown() {
        compose.runOnUiThread { store.clear() }
        db.close()
    }
}
