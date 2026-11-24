package dev.hgallery.core

/**
 * The one-tap filters that sit under the gallery header.
 *
 * ## Why this is a rule and not a condition in the screen
 *
 * The chips are a second way into the same decision that search makes: which items are
 * on screen. Writing the decision into a composable would put it out of reach of a
 * test, and the audit found that the chips existed in the toolbar with no filtering
 * behind them at all. A named rule with its own table of cases is cheaper to trust.
 */
object GalleryFilter {

    /** What the chip row offers. [label] is the resource name in the string table. */
    enum class Kind {
        ALL,
        FAVOURITES,
        PHOTOS,
        VIDEOS,
        ;

        companion object {
            /** The order the chips are drawn in. */
            val ordered: List<Kind> = listOf(ALL, FAVOURITES, PHOTOS, VIDEOS)
        }
    }

    /**
     * True when [item] survives [kind].
     *
     * [Kind.ALL] passes everything, including an item that is neither a photograph nor a
     * clip, because a filter must never hide something the unfiltered grid would show.
     */
    fun keeps(item: MediaItem, kind: Kind): Boolean = when (kind) {
        Kind.ALL -> true
        Kind.FAVOURITES -> item.isFavorite
        Kind.PHOTOS -> !item.isVideo
        Kind.VIDEOS -> item.isVideo
    }

    /**
     * Applies [kind] to [items], keeping the order the timeline gave them.
     *
     * Order is preserved on purpose. A filter that sorts would move the item under the
     * user's finger, and the grid is what tells them where they are in the roll.
     */
    fun apply(items: List<MediaItem>, kind: Kind): List<MediaItem> =
        if (kind == Kind.ALL) items else items.filter { keeps(it, kind) }

    /**
     * True when a chip should show a count beside its label.
     *
     * Only the chips that can be empty do. `All` is never empty when the library is not,
     * and a "0" beside it while the grid is full reads as a bug.
     */
    fun showsCount(kind: Kind): Boolean = kind != Kind.ALL

    /** The number a chip shows, or null when it shows none. */
    fun count(items: List<MediaItem>, kind: Kind): Int? =
        if (showsCount(kind)) items.count { keeps(it, kind) } else null
}
