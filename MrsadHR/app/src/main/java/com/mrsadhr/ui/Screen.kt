package com.mrsadhr.ui

import com.mrsadhr.core.render.Painter
import com.mrsadhr.core.render.Rect

/**
 * A screen is a stateless-ish painter with an explicit layout pass. Layout runs every frame and
 * republishes hit areas, so animation, scrolling and rotation are handled by construction.
 */
abstract class Screen(val stage: Stage) {
    val hits = HitLayer()

    open fun update(now: Long) {}

    abstract fun layout(p: Painter, d: Design, now: Long)

    abstract fun draw(p: Painter, d: Design, now: Long)

    open fun onBack(now: Long): Boolean = false

    open fun needsFrame(now: Long): Boolean = hits.anyAnimating(now)
}
