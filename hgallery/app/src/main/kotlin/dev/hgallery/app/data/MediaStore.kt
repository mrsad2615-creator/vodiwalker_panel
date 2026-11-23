package dev.hgallery.app.data

import android.content.Context
import android.util.LruCache
import dev.hgallery.core.AlbumBuilder
import dev.hgallery.core.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single source of truth the screens observe.
 *
 * The library is loaded once and then patched in place by the actions the user
 * performs (favourite, delete), so a change is reflected on every screen without
 * a full MediaStore re-query — which on this hardware is the difference between
 * an instant toggle and a visible stall.
 */
class MediaStore(
    private val repository: MediaRepository,
    private val thumbnails: ThumbnailStore,
    private val actions: MediaActions,
) {

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val loadLock = Mutex()
    private var loadedOnce = false

    val thumbnailStore: ThumbnailStore get() = thumbnails

    suspend fun load(force: Boolean = false) {
        if (loadedOnce && !force) return
        loadLock.withLock {
            if (loadedOnce && !force) return
            _loading.value = true
            _error.value = null
            try {
                _items.value = repository.loadAll()
                loadedOnce = true
            } catch (e: android.database.sqlite.SQLiteException) {
                // A corrupt or locked MediaStore database: recoverable by asking
                // again later, so it is reported rather than fatal.
                _error.value = e.message ?: "media store unavailable"
            } catch (e: SecurityException) {
                // The permission was revoked between the check and the query —
                // a real state on Android 14's partial access, not a crash.
                _error.value = e.message ?: "media permission was revoked"
            } finally {
                _loading.value = false
            }
        }
    }

    fun startLoading() {
        _loading.value = true
    }

    /**
     * The consent dialog for a favourite write, if the platform wants one.
     *
     * Null on platforms with no such API, and also null when the write needs no
     * grant — the caller then retries the toggle directly, which succeeds.
     */
    fun favouriteWriteRequest(id: Long): android.content.IntentSender? {
        val current = _items.value.firstOrNull { it.id == id } ?: return null
        return actions.buildWriteRequest(listOf(android.net.Uri.parse(current.uri)))
    }

    /**
     * Favourite toggle, applied optimistically and rolled back if the platform refuses.
     *
     * Returns false both when the row was never written *and* when the platform
     * wanted the user's consent first; the caller distinguishes the two by asking
     * [favouriteWriteRequest] and relaunching after consent. Before this, a refusal
     * was indistinguishable from a tap that did nothing, because `update` reports it
     * as "0 rows" rather than as an error.
     */
    suspend fun toggleFavorite(id: Long): Boolean {
        val current = _items.value.firstOrNull { it.id == id } ?: return false
        val target = !current.isFavorite
        val uri = android.net.Uri.parse(current.uri)
        val ok = repository.setFavorite(uri, target)
        if (ok) {
            _items.value = _items.value.map { if (it.id == id) it.copy(isFavorite = target) else it }
        }
        return ok
    }

    suspend fun delete(ids: Set<Long>): Int {
        val targets = _items.value.filter { it.id in ids }
        val removed = repository.delete(targets.map { android.net.Uri.parse(it.uri) })
        if (removed > 0) {
            val gone = targets.map { it.id }.toSet()
            _items.value = _items.value.filterNot { it.id in gone }
        }
        return removed
    }

    /** The mechanism this device offers for a delete. */
    fun deletePath(): dev.hgallery.core.DeletePath = actions.pathFor()

    fun newRequestCode(): Int = actions.newRequestCode()

    /**
     * The sender that launches Android's own trash / delete confirmation.
     *
     * `null` means the platform has no such dialog and the caller should apply
     * the delete directly after its own confirmation.
     */
    fun buildDeleteRequest(ids: Set<Long>): android.content.IntentSender? {
        val uris = _items.value.filter { it.id in ids }.map { android.net.Uri.parse(it.uri) }
        return actions.buildDeleteRequest(uris, deletePath())
    }

    /** Applies the approved delete and drops the rows from every screen at once. */
    suspend fun applyDelete(ids: Set<Long>): dev.hgallery.core.DeleteOutcome {
        val targets = _items.value.filter { it.id in ids }
        val outcome = actions.applyDelete(targets.map { android.net.Uri.parse(it.uri) }, deletePath())
        if (outcome.removed > 0) {
            val gone = targets.map { it.id }.toSet()
            _items.value = _items.value.filterNot { it.id in gone }
            thumbnails.invalidate(gone.map { id -> targets.first { it.id == id }.uri })
        }
        return outcome
    }

    /** Builds the system share sheet for the selected items. */
    fun buildShareIntent(ids: Set<Long>): android.content.Intent? {
        val targets = _items.value.filter { it.id in ids }
        return actions.buildShareIntent(
            targets.map { android.net.Uri.parse(it.uri) },
            targets.map { it.mimeType },
        )
    }

    fun itemById(id: Long): MediaItem? = _items.value.firstOrNull { it.id == id }

    /** Counters for the Photos subtitle: "372 photos and 19 videos". */
    fun counts(): Pair<Int, Int> {
        val all = _items.value
        val videos = all.count { it.isVideo }
        return (all.size - videos) to videos
    }

    fun albums(): List<dev.hgallery.core.Album> = AlbumBuilder.fromItems(_items.value)
}
