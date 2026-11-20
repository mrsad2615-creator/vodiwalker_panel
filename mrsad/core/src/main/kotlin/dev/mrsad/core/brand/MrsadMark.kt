package dev.mrsad.core.brand

import kotlin.math.abs
import kotlin.math.hypot

/**
 * The MRSAD brand mark, as pure geometry.
 *
 * This is the same "Geometric M" that ships as the launcher icon
 * (`app/src/main/res/drawable/ic_launcher_foreground.xml`) and as the splash
 * mark: six parallel 45-degree metal plates forming a chevron with two
 * outer stems.
 *
 * The mark's construction lives here, in the dependency-free core, so it can
 * be unit tested on a plain JVM and so the Android drawables, the splash
 * artwork and the in-app component are all generated from one description.
 * `docs/branding/build_icon.py` writes the same construction into the
 * vector drawables; the checks in `CoreChecks` pin the numbers that the
 * script produces, so the two cannot drift apart unnoticed.
 *
 * Mark space is a 703 x 653 grid, symmetric about x = 351.5, with the outer
 * left edge at x = 0 and the top edge at y = 0.
 */
object MrsadMark {

    /** Mark width in mark units. */
    const val WIDTH: Float = 703f

    /** Mark height in mark units. */
    const val HEIGHT: Float = 653f

    /** Half the mark width; the axis of symmetry. */
    const val CENTRE: Float = WIDTH / 2f

    /** Width divided by height. */
    const val ASPECT: Float = WIDTH / HEIGHT

    // Chevron band edges, sampled from the concept's own plate boundaries.
    private const val UPPER_SLOPE = 365f / CENTRE
    private const val UPPER_VERTEX = 365f
    private const val LOWER_AT_0 = 182f
    private const val LOWER_AT_CENTRE = 540f
    private const val LOWER_SLOPE = (LOWER_AT_CENTRE - LOWER_AT_0) / CENTRE

    // Vertical slices that cut the chevron band into plates.
    private const val SLICE_AD = 198f
    private const val SLICE_BC = CENTRE
    private const val SLICE_CD = 505f

    // Grooves: the vertical slices are cut across the band, so they need to
    // be wider than the stem groove to read the same at launcher size.
    const val GROOVE_SPLIT: Float = 6f
    const val GROOVE_STEM: Float = 13f

    // Stems.
    private const val STEM_W = 121f
    private const val STEM_BOTTOM = 653f
    private const val STEM_CUT = 480f

    /** A point in mark space. */
    data class Point(val x: Float, val y: Float)

    /** Upper edge of the chevron band at [x]. */
    fun upper(x: Float): Float = UPPER_SLOPE * minOf(x, WIDTH - x)

    /** Lower edge of the chevron band at [x]. */
    fun lower(x: Float): Float = LOWER_AT_0 + LOWER_SLOPE * minOf(x, WIDTH - x)

    private fun band(x0: Float, x1: Float): List<Point> = listOf(
        Point(x0, upper(x0)),
        Point(x1, upper(x1)),
        Point(x1, lower(x1)),
        Point(x0, lower(x0)),
    )

    /**
     * The six plates of the mark, in draw order:
     *
     * ```
     *  A  outer-left band, bright   \
     *  B  inner-left band,  mid       >  the chevron that forms the M's middle
     *  C  inner-right band, mid       /
     *  D  outer-right band, bright
     *  E  left stem, dark
     *  F  right stem, dark
     * ```
     *
     * The thin grooves between the plates are what make the mark read as
     * folded sheet metal rather than a flat glyph.
     */
    fun plates(
        grooveSplit: Float = GROOVE_SPLIT,
        grooveStem: Float = GROOVE_STEM,
    ): List<List<Point>> {
        val g = grooveSplit / 2f
        val gs = grooveStem
        fun stem(xOuter: Float, xInner: Float) = listOf(
            Point(xOuter, lower(xOuter) + gs),
            Point(xInner, lower(xInner) + gs),
            Point(xInner, STEM_BOTTOM),
            Point(xOuter, STEM_CUT),
        )
        return listOf(
            band(0f, SLICE_AD - g),
            band(SLICE_AD + g, SLICE_BC - g),
            band(SLICE_BC + g, SLICE_CD - g),
            band(SLICE_CD + g, WIDTH),
            stem(0f, STEM_W),
            stem(WIDTH, WIDTH - STEM_W),
        )
    }

    /**
     * The mark as one connected outline, for the monochrome layer.
     *
     * The platform tints themed icons with a single colour, so the grooves
     * have to close up for the shape to stay legible once it is flattened.
     */
    fun silhouette(): List<Point> = listOf(
        Point(0f, 0f),
        Point(CENTRE, UPPER_VERTEX),
        Point(WIDTH, 0f),
        Point(WIDTH, STEM_CUT),
        Point(WIDTH - STEM_W, STEM_BOTTOM),
        Point(WIDTH - STEM_W, lower(WIDTH - STEM_W)),
        Point(CENTRE, LOWER_AT_CENTRE),
        Point(STEM_W, lower(STEM_W)),
        Point(STEM_W, STEM_BOTTOM),
        Point(0f, STEM_CUT),
    )

    /**
     * Gradient axis for a plate: across the band, from the lit edge inward.
     *
     * Each plate is shaded along its own short axis, so the fold reads the
     * same way on every plate whichever way the band runs.
     */
    fun gradientAxis(poly: List<Point>): Pair<Point, Point> {
        val cx = poly.sumOf { it.x.toDouble() } / poly.size
        val cy = poly.sumOf { it.y.toDouble() } / poly.size
        var nx = -UPPER_SLOPE.toDouble()
        var ny = 1.0
        val norm = hypot(nx, ny)
        nx /= norm
        ny /= norm
        if (nx * cx + ny * cy > 0) {
            nx = -nx
            ny = -ny
        }
        var half = 0.0
        poly.forEach {
            val d = abs((it.x - cx) * nx + (it.y - cy) * ny)
            if (d > half) half = d
        }
        return Point((cx - nx * half).toFloat(), (cy - ny * half).toFloat()) to
            Point((cx + nx * half).toFloat(), (cy + ny * half).toFloat())
    }

    /**
     * The smallest circle that contains every point of the mark.
     *
     * The launcher fits the artwork into the 66dp safe zone of the
     * adaptive-icon canvas, so this circle's radius decides the scale
     * factor of the shipped icon.
     *
     * The search runs in double precision on purpose: the winning circle
     * usually touches three points exactly, and a single-precision centre
     * is enough to push one of them outside its own radius.
     */
    fun minimalEnclosingCircle(points: List<Point>): Pair<Point, Float> {
        require(points.size >= 2) { "need at least two points" }
        val xs = points.map { it.x.toDouble() }
        val ys = points.map { it.y.toDouble() }

        var bestX = 0.0
        var bestY = 0.0
        var bestRadius = Double.MAX_VALUE

        fun contains(cx: Double, cy: Double, radius: Double): Boolean {
            val tolerance = radius * 1e-9 + 1e-9
            return points.indices.all { i ->
                hypot(xs[i] - cx, ys[i] - cy) <= radius + tolerance
            }
        }

        // A circle through two points is centred on their midpoint.
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val cx = (xs[i] + xs[j]) / 2
                val cy = (ys[i] + ys[j]) / 2
                val radius = hypot(xs[j] - xs[i], ys[j] - ys[i]) / 2
                if (radius < bestRadius && contains(cx, cy, radius)) {
                    bestX = cx
                    bestY = cy
                    bestRadius = radius
                }
            }
        }

        // Otherwise the smallest circle is defined by three points.
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                for (k in j + 1 until points.size) {
                    val d = 2.0 * (xs[i] * (ys[j] - ys[k]) +
                        xs[j] * (ys[k] - ys[i]) +
                        xs[k] * (ys[i] - ys[j]))
                    if (abs(d) < 1e-12) continue
                    val ux = ((xs[i] * xs[i] + ys[i] * ys[i]) * (ys[j] - ys[k]) +
                        (xs[j] * xs[j] + ys[j] * ys[j]) * (ys[k] - ys[i]) +
                        (xs[k] * xs[k] + ys[k] * ys[k]) * (ys[i] - ys[j])) / d
                    val uy = ((xs[i] * xs[i] + ys[i] * ys[i]) * (xs[k] - xs[j]) +
                        (xs[j] * xs[j] + ys[j] * ys[j]) * (xs[i] - xs[k]) +
                        (xs[k] * xs[k] + ys[k] * ys[k]) * (xs[j] - xs[i])) / d
                    val radius = hypot(xs[i] - ux, ys[i] - uy)
                    if (radius < bestRadius && contains(ux, uy, radius)) {
                        bestX = ux
                        bestY = uy
                        bestRadius = radius
                    }
                }
            }
        }

        check(bestRadius < Double.MAX_VALUE) { "no enclosing circle found" }
        return Point(bestX.toFloat(), bestY.toFloat()) to bestRadius.toFloat()
    }
}
