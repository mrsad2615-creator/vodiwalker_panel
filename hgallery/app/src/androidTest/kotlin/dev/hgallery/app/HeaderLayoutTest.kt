package dev.hgallery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hgallery.app.ui.components.HgTestTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The home header must not squeeze its own title.
 *
 * A released build passed all seven instrumented suites while "Photos" was
 * drawn one letter per line, with the count underneath spilling past the
 * controls. Every existing test used `assertIsDisplayed`, which is true of a
 * title one character wide, so none of them could fail. The defect was found by
 * looking at the emulator screenshot.
 *
 * These tests assert the geometry that was wrong: the title is one line and it
 * is wider than it is tall, which a one-character-per-line wrap cannot satisfy.
 * They run on whatever emulator density the CI provides, so they are a real
 * check rather than a re-statement of a number this file picked.
 */
@RunWith(AndroidJUnit4::class)
class HeaderLayoutTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun openPhotos() {
        rule.onNodeWithTag(HgTestTags.tab("Photos")).performClick()
        rule.waitForIdle()
    }

    /** The bounds of the screen title, in pixels. */
    private fun titleBounds() =
        rule.onNodeWithTag(HgTestTags.SCREEN_TITLE).fetchSemanticsNode().boundsInRoot

    /**
     * The title occupies one line.
     *
     * "Photos" is six characters. Drawn on one line it is about three times
     * wider than it is tall. Drawn one character per line it is much taller
     * than it is wide. This test separates those two cases without hard-coding
     * a pixel width, so it holds at any density the emulator reports.
     */
    @Test
    fun thePhotosTitleIsDrawnOnOneLine() {
        openPhotos()
        val bounds = titleBounds()
        val w = bounds.width
        val h = bounds.height
        assertTrue(
            "title is ${w}x${h}: taller than wide means it wrapped",
            w > h,
        )
    }

    /**
     * The title is wide enough to be legible.
     *
     * "Photos" at the title size is many characters wide. A title narrower than
     * a third of the screen has lost its space to the controls beside it, which
     * is the condition that produced the one-letter-per-line wrap.
     */
    @Test
    fun theTitleKeepsSurvivingWidthBesideItsActions() {
        openPhotos()
        val bounds = titleBounds()
        val screen = rule.onRoot().fetchSemanticsNode().size.width
        assertTrue(
            "title width ${bounds.width} of $screen is too narrow",
            bounds.width > screen / 3,
        )
    }

    /** Every control in the header keeps a 48 dp touch target. */
    @Test
    fun theHeaderControlsAreStillReachable() {
        openPhotos()
        rule.onNodeWithTag(HgTestTags.SELECT_BUTTON).assertIsDisplayed()
        rule.onNodeWithTag(HgTestTags.VIEW_SWITCH).assertIsDisplayed()
        rule.onNodeWithTag(HgTestTags.MORE_BUTTON).assertIsDisplayed()
        rule.onNodeWithTag(HgTestTags.FILTER_CHIPS).assertIsDisplayed()
    }
}
