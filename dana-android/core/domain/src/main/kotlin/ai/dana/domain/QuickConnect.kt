package ai.dana.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.withTimeoutOrNull

/** Visible, fixed-destination defaults; the advanced editor still supports other models and private endpoints. */
enum class QuickService(val title: String, val protocol: ProviderProtocol, val api: String, val keyUrl: String, val models: List<String>) {
    FREE("OpenRouter Free", ProviderProtocol.OPENROUTER_FREE, OPENROUTER_BASE_URL, "https://openrouter.ai/settings/keys", listOf(OPENROUTER_FREE_MODEL)),
    GOOGLE("Gemini", ProviderProtocol.GEMINI_NATIVE, GOOGLE_GEMINI_BASE_URL, "https://aistudio.google.com/api-keys",
        listOf("models/gemini-3.5-flash-lite", "models/gemini-3.1-flash-lite", "models/gemini-2.5-flash-lite")),
    AVALAI(CloudEngine.AVALAI),
    OPENAI(CloudEngine.OPENAI),
    GROQ(CloudEngine.GROQ),
    MISTRAL(CloudEngine.MISTRAL),
    DEEPSEEK(CloudEngine.DEEPSEEK),
    CEREBRAS(CloudEngine.CEREBRAS);

    constructor(cloud: CloudEngine) : this(cloud.label, ProviderProtocol.OPENAI_COMPATIBLE, cloud.baseUrl, cloud.consoleUrl, cloud.suggested)

    val endpoint: ProviderEndpoint get() = ProviderEndpoint.create(protocol, ProviderNetwork.INTERNET, api)
    val supportsImage: Boolean get() = ArtModel.entries.any { it.service == this }
}

/** Official OpenRouter browser flow without a callback URI. Verifier lives only in memory and is never shown or logged. */
class RouterApproval private constructor(internal val verifier: String, val challenge: String) {
    private val created = TimeSource.Monotonic.markNow()
    val expired: Boolean get() = created.elapsedNow() >= 10.minutes
    val browserUrl: String get() = "https://openrouter.ai/auth?code_challenge=$challenge&code_challenge_method=S256&key_label=mrsad%20Free"
    override fun toString(): String = "RouterApproval([redacted])"

    companion object {
        fun create(): RouterApproval {
            val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
            val encoder = Base64.getUrlEncoder().withoutPadding()
            val verifier = encoder.encodeToString(bytes)
            bytes.fill(0)
            val challenge = encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
            return RouterApproval(verifier, challenge)
        }
    }
}

class RouterCode private constructor(val code: String, val verifier: String) {
    override fun toString(): String = "RouterCode([redacted])"
    companion object {
        fun create(raw: String, approval: RouterApproval): RouterCode {
            val code = raw.trim()
            if (approval.expired || code.length !in 8..1024 || !code.all { it in '!'..'~' } || "://" in code) {
                throw EngineFailure(EngineProblem.AUTHENTICATION)
            }
            return RouterCode(code, approval.verifier)
        }
    }
}

interface RouterKeyExchange {
    suspend fun exchange(code: RouterCode): ApiCredential
}

/** Only called after the user presses Connect. Sends a fixed probe, never chat history, even when enabling networking from offline. */
class QuickConnect(
    private val directory: ModelDirectory,
    private val transport: EngineTransport,
    private val exchanger: RouterKeyExchange,
) {
    suspend fun test(service: QuickService, raw: String, approval: RouterApproval? = null, imageOnly: Boolean = false): TestedEngine =
        withTimeoutOrNull(50_000L) {
            val key = if (approval != null && service == QuickService.FREE) exchanger.exchange(RouterCode.create(raw, approval))
                else ApiCredential.parse(raw)
            if (service == QuickService.FREE && !key.value.startsWith("sk-or-v1-")) reject(InputIssue.INVALID_API_KEY)
            val model = if (!imageOnly) service.models.first() else {
                ArtModel.entries.firstOrNull { it.service == service }?.id ?: throw EngineFailure(EngineProblem.CONFIGURATION)
            }
            val config = ProviderConfig.create(service.title, service.protocol, ProviderNetwork.INTERNET, service.api, model)
            // No required catalog lookup or model guessing for a text request. Some per-project keys cannot list models.
            // Image setup checks the key/catalog; it does not make a billable image request or promise that quota exists.
            if (imageOnly) {
                if (!directory.hasImageModel(service.endpoint, key, model)) throw EngineFailure(EngineProblem.CONFIGURATION)
            } else probe(config, key)
            TestedEngine(config, key)
        } ?: throw EngineFailure(EngineProblem.TIMEOUT)

    suspend fun probe(config: ProviderConfig, key: ApiCredential?): Long = withTimeoutOrNull(40_000L) {
        if (!config.acceptsChat) throw EngineFailure(EngineProblem.CONFIGURATION)
        if (!config.endpoint.acceptsCredential(key != null)) throw EngineFailure(EngineProblem.AUTHENTICATION)
        val started = TimeSource.Monotonic.markNow()
        var visible = false
        transport.stream(config, key, ConversationInput.connectionProbe()).collect {
            if (it.replaces || it.value.isNotBlank()) visible = it.value.isNotBlank()
        }
        if (!visible) throw EngineFailure(EngineProblem.NO_TEXT)
        started.elapsedNow().inWholeMilliseconds
    } ?: throw EngineFailure(EngineProblem.TIMEOUT)
}

class TestedEngine(val config: ProviderConfig, val credential: ApiCredential) {
    override fun toString(): String = "TestedEngine(${config.name}, [redacted])"
}
