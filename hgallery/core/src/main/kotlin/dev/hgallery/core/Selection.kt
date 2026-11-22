package dev.hgallery.core

/**
 * Selection state for a media grid.
 *
 * The rules are subtle enough to be worth testing without a device, which is why
 * they live here instead of inside a composable:
 *
 *  - selection is *lazy*: nothing is selected until the user long-presses a tile
 *    or explicitly enters selection mode, so a plain tap still opens a photo;
 *  - a tap inside selection mode toggles one tile;
 *  - deselecting the last tile leaves selection mode, because an empty selection
 *    toolbar with no way out but Back is a trap;
 *  - the id set is pruned against what still exists, so deleting items can never
 *    leave a selection referring to rows that are gone.
 *
 * Reference: R3 (Huawei's own "3 items selected" capture) shows a close glyph and
 * a count in the title, with the batch actions in a row at the bottom.
 */
data class Selection(
    val ids: Set<Long> = emptySet(),
    val active: Boolean = false,
) {

    val count: Int get() = ids.size

    val isEmpty: Boolean get() = ids.isEmpty()

    fun contains(id: Long): Boolean = id in ids

    /** The long-press entry point: select this tile and enter selection mode. */
    fun beginWith(id: Long): Selection = Selection(setOf(id), active = true)

    /**
     * A tap while selection mode is running.
     *
     * When selection is *not* active the caller is expected to have opened the
     * item instead; this returns [beginWith] so the invariant "tapping while
     * active toggles" still holds if a caller ever forwards the tap.
     */
    fun toggle(id: Long): Selection {
        if (!active) return beginWith(id)
        val next = if (id in ids) ids - id else ids + id
        return if (next.isEmpty()) Selection() else Selection(next, active = true)
    }

    /** "Select all" over the items currently on screen. */
    fun selectAll(all: Collection<Long>): Selection =
        if (all.isEmpty()) Selection() else Selection(all.toSet(), active = true)

    fun isAllSelected(all: Collection<Long>): Boolean {
        if (!active || ids.isEmpty()) return false
        val wanted = all.toSet()
        return wanted.isNotEmpty() && ids.size == wanted.size && ids.containsAll(wanted)
    }

    /** Drop ids that no longer exist; leave selection mode if none remain. */
    fun prune(existing: Set<Long>): Selection {
        val kept = ids.filterTo(LinkedHashSet()) { it in existing }
        return when {
            kept.isEmpty() -> Selection()
            kept.size == ids.size -> this
            else -> Selection(kept, active)
        }
    }

    fun exit(): Selection = Selection()

    companion object {
        val None = Selection()
    }
}
