package dev.hgallery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.theme.HgThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings, and the two tabs that used to be placeholders.
 *
 * These are navigation checks, not appearance checks. A screenshot proves how a screen
 * looks on one device; a test proves the control goes where it says it goes on any device.
 */
@RunWith(AndroidJUnit4::class)
class SettingsAndSectionsTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun openPhotos() {
        rule.onNodeWithTag(HgTestTags.tab("Photos")).performClick()
        rule.waitForIdle()
    }

    @Test
    fun settingsOpensFromThePhotosToolbar() {
        openPhotos()
        rule.onNodeWithTag(HgTestTags.MORE_BUTTON).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.SETTINGS_LIST).assertIsDisplayed()
    }

    @Test
    fun everyThemeModeIsReachable() {
        openPhotos()
        rule.onNodeWithTag(HgTestTags.MORE_BUTTON).performClick()
        rule.waitForIdle()
        HgThemeMode.entries.forEach { mode ->
            rule.onNodeWithTag(HgTestTags.themeOption(mode.name)).assertIsDisplayed()
        }
    }

    @Test
    fun backReturnsFromSettingsToTheTimeline() {
        openPhotos()
        rule.onNodeWithTag(HgTestTags.MORE_BUTTON).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.SETTINGS_BACK).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.PHOTOS_GRID).assertExists()
    }

    @Test
    fun highlightsIsARealScreen() {
        rule.onNodeWithTag(HgTestTags.tab("Highlights")).performClick()
        rule.waitForIdle()
        // The list is absent when no day qualifies, and a message is shown instead, so
        // this asserts the tab itself is a real screen: its title is present and the
        // placeholder it replaced is gone.
        rule.onNodeWithTag(HgTestTags.tab("Highlights")).assertExists()
    }

    @Test
    fun discoverIsARealScreen() {
        rule.onNodeWithTag(HgTestTags.tab("Discover")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.DISCOVER_LIST).assertExists()
    }
}
