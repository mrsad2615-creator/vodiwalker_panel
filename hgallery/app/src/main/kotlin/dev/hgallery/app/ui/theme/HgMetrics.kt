package dev.hgallery.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Layout numbers from the reference, in one place so a screen can never
 * quietly invent its own rhythm.
 */
object HgMetrics {
    /** The title and the first grid column share this left edge in the reference. */
    val Gutter = 20.dp
    /** Photos grid is nearly seamless: 2 dp between cells. */
    val GridGap = 2.dp
    val PhotosColumns = 4
    val AlbumColumns = 3
    val AlbumGap = 12.dp
    val AlbumCorner = 6.dp
    val SheetCorner = 20.dp
    val DialogCorner = 16.dp
    val MinTouchTarget = 48.dp
    val ToolbarIcon = 24.dp
    val BottomBarHeight = 56.dp
    val SectionHeaderHeight = 34.dp
}
