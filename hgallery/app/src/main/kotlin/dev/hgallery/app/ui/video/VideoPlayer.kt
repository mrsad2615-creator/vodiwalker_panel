package dev.hgallery.app.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.hgallery.app.data.rememberPlayerController
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.core.MediaItem
import dev.hgallery.core.PlayerPolicy
import kotlinx.coroutines.delay

/**
 * Full-screen video player.
 *
 * ## Why a custom surface and not Media3's own controller
 *
 * `PlayerView` ships a controller whose layout, icon weights and timings are
 * Google's, not Huawei's. The reference's player is a full-bleed black surface
 * with a slim translucent top row, a centre transport disc and a single progress
 * line whose thumb grows while held. Media3's controller cannot be configured
 * into that shape, so `useController` is off and every control is drawn from this
 * project's design system by [PlayerTopBar] and [PlayerBottomBar].
 *
 * ## Division of responsibility
 *
 *  - `core/PlayerPolicy` holds every rule and is unit-tested (`PlayerPolicyTest`).
 *  - `data/PlayerController` owns the `ExoPlayer` and the observable state.
 *  - this file is composition only: it wires gestures to the controller and the
 *    controller to the chrome.
 *
 * That split exists because the first version of this screen was a 371-line
 * function with cyclomatic complexity 37, and detekt was right to reject it.
 *
 * ## Gestures
 *
 *  - tap once: toggle the chrome (never pauses, never closes);
 *  - double tap left / right third: seek back or forward 10 s, with a hint;
 *  - double tap the middle: play/pause;
 *  - horizontal drag anywhere on the surface: seek, committed on release;
 *  - the progress line: a real scrub, with the timecode following the finger.
 */
@UnstableApi
@Composable
fun VideoPlayerScreen(
    item: MediaItem,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val controller = rememberPlayerController(item.uri)

    var chromeVisible by remember { mutableStateOf(true) }
    var chromePinned by remember { mutableStateOf(false) }
    var fillScreen by remember { mutableStateOf(false) }

    val chrome = PlayerPolicy.chromeFor(
        state = controller.state,
        positionMs = controller.positionMs,
        durationMs = controller.durationMs,
        scrub = controller.scrub,
        visible = chromeVisible,
        reachedEnd = controller.reachedEnd,
    )

    // Reveal the chrome whenever playback stops on its own, so the user is never
    // looking at a frozen frame with no way to act on it.
    LaunchedEffect(controller.state, controller.reachedEnd) {
        if (controller.state.hasError || controller.reachedEnd) chromeVisible = true
    }

    // Auto-hide, keyed on everything the policy looks at.
    LaunchedEffect(chromeVisible, controller.state, controller.scrub, chromePinned) {
        if (PlayerPolicy.shouldAutoHide(
                controller.state,
                controller.scrub != null,
                chromePinned,
                menuOpen = false,
            )
        ) {
            delay(PlayerPolicy.CHROME_TIMEOUT_MS)
            if (PlayerPolicy.shouldAutoHide(
                    controller.state,
                    controller.scrub != null,
                    chromePinned,
                    menuOpen = false,
                )
            ) {
                chromeVisible = false
            }
        }
    }

    // The skip hint is transient.
    LaunchedEffect(controller.hintAt) {
        if (controller.hintAt != 0L) {
            delay(700)
            controller.clearHint()
        }
    }

    Box(
        modifier = Modifier
            .testTag(HgTestTags.VIDEO_PLAYER)
            .fillMaxSize()
            .background(Color.Black),
    ) {
        PlayerSurface(
            controller = controller,
            fillScreen = fillScreen,
            onTap = {
                chromeVisible = !chromeVisible
                chromePinned = chromeVisible
            },
            onDoubleTap = { fraction ->
                when {
                    fraction < 1f / 3f -> controller.skip(-PlayerPolicy.SKIP_SMALL_MS)
                    fraction > 2f / 3f -> controller.skip(PlayerPolicy.SKIP_SMALL_MS)
                    else -> controller.togglePlay()
                }
            },
        )

        Box(Modifier.align(Alignment.Center)) {
            PlayerStatusOverlay(controller.state, chrome.showSpinner)
        }

        Box(Modifier.align(Alignment.Center)) {
            PlayerSkipHint(controller.hintMs)
        }

        PlayerCentreButton(
            visible = chrome.visible &&
                (chrome.showReplay || (!controller.state.isPlaying && !controller.state.hasError)),
            showReplay = chrome.showReplay,
            onClick = {
                if (chrome.showReplay) controller.replay() else controller.togglePlay()
                chromeVisible = true
            },
        )

        Box(Modifier.align(Alignment.TopCenter)) {
            PlayerTopBar(
                title = item.displayName,
                visible = chrome.visible,
                onClose = onClose,
                onRotate = {
                    activity?.requestedOrientation = nextOrientation(activity.requestedOrientation)
                },
            )
        }

        Box(Modifier.align(Alignment.BottomCenter)) {
            PlayerBottomBar(
                visible = chrome.visible,
                transport = PlayerTransport(
                    isPlaying = controller.state.isPlaying,
                    positionMs = chrome.displayPositionMs,
                    durationMs = controller.durationMs,
                    fraction = chrome.displayFraction,
                    scrubbing = chrome.scrubbing,
                    speed = controller.speed,
                    fillScreen = fillScreen,
                ),
                actions = PlayerActions(
                    onTogglePlay = {
                        controller.togglePlay()
                        chromeVisible = true
                    },
                    onScrubStart = { controller.beginScrub() },
                    onScrubMove = { controller.moveScrub(it) },
                    onScrubEnd = { controller.endScrub() },
                    onSpeed = { controller.setSpeed(it) },
                    onFillScreen = { fillScreen = !fillScreen },
                ),
            )
        }
    }
}

/** Landscape ↔ portrait, which is what the orientation control offers. */
private fun nextOrientation(current: Int): Int =
    if (current == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    } else {
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

/**
 * The video surface plus the two gesture layers on top of it.
 *
 * Kept apart from the screen so the screen reads as composition. The surface is
 * an `AndroidView` because `PlayerView` is a `ViewGroup`; everything drawn on top
 * of it is Compose.
 */
@UnstableApi
@Composable
private fun PlayerSurface(
    controller: dev.hgallery.app.data.PlayerController,
    fillScreen: Boolean,
    onTap: () -> Unit,
    onDoubleTap: (fraction: Float) -> Unit,
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                player = controller.player
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
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { offset -> onDoubleTap(offset.x / size.width) },
                )
            }
            .pointerInput(Unit) {
                var accumulatedDp = 0f
                var startMs = 0L
                var wasPlaying = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        accumulatedDp = 0f
                        startMs = controller.player.currentPosition
                        wasPlaying = controller.player.isPlaying
                    },
                    onDragEnd = {
                        val target = PlayerPolicy.seekByDrag(
                            startMs,
                            accumulatedDp,
                            controller.durationMs,
                        )
                        if (target != null) {
                            controller.player.seekTo(target)
                            if (wasPlaying) controller.player.play()
                        }
                        accumulatedDp = 0f
                    },
                    onDragCancel = { accumulatedDp = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    // px → dp so SEEK_MS_PER_DP is density-independent.
                    accumulatedDp += dragAmount / density
                }
            },
    )
}
