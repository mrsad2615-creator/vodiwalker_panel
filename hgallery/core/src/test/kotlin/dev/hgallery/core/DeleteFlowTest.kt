package dev.hgallery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteFlowTest {

    @Test
    fun api30AndAboveAlwaysUsesTheRecoverableTrashRequest() {
        // Even when the rows are writable directly: recoverability is the point.
        assertEquals(DeletePath.SYSTEM_TRASH, deletePathFor(30, canWriteDirectly = true))
        assertEquals(DeletePath.SYSTEM_TRASH, deletePathFor(33, canWriteDirectly = true))
        assertEquals(DeletePath.SYSTEM_TRASH, deletePathFor(35, canWriteDirectly = true))
    }

    @Test
    fun apiBelow30HasNoTrashSoItAsksTheSystemToConfirm() {
        assertEquals(DeletePath.SYSTEM_DELETE_REQUEST, deletePathFor(29, canWriteDirectly = false))
        assertEquals(DeletePath.SYSTEM_DELETE_REQUEST, deletePathFor(26, canWriteDirectly = false))
    }

    @Test
    fun apiBelow30WithWritableRowsDeletesDirectly() {
        assertEquals(DeletePath.DIRECT_DELETE, deletePathFor(29, canWriteDirectly = true))
    }

    @Test
    fun outcomeSeparatesRecoverableFromPermanent() {
        val trashed = DeleteOutcome(trashed = 3)
        assertTrue(trashed.allRecoverable)
        assertEquals(3, trashed.removed)

        val permanent = DeleteOutcome(deleted = 3)
        assertFalse(permanent.allRecoverable)

        val mixed = DeleteOutcome(trashed = 2, deleted = 1)
        assertFalse(mixed.allRecoverable, "a mixed batch is not fully recoverable")
        assertEquals(3, mixed.removed)

        val partlyFailed = DeleteOutcome(trashed = 2, failed = 1)
        assertFalse(partlyFailed.allRecoverable)
        assertEquals(2, partlyFailed.removed)
        assertEquals(3, partlyFailed.total)
    }

    @Test
    fun anEmptyOutcomeClaimsNothing() {
        assertFalse(DeleteOutcome().allRecoverable)
        assertEquals(0, DeleteOutcome().removed)
    }

    @Test
    fun theFlowCarriesTheSelectionItWasOpenedFor() {
        val flow: DeleteFlow = DeleteFlow.Confirm(setOf(1L, 2L))
        assertTrue(flow is DeleteFlow.Confirm)
        assertEquals(setOf(1L, 2L), (flow as DeleteFlow.Confirm).ids)
    }
}
