package dev.hgallery.core

/**
 * One item held in the app's trash.
 *
 * A plain value: the Android layer fills it while moving a file, stores it as text, and
 * reads it back. Nothing here reaches for a file system, which is why the retention rule
 * can be tested without a device.
 */
data class TrashEntry(
    /** The MediaStore id the item had before it was trashed. */
    val itemId: Long,
    val fileName: String,
    /** Absolute path inside the app's private storage. */
    val storedPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** Epoch millis, used for both ordering and the retention rule. */
    val deletedAtMs: Long,
    /** Where the file goes back when it is restored. Empty when unknown. */
    val originalRelativePath: String = "",
)

/**
 * How long the app's own trash keeps an item.
 *
 * Thirty days matches the platform's own trash, so a user who has used one has the right
 * expectation for the other. It is a value, not a setting: a user who could extend it
 * forever would use app storage for a permanent archive, and the storage is not the
 * user's to see or manage.
 */
object TrashPolicy {

    const val RETENTION_DAYS = 30L

    /** Milliseconds in one day, named so the arithmetic below reads as arithmetic. */
    private const val MS_PER_DAY = 24L * 60L * 60L * 1000L

    /**
     * Entries that are past the retention period.
     *
     * [nowMs] is passed in rather than read from the clock, so the rule is a function of
     * its inputs and a test can choose any date.
     */
    fun expired(entries: List<TrashEntry>, nowMs: Long): List<TrashEntry> =
        entries.filter { nowMs - it.deletedAtMs >= RETENTION_DAYS * MS_PER_DAY }

    /** Entries still inside the retention period, newest first. */
    fun active(entries: List<TrashEntry>, nowMs: Long): List<TrashEntry> =
        entries.filter { nowMs - it.deletedAtMs < RETENTION_DAYS * MS_PER_DAY }
            .sortedByDescending { it.deletedAtMs }

    /** Whole days left before [entry] is removed on its own. */
    fun daysLeft(entry: TrashEntry, nowMs: Long): Int {
        val elapsed = nowMs - entry.deletedAtMs
        val left = RETENTION_DAYS * MS_PER_DAY - elapsed
        if (left <= 0L) return 0
        return ((left + MS_PER_DAY - 1) / MS_PER_DAY).toInt()
    }
}
