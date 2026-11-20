package com.mrsadhr.ui.screen

import com.mrsadhr.app.Action
import com.mrsadhr.core.format.Numbers
import com.mrsadhr.core.format.TimeFormat
import com.mrsadhr.core.motion.Animated
import com.mrsadhr.core.motion.Easing
import com.mrsadhr.core.motion.Motion
import com.mrsadhr.core.render.Argb
import com.mrsadhr.core.render.Paint
import com.mrsadhr.core.render.Painter
import com.mrsadhr.core.render.Rect
import com.mrsadhr.core.render.TextAlign
import com.mrsadhr.ui.Design
import com.mrsadhr.ui.Weight
import com.mrsadhr.ui.HitLayer
import com.mrsadhr.ui.Stage
import com.mrsadhr.ui.art.Surfaces
import com.mrsadhr.ui.widget.Controls
import com.mrsadhr.ui.widget.Icon

/**
 * Cinematic completion: a controlled lighting transition, expanding light rings, the finishing
 * time and two clear ways forward.
 */
class CompletionOverlay(private val stage: Stage) {

    val hits = HitLayer()
    var visible = false
        private set

    private val appear = Animated(0f, Motion.COMPLETION, Easing.Decelerate)

    fun update(now: Long) {
        val completion = stage.state.completion
        val shouldShow = completion != null
        if (shouldShow && !visible) {
            visible = true
            appear.snap(0f)
            appear.setTarget(1f, now, Motion.COMPLETION)
        } else if (!shouldShow && visible) {
            visible = false
            appear.snap(0f)
        }
        if (shouldShow) appear.setTarget(1f, now, Motion.COMPLETION)
        appear.update(now)
        hits.pressed.values.forEach { it.update(now) }
    }

    fun needsFrame(now: Long): Boolean = visible || appear.isRunning

    fun draw(p: Painter, d: Design) {
        if (!visible) return
        val t = appear.value.coerceIn(0f, 1f)
        val pal = d.palette
        val lang = stage.lang
        val completion = stage.state.completion ?: return
        val w = p.width
        val h = p.height
        val cx = w * 0.5f
        val cy = h * 0.44f
        hits.clear()

        // 1. Scrim. Registered first so every control below sits above it in hit testing.
        hits.add("cmp-scrim", Rect(0f, 0f, w, h)) { stage.controller.dispatch(Action.DismissCompletion) }
        p.drawRect(Rect(0f, 0f, w, h), Paint().apply { color = Argb.withAlpha(pal.scrim, 0.95f * t) })

        // 2. Centre bloom: an opaque pool of light so the finished display is cleanly replaced
        //    instead of bleeding through the scrim.
        val bloomRadius = w * (0.45f + 0.35f * t)
        p.drawCircle(
            cx, cy, bloomRadius,
            Paint().apply {
                shader = p.radialGradient(
                    cx, cy, bloomRadius,
                    intArrayOf(
                        Argb.withAlpha(pal.bgMid, 0.97f * t),
                        Argb.withAlpha(pal.bgMid, 0.82f * t),
                        Argb.withAlpha(pal.bgMid, 0f),
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                )
            },
        )

        // 3. Two light rings breathing out of the centre, kept well inside the action buttons.
        for (i in 0 until 2) {
            val offset = i * 0.28f
            val local = ((t - offset) / (1f - offset)).coerceIn(0f, 1f)
            if (local <= 0.001f) continue
            val radius = w * (0.10f + local * 0.16f)
            p.drawArc(
                Rect.centered(cx, cy, radius * 2f), 0f, 360f, false,
                Paint().apply {
                    style = com.mrsadhr.core.render.PaintStyle.STROKE
                    strokeWidth = d.dp(1.6f) * (1f - local * 0.6f)
                    color = pal.accentEdge
                    alpha = (1f - local) * 0.45f
                    blend = com.mrsadhr.core.render.BlendMode.PLUS
                },
            )
        }

        val textAlpha = (t / 0.55f).coerceIn(0f, 1f)
        val rise = (1f - textAlpha) * d.dp(18f)

        p.drawText(
            lang.timeUp,
            cx, cy - d.dp(6f) + rise,
            d.displayPaint(30f, pal.textPrimary, Weight.Bold, TextAlign.CENTER).apply {
                letterSpacingEm = 0.18f
                alpha = textAlpha
            },
        )
        p.drawText(
            Numbers.localize(TimeFormat.hmsAlways(completion.durationMs), lang.persian),
            cx, cy + d.dp(34f) + rise,
            d.labelPaint(10f, Argb.scaleAlpha(pal.textSecondary, textAlpha), 0.30f, TextAlign.CENTER),
        )
        p.drawText(
            Numbers.localize(TimeFormat.spokenDuration(completion.durationMs, lang.persian), lang.persian),
            cx, cy + d.dp(54f) + rise,
            d.bodyPaint(10f, Argb.scaleAlpha(pal.textTertiary, textAlpha * 0.9f), TextAlign.CENTER),
        )

        // Actions.
        val actionT = ((t - 0.35f) / 0.5f).coerceIn(0f, 1f)
        if (actionT > 0.01f) {
            p.save()
            p.saveLayerAlpha(Easing.Decelerate.transform(actionT))
            val btnW = w - d.dp(72f)
            val primary = Rect(cx - btnW * 0.5f, cy + d.dp(96f), cx + btnW * 0.5f, cy + d.dp(152f))
            val secondary = Rect(cx - btnW * 0.5f, cy + d.dp(164f), cx + btnW * 0.5f, cy + d.dp(212f))
            Controls.primary(p, d, primary, lang.repeat, hits.press("cmp-again").value, Icon.Repeat)
            Controls.ghost(p, d, secondary, lang.done, hits.press("cmp-done").value, Icon.Check)
            hits.add("cmp-again", primary) { stage.controller.dispatch(Action.TimerRepeat) }
            hits.add("cmp-done", secondary) { stage.controller.dispatch(Action.DismissCompletion) }
            p.restore()
        } else {
            hits.add("cmp-block", Rect(0f, 0f, w, h)) { }
        }
    }
}
