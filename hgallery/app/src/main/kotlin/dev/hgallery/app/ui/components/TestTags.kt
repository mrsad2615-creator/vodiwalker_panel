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

    // Selection, from reference R3.
    const val EXIT_SELECTION = "selection-exit"
    const val SELECTION_COUNT = "selection-count"
    const val SELECTION_SHARE = "selection-share"
    const val SELECTION_DELETE = "selection-delete"
    const val SELECTION_SELECT_ALL = "selection-select-all"
    const val SELECTION_BAR = "selection-bar"
    const val SELECT_BUTTON = "photos-select-button"
    fun tick(id: Long) = "tick-$id"
}
