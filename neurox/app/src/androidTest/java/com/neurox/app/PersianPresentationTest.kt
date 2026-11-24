package com.neurox.app

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersianPresentationTest : NeuroxUiFixture() {
    @Test fun persianHomeUsesPersianSystemNamesAndEnglishRemainsAvailable() {
        compose.waitUntil(5_000) { compose.onAllNodesWithText("نوراکس").fetchSemanticsNodes().isNotEmpty() }
        for (name in listOf("دیداری", "واکنش", "خواندن", "تایپ")) {
            compose.onNodeWithText(name).performScrollTo().assertIsDisplayed()
        }
        compose.onAllNodesWithText("VISION").assertCountEquals(0)
        compose.onAllNodesWithText("TRAIN YOUR POTENTIAL").assertCountEquals(0)
        compose.runOnUiThread { model.updateProfile(requireNotNull(model.state.value.profile).copy(language = "EN")) }
        compose.waitUntil(5_000) { model.state.value.profile?.language == "EN" && !model.state.value.busy }
        compose.onNodeWithText("VISION").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("دیداری").assertCountEquals(0)
        compose.onNodeWithText("NEUROX").assertIsDisplayed()
    }
}
