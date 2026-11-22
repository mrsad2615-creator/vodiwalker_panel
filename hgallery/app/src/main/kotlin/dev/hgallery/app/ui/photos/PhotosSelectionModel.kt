package dev.hgallery.app.ui.photos

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.hgallery.app.data.AppContainer
import dev.hgallery.core.DeleteOutcome
import dev.hgallery.core.DeletePath
import dev.hgallery.core.Selection

/**
 * The selection and delete state of the Photos screen.
 *
 * This was previously inline in the composable, which produced a function of
 * cyclomatic complexity 50 — detekt's complaint was a symptom of the real problem:
 * screen state, platform calls and layout were all in one place. Pulling the state
 * out means:
 *
 *  - the composable's branches are about *drawing*, not about mutation order;
 *  - the delete handshake (app dialog → system dialog → result → store mutation)
 *    has one owner instead of being scattered across callbacks;
 *  - it can be reasoned about, and the pure rules it delegates to are still the
 *    ones under test in `core`.
 *
 * It is deliberately a plain state holder rather than an
 * `androidx.lifecycle.ViewModel`: it needs no `SavedStateHandle` (selection is
 * transient by nature) and no factory plumbing, and a ViewModel that does nothing
 * a holder cannot do is ceremony.
 */
@Stable
class PhotosSelectionModel(private val container: AppContainer) {

    var selection by mutableStateOf(Selection.None)
        private set

    /** True while the app's own confirmation dialog is up. */
    var confirmingDelete by mutableStateOf(false)
        private set

    /** The last result, for the toast. Consumed by the caller. */
    var lastOutcome by mutableStateOf<DeleteOutcome?>(null)
        private set

    val deletePath: DeletePath = container.mediaStore.deletePath()

    val selectedIds: Set<Long> get() = selection.ids

    fun beginWith(id: Long) {
        selection = selection.beginWith(id)
    }

    fun toggle(id: Long) {
        selection = selection.toggle(id)
    }

    fun selectAll(allIds: Set<Long>) {
        selection = selection.selectAll(allIds)
    }

    fun toggleSelectAll(allIds: Set<Long>) {
        selection = if (selection.isAllSelected(allIds)) Selection.None else selection.selectAll(allIds)
    }

    fun exit() {
        selection = Selection.None
    }

    /** Drops ids that a delete removed; leaves selection mode if none remain. */
    fun prune(existing: Set<Long>) {
        selection = selection.prune(existing)
    }

    fun requestDelete() {
        if (selection.isEmpty) return
        confirmingDelete = true
    }

    fun cancelDelete() {
        confirmingDelete = false
    }

    /**
     * The sender for Android's own trash/delete dialog, or `null` when this
     * platform has no such dialog and the delete should be applied directly.
     */
    fun systemDeleteRequest(): IntentSender? =
        container.mediaStore.buildDeleteRequest(selection.ids)

    suspend fun applyDelete(): DeleteOutcome {
        val ids = selection.ids
        confirmingDelete = false
        val outcome = if (ids.isEmpty()) DeleteOutcome() else container.mediaStore.applyDelete(ids)
        selection = Selection.None
        lastOutcome = outcome
        return outcome
    }

    fun consumeOutcome(): DeleteOutcome? {
        val value = lastOutcome
        lastOutcome = null
        return value
    }

    fun shareIntent(): Intent? =
        if (selection.isEmpty) null else container.mediaStore.buildShareIntent(selection.ids)

    fun existingIdsOf(sections: List<Set<Long>>): Set<Long> = sections.flatten().toSet()
}

/** Messages derived once from an outcome, so two call sites cannot disagree. */
fun DeleteOutcome.message(context: Context): String = when {
    removed == 0 -> context.getString(dev.hgallery.app.R.string.delete_failed)
    failed > 0 -> context.resources.getQuantityString(
        dev.hgallery.app.R.plurals.delete_partial, total, failed, total,
    )
    trashed > 0 -> context.resources.getQuantityString(
        dev.hgallery.app.R.plurals.moved_to_trash, trashed, trashed,
    )
    else -> context.resources.getQuantityString(
        dev.hgallery.app.R.plurals.deleted_permanently, deleted, deleted,
    )
}
