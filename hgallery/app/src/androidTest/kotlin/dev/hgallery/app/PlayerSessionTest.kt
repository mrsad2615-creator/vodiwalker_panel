package dev.hgallery.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.hgallery.app.data.PlayerController
import dev.hgallery.core.PlayerPolicy
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The player session, on a device.
 *
 * ## What this test can and cannot do
 *
 * The emulator in CI has no media files and no audio sink. So this test does not
 * play a clip and does not listen to one. It asserts the properties that made
 * defect A possible, and those properties do not need a decoder to be wrong:
 *
 *  - a clip change must keep the same `ExoPlayer` instance;
 *  - the mute state must follow the entry path, and so must the player volume;
 *  - `load()` must clear the state that belongs to the previous clip;
 *  - release must be final.
 *
 * A missing file makes Media3 report an error. That is acceptable here. The
 * player object exists before any file is opened, the volume is a property of
 * the player, and identity is identity. The defect lived in exactly that layer.
 *
 * The audible result still needs a person with a phone. This test narrows that
 * gap. It does not close it, and no document here says that it does.
 *
 * ## Why every call runs on the main thread
 *
 * `ExoPlayer.Builder(...).build()` needs a thread with a `Looper`, and the
 * instrumentation thread has none. `PlayerController` also writes Compose
 * snapshot state, which is not safe to write from an arbitrary thread. Building
 * and driving the controller on the main thread is therefore the only correct
 * arrangement, and `runOnMainSync` is how this test gets there.
 */
@RunWith(AndroidJUnit4::class)
class PlayerSessionTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    /** A path that is a valid Uri and absent from the filesystem. */
    private val missingClip = "file:///android_asset/no-such-clip-1.mp4"
    private val missingClip2 = "file:///android_asset/no-such-clip-2.mp4"

    private var controller: PlayerController? = null

    /** Builds a controller on the main thread and returns it. */
    private fun controller(startUri: String, startMuted: Boolean = false): PlayerController {
        lateinit var built: PlayerController
        instrumentation.runOnMainSync {
            built = PlayerController(context, startUri, startMuted)
        }
        controller = built
        return built
    }

    /** Runs [block] on the main thread, which is where the player must be touched. */
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    @After
    fun releaseController() {
        controller?.let { subject -> onMain { subject.release() } }
        controller = null
    }

    @Test
    fun aClipChangeKeepsTheSamePlayer() {
        // This is the regression. The old code keyed the controller on the URI, so a
        // second clip produced a second player. The surface kept the first one, which had
        // been released, and drew nothing while the second one made sound.
        val subject = controller(missingClip)
        val first = subject.player

        onMain { subject.load(missingClip2) }

        assertSame(
            "A clip change must not replace the player. A new player here is the defect.",
            first,
            subject.player,
        )
    }

    @Test
    fun aClipChangeUpdatesTheUri() {
        val subject = controller(missingClip)
        onMain { subject.load(missingClip2) }
        assertTrue(subject.uri.endsWith("no-such-clip-2.mp4"))
    }

    @Test
    fun aDirectOpenStartsUnmuted() {
        val arrival = PlayerPolicy.onArrival(PlayerPolicy.Entry.DIRECT)
        val subject = controller(missingClip, startMuted = arrival.muted)

        assertFalse("A clip the user tapped must make sound.", subject.muted)
        assertTrue("Volume must be full, not only the flag.", subject.player.volume > 0f)
    }

    @Test
    fun aSwipedOntoClipStartsMutedBeforeAnyFrameIsReady() {
        // The order matters. Volume is set before prepare() and before playWhenReady, so
        // there is no window in which the clip is audible while the surface attaches.
        val arrival = PlayerPolicy.onArrival(PlayerPolicy.Entry.SWIPE)
        val subject = controller(missingClip, startMuted = arrival.muted)

        assertTrue("A clip reached by swiping must not make sound.", subject.muted)
        assertTrue("Volume must be zero, not only the flag.", subject.player.volume == 0f)
    }

    @Test
    fun loadAppliesTheMuteStateOfTheNewClip() {
        // A clip change carries its own arrival policy. Without this, a muted clip that
        // followed an unmuted one would keep the old volume.
        val subject = controller(missingClip, startMuted = false)
        onMain { subject.load(missingClip2, startMuted = true) }

        assertTrue("The new clip arrived by swipe, so it must be muted.", subject.muted)
        assertTrue(subject.player.volume == 0f)
    }

    @Test
    fun unmutingIsAvailableImmediatelyAfterAMutedArrival() {
        // The audio rule is only honest if the control it promises works. This is the
        // manual unmute path, with no file and no decoder involved.
        val arrival = PlayerPolicy.onArrival(PlayerPolicy.Entry.SWIPE)
        val subject = controller(missingClip, startMuted = arrival.muted)

        onMain { subject.toggleMuted() }

        assertFalse(subject.muted)
        assertTrue("Unmuting must restore full volume.", subject.player.volume > 0f)
    }

    @Test
    fun mutingAndUnmutingRoundTrips() {
        val subject = controller(missingClip)
        onMain { repeat(4) { subject.toggleMuted() } }
        assertFalse("Four toggles are a round trip, so the state must be unmuted.", subject.muted)
        assertTrue(subject.player.volume > 0f)
    }

    @Test
    fun loadClearsTheStateThatBelongsToThePreviousClip() {
        // A stale position or duration scales the progress line for the first frames of
        // the new clip, so both are cleared rather than left to be overwritten later.
        val subject = controller(missingClip)
        onMain { subject.load(missingClip2) }

        assertTrue("Position must start at zero for a new clip.", subject.positionMs == 0L)
        assertTrue("Duration must be cleared until the new clip reports it.", subject.durationMs == 0L)
        assertFalse("The ended latch belongs to the old clip.", subject.reachedEnd)
        assertTrue("A new clip is not an error yet.", !subject.state.hasError)
    }

    @Test
    fun aReleasedControllerIsMarkedReleasedAndStaysMarked() {
        // The surface reads this flag before it touches the player. It must never go back
        // to false, or a last frame could call into a released player.
        val subject = controller(missingClip)
        assertFalse(subject.released)

        onMain { subject.release() }
        assertTrue(subject.released)

        onMain { subject.release() }
        assertTrue("A second release must not change the flag.", subject.released)
    }
}
