package dev.hgallery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectionTest {

    @Test
    fun nothingIsSelectedUntilTheUserAsks() {
        val s = Selection.None
        assertFalse(s.active)
        assertEquals(0, s.count)
    }

    @Test
    fun longPressEntersSelectionModeWithThatItem() {
        val s = Selection.None.beginWith(7L)
        assertTrue(s.active)
        assertEquals(setOf(7L), s.ids)
    }

    @Test
    fun tappingTogglesWithinSelectionMode() {
        val s = Selection.None.beginWith(1L).toggle(2L)
        assertEquals(setOf(1L, 2L), s.ids)
        assertEquals(setOf(2L), s.toggle(1L).ids)
    }

    @Test
    fun deselectingTheLastItemLeavesSelectionMode() {
        val s = Selection.None.beginWith(1L).toggle(1L)
        assertFalse(s.active)
        assertTrue(s.isEmpty)
    }

    @Test
    fun tappingWhileInactiveSelectsRatherThanOpeningAnEmptyState() {
        val s = Selection.None.toggle(5L)
        assertTrue(s.active)
        assertEquals(setOf(5L), s.ids)
    }

    @Test
    fun selectAllCoversExactlyWhatWasOffered() {
        val s = Selection.None.beginWith(1L).selectAll(listOf(1L, 2L, 3L))
        assertEquals(3, s.count)
        assertTrue(s.isAllSelected(listOf(1L, 2L, 3L)))
        // An extra item appearing later means "all" is no longer true.
        assertFalse(s.isAllSelected(listOf(1L, 2L, 3L, 4L)))
    }

    @Test
    fun selectAllOnAnEmptyLibraryIsNotSelectionMode() {
        val s = Selection.None.selectAll(emptyList())
        assertFalse(s.active)
    }

    @Test
    fun pruningDropsDeletedIds() {
        val s = Selection.None.beginWith(1L).toggle(2L).toggle(3L)
        val pruned = s.prune(setOf(2L))
        assertEquals(setOf(2L), pruned.ids)
        assertTrue(pruned.active)
    }

    @Test
    fun pruningEverythingLeavesSelectionMode() {
        val s = Selection.None.beginWith(1L).toggle(2L)
        val pruned = s.prune(emptySet())
        assertFalse(pruned.active)
        assertTrue(pruned.isEmpty)
    }

    @Test
    fun pruningWhatIsAlreadyGoneReturnsTheSameInstance() {
        val s = Selection.None.beginWith(1L).toggle(2L)
        assertTrue(s.prune(setOf(1L, 2L, 3L)) === s)
    }

    @Test
    fun exitClearsEverything() {
        val s = Selection.None.beginWith(1L).toggle(2L).exit()
        assertFalse(s.active)
        assertEquals(0, s.count)
    }
}
