package dev.hgallery.app.ui.viewer

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import dev.hgallery.app.data.AppContainer
import dev.hgallery.core.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes an image for the viewer.
 *
 * A full-resolution decode of a 48 MP file costs ~190 MB and would be killed on
 * a 3 GB phone, so the bitmap is sampled to at most 2048 px on the long edge —
 * at which point a 5× pinch still resolves more detail than the display shows.
 */
@Composable
fun FullResolutionImage(
    item: MediaItem,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val resolver = container.contentResolver
    // Lint cannot see through `withContext` inside a `produceState` block, so the
    // decode is awaited first and the result assigned directly.
    val bitmap by produceState<Bitmap?>(initialValue = null, item.id) {
        val decoded = withContext(Dispatchers.IO) {
            decodeBounded(resolver, item.uri, maxEdge = 2048)
        }
        value = decoded
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        val frame = bitmap
        if (frame != null) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Bounded decode: measure first, then decode with the smallest power-of-two
 * sample that still meets [maxEdge].
 */
internal fun decodeBounded(resolver: ContentResolver, uri: String, maxEdge: Int): Bitmap? =
    runCatching {
        val parsed = Uri.parse(uri)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxEdge || bounds.outHeight / (sample * 2) >= maxEdge) {
            sample *= 2
        }

        resolver.openInputStream(parsed)?.use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        }
    }.getOrNull()
