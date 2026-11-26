package com.neurox.presentation

import com.neurox.presentation.io.BackupFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The suggested name for a backup file.
 *
 * WHY IT IS TESTED AND NOT INLINE
 * The name is the only part of an export the user sees before the file exists. It has
 * to sort chronologically in a file manager (zero-padded ISO-8601), carry the app's
 * extension, and carry **nothing else**: a backup may be written to a shared folder,
 * and the envelope itself is deliberately identity-free. An inline `"+date"` would be
 * none of those, quietly.
 *
 * Catalogue parity and missing translations are already covered by
 * [CatalogueContractTest]; this file does not duplicate them.
 */
class BackupFilesTest {

    /** 2026-11-24T00:00:00Z */
    private val november24 = 1_795_478_400_000L

    @Test
    fun suggestedName_isIsoDatedWithTheNeuroxExtension() {
        assertEquals("neurox-backup-2026-11-24.neurox", BackupFiles.suggestedName(november24))
    }

    @Test
    fun suggestedName_padsSingleDigitMonthsAndDays() {
        // 2026-01-05: unpadded fields would sort "2026-1-5" after "2026-12-01".
        val name = BackupFiles.suggestedName(1_767_571_200_000L)
        assertTrue("expected a padded date, got $name", name.contains("-2026-01-05"))
        assertEquals("date part must be 10 chars (yyyy-MM-dd)", 10,
            name.length - "neurox-backup-".length - ".neurox".length)
    }

    @Test
    fun suggestedName_leaksNoProfileOrIdentity() {
        // Exactly the two separator dashes of the prefix, and nothing but the date and
        // extension after it.
        val name = BackupFiles.suggestedName(november24)
        // Exactly the prefix, an ISO date and the extension: nothing that could name
        // a person, a profile or a device.
        assertEquals("neurox-backup-2026-11-24.neurox", name)
        assertTrue(name, name.startsWith("neurox-backup-"))
        assertTrue(name, name.endsWith(".neurox"))
    }

    @Test
    fun theSameDayAlwaysProducesTheSameName() {
        // Two exports on one day must not produce "neurox-backup-2026-11-24 (1).neurox"
        // through a clock-dependent suffix; the picker handles collisions itself.
        val morning = november24 + 6 * 3_600_000L
        val night = november24 + 23 * 3_600_000L + 1_800_000L
        assertEquals(
            BackupFiles.suggestedName(morning),
            BackupFiles.suggestedName(night),
        )
    }

    @Test
    fun unavailableReportsCancellationRatherThanPretending() = kotlinx.coroutines.runBlocking {
        // A host with no picker must read as "nothing happened", never as success.
        // This is the exact distinction the dead `{ }` callbacks erased.
        assertNull(BackupFiles.Unavailable.save("{}", "x.neurox"))
        assertNull(BackupFiles.Unavailable.open())
    }
}
