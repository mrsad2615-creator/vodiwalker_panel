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

    /**
     * The month/day switch now, and the switch now lives behind the overflow.
     *
     * It was an icon on the title row. Four 48 dp controls on that row squeezed
     * the title to one letter per line on a 320 dp screen, so the two secondary
     * actions moved into a menu. This test drives the menu the way a user does:
     * open it, tap the row, then confirm the result.
     */
    private fun toggleView() {
        rule.onNodeWithTag(HgTestTags.OVERFLOW_BUTTON).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.MENU_VIEW).performClick()
        rule.waitForIdle()
    }

    @Test
    fun theViewSwitchIsReachable() {
        openPhotos()
        rule.onNodeWithTag(HgTestTags.OVERFLOW_BUTTON).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.MENU_VIEW).assertIsDisplayed()
    }

    @Test
    fun switchingToMonthAndBackKeepsTheGridAlive() {
        openPhotos()

        toggleView()
        rule.onNodeWithTag(HgTestTags.PHOTOS_GRID).assertExists()

        toggleView()
        rule.onNodeWithTag(HgTestTags.PHOTOS_GRID).assertExists()
    }

    @Test
    fun repeatedSwitchingDoesNotCrashTheScreen() {
        openPhotos()
        repeat(4) { toggleView() }
        rule.onNodeWithTag(HgTestTags.OVERFLOW_BUTTON).assertIsDisplayed()
    }
}
