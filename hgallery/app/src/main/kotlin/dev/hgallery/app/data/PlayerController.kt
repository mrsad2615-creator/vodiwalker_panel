package dev.hgallery.app.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dev.hgallery.core.PlayerPolicy
import dev.hgallery.core.PlayerState
import dev.hgallery.core.Scrub
import kotlinx.coroutines.delay

/**
 * Owns one `ExoPlayer` and the observable state the chrome renders.
 *
 * This exists because keeping the player, its listener, the position poll, the
 * auto-hide timer and the scrub in one composable made a 371-line function with
 * cyclomatic complexity 37 — detekt was right to reject it, and the decomposition
 * is genuinely better code rather than a threshold dodge:
 *
 *  - the player's lifecycle is in one place, so a listener can never outlive its
 *    player;
 *  - the composable becomes pure composition, which is what a composable should
 *    be;
 *  - every rule still lives in `core/PlayerPolicy`, so the tests that matter are
 *    unchanged.
 */
@UnstableApi
@Stable
class PlayerController(
    context: Context,
    startUri: String,
    /** True when this clip must open without sound. See `PlayerPolicy.onArrival`. */
    startMuted: Boolean = false,
) {

    /**
     * True after [release] has run.
     *
     * The surface is a platform view and can be asked to draw after the composition that
     * owned the player is gone. It reads this before touching the player, so a last frame
     * cannot call into a released object.
     */
    var released by mutableStateOf(false)
        private set

    /**
     * One player for the whole screen session.
     *
     * ## Why this must not be recreated per clip
     *
     * `AndroidView` runs its `factory` once and then only `update` on later
     * recompositions. A controller built per URI therefore left `PlayerView` attached to
     * the player of the first clip while a second player, with sound, ran underneath it.
     * The result was audio with no picture, and the platform reports no error for it.
     *
     * Keeping the same player and replacing the media item means the surface never has to
     * be re-attached, so the failure cannot happen. This is why the field is a `val`: a
     * reassignable player is the shape that caused the bug.
     */
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        // Without a declared usage the platform files this stream under "unknown":
        // the volume keys then adjust the ringtone, and unplugging headphones does
        // not pause playback. Movies with sound need the media usage declared.
        setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        setHandleAudioBecomingNoisy(true)
        // Volume is set before `prepare()` and before `playWhenReady`. Setting it after
        // playback starts would leave a window in which a clip the policy says must be
        // silent makes a noise: `prepare()` can reach the first audio frame quickly, and
        // the application would be audible for that window.
        volume = if (startMuted) SILENT else FULL_VOLUME
        setMediaItem(Media3Item.fromUri(startUri))
        prepare()
        playWhenReady = true
    }

    /** The clip this controller is showing. Read by the chrome for its title. */
    var uri: String = startUri
        private set

    /**
     * True while the player is muted.
     *
     * The policy is in `PlayerPolicy`; this is only the state it decides. Muting goes
     * through `Player.volume` rather than `setVolume(0)` on a wrapper so the track is
     * still decoded and a later unmute is instant.
     */
    var muted by mutableStateOf(startMuted)
        private set

    /**
     * What the chrome draws.
     *
     * Seeded as playing because the constructor sets `playWhenReady = true`. Starting
     * from a default that says "paused" meant the first frames of every clip drew a play
     * button over video that was already running, and held the chrome on screen because
     * the auto-hide rule reads `isPlaying`. A listener callback corrects this within a
     * frame either way; the seed only removes the flicker.
     */
    var state by mutableStateOf(PlayerState(isPlaying = true))
        private set

    var positionMs by mutableLongStateOf(0L)
        private set

    var durationMs by mutableLongStateOf(0L)
        private set

    var reachedEnd by mutableStateOf(false)
        private set

    var speed by mutableStateOf(1f)
        private set

    /** Non-null exactly while a finger is on the progress bar. */
    var scrub by mutableStateOf<Scrub?>(null)
        private set

    /** The "+10 s" hint: value in ms, and when it was raised. */
    var hintMs by mutableLongStateOf(0L)
        private set

    var hintAt by mutableLongStateOf(0L)
        private set

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            state = state.copy(isPlaying = isPlaying)
            if (isPlaying) reachedEnd = false
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            state = state.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
            if (playbackState == Player.STATE_ENDED) reachedEnd = true
            if (playbackState == Player.STATE_READY) {
                durationMs = player.duration.coerceAtLeast(0L)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            state = state.copy(hasError = true, isBuffering = false)
        }
    }

    init {
        player.addListener(listener)
    }

    /**
     * Loads another clip on the same player.
     *
     * The listener and the player are kept, so the chrome does not blink and the surface
     * keeps its own reference. Playback position, duration and the ended latch belong to
     * the old clip and are cleared here rather than left to be overwritten whenever the
     * new clip happens to report ready — a stale duration would scale the progress line
     * for the first frames of the new clip.
     */
    fun load(nextUri: String, playWhenReady: Boolean = true, startMuted: Boolean = false) {
        uri = nextUri
        reachedEnd = false
        positionMs = 0L
        durationMs = 0L
        scrub = null
        hintMs = 0L
        // `isPlaying` is seeded from the flag we are about to set, not from a default of
        // false. Resetting to a plain PlayerState left a window in which the policy saw a
        // paused player: the chrome refused to auto-hide, and a play button was drawn
        // over a clip that was already playing.
        state = PlayerState(isPlaying = playWhenReady)
        applyMute(startMuted)
        player.setMediaItem(Media3Item.fromUri(nextUri))
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    /**
     * Turns the sound on or off.
     *
     * Separate from [load] because the user can do it at any time, and because the policy
     * decides the value at the start of a clip while this applies it.
     */
    fun setMuted(value: Boolean) {
        if (muted == value) return
        applyMute(value)
    }

    fun toggleMuted() {
        applyMute(!muted)
    }

    private fun applyMute(value: Boolean) {
        muted = value
        player.volume = if (value) SILENT else FULL_VOLUME
    }

    /** Called from a `DisposableEffect`, never from the composable body. */
    fun release() {
        if (released) return
        released = true
        player.removeListener(listener)
        player.release()
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skip(deltaMs: Long) {
        val clamped = (player.currentPosition + deltaMs).coerceIn(0L, durationMs.coerceAtLeast(0L))
        player.seekTo(clamped)
        positionMs = clamped
        hintMs = deltaMs
        hintAt = System.currentTimeMillis()
    }

    fun replay() {
        player.seekTo(0)
        reachedEnd = false
        player.play()
    }

    /** Called by the screen once the skip hint has been visible long enough. */
    fun clearHint() {
        hintMs = 0L
    }

    /**
     * Applies a playback speed.
     *
     * Named `changeSpeed` rather than `setSpeed` on purpose: `var speed` already
     * generates a `setSpeed(F)V` accessor on the JVM, and two declarations with
     * that signature is a platform clash the compiler rejects.
     */
    fun changeSpeed(value: Float) {
        speed = value
        player.setPlaybackSpeed(value)
    }

    fun beginScrub() {
        scrub = Scrub(
            positionMs = player.currentPosition,
            durationMs = durationMs,
            wasPlayingBefore = player.isPlaying,
        )
        if (player.isPlaying) player.pause()
    }

    fun moveScrub(fraction: Float) {
        val existing = scrub ?: return
        scrub = existing.copy(positionMs = (fraction * durationMs).toLong())
    }

    fun endScrub() {
        val finished = scrub ?: return
        scrub = null
        player.seekTo(finished.positionMs)
        positionMs = finished.positionMs
        if (PlayerPolicy.resumeAfterScrub(finished)) player.play()
    }

    /** Polls the player's position; Media3 has no position callback. */
    suspend fun pollPosition() {
        while (true) {
            if (scrub == null) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                if (durationMs <= 0L) durationMs = player.duration.coerceAtLeast(0L)
            }
            delay(POSITION_POLL_MS)
        }
    }

    private companion object {
        /** 5 Hz, which is what Media3's own controller polls at. */
        const val POSITION_POLL_MS = 200L

        /** `Player.volume` is linear from 0 to 1; these are its two ends. */
        private const val SILENT = 0f
        private const val FULL_VOLUME = 1f
    }
}

/**
 * Binds a [PlayerController] to the composition: creates it once, releases it, and
 * runs the position poll.
 *
 * ## The key is the point
 *
 * The controller is remembered with **no key**, so it lives as long as this composable
 * does, not as long as one URI does. The first version keyed it on `uri`, which built a
 * controller — and therefore an `ExoPlayer` — for every clip, released the previous one,
 * and left the picture view bound to the released instance. The visible result was a
 * clip that played sound with a black rectangle where the picture should be.
 *
 * Changing the clip is now a [PlayerController.load] on the same player, performed by the
 * effect below.
 */
@UnstableApi
@Composable
fun rememberPlayerController(uri: String, startMuted: Boolean = false): PlayerController {
    val context = androidx.compose.ui.platform.LocalContext.current
    val controller = remember { PlayerController(context, uri, startMuted) }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    // The first clip is already prepared by the constructor, so the effect must not load
    // it a second time: a second `prepare()` restarts the buffer for no reason. Only a
    // change of clip goes through `load`.
    var loadedUri by remember { mutableStateOf(uri) }
    LaunchedEffect(uri) {
        if (uri != loadedUri) {
            loadedUri = uri
            controller.load(uri, playWhenReady = true, startMuted = startMuted)
        }
    }

    LaunchedEffect(controller) { controller.pollPosition() }

    return controller
}
