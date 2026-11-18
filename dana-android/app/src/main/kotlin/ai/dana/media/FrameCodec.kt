package ai.dana.media

import ai.dana.domain.ChatLimits
import ai.dana.domain.VisionPhoto
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Only re-encoded JPEG bytes are retained in state; no EXIF, file paths or persisted URI grant. */
internal class MemoryFrame internal constructor(internal val content: ByteArray, val width: Int, val height: Int) {
    fun decode(): Bitmap = BitmapFactory.decodeByteArray(content, 0, content.size, BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }) ?: throw IOException("Image could not be decoded")
    fun photo(): VisionPhoto = VisionPhoto.fromJpeg(content) ?: throw IOException("Invalid compressed image")
    override fun toString(): String = "MemoryFrame(${width}x$height)"
}

internal data class CropBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val usable: Boolean get() = listOf(left, top, right, bottom).all { it in 0f..1f } && right - left >= 0.03f && bottom - top >= 0.03f
    companion object { val Full = CropBox(0f, 0f, 1f, 1f) }
}

internal object FrameCodec {
    private const val MAX_SOURCE_PIXELS = 64_000_000L

    fun fromUri(context: Context, uri: Uri): MemoryFrame {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) throw IOException("Expected content URI")
        val bitmap = if (Build.VERSION.SDK_INT >= 28) fromDecoder(context.contentResolver, uri) else fromStream(context.contentResolver, uri)
        return try { compress(bitmap) } finally { bitmap.recycle() }
    }

    @RequiresApi(28)
    private fun fromDecoder(resolver: ContentResolver, uri: Uri): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
        val dimensions = info.size
        validate(dimensions.width, dimensions.height)
        val (w, h) = bounded(dimensions.width, dimensions.height)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setTargetSize(w, h)
    }

    private fun fromStream(resolver: ContentResolver, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: Unit
        validate(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > ChatLimits.PHOTO_EDGE) sample *= 2
        return resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
        } ?: throw IOException("Image could not be decoded")
    }

    fun compress(bitmap: Bitmap): MemoryFrame {
        validate(bitmap.width, bitmap.height)
        val (width, height) = bounded(bitmap.width, bitmap.height)
        val scaled = if (bitmap.width == width && bitmap.height == height) bitmap else Bitmap.createScaledBitmap(bitmap, width, height, true)
        val flattened = if (!scaled.hasAlpha()) scaled else Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(scaled, 0f, 0f, null)
            setHasAlpha(false)
        }
        try {
            for (quality in intArrayOf(88, 76, 62, 48, 34)) {
                val output = ByteArrayOutputStream()
                if (flattened.compress(Bitmap.CompressFormat.JPEG, quality, output) && output.size() <= ChatLimits.PHOTO_BYTES) {
                    return MemoryFrame(output.toByteArray(), width, height)
                }
            }
            throw IOException("Compressed image size exceeds limit")
        } finally {
            if (flattened !== scaled) flattened.recycle()
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    fun crop(frame: MemoryFrame, box: CropBox): MemoryFrame {
        if (box == CropBox.Full) return frame
        if (!box.usable) throw IOException("Crop outside frame")
        val full = frame.decode()
        try {
            val left = floor(box.left * full.width).toInt().coerceIn(0, full.width - 1)
            val top = floor(box.top * full.height).toInt().coerceIn(0, full.height - 1)
            val right = ceil(box.right * full.width).toInt().coerceIn(left + 1, full.width)
            val bottom = ceil(box.bottom * full.height).toInt().coerceIn(top + 1, full.height)
            val part = Bitmap.createBitmap(full, left, top, right - left, bottom - top)
            return try { compress(part) } finally { if (part !== full) part.recycle() }
        } finally { full.recycle() }
    }

    fun bounded(w: Int, h: Int, edge: Int = ChatLimits.PHOTO_EDGE): Pair<Int, Int> {
        val ratio = (edge.toDouble() / max(w, h)).coerceAtMost(1.0)
        return (w * ratio).roundToInt().coerceAtLeast(1) to (h * ratio).roundToInt().coerceAtLeast(1)
    }

    private fun validate(w: Int, h: Int) {
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_SOURCE_PIXELS) throw IOException("Invalid or oversized dimensions")
    }
}
