package ai.dana.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest

/** Image output is separate from chat and is never silently routed through the Free text profile. */
enum class ArtModel(val title: String, val id: String, val service: QuickService, val studio: Boolean = false) {
    AVAL_IMAGE("GPT Image 2.6 Flare", "gpt-image-2.6-flare", QuickService.AVALAI),
    AVAL_STUDIO("GPT Image 2.6 Sunburst", "gpt-image-2.6-sunburst", QuickService.AVALAI, studio = true),
    NANO_LITE("Nano Banana 2 Lite", "gemini-3.1-flash-lite-image", QuickService.GOOGLE),
    NANO("Nano Banana 2", "gemini-3.1-flash-image", QuickService.GOOGLE),
    NANO_PRO("Nano Banana Pro", "gemini-3-pro-image", QuickService.GOOGLE, studio = true),
    GPT_IMAGE("GPT Image 2.5 Flare", "gpt-image-2.5-flare", QuickService.OPENAI),
    GPT_STUDIO("GPT Image 2.6 Sunburst", "gpt-image-2.6-sunburst", QuickService.OPENAI, studio = true);

    val openAiQuality: String get() = if (studio) "high" else "low"
    val geminiSize: String get() = if (studio) "2K" else "1K"

    fun matches(profile: ProviderProfile): Boolean = profile.hasCredential && profile.config.endpoint == service.endpoint &&
        (profile.config.acceptsChat || profile.config.model.removePrefix("models/") == id)

    companion object {
        fun forService(service: QuickService): List<ArtModel> = entries.filter { it.service == service }.sortedBy { !it.studio }
    }
}

enum class ArtShape(val ratio: String, val openAiSize: String) {
    SQUARE("1:1", "1024x1024"), LANDSCAPE("3:2", "1536x1024"), PORTRAIT("2:3", "1024x1536"),
}

class ArtRequest private constructor(val profile: ProviderProfile, val model: ArtModel, val shape: ArtShape, val prompt: String) {
    val grant get() = SendGrant.image(profile, model.id)
    companion object {
        const val PROMPT_LIMIT = 2_000
        fun create(profile: ProviderProfile, model: ArtModel, shape: ArtShape, raw: String): ArtRequest? {
            val prompt = raw.trim()
            if (!model.matches(profile) || prompt.length !in 1..PROMPT_LIMIT) return null
            return ArtRequest(profile, model, shape, prompt)
        }
    }
}

/** Only bounded PNG/JPEG bytes. Remote URLs, SVG and other active/linked formats are not accepted. */
class ArtImage private constructor(val mime: String, private val bytes: ByteArray) {
    val extension: String get() = if (mime == "image/png") "png" else "jpg"
    fun data(): ByteArray = bytes.copyOf()
    fun writeTo(output: java.io.OutputStream) { output.write(bytes) }
    override fun toString(): String = "ArtImage($mime, ${bytes.size} bytes)"

    companion object {
        const val BYTE_LIMIT = 8 * 1024 * 1024
        private val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        fun create(mime: String, bytes: ByteArray): ArtImage? {
            if (bytes.size !in 24..BYTE_LIMIT) return null
            val matches = when (mime) {
                "image/png" -> png.indices.all { bytes[it] == png[it] }
                "image/jpeg" -> bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() &&
                    bytes[bytes.lastIndex - 1] == 0xff.toByte() && bytes.last() == 0xd9.toByte()
                else -> false
            }
            return if (matches) ArtImage(mime, bytes.copyOf()) else null
        }
    }
}

interface ImageTransport {
    suspend fun generate(request: ArtRequest, key: ApiCredential): ArtImage
}

class ImageGeneration(private val preferences: PrivacyPreferences, private val providers: ProviderRepository, private val transport: ImageTransport) {
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun generate(request: ArtRequest): ArtImage = preferences.offlineMode
        .catch { error -> if (error is CancellationException || error !is Exception) throw error else emit(true) }
        .distinctUntilChanged().transformLatest { offline ->
            if (offline) throw EngineFailure(EngineProblem.OFFLINE)
            val key = providers.credentialFor(request.profile) ?: throw EngineFailure(EngineProblem.AUTHENTICATION)
            emit(transport.generate(request, key))
        }.first()
}
