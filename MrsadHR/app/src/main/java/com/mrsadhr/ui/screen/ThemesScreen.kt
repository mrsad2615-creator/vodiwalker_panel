package com.mrsadhr.ui.screen

import com.mrsadhr.app.Action
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
import com.mrsadhr.themes.Palette
import com.mrsadhr.themes.RingStyle
import com.mrsadhr.themes.ThemeSpec
import com.mrsadhr.themes.Themes
import com.mrsadhr.ui.Design
import com.mrsadhr.ui.Weight
import com.mrsadhr.ui.Screen
import com.mrsadhr.ui.Stage
import com.mrsadhr.ui.art.Surfaces
import com.mrsadhr.ui.art.TimerRing
import com.mrsadhr.ui.widget.Controls
import com.mrsadhr.ui.widget.Icon
import kotlin.math.abs

/**
 * Themes centre: three material cards, each with a live miniature of the real ring so the choice
 * is made on the actual product rendering rather than an illustration.
 */
class ThemesScreen(stage: Stage) : Screen(stage) {

    private val cardRects = ArrayList<Pair<ThemeSpec, Rect>>()
    private val activePulse = Animated(1f, 900, Easing.Standard)
    private var scroll = 0f
    private var scrollMax = 0f
    private var lastY = Float.NaN
    private val expand = HashMap<String, Animated>()

    override fun update(now: Long) {
        activePulse.update(now)
        hits.pressed.values.forEach { it.update(now) }
        expand.values.forEach { it.update(now) }
    }

    override fun layout(p: Painter, d: Design, now: Long) {
        hits.clear()
        cardRects.clear()
        // Background scroll catcher is registered first so the cards above it win hit testing.
        hits.add(
            "themes-scroll",
            Rect(0f, stage.safeTop + d.dp(60f), p.width, p.height),
            onDrag = { _, py -> handleScroll(py) },
        )
        val lang = stage.lang
        val w = p.width
        val top = stage.safeTop + d.dp(84f) - scroll
        val gap = d.dp(14f)
        val available = p.height - stage.safeBottom - d.dp(24f) - (top + d.dp(54f))
        val cardH = ((available - gap * 2f) / 3f).coerceIn(d.dp(118f), d.dp(168f))
        var y = top + d.dp(54f)
        for (theme in Themes.all) {
            val rect = Rect(d.dp(20f), y, w - d.dp(20f), y + cardH)
            cardRects.add(theme to rect)
            hits.add("theme-${theme.id.key}", rect, onTap = {
                stage.controller.dispatch(Action.SetTheme(theme.id))
            }, onDrag = { _, py -> handleScroll(py) })
            y += cardH + gap
        }
        val contentBottom = y - gap
        scrollMax = ((contentBottom + scroll) - (p.height - stage.safeBottom - d.dp(16f))).coerceAtLeast(0f)
        scroll = scroll.coerceIn(0f, scrollMax)
        if (hits.pressed.values.none { it.down }) lastY = Float.NaN
        // keep a reference so the unused warning never fires
        if (lang.timer.isEmpty()) Unit
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

        p.drawText(
            lang.themes,
            if (isFa) p.width - d.dp(24f) else d.dp(24f),
            stage.safeTop + d.dp(96f),
            d.displayPaint(26f, d.palette.textPrimary, Weight.Bold, if (isFa) TextAlign.RIGHT else TextAlign.LEFT),
        )
        p.drawText(
            lang.themeSubtitle,
            if (isFa) p.width - d.dp(24f) else d.dp(24f),
            stage.safeTop + d.dp(114f),
            d.bodyPaint(10.5f, Argb.scaleAlpha(d.palette.textSecondary, 0.9f), if (isFa) TextAlign.RIGHT else TextAlign.LEFT),
        )

        p.save()
        p.clipRect(Rect(0f, stage.safeTop + d.dp(120f), p.width, p.height))
        for ((theme, rect) in cardRects) {
            drawThemeCard(p, d, theme, rect, now, isFa)
        }
        p.restore()
    }

    private fun drawThemeCard(p: Painter, d: Design, theme: ThemeSpec, rect: Rect, now: Long, isFa: Boolean) {
        val lang = stage.lang
        val pal = theme.palette
        val active = stage.currentTheme == theme.id
        val press = hits.press("theme-${theme.id.key}").value
        val radius = d.dp(26f)

        val scaled = if (press > 0.001f) {
            val f = 1f - 0.012f * press
            Rect.centered(rect.centerX, rect.centerY, rect.width * f, rect.height * f)
        } else rect

        // Card body: its own palette so the swatch is the material itself.
        val shadow = Paint().apply { color = Argb.withAlpha(pal.shadow, if (pal.lightness > 0.5f) 0.30f else 0.55f) }
        p.drawRoundRect(scaled.offset(0f, d.dp(10f)), Corners.all(radius), shadow)

        p.drawRoundRect(
            scaled, Corners.all(radius),
            Paint().apply {
                shader = p.linearGradient(
                    scaled.left, scaled.top, scaled.right, scaled.bottom,
                    intArrayOf(
                        Argb.mix(pal.bgMid, pal.bgTop, 0.5f),
                        pal.bgMid,
                        Argb.mix(pal.bgBottom, 0x00000000, 0.2f),
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                )
            },
        )

        // Ambient light of the theme inside the card.
        p.save()
        p.clipRoundRect(scaled, radius)
        Surfaces.glowCircle(p, scaled.left + scaled.width * 0.18f, scaled.top + scaled.height * 0.1f, scaled.width * 0.55f, pal.ambientA, 0.16f)
        Surfaces.glowCircle(p, scaled.right - scaled.width * 0.1f, scaled.bottom, scaled.width * 0.6f, pal.ambientB, 0.18f)
        Surfaces.glowCircle(p, scaled.centerX, scaled.bottom + scaled.height * 0.2f, scaled.width * 0.7f, pal.ambientC, 0.10f)
        p.restore()

        // Hairline border, accent when active.
        p.drawRoundRect(
            scaled.inset(d.dp(0.6f)), Corners.all(radius),
            Paint().apply {
                style = PaintStyle.STROKE
                strokeWidth = d.dp(if (active) 1.6f else 1f)
                shader = p.linearGradient(
                    scaled.left, scaled.top, scaled.right, scaled.bottom,
                    intArrayOf(
                        Argb.withAlpha(if (active) pal.accent else pal.glassBorder, if (active) 0.85f else 1.1f),
                        Argb.withAlpha(pal.glassBorder, if (active) 0.5f else 0.7f),
                        Argb.withAlpha(pal.glassBorder, 0.25f),
                    ),
                    null,
                )
            },
        )

        // Live ring miniature.
        val ringSize = scaled.height * 0.66f
        val ringCx = scaled.left + d.dp(22f) + ringSize * 0.5f
        val ringCy = scaled.centerY
        val themeDesign = d.withPalette(pal, theme.ringStyle)
        val box = Rect.centered(ringCx, ringCy, ringSize)
        TimerRing.draw(
            p, themeDesign, box,
            TimerRing.Params(progress = 0.62f, timeSec = now / 1000f, running = active, showHead = false),
        )

        // Typography block.
        val textLeft = if (isFa) scaled.left + d.dp(22f) else ringCx + ringSize * 0.5f + d.dp(20f)
        val textAlign = if (isFa) TextAlign.LEFT else TextAlign.LEFT
        val titleX = if (isFa) scaled.left + d.dp(22f) else textLeft
        p.drawText(
            if (isFa) theme.id.labelFa else theme.id.labelEn,
            titleX, scaled.top + d.dp(46f),
            d.displayPaint(20f, pal.textPrimary, Weight.Bold, textAlign),
        )
        p.drawText(
            if (isFa) theme.taglineFa else theme.taglineEn,
            titleX, scaled.top + d.dp(66f),
            d.bodyPaint(9.5f, Argb.scaleAlpha(pal.textSecondary, 0.95f), textAlign),
        )

        // Swatches.
        var sx = titleX
        val swatchY = scaled.bottom - d.dp(30f)
        val swatches = listOf(pal.accent, pal.progressMid, pal.progressEnd, pal.ambientA, pal.ambientB)
        swatches.forEach { color ->
            p.drawCircle(sx + d.dp(5f), swatchY, d.dp(5f), Paint().apply { this.color = color })
            sx += d.dp(15f)
        }

        if (active) {
            val badgeW = d.dp(62f)
            val badge = Rect(scaled.right - badgeW - d.dp(14f), scaled.top + d.dp(14f), scaled.right - d.dp(14f), scaled.top + d.dp(34f))
            p.drawRoundRect(
                badge, Corners.all(badge.height * 0.5f),
                Paint().apply {
                    shader = p.linearGradient(badge.left, badge.top, badge.right, badge.bottom, intArrayOf(pal.accent, pal.accentSoft), null)
                },
            )
            p.drawText(
                lang.active, badge.centerX, badge.centerY + d.dp(3.5f),
                d.labelPaint(7.5f, pal.onAccent, 0.22f, TextAlign.CENTER),
            )
        } else {
            val pillW = d.dp(58f)
            val pill = Rect(scaled.right - pillW - d.dp(14f), scaled.top + d.dp(14f), scaled.right - d.dp(14f), scaled.top + d.dp(34f))
            p.drawRoundRect(
                pill, Corners.all(pill.height * 0.5f),
                Paint().apply {
                    style = PaintStyle.STROKE
                    strokeWidth = d.dp(1f)
                    color = Argb.withAlpha(pal.accent, 0.5f)
                },
            )
            p.drawText(
                lang.applyTheme, pill.centerX, pill.centerY + d.dp(3.5f),
                d.labelPaint(7.5f, pal.accent, 0.2f, TextAlign.CENTER),
            )
        }
    }

    override fun needsFrame(now: Long): Boolean = super.needsFrame(now)
}
