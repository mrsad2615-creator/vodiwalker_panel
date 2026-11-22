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

/** The translucent scrim the player's chrome sits on, at the reference's weight. */
private val ChromeScrim = Color(0x66000000)

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
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(180)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(ChromeScrim)
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
)

/** The thin progress line: thickens while held, grows a thumb only while held. */
@Composable
fun PlayerProgress(
    fraction: Float,
    scrubbing: Boolean,
    onStart: () -> Unit,
    onMove: (Float) -> Unit,
    onEnd: () -> Unit,
) {
    val trackHeight = if (scrubbing) 4.dp else 2.5.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
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
        Box(Modifier.fillMaxWidth().height(trackHeight).background(Color.White.copy(alpha = 0.22f)))
        Box(Modifier.fillMaxWidth(fraction).height(trackHeight).background(Color.White))
        if (scrubbing) {
            Box(
                Modifier.fillMaxWidth(fraction).height(trackHeight),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(Modifier.size(13.dp).clip(CircleShape).background(Color.White))
            }
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
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(180)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(ChromeScrim)
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

                SpeedControl(speed = transport.speed, onPick = actions.onSpeed)

                HgIconButton(
                    icon = HgIcons.FillScreen,
                    contentDescription = stringResource(R.string.video_fill_screen),
                    onClick = actions.onFillScreen,
                    tint = if (transport.fillScreen) Color(0xFF0A84FF) else Color.White,
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
                        color = if (selected) Color(0xFF0A84FF) else Color.White,
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
