package dev.mrsad.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
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
                // Must happen before the activity rule launches MainActivity:
                // the very first rendered frame has to be free of animations.
                MrsadTestHooks.enableDeterministicMode()
                check(MrsadTestHooks.isDeterministic) {
                    "the deterministic test hook did not take effect"
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

    /**
     * Waits until a tagged node exists in the semantics tree.
     *
     * A plain timeout only says "condition still not satisfied", which does
     * not tell which element never showed up — so the wait is wrapped and
     * re-thrown with the tag it was waiting for.
     */
    private fun awaitTag(tag: String, timeoutMillis: Long = 20_000) {
        try {
            composeRule.waitUntil(timeoutMillis) {
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for the node tagged '$tag'", timeout)
        }
    }

    /** Waits until a tagged node reports itself as selected. */
    private fun awaitSelected(tag: String, timeoutMillis: Long = 20_000) {
        try {
            composeRule.waitUntil(timeoutMillis) {
                composeRule.onAllNodes(hasTestTag(tag) and isSelected())
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for '$tag' to be selected", timeout)
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
    fun theBrandMarkIsDrawnWhereTheProductIntroducesItself() {
        // The mark is drawn in code, from the same geometry the launcher
        // icon is generated from, so a broken draw would leave an empty
        // box on screen. Both placements are checked on a real device.
        awaitTag(MrsadTestTags.HOME_BRAND_MARK)
        composeRule.onNodeWithTag(MrsadTestTags.HOME_BRAND_MARK).assertIsDisplayed()

        composeRule.onNodeWithTag(MrsadTestTags.NAV_SETTINGS).performClick()
        awaitTag(MrsadTestTags.SETTINGS_BRAND_MARK)
        composeRule.onNodeWithTag(MrsadTestTags.SETTINGS_BRAND_MARK)
            .performScrollTo()
            .assertIsDisplayed()
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
    fun everyInteractiveControlIsNamed() {
        // An icon-only control without a name is invisible to a screen
        // reader, and it looks perfectly fine on screen — so the guarantee
        // has to be checked against the real semantics tree on a device.
        awaitTag(MrsadTestTags.NAV_BAR)
        awaitTag(MrsadTestTags.HOME_CORE)

        // A clickable merges its descendants' semantics, so a control that
        // carries a visible label or a description is named — and a control
        // that carries neither shows up here with nothing to announce.
        val tappable = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
        val unnamed = composeRule.onAllNodes(
            tappable and
                SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription) and
                SemanticsMatcher.keyNotDefined(SemanticsProperties.Text),
        ).fetchSemanticsNodes()

        check(unnamed.isEmpty()) {
            "every tappable control must be reachable by name, but " +
                "${unnamed.size} of them are not: " +
                unnamed.joinToString(", ") { it.id.toString() }
        }
        check(composeRule.onAllNodes(tappable).fetchSemanticsNodes().isNotEmpty()) {
            "the audit found no tappable control at all"
        }
    }

    @Test
    fun theLayoutIsRightToLeftOnEveryDevice() {
        // The app is Persian only, but the direction of the root view
        // follows the *system* locale — an English phone would lay the
        // whole app out from left to right and put the navigation and the
        // back arrows on the wrong side.
        //
        // The bottom bar is a Row, so its first destination ends up on the
        // right in a right-to-left layout and on the left in a left-to-right
        // one. The emulator in CI is not Persian, so this check is what
        // proves the app forces the direction instead of inheriting it
        // from the device.
        awaitTag(MrsadTestTags.NAV_BAR)

        val routes = listOf(
            Routes.HOME,
            Routes.TYPE,
            Routes.MIND,
            Routes.ANALYTICS,
            Routes.AI,
        )
        val bounds = routes.map { route ->
            composeRule.onAllNodesWithTag(MrsadTestTags.navItem(route))
                .fetchSemanticsNodes()
                .single()
                .boundsInRoot
        }

        check(bounds.none { it.width <= 0f || it.height <= 0f }) {
            "every bottom-bar destination must be laid out on screen, but " +
                "one of them has no size: " +
                bounds.joinToString(", ") { "[${it.left}, ${it.right}]" }
        }
        check(bounds.zipWithNext().all { (first, next) -> first.left > next.left }) {
            "the bottom bar must run from right to left, but its destinations " +
                "were laid out from left to right: " +
                bounds.joinToString(", ") { "[${it.left}, ${it.right}]" }
        }
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
