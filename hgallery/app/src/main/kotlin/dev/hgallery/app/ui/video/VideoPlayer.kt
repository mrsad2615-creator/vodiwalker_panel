package dev.hgallery.app.ui.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.hgallery.app.R
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.ui.components.HgIconButton
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.theme.PlayerIndicatorLabel
import dev.hgallery.app.ui.theme.TimecodeLabel
import dev.hgallery.core.MediaItem
import dev.hgallery.core.PlayerPolicy
import dev.hgallery.core.PlayerState
import dev.hgallery.core.Scrub
import kotlinx.coroutines.delay

/**
 * Full-screen video player.
 *
 * ## Why a custom surface and not Media3's controller
 *
 * `PlayerView` ships a controller whose layout, icon weights and timings are
 * Google's, not Huawei's. The reference's player is a full-bleed black surface
 * with a slim translucent top row, a centre transport cluster and a single
 * progress line whose thumb grows while held. Configuring Media3's controller to
 * look like that is not possible; the surface is therefore an `AndroidView`
 * wrapping a bare `AspectRatioFrameLayout` + `PlayerView` with its own controller
 * turned off, and every control here is drawn from this project's design system.
 *
 * ## Gestures
 *
 *  - tap once: toggle the chrome (never pauses, never closes);
 *  - double tap left / right: back or forward 10 s, with an on-screen indicator;
 *  - horizontal drag anywhere: seek, applied on release;
 *  - the progress bar: a real scrub, with the thumb growing and the timecode
 *    following the finger.
 *
 * All of that decision logic lives in `PlayerPolicy` in the pure module and is
 * unit-tested there; this file only reports facts into it.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    container: AppContainer,
    item: MediaItem,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    var state by remember { mutableStateOf(PlayerState()) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var reachedEnd by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    var chromePinned by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var scrub by remember { mutableStateOf<Scrub?>(null) }
    // "Fill screen" toggles crop-to-fill versus letterbox. Held here rather than
    // in a top-level variable: a mutable global would survive the composition and
    // leak between videos.
    var fillScreen by remember { mutableStateOf(false) }

    // The skip indicator ("+10 s") that flashes after a double tap.
    var skipHintMs by remember { mutableLongStateOf(0L) }
    var skipHintAt by remember { mutableLongStateOf(0L) }

    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3Item.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }

    // Listener detached before release, otherwise a callback can arrive on a
    // player that is already gone.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state = state.copy(isPlaying = isPlaying)
                if (isPlaying) reachedEnd = false
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                state = state.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                )
                if (playbackState == Player.STATE_ENDED) {
                    reachedEnd = true
                    chromeVisible = true
                }
                if (playbackState == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                state = state.copy(hasError = true, isBuffering = false)
                chromeVisible = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Position is polled on the composition clock rather than pushed by the
    // player: Media3 has no position listener, and polling at 5 Hz is what its
    // own controller does.
    LaunchedEffect(player) {
        while (true) {
            if (scrub == null) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                if (durationMs <= 0L) durationMs = player.duration.coerceAtLeast(0L)
            }
            delay(200)
        }
    }

    // Auto-hide, restarting whenever the chrome is shown again.
    LaunchedEffect(chromeVisible, state, scrub, chromePinned, speedMenuOpen) {
        if (chromeVisible &&
            PlayerPolicy.shouldAutoHide(state, scrub != null, chromePinned, speedMenuOpen)
        ) {
            delay(PlayerPolicy.CHROME_TIMEOUT_MS)
            // Re-check after the delay: the state that started the timer may be
            // long gone, and hiding the bar over a paused video is a defect.
            if (PlayerPolicy.shouldAutoHide(state, scrub != null, chromePinned, speedMenuOpen)) {
                chromeVisible = false
            }
        }
    }

    // Clear the skip hint after it has been visible long enough to read.
    LaunchedEffect(skipHintAt) {
        if (skipHintAt != 0L) {
            delay(700)
            skipHintMs = 0L
        }
    }

    fun skip(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceIn(0L, durationMs.coerceAtLeast(0L))
        player.seekTo(target)
        positionMs = target
        skipHintMs = deltaMs
        skipHintAt = System.currentTimeMillis()
        chromeVisible = true
    }

    val chrome = PlayerPolicy.chromeFor(
        state = state,
        positionMs = positionMs,
        durationMs = durationMs,
        scrub = scrub,
        visible = chromeVisible,
        reachedEnd = reachedEnd,
    )

    Box(
        modifier = Modifier
            .testTag(HgTestTags.VIDEO_PLAYER)
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // ---- the surface -------------------------------------------------
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = { view ->
                view.resizeMode = if (fillScreen) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier
                .fillMaxSize()
                // Tap toggles the chrome; double tap on a side seeks.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            chromeVisible = !chromeVisible
                            chromePinned = chromeVisible
                        },
                        onDoubleTap = { offset ->
                            when {
                                offset.x < size.width / 3f -> skip(-PlayerPolicy.SKIP_SMALL_MS)
                                offset.x > size.width * 2f / 3f -> skip(PlayerPolicy.SKIP_SMALL_MS)
                                else -> {
                                    if (player.isPlaying) player.pause() else player.play()
                                }
                            }
                        },
                    )
                }
                // Horizontal drag = seek, committed on release.
                .pointerInput(Unit) {
                    var accumulatedDp = 0f
                    var startMs = 0L
                    var wasPlaying = false
                    detectHorizontalDragGestures(
                        onDragStart = {
                            accumulatedDp = 0f
                            startMs = player.currentPosition
                            wasPlaying = player.isPlaying
                        },
                        onDragEnd = {
                            val target = PlayerPolicy.seekByDrag(startMs, accumulatedDp, durationMs)
                            if (target != null) {
                                player.seekTo(target)
                                positionMs = target
                                if (wasPlaying) player.play()
                            }
                            accumulatedDp = 0f
                        },
                        onDragCancel = { accumulatedDp = 0f },
                    ) { change, dragAmount ->
                        change.consume()
                        // px → dp so the constant is density-independent.
                        accumulatedDp += dragAmount / density
                    }
                },
        )

        // ---- buffering ---------------------------------------------------
        if (chrome.showSpinner) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(38.dp),
            )
        }

        // ---- error -------------------------------------------------------
        if (state.hasError) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.video_cannot_play),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.video_cannot_play_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }

        // ---- skip hint ---------------------------------------------------
        AnimatedVisibility(
            visible = skipHintMs != 0L,
            enter = fadeIn(tween(90)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(
                text = if (skipHintMs >= 0) {
                    "+${PlayerPolicy.timecode(skipHintMs)}"
                } else {
                    "-${PlayerPolicy.timecode(-skipHintMs)}"
                },
                style = PlayerIndicatorLabel,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }

        // ---- centre transport (only when paused or at the end) -----------
        AnimatedVisibility(
            visible = chrome.visible && (chrome.showReplay || (!state.isPlaying && !state.hasError)),
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.42f))
                    .pointerInput(reachedEnd) {
                        detectTapGestures(
                            onTap = {
                                if (reachedEnd) {
                                    player.seekTo(0)
                                    reachedEnd = false
                                }
                                player.play()
                                chromeVisible = true
                            },
                        )
                    }
                    .testTag(HgTestTags.VIDEO_CENTRE_BUTTON),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (chrome.showReplay) HgIcons.Restore else HgIcons.Play,
                    contentDescription = stringResource(
                        if (chrome.showReplay) R.string.video_replay else R.string.video_play,
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        // ---- top bar -----------------------------------------------------
        AnimatedVisibility(
            visible = chrome.visible,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.40f))
                    .statusBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HgIconButton(HgIcons.Close, stringResource(R.string.close), onClose, tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    HgIconButton(
                        icon = HgIcons.Rotate,
                        contentDescription = stringResource(R.string.video_rotate),
                        onClick = {
                            activity?.requestedOrientation =
                                if (activity.requestedOrientation ==
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                ) {
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                }
                        },
                        tint = Color.White,
                    )
                }
            }
        }

        // ---- bottom bar --------------------------------------------------
        AnimatedVisibility(
            visible = chrome.visible,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.40f))
                    .navigationBarsPadding()
                    .padding(bottom = 6.dp),
            ) {
                // Progress: a 3 dp line whose thumb is the finger's handle.
                VideoProgressBar(
                    fraction = chrome.displayFraction,
                    scrubbing = chrome.scrubbing,
                    onScrubStart = {
                        scrub = Scrub(
                            positionMs = player.currentPosition,
                            durationMs = durationMs,
                            wasPlayingBefore = player.isPlaying,
                        )
                        if (player.isPlaying) player.pause()
                    },
                    onScrubMove = { fraction ->
                        val existing = scrub ?: return@VideoProgressBar
                        scrub = existing.copy(positionMs = (fraction * durationMs).toLong())
                    },
                    onScrubEnd = {
                        val finished = scrub
                        scrub = null
                        if (finished != null) {
                            player.seekTo(finished.positionMs)
                            positionMs = finished.positionMs
                            if (PlayerPolicy.resumeAfterScrub(finished)) player.play()
                        }
                    },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HgIconButton(
                        icon = if (state.isPlaying) HgIcons.Pause else HgIcons.Play,
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.video_pause else R.string.video_play,
                        ),
                        onClick = {
                            if (state.isPlaying) player.pause() else player.play()
                            chromeVisible = true
                        },
                        tint = Color.White,
                        modifier = Modifier.testTag(HgTestTags.VIDEO_PLAY_PAUSE),
                    )

                    Text(
                        text = "${PlayerPolicy.timecode(chrome.displayPositionMs)} / " +
                            PlayerPolicy.timecode(durationMs),
                        style = TimecodeLabel,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .testTag(HgTestTags.VIDEO_TIMECODE),
                    )

                    Spacer(Modifier.weight(1f))

                    // Speed. A plain cycling control would hide which speeds
                    // exist; the sheet lists them, which is what a user expects
                    // from a player that advertises speed control at all.
                    Box {
                        Text(
                            text = PlayerPolicy.speedLabel(speed),
                            style = TimecodeLabel,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.14f))
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { speedMenuOpen = !speedMenuOpen })
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                                .testTag(HgTestTags.VIDEO_SPEED),
                        )

                        if (speedMenuOpen) {
                            SpeedMenu(
                                current = speed,
                                onPick = {
                                    speed = it
                                    player.setPlaybackSpeed(it)
                                    speedMenuOpen = false
                                    chromeVisible = true
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(bottom = 44.dp),
                            )
                        }
                    }

                    HgIconButton(
                        icon = HgIcons.GridDay,
                        contentDescription = stringResource(R.string.video_fill_screen),
                        onClick = { fillScreen = !fillScreen },
                        tint = if (fillScreen) Color(0xFF0A84FF) else Color.White,
                        modifier = Modifier.testTag(HgTestTags.VIDEO_FILL),
                    )
                }
            }
        }
    }
}

/**
 * The progress line.
 *
 * Deliberately not a `Slider`: Material's slider has a 20 dp track region, a
 * thumb that does not change size, and an overlay ripple, all of which are
 * visibly not the reference. This is a hairline that thickens on touch and grows
 * a thumb while held.
 */
@Composable
private fun VideoProgressBar(
    fraction: Float,
    scrubbing: Boolean,
    onScrubStart: () -> Unit,
    onScrubMove: (Float) -> Unit,
    onScrubEnd: () -> Unit,
) {
    val trackHeight = if (scrubbing) 4.dp else 2.5.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .testTag(HgTestTags.VIDEO_PROGRESS)
            .pointerInput(Unit) {
                androidx.compose.foundation.gestures.detectHorizontalDragGestures(
                    onDragStart = { onScrubStart() },
                    onDragEnd = { onScrubEnd() },
                    onDragCancel = { onScrubEnd() },
                ) { change, _ ->
                    change.consume()
                    onScrubMove((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onScrubStart()
                    onScrubMove((offset.x / size.width).coerceIn(0f, 1f))
                    onScrubEnd()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Rail.
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .background(Color.White.copy(alpha = 0.22f)),
        )
        // Played portion.
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(trackHeight)
                .background(Color.White),
        )
        // Thumb: only while the finger is down, so the line stays a line.
        if (scrubbing) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(trackHeight),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}

/** The speed list, anchored above its trigger. */
@Composable
private fun SpeedMenu(
    current: Float,
    onPick: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF01C1C1E))
            .padding(vertical = 6.dp)
            .testTag(HgTestTags.VIDEO_SPEED_MENU),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        PlayerPolicy.SPEEDS.forEach { option ->
            val selected = option == current
            Text(
                text = PlayerPolicy.speedLabel(option),
                style = TimecodeLabel,
                color = if (selected) Color(0xFF0A84FF) else Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(option) {
                        detectTapGestures(onTap = { onPick(option) })
                    }
                    .padding(horizontal = 22.dp, vertical = 11.dp),
            )
        }
    }
}
