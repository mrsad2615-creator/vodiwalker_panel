package com.neurox.presentation.io

import com.neurox.presentation.i18n.CivilDate

/**
 * Reading and writing backup files, as the screens see it.
 *
 * WHY AN INTERFACE
 * The screens need to save a vault to a file the user chooses and read one back.
 * On Android that is the Storage Access Framework: an Activity result, a content
 * URI, a permission grant that lives as long as the Activity. None of that exists
 * on a JVM, and a screen cannot write a file without it.
 *
 * BEFORE THIS INTERFACE, BOTH PATHS WERE DEAD ENDS
 * Export computed the bytes and threw them away; import had a "pick a file" button
 * wired to `{ }`. Backup and restore were, in practice, not implemented - while the
 * screens that present them were fully built and the vault logic behind them was
 * fully tested. That is the specific kind of half-built feature a compile gate
 * cannot see, because nothing is unresolved and nothing throws.
 *
 * Implementations live in `:app`; tests use a fake.
 */
interface BackupFiles {

    /** What a picker returned: the document's contents and the name to show for it. */
    data class PickedDocument(
        val contents: String,
        val displayName: String?,
    )

    /**
     * Writes [contents] to a file the user chooses.
     *
     * Returns the file's display name on success, null if the user cancelled. A
     * cancelled save is a decision, not a failure, and the screen must not report it
     * as an error.
     */
    suspend fun save(contents: String, suggestedName: String): String?

    /**
     * Asks the user for a file and returns its contents, or null if cancelled.
     *
     * The implementation rejects oversized files itself: a 200 MB pick must never be
     * read into a String.
     */
    suspend fun open(): PickedDocument?

    companion object {
        /**
         * The suggested name for an export.
         *
         * ISO-8601 so files sort chronologically in a file manager, `.neurox` so a
         * user can tell a backup from anything else, and **no profile name**: the
         * file may be saved to a shared folder, and the envelope itself deliberately
         * carries no identity.
         */
        fun suggestedName(createdAtMillis: Long): String {
            val (year, month, day) = CivilDate.fromEpochDay(createdAtMillis / 86_400_000L)
            return "neurox-backup-" +
                year.toString().padStart(4, '0') + "-" +
                month.toString().padStart(2, '0') + "-" +
                day.toString().padStart(2, '0') +
                ".neurox"
        }
    }

    /** A no-op implementation, for previews and for screens with no file host. */
    object Unavailable : BackupFiles {
        override suspend fun save(contents: String, suggestedName: String): String? = null
        override suspend fun open(): PickedDocument? = null
    }
}
