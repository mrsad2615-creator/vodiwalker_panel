package ai.dana.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflinePhotoTest {
    @Test
    fun realTesseractLoadsBundledPersianAndLatinDataAndFindsText() {
        runBlocking {
            withTimeout(25_000) {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val source = Bitmap.createBitmap(1050, 300, Bitmap.Config.ARGB_8888)
                val compressed = try {
                    val canvas = Canvas(source)
                    canvas.drawColor(Color.WHITE)
                    canvas.drawText("DANA OFFLINE 2026", 44f, 175f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 84f
                        color = Color.BLACK
                        typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    })
                    FrameCodec.compress(source)
                } finally { source.recycle() }
                val actual = OnDeviceOcr(context).read(compressed, OcrLanguage.MIXED)
                assertTrue("Native OCR did not recover the rendered fixture: $actual", "DANA OFFLINE" in actual)
            }
        }
    }

    @Test
    fun imageIsResizedAndOnlySelectedPixelsAreEncoded() {
        val original = Bitmap.createBitmap(2304, 1152, Bitmap.Config.ARGB_8888)
        val loaded = try {
            Canvas(original).apply {
                drawColor(Color.RED)
                drawRect(1152f, 0f, 2304f, 1152f, Paint().apply { color = Color.BLUE })
            }
            FrameCodec.compress(original)
        } finally { original.recycle() }
        assertEquals(1536, loaded.width)
        assertEquals(768, loaded.height)
        val right = FrameCodec.crop(loaded, CropBox(0.68f, 0.15f, 0.98f, 0.85f))
        assertTrue(right.width < loaded.width && right.height < loaded.height)
        val cropped = right.decode()
        try {
            val color = cropped.getPixel(cropped.width / 2, cropped.height / 2)
            assertTrue(Color.blue(color) > 240 && Color.red(color) < 20)
            assertTrue(right.photo().bytes <= 768_000)
            assertFalse(CropBox(0.8f, 0.9f, 0.1f, 0.2f).usable)
        } finally { cropped.recycle() }
    }
}
