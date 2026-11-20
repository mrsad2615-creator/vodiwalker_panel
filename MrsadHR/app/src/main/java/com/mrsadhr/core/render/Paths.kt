package com.mrsadhr.core.render

/** Shared geometry helpers used by both painters so that shapes resolve identically. */
object Paths {

    fun addRoundRect(path: PathHandle, rect: Rect, corners: Corners) {
        val maxR = corners.maxRadius().coerceAtMost(kotlin.math.min(rect.width, rect.height) * 0.5f)
        if (corners.topLeft == corners.topRight && corners.topRight == corners.bottomRight &&
            corners.bottomRight == corners.bottomLeft
        ) {
            path.addRoundRect(rect, Corners.all(maxR))
            return
        }
        val tl = corners.topLeft.coerceAtMost(maxR)
        val tr = corners.topRight.coerceAtMost(maxR)
        val br = corners.bottomRight.coerceAtMost(maxR)
        val bl = corners.bottomLeft.coerceAtMost(maxR)
        val k = 0.5523f
        path.moveTo(rect.left + tl, rect.top)
        path.lineTo(rect.right - tr, rect.top)
        if (tr > 0f) path.cubicTo(rect.right - tr + tr * k, rect.top, rect.right, rect.top + tr - tr * k, rect.right, rect.top + tr)
        path.lineTo(rect.right, rect.bottom - br)
        if (br > 0f) path.cubicTo(rect.right, rect.bottom - br + br * k, rect.right - br + br * k, rect.bottom, rect.right - br, rect.bottom)
        path.lineTo(rect.left + bl, rect.bottom)
        if (bl > 0f) path.cubicTo(rect.left + bl - bl * k, rect.bottom, rect.left, rect.bottom - bl + bl * k, rect.left, rect.bottom - bl)
        path.lineTo(rect.left, rect.top + tl)
        if (tl > 0f) path.cubicTo(rect.left, rect.top + tl - tl * k, rect.left + tl - tl * k, rect.top, rect.left + tl, rect.top)
        path.close()
    }
}
