package com.mrsadhr.ui.screen

import com.mrsadhr.app.Action
import com.mrsadhr.core.model.SoundCategory
import com.mrsadhr.core.motion.Animated
import com.mrsadhr.core.motion.Easing
import com.mrsadhr.core.motion.Motion
import com.mrsadhr.core.render.Argb
import com.mrsadhr.core.render.Corners
import com.mrsadhr.core.render.Paint
import com.mrsadhr.core.render.PaintStyle
import com.mrsadhr.core.render.Painter
import com.mrsadhr.core.render.Rect
import com.mrsadhr.core.render.TextAlign
import com.mrsadhr.sounds.SoundCatalog
import com.mrsadhr.sounds.SoundDef
import com.mrsadhr.ui.Design
import com.mrsadhr.ui.Weight
import com.mrsadhr.ui.Screen
import com.mrsadhr.ui.Stage
import com.mrsadhr.ui.art.Surfaces
import com.mrsadhr.ui.widget.Controls
import com.mrsadhr.ui.widget.Icon
import com.mrsadhr.ui.widget.Icons
import kotlin.math.abs

/** Sounds centre: category filter, original previews, selection and volume. */
class SoundsScreen(stage: Stage) : Screen(stage) {

    private var category: SoundCategory? = null
    private val catT = Animated(0f, Motion.CHIP, Easing.Emphasize)
    private val rowRects = ArrayList<Pair<SoundDef, Rect>>()
    private val categoryRects = ArrayList<Pair<SoundCategory?, Rect>>()
    private var scroll = 0f
    private var scrollMax = 0f
    private var lastY = Float.NaN
    private var sliderRect: Rect = Rect.Zero
    private var volumeDragActive = false

    override fun update(now: Long) {
        catT.update(now)
        hits.pressed.values.forEach { it.update(now) }
    }

    override fun layout(p: Painter, d: Design, now: Long) {
        hits.clear()
        hits.add(
            "sound-scroll",
            Rect(0f, stage.safeTop + d.dp(126f), p.width, p.height),
            onDrag = { _, py -> handleScroll(py) },
        )
        rowRects.clear()
        categoryRects.clear()
        val w = p.width
        val lang = stage.lang
        val persian = lang.persian

        var y = stage.safeTop + d.dp(150f) - scroll

        // Volume card.
        val volumeCard = Rect(d.dp(20f), y, w - d.dp(20f), y + d.dp(78f))
        sliderRect = Rect(volumeCard.left + d.dp(16f), volumeCard.bottom - d.dp(34f), volumeCard.right - d.dp(16f), volumeCard.bottom - d.dp(6f))
        hits.add("volume", sliderRect, onDrag = { x, _ ->
            val v = ((x - sliderRect.left) / sliderRect.width).coerceIn(0f, 1f)
            volumeDragActive = true
            stage.controller.dispatch(Action.SetVolume(v))
        }, onTap = {})
        y += d.dp(78f) + d.dp(18f)

        // Category chips.
        val cats: List<SoundCategory?> = listOf(null) + SoundCatalog.categories
        var x = d.dp(20f)
        val chipH = d.dp(30f)
        val chipPaint = d.labelPaint(11f, d.palette.chipText, 0.08f)
        for (cat in cats) {
            val label = if (cat == null) (if (persian) "همه" else "All") else SoundCatalog.categoryLabel(cat, persian)
            val chipW = p.measureText(label, chipPaint) + d.dp(28f)
            val rect = Rect(x, y, x + chipW, y + chipH)
            categoryRects.add(cat to rect)
            hits.add("cat-${cat?.key ?: "all"}", rect) {
                category = cat
            }
            x += chipW + d.dp(8f)
        }
        y += chipH + d.dp(16f)

        // Sound rows.
        val rowH = d.dp(66f)
        val gap = d.dp(10f)
        val visible = SoundCatalog.all.filter { category == null || it.category == category }
        for (sound in visible) {
            val rect = Rect(d.dp(20f), y, w - d.dp(20f), y + rowH)
            rowRects.add(sound to rect)
            val previewId = "snd-preview-${sound.id}"
            val selectId = "snd-select-${sound.id}"
            hits.add(selectId, Rect(rect.left, rect.top, rect.right - d.dp(60f), rect.bottom), onDrag = { _, py -> handleScroll(py) }) {
                stage.controller.dispatch(Action.SelectSound(sound.id))
            }
            hits.add(previewId, Rect(rect.right - d.dp(56f), rect.centerY - d.dp(18f), rect.right - d.dp(20f), rect.centerY + d.dp(18f)), onDrag = { _, py -> handleScroll(py) }) {
                val playing = stage.controller.previewingSound() == sound.id
                if (playing) stage.controller.dispatch(Action.StopPreview)
                else stage.controller.dispatch(Action.PreviewSound(sound.id))
            }
            y += rowH + gap
        }
        y += d.dp(10f)

        scrollMax = ((y + scroll) - (p.height - stage.safeBottom - d.dp(12f))).coerceAtLeast(0f)
        scroll = scroll.coerceIn(0f, scrollMax)
        if (hits.pressed.values.none { it.down }) lastY = Float.NaN
    }

    private fun handleScroll(y: Float) {
        if (lastY.isNaN()) lastY = y
        val dy = y - lastY
        scroll = (scroll - dy).coerceIn(0f, scrollMax)
        lastY = y
    }

    override fun draw(p: Painter, d: Design, now: Long) {
        val lang = stage.lang
        val isFa = lang.persian
        val pal = d.palette
        val w = p.width

        p.drawText(
            lang.sounds,
            if (isFa) w - d.dp(24f) else d.dp(24f),
            stage.safeTop + d.dp(96f),
            d.displayPaint(26f, pal.textPrimary, Weight.Bold, if (isFa) TextAlign.RIGHT else TextAlign.LEFT),
        )
        p.drawText(
            lang.soundSubtitle,
            if (isFa) w - d.dp(24f) else d.dp(24f),
            stage.safeTop + d.dp(114f),
            d.bodyPaint(10.5f, Argb.scaleAlpha(pal.textSecondary, 0.9f), if (isFa) TextAlign.RIGHT else TextAlign.LEFT),
        )

        p.save()
        p.clipRect(Rect(0f, stage.safeTop + d.dp(128f), w, p.height))

        // Volume card.
        val volumeCard = Rect(d.dp(20f), stage.safeTop + d.dp(150f) - scroll, w - d.dp(20f), stage.safeTop + d.dp(228f) - scroll)
        if (volumeCard.bottom > stage.safeTop + d.dp(128f)) {
            Surfaces.glass(p, d, volumeCard, d.dp(22f), 0.6f)
            p.drawText(
                lang.volume, volumeCard.left + d.dp(16f), volumeCard.top + d.dp(25f),
                d.labelPaint(8.5f, Argb.scaleAlpha(pal.textSecondary, 0.9f), 0.22f),
            )
            p.drawText(
                (stage.state.settings.soundVolume * 100).toInt().toString() + "%",
                volumeCard.right - d.dp(16f), volumeCard.top + d.dp(25f),
                d.titlePaint(12f, pal.textPrimary, TextAlign.RIGHT),
            )
            Controls.slider(p, d, sliderRect, stage.state.settings.soundVolume, volumeDragActive)
        }

        // Category chips.
        for ((cat, rect) in categoryRects) {
            val selected = cat == category
            Controls.chip(p, d, rect, if (cat == null) (if (isFa) "همه" else "All") else SoundCatalog.categoryLabel(cat, isFa), selected, hits.press("cat-${cat?.key ?: "all"}").value)
        }

        // Rows.
        val selectedId = stage.state.settings.soundId
        val previewing = stage.controller.previewingSound()
        for ((sound, rect) in rowRects) {
            if (rect.bottom < stage.safeTop + d.dp(126f) || rect.top > p.height) continue
            val isSelected = sound.id == selectedId
            val isPlaying = sound.id == previewing
            Surfaces.glass(
                p, d, rect, d.dp(20f),
                elevation = if (isSelected) 0.9f else 0.4f,
                tint = if (isSelected) Argb.withAlpha(pal.accent, 0.09f) else null,
                specularAlpha = if (isSelected) 0.9f else 0.5f,
            )
            if (isSelected) {
                p.drawRoundRect(
                    rect.inset(d.dp(0.6f)), Corners.all(d.dp(20f)),
                    Paint().apply {
                        style = PaintStyle.STROKE
                        strokeWidth = d.dp(1.1f)
                        color = Argb.withAlpha(pal.accent, 0.5f)
                    },
                )
            }
            val nameX = rect.left + d.dp(18f)
            p.drawText(
                if (isFa) sound.nameFa else sound.nameEn,
                nameX, rect.centerY - d.dp(3f),
                d.titlePaint(13f, if (isSelected) pal.textPrimary else Argb.scaleAlpha(pal.textPrimary, 0.95f)),
            )
            p.drawText(
                if (isFa) sound.blurbFa else sound.blurbEn,
                nameX, rect.centerY + d.dp(14f),
                d.bodyPaint(9.5f, Argb.scaleAlpha(pal.textSecondary, 0.85f)),
            )
            val durationLabel = String.format("%.1fs", sound.durationMs / 1000f)
            p.drawText(
                durationLabel,
                nameX + p.measureText(if (isFa) sound.nameFa else sound.nameEn, d.titlePaint(13f, pal.textPrimary)) + d.dp(10f),
                rect.centerY - d.dp(3f),
                d.labelPaint(7.5f, Argb.scaleAlpha(pal.textTertiary, 0.85f), 0.14f, latinOnly = true),
            )

            val btn = Rect(rect.right - d.dp(56f), rect.centerY - d.dp(18f), rect.right - d.dp(20f), rect.centerY + d.dp(18f))
            if (isPlaying) {
                Surfaces.glowCircle(p, btn.centerX, btn.centerY, btn.width * 0.9f, pal.accent, 0.30f)
            }
            Controls.iconButton(p, d, btn, if (isPlaying) Icon.Pause else Icon.Play, hits.press("snd-preview-${sound.id}").value, active = isPlaying)
            if (isSelected) {
                // Sits between the text block and the play button so it never covers the title.
                val checkSize = d.dp(13f)
                val check = Rect.centered(rect.right - d.dp(80f), rect.centerY, checkSize)
                Icons.draw(p, Icon.Check, check, pal.accent, 1.9f, d.density)
            }
        }
        p.restore()
    }

    override fun needsFrame(now: Long): Boolean = super.needsFrame(now) || catT.isRunning

    override fun onBack(now: Long): Boolean = false
}
