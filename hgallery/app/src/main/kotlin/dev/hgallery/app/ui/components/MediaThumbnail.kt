package dev.hgallery.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.hgallery.app.data.ThumbnailStore
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.core.DurationFormatter
import dev.hgallery.core.MediaItem

/**
 * One grid cell.
 *
 * The placeholder is a flat neutral, never a spinner: a timeline that flashes
 * spinners while scrolling is the single most common way a gallery feels cheap,
 * and the reference never shows one.
 */
@Composable
fun MediaThumbnail(
    item: MediaItem,
    store: ThumbnailStore,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 0,
    showVideoBadge: Boolean = true,
    /**
     * Whether the image announces its own name. False whenever the caller wraps
     * the tile in a clickable element that supplies a full description, so
     * TalkBack does not read the name twice for one cell.
     */
    describeImage: Boolean = true,
) {
    val palette = hgPalette
    var bitmap by remember(item.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(item.uri) {
        val decoded = store.get(item.uri, item.isVideo)
        bitmap = decoded
    }

    val shape = remember(cornerRadius) {
        if (cornerRadius > 0) RoundedCornerShape(cornerRadius.dp) else RoundedCornerShape(0.dp)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(palette.chipBackground),
    ) {
        val frame = bitmap
        if (frame != null) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = if (describeImage) item.displayName else null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showVideoBadge && item.isVideo) {
            // Reference places the duration at the bottom-right of the tile in
            // white on a soft shadow, with a small play glyph at the left.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
            ) {
                Icon(
                    imageVector = HgIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp),
                )
            }
            Text(
                text = DurationFormatter.badge(item.durationMs),
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 5.dp, bottom = 3.dp),
            )
        }
    }
}
