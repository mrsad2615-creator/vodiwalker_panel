package com.neurox.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run in a clean install with the default Persian guest profile. */
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun homeOpensAnActualExercise() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("VISION").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("VISION").performClick()
        compose.onNodeWithText("آغاز جلسه").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("آغاز جلسه").performClick()
        compose.onNodeWithText("خانهٔ نشانه‌دار را لمس کنید").assertIsDisplayed()
        compose.onNodeWithText("مکث").performClick()
        compose.onNodeWithText("جلسه متوقف است").assertIsDisplayed()
    }
    @Test fun emptyHistoryDoesNotInventStatistics() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("خانه").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("تحلیل").performClick()
        compose.onNodeWithText("مسیر شما از اینجا شروع می‌شود").assertIsDisplayed()
    }
}
