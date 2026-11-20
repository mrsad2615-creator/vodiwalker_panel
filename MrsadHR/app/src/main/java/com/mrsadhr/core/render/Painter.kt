package com.mrsadhr.core.render

/** Opaque platform font handle (Android Typeface / AWT Font). */
abstract class Typeface {
    abstract val debugName: String
}

/** Opaque platform gradient handle (Android Shader / AWT Paint). */
abstract class Shader

enum class PaintStyle { FILL, STROKE }

enum class StrokeCap { BUTT, ROUND, SQUARE }

enum class TextAlign { LEFT, CENTER, RIGHT }

enum class BlendMode { NORMAL, PLUS, MULTIPLY }

/** Mutable paint description; painters translate it into their native paint object on use. */
class Paint {
    var color: Int = Argb.rgb(255, 255, 255)
    var style: PaintStyle = PaintStyle.FILL
    var strokeWidth: Float = 1f
    var strokeCap: StrokeCap = StrokeCap.BUTT
    var shader: Shader? = null
    var blend: BlendMode = BlendMode.NORMAL
    var alpha: Float = 1f

    // text only
    var textSize: Float = 14f
    var typeface: Typeface? = null
    var textAlign: TextAlign = TextAlign.LEFT
    var letterSpacingEm: Float = 0f
    var isAntiAlias: Boolean = true

    fun copy(): Paint = Paint().also {
        it.color = color
        it.style = style
        it.strokeWidth = strokeWidth
        it.strokeCap = strokeCap
        it.shader = shader
        it.blend = blend
        it.alpha = alpha
        it.textSize = textSize
        it.typeface = typeface
        it.textAlign = textAlign
        it.letterSpacingEm = letterSpacingEm
        it.isAntiAlias = isAntiAlias
    }

    fun fill(): Paint = apply { style = PaintStyle.FILL }
    fun stroke(width: Float): Paint = apply { style = PaintStyle.STROKE; strokeWidth = width }
    fun cap(c: StrokeCap): Paint = apply { strokeCap = c }
    fun shader(s: Shader?): Paint = apply { shader = s }
    fun blend(b: BlendMode): Paint = apply { blend = b }
    fun alphaOf(a: Float): Paint = apply { alpha = a }
}

class FontMetrics(val ascent: Float, val descent: Float) {
    val height: Float get() = descent - ascent
}

/** Platform independent path builder. */
interface PathHandle {
    fun reset(): PathHandle
    fun moveTo(x: Float, y: Float): PathHandle
    fun lineTo(x: Float, y: Float): PathHandle
    fun quadTo(cx: Float, cy: Float, x: Float, y: Float): PathHandle
    fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float): PathHandle
    fun close(): PathHandle
    fun addRoundRect(rect: Rect, corners: Corners): PathHandle
    fun addCircle(cx: Float, cy: Float, r: Float): PathHandle
    fun arcTo(rect: Rect, startDeg: Float, sweepDeg: Float, forceMoveTo: Boolean): PathHandle
}

/**
 * The single drawing surface the design system is written against.
 *
 * Two implementations exist: [com.mrsadhr.platform.AndroidPainter] for the device and the desktop
 * harness painter used to render design previews and automated visual regressions in CI.
 */
interface Painter {

    val width: Float
    val height: Float

    fun save()
    fun restore()
    fun translate(dx: Float, dy: Float)
    fun rotate(deg: Float)
    fun rotateAround(deg: Float, pivotX: Float, pivotY: Float)
    fun scale(sx: Float, sy: Float)
    fun clipRect(rect: Rect)
    fun clipRoundRect(rect: Rect, radius: Float)
    fun saveLayerAlpha(alpha: Float)

    fun drawRect(rect: Rect, paint: Paint)
    fun drawRoundRect(rect: Rect, corners: Corners, paint: Paint)
    fun drawOval(rect: Rect, paint: Paint)
    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint)
    fun drawArc(rect: Rect, startDeg: Float, sweepDeg: Float, useCenter: Boolean, paint: Paint)
    fun drawPath(path: PathHandle, paint: Paint)
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, paint: Paint)

    fun drawText(text: String, x: Float, baselineY: Float, paint: Paint)
    fun measureText(text: String, paint: Paint): Float
    fun fontMetrics(paint: Paint): FontMetrics

    fun newPath(): PathHandle

    fun linearGradient(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, positions: FloatArray?): Shader
    fun radialGradient(cx: Float, cy: Float, radius: Float, colors: IntArray, positions: FloatArray?): Shader
}

inline fun Painter.translated(dx: Float, dy: Float, block: Painter.() -> Unit) {
    save(); translate(dx, dy); block(); restore()
}

inline fun Painter.clipped(rect: Rect, block: Painter.() -> Unit) {
    save(); clipRect(rect); block(); restore()
}

inline fun Painter.roundedClip(rect: Rect, radius: Float, block: Painter.() -> Unit) {
    save(); clipRoundRect(rect, radius); block(); restore()
}

inline fun Painter.withAlpha(alpha: Float, block: Painter.() -> Unit) {
    saveLayerAlpha(alpha); block(); restore()
}

/** Draws text vertically centred on [centerY] (optical centre of the x-height band). */
fun Painter.drawTextCentered(text: String, centerX: Float, centerY: Float, paint: Paint) {
    val fm = fontMetrics(paint)
    val baseline = centerY - (fm.ascent + fm.descent) * 0.5f
    val x = when (paint.textAlign) {
        TextAlign.LEFT -> centerX
        TextAlign.CENTER -> centerX
        TextAlign.RIGHT -> centerX
    }
    drawText(text, x, baseline, paint)
}
