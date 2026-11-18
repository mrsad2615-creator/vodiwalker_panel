package ai.dana.media

import ai.dana.R
import ai.dana.domain.ComparisonLimits
import ai.dana.domain.VisionPhoto
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class MediaState(
    val open: Boolean = false,
    val frame: MemoryFrame? = null,
    val crop: CropBox = CropBox.Full,
    val recognized: String = "",
    val language: OcrLanguage = OcrLanguage.MIXED,
    val working: Boolean = false,
    val capturing: Boolean = false,
    val error: Int? = null,
)

@HiltViewModel
internal class MediaViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ocr: OnDeviceOcr,
    private val inbox: CaptureInbox,
) : ViewModel() {
    private val mutable = MutableStateFlow(MediaState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var generation = 0L

    init {
        viewModelScope.launch {
            inbox.results.collect { event ->
                if (event != null && mutable.value.open) {
                    when (event) {
                        is CaptureResult.Ready -> showFrame(event.frame)
                        is CaptureResult.Failed -> mutable.update { it.copy(capturing = false, error = event.message) }
                    }
                    inbox.clear()
                }
            }
        }
    }

    @MainThread
    fun open() { mutable.update { it.copy(open = true) } }

    @MainThread
    fun close() {
        ++generation
        work?.cancel()
        work = null
        cancelCapture()
        mutable.value = MediaState()
    }

    @MainThread
    fun cancelCapture() {
        if (mutable.value.capturing) {
            inbox.clear()
            context.stopService(Intent(context, OneFrameCaptureService::class.java))
            mutable.update { it.copy(capturing = false) }
        }
    }

    @MainThread
    fun capture(grant: Intent) {
        if (!mutable.value.open || mutable.value.capturing || mutable.value.working) return
        val ticket = inbox.issue()
        mutable.update { it.copy(capturing = true, error = null) }
        try {
            ContextCompat.startForegroundService(context, Intent(context, OneFrameCaptureService::class.java)
                .putExtra(OneFrameCaptureService.EXTRA_TICKET, ticket).putExtra(OneFrameCaptureService.EXTRA_GRANT, grant))
        } catch (_: RuntimeException) {
            inbox.clear()
            mutable.update { it.copy(capturing = false, error = R.string.media_capture_error) }
        }
    }

    @MainThread
    fun uri(uri: Uri) { execute(R.string.media_load_error) { showFrame(withContext(Dispatchers.IO) { FrameCodec.fromUri(context, uri) }) } }

    @MainThread
    fun camera(bitmap: Bitmap) {
        if (!mutable.value.open || mutable.value.working || mutable.value.capturing) {
            bitmap.recycle()
            return
        }
        execute(R.string.media_load_error) {
            try { showFrame(withContext(Dispatchers.Default) { FrameCodec.compress(bitmap) }) } finally { bitmap.recycle() }
        }
    }

    @MainThread
    fun crop(box: CropBox) {
        if (box.usable && !mutable.value.working) mutable.update { it.copy(crop = box, recognized = "", error = null) }
    }

    @MainThread
    fun language(lang: OcrLanguage) { mutable.update { if (it.working) it else it.copy(language = lang, error = null) } }

    @MainThread
    fun recognized(text: String) { mutable.update { it.copy(recognized = text.take(ComparisonLimits.PROMPT_CHARS)) } }

    @MainThread
    fun read() {
        val current = mutable.value
        val frame = current.frame ?: return
        execute(R.string.media_ocr_error) {
            val trimmed = withContext(Dispatchers.Default) { FrameCodec.crop(frame, current.crop) }
            val result = ocr.read(trimmed, current.language)
            mutable.update { it.copy(recognized = result, error = if (result.isBlank()) R.string.media_ocr_empty else null) }
        }
    }

    @MainThread
    fun attach(onReady: (VisionPhoto) -> Unit) {
        val cur = mutable.value
        val frame = cur.frame ?: return
        execute(R.string.media_load_error) {
            val photo = withContext(Dispatchers.Default) { FrameCodec.crop(frame, cur.crop).photo() }
            onReady(photo)
            close()
        }
    }

    @MainThread
    fun error(message: Int) { if (mutable.value.open) mutable.update { it.copy(error = message) } }

    private fun showFrame(frame: MemoryFrame) { mutable.update { it.copy(frame = frame, crop = CropBox.Full, recognized = "", capturing = false, error = null) } }

    private fun execute(error: Int, operation: suspend () -> Unit) {
        if (!mutable.value.open || mutable.value.working || mutable.value.capturing) return
        val ticket = ++generation
        mutable.update { it.copy(working = true, error = null) }
        work = viewModelScope.launch {
            try {
                operation()
                coroutineContext.ensureActive()
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (_: Exception) {
                error(error)
            } catch (_: LinkageError) {
                error(error)
            } finally {
                if (ticket == generation) mutable.update { it.copy(working = false) }
            }
        }
    }

    override fun onCleared() { cancelCapture(); super.onCleared() }
}
