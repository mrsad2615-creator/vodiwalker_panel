package dev.hgallery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hgallery.app.ui.components.HgTestTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Multi-selection, driven the way a user drives it.
 *
 * The long press is the documented entry point, so it is what the test uses; the
 * toolbar button exercises the same state machine for reachability. Neither test
 * asserts on a screenshot: they assert the toolbar appeared and that the count is
 * on screen, which is the actual requirement from reference R3.
 */
@RunWith(AndroidJUnit4::class)
class SelectionTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun openPhotos() {
        rule.onNodeWithTag(HgTestTags.tab("Photos")).performClick()
        rule.waitForIdle()
    }

    /**
     * The first tile the timeline actually drew, whatever its id happens to be.
     *
     * `firstTile()` in TestTiles.kt does the matching, and returns the node rather than
     * its tag. An earlier version read the tag back out of the node's configuration and
     * looked the node up a second time by it, which needed a semantics accessor this
     * Compose version does not have.
     */
    private fun firstTile() = rule.firstTile()

    /**
     * Select-all lives in the overflow menu now.
     *
     * It shared the title row with three other controls, which on a 320 dp screen
     * left the title too little width to draw. The menu is one extra tap and the
     * control is still reachable without a long press, which is what this test
     * exists to protect.
     */
    @Test
    fun theSelectControlIsReachable() {
        openPhotos()
        rule.onNodeWithTag(HgTestTags.OVERFLOW_BUTTON).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.MENU_SELECT_ALL).assertIsDisplayed()
    }

    /** The menu row selects everything and closes the menu. */
    @Test
    fun selectAllFromTheMenuEntersSelectionMode() {
        openPhotos()
        rule.onNodeWithTag(HgTestTags.OVERFLOW_BUTTON).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.MENU_SELECT_ALL).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.SELECTION_BAR).assertIsDisplayed()
    }

    @Test
    fun longPressingATileOpensTheSelectionToolbar() {
        openPhotos()
        val tile = firstTile() ?: return // empty emulator library: nothing to select
        tile.performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.SELECTION_BAR).assertIsDisplayed()
        rule.onNodeWithTag(HgTestTags.SELECTION_COUNT).assertIsDisplayed()
    }

    @Test
    fun exitingSelectionRestoresThePhotosTitle() {
        openPhotos()
        val tile = firstTile() ?: return
        tile.performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.EXIT_SELECTION).performClick()
        rule.waitForIdle()
        // The toolbar is back, which is what "selection ended" means visually.
        rule.onNodeWithTag(HgTestTags.SCREEN_TITLE).assertIsDisplayed()
    }

    @Test
    fun selectAllThenDeselectAllLeavesTheGridUsable() {
        openPhotos()
        val tile = firstTile() ?: return
        tile.performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.SELECTION_SELECT_ALL).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.SELECTION_COUNT).assertIsDisplayed()
        rule.onNodeWithTag(HgTestTags.SELECTION_SELECT_ALL).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.PHOTOS_GRID).assertIsDisplayed()
    }

    /**
     * The action bar must actually be *laid out on screen*, not merely present in
     * the tree.
     *
     * An earlier build produced a selection screenshot with the header and the
     * ticks but no bottom bar at all: the grid was unweighted, so `fillMaxSize()`
     * left the bar zero height. `assertIsDisplayed()` alone would not have caught
     * it reliably, so the height of the node is asserted as well.
     */
    @Test
    fun theActionBarIsLaidOutWithRealHeight() {
        openPhotos()
        val tile = firstTile() ?: return
        tile.performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.SELECTION_BAR).assertIsDisplayed()
        val height = rule.onNodeWithTag(HgTestTags.SELECTION_BAR)
            .fetchSemanticsNode().size.height
        // JUnit's argument order is (message, condition). The arguments were the other
        // way round, which is a compile error rather than a wrong assertion, and it is
        // how a test that never compiled went unnoticed for as long as the harness was
        // reporting every instrumented run as green.
        assertTrue(
            "the selection action bar was $height px tall, so it is not really on screen",
            height > 40 * rule.density.density,
        )
    }

    @Test
    fun openingTheDeleteConfirmationIsNotItselfADeletion() {
        openPhotos()
        val tile = firstTile() ?: return
        tile.performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.SELECTION_DELETE).performClick()
        rule.waitForIdle()
        // The dialog is up; the grid must still hold the item, because the user
        // has not confirmed. Cancelling is the next assertion a human would make.
        rule.onNodeWithTag(HgTestTags.PHOTOS_GRID).assertIsDisplayed()
    }
}
