package ai.dana.media

import ai.dana.domain.ComparisonLimits
import android.content.Context
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class OcrLanguage(val code: String) { MIXED("fas+eng"), PERSIAN("fas"), ENGLISH("eng") }

@Singleton
internal class OnDeviceOcr @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()

    suspend fun read(frame: MemoryFrame, language: OcrLanguage): String = mutex.withLock {
        withContext(Dispatchers.Default) {
            val root = withContext(Dispatchers.IO) { installAssets() }
            val tess = TessBaseAPI()
            try {
                if (!tess.init(root.absolutePath, language.code)) throw IOException("OCR initialization failed")
                tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                val bitmap = frame.decode()
                try {
                    tess.setImage(bitmap)
                    tess.getUTF8Text().orEmpty().trim().take(ComparisonLimits.PROMPT_CHARS)
                } finally { bitmap.recycle() }
            } finally { tess.recycle() }
        }
    }

    private fun installAssets(): File {
        val root = File(context.noBackupFilesDir, "ocr-4.1.0")
        val languages = File(root, "tessdata")
        if (!languages.isDirectory && !languages.mkdirs()) throw IOException("No OCR data directory")
        for ((name, info) in FILES) {
            val model = File(languages, name)
            if (model.length() == info.first && hash(model) == info.second) continue
            val temp = File.createTempFile("tess-", ".tmp", languages)
            try {
                context.assets.open("tessdata/$name").use { source -> temp.outputStream().use { dest -> source.copyTo(dest) } }
                if (temp.length() != info.first || hash(temp) != info.second || !temp.renameTo(model)) throw IOException("Invalid OCR data")
            } finally { temp.delete() }
        }
        return root
    }

    private fun hash(file: File): String {
        val sha = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16_384)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                sha.update(buffer, 0, count)
            }
        }
        return sha.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private companion object {
        val FILES = mapOf(
            "fas.traineddata" to (431_500L to "db1c0a91208aff00d3cf1ed2c1d23f76419afd5f024688b4f71adc3f2ce4a505"),
            "eng.traineddata" to (4_113_088L to "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"),
        )
    }
}
