package com.mrsadhr.ui.screen

import com.mrsadhr.app.Action
import com.mrsadhr.core.format.Numbers
import com.mrsadhr.core.format.TimeFormat
import com.mrsadhr.core.model.Mode
import com.mrsadhr.core.model.TimePart
import com.mrsadhr.core.motion.Animated
import com.mrsadhr.core.motion.Easing
import com.mrsadhr.core.motion.Motion
import com.mrsadhr.core.render.Argb
import com.mrsadhr.core.render.Corners
import com.mrsadhr.core.render.Paint
import com.mrsadhr.core.render.Painter
import com.mrsadhr.core.render.Rect
import com.mrsadhr.core.render.TextAlign
import com.mrsadhr.ui.Design
import com.mrsadhr.ui.Weight
import com.mrsadhr.ui.Screen
import com.mrsadhr.ui.Stage
import com.mrsadhr.ui.art.Surfaces
import com.mrsadhr.ui.art.TimerRing
import com.mrsadhr.ui.widget.Controls
import com.mrsadhr.ui.widget.Icon
import com.mrsadhr.ui.widget.KineticTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The main experience: mode switch, the 3D ring, the time editor, presets and transport controls,
 * plus the stopwatch which shares the same stage.
 *
 * Layout is computed once per frame into [Metrics] and reused by both the hit layer and the
 * renderer, so a control can never be drawn somewhere it cannot be touched.
 */
class TimerScreen(stage: Stage) : Screen(stage) {

    private class Metrics(
        val modeRect: Rect,
        val ringBox: Rect,
        val editorTop: Float,
        val editorBottom: Float,
        val presetTop: Float,
        val presetBottom: Float,
        val steppers: List<Rect>,
        val primary: Rect,
        val reset: Rect,
        val lap: Rect,
        val lapArea: Rect,
        val focusPill: Rect,
        val sheet: Rect?,
    )

    private val kinetic = KineticTime()
    private val modeSel = Animated(0f, Motion.CHIP, Easing.Emphasize)
    private val editorT = Animated(1f, Motion.SCREEN, Easing.Emphasize)
    private val presetT = Animated(1f, Motion.SCREEN, Easing.Emphasize)

    private var presetScroll = 0f
    private var presetScrollMax = 0f
    private var scrollLastX = 0f

    private var sheetPresetId: String? = null
    private var activePart = TimePart.Minutes
    private var lastStepAt = 0L

    private var metrics: Metrics? = null

    override fun update(now: Long) {
        val s = stage.state
        val focus = stage.focusAmount
        modeSel.setTarget(if (s.mode == Mode.Timer) 0f else 1f, now, Motion.CHIP)
        modeSel.update(now)
        val showEditor = s.mode == Mode.Timer && !s.timer.running && !s.timer.completed && focus < 0.5f
        val showPresets = s.mode == Mode.Timer && !s.timer.running && !s.timer.completed && focus < 0.5f
        if (s.settings.reducedMotion) {
            editorT.snap(if (showEditor) 1f else 0f)
            presetT.snap(if (showPresets) 1f else 0f)
        } else {
            editorT.setTarget(if (showEditor) 1f else 0f, now)
            presetT.setTarget(if (showPresets) 1f else 0f, now)
        }

        editorT.update(now)
        presetT.update(now)
        hits.pressed.values.forEach { it.update(now) }

        // Held stepper buttons auto-repeat for fast, still precise adjustment.
        for ((id, target) in STEPPERS) {
            val press = hits.pressed[id] ?: continue
            if (press.down && now - press.downAt > 380L) {
                val interval = if (now - press.downAt > 2000L) 50L else 110L
                if (now - lastStepAt > interval) {
                    lastStepAt = now
                    stage.controller.dispatch(Action.StepPart(target.first, target.second))
                }
            }
        }
    }

    override fun layout(p: Painter, d: Design, now: Long) {
        hits.clear()
        val s = stage.state
        val w = p.width
        val h = p.height
        val safeTop = stage.safeTop
        val safeBottom = stage.safeBottom
        val focus = stage.focusAmount
        val lang = stage.lang

        // Mode switch (shrinks and fades while the focus presentation is active).
        val modeW = d.dp(206f) * (1f - 0.12f * focus)
        val modeH = d.dp(38f) * (1f - 0.16f * focus)
        val modeRect = Rect.centered(w * 0.5f, safeTop + d.dp(62f) + modeH * 0.5f + d.dp(4f), modeW, modeH)
        if (focus < 0.5f) {
            hits.add("mode-timer", Rect(modeRect.left, modeRect.top, modeRect.centerX, modeRect.bottom)) {
                stage.controller.dispatch(Action.SetMode(Mode.Timer))
            }
            hits.add("mode-stopwatch", Rect(modeRect.centerX, modeRect.top, modeRect.right, modeRect.bottom)) {
                stage.controller.dispatch(Action.SetMode(Mode.Stopwatch))
            }
        }

        // Transport row.
        val controlsH = d.dp(56f)
        val controlsBottom = h - safeBottom - d.dp(22f)
        val controlsTop = controlsBottom - controlsH
        val primaryW = d.dp(186f)
        val primary = Rect.centered(w * 0.5f, controlsTop + controlsH * 0.5f, primaryW, controlsH)
        val resetSize = d.dp(50f)
        val reset = Rect.centered(primary.right + d.dp(15f) + resetSize * 0.5f, primary.centerY, resetSize)
        val lap = Rect.centered(primary.left - d.dp(15f) - resetSize * 0.5f, primary.centerY, resetSize)

        // Flexible vertical stack: ring, editor, presets.
        val editorH = d.dp(92f) * editorT.value
        val presetH = d.dp(32f) * presetT.value
        val presetBottom = controlsTop - d.dp(24f)
        val presetTop = presetBottom - presetH
        val editorBottom = (if (presetT.value > 0.02f) presetTop - d.dp(18f) else controlsTop - d.dp(30f))
        val editorTop = editorBottom - editorH

        val ringTop = modeRect.bottom + d.dp(12f)
        val ringBottom = (if (editorT.value > 0.02f) editorTop - d.dp(14f) else controlsTop - d.dp(26f))
        val ringSide = minOf(w - d.dp(44f), (ringBottom - ringTop).coerceAtLeast(d.dp(190f)))
        val ringBox = Rect.centered(w * 0.5f, ringTop + (ringBottom - ringTop) * 0.5f, ringSide)

        // Steppers.
        val steppers = ArrayList<Rect>(3)
        if (d.dp(1f) > 0f && s.mode == Mode.Timer) {
            val colW = d.dp(78f)
            val gap = d.dp(14f)
            val totalW = colW * 3 + gap * 2
            var x = w * 0.5f - totalW * 0.5f
            val entries = listOf("h" to TimePart.Hours, "m" to TimePart.Minutes, "s" to TimePart.Seconds)
            for ((key, part) in entries) {
                val rect = Rect(x, editorTop, x + colW, editorBottom)
                steppers.add(rect)
                if (editorT.value > 0.35f) {
                    hits.add("st-$key-plus", Rect(rect.left, rect.top, rect.right, rect.top + rect.height * 0.30f)) {
                        activePart = part
                        stage.controller.dispatch(Action.StepPart(part, 1))
                    }
                    hits.add("st-$key-minus", Rect(rect.left, rect.bottom - rect.height * 0.30f, rect.right, rect.bottom)) {
                        activePart = part
                        stage.controller.dispatch(Action.StepPart(part, -1))
                    }
                    var lastDragY = Float.NaN
                    hits.add(
                        "st-$key-body",
                        Rect(rect.left, rect.top + rect.height * 0.30f, rect.right, rect.bottom - rect.height * 0.30f),
                        onTap = { activePart = part },
                        onDrag = { _, y ->
                            if (lastDragY.isNaN()) lastDragY = y
                            val dy = y - lastDragY
                            if (abs(dy) >= d.dp(10f)) {
                                activePart = part
                                val steps = (dy / d.dp(10f)).roundToInt()
                                if (steps != 0) {
                                    stage.controller.dispatch(Action.StepPart(part, -steps))
                                    lastDragY = y
                                }
                            }
                        },
                    )
                }
                x += colW + gap
            }
        }

        // Preset strip.
        if (presetT.value > 0.35f && s.mode == Mode.Timer) {
            layoutPresets(p, d, presetTop, presetBottom, now)
        } else {
            presetScroll = 0f
            presetScrollMax = 0f
        }

        // Preset context sheet.
        var sheetRect: Rect? = null
        val sheetId = sheetPresetId
        if (sheetId != null) {
            val sheetH = d.dp(146f)
            sheetRect = Rect(d.dp(18f), controlsTop - d.dp(10f) - sheetH, w - d.dp(18f), controlsTop - d.dp(10f))
            hits.add("sheet-dismiss", Rect(0f, 0f, w, h)) { sheetPresetId = null }
            hits.add("sheet-update", Rect(sheetRect.left + d.dp(14f), sheetRect.top + d.dp(52f), sheetRect.right - d.dp(14f), sheetRect.top + d.dp(96f))) {
                stage.controller.dispatch(Action.UpdatePreset(sheetId))
                sheetPresetId = null
            }
            hits.add("sheet-delete", Rect(sheetRect.left + d.dp(14f), sheetRect.top + d.dp(100f), sheetRect.right - d.dp(14f), sheetRect.bottom - d.dp(12f))) {
                stage.controller.dispatch(Action.DeletePreset(sheetId))
                sheetPresetId = null
            }
        }

        val focusPill = Rect.centered(w * 0.5f, primary.top - d.dp(34f), d.dp(104f), d.dp(28f))
        if (focus > 0.05f && s.completion == null) {
            hits.add("focus-exit", Rect.centered(w * 0.5f, focusPill.centerY, d.dp(150f), d.dp(48f))) {
                stage.controller.dispatch(Action.ToggleFocusMode)
            }
        }

        // Transport hits.
        when (s.mode) {
            Mode.Timer -> {
                hits.add("primary", primary) {
                    stage.controller.dispatch(
                        when {
                            s.timer.running -> Action.TimerPause
                            s.timer.completed -> Action.TimerRepeat
                            else -> Action.TimerStart
                        },
                    )
                }
            }
            Mode.Stopwatch -> {
                hits.add("primary", primary) { stage.controller.dispatch(Action.StopwatchToggle) }
                if (s.stopwatch.running) hits.add("lap", lap) { stage.controller.dispatch(Action.StopwatchLap) }
            }
        }
        hits.add("reset", reset) {
            if (s.mode == Mode.Timer) stage.controller.dispatch(Action.TimerReset)
            else stage.controller.dispatch(Action.StopwatchReset)
        }

        metrics = Metrics(
            modeRect = modeRect,
            ringBox = ringBox,
            editorTop = editorTop,
            editorBottom = editorBottom,
            presetTop = presetTop,
            presetBottom = presetBottom,
            steppers = steppers,
            primary = primary,
            reset = reset,
            lap = lap,
            lapArea = Rect(d.dp(22f), ringBox.bottom + d.dp(6f), w - d.dp(22f), controlsTop - d.dp(16f)),
            focusPill = focusPill,
            sheet = sheetRect,
        )
        // lang is used for label measurement below
        measurePresetLabels(p, d, lang.persian)
    }

    private val presetChipRects = ArrayList<Pair<String, Rect>>()
    private var presetSaveRect: Rect = Rect.Zero

    private fun measurePresetLabels(p: Painter, d: Design, persian: Boolean) {
        // widths are computed in layoutPresets; kept for clarity of intent
    }

    private fun layoutPresets(p: Painter, d: Design, top: Float, bottom: Float, now: Long) {
        val s = stage.state
        val lang = stage.lang
        presetChipRects.clear()
        hits.add(
            "preset-strip",
            Rect(0f, top - d.dp(8f), p.width, bottom + d.dp(8f)),
            onDrag = { px, _ ->
                if (scrollLastX == 0f) scrollLastX = px
                val dx = px - scrollLastX
                presetScroll = (presetScroll - dx).coerceIn(0f, presetScrollMax)
                scrollLastX = px
            },
        )
        val presetPaint = d.labelPaint(11.5f, d.palette.chipText, 0.10f)
        val gap = d.dp(8f)
        var x = d.dp(20f) - presetScroll
        for (preset in s.settings.presets) {
            val label = Numbers.localize(TimeFormat.presetLabel(preset.seconds * 1000L, lang.persian), lang.persian)
            val chipW = (p.measureText(label, presetPaint) + d.dp(30f)).coerceAtLeast(d.dp(50f))
            val rect = Rect(x, top, x + chipW, bottom)
            presetChipRects.add(preset.id to rect)
            hits.add("preset-${preset.id}", rect, onTap = {
                stage.controller.dispatch(Action.SelectPreset(preset.seconds))
            }, onLongPress = {
                sheetPresetId = preset.id
            })
            x += chipW + gap
        }
        presetSaveRect = Rect(x, top, x + d.dp(44f), bottom)
        hits.add("preset-save", presetSaveRect) { stage.controller.dispatch(Action.SavePreset) }
        presetScrollMax = ((presetSaveRect.right + presetScroll) - (p.width - d.dp(20f))).coerceAtLeast(0f)
        presetScroll = presetScroll.coerceIn(0f, presetScrollMax)
        if (hits.pressed.values.none { it.down }) scrollLastX = 0f
    }

    override fun draw(p: Painter, d: Design, now: Long) {
        val m = metrics ?: return
        val s = stage.state
        val lang = stage.lang
        val pal = d.palette
        val isFa = lang.persian

        // Mode switch.
        if (stage.focusAmount < 0.96f) {
            p.save()
            p.saveLayerAlpha((1f - stage.focusAmount).coerceIn(0f, 1f))
            Controls.segmented(
                p, d, m.modeRect,
                listOf(lang.timer, lang.stopwatch),
                if (s.mode == Mode.Timer) 0 else 1,
                modeSel.value,
                0f,
            )
            p.restore()
        }

        // Light pooled behind the instrument: lifts the ring off the backdrop.
        run {
            val geo = TimerRing.geometry(d, m.ringBox)
            Surfaces.glowCircle(p, geo.cx, geo.cy + d.dp(6f), geo.outerRadius * 1.55f, pal.ringGlow, 0.05f + 0.05f * stage.focusAmount)
        }

        // Ring.
        val timer = s.timer
        val progress = if (s.mode == Mode.Timer) timer.progress else (s.stopwatch.elapsedMs % 60_000L) / 60_000f
        TimerRing.draw(
            p, d, m.ringBox,
            TimerRing.Params(
                progress = progress,
                timeSec = now / 1000f,
                running = if (s.mode == Mode.Timer) timer.running else s.stopwatch.running,
                completionPulse = 0f,
                idleGlow = 0.05f * stage.focusAmount,
                showHead = timer.remainingMs < timer.durationMs || s.stopwatch.running,
            ),
        )

        drawReadout(p, d, TimerRing.geometry(d, m.ringBox), now, isFa)

        // Editor.
        if (editorT.value > 0.01f && s.mode == Mode.Timer) {
            p.save()
            p.saveLayerAlpha(editorT.value)
            drawEditor(p, d, m, now)
            p.restore()
        }

        // Presets.
        if (presetT.value > 0.01f && s.mode == Mode.Timer) {
            p.save()
            p.saveLayerAlpha(presetT.value)
            drawPresets(p, d, m, now)
            p.restore()
        }

        // Transport.
        val primaryLabel: String
        val primaryIcon: Icon
        when (s.mode) {
            Mode.Timer -> {
                primaryIcon = when {
                    timer.running -> Icon.Pause
                    timer.completed -> Icon.Repeat
                    else -> Icon.Play
                }
                primaryLabel = when {
                    timer.running -> lang.pause
                    timer.completed -> lang.repeat
                    timer.remainingMs < timer.durationMs -> lang.resume
                    else -> lang.start
                }
            }
            Mode.Stopwatch -> {
                primaryIcon = if (s.stopwatch.running) Icon.Pause else Icon.Play
                primaryLabel = when {
                    s.stopwatch.running -> lang.pause
                    s.stopwatch.elapsedMs > 0L -> lang.resume
                    else -> lang.start
                }
            }
        }
        // The completion overlay owns the screen once the countdown is over; the transport is
        // hidden so its ghost cannot sit behind the overlay's own actions.
        if (s.completion == null) {
            Controls.primary(
                p, d, m.primary, primaryLabel,
                hits.press("primary").value, primaryIcon,
                enabled = if (s.mode == Mode.Timer) timer.durationMs > 0L else true,
            )
            Controls.iconButton(p, d, m.reset, Icon.Reset, hits.press("reset").value)
            if (s.mode == Mode.Stopwatch && s.stopwatch.running) {
                Controls.iconButton(p, d, m.lap, Icon.Flag, hits.press("lap").value)
            }
        }

        if (s.mode == Mode.Stopwatch) drawLaps(p, d, now, m.lapArea)

        drawSheet(p, d, m)

        if (stage.focusAmount > 0.05f && s.completion == null) {
            p.save()
            p.saveLayerAlpha(stage.focusAmount)
            Controls.chip(p, d, m.focusPill, lang.focus, selected = false, press = 0f, icon = Icon.Close)
            p.restore()
        }
    }

    private fun drawReadout(p: Painter, d: Design, g: TimerRing.Geometry, now: Long, isFa: Boolean) {
        val s = stage.state
        val lang = stage.lang
        val pal = d.palette
        val timer = s.timer
        val isStopwatch = s.mode == Mode.Stopwatch

        val text = if (isStopwatch) {
            Numbers.localize(TimeFormat.stopwatch(s.stopwatch.elapsedMs), isFa)
        } else {
            Numbers.localize(TimeFormat.hms(timer.remainingMs), isFa)
        }

        val maxWidth = g.discRadius * 1.66f
        val baseSize = if (isStopwatch) g.discRadius * 0.285f else g.discRadius * 0.42f
        val paint = d.timePaint(10f, pal.textPrimary, Weight.Medium)
        paint.textSize = baseSize
        val measured = p.measureText(text, paint)
        if (measured > maxWidth) paint.textSize = baseSize * (maxWidth / measured)

        if (isStopwatch) {
            p.drawText(text, g.cx, g.cy + paint.textSize * 0.18f, paint.copy().apply { textAlign = TextAlign.CENTER })
        } else {
            kinetic.setText(text, now)
            kinetic.draw(p, g.cx, g.cy - d.dp(8f), paint, now)
        }

        val caption = when {
            isStopwatch -> when {
                s.stopwatch.running -> lang.elapsed
                s.stopwatch.elapsedMs > 0L -> lang.paused
                else -> lang.stopwatch
            }
            timer.completed -> lang.timeUp
            timer.running -> lang.remaining
            timer.remainingMs < timer.durationMs -> lang.paused
            else -> lang.setTime
        }
        val captionPaint = d.labelPaint(8.5f, Argb.scaleAlpha(pal.textSecondary, 0.92f), 0.30f, TextAlign.CENTER)
        p.drawText(caption, g.cx, g.cy + paint.textSize * 0.54f + d.dp(18f), captionPaint)

        val subPaint = d.labelPaint(8f, Argb.scaleAlpha(pal.textTertiary, 0.9f), 0.18f, TextAlign.CENTER)
        when {
            isStopwatch && s.stopwatch.laps.isNotEmpty() ->
                p.drawText(lang.lapWord + " " + Numbers.localize(s.stopwatch.laps.size.toString(), isFa), g.cx, g.cy + paint.textSize * 0.54f + d.dp(33f), subPaint)
            !isStopwatch && timer.running ->
                p.drawText(lang.ofLabel + " " + Numbers.localize(TimeFormat.hms(timer.durationMs), isFa), g.cx, g.cy + paint.textSize * 0.54f + d.dp(33f), subPaint)
        }
    }

    private fun drawEditor(p: Painter, d: Design, m: Metrics, now: Long) {
        val s = stage.state
        val lang = stage.lang
        val pal = d.palette
        val (h, mi, se) = TimeFormat.parts(s.timer.durationMs)
        val values = listOf(h, mi, se)
        val marks = listOf(lang.hourMark, lang.minuteMark, lang.secondMark)
        val parts = listOf(TimePart.Hours, TimePart.Minutes, TimePart.Seconds)
        val keys = listOf("h", "m", "s")
        val enabled = !s.timer.running

        m.steppers.forEachIndexed { index, rect ->
            Controls.stepper(
                p, d, rect,
                Numbers.localize(TimeFormat.two(values[index]), lang.persian),
                marks[index],
                activePart == parts[index],
                hits.press("st-${keys[index]}-plus").value,
                hits.press("st-${keys[index]}-minus").value,
                enabled,
            )
        }
    }

    private fun drawPresets(p: Painter, d: Design, m: Metrics, now: Long) {
        val s = stage.state
        val lang = stage.lang
        Controls.sectionCaption(
            p, d, lang.shortcuts,
            if (lang.persian) p.width - d.dp(20f) else d.dp(20f),
            m.presetTop - d.dp(12f),
            if (lang.persian) TextAlign.RIGHT else TextAlign.LEFT,
        )
        p.save()
        p.clipRect(Rect(0f, m.presetTop - d.dp(12f), p.width, m.presetBottom + d.dp(12f)))
        for ((id, rect) in presetChipRects) {
            val preset = s.settings.presets.firstOrNull { it.id == id } ?: continue
            val label = Numbers.localize(TimeFormat.presetLabel(preset.seconds * 1000L, lang.persian), lang.persian)
            val selected = preset.seconds * 1000L == s.timer.durationMs && !s.timer.running
            Controls.chip(p, d, rect, label, selected, hits.press("preset-$id").value)
        }
        Controls.chip(p, d, presetSaveRect, "", selected = false, press = hits.press("preset-save").value, icon = Icon.Plus)
        p.restore()
    }

    private fun drawLaps(p: Painter, d: Design, now: Long, area: Rect) {
        val s = stage.state
        val lang = stage.lang
        val pal = d.palette
        val laps = s.stopwatch.laps
        if (laps.isEmpty()) {
            p.drawText(lang.noLaps, area.centerX, area.centerY, d.bodyPaint(11f, Argb.scaleAlpha(pal.textTertiary, 0.8f), TextAlign.CENTER))
            return
        }
        val rowH = d.dp(32f)
        val rows = minOf(3, laps.size)
        var y = area.centerY - rows * rowH * 0.5f
        for (i in 0 until rows) {
            val lap = laps[laps.size - 1 - i]
            val rect = Rect(area.left, y, area.right, y + rowH)
            if (i == 0) {
                p.drawRoundRect(rect, Corners.all(d.dp(12f)), Paint().apply { color = Argb.withAlpha(pal.accent, 0.055f) })
            }
            p.drawText(
                Numbers.localize(TimeFormat.two(lap.index), lang.persian),
                rect.left + d.dp(12f), rect.centerY + d.dp(4f),
                d.labelPaint(8f, Argb.scaleAlpha(pal.textTertiary, 0.95f), 0.16f),
            )
            p.drawText(
                Numbers.localize(TimeFormat.stopwatch(lap.splitMs), lang.persian),
                rect.centerX + d.dp(4f), rect.centerY + d.dp(4.5f),
                d.titlePaint(12f, pal.textPrimary, TextAlign.CENTER),
            )
            p.drawText(
                Numbers.localize(TimeFormat.stopwatch(lap.totalMs), lang.persian),
                rect.right - d.dp(12f), rect.centerY + d.dp(4f),
                d.bodyPaint(10f, Argb.scaleAlpha(pal.textSecondary, 0.9f), TextAlign.RIGHT),
            )
            y += rowH
        }
        // clear affordance
        val clearRect = Rect(area.right - d.dp(72f), area.bottom - d.dp(28f), area.right, area.bottom)
        Controls.chip(p, d, clearRect, lang.clearLaps, selected = false, press = hits.press("laps-clear").value)
        hits.add("laps-clear", clearRect) { stage.controller.dispatch(Action.StopwatchClearLaps) }
    }

    private fun drawSheet(p: Painter, d: Design, m: Metrics) {
        val sheetRect = m.sheet ?: return
        val id = sheetPresetId ?: return
        val pal = d.palette
        val lang = stage.lang
        p.drawRect(Rect(0f, 0f, p.width, p.height), Paint().apply {
            color = Argb.withAlpha(pal.scrim, 0.5f)
        })
        Surfaces.glass(p, d, sheetRect, d.dp(24f), 1.3f)
        val preset = stage.state.settings.presets.firstOrNull { it.id == id }
        val label = preset?.let { Numbers.localize(TimeFormat.presetLabel(it.seconds * 1000L, lang.persian), lang.persian) } ?: ""
        p.drawText(
            lang.presetEditorTitle + " · " + label,
            sheetRect.centerX, sheetRect.top + d.dp(30f),
            d.labelPaint(9.5f, pal.textSecondary, 0.24f, TextAlign.CENTER),
        )
        Controls.ghost(
            p, d,
            Rect(sheetRect.left + d.dp(14f), sheetRect.top + d.dp(52f), sheetRect.right - d.dp(14f), sheetRect.top + d.dp(96f)),
            lang.update, hits.press("sheet-update").value, Icon.Check,
            tint = Argb.withAlpha(pal.accent, 0.10f),
        )
        Controls.ghost(
            p, d,
            Rect(sheetRect.left + d.dp(14f), sheetRect.top + d.dp(100f), sheetRect.right - d.dp(14f), sheetRect.bottom - d.dp(12f)),
            lang.delete, hits.press("sheet-delete").value, Icon.Trash,
            tint = Argb.withAlpha(pal.danger, 0.10f),
        )
    }

    override fun needsFrame(now: Long): Boolean =
        super.needsFrame(now) || editorT.isRunning || presetT.isRunning || modeSel.isRunning

    override fun onBack(now: Long): Boolean {
        if (sheetPresetId != null) {
            sheetPresetId = null
            return true
        }
        return false
    }

    companion object {
        private val STEPPERS: List<Pair<String, Pair<TimePart, Int>>> = listOf(
            "st-h-plus" to (TimePart.Hours to 1), "st-h-minus" to (TimePart.Hours to -1),
            "st-m-plus" to (TimePart.Minutes to 1), "st-m-minus" to (TimePart.Minutes to -1),
            "st-s-plus" to (TimePart.Seconds to 1), "st-s-minus" to (TimePart.Seconds to -1),
        )
    }
}
