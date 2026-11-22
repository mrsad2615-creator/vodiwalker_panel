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
            } catch (t: Throwable) {
                _error.value = t.message ?: t::class.java.simpleName
            } finally {
                _loading.value = false
            }
        }
    }

    fun startLoading() {
        _loading.value = true
    }

    /** Favourite toggle, applied optimistically and rolled back if the platform refuses. */
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

    fun itemById(id: Long): MediaItem? = _items.value.firstOrNull { it.id == id }

    /** Counters for the Photos subtitle: "372 photos and 19 videos". */
    fun counts(): Pair<Int, Int> {
        val all = _items.value
        val videos = all.count { it.isVideo }
        return (all.size - videos) to videos
    }

    fun albums(): List<dev.hgallery.core.Album> = AlbumBuilder.fromItems(_items.value)
}
