package dev.hgallery.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import dev.hgallery.app.ui.components.HgTestTags

/**
 * Finding a grid tile without knowing its MediaStore id.
 *
 * ## Why this file exists
 *
 * Three test files wanted "the first tile the timeline drew". The tag is built from the
 * row id (`thumb-<id>`), and the tests must not hard-code an id that depends on whatever
 * the emulator's MediaStore happened to contain.
 *
 * Each file solved it slightly differently, and all three solutions were wrong in ways
 * that only a compiler could see:
 *
 *  - `hasTestTagStartingWith` was used by one. No such function exists in Compose's test
 *    API, in this version or any other.
 *  - `SemanticsNode.config.getOrNull(key)` was used by two. `getOrNull` was removed from
 *    `SemanticsConfiguration`; the property is read by iterating the configuration.
 *  - `assertExists` was used by one. It was deprecated and then removed, which is why
 *    `assertIsDisplayed` is the assertion everywhere else in this suite.
 *
 * None of that was caught, because the androidTest source set had never been compiled:
 * `connectedDebugAndroidTest` failed at compile time, the harness recorded `tee`'s exit
 * status instead of Gradle's, and every run reported the instrumented suite as passing.
 * One matcher in one place is the smallest thing that makes the next such mistake a
 * single-line fix.
 *
 * ## Reading the tag by iterating
 *
 * `SemanticsConfiguration` is `Iterable<Map.Entry<SemanticsPropertyKey<*>, Any?>>`, and
 * `SemanticsPropertyKey.name` is stable. Comparing that name is uglier than a typed
 * lookup, and it is the form that compiles against this version without depending on an
 * accessor that may have been removed.
 */
internal object TestTiles {

    /** Matches any node whose test tag begins with [HgTestTags.thumbPrefix]. */
    val matcher: SemanticsMatcher = SemanticsMatcher(
        "test tag starts with ${HgTestTags.thumbPrefix}",
    ) { node ->
        node.config.any { entry ->
            entry.key.name == SemanticsProperties.TestTag.name &&
                (entry.value as? String)?.startsWith(HgTestTags.thumbPrefix) == true
        }
    }
}

/**
 * The first tile in the tree, or null when the library is empty.
 *
 * Returning null rather than throwing keeps the "no media on this device" case a skip
 * instead of a failure, which is what every caller here wants: a missing fixture is not
 * a broken gesture.
 */
internal fun AndroidComposeTestRule<*, *>.firstTile(): SemanticsNodeInteraction? {
    // `AndroidComposeTestRule` implements `SemanticsNodeInteractionsProvider`, so the
    // compiler already knows `onAllNodes` is there. The cast is only to name the type
    // the function comes from, and it is unchecked by definition of the generic rule.
    val provider = this as SemanticsNodeInteractionsProvider
    val nodes = provider.onAllNodes(TestTiles.matcher, useUnmergedTree = true)
    return if (nodes.fetchSemanticsNodes().isEmpty()) null else nodes[0]
}
