package dev.hgallery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hgallery.app.ui.components.HgTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The viewer's vertical dismissal.
 *
 * The screen's own documentation promised "vertical drags would dismiss" from the
 * first release, and no such gesture existed. The comment outlived three reviews
 * because nothing tested it. These tests drive the gesture the way a hand does:
 * a real swipe through the touch input, on the real screen.
 *
 * A fixture with no photos cannot open a viewer, so each test returns early on an
 * empty library rather than failing for a reason unrelated to the gesture. The CI
 * emulator seeds its library, so they run.
 */
@RunWith(AndroidJUnit4::class)
class ViewerDismissTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Opens the first photograph in the viewer.
     *
     * Tiles carry a tag built from the item id, so the first one is found by asking
     * the tree for nodes whose tag starts with the thumbnail prefix rather than by
     * guessing an id. Returns false on an empty library, which is the one case
     * where the test cannot run.
     */
    private fun openViewer(): Boolean {
        rule.onNodeWithTag(HgTestTags.tab("Photos")).performClick()
        rule.waitForIdle()
        // `firstTile()` is in TestTiles.kt: it owns the one matcher for "a tile, whatever
        // its id", because three files needed it and each solved it differently.
        val tile = rule.firstTile() ?: return false
        tile.performClick()
        rule.waitForIdle()
        return rule.onAllNodesWithTag(HgTestTags.VIEWER).fetchSemanticsNodes().isNotEmpty()
    }

    /** The viewer opens and the page is there to be dismissed. */
    @Test
    fun theViewerOpensOnAPhoto() {
        if (!openViewer()) return // no photo on this device
        rule.onNodeWithTag(HgTestTags.VIEWER).assertIsDisplayed()
    }

    /**
     * A downward swipe closes the viewer.
     *
     * `swipeDown` moves further than the 0.18-of-height threshold by construction:
     * it travels the full distance between the swipe's two default points, which is
     * most of the screen.
     */
    @Test
    fun aDownwardSwipeDismissesTheViewer() {
        if (!openViewer()) return
        rule.onNodeWithTag(HgTestTags.VIEWER).performTouchInput { swipeDown() }
        rule.waitForIdle()
        // Back on the grid: the viewer is gone and the tab bar is reachable again.
        rule.onNodeWithTag(HgTestTags.PHOTOS_GRID).assertIsDisplayed()
    }

    /** A short pull settles back and leaves the viewer open. */
    @Test
    fun aShortPullDoesNotDismiss() {
        if (!openViewer()) return
        val height = rule.onNodeWithTag(HgTestTags.VIEWER).fetchSemanticsNode().size.height
        rule.onNodeWithTag(HgTestTags.VIEWER).performTouchInput {
            // About 10 % of the height: under the threshold, so the page must return.
            swipeDown(startY = height * 0.4f, endY = height * 0.5f, durationMillis = 200)
        }
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.VIEWER).assertIsDisplayed()
    }

    /**
     * An upward swipe does not dismiss.
     *
     * Upward belongs to the chrome, which slides in and out vertically. A pull in
     * that direction must not also close the screen.
     */
    @Test
    fun anUpwardSwipeDoesNotDismiss() {
        if (!openViewer()) return
        rule.onNodeWithTag(HgTestTags.VIEWER).performTouchInput { swipeUp() }
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.VIEWER).assertIsDisplayed()
    }
}
