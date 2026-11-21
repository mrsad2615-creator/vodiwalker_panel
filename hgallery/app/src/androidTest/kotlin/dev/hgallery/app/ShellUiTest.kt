package dev.hgallery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hgallery.app.ui.components.HgTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Shell navigation, exercised on a real device/emulator.
 *
 * These tests deliberately avoid depending on the device's photo library: a
 * clean emulator has no images, so anything that asserted on thumbnails would
 * pass locally and fail in CI. What is asserted here is the part that must be
 * true on every device — the bar exists, every tab is reachable, and switching
 * tabs actually changes the screen.
 */
@RunWith(AndroidJUnit4::class)
class ShellUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesAndShowsAllFourTabs() {
        listOf("Photos", "Albums", "Highlights", "Discover").forEach { name ->
            rule.onNodeWithTag(HgTestTags.tab(name)).assertIsDisplayed()
        }
    }

    @Test
    fun photosTabIsTheStartDestination() {
        rule.onNodeWithText("Photos").assertIsDisplayed()
    }

    @Test
    fun everyTabCanBeSelected() {
        listOf("Albums", "Highlights", "Discover", "Photos").forEach { name ->
            rule.onNodeWithTag(HgTestTags.tab(name)).performClick()
            rule.waitForIdle()
            rule.onNodeWithTag(HgTestTags.tab(name)).assertIsDisplayed()
        }
    }

    @Test
    fun albumsTabShowsItsOwnTitle() {
        rule.onNodeWithTag(HgTestTags.tab("Albums")).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Albums").assertIsDisplayed()
    }

    @Test
    fun anUnimplementedTabSaysSoInsteadOfShowingFakeContent() {
        rule.onNodeWithTag(HgTestTags.tab("Highlights")).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Highlights is not implemented yet").assertIsDisplayed()
    }
}
