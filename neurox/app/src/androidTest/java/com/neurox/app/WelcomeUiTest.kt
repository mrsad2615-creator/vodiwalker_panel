package com.neurox.app

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neurox.domain.Exercise
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WelcomeUiTest : NeuroxUiFixture() {
    override val welcomeInitiallyComplete = false
    @Test fun freshIntroductionCanBeSkippedAndTheChoicePersists() {
        compose.waitUntil(5_000) { compose.onAllNodesWithText("رد کردن آشنایی").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("رد کردن آشنایی").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.screen.value == Screen.HOME.name && !model.state.value.busy }
        runBlocking { assertTrue(repository.welcomeComplete.first()) }
        assertTrue(model.state.value.sessions.isEmpty())
    }
    @Test fun languageAndThemeApplyDuringIntroductionAndPersist() {
        compose.waitUntil(5_000) { compose.onAllNodesWithText("ادامهٔ آشنایی").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ادامهٔ آشنایی").performScrollTo().performClick()
        compose.onNodeWithText("English").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Appearance").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Light").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.state.value.profile?.theme == "LIGHT" }
        assertEquals("EN", model.state.value.profile?.language)
        compose.onNodeWithText("Continue introduction").performScrollTo().performClick()
        compose.onNodeWithText("Begin at my own pace").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.screen.value == Screen.HOME.name }
        assertEquals("EN", model.state.value.profile?.language)
        assertEquals("LIGHT", model.state.value.profile?.theme)
    }
    @Test fun optionalBaselineOpensRealSetupWithoutAwardingAnything() {
        compose.waitUntil(5_000) { compose.onAllNodesWithText("ادامهٔ آشنایی").fetchSemanticsNodes().isNotEmpty() }
        repeat(2) { compose.onNodeWithText("ادامهٔ آشنایی").performScrollTo().performClick() }
        compose.onNodeWithText("تنظیم سنجش پایهٔ دیداری").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.screen.value == Screen.TRAINING.name }
        assertEquals(Exercise.FOCUS_LOCK.name, model.selectedExercise.value)
        assertTrue(model.setupBaseline.value)
        assertTrue(model.state.value.sessions.isEmpty())
        runBlocking { assertTrue(repository.welcomeComplete.first()) }
    }
    @Test fun requestingANewWelcomeBaselineResetsAnEarlierManualToggle() {
        compose.runOnUiThread { model.beginWelcomeBaseline() }
        compose.waitUntil(5_000) { model.screen.value == Screen.TRAINING.name && !model.state.value.busy }
        compose.onNode(isToggleable()).performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.runOnUiThread { model.beginWelcomeBaseline() }
        compose.waitUntil(5_000) { model.setupRevision.value == 2L && !model.state.value.busy }
        compose.onNode(isToggleable()).performScrollTo().assertIsOn()
        assertTrue(model.state.value.sessions.isEmpty())
    }

}
