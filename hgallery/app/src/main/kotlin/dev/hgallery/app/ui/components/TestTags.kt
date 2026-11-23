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

    // The video filmstrip.
    const val FILMSTRIP = "video-filmstrip"
    const val FILMSTRIP_HANDLE = "video-filmstrip-handle"
    fun filmstripCell(id: Long) = "filmstrip-cell-$id"

    // Settings.
    const val SETTINGS_LIST = "settings-list"
    const val SETTINGS_BACK = "settings-back"
    const val GRID_SLIDER = "settings-grid-slider"
    const val FILMSTRIP_SWITCH = "settings-filmstrip"
    const val AUTOPLAY_SWITCH = "settings-autoplay"
    fun themeOption(mode: String) = "settings-theme-$mode"
}
