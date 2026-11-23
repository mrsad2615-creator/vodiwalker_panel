package dev.hgallery.core

/**
 * A collection the user made, as opposed to a folder the device already has.
 *
 * ## Why the distinction matters
 *
 * A device folder is a fact: MediaStore says these files are in `DCIM/Camera`. A custom
 * album is a choice: the user put these items together. The two cannot live in the same
 * model, because renaming a folder means moving files on disk while renaming a custom
 * album changes one word in the app.
 *
 * ## Membership
 *
 * An item's membership is a set of ids, not a copy of the files. Nothing is duplicated on
 * the device, so an album cannot drift out of sync with the library and cannot double the
 * storage a photograph costs.
 *
 * ## Private
 *
 * [isPrivate] hides the album behind the device lock. It is *not* encryption: the media
 * files stay where they are, readable by any app with media permission. The app states
 * that plainly instead of implying more.
 */
data class CustomAlbum(
    val id: Long,
    val name: String,
    /** Item ids, in the order the user added them. */
    val itemIds: List<Long> = emptyList(),
    /** Epoch millis of the last name or membership change; drives ordering. */
    val updatedAtMs: Long = 0L,
    val isPrivate: Boolean = false,
) {
    val count: Int get() = itemIds.size
}

/**
 * The rules that govern custom albums.
 *
 * Kept as pure functions so the screen never has to encode a policy: what a valid name is,
 * what happens to a name that is only spaces, and which album a new item goes into are all
 * questions with one answer, and that answer lives here.
 */
object AlbumRules {

    /** Longest name the app accepts. Long enough for a trip, short enough for a tile. */
    const val MAX_NAME_LENGTH = 40

    /** The first id a custom album may use. Negative ids are reserved for the built-ins. */
    const val FIRST_ID = 1L

    /**
     * Cleans a typed name.
     *
     * Surrounding spaces are removed and internal runs of spaces are collapsed, because a
     * name that ends in two spaces is invisible and then unmatched when the user looks for
     * it. A name that is only spaces becomes an empty string, which [isValidName] rejects.
     */
    fun clean(name: String): String =
        name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH)

    /** Whether [name] can be saved. */
    fun isValidName(name: String): Boolean = clean(name).isNotEmpty()

    /**
     * Whether [name] is already used by another album.
     *
     * Case is ignored: two tiles reading "Trips" and "trips" are indistinguishable at a
     * glance, and the user who made the second one meant the first.
     *
     * [exceptId] lets a rename keep its own name.
     */
    fun isTaken(name: String, albums: List<CustomAlbum>, exceptId: Long? = null): Boolean {
        val candidate = clean(name).lowercase()
        return albums.any { it.id != exceptId && clean(it.name).lowercase() == candidate }
    }

    /**
     * The next free id.
     *
     * Ids are never reused: a deleted album's id must not come back on a different album,
     * or a stale reference somewhere would silently point at new content.
     */
    fun nextId(albums: List<CustomAlbum>): Long =
        (albums.maxOfOrNull { it.id } ?: (FIRST_ID - 1)) + 1

    /** Adds an item to an album, keeping the order and dropping duplicates. */
    fun addItem(album: CustomAlbum, itemId: Long): CustomAlbum =
        if (itemId in album.itemIds) album else album.copy(itemIds = album.itemIds + itemId)

    /** Removes an item. Removing something absent is not an error. */
    fun removeItem(album: CustomAlbum, itemId: Long): CustomAlbum =
        album.copy(itemIds = album.itemIds - itemId)

    /**
     * Drops ids that are no longer in the library.
     *
     * Called after a delete: an album that kept the id of a deleted photograph would show
     * a count that does not match what it opens.
     */
    fun prune(album: CustomAlbum, live: Set<Long>): CustomAlbum =
        album.copy(itemIds = album.itemIds.filter { it in live })
}
