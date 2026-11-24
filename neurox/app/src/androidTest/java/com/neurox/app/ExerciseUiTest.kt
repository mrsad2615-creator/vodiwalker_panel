package com.neurox.app

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neurox.domain.Exercise
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseUiTest : NeuroxUiFixture() {
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
