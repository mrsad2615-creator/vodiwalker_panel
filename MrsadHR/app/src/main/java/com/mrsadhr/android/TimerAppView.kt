package com.mrsadhr.android

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import com.mrsadhr.ui.Stage
import com.mrsadhr.ui.TouchAction
import com.mrsadhr.ui.TouchEvent

/**
 * The single Android view of the app.
 *
 * It owns the frame loop: frames are requested from the Choreographer only while something is
 * actually moving (a running timer, a transition, a pressed control). When the interface is idle the
 * loop parks and the app costs nothing, which is what makes 60 fps affordable on a countdown that
 * can sit on screen for an hour.
 */
class TimerAppView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    lateinit var stage: Stage
        private set

    private val choreographer = Choreographer.getInstance()
    private var frameScheduled = false
    private var attached = false
    private var visible = true
    private var lastDrawnThemeIsLight: Boolean? = null
    private var painter: AndroidPainter? = null

    var onThemeChanged: ((light: Boolean) -> Unit)? = null

    private val frameCallback = Choreographer.FrameCallback {
        frameScheduled = false
        renderFrame()
    }

    /** Wires the stage. Called once by the activity after the dependency graph exists. */
    fun bind(stage: Stage) {
        this.stage = stage
        stage.setViewport(width.toFloat(), height.toFloat(), resources.displayMetrics.density)
        stage.fontScale = resources.configuration.fontScale
        stage.update(SystemClock.elapsedRealtime())
        requestFrame()
    }

    private fun renderFrame() {
        if (!attached || !visible) return
        val now = SystemClock.elapsedRealtime()
        stage.update(now)
        invalidate()
        syncScreenAwake()
        notifyTheme()
        scheduleNext(now)
    }

    private fun scheduleNext(now: Long) {
        if (!attached || !visible) return
        if (frameScheduled) return
        frameScheduled = true
        if (stage.needsFrame(now)) {
            choreographer.postFrameCallback(frameCallback)
        } else {
            // Idle: wake up twice a second, which is enough for a second tick and keeps the app
            // warm for the next interaction without burning battery.
            choreographer.postFrameCallbackDelayed(frameCallback, IDLE_FRAME_MS)
        }
    }

    fun requestFrame() {
        if (!attached || !visible) return
        if (frameScheduled) return
        frameScheduled = true
        choreographer.postFrameCallback(frameCallback)
    }

    private fun syncScreenAwake() {
        val want = stage.keepScreenOn
        if (keepScreenOn != want) keepScreenOn = want
    }

    private fun notifyTheme() {
        val light = stage.state.settings.theme == com.mrsadhr.themes.ThemeId.Pure
        if (lastDrawnThemeIsLight != light) {
            lastDrawnThemeIsLight = light
            onThemeChanged?.invoke(light)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (::stage.isInitialized) {
            stage.setViewport(w.toFloat(), h.toFloat(), resources.displayMetrics.density)
            stage.fontScale = resources.configuration.fontScale
            requestFrame()
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (::stage.isInitialized) {
            stage.safeTop = insets.systemWindowInsetTop.toFloat()
            stage.safeBottom = insets.systemWindowInsetBottom.toFloat()
            requestFrame()
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        if (::stage.isInitialized) requestFrame()
    }

    override fun onDetachedFromWindow() {
        attached = false
        choreographer.removeFrameCallback(frameCallback)
        frameScheduled = false
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        visible = visibility == VISIBLE
        if (visible && attached) {
            if (::stage.isInitialized) {
                stage.update(SystemClock.elapsedRealtime())
                requestFrame()
            }
        } else {
            choreographer.removeFrameCallback(frameCallback)
            frameScheduled = false
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!::stage.isInitialized) return
        val p = painter ?: AndroidPainter(canvas, width.toFloat(), height.toFloat()).also { painter = it }
        p.bind(canvas, width.toFloat(), height.toFloat())
        stage.draw(p)
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!::stage.isInitialized) return false
        val now = SystemClock.elapsedRealtime()
        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> TouchAction.Down
            MotionEvent.ACTION_MOVE -> TouchAction.Move
            MotionEvent.ACTION_UP -> TouchAction.Up
            MotionEvent.ACTION_CANCEL -> TouchAction.Cancel
            else -> return false
        }
        stage.onTouch(TouchEvent(event.x, event.y, action), now)
        if (action == TouchAction.Up) performClick()
        invalidate()
        requestFrame()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        const val IDLE_FRAME_MS = 450L
    }
}
