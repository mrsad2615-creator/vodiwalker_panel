package dev.hgallery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteFlowTest {

    @Test
    fun api30AndAboveAlwaysUsesTheRecoverableTrashRequest() {
        // Even when the rows would be writable directly: recoverability is the
        // point, and the trash is the only path that offers it.
        assertEquals(DeletePath.SYSTEM_TRASH, deletePathFor(30))
        assertEquals(DeletePath.SYSTEM_TRASH, deletePathFor(33))
        assertEquals(DeletePath.SYSTEM_TRASH, deletePathFor(35))
    }

    @Test
    fun apiBelow30HasNoTrashAtAllSoItDeletesDirectly() {
        // There is no system request API below 30 either, so the app's own
        // confirmation is the last word and the dialog must say the deletion is
        // permanent. Naming a "recoverable" path here would be a false promise.
        assertEquals(DeletePath.DIRECT_DELETE, deletePathFor(29))
        assertEquals(DeletePath.DIRECT_DELETE, deletePathFor(26))
    }

    @Test
    fun theDecisionNeverNamesAPathTheDeviceCannotUse() {
        // Exhaustive over the supported range: every answer is either the trash,
        // which exists from 30, or a direct delete, which needs no API at all.
        (26..35).forEach { sdk ->
            val path = deletePathFor(sdk)
            if (sdk >= 30) {
                assertEquals(DeletePath.SYSTEM_TRASH, path, "sdk=$sdk")
            } else {
                assertEquals(DeletePath.DIRECT_DELETE, path, "sdk=$sdk")
            }
        }
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
