package dev.hgallery.core

/**
 * Grid density: how many photographs go in one row.
 *
 * The rule is here, and not in the screen, because a pinch gesture is hard to test and a
 * pure function on numbers is easy to test. The screen only supplies two finger
 * distances; this file decides what that means.
 */
object GridDensity {

    /** Two columns show a photograph large. Six show a month at a glance. */
    const val MIN_COLUMNS = 2
    const val MAX_COLUMNS = 6

    /** The density the app opens at, and the one a new install starts with. */
    const val DEFAULT_COLUMNS = 4

    /**
     * Returns [columns], held inside the supported range.
     *
     * Used on values that arrive from storage as well as from a gesture: a stored value
     * written by an older build must not be able to create a one-column grid.
     */
    fun clamp(columns: Int): Int = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    /**
     * Returns the column count after a pinch, given how far the fingers travelled.
     *
     * [scale] is the current finger distance divided by the starting distance, so a
     * spread is greater than 1 and a pinch is less than 1. Spreading the fingers shows
     * fewer, larger photographs, which is the direction a user expects from every photo
     * app on the platform.
     *
     * The result is rounded, not truncated: at four columns a spread of 1.2 gives a
     * ratio of 3.3, and truncation would make the gesture feel dead for the first part
     * of every pinch.
     */
    fun afterPinch(startColumns: Int, scale: Float): Int {
        if (!scale.isFinite() || scale <= 0f) return clamp(startColumns)
        val scaled = clamp(startColumns) / scale
        return clamp(Math.round(scaled))
    }
}
