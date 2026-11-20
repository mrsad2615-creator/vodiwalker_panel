package com.mrsadhr.ui.art

import com.mrsadhr.core.render.Argb
import com.mrsadhr.core.render.Paint
import com.mrsadhr.core.render.Painter
import com.mrsadhr.core.render.Rect
import com.mrsadhr.ui.Design
import kotlin.math.cos
import kotlin.math.sin

/**
 * Background system.
 *
 * Three independent compositions, each built exclusively from gradients drawn edge-to-edge so the
 * frame cost stays flat and no hard gradient seams can ever appear on screen.
 */
object Backgrounds {

    fun draw(p: Painter, d: Design, timeMs: Long, alpha: Float = 1f) {
        if (alpha <= 0.002f) return
        val t = timeMs / 1000f
        p.save()
        if (alpha < 0.999f) p.saveLayerAlpha(alpha)
        if (d.palette.lightness > 0.5f) pure(p, d, t) else {
            if (isAurora(d)) aurora(p, d, t) else obsidian(p, d, t)
        }
        if (alpha < 0.999f) p.restore()
        p.restore()
    }

    private fun isAurora(d: Design): Boolean =
        Argb.b(d.palette.ambientA) > 200 && Argb.g(d.palette.ambientA) > 150

    /**
     * A soft diagonal light band produced by one full-canvas linear gradient. Because the gradient
     * runs from beyond one screen corner to beyond the other, the band has no visible edges.
     */
    private fun streak(p: Painter, color: Int, peak: Float, angleDeg: Float, offset: Float) {
        if (peak <= 0.0005f) return
        val w = p.width
        val h = p.height
        val rad = Math.toRadians(angleDeg.toDouble())
        val nx = -sin(rad).toFloat()
        val ny = cos(rad).toFloat()
        val span = kotlin.math.sqrt(w * w + h * h) * 0.62f
        val cx = w * 0.5f + nx * offset * h
        val cy = h * 0.42f + ny * offset * h
        p.drawRect(
            Rect(-w * 0.1f, -h * 0.1f, w * 1.1f, h * 1.1f),
            Paint().apply {
                shader = p.linearGradient(
                    cx - nx * span, cy - ny * span, cx + nx * span, cy + ny * span,
                    intArrayOf(
                        Argb.withAlpha(color, 0f),
                        Argb.withAlpha(color, peak * 0.35f),
                        Argb.withAlpha(color, peak),
                        Argb.withAlpha(color, peak * 0.30f),
                        Argb.withAlpha(color, 0f),
                    ),
                    floatArrayOf(0f, 0.30f, 0.46f, 0.66f, 1f),
                )
            },
        )
    }

    // ------------------------------------------------------------------ OBSIDIAN

    private fun obsidian(p: Painter, d: Design, t: Float) {
        val pal = d.palette
        val w = p.width
        val h = p.height

        p.drawRect(
            Rect(0f, 0f, w, h),
            Paint().apply {
                shader = p.linearGradient(
                    w * 0.2f, 0f, w * 0.8f, h,
                    intArrayOf(pal.bgTop, pal.bgMid, pal.bgBottom),
                    floatArrayOf(0f, 0.48f, 1f),
                )
            },
        )

        Surfaces.glowCircle(p, w * 0.02f, h * -0.04f, w * 1.15f, pal.ambientA, 0.10f)
        Surfaces.glowCircle(p, w * 1.02f, h * 0.90f, w * 1.0f, pal.ambientB, 0.18f)
        Surfaces.glowCircle(p, w * 0.50f, h * 0.66f, w * 0.85f, pal.ambientC, 0.05f)

        // Polished graphite sheen.
        streak(p, 0xFFFFFFFF.toInt(), 0.030f, -68f, -0.12f)
        streak(p, 0xFFFFFFFF.toInt(), 0.016f, -68f, 0.22f)

        // Chromatic bloom low on the screen: the "lit table" the ring floats above.
        Surfaces.glowCircle(p, w * 0.5f, h * 1.04f, w * 0.9f, pal.ambientA, 0.06f)

        Surfaces.vignette(p, pal.scrim, 0.62f)
    }

    // ---------------------------------------------------------------------- PURE

    private fun pure(p: Painter, d: Design, t: Float) {
        val pal = d.palette
        val w = p.width
        val h = p.height

        p.drawRect(
            Rect(0f, 0f, w, h),
            Paint().apply {
                shader = p.linearGradient(
                    w * 0.15f, 0f, w * 0.85f, h,
                    intArrayOf(pal.bgTop, pal.bgMid, pal.bgBottom),
                    floatArrayOf(0f, 0.46f, 1f),
                )
            },
        )

        Surfaces.glowCircle(p, w * 0.86f, h * 0.02f, w * 1.0f, 0xFFFFFFFF.toInt(), 0.85f)
        Surfaces.glowCircle(p, w * 0.02f, h * 0.40f, w * 0.85f, pal.ambientC, 0.55f)
        Surfaces.glowCircle(p, w * 0.25f, h * 1.02f, w * 0.95f, pal.ambientB, 0.30f)

        // Daylight bounce.
        streak(p, 0xFFFFFFFF.toInt(), 0.50f, 22f, 0.10f)

        // Grounded floor.
        p.drawRect(
            Rect(0f, h * 0.70f, w, h),
            Paint().apply {
                shader = p.linearGradient(
                    0f, h * 0.70f, 0f, h,
                    intArrayOf(
                        Argb.withAlpha(Argb.darken(pal.bgBottom, 0.05f), 0f),
                        Argb.withAlpha(Argb.darken(pal.bgBottom, 0.10f), 0.55f),
                    ),
                    null,
                )
            },
        )

        Surfaces.vignette(p, Argb.hex(0x93A3B7), 0.22f)
    }

    // -------------------------------------------------------------------- AURORA

    private fun aurora(p: Painter, d: Design, t: Float) {
        val pal = d.palette
        val w = p.width
        val h = p.height
        val motion = if (d.reducedMotion) 0f else 1f

        p.drawRect(
            Rect(0f, 0f, w, h),
            Paint().apply {
                shader = p.linearGradient(
                    0f, 0f, w * 0.35f, h,
                    intArrayOf(pal.bgTop, pal.bgMid, pal.bgBottom),
                    floatArrayOf(0f, 0.42f, 1f),
                )
            },
        )

        // Drifting plasma fields.
        val drift1 = sin(t * 0.19f) * w * 0.045f * motion
        val drift2 = cos(t * 0.15f) * w * 0.055f * motion
        val drift3 = sin(t * 0.11f + 1.7f) * h * 0.025f * motion
        Surfaces.glowCircle(p, w * 0.12f + drift1, h * 0.10f + drift3, w * 0.78f, pal.ambientA, 0.30f)
        Surfaces.glowCircle(p, w * 0.92f + drift2, h * 0.34f, w * 0.70f, pal.ambientB, 0.34f)

        // Aurora curtain: a wave of overlapping soft fields (no polygon edges).
        val segments = 7
        for (i in 0..segments) {
            val f = i / segments.toFloat()
            val wave = sin(f * 3.4f + t * 0.35f * motion) * h * 0.045f
            val cx = lerpF(-w * 0.15f, w * 1.15f, f) + drift1 * (0.4f + f * 0.5f)
            val cy = h * 0.13f + wave + drift3 * 0.5f
            val color = Argb.mix(pal.ambientA, pal.ambientB, (f * 1.15f).coerceIn(0f, 1f))
            Surfaces.glowCircle(p, cx, cy, w * 0.34f, color, 0.085f)
        }

        // Horizon haze behind the ring.
        Surfaces.glowCircle(p, w * 0.5f, h * 0.52f, w * 0.95f, pal.ambientC, 0.055f)
        Surfaces.glowCircle(p, w * 0.5f, h * 1.02f, w * 1.0f, pal.ambientA, 0.06f)

        stars(p, d, t, motion)

        Surfaces.vignette(p, pal.scrim, 0.58f)
    }

    private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun stars(p: Painter, d: Design, t: Float, motion: Float) {
        val count = 78
        val paint = Paint()
        for (i in 0 until count) {
            val seed = i * 2654435761L
            val rx = ((seed ushr 8) and 0xFFFF).toFloat() / 65535f
            val ry = ((seed ushr 24) and 0xFFFF).toFloat() / 65535f
            val sizeSeed = ((seed ushr 40) and 0xFF).toFloat() / 255f
            val x = rx * p.width
            val y = ry * p.height * 0.88f
            val twinkle = if (motion > 0f) (0.5f + 0.5f * sin(t * (0.4f + sizeSeed * 0.8f) + i * 1.7f)).coerceIn(0f, 1f) else 0.7f
            val alpha = (0.08f + 0.38f * sizeSeed) * twinkle
            paint.color = Argb.withAlpha(d.palette.textPrimary, alpha)
            p.drawCircle(x, y, d.dp(0.6f) + sizeSeed * d.dp(1.0f), paint)
        }
    }
}
