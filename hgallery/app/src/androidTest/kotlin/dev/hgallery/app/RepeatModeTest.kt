package dev.hgallery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.core.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The repeat mode, driven on a device.
 *
 * ## The gap this closes
 *
 * `RepeatPolicyTest` proves the rule. It cannot prove that the control reaches the rule,
 * that the stored value is written, or that the two places the mode appears agree. Those
 * are the failures that survive a green unit suite: a button wired to nothing, a
 * preference key typo, a screen reading a field nobody writes.
 *
 * ## Why Settings and not the player
 *
 * The player needs a video in MediaStore, and the CI emulator has none. Settings is
 * reachable from the shell with no media at all, so this test can assert the whole
 * write-and-read path without a decoder. The player's control is a second view of the
 * same stored value -- it is drawn from `settings.repeatMode` -- so proving the store is
 * proven there too, and the screenshot `evidence/15-video-player-ended.png` shows the
 * control drawn in the off state.
 *
 * What is still not covered: an actual clip reaching its end and the queue advancing.
 * That needs a real file and a real decoder, and no test here claims it.
 */
@RunWith(AndroidJUnit4::class)
class RepeatModeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** Opens Settings through the overflow menu, which is the path a user takes. */
    private fun openSettings() {
        rule.onNodeWithTag(HgTestTags.OVERFLOW_BUTTON).performClick()
        rule.onNodeWithTag(HgTestTags.MENU_SETTINGS).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.SETTINGS_LIST).assertIsDisplayed()
    }

    /** Selects a mode by tapping its row, scrolling it into view first. */
    private fun selectMode(mode: RepeatMode) {
        val tag = HgTestTags.repeatOption(mode.name)
        rule.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
        rule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
        rule.waitForIdle()
    }

    /** True when the row for [mode] is the selected one, read from its semantics. */
    private fun isSelected(mode: RepeatMode): Boolean =
        rule.onAllNodesWithTag(HgTestTags.repeatOption(mode.name), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.config
            ?.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Selected)
            ?: false

    @Test
    fun everyModeIsOfferedAsARow() {
        openSettings()
        // Three rows, not a switch: "repeat this clip" has nowhere to live in a boolean,
        // and a missing row would mean the mode is unreachable without the player.
        RepeatMode.entries.forEach { mode ->
            rule.onNodeWithTag(HgTestTags.repeatOption(mode.name), useUnmergedTree = true)
                .performScrollTo()
        }
    }

    @Test
    fun pickingAModeSelectsItAndOnlyIt() {
        openSettings()

        selectMode(RepeatMode.ONE)
        assertEquals(true, isSelected(RepeatMode.ONE))
        // Mutually exclusive. Two selected rows would mean the mode is stored twice and
        // the player could read either one.
        assertEquals(false, isSelected(RepeatMode.ALL))
        assertEquals(false, isSelected(RepeatMode.OFF))

        selectMode(RepeatMode.ALL)
        assertEquals(true, isSelected(RepeatMode.ALL))
        assertEquals(false, isSelected(RepeatMode.ONE))
    }

    @Test
    fun theChoiceSurvivesLeavingAndReopeningTheScreen() {
        openSettings()
        selectMode(RepeatMode.ONE)

        // Leave the screen entirely, then come back. A mode held only in composition
        // state would reset here; one written to DataStore does not.
        rule.onNodeWithTag(HgTestTags.SETTINGS_BACK).performClick()
        rule.waitForIdle()
        openSettings()

        assertEquals(true, isSelected(RepeatMode.ONE))
    }

    @Test
    fun selectingOffReturnsToTheDefault() {
        openSettings()
        selectMode(RepeatMode.ALL)
        selectMode(RepeatMode.OFF)

        assertEquals(true, isSelected(RepeatMode.OFF))
        assertEquals(false, isSelected(RepeatMode.ALL))
    }
}
