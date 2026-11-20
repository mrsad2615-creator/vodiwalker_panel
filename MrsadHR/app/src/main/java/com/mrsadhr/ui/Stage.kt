package com.mrsadhr.ui

import com.mrsadhr.app.Action
import com.mrsadhr.app.AppController
import com.mrsadhr.core.AppFlags
import com.mrsadhr.core.model.AppState
import com.mrsadhr.core.model.Language
import com.mrsadhr.core.model.Mode
import com.mrsadhr.core.motion.Animated
import com.mrsadhr.core.motion.Easing
import com.mrsadhr.core.motion.Motion
import com.mrsadhr.core.render.Argb
import com.mrsadhr.core.render.Paint
import com.mrsadhr.core.render.Painter
import com.mrsadhr.core.render.Rect
import com.mrsadhr.themes.Palette
import com.mrsadhr.themes.RingStyle
import com.mrsadhr.themes.ThemeId
import com.mrsadhr.themes.ThemeSpec
import com.mrsadhr.themes.Themes
import com.mrsadhr.ui.art.Backgrounds
import com.mrsadhr.ui.screen.CompletionOverlay
import com.mrsadhr.ui.screen.SettingsScreen
import com.mrsadhr.ui.screen.SoundsScreen
import com.mrsadhr.ui.screen.ThemesScreen
import com.mrsadhr.ui.screen.TimerScreen
import com.mrsadhr.ui.widget.Controls
import com.mrsadhr.ui.widget.Icon
import kotlin.math.abs

enum class ScreenId { Timer, Themes, Sounds, Settings }

/**
 * The root of the interface.
 *
 * Owns the theme blend, the screen router with cinematic transitions, the persistent header, the
 * completion overlay and the touch dispatcher. Screens draw themselves entirely through the
 * platform-independent [Painter], which is what makes every screen previewable and testable
 * off-device.
 */
class Stage(
    val controller: AppController,
    val typefaces: Typefaces,
) {

    var state: AppState = controller.state()
        private set

    var width: Float = 1080f
        private set
    var height: Float = 2280f
        private set
    var density: Float = 2.75f
        private set
    var safeTop: Float = 0f
    var safeBottom: Float = 0f

    /** Set from the platform configuration; text scales, layout does not. */
    var fontScale: Float = 1f

    var nowMs: Long = 0L
        private set

    val lang: L get() = L(state.settings.language == Language.Persian)

    // ------------------------------------------------------------- theme blending

    private var fromSpec: ThemeSpec = Themes.spec(state.settings.theme)
    private var toSpec: ThemeSpec = Themes.spec(state.settings.theme)
    private val themeT = Animated(1f, Motion.THEME, Easing.Emphasize)

    val palette: Palette get() = Palette.lerp(fromSpec.palette, toSpec.palette, themeT.value)
    val ringStyle: RingStyle get() = RingStyle.lerp(fromSpec.ringStyle, toSpec.ringStyle, themeT.value)
    val currentTheme: ThemeId get() = toSpec.id
    val themeSpec: ThemeSpec get() = toSpec

    // --------------------------------------------------------------- navigation

    var screen: ScreenId = ScreenId.Timer
        private set
    private var prevScreen: ScreenId = ScreenId.Timer
    private val routeT = Animated(1f, Motion.SCREEN, Easing.Emphasize)
    private var routeDir = 1

    val timerScreen = TimerScreen(this)
    val themesScreen = ThemesScreen(this)
    val soundsScreen = SoundsScreen(this)
    val settingsScreen = SettingsScreen(this)
    val completionOverlay = CompletionOverlay(this)

    private fun screenFor(id: ScreenId): Screen = when (id) {
        ScreenId.Timer -> timerScreen
        ScreenId.Themes -> themesScreen
        ScreenId.Sounds -> soundsScreen
        ScreenId.Settings -> settingsScreen
    }

    // ------------------------------------------------------------------ header

    private val headerHits = HitLayer()
    private val headerAlpha = Animated(1f, Motion.SCREEN, Easing.Standard)
    private val focusT = Animated(0f, Motion.SCREEN, Easing.Emphasize)

    val focusAmount: Float get() = focusT.value

    // ------------------------------------------------------------------- touch

    private var downArea: HitArea? = null
    private var downLayer: HitLayer? = null
    private var downAt = 0L
    private var longFired = false
    private var dragArea: HitArea? = null
    private var dragging = false
    private var downX = 0f
    private var downY = 0f

    init {
        controller.observe { newState ->
            val previousTheme = toSpec.id
            state = newState
            val incoming = Themes.spec(newState.settings.theme)
            if (incoming.id != previousTheme) {
                fromSpec = specAtCurrentBlend()
                toSpec = incoming
                themeT.snap(0f)
                themeT.setTarget(1f, nowMs, Motion.THEME)
                if (newState.settings.reducedMotion) themeT.snap(1f)
            }
            AppFlags.reducedMotion = newState.settings.reducedMotion
        }
    }

    private fun specAtCurrentBlend(): ThemeSpec {
        val t = themeT.value
        if (t >= 0.999f) return toSpec
        return ThemeSpec(
            id = toSpec.id,
            palette = Palette.lerp(fromSpec.palette, toSpec.palette, t),
            ringStyle = RingStyle.lerp(fromSpec.ringStyle, toSpec.ringStyle, t),
            taglineEn = toSpec.taglineEn,
            taglineFa = toSpec.taglineFa,
        )
    }

    fun design(): Design = Design(
        typefaces = typefaces,
        palette = palette,
        ringStyle = ringStyle,
        density = density,
        isPersian = state.settings.language == Language.Persian,
        reducedMotion = state.settings.reducedMotion,
        fontScale = fontScale,
    )

    fun setViewport(widthPx: Float, heightPx: Float, density: Float) {
        this.width = widthPx
        this.height = heightPx
        this.density = density
    }

    fun navigate(id: ScreenId, now: Long) {
        if (id == screen) return
        prevScreen = screen
        routeDir = if (id.ordinal > screen.ordinal) 1 else -1
        screen = id
        if (state.settings.reducedMotion) {
            routeT.snap(1f)
        } else {
            routeT.snap(0f)
            routeT.setTarget(1f, now, Motion.SCREEN)
        }
        if (state.settings.soundEnabled) controller.dispatch(Action.StopPreview)
    }

    fun back(now: Long): Boolean {
        if (screen != ScreenId.Timer) {
            navigate(ScreenId.Timer, now)
            return true
        }
        return screenFor(screen).onBack(now)
    }

    val focusActive: Boolean
        get() = screen == ScreenId.Timer && state.mode == Mode.Timer &&
            state.settings.focusMode && (state.timer.running || state.timer.completed)

    val keepScreenOn: Boolean
        get() = state.settings.keepScreenOn && (state.timer.running || state.stopwatch.running)

    fun dp(v: Float): Float = v * density

    // ------------------------------------------------------------------ frame

    fun update(now: Long) {
        nowMs = now
        // The engines are time driven, so the rendered state is re-read every frame.
        state = controller.state()
        routeT.update(now)
        themeT.update(now)
        focusT.update(now)
        headerAlpha.update(now)

        controller.tick()

        val targetFocus = if (focusActive) 1f else 0f
        if (state.settings.reducedMotion) focusT.snap(targetFocus) else focusT.setTarget(targetFocus, now)
        val headerTarget = if (focusT.value > 0.55f) 0f else 1f
        if (state.settings.reducedMotion) headerAlpha.snap(headerTarget)
        else headerAlpha.setTarget(headerTarget, now, Motion.SCREEN)

        timerScreen.update(now)
        themesScreen.update(now)
        soundsScreen.update(now)
        settingsScreen.update(now)
        completionOverlay.update(now)

        val area = downArea
        if (area != null && !longFired && area.onLongPress != null && now - downAt > 420L) {
            longFired = true
            area.onLongPress?.invoke()
        }
    }

    fun needsFrame(now: Long): Boolean {
        if (state.timer.running || state.stopwatch.running) return true
        if (themeT.isRunning || routeT.isRunning || focusT.isRunning || headerAlpha.isRunning) return true
        if (downArea != null && downArea?.onLongPress != null && !longFired) return true
        if (completionOverlay.needsFrame(now)) return true
        if (screenFor(screen).needsFrame(now)) return true
        if (routeT.value < 0.999f && screenFor(prevScreen).needsFrame(now)) return true
        return false
    }

    fun draw(p: Painter) {
        val now = nowMs
        val d = design()
        drawThemeBackdrop(p, d, now)
        drawContent(p, d, now)
        if (headerAlpha.value > 0.01f) drawHeader(p, d, now)
        completionOverlay.draw(p, d)
        drawThemeSweep(p, d)
    }

    private fun drawThemeBackdrop(p: Painter, d: Design, now: Long) {
        val t = themeT.value
        if (t >= 0.999f) {
            Backgrounds.draw(p, d, now)
        } else {
            val fromDesign = d.withPalette(fromSpec.palette, fromSpec.ringStyle)
            val toDesign = d.withPalette(toSpec.palette, toSpec.ringStyle)
            Backgrounds.draw(p, fromDesign, now, 1f - t)
            Backgrounds.draw(p, toDesign, now, t)
        }
    }

    private fun drawContent(p: Painter, d: Design, now: Long) {
        val area = Rect(0f, 0f, p.width, p.height)
        val t = routeT.value
        if (t < 0.999f && prevScreen != screen) {
            val prev = screenFor(prevScreen)
            p.save()
            p.translate(-routeDir * p.width * 0.28f * t, 0f)
            p.saveLayerAlpha(1f - t)
            p.clipRect(area)
            prev.layout(p, d, now)
            prev.draw(p, d, now)
            p.restore()
            p.restore()
        }
        p.save()
        p.translate(routeDir * p.width * (1f - t), 0f)
        p.clipRect(area)
        val active = screenFor(screen)
        active.layout(p, d, now)
        active.draw(p, d, now)
        p.restore()
    }

    private fun drawHeader(p: Painter, d: Design, now: Long) {
        val alpha = headerAlpha.value
        if (alpha <= 0.01f) {
            headerHits.clear()
            return
        }
        headerHits.clear()
        val pal = d.palette
        p.save()
        p.saveLayerAlpha(alpha)
        val top = safeTop + dp(12f)
        val left = dp(22f)
        val right = p.width - dp(22f)

        p.drawText("MRSADHR", left, top + dp(17f), d.wordmarkPaint(12.5f, pal.textPrimary))
        Controls.sectionCaption(p, d, lang.pick("PRECISION TIMER", "زمان‌سنج دقیق"), left, top + dp(33f))

        val size = dp(40f)
        val gap = dp(9f)
        val entries = listOf(Icon.Palette to ScreenId.Themes, Icon.Sound to ScreenId.Sounds, Icon.Settings to ScreenId.Settings)
        var x = right - size
        for (i in entries.indices.reversed()) {
            val (icon, target) = entries[i]
            val rect = Rect(x, top, x + size, top + size)
            val press = headerHits.press("hdr-$i").also { it.update(now) }
            Controls.iconButton(p, d, rect, icon, press.value, active = screen == target)
            headerHits.add("hdr-$i", rect) { navigate(target, now) }
            x -= size + gap
        }
        p.restore()
        p.restore()
    }

    private fun drawThemeSweep(p: Painter, d: Design) {
        if (!themeT.isRunning) return
        val t = themeT.value
        val sweepX = p.width * (-0.25f + 1.5f * Easing.Standard.transform(t))
        val intensity = 0.055f * (1f - abs(t - 0.45f) * 1.6f).coerceAtLeast(0f)
        if (intensity <= 0.002f) return
        p.drawRect(
            Rect(sweepX - p.width * 0.25f, 0f, sweepX + p.width * 0.25f, p.height),
            Paint().apply {
                shader = p.linearGradient(
                    sweepX - p.width * 0.25f, 0f, sweepX + p.width * 0.25f, 0f,
                    intArrayOf(
                        Argb.withAlpha(d.palette.accentEdge, 0f),
                        Argb.withAlpha(d.palette.accentEdge, intensity),
                        Argb.withAlpha(d.palette.accentEdge, 0f),
                    ),
                    null,
                )
            },
        )
    }

    // ------------------------------------------------------------------ touch

    private fun activeLayers(): List<HitLayer> {
        val out = ArrayList<HitLayer>(3)
        if (completionOverlay.visible) {
            out.add(completionOverlay.hits)
        } else {
            out.add(screenFor(screen).hits)
            if (headerAlpha.value > 0.5f) out.add(headerHits)
        }
        return out
    }

    fun findHit(x: Float, y: Float): Pair<HitLayer, HitArea>? {
        for (layer in activeLayers()) {
            val area = layer.find(x, y)
            if (area != null) return layer to area
        }
        return null
    }

    fun onTouch(event: TouchEvent, now: Long) {
        nowMs = now
        when (event.action) {
            TouchAction.Down -> {
                val hit = findHit(event.x, event.y) ?: return
                downLayer = hit.first
                downArea = hit.second
                downAt = now
                longFired = false
                dragging = false
                downX = event.x
                downY = event.y
                hit.first.press(hit.second.id).onDown(now)
                if (hit.second.onDrag != null) dragArea = hit.second
            }
            TouchAction.Move -> {
                if (dragging) {
                    dragArea?.onDrag?.invoke(event.x, event.y)
                    return
                }
                if (abs(event.y - downY) > dp(9f) || abs(event.x - downX) > dp(9f)) {
                    // A gesture that begins on a button but turns into a swipe should scroll the
                    // surface underneath instead of being swallowed.
                    val area = dragArea ?: findHit(downX, downY)?.second
                    if (area?.onDrag != null) {
                        dragging = true
                        dragArea = area
                        // Seed the gesture with its origin so drag handlers can compute a delta
                        // even though they only ever see the newest position.
                        area.onDrag?.invoke(downX, downY)
                        area.onDrag?.invoke(event.x, event.y)
                    } else {
                        downArea?.let { downLayer?.press(it.id)?.onUp(now) }
                        downArea = null
                    }
                }
            }
            TouchAction.Up -> {
                val area = downArea
                if (area != null) {
                    downLayer?.press(area.id)?.onUp(now)
                    val still = findHit(event.x, event.y)
                    val sameTarget = still?.second?.id == area.id
                    if (!longFired && !dragging && sameTarget) {
                        area.onTap?.invoke()
                    }
                }
                downArea = null
                downLayer = null
                dragArea = null
                dragging = false
            }
            TouchAction.Cancel -> {
                downArea?.let { downLayer?.press(it.id)?.onUp(now) }
                downArea = null
                downLayer = null
                dragArea = null
                dragging = false
            }
        }
    }
}
