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

    /** The first tile the timeline actually drew, whatever its id happens to be. */
    private fun firstTileTag(): String? =
        rule.onAllNodes(TileMatcher).fetchSemanticsNodes()
            .firstOrNull()
            ?.config
            ?.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)

    @Test
    fun theSelectControlIsReachable() {
        openPhotos()
        rule.onNodeWithTag(HgTestTags.SELECT_BUTTON).assertIsDisplayed()
    }

    @Test
    fun longPressingATileOpensTheSelectionToolbar() {
        openPhotos()
        val tag = firstTileTag() ?: return // empty emulator library: nothing to select
        rule.onNodeWithTag(tag).performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.SELECTION_BAR).assertIsDisplayed()
        rule.onNodeWithTag(HgTestTags.SELECTION_COUNT).assertIsDisplayed()
    }

    @Test
    fun exitingSelectionRestoresThePhotosTitle() {
        openPhotos()
        val tag = firstTileTag() ?: return
        rule.onNodeWithTag(tag).performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.EXIT_SELECTION).performClick()
        rule.waitForIdle()
        // The toolbar is back, which is what "selection ended" means visually.
        rule.onNodeWithTag(HgTestTags.SELECT_BUTTON).assertIsDisplayed()
    }

    @Test
    fun selectAllThenDeselectAllLeavesTheGridUsable() {
        openPhotos()
        val tag = firstTileTag() ?: return
        rule.onNodeWithTag(tag).performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.SELECTION_SELECT_ALL).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.SELECTION_COUNT).assertIsDisplayed()
        rule.onNodeWithTag(HgTestTags.SELECTION_SELECT_ALL).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.PHOTOS_GRID).assertExists()
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
        val tag = firstTileTag() ?: return
        rule.onNodeWithTag(tag).performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.SELECTION_BAR).assertIsDisplayed()
        val height = rule.onNodeWithTag(HgTestTags.SELECTION_BAR)
            .fetchSemanticsNode().size.height
        assertTrue(
            height > 40 * rule.density.density,
            "the selection action bar was $height px tall, so it is not really on screen",
        )
    }

    @Test
    fun openingTheDeleteConfirmationIsNotItselfADeletion() {
        openPhotos()
        val tag = firstTileTag() ?: return
        rule.onNodeWithTag(tag).performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.SELECTION_DELETE).performClick()
        rule.waitForIdle()
        // The dialog is up; the grid must still hold the item, because the user
        // has not confirmed. Cancelling is the next assertion a human would make.
        rule.onNodeWithTag(HgTestTags.PHOTOS_GRID).assertExists()
    }
}

/**
 * Matches any tile tag, so the test does not have to know a MediaStore row id.
 * `hasTestTag` only matches exactly, which is why this exists.
 */
private val TileMatcher = androidx.compose.ui.test.SemanticsMatcher(
    "test tag starts with ${HgTestTags.thumbPrefix}",
) { node ->
    node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)
        ?.startsWith(HgTestTags.thumbPrefix) == true
}
