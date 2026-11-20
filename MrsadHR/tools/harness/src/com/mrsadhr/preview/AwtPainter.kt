package com.mrsadhr.preview

import com.mrsadhr.core.render.Argb
import com.mrsadhr.core.render.Corners
import com.mrsadhr.core.render.FontMetrics
import com.mrsadhr.core.render.Paint
import com.mrsadhr.core.render.PaintStyle
import com.mrsadhr.core.render.Painter
import com.mrsadhr.core.render.PathHandle
import com.mrsadhr.core.render.Paths
import com.mrsadhr.core.render.Rect
import com.mrsadhr.core.render.Shader
import com.mrsadhr.core.render.StrokeCap
import com.mrsadhr.core.render.TextAlign
import com.mrsadhr.core.render.Typeface
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Composite
import java.awt.CompositeContext
import java.awt.Font
import java.awt.Graphics2D
import java.awt.MultipleGradientPaint
import java.awt.RenderingHints
import java.awt.RadialGradientPaint
import java.awt.LinearGradientPaint
import java.awt.Shape
import java.awt.Stroke
import java.awt.font.TextAttribute
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.awt.image.Raster
import java.awt.image.WritableRaster
import java.io.File
import java.text.AttributedString
import kotlin.math.abs
import kotlin.math.max

class AwtTypeface(val font: Font) : Typeface() {
    override val debugName: String get() = font.fontName
}

private class AwtShader(val paint: java.awt.Paint) : Shader()

/** Additive RGB composite (Android BlendMode.PLUS) for the preview harness. */
private class AdditiveComposite : Composite {
    override fun createContext(srcColorModel: ColorModel, dstColorModel: ColorModel, hints: RenderingHints?): CompositeContext =
        object : CompositeContext {
            override fun compose(src: Raster, dstIn: Raster, dstOut: WritableRaster) {
                val s = IntArray(4)
                val d = IntArray(4)
                for (y in 0 until dstIn.height) {
                    for (x in 0 until dstIn.width) {
                        src.getPixel(x, y, s)
                        dstIn.getPixel(x, y, d)
                        // PorterDuff.ADD semantics: the source is premultiplied by its own alpha, so
                        // a 5% white sheen adds 5% light and not a solid white band.
                        val a = s[3] / 255f
                        for (i in 0 until 3) {
                            dstOut.setSample(x, y, i, (s[i] * a + d[i]).toInt().coerceAtMost(255))
                        }
                        dstOut.setSample(x, y, 3, max(s[3], d[3]))
                    }
                }
            }

            override fun dispose() {}
        }
}

class AwtPath : PathHandle {
    val path = Path2D.Float()
    private var lastX = 0f
    private var lastY = 0f

    override fun reset(): PathHandle { path.reset(); return this }
    override fun moveTo(x: Float, y: Float): PathHandle { path.moveTo(x, y); lastX = x; lastY = y; return this }
    override fun lineTo(x: Float, y: Float): PathHandle { path.lineTo(x, y); lastX = x; lastY = y; return this }
    override fun quadTo(cx: Float, cy: Float, x: Float, y: Float): PathHandle { path.quadTo(cx, cy, x, y); lastX = x; lastY = y; return this }
    override fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float): PathHandle {
        path.curveTo(c1x, c1y, c2x, c2y, x, y); lastX = x; lastY = y; return this
    }
    override fun close(): PathHandle { path.closePath(); return this }
    override fun addRoundRect(rect: Rect, corners: Corners): PathHandle {
        val r = corners.maxRadius()
        path.append(RoundRectangle2D.Float(rect.left, rect.top, rect.width, rect.height, r * 2, r * 2), false)
        return this
    }
    override fun addCircle(cx: Float, cy: Float, r: Float): PathHandle {
        path.append(Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2), false); return this
    }
    override fun arcTo(rect: Rect, startDeg: Float, sweepDeg: Float, forceMoveTo: Boolean): PathHandle {
        val arc = Arc2D.Float(rect.left, rect.top, rect.width, rect.height, -(startDeg + sweepDeg), sweepDeg, Arc2D.OPEN)
        val converted = java.awt.geom.Path2D.Float().also { it.append(arc, forceMoveTo) }
        path.append(converted, false)
        return this
    }
}

/**
 * Desktop implementation of [Painter] used by the design-preview harness:
 * the exact same screen code renders to PNG through this class.
 */
class AwtPainter(
    private val g: Graphics2D,
    override val width: Float,
    override val height: Float,
) : Painter {

    private var alphaMul = 1f

    init {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    }

    private fun effectiveColor(paint: Paint): Color {
        val a = (Argb.a(paint.color) / 255f) * paint.alpha * alphaMul
        return Color(Argb.r(paint.color), Argb.g(paint.color), Argb.b(paint.color), (a.coerceIn(0f, 1f) * 255).toInt())
    }

    private fun applyPaint(paint: Paint) {
        g.color = effectiveColor(paint)
        val native = paint.shader
        if (native is AwtShader) {
            g.paint = native.paint
            g.composite = java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER,
                (paint.alpha * alphaMul).coerceIn(0f, 1f),
            )
        } else {
            g.composite = if (paint.blend == com.mrsadhr.core.render.BlendMode.PLUS) AdditiveComposite()
            else java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (paint.alpha * alphaMul).coerceIn(0f, 1f))
        }
        g.stroke = if (paint.style == PaintStyle.STROKE) {
            BasicStroke(
                paint.strokeWidth,
                when (paint.strokeCap) {
                    StrokeCap.BUTT -> BasicStroke.CAP_BUTT
                    StrokeCap.ROUND -> BasicStroke.CAP_ROUND
                    StrokeCap.SQUARE -> BasicStroke.CAP_SQUARE
                },
                BasicStroke.JOIN_ROUND,
            )
        } else BasicStroke(1f)
    }

    override fun save() {
        push()
    }

    override fun restore() {
        val st = stack.removeLast()
        g.transform = st.transform
        g.clip = st.clip
        alphaMul = st.alpha
    }

    private data class State(
        val transform: java.awt.geom.AffineTransform,
        val clip: Shape?,
        val alpha: Float,
    )

    private val stack = ArrayDeque<State>()

    fun push() {
        stack.addLast(State(java.awt.geom.AffineTransform(g.transform), g.clip, alphaMul))
        g.setTransform(g.transform)
    }

    override fun translate(dx: Float, dy: Float) = g.translate(dx.toDouble(), dy.toDouble())

    override fun rotate(deg: Float) = g.rotate(Math.toRadians(deg.toDouble()))

    override fun rotateAround(deg: Float, pivotX: Float, pivotY: Float) {
        g.rotate(Math.toRadians(deg.toDouble()), pivotX.toDouble(), pivotY.toDouble())
    }

    override fun scale(sx: Float, sy: Float) = g.scale(sx.toDouble(), sy.toDouble())

    override fun clipRect(rect: Rect) = g.clip(java.awt.geom.Rectangle2D.Float(rect.left, rect.top, rect.width, rect.height))

    override fun clipRoundRect(rect: Rect, radius: Float) {
        g.clip(RoundRectangle2D.Float(rect.left, rect.top, rect.width, rect.height, radius * 2, radius * 2))
    }

    override fun saveLayerAlpha(alpha: Float) {
        stack.addLast(State(java.awt.geom.AffineTransform(g.transform), g.clip, alphaMul))
        alphaMul *= alpha
    }

    override fun drawRect(rect: Rect, paint: Paint) {
        applyPaint(paint)
        val shape = java.awt.geom.Rectangle2D.Float(rect.left, rect.top, rect.width, rect.height)
        if (paint.style == PaintStyle.FILL) g.fill(shape) else g.draw(shape)
    }

    override fun drawRoundRect(rect: Rect, corners: Corners, paint: Paint) {
        applyPaint(paint)
        val handle = AwtPath()
        Paths.addRoundRect(handle, rect, corners)
        if (paint.style == PaintStyle.FILL) g.fill(handle.path) else g.draw(handle.path)
    }

    override fun drawOval(rect: Rect, paint: Paint) {
        applyPaint(paint)
        val shape = Ellipse2D.Float(rect.left, rect.top, rect.width, rect.height)
        if (paint.style == PaintStyle.FILL) g.fill(shape) else g.draw(shape)
    }

    override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
        applyPaint(paint)
        val shape = Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2)
        if (paint.style == PaintStyle.FILL) g.fill(shape) else g.draw(shape)
    }

    override fun drawArc(rect: Rect, startDeg: Float, sweepDeg: Float, useCenter: Boolean, paint: Paint) {
        applyPaint(paint)
        val type = if (useCenter) Arc2D.PIE else Arc2D.OPEN
        val arc = Arc2D.Float(rect.left, rect.top, rect.width, rect.height, -(startDeg + sweepDeg), sweepDeg, type)
        if (paint.style == PaintStyle.FILL) g.fill(arc) else g.draw(arc)
    }

    override fun drawPath(path: PathHandle, paint: Paint) {
        applyPaint(paint)
        val p = (path as AwtPath).path
        if (paint.style == PaintStyle.FILL) g.fill(p) else g.draw(p)
    }

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, paint: Paint) {
        applyPaint(paint)
        g.draw(java.awt.geom.Line2D.Float(x0, y0, x1, y1))
    }

    private fun awtFont(paint: Paint): Font {
        val tf = paint.typeface as? AwtTypeface
        val base = tf?.font ?: Font("SansSerif", Font.PLAIN, 12)
        return base.deriveFont(paint.textSize)
    }

    private fun attributed(text: String, paint: Paint): AttributedString {
        val as_ = AttributedString(text)
        as_.addAttribute(TextAttribute.FONT, awtFont(paint))
        if (paint.letterSpacingEm != 0f) {
            as_.addAttribute(TextAttribute.TRACKING, paint.letterSpacingEm)
        }
        return as_
    }

    private fun layoutText(text: String, paint: Paint): java.awt.font.TextLayout {
        val frc = g.fontRenderContext
        return java.awt.font.TextLayout(attributed(text, paint).iterator, frc)
    }

    override fun drawText(text: String, x: Float, baselineY: Float, paint: Paint) {
        if (text.isEmpty()) return
        val w = measureText(text, paint)
        val dx = when (paint.textAlign) {
            TextAlign.LEFT -> x
            TextAlign.CENTER -> x - w * 0.5f
            TextAlign.RIGHT -> x - w
        }
        val tl = layoutText(text, paint)
        val col = effectiveColor(paint)
        g.paint = col
        g.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (paint.alpha * alphaMul).coerceIn(0f, 1f))
        val tx = java.awt.geom.AffineTransform.getTranslateInstance(dx.toDouble(), baselineY.toDouble())
        g.fill(tl.getOutline(tx))
    }

    override fun measureText(text: String, paint: Paint): Float {
        if (text.isEmpty()) return 0f
        return layoutText(text, paint).advance
    }

    override fun fontMetrics(paint: Paint): FontMetrics {
        val f = awtFont(paint)
        val lm = f.getLineMetrics("Hg", g.fontRenderContext)
        return FontMetrics(-lm.ascent, lm.descent)
    }

    override fun newPath(): PathHandle = AwtPath()

    private fun normalize(colors: IntArray, positions: FloatArray?): Pair<Array<Color>, FloatArray> {
        val cols = colors.map { c -> Color(Argb.r(c), Argb.g(c), Argb.b(c), Argb.a(c)) }.toTypedArray()
        val pos = positions ?: FloatArray(colors.size) { i -> if (colors.size == 1) 0f else i.toFloat() / (colors.size - 1) }
        var c2 = cols
        var p2 = pos.copyOf()
        if (p2[0] > 0f) {
            c2 = arrayOf(c2[0]) + c2
            p2 = floatArrayOf(0f) + p2
        }
        if (p2.last() < 1f) {
            c2 = c2 + c2.last()
            p2 = p2 + 1f
        }
        for (i in 1 until p2.size) if (p2[i] <= p2[i - 1]) p2[i] = p2[i - 1] + 1e-4f
        return c2 to p2
    }

    override fun linearGradient(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, positions: FloatArray?): Shader {
        val (c, p) = normalize(colors, positions)
        if (abs(x1 - x0) < 0.01f && abs(y1 - y0) < 0.01f) {
            return AwtShader(Color(c[0].red, c[0].green, c[0].blue, c[0].alpha))
        }
        return AwtShader(
            LinearGradientPaint(
                java.awt.geom.Point2D.Float(x0, y0),
                java.awt.geom.Point2D.Float(if (abs(x1 - x0) < 0.01f) x0 + 0.01f else x1, y1),
                p, c, MultipleGradientPaint.CycleMethod.NO_CYCLE,
            ),
        )
    }

    override fun radialGradient(cx: Float, cy: Float, radius: Float, colors: IntArray, positions: FloatArray?): Shader {
        val (c, p) = normalize(colors, positions)
        return AwtShader(
            RadialGradientPaint(
                java.awt.geom.Point2D.Float(cx, cy), radius.coerceAtLeast(0.01f), p, c,
                MultipleGradientPaint.CycleMethod.NO_CYCLE,
            ),
        )
    }
}
