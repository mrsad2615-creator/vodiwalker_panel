package dev.hgallery.core

/**
 * What the app can offer for a batch delete, decided from the platform version
 * and how the delete was authorised.
 *
 * This is written so it can be unit-tested without a device: the decision is a
 * pure function of two booleans, and the Android layer only supplies them.
 */
enum class DeletePath {
    /**
     * Android 11+: `MediaStore.createTrashRequest` moves items to the system
     * trash, where they stay for up to 30 days and can be restored from the
     * phone's own Recycle bin. This is the only path that is genuinely
     * recoverable, so it is preferred whenever it exists.
     */
    SYSTEM_TRASH,

    /**
     * Android 8–9: no trash and no request API. The app deletes rows it can write
     * and reports the rest as failures; there is no recoverable option here, and
     * the confirmation dialog says so.
     */
    DIRECT_DELETE,
}

/**
 * The delete flow as an explicit state machine.
 *
 * The reason this is not three `if`s inside a composable: the transition that
 * matters is *who is allowed to say "go"*. The OS dialog and the app's own
 * confirmation must never both fire, and a result must never be applied to a
 * selection the user has changed since. Both are ordering bugs that only show up
 * on a real device, so they are pinned down here where they can be tested.
 */
sealed interface DeleteFlow {

    /** Nothing is in flight. */
    data object Idle : DeleteFlow

    /**
     * The app is showing its own confirmation for [ids].
     *
     * Shown for every path, because the reference confirms first and because a
     * destructive action should never be one tap away.
     */
    data class Confirm(val ids: Set<Long>) : DeleteFlow

    /**
     * The app has handed the decision to the system and is waiting for the
     * launcher to return a result. The selection is frozen while this is true.
     */
    data class AwaitingSystem(val ids: Set<Long>, val path: DeletePath) : DeleteFlow

    /** The delete has been accepted and is being applied to the media store. */
    data class Applying(val ids: Set<Long>) : DeleteFlow
}

/**
 * How a delete should be performed on a given API level.
 *
 * An app that can already modify a row does not need a user consent dialog for
 * it, but routing through the trash request is still preferable on API 30+
 * because that is what makes the item recoverable. Below 30 there is no trash,
 * so the direct path is used when the rows are writable.
 */
/** Android 10: scoped storage, and `MediaStore.createTrashRequest` does not exist. */
const val API_SCOPED_STORAGE = 29

/**
 * Android 11: the system trash and `MediaStore.createTrashRequest` arrived, and
 * `createDeleteRequest` became usable for rows the app does not own.
 */
const val API_SYSTEM_TRASH = 30

/**
 * Chooses the mechanism, and never names one this device cannot use.
 *
 * The first version of this function returned a "system delete request" path for
 * API 29, but `MediaStore.createDeleteRequest` is API 30 — lint's `NewApi` check
 * found the mismatch. Both request APIs therefore exist only from 30 up, which
 * makes the decision two-way, not three-way: on 30+ the items go to the trash and
 * are recoverable; below that there is no recoverable option at all and the app
 * must say so rather than imply it.
 */
fun deletePathFor(sdkInt: Int): DeletePath =
    if (sdkInt >= API_SYSTEM_TRASH) DeletePath.SYSTEM_TRASH else DeletePath.DIRECT_DELETE

/**
 * The outcome of a batch delete, in the terms the UI has to show.
 *
 * [trashed] items are recoverable; [deleted] items are not. Keeping the two
 * apart matters: a confirmation dialog may only promise "can be restored" when
 * every item in the batch really did go to the trash.
 */
data class DeleteOutcome(
    val trashed: Int = 0,
    val deleted: Int = 0,
    val failed: Int = 0,
) {
    val total: Int get() = trashed + deleted + failed
    val removed: Int get() = trashed + deleted

    /** True only when the whole batch is recoverable, which is what a toast may claim. */
    val allRecoverable: Boolean get() = total > 0 && failed == 0 && deleted == 0
}
