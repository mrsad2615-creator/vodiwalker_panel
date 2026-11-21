package dev.hgallery.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icons drawn as original geometry to Huawei's optical weight: a 24 dp box, a
 * 1.8 dp stroke, round caps and round joins.
 *
 * Material's default 2 dp stroke reads heavier than the reference, which is why
 * these exist instead of `Icons.Outlined.*`.
 */
object HgIcons {

    private fun icon(name: String, build: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = build,
            )
        }.build()

    private fun solid(name: String, build: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathBuilder = build)
        }.build()

    /** Photos: a rounded picture frame with a sun and a hill. */
    val Photos: ImageVector = icon("HG.Photos") {
        moveTo(3.2f, 6.2f)
        arcToRelative(1.6f, 1.6f, 0f, false, true, 1.6f, -1.6f)
        horizontalLineToRelative(14.4f)
        arcToRelative(1.6f, 1.6f, 0f, false, true, 1.6f, 1.6f)
        verticalLineToRelative(11.6f)
        arcToRelative(1.6f, 1.6f, 0f, false, true, -1.6f, 1.6f)
        horizontalLineToRelative(-14.4f)
        arcToRelative(1.6f, 1.6f, 0f, false, true, -1.6f, -1.6f)
        close()
        moveTo(3.2f, 16.2f)
        lineTo(8.6f, 11f)
        curveTo(9.2f, 10.4f, 10.1f, 10.4f, 10.7f, 11f)
        lineTo(15.4f, 15.6f)
        lineTo(17.8f, 13.4f)
        curveTo(18.4f, 12.8f, 19.3f, 12.8f, 19.9f, 13.4f)
        lineTo(20.8f, 14.3f)
        moveTo(15.4f, 9.2f)
        arcToRelative(1.05f, 1.05f, 0f, true, false, 0.001f, 0f)
        close()
    }

    /** Albums: an open folder. */
    val Albums: ImageVector = icon("HG.Albums") {
        moveTo(3f, 8.4f)
        curveTo(3f, 7.5f, 3.7f, 6.8f, 4.6f, 6.8f)
        horizontalLineToRelative(3.6f)
        lineTo(10.4f, 4.6f)
        horizontalLineToRelative(4.4f)
        curveTo(15.8f, 4.6f, 16.5f, 5.3f, 16.5f, 6.2f)
        verticalLineToRelative(1.4f)
        moveTo(3f, 8.4f)
        verticalLineToRelative(8.4f)
        curveTo(3f, 17.7f, 3.7f, 18.4f, 4.6f, 18.4f)
        horizontalLineToRelative(12.9f)
        curveTo(18.4f, 18.4f, 19.1f, 17.7f, 19.1f, 16.8f)
        verticalLineToRelative(-6.9f)
        curveTo(19.1f, 9.0f, 18.4f, 8.3f, 17.5f, 8.3f)
        close()
        moveTo(19.1f, 10.6f)
        lineTo(21f, 16.2f)
        curveTo(21.2f, 16.9f, 20.7f, 17.6f, 20f, 17.6f)
    }

    /**
     * Discover. The reference uses a faceted spherical mark; a circle crossed by
     * two shallow arcs reads as the same object at 24 dp.
     */
    val Discover: ImageVector = icon("HG.Discover") {
        moveTo(12f, 20.8f)
        arcToRelative(8.8f, 8.8f, 0f, true, false, 0.001f, 0f)
        close()
        moveTo(6.4f, 6.2f)
        curveTo(9.6f, 10.2f, 9.6f, 13.8f, 6.4f, 17.8f)
        moveTo(17.6f, 6.2f)
        curveTo(14.4f, 10.2f, 14.4f, 13.8f, 17.6f, 17.8f)
    }

    /** Highlights: a five-point star, Huawei's favourites mark. */
    val Highlights: ImageVector = icon("HG.Highlights") {
        moveTo(12f, 3.6f)
        lineTo(14.5f, 9.4f)
        lineTo(20.8f, 10f)
        lineTo(16.1f, 14.2f)
        lineTo(17.5f, 20.4f)
        lineTo(12f, 17.1f)
        lineTo(6.5f, 20.4f)
        lineTo(7.9f, 14.2f)
        lineTo(3.2f, 10f)
        lineTo(9.5f, 9.4f)
        close()
    }

    val Search: ImageVector = icon("HG.Search") {
        moveTo(10.8f, 18.2f)
        arcToRelative(7.4f, 7.4f, 0f, true, false, 0.001f, 0f)
        close()
        moveTo(16.2f, 16.2f)
        lineTo(20.8f, 20.8f)
    }

    val More: ImageVector = solid("HG.More") {
        moveTo(11.1f, 12f)
        arcToRelative(0.95f, 0.95f, 0f, true, false, 1.8f, 0f)
        arcToRelative(0.95f, 0.95f, 0f, true, false, -1.8f, 0f)
        close()
        moveTo(11.1f, 5.6f)
        arcToRelative(0.95f, 0.95f, 0f, true, false, 1.8f, 0f)
        arcToRelative(0.95f, 0.95f, 0f, true, false, -1.8f, 0f)
        close()
        moveTo(11.1f, 18.4f)
        arcToRelative(0.95f, 0.95f, 0f, true, false, 1.8f, 0f)
        arcToRelative(0.95f, 0.95f, 0f, true, false, -1.8f, 0f)
        close()
    }

    val Back: ImageVector = icon("HG.Back") {
        moveTo(14.6f, 4.8f)
        lineTo(7.2f, 12f)
        lineTo(14.6f, 19.2f)
    }

    val Close: ImageVector = icon("HG.Close") {
        moveTo(5.6f, 5.6f)
        lineTo(18.4f, 18.4f)
        moveTo(18.4f, 5.6f)
        lineTo(5.6f, 18.4f)
    }

    val Share: ImageVector = icon("HG.Share") {
        moveTo(12f, 15.4f)
        verticalLineToRelative(-11f)
        moveTo(7.8f, 8.2f)
        lineTo(12f, 4f)
        lineTo(16.2f, 8.2f)
        moveTo(5.4f, 13.4f)
        verticalLineToRelative(5.2f)
        curveTo(5.4f, 19.4f, 6.1f, 20f, 6.9f, 20f)
        horizontalLineToRelative(10.2f)
        curveTo(17.9f, 20f, 18.6f, 19.4f, 18.6f, 18.6f)
        verticalLineToRelative(-5.2f)
    }

    val Delete: ImageVector = icon("HG.Delete") {
        moveTo(4.4f, 6.6f)
        horizontalLineToRelative(15.2f)
        moveTo(9.2f, 6.6f)
        verticalLineToRelative(-1.4f)
        curveTo(9.2f, 4.5f, 9.8f, 4f, 10.4f, 4f)
        horizontalLineToRelative(3.2f)
        curveTo(14.2f, 4f, 14.8f, 4.5f, 14.8f, 5.2f)
        verticalLineToRelative(1.4f)
        moveTo(6.6f, 6.6f)
        verticalLineToRelative(12f)
        curveTo(6.6f, 19.4f, 7.2f, 20f, 8f, 20f)
        horizontalLineToRelative(8f)
        curveTo(16.8f, 20f, 17.4f, 19.4f, 17.4f, 18.6f)
        verticalLineToRelative(-12f)
        moveTo(10.2f, 10f)
        verticalLineToRelative(6.4f)
        moveTo(13.8f, 10f)
        verticalLineToRelative(6.4f)
    }

    val Favorite: ImageVector = icon("HG.Favorite") {
        moveTo(12f, 20f)
        curveTo(12f, 20f, 3.6f, 14.8f, 3.6f, 9.5f)
        curveTo(3.6f, 6.9f, 5.6f, 4.8f, 8.1f, 4.8f)
        curveTo(9.8f, 4.8f, 11.2f, 5.7f, 12f, 7.1f)
        curveTo(12.8f, 5.7f, 14.2f, 4.8f, 15.9f, 4.8f)
        curveTo(18.4f, 4.8f, 20.4f, 6.9f, 20.4f, 9.5f)
        curveTo(20.4f, 14.8f, 12f, 20f, 12f, 20f)
        close()
    }

    val FavoriteFilled: ImageVector = solid("HG.FavoriteFilled") {
        moveTo(12f, 20f)
        curveTo(12f, 20f, 3.6f, 14.8f, 3.6f, 9.5f)
        curveTo(3.6f, 6.9f, 5.6f, 4.8f, 8.1f, 4.8f)
        curveTo(9.8f, 4.8f, 11.2f, 5.7f, 12f, 7.1f)
        curveTo(12.8f, 5.7f, 14.2f, 4.8f, 15.9f, 4.8f)
        curveTo(18.4f, 4.8f, 20.4f, 6.9f, 20.4f, 9.5f)
        curveTo(20.4f, 14.8f, 12f, 20f, 12f, 20f)
        close()
    }

    val Edit: ImageVector = icon("HG.Edit") {
        moveTo(4.2f, 19.8f)
        lineTo(4.9f, 15.6f)
        lineTo(16.2f, 4.3f)
        curveTo(16.9f, 3.6f, 18f, 3.6f, 18.7f, 4.3f)
        lineTo(19.7f, 5.3f)
        curveTo(20.4f, 6f, 20.4f, 7.1f, 19.7f, 7.8f)
        lineTo(8.4f, 19.1f)
        close()
        moveTo(14.6f, 5.9f)
        lineTo(18.1f, 9.4f)
    }

    val Info: ImageVector = icon("HG.Info") {
        moveTo(12f, 20.8f)
        arcToRelative(8.8f, 8.8f, 0f, true, false, 0.001f, 0f)
        close()
        moveTo(12f, 11f)
        verticalLineToRelative(5.4f)
        moveTo(12f, 7.8f)
        verticalLineToRelative(0.4f)
    }

    val Play: ImageVector = solid("HG.Play") {
        moveTo(8.4f, 5.6f)
        lineTo(18.6f, 12f)
        lineTo(8.4f, 18.4f)
        close()
    }

    val Check: ImageVector = icon("HG.Check") {
        moveTo(5f, 12.4f)
        lineTo(9.6f, 17f)
        lineTo(19f, 7.4f)
    }

    val Add: ImageVector = icon("HG.Add") {
        moveTo(12f, 5f)
        verticalLineToRelative(14f)
        moveTo(5f, 12f)
        horizontalLineToRelative(14f)
    }

    val Folder: ImageVector = icon("HG.Folder") {
        moveTo(3.4f, 7.2f)
        curveTo(3.4f, 6.3f, 4.1f, 5.6f, 5f, 5.6f)
        horizontalLineToRelative(4.1f)
        lineTo(11f, 7.6f)
        horizontalLineToRelative(8f)
        curveTo(19.9f, 7.6f, 20.6f, 8.3f, 20.6f, 9.2f)
        verticalLineToRelative(8f)
        curveTo(20.6f, 18.1f, 19.9f, 18.8f, 19f, 18.8f)
        horizontalLineTo(5f)
        curveTo(4.1f, 18.8f, 3.4f, 18.1f, 3.4f, 17.2f)
        close()
    }

    val Trash: ImageVector = Delete

    val Restore: ImageVector = icon("HG.Restore") {
        moveTo(4.2f, 12f)
        arcToRelative(7.8f, 7.8f, 0f, true, false, 2.3f, -5.5f)
        moveTo(4.2f, 4.4f)
        verticalLineToRelative(4.4f)
        horizontalLineToRelative(4.4f)
    }

    val Sort: ImageVector = icon("HG.Sort") {
        moveTo(4.6f, 7f)
        horizontalLineToRelative(14.8f)
        moveTo(7.4f, 12f)
        horizontalLineToRelative(9.2f)
        moveTo(10.2f, 17f)
        horizontalLineToRelative(3.6f)
    }

    val SelectAll: ImageVector = icon("HG.SelectAll") {
        moveTo(4.4f, 6.6f)
        curveTo(4.4f, 5.4f, 5.4f, 4.4f, 6.6f, 4.4f)
        horizontalLineToRelative(10.8f)
        curveTo(18.6f, 4.4f, 19.6f, 5.4f, 19.6f, 6.6f)
        verticalLineToRelative(10.8f)
        curveTo(19.6f, 18.6f, 18.6f, 19.6f, 17.4f, 19.6f)
        horizontalLineTo(6.6f)
        curveTo(5.4f, 19.6f, 4.4f, 18.6f, 4.4f, 17.4f)
        close()
        moveTo(8.4f, 12.2f)
        lineTo(11.2f, 15f)
        lineTo(15.8f, 9.4f)
    }

    val Copy: ImageVector = icon("HG.Copy") {
        moveTo(8.4f, 8.4f)
        verticalLineToRelative(-2.2f)
        curveTo(8.4f, 5.2f, 9.2f, 4.4f, 10.2f, 4.4f)
        horizontalLineToRelative(7.4f)
        curveTo(18.6f, 4.4f, 19.4f, 5.2f, 19.4f, 6.2f)
        verticalLineToRelative(7.4f)
        curveTo(19.4f, 14.6f, 18.6f, 15.4f, 17.6f, 15.4f)
        horizontalLineToRelative(-2.2f)
        moveTo(4.6f, 10.2f)
        curveTo(4.6f, 9.2f, 5.4f, 8.4f, 6.4f, 8.4f)
        horizontalLineToRelative(7.4f)
        curveTo(14.8f, 8.4f, 15.6f, 9.2f, 15.6f, 10.2f)
        verticalLineToRelative(7.4f)
        curveTo(15.6f, 18.6f, 14.8f, 19.4f, 13.8f, 19.4f)
        horizontalLineTo(6.4f)
        curveTo(5.4f, 19.4f, 4.6f, 18.6f, 4.6f, 17.6f)
        close()
    }

    val Rotate: ImageVector = icon("HG.Rotate") {
        moveTo(12f, 5.4f)
        lineTo(12f, 18.6f)
        moveTo(5.4f, 12f)
        lineTo(18.6f, 12f)
        moveTo(7.2f, 7.2f)
        lineTo(16.8f, 16.8f)
        moveTo(16.8f, 7.2f)
        lineTo(7.2f, 16.8f)
    }
}
