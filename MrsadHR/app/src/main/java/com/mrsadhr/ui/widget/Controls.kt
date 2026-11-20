package com.mrsadhr.ui.widget

import com.mrsadhr.core.render.Argb
import com.mrsadhr.core.render.BlendMode
import com.mrsadhr.core.render.Corners
import com.mrsadhr.core.render.Paint
import com.mrsadhr.core.render.PaintStyle
import com.mrsadhr.core.render.Painter
import com.mrsadhr.core.render.Rect
import com.mrsadhr.core.render.StrokeCap
import com.mrsadhr.core.render.TextAlign
import com.mrsadhr.ui.Design
import com.mrsadhr.ui.Weight
import com.mrsadhr.ui.art.Surfaces

/** The complete control vocabulary of MrsadHR. */
object Controls {

    private fun compress(rect: Rect, press: Float): Rect {
        val f = 1f - 0.028f * press
        return Rect.centered(rect.centerX, rect.centerY, rect.width * f, rect.height * f)
    }

    /**
     * Primary action: a machined accent capsule with a top light band, a contact shadow and an
     * accent bloom that lifts on press.
     */
    fun primary(
        p: Painter,
        d: Design,
        rect: Rect,
        label: String,
        press: Float,
        icon: Icon? = null,
        enabled: Boolean = true,
    ) {
        val pal = d.palette
        val r = compress(rect, press)
        val radius = r.height * 0.5f
        val accent = if (enabled) pal.accent else Argb.mix(pal.accent, pal.textTertiary, 0.5f)

        // Accent bloom around the button.
        val bloom = 0.24f + press * 0.30f
        if (d.ringStyle.neon > 0.1f || d.palette.lightness < 0.5f) {
            p.save()
            Surfaces.glowCircle(p, r.centerX, r.centerY + d.dp(4f), r.width * 0.62f, accent, bloom * 0.55f)
            p.restore()
        }

        // Contact shadow.
        p.drawRoundRect(
            r.offset(0f, d.dp(6f)), Corners.all(radius),
            Paint().apply { color = Argb.withAlpha(pal.shadow, 0.55f) },
        )

        // Body: vertical light-to-dark accent ramp.
        p.drawRoundRect(
            r, Corners.all(radius),
            Paint().apply {
                shader = p.linearGradient(
                    r.left, r.top, r.left, r.bottom,
                    intArrayOf(
                        Argb.lighten(accent, 0.18f + 0.10f * press),
                        accent,
                        Argb.mix(accent, Argb.darken(accent, 0.35f), 0.55f),
                    ),
                    floatArrayOf(0f, 0.42f, 1f),
                )
            },
        )

        // Top gloss.
        p.save()
        p.clipRoundRect(r.inset(d.dp(1f)), radius)
        p.drawRoundRect(
            Rect(r.left, r.top, r.right, r.top + r.height * 0.52f), Corners.all(radius),
            Paint().apply {
                shader = p.linearGradient(
                    r.left, r.top, r.left, r.top + r.height * 0.52f,
                    intArrayOf(
                        Argb.withAlpha(pal.accentEdge, 0.55f),
                        Argb.withAlpha(pal.accentEdge, 0.06f),
                        Argb.withAlpha(pal.accentEdge, 0f),
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                )
            },
        )
        p.restore()

        // Hairline edge.
        p.drawRoundRect(
            r.inset(d.dp(0.6f)), Corners.all(radius),
            Paint().apply {
                style = PaintStyle.STROKE
                strokeWidth = d.dp(1f)
                color = Argb.withAlpha(pal.accentEdge, 0.28f + 0.15f * press)
            },
        )

        val textColor = pal.onAccent
        val paint = d.labelPaint(13f, textColor, 0.18f, TextAlign.CENTER)
        if (icon != null) {
            val gap = d.dp(10f)
            val textW = p.measureText(label, paint)
            val iconSize = d.dp(17f)
            val totalW = textW + gap + iconSize
            var x = r.centerX - totalW * 0.5f
            Icons.draw(p, icon, Rect(x, r.centerY - iconSize * 0.5f, x + iconSize, r.centerY + iconSize * 0.5f), textColor, 2.0f, d.density)
            x += iconSize + gap
            p.drawText(label, x, r.centerY + d.dp(4.5f), paint.copy().apply { textAlign = TextAlign.LEFT })
        } else {
            p.drawText(label, r.centerX, r.centerY + d.dp(4.5f), paint)
        }
    }

    /** Secondary capsule: dark glass with a hairline and no bloom. */
    fun ghost(
        p: Painter,
        d: Design,
        rect: Rect,
        label: String,
        press: Float,
        icon: Icon? = null,
        tint: Int? = null,
        enabled: Boolean = true,
    ) {
        val pal = d.palette
        val r = compress(rect, press)
        val radius = r.height * 0.5f
        Surfaces.glass(p, d, r, radius, 0.5f + press * 0.3f, tint = tint, specularAlpha = 0.8f)

        val color = if (enabled) pal.textPrimary else pal.textTertiary
        val paint = d.labelPaint(12f, color, 0.16f, TextAlign.CENTER)
        if (icon != null) {
            val gap = d.dp(8f)
            val textW = p.measureText(label, paint)
            val iconSize = d.dp(15f)
            val totalW = textW + gap + iconSize
            var x = r.centerX - totalW * 0.5f
            Icons.draw(p, icon, Rect(x, r.centerY - iconSize * 0.5f, x + iconSize, r.centerY + iconSize * 0.5f), color, 1.8f, d.density)
            x += iconSize + gap
            p.drawText(label, x, r.centerY + d.dp(4f), paint.copy().apply { textAlign = TextAlign.LEFT })
        } else {
            p.drawText(label, r.centerX, r.centerY + d.dp(4f), paint)
        }
    }

    fun iconButton(
        p: Painter,
        d: Design,
        rect: Rect,
        icon: Icon,
        press: Float,
        active: Boolean = false,
        badge: Boolean = false,
    ) {
        val pal = d.palette
        val r = compress(rect, press)
        Surfaces.glass(p, d, r, r.height * 0.5f, 0.45f + press * 0.35f, specularAlpha = 0.9f)
        val iconSize = r.minSize() * 0.46f
        Icons.draw(
            p, icon, Rect.centered(r.centerX, r.centerY, iconSize),
            if (active) pal.accent else pal.textPrimary, 1.9f, d.density,
        )
        if (active) {
            p.drawRoundRect(
                r.inset(d.dp(0.5f)), Corners.all(r.height * 0.5f),
                Paint().apply {
                    style = PaintStyle.STROKE
                    strokeWidth = d.dp(1.1f)
                    color = Argb.withAlpha(pal.accent, 0.55f)
                },
            )
        }
        if (badge) {
            p.drawCircle(r.right - r.width * 0.22f, r.top + r.height * 0.22f, d.dp(3.2f), Paint().apply { color = pal.accent })
        }
    }

    fun chip(
        p: Painter,
        d: Design,
        rect: Rect,
        label: String,
        selected: Boolean,
        press: Float,
        icon: Icon? = null,
        dimmed: Boolean = false,
    ) {
        val pal = d.palette
        val r = compress(rect, press)
        val radius = r.height * 0.5f
        val scale = 1f + 0.02f * press

        if (selected) {
            p.save()
            Surfaces.glowCircle(p, r.centerX, r.centerY, r.width * 0.7f, pal.chipSelectedFill, 0.22f)
            p.restore()
            p.drawRoundRect(
                Rect.centered(r.centerX, r.centerY, r.width * scale, r.height * scale), Corners.all(radius),
                Paint().apply {
                    shader = p.linearGradient(
                        r.left, r.top, r.left, r.bottom,
                        intArrayOf(
                            Argb.lighten(pal.chipSelectedFill, 0.22f),
                            pal.chipSelectedFill,
                            Argb.mix(pal.chipSelectedFill, pal.chipText, 0.16f),
                        ),
                        floatArrayOf(0f, 0.5f, 1f),
                    )
                },
            )
        } else {
            Surfaces.glass(p, d, r, radius, 0.35f, tint = if (dimmed) null else pal.chipFill, specularAlpha = 0.55f)
        }

        val textColor = when {
            selected -> pal.chipSelectedText
            dimmed -> Argb.scaleAlpha(pal.chipText, 0.55f)
            else -> pal.chipText
        }
        val paint = d.labelPaint(11.5f, textColor, 0.10f, TextAlign.CENTER)
        if (icon != null) {
            val gap = d.dp(6f)
            val textW = p.measureText(label, paint)
            val iconSize = d.dp(13f)
            val totalW = textW + gap + iconSize
            var x = r.centerX - totalW * 0.5f
            Icons.draw(p, icon, Rect(x, r.centerY - iconSize * 0.5f, x + iconSize, r.centerY + iconSize * 0.5f), textColor, 1.8f, d.density)
            x += iconSize + gap
            p.drawText(label, x, r.centerY + d.dp(4f), paint.copy().apply { textAlign = TextAlign.LEFT })
        } else {
            p.drawText(label, r.centerX, r.centerY + d.dp(4f), paint)
        }
    }

    /** Segmented glass switch with a sliding selection pill. */
    fun segmented(
        p: Painter,
        d: Design,
        rect: Rect,
        labels: List<String>,
        selectedIndex: Int,
        selection: Float,
        press: Float,
    ) {
        val pal = d.palette
        Surfaces.glass(p, d, rect, rect.height * 0.5f, 0.55f, specularAlpha = 0.7f)
        val count = labels.size
        val inset = d.dp(3f)
        val slotW = (rect.width - inset * 2) / count
        val pillLeft = rect.left + inset + slotW * selection
        val pill = Rect(pillLeft, rect.top + inset, pillLeft + slotW, rect.bottom - inset)
        // Selection pill
        p.drawRoundRect(
            pill, Corners.all(pill.height * 0.5f),
            Paint().apply {
                shader = p.linearGradient(
                    pill.left, pill.top, pill.left, pill.bottom,
                    intArrayOf(Argb.lighten(pal.accent, 0.28f), pal.accent, Argb.mix(pal.accent, pal.accentSoft, 0.35f)),
                    floatArrayOf(0f, 0.45f, 1f),
                )
            },
        )
        p.drawRoundRect(
            pill.inset(d.dp(0.5f)), Corners.all(pill.height * 0.5f),
            Paint().apply {
                style = PaintStyle.STROKE
                strokeWidth = d.dp(1f)
                color = Argb.withAlpha(pal.accentEdge, 0.32f)
            },
        )
        labels.forEachIndexed { index, label ->
            val cx = rect.left + inset + slotW * (index + 0.5f)
            val active = index == selectedIndex
            val paint = d.labelPaint(11.5f, if (active) pal.onAccent else pal.textSecondary, 0.08f, TextAlign.CENTER)
            p.drawText(label, cx, rect.centerY + d.dp(4f), paint.copy().apply { alpha = if (active) 1f else 0.85f })
        }
    }

    fun toggle(p: Painter, d: Design, rect: Rect, checked: Boolean, t: Float) {
        val pal = d.palette
        val radius = rect.height * 0.5f
        val offColor = Argb.mix(pal.chipFill, pal.textTertiary, 0.18f)
        val onColor = pal.accent
        val trackColor = Argb.mix(offColor, onColor, t)
        p.drawRoundRect(
            rect, Corners.all(radius),
            Paint().apply {
                color = trackColor
                if (t > 0.02f) {
                    shader = p.linearGradient(
                        rect.left, rect.top, rect.left, rect.bottom,
                        intArrayOf(Argb.lighten(trackColor, 0.14f), trackColor, Argb.darken(trackColor, 0.16f)),
                        null,
                    )
                }
            },
        )
        p.drawRoundRect(
            rect.inset(d.dp(0.5f)), Corners.all(radius),
            Paint().apply {
                style = PaintStyle.STROKE
                strokeWidth = d.dp(1f)
                color = Argb.withAlpha(pal.glassBorder, 0.7f)
            },
        )
        val knobR = rect.height * 0.5f - d.dp(3f)
        val knobX = rect.left + rect.height * 0.5f + (rect.width - rect.height) * t
        p.drawCircle(knobX, rect.centerY + d.dp(0.6f), knobR, Paint().apply { color = Argb.withAlpha(pal.shadow, 0.45f) })
        p.drawCircle(
            knobX, rect.centerY, knobR,
            Paint().apply {
                shader = p.radialGradient(
                    knobX - knobR * 0.3f, rect.centerY - knobR * 0.4f, knobR * 1.6f,
                    intArrayOf(0xFFFFFFFF.toInt(), Argb.hex(0xE6EAF0), Argb.hex(0xC2C9D4)),
                    floatArrayOf(0f, 0.55f, 1f),
                )
            },
        )
    }

    fun slider(p: Painter, d: Design, rect: Rect, value: Float, active: Boolean) {
        val pal = d.palette
        val trackH = d.dp(3.5f)
        val cy = rect.centerY
        val radius = rect.height * 0.5f
        val track = Rect(rect.left + radius, cy - trackH * 0.5f, rect.right - radius, cy + trackH * 0.5f)
        p.drawRoundRect(track, Corners.all(trackH * 0.5f), Paint().apply { color = Argb.mix(pal.chipFill, pal.textTertiary, 0.22f) })
        val filled = Rect(track.left, track.top, track.left + track.width * value.coerceIn(0f, 1f), track.bottom)
        if (filled.width > 0f) {
            p.drawRoundRect(
                filled, Corners.all(trackH * 0.5f),
                Paint().apply {
                    shader = p.linearGradient(filled.left, 0f, filled.right, 0f, intArrayOf(pal.accentSoft, pal.accent), null)
                },
            )
        }
        val knobX = track.left + track.width * value.coerceIn(0f, 1f)
        val knobR = d.dp(if (active) 9f else 8f)
        if (active) Surfaces.glowCircle(p, knobX, cy, knobR * 2.6f, pal.accent, 0.35f)
        p.drawCircle(knobX, cy + d.dp(1f), knobR, Paint().apply { color = Argb.withAlpha(pal.shadow, 0.5f) })
        p.drawCircle(
            knobX, cy, knobR,
            Paint().apply {
                shader = p.radialGradient(
                    knobX - knobR * 0.35f, cy - knobR * 0.45f, knobR * 1.7f,
                    intArrayOf(0xFFFFFFFF.toInt(), pal.accent, Argb.darken(pal.accent, 0.12f)),
                    floatArrayOf(0f, 0.6f, 1f),
                )
            },
        )
    }

    /** Vertical stepper used by the hour / minute / second editor. */
    fun stepper(
        p: Painter,
        d: Design,
        rect: Rect,
        value: String,
        caption: String,
        selected: Boolean,
        pressUp: Float,
        pressDown: Float,
        enabled: Boolean,
    ) {
        val pal = d.palette
        val radius = d.dp(20f)
        if (selected) {
            p.save()
            Surfaces.glowCircle(p, rect.centerX, rect.centerY, rect.width * 0.8f, pal.accent, 0.10f)
            p.restore()
        }
        Surfaces.glass(
            p, d, rect, radius, if (selected) 0.7f else 0.35f,
            tint = if (selected) Argb.withAlpha(pal.accent, 0.10f) else null,
            specularAlpha = if (selected) 1f else 0.6f,
        )
        if (selected) {
            p.drawRoundRect(
                rect.inset(d.dp(0.6f)), Corners.all(radius),
                Paint().apply {
                    style = PaintStyle.STROKE
                    strokeWidth = d.dp(1.1f)
                    color = Argb.withAlpha(pal.accent, 0.45f)
                },
            )
        }
        val arrowY = d.dp(18f)
        val upRect = Rect.centered(rect.centerX, rect.top + arrowY, d.dp(28f))
        val downRect = Rect.centered(rect.centerX, rect.bottom - arrowY, d.dp(28f))
        Icons.draw(p, Icon.Plus, upRect, if (enabled) pal.textSecondary else pal.textTertiary, 2.4f, d.density)
        Icons.draw(p, Icon.Minus, downRect, if (enabled) pal.textSecondary else pal.textTertiary, 2.4f, d.density)
        if (pressUp > 0.01f) {
            p.drawCircle(upRect.centerX, upRect.centerY, d.dp(15f) * (0.9f + pressUp * 0.1f), Paint().apply { color = Argb.withAlpha(pal.accent, 0.16f) })
        }
        if (pressDown > 0.01f) {
            p.drawCircle(downRect.centerX, downRect.centerY, d.dp(15f) * (0.9f + pressDown * 0.1f), Paint().apply { color = Argb.withAlpha(pal.accent, 0.16f) })
        }
        val valuePaint = d.timePaint(36f, if (enabled) pal.textPrimary else pal.textTertiary, Weight.Medium)
        p.drawText(value, rect.centerX, rect.centerY + d.dp(10f), valuePaint.copy().apply { textAlign = TextAlign.CENTER })
        val capPaint = d.labelPaint(8f, Argb.scaleAlpha(pal.textSecondary, 0.85f), 0.22f, TextAlign.CENTER)
        p.drawText(caption, rect.centerX, rect.bottom - d.dp(5f), capPaint)
    }

    /** Grouped settings row inside a glass card. */
    fun settingRow(
        p: Painter,
        d: Design,
        rect: Rect,
        title: String,
        subtitle: String?,
        icon: Icon?,
        checked: Boolean? = null,
        toggleT: Float = 0f,
        chevron: Boolean = false,
        press: Float = 0f,
    ) {
        val pal = d.palette
        if (press > 0.01f) {
            p.drawRoundRect(rect.inset(d.dp(2f)), Corners.all(d.dp(16f)), Paint().apply { color = Argb.withAlpha(pal.accent, 0.05f * press) })
        }
        var textLeft = rect.left
        if (icon != null) {
            val iconSize = d.dp(18f)
            Icons.draw(p, icon, Rect(rect.left, rect.centerY - iconSize * 0.5f, rect.left + iconSize, rect.centerY + iconSize * 0.5f), pal.textSecondary, 1.8f, d.density)
            textLeft = rect.left + iconSize + d.dp(14f)
        }
        val titlePaint = d.titlePaint(13.5f, pal.textPrimary)
        val subtitlePaint = d.bodyPaint(11f, pal.textTertiary)
        if (subtitle == null) {
            p.drawText(title, textLeft, rect.centerY + d.dp(4.5f), titlePaint)
        } else {
            p.drawText(title, textLeft, rect.centerY - d.dp(3f), titlePaint)
            p.drawText(subtitle, textLeft, rect.centerY + d.dp(13f), subtitlePaint)
        }
        if (checked != null) {
            val toggleW = d.dp(40f)
            val toggleH = d.dp(23f)
            toggle(
                p, d,
                Rect(rect.right - toggleW, rect.centerY - toggleH * 0.5f, rect.right, rect.centerY + toggleH * 0.5f),
                checked, toggleT,
            )
        }
        if (chevron) {
            val size = d.dp(14f)
            Icons.draw(p, Icon.ChevronRight, Rect(rect.right - size, rect.centerY - size * 0.5f, rect.right, rect.centerY + size * 0.5f), pal.textTertiary, 1.8f, d.density)
        }
    }

    fun divider(p: Painter, d: Design, left: Float, right: Float, y: Float) {
        val pal = d.palette
        p.drawLine(
            left, y, right, y,
            Paint().apply {
                style = PaintStyle.STROKE
                strokeWidth = d.dp(0.8f)
                strokeCap = StrokeCap.BUTT
                shader = p.linearGradient(
                    left, y, right, y,
                    intArrayOf(Argb.withAlpha(pal.glassBorder, 0f), Argb.withAlpha(pal.glassBorder, 0.9f), Argb.withAlpha(pal.glassBorder, 0f)),
                    null,
                )
            },
        )
    }

    /** Section caption: wide tracking, upper case, low contrast. */
    fun sectionCaption(p: Painter, d: Design, text: String, x: Float, y: Float, align: TextAlign = TextAlign.LEFT) {
        p.drawText(text.uppercase(), x, y, d.labelPaint(8.5f, Argb.scaleAlpha(d.palette.textSecondary, 0.85f), 0.24f, align))
    }

    fun glowDot(p: Painter, d: Design, cx: Float, cy: Float, color: Int, radius: Float, strength: Float) {
        Surfaces.glowCircle(p, cx, cy, radius * 3f, color, 0.4f * strength)
        p.drawCircle(cx, cy, radius, Paint().apply { this.color = color; blend = BlendMode.NORMAL })
    }
}
