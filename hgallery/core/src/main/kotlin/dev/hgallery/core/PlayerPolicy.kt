package dev.hgallery.core

/**
 * The rules of the video player's transport, written as a pure state machine.
 *
 * Everything here is the part that is easy to get subtly wrong and impossible to
 * unit-test once it lives inside a composable:
 *
 *  - a scrub must *not* be fought by the position updates still arriving from the
 *    player, or the thumb jumps back under the user's finger;
 *  - releasing a scrub must resume playback only if it was playing beforehand,
 *    otherwise pausing to seek silently starts the video;
 *  - "ended" must be distinguishable from "paused at the end", because one shows
 *    a replay button and the other shows play;
 *  - the chrome must not auto-hide while scrubbing, while paused, or while the
 *    speed menu is open.
 *
 * The Android layer only reports facts into this machine and reacts to its state.
 */
data class PlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    /** True when the prepared media has an audio track; volume controls hide when not. */
    val hasAudio: Boolean = true,
    /** True when the source reports an error; the UI shows a retry affordance. */
    val hasError: Boolean = false,
) {
    val isPaused: Boolean get() = !isPlaying && !hasError
}

/**
 * A drag along the progress bar.
 *
 * [wasPlayingBefore] is captured at the moment the finger went down, because that
 * is the only instant at which the intent is still knowable.
 */
data class Scrub(
    val positionMs: Long,
    val durationMs: Long,
    val wasPlayingBefore: Boolean,
) {
    /** 0f..1f, guarded against a zero or unknown duration. */
    val fraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** What the player chrome should draw right now. */
data class PlayerChrome(
    /** The full bar set, or just the "show controls" state. */
    val visible: Boolean,
    /** Position to draw: the scrub position while dragging, the player's otherwise. */
    val displayPositionMs: Long,
    val displayFraction: Float,
    /** True while the user's finger is down on the bar. */
    val scrubbing: Boolean,
    val showReplay: Boolean,
    val showSpinner: Boolean,
)

/**
 * Auto-hide policy.
 *
 * A player that hides its controls while the user is reading the timecode is
 * annoying; a player whose controls never hide is unusable for watching. The rule
 * is therefore: hide only during uninterrupted playback, and only after
 * [CHROME_TIMEOUT_MS].
 */
object PlayerPolicy {

    const val CHROME_TIMEOUT_MS = 3_000L

    /** Speeds offered by the menu, in the order they are listed. */
    val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    /** Double-tap step, and the step of the +/-10 s buttons. */
    const val SKIP_SMALL_MS = 10_000L

    /** Horizontal drag on the video surface: how many ms per dp of travel. */
    const val SEEK_MS_PER_DP = 250f

    /** The shortest a seek can be and still be worth applying. */
    const val MIN_SEEK_DELTA_MS = 500L

    private const val MS_PER_SECOND = 1000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3600L

    /**
     * Speeds are rounded to this many steps per unit before being labelled, so
     * binary float noise (0.1f + 0.2f) cannot leak into "0.3x".
     */
    private const val SPEED_ROUNDING = 100f

    /**
     * How a clip came to be on screen.
     *
     * The two paths are not equivalent to a user. Tapping a video tile is a request to
     * watch it; arriving at it by swiping through the timeline is not.
     */
    enum class Entry {
        /** The user chose this clip: they tapped its tile or its strip cell. */
        DIRECT,

        /** The user swiped through the timeline and this clip happened to be next. */
        SWIPE,
    }

    /**
     * How a clip must behave at the moment the viewer arrives at it.
     *
     * @property muted true when the clip must make no sound until the user asks for it
     * @property autoplay true when it must start moving without a tap
     */
    data class Arrival(val muted: Boolean, val autoplay: Boolean)

    /**
     * The arrival policy for a clip, as one answer rather than two independent rules.
     *
     * Both paths autoplay. A clip that sits at frame zero waiting for a tap reads as a
     * still photograph, and the user has already asked to see it — by tapping its tile, or
     * by swiping onto it. What differs is sound: only a deliberate choice is allowed to
     * make noise.
     *
     * Written as one function returning both values because they are decided together.
     * Two separate functions made it possible to change one and forget the other, and the
     * pair that results is not a policy anyone chose.
     */
    fun onArrival(entry: Entry): Arrival = Arrival(muted = entry == Entry.SWIPE, autoplay = true)

    fun shouldAutoHide(
        state: PlayerState,
        scrubbing: Boolean,
        chromePinned: Boolean,
        menuOpen: Boolean,
    ): Boolean = state.isPlaying &&
        !state.isBuffering &&
        !state.hasError &&
        !scrubbing &&
        !chromePinned &&
        !menuOpen

    /**
     * The chrome for a given instant.
     *
     * [positionMs] is the player's own position; it is *ignored* while [scrub] is
     * non-null, which is the whole point — otherwise the position updates that
     * arrive during a drag pull the thumb backwards.
     */
    fun chromeFor(
        state: PlayerState,
        positionMs: Long,
        durationMs: Long,
        scrub: Scrub?,
        visible: Boolean,
        reachedEnd: Boolean,
    ): PlayerChrome {
        val safeDuration = durationMs.coerceAtLeast(0L)
        val effective = scrub?.positionMs ?: positionMs.coerceIn(0L, safeDuration.coerceAtLeast(0L))
        val fraction = if (safeDuration > 0L) {
            (effective.toFloat() / safeDuration).coerceIn(0f, 1f)
        } else {
            0f
        }
        return PlayerChrome(
            // While scrubbing the bar must stay on screen even if the auto-hide
            // timer fired a moment ago.
            visible = visible || scrub != null,
            displayPositionMs = effective,
            displayFraction = fraction,
            scrubbing = scrub != null,
            showReplay = reachedEnd && !state.isPlaying,
            showSpinner = state.isBuffering && !state.hasError,
        )
    }

    /** A scrub was released: what should the player do? */
    fun resumeAfterScrub(scrub: Scrub): Boolean = scrub.wasPlayingBefore

    /**
     * Applies a horizontal drag on the video surface.
     *
     * The result is clamped to the media, and a drag shorter than
     * [MIN_SEEK_DELTA_MS] is treated as a tap rather than a tiny seek.
     */
    fun seekByDrag(startMs: Long, deltaDp: Float, durationMs: Long): Long? {
        if (durationMs <= 0L) return null
        val deltaMs = (deltaDp * SEEK_MS_PER_DP).toLong()
        if (kotlin.math.abs(deltaMs) < MIN_SEEK_DELTA_MS) return null
        return (startMs + deltaMs).coerceIn(0L, durationMs)
    }

    /** Formats a position for the timecode: `m:ss`, or `h:mm:ss` past an hour. */
    fun timecode(ms: Long): String {
        val safe = if (ms < 0L) 0L else ms
        val totalSeconds = safe / MS_PER_SECOND
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        val minutes = (totalSeconds / SECONDS_PER_MINUTE) % SECONDS_PER_MINUTE
        val hours = totalSeconds / SECONDS_PER_HOUR
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    /** The label for a speed: `1x`, `1.5x`, never `1.0x`. */
    fun speedLabel(speed: Float): String {
        val rounded = kotlin.math.round(speed * SPEED_ROUNDING) / SPEED_ROUNDING
        return if (rounded == rounded.toInt().toFloat()) {
            "${rounded.toInt()}x"
        } else {
            "${rounded.toString().trimEnd('0').trimEnd('.')}x"
        }
    }
}
