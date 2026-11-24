package dev.hgallery.app.ui.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import dev.hgallery.app.ui.motion.HgMotion
import dev.hgallery.app.ui.motion.rememberReduceMotion
import dev.hgallery.app.ui.theme.hgColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hgallery.app.R
import dev.hgallery.app.ui.components.HgIconButton
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.theme.PlayerIndicatorLabel
import dev.hgallery.app.ui.theme.TimecodeLabel
import dev.hgallery.core.PlayerPolicy
import dev.hgallery.core.PlayerState

/**
 * The scrims behind the chrome.
 *
 * A flat 40% black over a bright frame gave white text about 2.4:1, which is below the
 * floor for body text. A vertical gradient is darker where the text sits and clear
 * where the picture matters, so the title stays readable without dimming the film.
 */
private val TopScrim = Brush.verticalGradient(
    listOf(Color(0xCC000000), Color(0x80000000), Color(0x00000000)),
)

private val BottomScrim = Brush.verticalGradient(
    listOf(Color(0x00000000), Color(0x99000000), Color(0xD9000000)),
)

/**
 * The top row: close, title, orientation.
 *
 * Split out of `VideoPlayerScreen` so the screen is composition of named parts
 * rather than one long function; detekt's `LongMethod` was flagging a real
 * readability problem, not a style preference.
 */
@Composable
fun PlayerTopBar(
    title: String,
    visible: Boolean,
    onClose: () -> Unit,
    onRotate: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        // The top bar drops in from above and leaves upward, so the direction matches
        // where it came from. A cross-fade alone reads as a flicker.
        enter = fadeIn(tween(140)) + slideInVertically(tween(180, easing = HgMotion.emphasizedDecelerate)) { -it },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(160, easing = HgMotion.emphasizedAccelerate)) { -it },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(TopScrim)
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
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                HgIconButton(
                    icon = HgIcons.Orientation,
                    contentDescription = stringResource(R.string.video_rotate),
                    onClick = onRotate,
                    tint = Color.White,
                )
            }
        }
    }
}

/**
 * Everything the bottom bar needs to draw, as one value.
 *
 * `PlayerBottomBar` took fourteen parameters before this existed — detekt's
 * `LongParameterList` was correct that call sites had become unreadable, and a
 * value object also makes the bar trivially previewable.
 */
@androidx.compose.runtime.Immutable
data class PlayerTransport(
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val fraction: Float,
    val scrubbing: Boolean,
    val speed: Float,
    val fillScreen: Boolean,
    /** True while the player's volume is zero. */
    val muted: Boolean,
)

/** The callbacks the bottom bar fires. Grouped for the same reason as the state. */
@androidx.compose.runtime.Immutable
data class PlayerActions(
    val onTogglePlay: () -> Unit,
    val onScrubStart: () -> Unit,
    val onScrubMove: (Float) -> Unit,
    val onScrubEnd: () -> Unit,
    val onSpeed: (Float) -> Unit,
    val onFillScreen: () -> Unit,
    val onToggleMute: () -> Unit,
)

/**
 * The progress line.
 *
 * ## What changed
 *
 * It used to be two rectangles. A rectangle does not say "drag me": the thumb appeared
 * only after the finger was already down, so the only way to learn the control was to
 * try it. The track is now a rounded capsule with a thumb that is always present, small
 * at rest and larger while held, and the played portion grows under a spring so a tap
 * to seek shows movement instead of a jump.
 *
 * The track thickens from 3 dp to 5 dp while held, which is the reference's behaviour
 * and also enlarges the target exactly when the user has committed to using it.
 */
@Composable
fun PlayerProgress(
    fraction: Float,
    scrubbing: Boolean,
    onStart: () -> Unit,
    onMove: (Float) -> Unit,
    onEnd: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val playedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        // While a finger is on the line the value must follow it exactly; a spring
        // there would lag behind the finger and feel broken.
        animationSpec = if (scrubbing) {
            spring(stiffness = Spring.StiffnessHigh)
        } else {
            HgMotion.defaultEffects<Float>(reduceMotion)
        },
        label = "playedFraction",
    )
    val trackHeight by animateDpAsState(
        targetValue = if (scrubbing) 5.dp else 3.dp,
        animationSpec = HgMotion.fastSpatial(reduceMotion),
        label = "trackHeight",
    )
    val thumbSize by animateDpAsState(
        targetValue = if (scrubbing) 16.dp else 9.dp,
        animationSpec = HgMotion.fastSpatial(reduceMotion),
        label = "thumbSize",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .testTag(HgTestTags.VIDEO_PROGRESS)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { onStart() },
                    onDragEnd = { onEnd() },
                    onDragCancel = { onEnd() },
                ) { change, _ ->
                    change.consume()
                    onMove((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onStart()
                    onMove((offset.x / size.width).coerceIn(0f, 1f))
                    onEnd()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.26f)),
        )
        Box(
            Modifier
                .fillMaxWidth(playedFraction)
                .padding(start = 12.dp)
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(Color.White),
        )
        // The thumb rides the end of the played portion. It is offset from the same
        // 12 dp inset as the track, or it would sit outside the bar at 0%.
        Box(
            Modifier
                .fillMaxWidth(playedFraction)
                .padding(start = 12.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(Color.White)
                    .shadow(elevation = 3.dp, shape = CircleShape),
            )
        }
    }
}

/**
 * The bottom row: play/pause, timecode, speed, fill-screen.
 *
 * Takes plain values rather than the controller so it can be rendered in a
 * preview and driven from a test without constructing an `ExoPlayer`.
 */
@Composable
fun PlayerBottomBar(
    visible: Boolean,
    transport: PlayerTransport,
    actions: PlayerActions,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(140)) + slideInVertically(tween(200, easing = HgMotion.emphasizedDecelerate)) { it },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(160, easing = HgMotion.emphasizedAccelerate)) { it },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(BottomScrim)
                .navigationBarsPadding()
                .padding(bottom = 6.dp),
        ) {
            PlayerProgress(
                fraction = transport.fraction,
                scrubbing = transport.scrubbing,
                onStart = actions.onScrubStart,
                onMove = actions.onScrubMove,
                onEnd = actions.onScrubEnd,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HgIconButton(
                    icon = if (transport.isPlaying) HgIcons.Pause else HgIcons.Play,
                    contentDescription = stringResource(
                        if (transport.isPlaying) R.string.video_pause else R.string.video_play,
                    ),
                    onClick = actions.onTogglePlay,
                    tint = Color.White,
                    modifier = Modifier.testTag(HgTestTags.VIDEO_PLAY_PAUSE),
                )

                Text(
                    text = "${PlayerPolicy.timecode(transport.positionMs)} / " +
                        PlayerPolicy.timecode(transport.durationMs),
                    style = TimecodeLabel,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .testTag(HgTestTags.VIDEO_TIMECODE),
                )

                Spacer(Modifier.weight(1f))

                // Sound is a first-class control, not a menu item. A clip that arrives
                // muted by the swipe rule must show how to unmute it in the same place
                // it would show a volume slider, or the mute reads as a broken speaker.
                HgIconButton(
                    icon = if (transport.muted) HgIcons.SoundOff else HgIcons.SoundOn,
                    contentDescription = stringResource(
                        if (transport.muted) R.string.video_unmute else R.string.video_mute,
                    ),
                    onClick = actions.onToggleMute,
                    tint = if (transport.muted) hgColors.accentWarm else Color.White,
                    modifier = Modifier.testTag(HgTestTags.VIDEO_MUTE),
                )

                SpeedControl(speed = transport.speed, onPick = actions.onSpeed)

                HgIconButton(
                    icon = HgIcons.FillScreen,
                    contentDescription = stringResource(R.string.video_fill_screen),
                    onClick = actions.onFillScreen,
                    tint = if (transport.fillScreen) hgColors.accentWarm else Color.White,
                    modifier = Modifier.testTag(HgTestTags.VIDEO_FILL),
                )
            }
        }
    }
}

/** The speed trigger and its anchored sheet. */
@Composable
private fun SpeedControl(speed: Float, onPick: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            text = PlayerPolicy.speedLabel(speed),
            style = TimecodeLabel,
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.14f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { open = !open })
                }
                .padding(horizontal = 14.dp, vertical = 7.dp)
                .testTag(HgTestTags.VIDEO_SPEED),
        )

        if (open) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xF01C1C1E))
                    .padding(vertical = 6.dp)
                    .testTag(HgTestTags.VIDEO_SPEED_MENU),
            ) {
                PlayerPolicy.SPEEDS.forEach { option ->
                    val selected = option == speed
                    Text(
                        text = PlayerPolicy.speedLabel(option),
                        style = TimecodeLabel,
                        color = if (selected) hgColors.accentWarm else Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(option) {
                                detectTapGestures {
                                    open = false
                                    onPick(option)
                                }
                            }
                            .padding(horizontal = 22.dp, vertical = 11.dp),
                    )
                }
            }
        }
    }
}

/** The buffering spinner and the error copy. */
@Composable
fun PlayerStatusOverlay(state: PlayerState, showSpinner: Boolean) {
    if (showSpinner) {
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.dp,
            modifier = Modifier.size(38.dp),
        )
    }
    if (state.hasError) {
        Column(
            modifier = Modifier.padding(32.dp),
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
}

/** The centre play / replay disc shown only when playback is not running. */
@Composable
fun PlayerCentreButton(
    visible: Boolean,
    showReplay: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(140)),
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f))
                .pointerInput(showReplay) {
                    detectTapGestures { onClick() }
                }
                .testTag(HgTestTags.VIDEO_CENTRE_BUTTON),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (showReplay) HgIcons.Restore else HgIcons.Play,
                contentDescription = stringResource(
                    if (showReplay) R.string.video_replay else R.string.video_play,
                ),
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

/** The "+0:10" / "-0:10" hint that flashes after a double tap. */
@Composable
fun PlayerSkipHint(hintMs: Long) {
    AnimatedVisibility(
        visible = hintMs != 0L,
        enter = fadeIn(tween(90)),
        exit = fadeOut(tween(180)),
    ) {
        Text(
            text = if (hintMs >= 0) {
                "+${PlayerPolicy.timecode(hintMs)}"
            } else {
                "-${PlayerPolicy.timecode(-hintMs)}"
            },
            style = PlayerIndicatorLabel,
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}
