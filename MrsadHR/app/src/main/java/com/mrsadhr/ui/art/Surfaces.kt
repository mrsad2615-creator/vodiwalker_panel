package com.mrsadhr.ui.art

import com.mrsadhr.core.render.Argb
import com.mrsadhr.core.render.Corners
import com.mrsadhr.core.render.Paint
import com.mrsadhr.core.render.Painter
import com.mrsadhr.core.render.Rect
import com.mrsadhr.ui.Design

/**
 * Low level "materials" library: every surface in the app is built from these primitives so the
 * lighting model stays consistent across screens and themes.
 */
object Surfaces {

    fun fill(color: Int): Paint = Paint().apply { this.color = color }

    fun softGlow(p: Painter, cx: Float, cy: Float, radius: Float, color: Int, alpha: Float): Paint {
        if (alpha <= 0.001f || radius <= 0f) return fill(Argb.withAlpha(color, 0f))
        val paint = Paint()
        paint.color = Argb.withAlpha(color, 1f)
        paint.alpha = alpha.coerceIn(0f, 1f)
        paint.shader = p.radialGradient(
            cx, cy, radius,
            intArrayOf(Argb.withAlphaInt(color, (alpha * 255).toInt().coerceIn(0, 255)), Argb.withAlphaInt(color, (alpha * 120).toInt().coerceIn(0, 255)), Argb.withAlphaInt(color, 0)),
            floatArrayOf(0f, 0.45f, 1f),
        )
        return paint
    }

    fun glowCircle(p: Painter, cx: Float, cy: Float, radius: Float, color: Int, alpha: Float) {
        if (alpha <= 0.001f || radius <= 0f) return
        val paint = Paint()
        paint.color = 0
        paint.shader = p.radialGradient(
            cx, cy, radius,
            intArrayOf(Argb.withAlphaInt(color, (alpha * 255).toInt().coerceIn(0, 255)), Argb.withAlphaInt(color, (alpha * 90).toInt().coerceIn(0, 255)), Argb.withAlphaInt(color, 0)),
            floatArrayOf(0f, 0.42f, 1f),
        )
        p.drawCircle(cx, cy, radius, paint)
    }

    /**
     * Liquid glass panel: multi layer shadow, gradient body, hairline border, top specular streak
     * and a bottom rim light. Drawn with at most 7 cheap primitives.
     */
    fun glass(
        p: Painter,
        d: Design,
        rect: Rect,
        radius: Float,
        elevation: Float = 1f,
        tint: Int? = null,
        specularAlpha: Float = 1f,
    ) {
        val pal = d.palette
        val corners = Corners.all(radius)

        // 1. Ambient + key shadow (two soft passes, no platform blur so it stays 60fps cheap).
        if (elevation > 0f) {
            val shadow = pal.shadow
            val spread = d.dp(10f) * elevation
            p.drawRoundRect(
                rect.offset(0f, d.dp(6f) * elevation).inset(-spread * 0.25f),
                corners,
                fill(Argb.scaleAlpha(shadow, 0.42f)),
            )
            p.drawRoundRect(
                rect.offset(0f, d.dp(2f) * elevation),
                corners,
                fill(Argb.scaleAlpha(shadow, 0.5f)),
            )
        }

        // 2. Body gradient.
        val body = Paint()
        val top = tint ?: pal.glassTop
        val bottom = tint?.let { Argb.scaleAlpha(it, 0.55f) } ?: pal.glassBottom
        body.shader = p.linearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            intArrayOf(top, Argb.mix(top, bottom, 0.55f), bottom),
            floatArrayOf(0f, 0.55f, 1f),
        )
        p.drawRoundRect(rect, corners, body)

        // 3. Hairline border, brighter on the lit side.
        val border = Paint()
        border.style = com.mrsadhr.core.render.PaintStyle.STROKE
        border.strokeWidth = d.dp(1f)
        border.shader = p.linearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(Argb.scaleAlpha(pal.glassBorder, 1.35f), pal.glassBorder, Argb.scaleAlpha(pal.glassBorder, 0.55f)),
            floatArrayOf(0f, 0.5f, 1f),
        )
        p.drawRoundRect(rect.inset(d.dp(0.5f)), corners, border)

        // 4. Top specular streak — a thin band that sells the glass.
        if (specularAlpha > 0.01f) {
            val bandHeight = (rect.height * 0.42f).coerceAtMost(d.dp(96f))
            val band = Rect(rect.left + radius * 0.5f, rect.top + d.dp(0.8f), rect.right - radius * 0.5f, rect.top + bandHeight)
            val spec = Paint()
            spec.shader = p.linearGradient(
                band.left, band.top, band.left, band.bottom,
                intArrayOf(
                    Argb.scaleAlpha(pal.glassSpecular, 0.55f * specularAlpha),
                    Argb.scaleAlpha(pal.glassSpecular, 0.12f * specularAlpha),
                    Argb.withAlpha(pal.glassSpecular, 0f),
                ),
                floatArrayOf(0f, 0.45f, 1f),
            )
            p.save()
            p.clipRoundRect(rect.inset(d.dp(1.2f)), radius - d.dp(1.2f))
            p.drawRoundRect(band, Corners.all(bandHeight * 0.5f), spec)
            p.restore()
        }

        // 5. Bottom rim light for depth.
        val rim = Paint()
        rim.shader = p.linearGradient(
            rect.left, rect.bottom - d.dp(2f), rect.left, rect.bottom,
            intArrayOf(Argb.withAlpha(pal.glassBorder, 0f), Argb.scaleAlpha(pal.glassBorder, 0.75f)),
            floatArrayOf(0f, 1f),
        )
        p.drawRect(Rect(rect.left + radius * 0.4f, rect.bottom - d.dp(1.6f), rect.right - radius * 0.4f, rect.bottom - d.dp(0.4f)), rim)
    }

    /** Inner well: a recessed circular basin that seats the timer digits. */
    fun basin(p: Painter, d: Design, centerX: Float, centerY: Float, radius: Float) {
        val pal = d.palette
        val body = Paint()
        body.shader = p.radialGradient(
            centerX - radius * 0.22f, centerY - radius * 0.3f, radius * 1.45f,
            intArrayOf(pal.ringDiscTop, Argb.mix(pal.ringDiscTop, pal.ringDiscBottom, 0.5f), pal.ringDiscBottom),
            floatArrayOf(0f, 0.55f, 1f),
        )
        p.drawCircle(centerX, centerY, radius, body)

        // Recess shadow along the top inner edge.
        p.drawArc(
            Rect.centered(centerX, centerY, radius * 2f - d.dp(1f)),
            185f, 170f, false,
            Paint().apply {
                style = com.mrsadhr.core.render.PaintStyle.STROKE
                strokeWidth = d.dp(9f)
                shader = p.linearGradient(
                    centerX, centerY - radius, centerX, centerY,
                    intArrayOf(Argb.scaleAlpha(pal.ringBevelDark, 0.34f), Argb.withAlpha(pal.ringBevelDark, 0f)),
                    null,
                )
            },
        )

        // Light pooling at the bottom of the well.
        p.drawArc(
            Rect.centered(centerX, centerY, radius * 2f - d.dp(1f)),
            8f, 150f, false,
            Paint().apply {
                style = com.mrsadhr.core.render.PaintStyle.STROKE
                strokeWidth = d.dp(6f)
                shader = p.linearGradient(
                    centerX, centerY + radius, centerX, centerY,
                    intArrayOf(Argb.scaleAlpha(pal.ringBevelLight, 0.16f), Argb.withAlpha(pal.ringBevelLight, 0f)),
                    null,
                )
            },
        )
    }

    /** Vignette that pushes the eye toward the centre of the screen. */
    fun vignette(p: Painter, color: Int, strength: Float) {
        val paint = Paint()
        paint.shader = p.radialGradient(
            p.width * 0.5f, p.height * 0.46f, maxOf(p.width, p.height) * 0.72f,
            intArrayOf(Argb.withAlpha(color, 0f), Argb.withAlpha(color, 0f), Argb.withAlpha(color, strength)),
            floatArrayOf(0f, 0.55f, 1f),
        )
        p.drawRect(Rect(0f, 0f, p.width, p.height), paint)
    }
}
