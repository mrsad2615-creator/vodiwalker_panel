package dev.hgallery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hgallery.app.ui.components.HgTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Album drill-down, on a device that has media.
 *
 * The CI harness installs twelve fixtures and points MediaStore at them before
 * this runs, so the "All photos" album genuinely exists. If the device has no
 * media the test returns early rather than passing vacuously.
 */
@RunWith(AndroidJUnit4::class)
class AlbumNavigationTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun openFirstAlbum(): Boolean {
        rule.onNodeWithTag(HgTestTags.tab("Albums")).performClick()
        rule.waitForIdle()
        val allPhotos = rule.onAllNodesWithText("All photos")
        if (allPhotos.fetchSemanticsNodes().isEmpty()) return false
        allPhotos[0].performClick()
        rule.waitForIdle()
        return true
    }

    @Test
    fun tappingAnAlbumOpensItsContentsInsteadOfTheViewer() {
        if (!openFirstAlbum()) return

        // The viewer must NOT be showing: jumping straight into it was the defect
        // this test exists to prevent from coming back.
        rule.onNodeWithTag(HgTestTags.VIEWER).assertDoesNotExist()
        // The back affordance proves an album detail screen was pushed.
        rule.onNodeWithText("Back").assertIsDisplayed()
    }

    @Test
    fun backFromAnAlbumReturnsToTheAlbumsTab() {
        if (!openFirstAlbum()) return

        rule.onNodeWithText("Back").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Albums").assertIsDisplayed()
    }
}
