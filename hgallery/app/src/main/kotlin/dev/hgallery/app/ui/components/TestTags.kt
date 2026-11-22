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
    fun thumbnail(id: Long) = "thumb-$id"
    fun album(name: String) = "album-$name"
    const val VIEWER = "viewer"
    const val PHOTOS_GRID = "photos-grid"
    const val VIEW_SWITCH = "photos-view-switch"
}
