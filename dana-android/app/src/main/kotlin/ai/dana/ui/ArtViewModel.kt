package ai.dana.ui

import ai.dana.R
import ai.dana.domain.ArtImage
import ai.dana.domain.ArtModel
import ai.dana.domain.ArtRequest
import ai.dana.domain.ArtShape
import ai.dana.domain.DirectSendSettings
import ai.dana.domain.EngineFailure
import ai.dana.domain.EngineProblem
import ai.dana.domain.ImageGeneration
import ai.dana.domain.ProviderProfile
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ArtState(
    val prompt: String = "",
    val model: ArtModel? = null,
    val engineId: String? = null,
    val shape: ArtShape = ArtShape.SQUARE,
    val pending: ArtRequest? = null,
    val running: Boolean = false,
    val preview: ArtPreview? = null,
    val error: Int? = null,
    val status: Int? = null,
)

internal class ArtPreview(val id: String, val image: ArtImage, val bitmap: Bitmap, val width: Int, val height: Int,
    val engine: String, val millis: Long)

@HiltViewModel
internal class ArtViewModel @Inject constructor(private val images: ImageGeneration, private val direct: DirectSendSettings) : ViewModel() {
    private val mutable = MutableStateFlow(ArtState())
    val state = mutable.asStateFlow()
    private var work: Job? = null

    @MainThread fun prompt(text: String) = edit { if (text.length <= ArtRequest.PROMPT_LIMIT) it.copy(prompt = text) else it }
    @MainThread fun select(model: ArtModel, profile: ProviderProfile) = edit { it.copy(model = model, engineId = profile.id) }
    @MainThread fun shape(shape: ArtShape) = edit { it.copy(shape = shape) }
    @MainThread fun clearPreview() = edit { it.copy(preview = null) }
    @MainThread fun dismiss() { if (!mutable.value.running) mutable.update { it.copy(pending = null) } }

    @MainThread fun submit(model: ArtModel, profile: ProviderProfile) {
        val state = mutable.value
        if (state.running || state.pending != null) return
        val request = ArtRequest.create(profile, model, state.shape, state.prompt) ?: return
        run(request, approved = false)
    }

    @MainThread fun approve() { mutable.value.pending?.let { run(it, approved = true) } }

    @MainThread fun stop() {
        work?.cancel()
        mutable.update { it.copy(pending = null) }
    }

    private fun edit(change: (ArtState) -> ArtState) {
        if (!mutable.value.running && mutable.value.pending == null) mutable.update { change(it).copy(error = null, status = null) }
    }

    private fun run(request: ArtRequest, approved: Boolean) {
        if (mutable.value.running) return
        mutable.update { it.copy(running = true, pending = null, error = null, status = null) }
        work = viewModelScope.launch {
            try {
                if (!approved && (request.model.studio || !direct.permits(request.grant))) {
                    mutable.update { it.copy(pending = request) }
                    return@launch
                }
                if (approved && !request.model.studio) direct.remember(request.grant)
                val started = TimeSource.Monotonic.markNow()
                val image = images.generate(request)
                val preview = withContext(Dispatchers.Default) {
                    decodeArtPreview(image, "${request.profile.config.name} · ${request.model.title}", started.elapsedNow().inWholeMilliseconds)
                }
                mutable.update { it.copy(preview = preview) }
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (error: Exception) {
                mutable.update { it.copy(error = DanaErrorHandler.messageFor(error), status = (error as? EngineFailure)?.httpStatus) }
            } finally {
                mutable.update { it.copy(running = false) }
            }
        }
    }
}

/** Bound decoded dimensions, not just compressed bytes. Save uses the original provider bytes, not the preview. */
internal fun decodeArtPreview(image: ArtImage, engine: String, millis: Long): ArtPreview {
    val bytes = image.data()
    val meta = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, meta)
    val width = meta.outWidth
    val height = meta.outHeight
    if (width !in 1..3072 || height !in 1..3072 || width.toLong() * height > 4_250_000 || meta.outMimeType != image.mime) {
        throw EngineFailure(EngineProblem.PROTOCOL)
    }
    val options = BitmapFactory.Options().apply { inSampleSize = if (maxOf(width, height) > 1280) 2 else 1 }
    val preview = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw EngineFailure(EngineProblem.PROTOCOL)
    return ArtPreview(java.util.UUID.randomUUID().toString(), image, preview, width, height, engine, millis)
}
