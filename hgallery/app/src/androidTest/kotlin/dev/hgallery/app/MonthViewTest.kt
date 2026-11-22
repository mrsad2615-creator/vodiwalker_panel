package dev.hgallery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hgallery.app.ui.components.HgTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Month and day views of the Photos tab.
 *
 * Huawei's Gallery manual states the Photos tab switches between month and day
 * view by pinching. The pinch is kept, but the toolbar switch is the primary
 * control precisely because a gesture cannot be driven from an instrumented test
 * — and an untested gesture is an unchecked one.
 */
@RunWith(AndroidJUnit4::class)
class MonthViewTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun openPhotos() {
        rule.onNodeWithTag(HgTestTags.tab("Photos")).performClick()
        rule.waitForIdle()
    }

    @Test
    fun theViewSwitchIsReachable() {
        openPhotos()
        rule.onNodeWithTag(HgTestTags.VIEW_SWITCH).assertIsDisplayed()
    }

    @Test
    fun switchingToMonthAndBackKeepsTheGridAlive() {
        openPhotos()

        rule.onNodeWithTag(HgTestTags.VIEW_SWITCH).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.PHOTOS_GRID).assertExists()

        rule.onNodeWithTag(HgTestTags.VIEW_SWITCH).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.PHOTOS_GRID).assertExists()
    }

    @Test
    fun repeatedSwitchingDoesNotCrashTheScreen() {
        openPhotos()
        repeat(4) {
            rule.onNodeWithTag(HgTestTags.VIEW_SWITCH).performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag(HgTestTags.VIEW_SWITCH).assertIsDisplayed()
    }
}
