package com.mrsadhr.ui.widget

import com.mrsadhr.core.render.Paint
import com.mrsadhr.core.render.PaintStyle
import com.mrsadhr.core.render.Painter
import com.mrsadhr.core.render.PathHandle
import com.mrsadhr.core.render.Rect
import com.mrsadhr.core.render.StrokeCap
import kotlin.math.cos
import kotlin.math.sin

/** Crisp vector icon set drawn with paths — no bitmaps, scales to any density. */
enum class Icon {
    Play, Pause, Reset, Repeat, Plus, Minus, Close, Check, ChevronRight,
    Palette, Sound, Settings, Flag, Trash, Clock, Stopwatch, Spark, Volume, Mute, Info, Globe,
    Target, Motion, Screen, Vibrate, Bell,
}

object Icons {

    fun draw(
        p: Painter,
        icon: Icon,
        box: Rect,
        color: Int,
        strokeDp: Float = 1.8f,
        density: Float = 1f,
        filled: Boolean = false,
    ) {
        val s = box.minSize()
        val cx = box.centerX
        val cy = box.centerY
        val half = s * 0.5f
        val stroke = Paint().apply {
            style = PaintStyle.STROKE
            strokeWidth = strokeDp * density
            strokeCap = StrokeCap.ROUND
            this.color = color
        }
        val fillPaint = Paint().apply {
            style = PaintStyle.FILL
            this.color = color
        }

        fun path(block: (PathHandle) -> Unit): PathHandle {
            val path = p.newPath()
            block(path)
            return path
        }

        when (icon) {
            Icon.Play -> {
                val path = path { b ->
                    b.moveTo(cx - half * 0.42f, cy - half * 0.56f)
                    b.lineTo(cx + half * 0.58f, cy)
                    b.lineTo(cx - half * 0.42f, cy + half * 0.56f)
                    b.close()
                }
                p.drawPath(path, fillPaint)
            }
            Icon.Pause -> {
                val barW = s * 0.13f
                val barH = s * 0.62f
                p.drawRoundRect(
                    Rect(cx - s * 0.23f, cy - barH / 2, cx - s * 0.23f + barW, cy + barH / 2),
                    com.mrsadhr.core.render.Corners.all(barW * 0.45f), fillPaint,
                )
                p.drawRoundRect(
                    Rect(cx + s * 0.10f, cy - barH / 2, cx + s * 0.10f + barW, cy + barH / 2),
                    com.mrsadhr.core.render.Corners.all(barW * 0.45f), fillPaint,
                )
            }
            Icon.Reset, Icon.Repeat -> {
                val r = s * 0.30f
                val arcRect = Rect.centered(cx, cy, r * 2f)
                p.drawArc(arcRect, 55f, 300f, false, stroke)
                // arrow head
                val a = Math.toRadians(55.0)
                val px = cx + (cos(a) * r).toFloat()
                val py = cy + (sin(a) * r).toFloat()
                val head = path { b ->
                    b.moveTo(px + s * 0.10f, py - s * 0.02f)
                    b.lineTo(px - s * 0.02f, py - s * 0.13f)
                    b.lineTo(px - s * 0.02f, py + s * 0.07f)
                }
                p.drawPath(head, Paint().apply { style = PaintStyle.FILL; this.color = color })
                if (icon == Icon.Repeat) p.drawCircle(cx, cy, s * 0.075f, fillPaint)
            }
            Icon.Plus, Icon.Minus -> {
                p.drawLine(cx - half * 0.5f, cy, cx + half * 0.5f, cy, stroke)
                if (icon == Icon.Plus) p.drawLine(cx, cy - half * 0.5f, cx, cy + half * 0.5f, stroke)
            }
            Icon.Close -> {
                val d = s * 0.24f
                p.drawLine(cx - d, cy - d, cx + d, cy + d, stroke)
                p.drawLine(cx + d, cy - d, cx - d, cy + d, stroke)
            }
            Icon.Check -> {
                val path = path { b ->
                    b.moveTo(cx - s * 0.26f, cy + s * 0.01f)
                    b.lineTo(cx - s * 0.06f, cy + s * 0.20f)
                    b.lineTo(cx + s * 0.27f, cy - s * 0.18f)
                }
                p.drawPath(path, stroke)
            }
            Icon.ChevronRight -> {
                val path = path { b ->
                    b.moveTo(cx - s * 0.10f, cy - s * 0.22f)
                    b.lineTo(cx + s * 0.12f, cy)
                    b.lineTo(cx - s * 0.10f, cy + s * 0.22f)
                }
                p.drawPath(path, stroke)
            }
            Icon.Palette -> {
                // three stacked layers
                for (i in 0..2) {
                    val y = cy - s * 0.20f + i * s * 0.20f
                    val path = path { b ->
                        b.moveTo(cx - s * 0.26f, y)
                        b.lineTo(cx, y - s * 0.14f)
                        b.lineTo(cx + s * 0.26f, y)
                        b.lineTo(cx, y + s * 0.14f)
                        b.close()
                    }
                    if (i == 0) p.drawPath(path, fillPaint) else p.drawPath(path, stroke)
                }
            }
            Icon.Sound, Icon.Volume -> {
                val path = path { b ->
                    b.moveTo(cx - s * 0.30f, cy - s * 0.12f)
                    b.lineTo(cx - s * 0.13f, cy - s * 0.12f)
                    b.lineTo(cx + s * 0.04f, cy - s * 0.28f)
                    b.lineTo(cx + s * 0.04f, cy + s * 0.28f)
                    b.lineTo(cx - s * 0.13f, cy + s * 0.12f)
                    b.lineTo(cx - s * 0.30f, cy + s * 0.12f)
                    b.close()
                }
                p.drawPath(path, if (filled) fillPaint else stroke)
                p.drawArc(Rect.centered(cx + s * 0.04f, cy, s * 0.36f), -55f, 110f, false, stroke)
                p.drawArc(Rect.centered(cx + s * 0.04f, cy, s * 0.58f), -50f, 100f, false, stroke)
            }
            Icon.Mute -> {
                val path = path { b ->
                    b.moveTo(cx - s * 0.30f, cy - s * 0.12f)
                    b.lineTo(cx - s * 0.13f, cy - s * 0.12f)
                    b.lineTo(cx + s * 0.04f, cy - s * 0.28f)
                    b.lineTo(cx + s * 0.04f, cy + s * 0.28f)
                    b.lineTo(cx - s * 0.13f, cy + s * 0.12f)
                    b.lineTo(cx - s * 0.30f, cy + s * 0.12f)
                    b.close()
                }
                p.drawPath(path, fillPaint)
                p.drawLine(cx + s * 0.16f, cy - s * 0.12f, cx + s * 0.34f, cy + s * 0.12f, stroke)
                p.drawLine(cx + s * 0.34f, cy - s * 0.12f, cx + s * 0.16f, cy + s * 0.12f, stroke)
            }
            Icon.Settings -> {
                val ys = floatArrayOf(cy - s * 0.20f, cy, cy + s * 0.20f)
                ys.forEachIndexed { i, y ->
                    p.drawLine(cx - s * 0.30f, y, cx + s * 0.30f, y, stroke)
                    val knobX = cx + if (i == 1) s * 0.12f else -s * 0.12f
                    p.drawCircle(knobX, y, s * 0.075f, Paint().apply { this.color = color; style = PaintStyle.FILL })
                }
            }
            Icon.Flag -> {
                val path = path { b ->
                    b.moveTo(cx - s * 0.22f, cy - s * 0.30f)
                    b.lineTo(cx - s * 0.22f, cy + s * 0.30f)
                }
                p.drawPath(path, stroke)
                val flag = path { b ->
                    b.moveTo(cx - s * 0.22f, cy - s * 0.28f)
                    b.lineTo(cx + s * 0.24f, cy - s * 0.16f)
                    b.lineTo(cx - s * 0.22f, cy - s * 0.02f)
                    b.close()
                }
                p.drawPath(flag, stroke)
            }
            Icon.Trash -> {
                p.drawLine(cx - s * 0.24f, cy - s * 0.20f, cx + s * 0.24f, cy - s * 0.20f, stroke)
                val body = path { b ->
                    b.moveTo(cx - s * 0.18f, cy - s * 0.20f)
                    b.lineTo(cx - s * 0.14f, cy + s * 0.28f)
                    b.lineTo(cx + s * 0.14f, cy + s * 0.28f)
                    b.lineTo(cx + s * 0.18f, cy - s * 0.20f)
                }
                p.drawPath(body, stroke)
                p.drawLine(cx - s * 0.08f, cy - s * 0.30f, cx + s * 0.08f, cy - s * 0.30f, stroke)
            }
            Icon.Clock -> {
                p.drawCircle(cx, cy, s * 0.30f, stroke)
                p.drawLine(cx, cy, cx, cy - s * 0.17f, stroke)
                p.drawLine(cx, cy, cx + s * 0.13f, cy + s * 0.06f, stroke)
            }
            Icon.Stopwatch -> {
                p.drawCircle(cx, cy + s * 0.04f, s * 0.28f, stroke)
                p.drawLine(cx - s * 0.10f, cy - s * 0.30f, cx + s * 0.10f, cy - s * 0.30f, stroke)
                p.drawLine(cx, cy - s * 0.28f, cx, cy - s * 0.38f, stroke)
                p.drawLine(cx, cy + s * 0.04f, cx, cy - s * 0.08f, stroke)
            }
            Icon.Spark -> {
                val path = path { b ->
                    b.moveTo(cx, cy - s * 0.34f)
                    b.quadTo(cx + s * 0.08f, cy - s * 0.06f, cx + s * 0.34f, cy)
                    b.quadTo(cx + s * 0.08f, cy + s * 0.06f, cx, cy + s * 0.34f)
                    b.quadTo(cx - s * 0.08f, cy + s * 0.06f, cx - s * 0.34f, cy)
                    b.quadTo(cx - s * 0.08f, cy - s * 0.06f, cx, cy - s * 0.34f)
                    b.close()
                }
                p.drawPath(path, if (filled) fillPaint else stroke)
            }
            Icon.Info -> {
                p.drawCircle(cx, cy, s * 0.30f, stroke)
                p.drawCircle(cx, cy - s * 0.13f, s * 0.035f, fillPaint)
                p.drawLine(cx, cy - s * 0.02f, cx, cy + s * 0.15f, stroke)
            }
            Icon.Target -> {
                p.drawCircle(cx, cy, s * 0.30f, stroke)
                p.drawCircle(cx, cy, s * 0.16f, stroke)
                p.drawCircle(cx, cy, s * 0.05f, fillPaint)
            }
            Icon.Motion -> {
                val path = path { b ->
                    b.moveTo(cx - s * 0.26f, cy - s * 0.14f)
                    b.lineTo(cx + s * 0.02f, cy - s * 0.14f)
                    b.moveTo(cx - s * 0.26f, cy + s * 0.14f)
                    b.lineTo(cx + s * 0.12f, cy + s * 0.14f)
                }
                p.drawPath(path, stroke)
                p.drawLine(cx + s * 0.14f, cy - s * 0.22f, cx + s * 0.26f, cy - s * 0.14f, stroke)
                p.drawLine(cx + s * 0.26f, cy - s * 0.14f, cx + s * 0.14f, cy - s * 0.06f, stroke)
            }
            Icon.Screen -> {
                p.drawRoundRect(
                    Rect.centered(cx, cy - s * 0.04f, s * 0.56f, s * 0.40f),
                    com.mrsadhr.core.render.Corners.all(s * 0.06f), stroke,
                )
                p.drawLine(cx - s * 0.14f, cy + s * 0.26f, cx + s * 0.14f, cy + s * 0.26f, stroke)
                p.drawLine(cx, cy + s * 0.16f, cx, cy + s * 0.26f, stroke)
            }
            Icon.Vibrate -> {
                p.drawRoundRect(
                    Rect.centered(cx, cy, s * 0.34f, s * 0.54f),
                    com.mrsadhr.core.render.Corners.all(s * 0.08f), stroke,
                )
                p.drawLine(cx - s * 0.26f, cy - s * 0.16f, cx - s * 0.26f, cy + s * 0.16f, stroke)
                p.drawLine(cx + s * 0.26f, cy - s * 0.16f, cx + s * 0.26f, cy + s * 0.16f, stroke)
            }
            Icon.Bell -> {
                val body = path { b ->
                    b.moveTo(cx - s * 0.26f, cy + s * 0.14f)
                    b.quadTo(cx - s * 0.18f, cy + s * 0.04f, cx - s * 0.18f, cy - s * 0.06f)
                    b.quadTo(cx - s * 0.18f, cy - s * 0.26f, cx, cy - s * 0.26f)
                    b.quadTo(cx + s * 0.18f, cy - s * 0.26f, cx + s * 0.18f, cy - s * 0.06f)
                    b.quadTo(cx + s * 0.18f, cy + s * 0.04f, cx + s * 0.26f, cy + s * 0.14f)
                    b.close()
                }
                p.drawPath(body, stroke)
                p.drawLine(cx - s * 0.06f, cy + s * 0.24f, cx + s * 0.06f, cy + s * 0.24f, stroke)
            }
            Icon.Globe -> {
                p.drawCircle(cx, cy, s * 0.30f, stroke)
                p.drawOval(Rect.centered(cx, cy, s * 0.26f, s * 0.60f), stroke)
                p.drawLine(cx - s * 0.30f, cy, cx + s * 0.30f, cy, stroke)
            }
        }
    }
}
