package dev.hgallery.app.ui.components

/**
 * Stable selectors for instrumented tests.
 *
 * Kept in the main source set on purpose: a test tag that only exists in the
 * test source cannot be applied to the composable it is meant to select, and
 * `contentDescription` is user-facing text that is expected to change with the
 * locale.
 */
object HgTestTags {
    fun tab(name: String) = "tab-$name"
    /**
     * The stop a tap tool can find: the name the tab announces.
     *
     * `tab-*` is a test tag, and a test tag is not part of the view hierarchy that
     * `uiautomator` prints. The screenshot harness taps by content description, so
     * without this it had to guess a coordinate, and it guessed one that only worked
     * while the bar was under the system navigation inset.
     */
    const val TAB_LABEL_PREFIX = "tab-label-"
    const val thumbPrefix = "thumb-"
    fun thumbnail(id: Long) = "$thumbPrefix$id"
    fun album(name: String) = "album-$name"
    const val VIEWER = "viewer"
    const val PHOTOS_GRID = "photos-grid"
    const val VIEW_SWITCH = "photos-view-switch"
    const val MORE_BUTTON = "toolbar-more"

    // Selection, from reference R3.
    const val EXIT_SELECTION = "selection-exit"
    const val SELECTION_COUNT = "selection-count"
    const val SELECTION_SHARE = "selection-share"
    const val SELECTION_DELETE = "selection-delete"
    const val SELECTION_SELECT_ALL = "selection-select-all"
    const val SELECTION_BAR = "selection-bar"
    const val SELECT_BUTTON = "photos-select-button"
    const val SELECTION_ADD_TO_ALBUM = "selection-add-to-album"
    fun tick(id: Long) = "tick-$id"

    // Video player.
    const val VIDEO_PLAYER = "video-player"
    const val VIDEO_PLAY_PAUSE = "video-play-pause"
    const val VIDEO_CENTRE_BUTTON = "video-centre-button"
    const val VIDEO_PROGRESS = "video-progress"
    const val VIDEO_TIMECODE = "video-timecode"
    const val VIDEO_SPEED = "video-speed"
    const val VIDEO_SPEED_MENU = "video-speed-menu"
    const val VIDEO_FILL = "video-fill"
    const val VIDEO_MUTE = "video-mute"

    /** The one-tap filter row under the gallery title, and one chip inside it. */
    const val FILTER_CHIPS = "filter-chips"
    fun filterChip(kind: String) = "filter-chip-$kind"

    /** The page a clip occupies inside the viewer, and the target that opens it. */
    fun viewerVideoPage(id: Long) = "viewer-video-$id"
    fun viewerVideoPlay(id: Long) = "viewer-video-play-$id"

    // Search.
    const val SEARCH_FIELD = "search-field"
    const val SEARCH_RESULTS = "search-results"

    /** The details sheet in the viewer. */
    const val DETAILS_SHEET = "details-sheet"

    // Highlights and Discover.
    const val HIGHLIGHTS_LIST = "highlights-list"
    const val DISCOVER_LIST = "discover-list"
    const val DISCOVER_ON_THIS_DAY = "discover-on-this-day"
    const val DISCOVER_FAVOURITES = "discover-favourites"
    const val DISCOVER_LARGEST = "discover-largest"
    fun highlight(key: String) = "highlight-$key"
    fun discoverItem(id: Long) = "discover-item-$id"
    fun discoverYear(key: String) = "discover-$key"

    // Custom albums.
    const val ADD_ALBUM = "add-album"
    const val ALBUM_NAME_FIELD = "album-name-field"
    const val ALBUM_NAME_CONFIRM = "album-name-confirm"
    const val ALBUM_DELETE_CONFIRM = "album-delete-confirm"
    const val ALBUM_MENU_RENAME = "album-menu-rename"
    const val ALBUM_MENU_DELETE = "album-menu-delete"
    const val ALBUM_MENU_PRIVATE = "album-menu-private"
    const val ALBUM_MENU = "album-menu"
    fun customAlbum(id: Long) = "custom-album-$id"
    fun addToAlbumRow(id: Long) = "add-to-album-$id"
    const val SELECTION_NEW_ALBUM = "selection-new-album"

    // The photo filmstrip in the viewer.
    const val PHOTO_FILMSTRIP = "photo-filmstrip"
    fun filmstripThumb(id: Long) = "filmstrip-thumb-$id"

    // The video filmstrip.
    const val FILMSTRIP = "video-filmstrip"
    const val FILMSTRIP_HANDLE = "video-filmstrip-handle"
    fun filmstripCell(id: Long) = "filmstrip-cell-$id"

    // Rotate.
    const val ROTATE_90 = "rotate-90"
    const val ROTATE_CANCEL = "rotate-cancel"

    // Trash.
    const val TRASH_LIST = "trash-list"
    const val TRASH_BACK = "trash-back"
    const val TRASH_EMPTY = "trash-empty"
    const val TRASH_EMPTY_CONFIRM = "trash-empty-confirm"
    fun trashRestore(id: Long) = "trash-restore-$id"
    fun trashDelete(id: Long) = "trash-delete-$id"

    // Settings.
    const val SETTINGS_LIST = "settings-list"
    const val SETTINGS_BACK = "settings-back"
    const val GRID_SLIDER = "settings-grid-slider"
    const val SETTINGS_TRASH = "settings-trash"
    const val FILMSTRIP_SWITCH = "settings-filmstrip"
    const val AUTOPLAY_SWITCH = "settings-autoplay"
    fun themeOption(mode: String) = "settings-theme-$mode"
}
