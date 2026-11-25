package dev.hgallery.core

/**
 * What the player does when a clip reaches its end.
 *
 * ## Where this comes from, and how much to trust it
 *
 * `UNCONFIRMED`: this is modelled on MX Player's transport from memory, not from its
 * code or its binary. Play and the store are not reachable from this environment, so no
 * line below is evidence about that application. The behaviour is described here as
 * this project's own decision, made with that player as the reference in mind. Where a
 * choice was a guess, the entry says so.
 *
 * ## The modes
 *
 * - [OFF] stops at the end. The player shows replay. This is the product's existing
 *   behaviour and it stays the default, because a video that starts itself again is a
 *   video the user cannot leave.
 * - [ONE] repeats the clip. Playback returns to zero and continues without the user
 *   touching anything.
 * - [ALL] advances to the next clip in the queue, and wraps from the last to the first.
 *   It is the mode that makes a filmstrip worth having: a queue that stops at the end
 *   is a queue the user has to babysit.
 *
 * An earlier version of the queue deliberately refused to wrap ([VideoQueue.next]
 * returns null at the end) on the grounds that wrapping looks like ignoring intent. The
 * decision is reversible only by a mode the user selected, which is why wrapping lives
 * here and not in the queue: the queue still answers "what is after this one", and the
 * repeat mode answers "and should we go there".
 *
 * ## Why a state machine and not three booleans
 *
 * "repeat one" and "repeat all" are mutually exclusive, and off is neither. Three
 * booleans admit four of their eight combinations being nonsense. A single enum makes
 * the nonsense unrepresentable, and it is the value that is persisted.
 */
enum class RepeatMode {
    OFF,
    ONE,
    ALL,
    ;

    /**
     * The next mode in the cycle that the single player control steps through.
     *
     * The order is off, all, one. All comes before one because the common case while
     * watching a series is "keep going", and one tap should reach it.
     */
    val next: RepeatMode
        get() = when (this) {
            OFF -> ALL
            ALL -> ONE
            ONE -> OFF
        }

    companion object {
        /**
         * Reads a persisted name.
         *
         * Anything unknown falls back to [OFF]. A stored value that this build does not
         * recognise is not a reason to repeat a clip the user never asked to repeat.
         */
        fun fromName(name: String?): RepeatMode =
            entries.firstOrNull { it.name == name } ?: OFF
    }
}

/**
 * What to do when playback reaches the end of a clip.
 *
 * One of three things happens, and the difference between them is the whole feature:
 *
 * - [DoNothing] — leave the ended latch alone and let the chrome show replay.
 * - [ReplayThis] — the same clip starts again from zero.
 * - [PlayNext] — another clip is loaded. [PlayNext.id] is the one to load, and it is
 *   resolved here rather than by the caller so that the wrap rule has exactly one
 *   implementation.
 *
 * ## Why the queue is a parameter and not a field
 *
 * The player is the only thing that knows which clip is on screen, and it changes
 * without the repeat mode changing. Passing the queue in makes this a pure function of
 * (mode, queue, current), which is the form a test can pin down in one line.
 */
sealed interface EndOfClip {
    data object DoNothing : EndOfClip

    data object ReplayThis : EndOfClip

    /** Load this clip. Never the clip that just ended. */
    data class PlayNext(val id: Long) : EndOfClip
}

/**
 * The end-of-clip rule.
 *
 * [nextInQueue] is passed in rather than computed here so that "next" keeps meaning
 * what [VideoQueue.next] says it means. For [RepeatMode.ALL] the caller passes the
 * wrapper: the clip after this one, or the first clip of the queue when this one is
 * last. Passing the raw `VideoQueue.next` result for [RepeatMode.ONE] would be wrong,
 * which is why the mode decides before the queue is consulted at all.
 */
object RepeatPolicy {

    /**
     * @param mode the user's choice.
     * @param nextInQueue the clip to advance to, in queue order, or the first clip when
     *   the current one is last, or null when there is nowhere to go. The wrap is the
     *   caller's job because only the caller holds the whole queue; see
     *   [nextWithWrap], which is where that rule is written.
     *
     * There is no `currentId` parameter. An earlier version took one and never read it:
     * the mode decides what happens before the identity of the clip can matter, and
     * detekt was right that a parameter carrying no information is one a reader will
     * assume is doing something.
     */
    fun onEnd(mode: RepeatMode, nextInQueue: Long?): EndOfClip =
        when (mode) {
            RepeatMode.OFF -> EndOfClip.DoNothing
            RepeatMode.ONE -> EndOfClip.ReplayThis
            // A one-clip queue under repeat-all has nowhere to go but back to itself.
            // Replaying is the honest answer; doing nothing would silently ignore the
            // mode the user selected.
            RepeatMode.ALL -> nextInQueue?.let { EndOfClip.PlayNext(it) } ?: EndOfClip.ReplayThis
        }

    /**
     * The clip that follows [currentId] with wrap-around, or null when there is nothing
     * to advance to.
     *
     * Separate from [onEnd] so that the queue rule can be tested without a repeat mode,
     * and so that the wrap is written once. Returns null for an empty queue, for a queue
     * holding only [currentId], and for an id that is not in the queue at all -- a clip
     * whose file was deleted while it played.
     */
    fun nextWithWrap(queue: List<MediaItem>, currentId: Long): MediaItem? {
        if (queue.size < 2) return null
        val index = queue.indexOfFirst { it.id == currentId }
        if (index < 0) return null
        return queue[(index + 1) % queue.size]
    }
}
