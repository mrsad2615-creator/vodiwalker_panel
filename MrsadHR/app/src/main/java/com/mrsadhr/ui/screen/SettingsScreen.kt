package com.mrsadhr.ui.screen

import com.mrsadhr.app.Action
import com.mrsadhr.core.model.Language
import com.mrsadhr.core.motion.Animated
import com.mrsadhr.core.motion.Easing
import com.mrsadhr.core.motion.Motion
import com.mrsadhr.core.render.Argb
import com.mrsadhr.core.render.Corners
import com.mrsadhr.core.render.Painter
import com.mrsadhr.core.render.Rect
import com.mrsadhr.core.render.TextAlign
import com.mrsadhr.sounds.SoundCatalog
import com.mrsadhr.ui.Design
import com.mrsadhr.ui.Weight
import com.mrsadhr.ui.Screen
import com.mrsadhr.ui.ScreenId
import com.mrsadhr.ui.Stage
import com.mrsadhr.ui.art.Surfaces
import com.mrsadhr.ui.widget.Controls
import com.mrsadhr.ui.widget.Icon

/** Minimal, purposeful settings. No feature bloat, every switch does something real. */
class SettingsScreen(stage: Stage) : Screen(stage) {

    private class Row(val id: String, val rect: Rect)

    private val rows = ArrayList<Row>()
    private val toggles = HashMap<String, Animated>()
    private var scroll = 0f
    private var scrollMax = 0f
    private var lastY = Float.NaN
    private var volumeRect: Rect = Rect.Zero

    private fun toggleAnim(key: String) = toggles.getOrPut(key) { Animated(0f, Motion.CHIP, Easing.Emphasize) }

    override fun update(now: Long) {
        hits.pressed.values.forEach { it.update(now) }
        val s = stage.state.settings
        val targets = mapOf(
            "haptics" to s.haptics,
            "sound" to s.soundEnabled,
            "focus" to s.focusMode,
            "motion" to s.reducedMotion,
            "screen" to s.keepScreenOn,
            "notif" to s.notifications,
        )
        for ((key, value) in targets) {
            val anim = toggleAnim(key)
            val target = if (value) 1f else 0f
            if (s.reducedMotion && key != "motion") anim.snap(target) else anim.setTarget(target, now)
            anim.update(now)
        }
    }

    override fun layout(p: Painter, d: Design, now: Long) {
        hits.clear()
        hits.add(
            "settings-scroll",
            Rect(0f, stage.safeTop + d.dp(126f), p.width, p.height),
            onDrag = { _, py -> handleScroll(py) },
        )
        rows.clear()
        val w = p.width
        val lang = stage.lang
        val persian = lang.persian
        val s = stage.state.settings

        var y = stage.safeTop + d.dp(152f) - scroll
        val cardLeft = d.dp(20f)
        val cardRight = w - d.dp(20f)
        val rowH = d.dp(54f)

        fun card(title: String, build: (Float) -> Float): Float {
            val top = y
            y += d.dp(20f)
            var rowY = y
            val end = build(rowY)
            val cardRect = Rect(cardLeft, top, cardRight, end)
            Surfaces.glass(p, d, cardRect, d.dp(24f), 0.55f)
            Controls.sectionCaption(
                p, d, title,
                if (persian) cardRight - d.dp(2f) else cardLeft + d.dp(2f),
                top - d.dp(6f),
                if (persian) TextAlign.RIGHT else TextAlign.LEFT,
            )
            y = end + d.dp(22f)
            return rowY
        }

        // ---- Appearance ----
        card(lang.appearance) { rowY ->
            var ry = rowY + d.dp(8f)
            val themeRect = Rect(cardLeft + d.dp(14f), ry, cardRight - d.dp(14f), ry + rowH)
            rows.add(Row("theme", themeRect))
            hits.add("row-theme", themeRect, onDrag = { _, py -> handleScroll(py) }) {
                stage.navigate(ScreenId.Themes, now)
            }
            ry += rowH
            Controls.divider(p, d, cardLeft + d.dp(16f), cardRight - d.dp(16f), ry)
            val langRect = Rect(cardLeft + d.dp(14f), ry, cardRight - d.dp(14f), ry + rowH)
            rows.add(Row("language", langRect))
            val segW = d.dp(132f)
            val segRect = Rect(langRect.right - segW, langRect.centerY - d.dp(15f), langRect.right, langRect.centerY + d.dp(15f))
            val half = segRect.width * 0.5f
            hits.add("lang-en", Rect(segRect.left, segRect.top, segRect.left + half, segRect.bottom), onDrag = { _, py -> handleScroll(py) }) {
                stage.controller.dispatch(Action.SetLanguage(Language.English))
            }
            hits.add("lang-fa", Rect(segRect.left + half, segRect.top, segRect.right, segRect.bottom), onDrag = { _, py -> handleScroll(py) }) {
                stage.controller.dispatch(Action.SetLanguage(Language.Persian))
            }
            langSegRect = segRect
            ry += rowH + d.dp(8f)
            ry
        }

        // ---- Sound ----
        card(lang.soundsSection) { rowY ->
            var ry = rowY + d.dp(8f)
            val soundToggle = Rect(cardLeft + d.dp(14f), ry, cardRight - d.dp(14f), ry + rowH)
            rows.add(Row("sound", soundToggle))
            hits.add("row-sound", soundToggle, onDrag = { _, py -> handleScroll(py) }) { stage.controller.dispatch(Action.ToggleSound) }
            ry += rowH
            Controls.divider(p, d, cardLeft + d.dp(16f), cardRight - d.dp(16f), ry)
            val soundRow = Rect(cardLeft + d.dp(14f), ry, cardRight - d.dp(14f), ry + rowH)
            rows.add(Row("soundPicker", soundRow))
            hits.add("row-soundpicker", soundRow, onDrag = { _, py -> handleScroll(py) }) { stage.navigate(ScreenId.Sounds, now) }
            ry += rowH
            Controls.divider(p, d, cardLeft + d.dp(16f), cardRight - d.dp(16f), ry)
            val volRow = Rect(cardLeft + d.dp(14f), ry, cardRight - d.dp(14f), ry + rowH + d.dp(6f))
            rows.add(Row("volume", volRow))
            volumeRect = Rect(volRow.left, volRow.bottom - d.dp(26f), volRow.right, volRow.bottom - d.dp(6f))
            hits.add("volume", volumeRect, onDrag = { x, _ ->
                val v = ((x - volumeRect.left) / volumeRect.width).coerceIn(0f, 1f)
                stage.controller.dispatch(Action.SetVolume(v))
            })
            ry += rowH + d.dp(6f)
            ry
        }

        // ---- Behaviour ----
        card(lang.behaviour) { rowY ->
            var ry = rowY + d.dp(8f)
            val entries = listOf(
                Triple("focus", lang.focusMode, lang.focusHint),
                Triple("motion", lang.reducedMotion, lang.reducedMotionHint),
                Triple("screen", lang.keepScreenOn, lang.keepScreenOnHint),
                Triple("haptics", lang.haptics, lang.hapticsHint),
                Triple("notif", lang.notifications, lang.notificationsHint),
            )
            entries.forEachIndexed { index, (id, title, subtitle) ->
                val rect = Rect(cardLeft + d.dp(14f), ry, cardRight - d.dp(14f), ry + rowH)
                rows.add(Row(id, rect))
                hits.add("row-$id", rect, onDrag = { _, py -> handleScroll(py) }) {
                    when (id) {
                        "focus" -> stage.controller.dispatch(Action.ToggleFocusMode)
                        "motion" -> stage.controller.dispatch(Action.ToggleReducedMotion)
                        "screen" -> stage.controller.dispatch(Action.ToggleKeepScreenOn)
                        "haptics" -> stage.controller.dispatch(Action.ToggleHaptics)
                        "notif" -> stage.controller.dispatch(Action.ToggleNotifications)
                    }
                }
                ry += rowH
                if (index != entries.lastIndex) Controls.divider(p, d, cardLeft + d.dp(16f), cardRight - d.dp(16f), ry)
            }
            ry + d.dp(2f)
        }

        // ---- About ----
        card(lang.about) { rowY ->
            var ry = rowY + d.dp(10f)
            val aboutRect = Rect(cardLeft + d.dp(14f), ry, cardRight - d.dp(14f), ry + d.dp(64f))
            rows.add(Row("about", aboutRect))
            ry += d.dp(68f)
            ry
        }

        // Content heights are measured from the unscrolled origin: the layout starts at -scroll,
        // so the offset has to be added back or the maximum would shrink as you scroll.
        scrollMax = ((y + scroll) - (p.height - stage.safeBottom - d.dp(10f))).coerceAtLeast(0f)
        scroll = scroll.coerceIn(0f, scrollMax)
        if (hits.pressed.values.none { it.down }) lastY = Float.NaN
    }

    private var langSegRect: Rect = Rect.Zero

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
        val s = stage.state.settings

        p.drawText(
            lang.settings,
            if (isFa) w - d.dp(24f) else d.dp(24f),
            stage.safeTop + d.dp(96f),
            d.displayPaint(26f, pal.textPrimary, Weight.Bold, if (isFa) TextAlign.RIGHT else TextAlign.LEFT),
        )
        p.drawText(
            lang.builtFor,
            if (isFa) w - d.dp(24f) else d.dp(24f),
            stage.safeTop + d.dp(114f),
            d.bodyPaint(10.5f, Argb.scaleAlpha(pal.textSecondary, 0.9f), if (isFa) TextAlign.RIGHT else TextAlign.LEFT),
        )

        p.save()
        p.clipRect(Rect(0f, stage.safeTop + d.dp(128f), w, p.height))

        for (row in rows) {
            when (row.id) {
                "theme" -> {
                    val theme = stage.themeSpec
                    Controls.settingRow(
                        p, d, row.rect,
                        if (isFa) theme.id.labelFa else theme.id.labelEn,
                        if (isFa) theme.taglineFa else theme.taglineEn,
                        Icon.Palette, chevron = true, press = hits.press("row-theme").value,
                    )
                }
                "language" -> {
                    p.drawText(lang.language, row.rect.left + d.dp(32f), row.rect.centerY + d.dp(4.5f), d.titlePaint(13.5f, pal.textPrimary))
                    Controls.segmented(
                        p, d, langSegRect,
                        listOf("EN", "FA"),
                        if (s.language == Language.Persian) 1 else 0,
                        if (s.language == Language.Persian) 1f else 0f,
                        0f,
                    )
                }
                "sound" -> Controls.settingRow(
                    p, d, row.rect, lang.soundEnabled, null, Icon.Sound,
                    checked = s.soundEnabled, toggleT = toggleAnim("sound").value, press = hits.press("row-sound").value,
                )
                "soundPicker" -> {
                    val sound = SoundCatalog.byId(s.soundId)
                    Controls.settingRow(
                        p, d, row.rect, lang.completionSound,
                        if (isFa) sound.nameFa else sound.nameEn,
                        Icon.Volume, chevron = true, press = hits.press("row-soundpicker").value,
                    )
                }
                "volume" -> {
                    p.drawText(lang.volume, row.rect.left + d.dp(32f), row.rect.top + d.dp(26f), d.titlePaint(13.5f, pal.textPrimary))
                    Controls.slider(p, d, volumeRect, s.soundVolume, hits.pressed["volume"]?.down == true)
                }
                "focus" -> Controls.settingRow(p, d, row.rect, lang.focusMode, lang.focusHint, Icon.Target, checked = s.focusMode, toggleT = toggleAnim("focus").value, press = hits.press("row-focus").value)
                "motion" -> Controls.settingRow(p, d, row.rect, lang.reducedMotion, lang.reducedMotionHint, Icon.Motion, checked = s.reducedMotion, toggleT = toggleAnim("motion").value, press = hits.press("row-motion").value)
                "screen" -> Controls.settingRow(p, d, row.rect, lang.keepScreenOn, lang.keepScreenOnHint, Icon.Screen, checked = s.keepScreenOn, toggleT = toggleAnim("screen").value, press = hits.press("row-screen").value)
                "haptics" -> Controls.settingRow(p, d, row.rect, lang.haptics, lang.hapticsHint, Icon.Vibrate, checked = s.haptics, toggleT = toggleAnim("haptics").value, press = hits.press("row-haptics").value)
                "notif" -> Controls.settingRow(p, d, row.rect, lang.notifications, lang.notificationsHint, Icon.Bell, checked = s.notifications, toggleT = toggleAnim("notif").value, press = hits.press("row-notif").value)
                "about" -> {
                    p.drawText("MrsadHR", row.rect.left + d.dp(4f), row.rect.top + d.dp(26f), d.displayPaint(19f, pal.textPrimary, Weight.Bold))
                    p.drawText(
                        lang.version + " 1.0.0 · " + lang.builtFor,
                        row.rect.left + d.dp(4f), row.rect.top + d.dp(46f),
                        d.bodyPaint(9.5f, Argb.scaleAlpha(pal.textSecondary, 0.9f)),
                    )
                    val dot = Rect(row.rect.right - d.dp(26f), row.rect.top + d.dp(16f), row.rect.right - d.dp(14f), row.rect.top + d.dp(28f))
                    p.drawCircle(dot.centerX, dot.centerY, d.dp(5f), com.mrsadhr.core.render.Paint().apply { color = pal.accent })
                }
            }
        }
        p.restore()
    }

    override fun needsFrame(now: Long): Boolean = super.needsFrame(now) || toggles.values.any { it.isRunning }
}
