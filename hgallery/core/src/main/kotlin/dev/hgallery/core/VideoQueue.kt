package dev.hgallery.core

/**
 * The order videos play in, and which one comes next.
 *
 * The filmstrip shows the same list this returns, so the strip and the queue cannot
 * disagree about what "next" means. Both read one ordering, newest first.
 *
 * ## Why an explicit index
 *
 * The player is given one item at a time. Without a queue there is nothing to move to
 * when a clip ends, and the user has to leave the player to find the next one. The queue
 * is the reason the filmstrip can switch without a round trip through a grid.
 */
object VideoQueue {

    /**
     * Every video, in the order the filmstrip shows it.
     *
     * The order is the same as the Photos timeline: newest first. A strip that reordered
     * the clips would make "next" mean something different from what the grid showed.
     */
    fun build(items: List<MediaItem>): List<MediaItem> =
        items.filter { it.isVideo }
            .sortedWith(compareByDescending<MediaItem> { it.dateTakenMs }.thenByDescending { it.id })

    /** Positions of [id] in [queue], or -1 when it is not there. */
    fun indexOf(queue: List<MediaItem>, id: Long): Int = queue.indexOfFirst { it.id == id }

    /**
     * The item after [id].
     *
     * Returns null at the end of the queue, and null when [id] is unknown. Wrapping around
     * is deliberately *not* done here: an app that plays the first video again after the
     * last one looks like it ignored the user's intent to stop. The caller decides.
     */
    fun next(queue: List<MediaItem>, id: Long): MediaItem? {
        val index = indexOf(queue, id)
        if (index < 0) return null
        return queue.getOrNull(index + 1)
    }

    /** The item before [id], or null at the start. */
    fun previous(queue: List<MediaItem>, id: Long): MediaItem? {
        val index = indexOf(queue, id)
        if (index <= 0) return null
        return queue.getOrNull(index - 1)
    }

    /**
     * The one-line caption for a strip cell: the length, and nothing else.
     *
     * A name is not used because a filmstrip cell is about 80 dp wide and a truncated file
     * name reads as noise. The reader still needs to know which clip is which, so the
     * screen gives each cell a spoken label from the media itself.
     */
    fun badge(item: MediaItem): String = DurationFormatter.badge(item.durationMs)
}
