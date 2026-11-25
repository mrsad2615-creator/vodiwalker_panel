package com.neurox.presentation

import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.model.Module
import com.neurox.presentation.nav.AppDestination
import com.neurox.presentation.nav.NavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Navigation invariants.
 *
 * A mobile app rots from its navigation first: a screen that loses its way back, a
 * module reachable from one place but not another, chrome that covers a stimulus.
 * These are cheap tests for expensive bugs.
 */
class NavigationTest {

    @Test
    fun theGraphIsCoherent() {
        assertTrue(NavigationState.isCoherent())
    }

    @Test
    fun theBottomBarIsHomePlusTheFourModules() {
        val bar = NavigationState.topLevel()
        assertEquals(5, bar.size)
        assertEquals(AppDestination.HOME, bar.first())
        assertEquals(
            Module.entries.toSet(),
            bar.mapNotNull { it.module }.toSet(),
        )
    }

    @Test
    fun everyModuleIsEquallyReachable() {
        for (module in Module.entries) {
            val destination = AppDestination.fromModule(module)
            assertTrue("module $module has no destination", destination != null)
            assertTrue("module $module is not in the bar", destination!!.inBottomBar)
            assertFalse("module $module must not be immersive at browse level", destination.immersive)
        }
    }

    @Test
    fun immersiveDestinationsNeverShowChrome() {
        val immersive = AppDestination.entries.filter { it.immersive }
        assertTrue("expected immersive screens", immersive.isNotEmpty())
        for (destination in immersive) {
            assertFalse(
                "immersive destination $destination must not show the bottom bar",
                NavigationState.showsBottomBar(destination),
            )
        }
        // A session is the canonical immersive screen.
        assertTrue(AppDestination.SESSION.immersive)
        assertFalse(NavigationState.showsBottomBar(AppDestination.SESSION))
    }

    @Test
    fun routesAreUnique() {
        val routes = AppDestination.entries.map { it.route }
        assertEquals(routes.size, routes.distinct().size)
    }

    @Test
    fun aConcreteSessionRouteResolvesToTheSessionDestination() {
        for (template in ExerciseTemplate.entries) {
            val route = NavigationState.sessionRoute(template.id)
            assertEquals(AppDestination.SESSION, AppDestination.fromRoute(route))
        }
    }

    @Test
    fun sessionRoutesRejectIdsThatWouldBreakAPath() {
        var threw = false
        try {
            NavigationState.sessionRoute("vision/broken")
        } catch (expected: IllegalArgumentException) {
            threw = true
        }
        assertTrue("a slash in an exercise id must be rejected", threw)
    }

    @Test
    fun everyDestinationHasALabelKeyThatExistsInBothLanguages() {
        for (destination in AppDestination.entries) {
            for (language in listOf("en", "fa")) {
                val strings = com.neurox.presentation.i18n.Strings.of(language)
                val key = destination.labelKey
                assertFalse(
                    "missing label for $destination in $language ($key)",
                    strings.raw(key) == key,
                )
            }
        }
    }

    @Test
    fun anUnknownRouteResolvesToNothingInsteadOfGuessing() {
        assertNull(AppDestination.fromRoute("nope/nowhere"))
        assertNull(AppDestination.fromRoute(null))
    }

    @Test
    fun everyExerciseOfEveryModuleHasAStartRoute() {
        // The library and the module browser both start sessions by exercise id; the
        // route builder must accept every id the catalogue contains.
        for (template in ExerciseTemplate.entries) {
            assertFalse(template.id.contains('/'))
            assertTrue(NavigationState.sessionRoute(template.id).isNotBlank())
        }
    }
}
