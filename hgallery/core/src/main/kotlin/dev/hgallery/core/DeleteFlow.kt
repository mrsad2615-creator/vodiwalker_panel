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
     * Android 8–10: there is no trash. `MediaStore.createDeleteRequest` asks the
     * user to confirm the removal of items the app does not own.
     */
    SYSTEM_DELETE_REQUEST,

    /**
     * Last resort, only when the platform supplies no request at all: a direct
     * `ContentResolver.delete` against rows this app can write.
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
/** Android 10: `createDeleteRequest` and scoped storage. */
const val API_SCOPED_STORAGE = 29

/** Android 11: the system trash arrived with `createTrashRequest`. */
const val API_SYSTEM_TRASH = 30

fun deletePathFor(sdkInt: Int, canWriteDirectly: Boolean): DeletePath = when {
    sdkInt >= API_SYSTEM_TRASH -> DeletePath.SYSTEM_TRASH
    !canWriteDirectly -> DeletePath.SYSTEM_DELETE_REQUEST
    else -> DeletePath.DIRECT_DELETE
}

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
