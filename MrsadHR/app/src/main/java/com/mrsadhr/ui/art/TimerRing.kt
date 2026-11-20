package com.mrsadhr.ui.art

import com.mrsadhr.core.render.Argb
import com.mrsadhr.core.render.BlendMode
import com.mrsadhr.core.render.Paint
import com.mrsadhr.core.render.PaintStyle
import com.mrsadhr.core.render.Painter
import com.mrsadhr.core.render.Rect
import com.mrsadhr.core.render.StrokeCap
import com.mrsadhr.ui.Design
import kotlin.math.cos
import kotlin.math.sin

/**
 * The three-dimensional progress ring.
 *
 * Built from physical layers — cast shadow, machined channel with rim lights, recessed basin, light
 * carrying arc, specular reflection, travelling sheen and (for Aurora) neon bloom — so the ring
 * reads as a solid object lit from the upper left rather than a flat stroked circle.
 *
 * Only the arc that carries the remaining time has round caps; every other layer is a full circle or
 * a butt-capped arc, which keeps the silhouette clean without capsule artefacts.
 */
object TimerRing {

    class Params(
        val progress: Float,
        val timeSec: Float,
        val running: Boolean,
        val completionPulse: Float = 0f,
        val idleGlow: Float = 0f,
        val showHead: Boolean = true,
    )

    class Geometry(
        val cx: Float,
        val cy: Float,
        val outerRadius: Float,
        val thickness: Float,
        val bandRadius: Float,
        val discRadius: Float,
    )

    fun geometry(d: Design, box: Rect): Geometry {
        val outer = box.minSize() * 0.5f
        val th = d.dp(d.ringStyle.thicknessDp)
        val bandRadius = outer - d.dp(18f) - th * 0.5f
        val discRadius = bandRadius - th * 0.5f - d.dp(12f)
        return Geometry(box.centerX, box.centerY, outer, th, bandRadius, discRadius)
    }

    fun draw(p: Painter, d: Design, box: Rect, params: Params) {
        val st = d.ringStyle
        val g = geometry(d, box)
        val progress = params.progress.coerceIn(0f, 1f)
        val bandRect = Rect.centered(g.cx, g.cy, g.bandRadius * 2f)

        drawCastShadow(p, d, g)
        drawAmbientBloom(p, d, g, params)
        drawTicks(p, d, g, progress)
        drawChannel(p, d, bandRect, g)
        Surfaces.basin(p, d, g.cx, g.cy, g.discRadius)
        drawProgress(p, d, bandRect, g, progress, params)
        if (st.sweepSpeed > 0.01f) drawTravellingSheen(p, d, bandRect, g, params)
        if (params.completionPulse > 0.001f) drawCompletionPulse(p, d, g, params.completionPulse)
    }

    private fun drawCastShadow(p: Painter, d: Design, g: Geometry) {
        Surfaces.glowCircle(
            p,
            g.cx + d.dp(2f),
            g.cy + d.dp(30f),
            g.outerRadius * 1.05f,
            d.palette.shadow,
            0.55f,
        )
    }

    private fun drawAmbientBloom(p: Painter, d: Design, g: Geometry, params: Params) {
        val st = d.ringStyle
        val breathe = if (params.running && !d.reducedMotion) {
            0.5f + 0.5f * sin((params.timeSec * 0.9f).toDouble()).toFloat()
        } else 0.3f
        val strength = st.glow * (0.55f + 0.45f * breathe) + params.idleGlow
        if (strength <= 0.01f) return
        Surfaces.glowCircle(p, g.cx, g.cy, g.bandRadius + g.thickness * 1.9f, d.palette.ringGlow, 0.11f * strength)
    }

    private fun drawTicks(p: Painter, d: Design, g: Geometry, progress: Float) {
        val pal = d.palette
        val tickRadius = g.outerRadius - d.dp(1.5f)
        val elapsedAngle = progress * 360f
        val paint = Paint().apply { style = PaintStyle.STROKE; strokeCap = StrokeCap.ROUND }
        for (i in 0 until 60) {
            val angle = -90f + i * 6f
            val major = i % 5 == 0
            val len = if (major) d.dp(9f) else d.dp(4f)
            val rad = Math.toRadians(angle.toDouble())
            val dx = cos(rad).toFloat()
            val dy = sin(rad).toFloat()
            val rel = (i * 6f).toFloat()
            val lit = rel <= elapsedAngle
            paint.strokeWidth = if (major) d.dp(1.8f) else d.dp(1.1f)
            paint.color = when {
                lit && major -> Argb.scaleAlpha(pal.accentEdge, 0.70f)
                lit -> Argb.scaleAlpha(pal.tick, 1.5f)
                major -> Argb.scaleAlpha(pal.tick, 1.15f)
                else -> Argb.scaleAlpha(pal.tick, 0.68f)
            }
            p.drawLine(
                g.cx + dx * tickRadius, g.cy + dy * tickRadius,
                g.cx + dx * (tickRadius - len), g.cy + dy * (tickRadius - len),
                paint,
            )
        }
    }

    private fun drawChannel(p: Painter, d: Design, bandRect: Rect, g: Geometry) {
        val pal = d.palette
        val st = d.ringStyle

        // Machined channel body.
        p.drawArc(
            bandRect, 0f, 360f, false,
            Paint().apply {
                style = PaintStyle.STROKE
                strokeWidth = g.thickness
                shader = p.linearGradient(
                    bandRect.left, bandRect.top, bandRect.right, bandRect.bottom,
                    intArrayOf(
                        Argb.mix(pal.ringTrack, pal.ringTrackEdge, 0.75f),
                        pal.ringTrack,
                        Argb.darken(pal.ringTrack, 0.30f * st.trackDepth),
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                )
            },
        )

        // Outer rim: bright on the lit side, dark on the far side. Full circles, no caps.
        p.drawArc(
            Rect.centered(g.cx, g.cy, (g.bandRadius + g.thickness * 0.5f) * 2f), 0f, 360f, false,
            Paint().apply {
                style = PaintStyle.STROKE
                strokeWidth = d.dp(1.3f)
                shader = p.linearGradient(
                    g.cx - g.bandRadius, g.cy - g.bandRadius, g.cx + g.bandRadius, g.cy + g.bandRadius,
                    intArrayOf(
                        Argb.scaleAlpha(pal.ringBevelLight, 0.42f * st.bevel),
                        Argb.scaleAlpha(pal.ringBevelLight, 0.04f),
                        Argb.scaleAlpha(pal.ringBevelDark, 0.30f * st.bevel),
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                )
            },
        )

        // Inner rim sits in shadow at the top and catches bounce light at the bottom.
        p.drawArc(
            Rect.centered(g.cx, g.cy, (g.bandRadius - g.thickness * 0.5f) * 2f), 0f, 360f, false,
            Paint().apply {
                style = PaintStyle.STROKE
                strokeWidth = d.dp(1.6f)
                shader = p.linearGradient(
                    g.cx, g.cy - g.bandRadius, g.cx, g.cy + g.bandRadius,
                    intArrayOf(
                        Argb.scaleAlpha(pal.ringBevelDark, 0.44f * st.trackDepth),
                        Argb.scaleAlpha(pal.ringBevelDark, 0.10f),
                        Argb.scaleAlpha(pal.ringBevelLight, 0.18f),
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                )
            },
        )
    }

    private fun drawProgress(p: Painter, d: Design, bandRect: Rect, g: Geometry, progress: Float, params: Params) {
        if (progress <= 0.0004f) return
        val pal = d.palette
        val st = d.ringStyle
        val sweep = if (progress >= 0.9995f) 360f else progress * 360f

        // Bloom behind the arc (butt caps so it never bulges into a capsule).
        if (st.glow > 0.01f || st.neon > 0.01f) {
            p.drawArc(
                bandRect, -90f, sweep, false,
                Paint().apply {
                    style = PaintStyle.STROKE
                    strokeWidth = g.thickness * (1.5f + st.neon * 1.6f)
                    color = pal.ringGlow
                    alpha = 0.10f * st.glow + 0.14f * st.neon
                    blend = BlendMode.PLUS
                },
            )
            p.drawArc(
                bandRect, -90f, sweep, false,
                Paint().apply {
                    style = PaintStyle.STROKE
                    strokeWidth = g.thickness * (2.6f + st.neon * 1.4f)
                    color = pal.ringGlow
                    alpha = 0.045f * st.glow + 0.07f * st.neon
                    blend = BlendMode.PLUS
                },
            )
        }

        // Light carrying arc.
        p.drawArc(
            bandRect, -90f, sweep, false,
            Paint().apply {
                style = PaintStyle.STROKE
                strokeWidth = g.thickness
                strokeCap = StrokeCap.ROUND
                shader = p.linearGradient(
                    bandRect.left, bandRect.top, bandRect.right, bandRect.bottom,
                    intArrayOf(pal.progressStart, pal.progressMid, pal.progressEnd),
                    floatArrayOf(0f, 0.55f, 1f),
                )
            },
        )

        // Seating shadow so the arc sits inside the channel.
        p.save()
        p.clipRect(bandRect.inset(-g.thickness * 2f))
        p.drawArc(
            Rect.centered(g.cx, g.cy, (g.bandRadius - g.thickness * 0.40f) * 2f), -90f, sweep, false,
            Paint().apply {
                style = PaintStyle.STROKE
                strokeWidth = g.thickness * 0.22f
                strokeCap = StrokeCap.ROUND
                color = pal.ringBevelDark
                alpha = 0.20f * st.bevel
            },
        )
        p.restore()

        // Neon laser edge.
        if (st.neon > 0.15f) {
            p.drawArc(
                Rect.centered(g.cx, g.cy, (g.bandRadius + g.thickness * 0.44f) * 2f), -90f, sweep, false,
                Paint().apply {
                    style = PaintStyle.STROKE
                    strokeWidth = d.dp(1.3f)
                    strokeCap = StrokeCap.ROUND
                    color = pal.accentEdge
                    alpha = 0.55f * st.neon
                    blend = BlendMode.PLUS
                },
            )
        }

        // Head of the arc: a light bead at the leading edge.
        if (params.showHead && sweep < 359.4f) {
            val rad = Math.toRadians((-90f + sweep).toDouble())
            val hx = g.cx + (cos(rad) * g.bandRadius).toFloat()
            val hy = g.cy + (sin(rad) * g.bandRadius).toFloat()
            Surfaces.glowCircle(p, hx, hy, g.thickness * 1.5f, pal.ringGlow, 0.20f + 0.35f * st.neon)
            p.drawCircle(hx, hy, g.thickness * 0.16f, Paint().apply { color = pal.accentEdge; alpha = 0.95f })
        }
    }

    private fun drawTravellingSheen(p: Painter, d: Design, bandRect: Rect, g: Geometry, params: Params) {
        val st = d.ringStyle
        if (d.reducedMotion) return
        val alpha = 0.040f * st.specular + 0.018f
        if (alpha <= 0.005f) return
        val speed = if (params.running) st.sweepSpeed else st.sweepSpeed * 0.3f
        val angle = (params.timeSec * speed * 70f) % 360f
        p.drawArc(
            bandRect, angle, 44f, false,
            Paint().apply {
                style = PaintStyle.STROKE
                strokeWidth = g.thickness * 0.55f
                strokeCap = StrokeCap.BUTT
                color = d.palette.ringBevelLight
                this.alpha = alpha
                blend = BlendMode.PLUS
            },
        )
    }

    private fun drawCompletionPulse(p: Painter, d: Design, g: Geometry, pulse: Float) {
        val t = pulse.coerceIn(0f, 1f)
        Surfaces.glowCircle(p, g.cx, g.cy, g.outerRadius * (1.1f + t * 0.7f), d.palette.ringGlow, (1f - t) * 0.40f)
        for (i in 0 until 2) {
            val offset = i * 0.22f
            val local = ((t - offset) / (1f - offset)).coerceIn(0f, 1f)
            if (local <= 0f) continue
            val radius = g.bandRadius * (1.02f + local * 0.40f)
            p.drawArc(
                Rect.centered(g.cx, g.cy, radius * 2f), 0f, 360f, false,
                Paint().apply {
                    style = PaintStyle.STROKE
                    strokeWidth = d.dp(2.2f) * (1f - local * 0.55f)
                    color = d.palette.accentEdge
                    this.alpha = (1f - local) * 0.5f
                    blend = BlendMode.PLUS
                },
            )
        }
    }
}
