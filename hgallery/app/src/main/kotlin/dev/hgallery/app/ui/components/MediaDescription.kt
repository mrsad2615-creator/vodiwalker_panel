package dev.hgallery.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.hgallery.app.R
import dev.hgallery.core.MediaItem

/**
 * The sentence a screen reader announces for one grid cell.
 *
 * A grid cell is the tappable element, so the label has to live on the cell
 * rather than on the image inside it: before this existed, the image carried the
 * file name and the clickable container carried nothing, which left TalkBack
 * focused on an unlabelled target. Type and duration are part of the sentence
 * because in a wall of identical squares they are the only way to tell a six
 * second clip from a still.
 */
@Composable
fun mediaDescription(item: MediaItem): String = when {
    item.isVideo && item.durationMs > 0 -> stringResource(
        R.string.media_desc_video,
        spokenLength(item.durationMs),
        item.displayName,
    )
    item.isVideo -> stringResource(R.string.media_desc_video_no_length, item.displayName)
    else -> stringResource(R.string.media_desc_photo, item.displayName)
}

/**
 * A duration in words. Screen readers say "zero colon six" for `0:06`, which is
 * meaningless, so the length is spelled out in seconds or minutes instead.
 */
@Composable
private fun spokenLength(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(1L).toInt()
    return if (totalSeconds < 60) {
        pluralStringResource(R.plurals.media_length_seconds, totalSeconds, totalSeconds)
    } else {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        if (seconds == 0) {
            pluralStringResource(R.plurals.media_length_minutes, minutes, minutes)
        } else {
            pluralStringResource(R.plurals.media_length_minutes, minutes, minutes) +
                " " + pluralStringResource(R.plurals.media_length_seconds, seconds, seconds)
        }
    }
}
