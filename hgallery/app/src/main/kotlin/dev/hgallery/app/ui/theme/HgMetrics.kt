package dev.hgallery.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The old metrics name, kept as a view onto the new spacing and shape tokens.
 *
 * Same reasoning as [hgPalette]: fifteen files read `HgMetrics`, and the redesign
 * changes the grid gap and the corner radii. The values now come from [HgSpacing] and
 * [HgShapes], so a screen that has not been ported still moves with the design instead
 * of keeping the old numbers forever.
 */
object HgMetrics {
    val Gutter = 20.dp

    /** The grid gap grew from 2 dp to 3 dp, and titles sit on a radius now. */
    val GridGap = 3.dp

    const val PhotosColumns = 4
    const val MonthColumns = 6
    const val AlbumColumns = 2

    val AlbumGap = 12.dp

    /** Photographs are no longer square-cornered: this is the one visible radius change. */
    val AlbumCorner = 10.dp

    val SheetCorner = 28.dp
    val DialogCorner = 20.dp
    val MinTouchTarget = 48.dp
    val ToolbarIcon = 24.dp
    val BottomBarHeight = 64.dp
    val SectionHeaderHeight = 34.dp
}
