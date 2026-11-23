package dev.hgallery.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.hgallery.core.AlbumRules
import dev.hgallery.core.CustomAlbum
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Where custom albums live.
 *
 * ## One record, one entry
 *
 * Each album is stored as a single line of text, not as a serialisation of the whole list.
 * Rewriting the list on every change would make two changes in the same frame race, and a
 * crash mid-write would lose every album instead of one.
 *
 * ## Why text and not JSON
 *
 * The record has four fields and one list of numbers. A hand-written format keeps the app
 * free of a serialisation dependency, and the parser can be written to skip a damaged line
 * rather than throw: a single corrupt album must not make the Albums tab unopenable.
 *
 * Format: `id|name|updatedAt|private|id,id,id`
 */
private val Context.albumStore: DataStore<Preferences> by preferencesDataStore(name = "hgallery-albums")

class CustomAlbumStore(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Every album, oldest change first so the tab can show the newest last.
     *
     * A line that cannot be parsed is skipped and logged, not fatal. The alternative is a
     * single bad write that hides every album the user made.
     */
    val albums: Flow<List<CustomAlbum>> = appContext.albumStore.data.map { prefs ->
        prefs.asMap().entries
            .filter { it.key.name.startsWith(KEY_PREFIX) }
            .mapNotNull { (key, value) -> decode(value as? String) }
            .sortedBy { it.updatedAtMs }
    }

    /**
     * Saves one album.
     *
     * Returns the saved album so the caller can navigate to it without re-reading the
     * flow, and so a new album's id is known to the screen that created it.
     */
    suspend fun save(album: CustomAlbum): CustomAlbum {
        val stamped = album.copy(updatedAtMs = System.currentTimeMillis())
        appContext.albumStore.edit { prefs -> prefs[keyFor(stamped.id)] = encode(stamped) }
        return stamped
    }

    /**
     * Creates an album from a typed name.
     *
     * The name is cleaned and checked here rather than in the screen, so every path that
     * creates an album — the add sheet and any future import — obeys the same rule.
     * Returns null when the name is empty or already used.
     */
    suspend fun create(name: String, existing: List<CustomAlbum>): CustomAlbum? {
        val cleaned = AlbumRules.clean(name)
        if (!AlbumRules.isValidName(cleaned)) return null
        if (AlbumRules.isTaken(cleaned, existing)) return null
        return save(
            CustomAlbum(
                id = AlbumRules.nextId(existing),
                name = cleaned,
                itemIds = emptyList(),
            ),
        )
    }

    /** Renames an album. Returns null when the new name is not usable. */
    suspend fun rename(
        album: CustomAlbum,
        name: String,
        existing: List<CustomAlbum>,
    ): CustomAlbum? {
        val cleaned = AlbumRules.clean(name)
        if (!AlbumRules.isValidName(cleaned)) return null
        if (AlbumRules.isTaken(cleaned, existing, exceptId = album.id)) return null
        return save(album.copy(name = cleaned))
    }

    suspend fun delete(album: CustomAlbum) {
        appContext.albumStore.edit { prefs -> prefs.remove(keyFor(album.id)) }
    }

    /**
     * Records that an item is now in an album.
     *
     * The caller passes the album it already has; this writes the new membership back.
     */
    suspend fun setMembership(album: CustomAlbum): CustomAlbum = save(album)

    /**
     * Drops deleted media from every custom album.
     *
     * Called after a delete. Without it an album's count would include files that no
     * longer exist, and opening it would show fewer tiles than the count promised.
     */
    suspend fun pruneDeleted(albums: List<CustomAlbum>, liveIds: Set<Long>) {
        appContext.albumStore.edit { prefs ->
            albums.forEach { album ->
                val pruned = AlbumRules.prune(album, liveIds)
                if (pruned.itemIds.size != album.itemIds.size) {
                    prefs[keyFor(pruned.id)] = encode(pruned)
                }
            }
        }
    }

    private fun keyFor(id: Long) = stringPreferencesKey("$KEY_PREFIX$id")

    private fun encode(album: CustomAlbum): String = buildString {
        append(album.id)
        append(SEP)
        // A name may contain the separator only if it is escaped; names are cleaned to
        // collapse spaces, so the separator is replaced instead of escaped to keep the
        // round trip obvious.
        append(album.name.replace(SEP, ' '))
        append(SEP)
        append(album.updatedAtMs)
        append(SEP)
        append(if (album.isPrivate) "1" else "0")
        append(SEP)
        append(album.itemIds.joinToString(","))
    }

    private fun decode(raw: String?): CustomAlbum? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(SEP)
        if (parts.size < 5) return null
        val id = parts[0].toLongOrNull() ?: return null
        val name = parts[1].takeIf { AlbumRules.isValidName(it) } ?: return null
        val updated = parts[2].toLongOrNull() ?: 0L
        val isPrivate = parts[3] == "1"
        val ids = parts[4].split(",").mapNotNull { it.toLongOrNull() }
        return CustomAlbum(id, name, ids, updated, isPrivate)
    }

    private companion object {
        const val KEY_PREFIX = "album-"

        /** A unit separator: it cannot be typed, so a name cannot contain it by accident. */
        const val SEP = "\u001F"
    }
}
