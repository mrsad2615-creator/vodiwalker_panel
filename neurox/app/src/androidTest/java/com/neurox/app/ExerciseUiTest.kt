package com.neurox.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neurox.data.NeuroxDatabase
import com.neurox.data.NeuroxRepository
import com.neurox.domain.Exercise
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Each test uses a new in-memory database and a managed ViewModel lifecycle. */
@RunWith(AndroidJUnit4::class)
class ExerciseUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: NeuroxDatabase
    private lateinit var model: AppViewModel
    private val store = ViewModelStore()

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, NeuroxDatabase::class.java).build()
        val repository = NeuroxRepository(context, db)
        runBlocking { repository.initialize(); repository.selectProfile("guest") }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repository, SavedStateHandle()) as T
        }
        compose.runOnUiThread { model = ViewModelProvider(store, factory)[AppViewModel::class.java] }
        compose.setContent { NeuroxApp(model) }
        compose.waitUntil(10_000) { model.state.value.profile != null && !model.state.value.loading }
    }
    @After fun tearDown() {
        compose.runOnUiThread { store.clear() }
        db.close()
    }
    @Test fun visualSearchShowsRealGridAndCanPause() {
        compose.runOnUiThread { model.start(Exercise.VISUAL_SEARCH, 3, 1) }
        compose.waitUntil(10_000) { model.training.value?.exercise == Exercise.VISUAL_SEARCH }
        compose.onNodeWithText("فقط T ایستاده را پیدا کنید").assertIsDisplayed()
        compose.onAllNodes(hasClickAction() and hasContentDescription("حرف", substring = true)).assertCountEquals(4)
        compose.onNodeWithText("مکث").performClick()
        compose.onNodeWithText("جلسه متوقف است").assertIsDisplayed()
    }
    @Test fun sprintSubmitsOneWordRatherThanCompletingTheRound() {
        compose.runOnUiThread { model.start(Exercise.WORD_SPRINT, 2, 1) }
        compose.waitUntil(10_000) { model.training.value?.exercise == Exercise.WORD_SPRINT }
        val target = requireNotNull(model.training.value).typingTarget
        compose.onNodeWithText("اینجا تایپ کنید").performScrollTo().performTextInput(target)
        compose.onNodeWithText("ثبت واژه").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.training.value?.sprintWordIndex == 2 }
        compose.onNodeWithText("واژه ۲ از ۴").performScrollTo().assertIsDisplayed()
        check(model.training.value?.attempts?.isEmpty() == true)
    }
    @Test fun phraseReadingDoesNotOfferAnEarlyQuestionButton() {
        compose.runOnUiThread { model.start(Exercise.PHRASE_READING, 1, 1) }
        compose.waitUntil(10_000) { model.training.value?.exercise == Exercise.PHRASE_READING }
        compose.onNodeWithText("خواندم؛ نمایش پرسش").assertDoesNotExist()
        compose.onNodeWithText(requireNotNull(model.training.value?.phrase).text).assertIsDisplayed()
    }
}
