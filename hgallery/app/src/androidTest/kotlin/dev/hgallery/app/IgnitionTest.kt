package dev.hgallery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hgallery.app.ui.brand.ignitionDurationMs
import dev.hgallery.app.ui.components.HgTestTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * HGallery Ignition must not outlive its own animation.
 *
 * The sequence is a cover over a live screen. If its removal depends on an animation
 * completing, then a device with animations turned off, a cancelled coroutine, or a
 * frame that never arrives leaves the cover in place and the application unreachable.
 * That is the failure mode of every splash screen that is also a gate, and it is the
 * one the directive forbids.
 *
 * These tests assert the two properties that prevent it: the cover is gone within a
 * bounded time, and the shell underneath is usable afterwards.
 */
@RunWith(AndroidJUnit4::class)
class IgnitionTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** The cover's budget, with room for the device to be slow without hiding a hang. */
    private val budgetMs = ignitionDurationMs.toLong() + 4_000L

    /**
     * The shell is reachable within the budget.
     *
     * `waitUntil` polls the tree; a cover that never lifts fails here with the time it
     * waited, which is the useful message. The tap afterwards proves the cover is gone
     * rather than merely invisible: a tap lands only if nothing is on top.
     */
    @Test
    fun theShellIsUsableWithinTheBudget() {
        rule.waitUntil(timeoutMillis = budgetMs) {
            rule.onAllNodesWithTag(HgTestTags.tab("Photos")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag(HgTestTags.tab("Photos")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.PHOTOS_GRID).assertIsDisplayed()
    }

    /** The cover is removed, not hidden. */
    @Test
    fun theCoverDoesNotExistAfterTheSequence() {
        rule.waitUntil(timeoutMillis = budgetMs) {
            rule.onAllNodesWithTag(HgTestTags.tab("Photos")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
        assertTrue(
            "the ignition cover is still in the tree after the sequence",
            rule.onAllNodesWithTag(HgTestTags.IGNITION).fetchSemanticsNodes().isEmpty(),
        )
    }

    /** The budget is a real bound, not an open-ended wait. */
    @Test
    fun theSequenceIsShorterThanASecond() {
        // A launch longer than a second is a delay the user can feel, whatever it draws.
        assertTrue(
            "ignition is $ignitionDurationMs ms",
            ignitionDurationMs in 1..1_000,
        )
    }

    /**
     * Rotation during the sequence does not replay it.
     *
     * The flag is saved, so the cover stays gone after a configuration change. A
     * replayed launch after every rotation is the bug this pins.
     */
    @Test
    fun rotationDoesNotReplayTheSequence() {
        rule.waitUntil(timeoutMillis = budgetMs) {
            rule.onAllNodesWithTag(HgTestTags.tab("Photos")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.tab("Photos")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.PHOTOS_GRID).assertIsDisplayed()
    }
}
