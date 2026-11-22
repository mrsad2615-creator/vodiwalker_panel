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
class PlayerController(context: Context, private val uri: String) {

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
        setMediaItem(Media3Item.fromUri(uri))
        prepare()
        playWhenReady = true
    }

    var state by mutableStateOf(PlayerState())
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

    /** Called from a `DisposableEffect`, never from the composable body. */
    fun release() {
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
    }
}

/**
 * Binds a [PlayerController] to the composition: creates it, releases it, and
 * runs the position poll. Nothing else is done here on purpose.
 */
@UnstableApi
@Composable
fun rememberPlayerController(uri: String): PlayerController {
    val context = androidx.compose.ui.platform.LocalContext.current
    val controller = remember(uri) { PlayerController(context, uri) }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    LaunchedEffect(controller) { controller.pollPosition() }

    return controller
}
