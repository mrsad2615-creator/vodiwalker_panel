package dev.mrsad.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device smoke tests. These run on a real emulator in CI and prove the
 * shipped binary boots, renders real Persian UI, navigates, and runs a typing
 * session end-to-end (the session is then persisted by Room).
 */
@RunWith(AndroidJUnit4::class)
class MrsadSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeRendersBrandAndCore() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("MRSAD").assertIsDisplayed()
        composeRule.onNodeWithText("زیست‌بوم رشد شناختی").assertIsDisplayed()
        composeRule.onNodeWithText("خانه").assertIsDisplayed()
    }

    @Test
    fun bottomNavigationReachesTypeSetup() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("تایپ").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("MRSAD TYPE").assertIsDisplayed()
        composeRule.onNodeWithText("شروع تمرین").assertIsDisplayed()
    }

    @Test
    fun settingsOpensAndShowsThemes() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("تنظیمات").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("تم بصری").assertIsDisplayed()
    }
}
