package com.mrsadhr.android

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint as APaint
import android.graphics.Path as APath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader as AShader
import android.graphics.Typeface as ATypeface
import com.mrsadhr.core.render.BlendMode
import com.mrsadhr.core.render.Corners
import com.mrsadhr.core.render.FontMetrics
import com.mrsadhr.core.render.Paint
import com.mrsadhr.core.render.PaintStyle
import com.mrsadhr.core.render.Painter
import com.mrsadhr.core.render.PathHandle
import com.mrsadhr.core.render.Rect
import com.mrsadhr.core.render.Shader
import com.mrsadhr.core.render.StrokeCap
import com.mrsadhr.core.render.TextAlign
import com.mrsadhr.core.render.Typeface
import com.mrsadhr.ui.FontLoader
import kotlin.math.min

class AndroidTypeface(val face: ATypeface) : Typeface() {
    override val debugName: String get() = face.toString()
}

class AndroidShader(val shader: AShader) : Shader()

/** Loads the bundled OFL fonts straight out of `assets/fonts`. */
class AndroidFontLoader(private val assets: AssetManager) : FontLoader {
    private val cache = HashMap<String, AndroidTypeface>()

    override fun load(fileName: String): Typeface = cache.getOrPut(fileName) {
        AndroidTypeface(ATypeface.createFromAsset(assets, "fonts/$fileName"))
    }
}

private class AndroidPath : PathHandle {
    val path = APath()
    override fun reset(): PathHandle { path.reset(); return this }
    override fun moveTo(x: Float, y: Float) = apply { path.moveTo(x, y) }
    override fun lineTo(x: Float, y: Float) = apply { path.lineTo(x, y) }
    override fun quadTo(cx: Float, cy: Float, x: Float, y: Float) = apply { path.quadTo(cx, cy, x, y) }
    override fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) =
        apply { path.cubicTo(c1x, c1y, c2x, c2y, x, y) }
    override fun close(): PathHandle = apply { path.close() }

    override fun addRoundRect(rect: Rect, corners: Corners): PathHandle = apply {
        path.addRoundRect(
            RectF(rect.left, rect.top, rect.right, rect.bottom),
            floatArrayOf(corners.topLeft, corners.topLeft, corners.topRight, corners.topRight,
                corners.bottomRight, corners.bottomRight, corners.bottomLeft, corners.bottomLeft),
            APath.Direction.CW,
        )
    }

    override fun addCircle(cx: Float, cy: Float, r: Float): PathHandle = apply {
        path.addCircle(cx, cy, r, APath.Direction.CW)
    }

    override fun arcTo(rect: Rect, startDeg: Float, sweepDeg: Float, forceMoveTo: Boolean): PathHandle = apply {
        path.arcTo(RectF(rect.left, rect.top, rect.right, rect.bottom), startDeg, sweepDeg, forceMoveTo)
    }
}

/**
 * [Painter] on top of the framework Canvas. The whole interface is written against this single
 * implementation on device, which keeps the design system independent of any UI toolkit.
 */
class AndroidPainter(canvas: Canvas, w: Float, h: Float) : Painter {

    private var canvas: Canvas = canvas
    private var w: Float = w
    private var h: Float = h

    /** Rebinds the painter to a new canvas/frame so the render loop allocates nothing per frame. */
    fun bind(canvas: Canvas, width: Float, height: Float) {
        this.canvas = canvas
        this.w = width
        this.h = height
    }

    private val paint = APaint(APaint.ANTI_ALIAS_FLAG)
    private val textPaint = APaint(APaint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()
    private val xferClear = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    override val width: Float get() = w
    override val height: Float get() = h

    override fun save() {
        canvas.save()
    }

    override fun restore() = canvas.restore()

    override fun translate(dx: Float, dy: Float) = canvas.translate(dx, dy)

    override fun rotate(deg: Float) = canvas.rotate(deg)

    override fun rotateAround(deg: Float, pivotX: Float, pivotY: Float) =
        canvas.rotate(deg, pivotX, pivotY)

    override fun scale(sx: Float, sy: Float) = canvas.scale(sx, sy)

    override fun clipRect(rect: Rect) {
        rectF.set(rect.left, rect.top, rect.right, rect.bottom)
        canvas.clipRect(rectF)
    }

    override fun clipRoundRect(rect: Rect, radius: Float) {
        rectF.set(rect.left, rect.top, rect.right, rect.bottom)
        if (radius <= 0f) {
            canvas.clipRect(rectF)
        } else {
            canvas.clipPath(APath().apply { addRoundRect(rectF, radius, radius, APath.Direction.CW) })
        }
    }

    override fun saveLayerAlpha(alpha: Float) {
        val value = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        canvas.saveLayerAlpha(0f, 0f, w, h, value)
    }

    private fun apply(p: Paint): APaint {
        // Style
        paint.style = when (p.style) {
            PaintStyle.FILL -> APaint.Style.FILL
            PaintStyle.STROKE -> APaint.Style.STROKE
        }
        paint.strokeWidth = p.strokeWidth.coerceAtLeast(0.01f)
        paint.strokeCap = when (p.strokeCap) {
            StrokeCap.BUTT -> APaint.Cap.BUTT
            StrokeCap.ROUND -> APaint.Cap.ROUND
            StrokeCap.SQUARE -> APaint.Cap.SQUARE
        }
        paint.isAntiAlias = p.isAntiAlias
        paint.xfermode = when (p.blend) {
            BlendMode.NORMAL -> null
            BlendMode.PLUS -> PorterDuffXfermode(PorterDuff.Mode.ADD)
            BlendMode.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        }
        // Colour + shader
        val shader = (p.shader as? AndroidShader)?.shader
        paint.shader = shader
        paint.color = p.color
        paint.alpha = (Color.alpha(p.color) * p.alpha).toInt().coerceIn(0, 255)
        return paint
    }

    private fun applyText(p: Paint): APaint {
        apply(p)
        textPaint.style = APaint.Style.FILL
        textPaint.shader = (p.shader as? AndroidShader)?.shader
        textPaint.xfermode = null
        textPaint.color = p.color
        textPaint.alpha = (Color.alpha(p.color) * p.alpha).toInt().coerceIn(0, 255)
        textPaint.textSize = p.textSize
        textPaint.letterSpacing = p.letterSpacingEm
        textPaint.typeface = (p.typeface as? AndroidTypeface)?.face
        textPaint.textAlign = when (p.textAlign) {
            TextAlign.LEFT -> APaint.Align.LEFT
            TextAlign.CENTER -> APaint.Align.CENTER
            TextAlign.RIGHT -> APaint.Align.RIGHT
        }
        textPaint.isAntiAlias = p.isAntiAlias
        textPaint.isSubpixelText = true
        return textPaint
    }

    override fun drawRect(rect: Rect, paint: Paint) {
        canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, apply(paint))
    }

    override fun drawRoundRect(rect: Rect, corners: Corners, paint: Paint) {
        rectF.set(rect.left, rect.top, rect.right, rect.bottom)
        val maxRadius = min(rect.width, rect.height) * 0.5f
        val r = corners.topLeft.coerceIn(0f, maxRadius)
        canvas.drawRoundRect(rectF, r, r, apply(paint))
    }

    override fun drawOval(rect: Rect, paint: Paint) {
        rectF.set(rect.left, rect.top, rect.right, rect.bottom)
        canvas.drawOval(rectF, apply(paint))
    }

    override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
        if (radius <= 0f) return
        canvas.drawCircle(cx, cy, radius, apply(paint))
    }

    override fun drawArc(rect: Rect, startDeg: Float, sweepDeg: Float, useCenter: Boolean, paint: Paint) {
        rectF.set(rect.left, rect.top, rect.right, rect.bottom)
        canvas.drawArc(rectF, startDeg, sweepDeg, useCenter, apply(paint))
    }

    override fun drawPath(path: PathHandle, paint: Paint) {
        canvas.drawPath((path as AndroidPath).path, apply(paint))
    }

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, paint: Paint) {
        canvas.drawLine(x0, y0, x1, y1, apply(paint))
    }

    override fun drawText(text: String, x: Float, baselineY: Float, paint: Paint) {
        if (text.isEmpty()) return
        // The framework applies `letterSpacing` itself, so tracking needs no manual pass.
        canvas.drawText(text, x, baselineY, applyText(paint))
    }

    override fun measureText(text: String, paint: Paint): Float = applyText(paint).measureText(text)

    override fun fontMetrics(paint: Paint): FontMetrics {
        val fm = applyText(paint).fontMetrics
        return FontMetrics(fm.ascent, fm.descent)
    }

    override fun newPath(): PathHandle = AndroidPath()

    override fun linearGradient(
        x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, positions: FloatArray?,
    ): Shader = AndroidShader(
        LinearGradient(x0, y0, x1, y1, colors, positions, AShader.TileMode.CLAMP),
    )

    override fun radialGradient(
        cx: Float, cy: Float, radius: Float, colors: IntArray, positions: FloatArray?,
    ): Shader = AndroidShader(
        RadialGradient(cx, cy, radius.coerceAtLeast(0.01f), colors, positions, AShader.TileMode.CLAMP),
    )

    /** Clears the surface to transparent (used by the theme sweep layer). */
    fun clearAll() {
        paint.reset()
        paint.xfermode = xferClear
        canvas.drawRect(0f, 0f, w, h, paint)
    }
}
