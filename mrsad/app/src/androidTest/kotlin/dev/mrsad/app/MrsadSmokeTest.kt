package dev.mrsad.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mrsad.app.data.settings.ThemeVariant
import dev.mrsad.app.navigation.Routes
import dev.mrsad.app.testing.MrsadTestHooks
import dev.mrsad.app.testing.MrsadTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/**
 * On-device smoke tests. These run on a real emulator in CI and prove the
 * shipped binary boots, renders the real Persian UI, navigates between the
 * bottom-bar destinations, runs a typing session and applies a persisted
 * theme change.
 *
 * Two rules make the suite deterministic:
 *
 *  1. Every element is addressed through an app-owned `testTag`
 *     ([MrsadTestTags]) — never through a user-visible Persian label, which
 *     may be duplicated, may switch to Persian digits, and may change.
 *  2. [MrsadTestHooks] removes every recurring animation and timer *before*
 *     the activity is created. A Compose infinite transition requests frames
 *     forever, which would keep the Compose idling resource busy and make the
 *     test wait for idle until it times out. The emulator's "disable
 *     animations" switch only scales Android animators and does not help.
 */
@RunWith(AndroidJUnit4::class)
class MrsadSmokeTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * Switches the app into deterministic mode. It has to be the *outermost*
     * rule so that it runs before the activity rule launches `MainActivity`:
     * the very first rendered frame must already be free of animations.
     */
    private val deterministicModeRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                // Written on the main thread so that the value is visible to
                // the composition the moment it starts.
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    MrsadTestHooks.enableDeterministicMode()
                }
                base.evaluate()
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(deterministicModeRule)
        .around(composeRule)

    // ---- helpers ------------------------------------------------------------

    /** Waits until a tagged node exists in the semantics tree. */
    private fun awaitTag(tag: String, timeoutMillis: Long = 20_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Waits until a tagged node reports itself as selected. */
    private fun awaitSelected(tag: String, timeoutMillis: Long = 20_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag)
                .fetchSemanticsNodes()
                .firstOrNull()
                ?.config
                ?.getOrNull(SemanticsProperties.Selected) == true
        }
    }

    private fun openBottomDestination(route: String) {
        awaitTag(MrsadTestTags.NAV_BAR)
        composeRule.onNodeWithTag(MrsadTestTags.navItem(route)).performClick()
    }

    // ---- tests --------------------------------------------------------------

    @Test
    fun homeShowsBrandAndLivingCore() {
        awaitTag(MrsadTestTags.HOME_BRAND)
        composeRule.onNodeWithTag(MrsadTestTags.HOME_BRAND).assertIsDisplayed()

        awaitTag(MrsadTestTags.HOME_CORE)
        composeRule.onNodeWithTag(MrsadTestTags.HOME_CORE).assertIsDisplayed()

        // The bottom bar is the app's primary navigation and must be present.
        composeRule.onNodeWithTag(MrsadTestTags.NAV_BAR).assertIsDisplayed()
    }

    @Test
    fun bottomNavigationReachesTypeSetup() {
        openBottomDestination(Routes.TYPE)

        awaitTag(MrsadTestTags.TYPE_SETUP)
        composeRule.onNodeWithTag(MrsadTestTags.TYPE_START)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun typingSessionStartsAndAbandonsWithoutFabricatingARecord() {
        openBottomDestination(Routes.TYPE)
        awaitTag(MrsadTestTags.TYPE_START)
        composeRule.onNodeWithTag(MrsadTestTags.TYPE_START)
            .performScrollTo()
            .performClick()

        awaitTag(MrsadTestTags.TYPE_RUNNING)
        composeRule.onNodeWithTag(MrsadTestTags.TYPE_FINISH)
            .performScrollTo()
            .assertIsDisplayed()

        // Ending a run without a single keystroke must not invent a session:
        // the app returns to the setup panel instead of showing results.
        composeRule.onNodeWithTag(MrsadTestTags.TYPE_FINISH).performClick()
        awaitTag(MrsadTestTags.TYPE_SETUP)
        composeRule.onNodeWithTag(MrsadTestTags.TYPE_SETUP).assertIsDisplayed()
        check(
            composeRule.onAllNodesWithTag(MrsadTestTags.TYPE_RESULTS)
                .fetchSemanticsNodes()
                .isEmpty(),
        ) { "A session without keystrokes must not produce a result panel." }
    }

    @Test
    fun settingsOpensAndAppliesTheChosenTheme() {
        awaitTag(MrsadTestTags.NAV_BAR)
        composeRule.onNodeWithTag(MrsadTestTags.NAV_SETTINGS).performClick()

        awaitTag(MrsadTestTags.SETTINGS_ROOT)
        composeRule.onNodeWithTag(MrsadTestTags.SETTINGS_THEME_SECTION).assertIsDisplayed()

        val crystal = MrsadTestTags.settingsTheme(ThemeVariant.CRYSTAL.id)
        val obsidian = MrsadTestTags.settingsTheme(ThemeVariant.OBSIDIAN.id)

        composeRule.onNodeWithTag(crystal).performScrollTo().performClick()

        // The choice is persisted through DataStore, so the selection has to
        // survive the round trip through the repository.
        awaitSelected(crystal)
        composeRule.onNodeWithTag(crystal).assertIsSelected()
        composeRule.onNodeWithTag(obsidian).assertIsNotSelected()
    }
}
