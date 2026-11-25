package dev.hgallery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The end-of-clip rule, and the wrap that goes with it.
 *
 * Every test here is a boundary. The interior of the feature is trivial -- "repeat means
 * repeat" -- and the interesting failures are a one-clip queue under repeat-all, a queue
 * whose current id is no longer in it, and a mode name read from storage that this build
 * does not know.
 */
class RepeatPolicyTest {

    private fun video(id: Long) = MediaItem(
        id = id,
        uri = "content://media/video/$id",
        displayName = "clip $id.mp4",
        mimeType = "video/mp4",
        dateTakenMs = id,
        sizeBytes = 1_000L * id,
        durationMs = 30_000L,
        width = 1920,
        height = 1080,
        bucketId = 1,
        bucketName = "Camera",
    )

    private val queue = listOf(video(1), video(2), video(3))

    // ------------------------------------------------------------- the three modes

    @Test
    fun offStopsAtTheEndAndShowsReplay() {
        assertEquals(EndOfClip.DoNothing, RepeatPolicy.onEnd(RepeatMode.OFF, nextInQueue = 3))
    }

    @Test
    fun oneRepeatsTheSameClipEvenWhenAnotherExists() {
        // The next clip is available and must be ignored: that is what "one" means.
        assertEquals(EndOfClip.ReplayThis, RepeatPolicy.onEnd(RepeatMode.ONE, nextInQueue = 3))
    }

    @Test
    fun allAdvancesToTheNextClip() {
        assertEquals(
            EndOfClip.PlayNext(3),
            RepeatPolicy.onEnd(RepeatMode.ALL, nextInQueue = 3),
        )
    }

    @Test
    fun allWrapsAtTheEndOfTheQueue() {
        // The clip after 3 in the queue is 1, and the caller passes that in.
        assertEquals(
            EndOfClip.PlayNext(1),
            RepeatPolicy.onEnd(RepeatMode.ALL, nextInQueue = 1),
        )
    }

    @Test
    fun allOnASingleClipRepeatsItRatherThanDoingNothing() {
        // The user asked to keep playing. Explicitly doing nothing would ignore them, and
        // "next" for a one-clip queue is that same clip, which is a repeat in everything
        // but name.
        assertEquals(EndOfClip.ReplayThis, RepeatPolicy.onEnd(RepeatMode.ALL, nextInQueue = null))
    }

    // ------------------------------------------------------------------ the wrap

    @Test
    fun nextWithWrapReturnsTheFollowingClip() {
        assertEquals(2L, RepeatPolicy.nextWithWrap(queue, 1)?.id)
        assertEquals(3L, RepeatPolicy.nextWithWrap(queue, 2)?.id)
    }

    @Test
    fun nextWithWrapWrapsFromTheLastToTheFirst() {
        assertEquals(1L, RepeatPolicy.nextWithWrap(queue, 3)?.id)
    }

    @Test
    fun nextWithWrapReturnsNullForASingleClipQueue() {
        // A single clip has nowhere to go. Returning the clip itself here would make every
        // caller load the clip that just ended and restart it, which is a decision for
        // RepeatPolicy.onEnd to make with the mode in hand, not for the wrap helper.
        assertNull(RepeatPolicy.nextWithWrap(listOf(video(9)), 9))
    }

    @Test
    fun nextWithWrapReturnsNullWhenTheClipIsGone() {
        // A clip whose file was deleted while it played. Advancing from an unknown
        // position would mean guessing, and the wrong guess reloads a deleted file.
        assertNull(RepeatPolicy.nextWithWrap(queue, 99))
    }

    @Test
    fun nextWithWrapReturnsNullForAnEmptyQueue() {
        assertNull(RepeatPolicy.nextWithWrap(emptyList(), 1))
    }

    // ------------------------------------------------------------- the mode itself

    @Test
    fun theCycleReachesEveryModeAndReturns() {
        assertEquals(RepeatMode.ALL, RepeatMode.OFF.next)
        assertEquals(RepeatMode.ONE, RepeatMode.ALL.next)
        assertEquals(RepeatMode.OFF, RepeatMode.ONE.next)
    }

    @Test
    fun theCycleVisitsEveryModeExactlyOnce() {
        // A cycle that skips a mode makes that mode unreachable from the player button,
        // and the button is the only control a user reaches without opening Settings.
        val seen = mutableListOf(RepeatMode.OFF)
        repeat(RepeatMode.entries.size - 1) { seen += seen.last().next }
        assertEquals(RepeatMode.entries.toSet(), seen.toSet())
        assertEquals(RepeatMode.OFF, seen.last().next)
    }

    @Test
    fun anUnknownStoredModeFallsBackToOff() {
        assertEquals(RepeatMode.OFF, RepeatMode.fromName(null))
        assertEquals(RepeatMode.OFF, RepeatMode.fromName("SOMETIMES"))
        assertEquals(RepeatMode.ALL, RepeatMode.fromName("ALL"))
    }

    @Test
    fun theEnumNamesAreStableBecauseTheyArePersisted() {
        // The stored value is `name`. Renaming a constant silently resets every install
        // that had selected it, so the strings are pinned here on purpose.
        assertEquals(listOf("OFF", "ONE", "ALL"), RepeatMode.entries.map { it.name })
        assertTrue(RepeatMode.entries.all { RepeatMode.fromName(it.name) == it })
    }
}
