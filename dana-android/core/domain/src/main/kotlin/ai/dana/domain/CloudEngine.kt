package ai.dana.domain

/** Fixed, documented entry points. Persisted profiles still use the existing compatible protocol and credential scope. */
enum class CloudEngine(val label: String, val baseUrl: String, val consoleUrl: String, val suggested: List<String>) {
    GROQ("Groq", "https://api.groq.com/openai/v1", "https://console.groq.com/keys",
        listOf("qwen/qwen3.8-27b", "openai/gpt-oss-20b")),
    MISTRAL("Mistral", "https://api.mistral.ai/v1", "https://console.mistral.ai/api-keys",
        listOf("mistral-small-latest", "mistral-medium-latest")),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "https://platform.deepseek.com/api_keys",
        listOf("deepseek-flash", "deepseek-v4-pro")),
    CEREBRAS("Cerebras", "https://api.cerebras.ai/v1", "https://cloud.cerebras.ai/",
        listOf("qwen-3.8-27b", "gpt-oss-120b"));

    val prefersMaxCompletionTokens: Boolean get() = this == GROQ || this == CEREBRAS

    // Catalogs can include transcribers, classifiers and hosted tool runners. This is a chat-only integration.
    fun acceptsChat(model: String): Boolean = when (this) {
        GROQ -> !(model.startsWith("whisper-") || model.startsWith("canopylabs/") ||
            model.startsWith("groq/compound") || model.startsWith("meta-llama/llama-guard") ||
            model.startsWith("meta-llama/llama-prompt-guard"))
        MISTRAL -> !(model.startsWith("mistral-embed") || model.startsWith("codestral-embed") ||
            model.startsWith("mistral-ocr") || model.startsWith("voxtral") || model.startsWith("mistral-moderation"))
        DEEPSEEK, CEREBRAS -> true
    }

    fun photoSupport(model: String): PhotoSupport = when (this) {
        GROQ -> if (model in GROQ_VISION) PhotoSupport.YES else PhotoSupport.NO
        DEEPSEEK -> if (model in DEEPSEEK_VISION) PhotoSupport.YES else PhotoSupport.NO
        CEREBRAS -> if (model in CEREBRAS_VISION) PhotoSupport.YES else PhotoSupport.NO
        MISTRAL -> if (model in MISTRAL_VISION) PhotoSupport.YES else PhotoSupport.UNKNOWN
    }

    companion object {
        private val GROQ_VISION = setOf("qwen/qwen3.8-27b", "meta-models/muse-spark-open", "qwen/qwen3-vl-32b-instruct",
            "meta-llama/llama-4-scout-17b-16e-instruct", "meta-llama/llama-4-maverick-17b-128e-instruct")
        private val MISTRAL_VISION = setOf("mistral-small-latest", "pixtral-large-latest")
        private val DEEPSEEK_VISION = setOf("deepseek-flash", "deepseek-v4-flash-vision-exp")
        private val CEREBRAS_VISION = setOf("qwen-3.8-27b", "gemma-4-31b", "kimi-k2.7-code", "kimi-k3", "kimi-k3.1-pro")

        fun forUrl(protocol: ProviderProtocol, network: ProviderNetwork, url: String): CloudEngine? {
            if (protocol != ProviderProtocol.OPENAI_COMPATIBLE || network != ProviderNetwork.INTERNET) return null
            return entries.firstOrNull { it.baseUrl == url || (it == DEEPSEEK && url == "${it.baseUrl}/v1") }
        }
    }
}

enum class PhotoSupport { YES, NO, UNKNOWN }

val ProviderEndpoint.cloudEngine: CloudEngine?
    get() = CloudEngine.forUrl(protocol, network, baseUrl)

val ProviderEndpoint.requiresCredential: Boolean
    get() = protocol != ProviderProtocol.OPENAI_COMPATIBLE || cloudEngine != null

fun ProviderEndpoint.acceptsCredential(present: Boolean): Boolean = present || !requiresCredential

val ProviderConfig.cloudEngine: CloudEngine?
    get() = CloudEngine.forUrl(protocol, network, baseUrl)

val ProviderConfig.photoSupport: PhotoSupport
    get() = when (protocol) {
        ProviderProtocol.APPLE_MAC -> PhotoSupport.NO
        // The API can accept images, but model families (e.g. Gemma text or custom proxies) vary.
        ProviderProtocol.GEMINI_NATIVE -> if (model.removePrefix("models/").startsWith("gemini-") &&
            listOf("embedding", "native-audio", "-tts", "-image").none { it in model }) PhotoSupport.YES else PhotoSupport.UNKNOWN
        ProviderProtocol.OPENROUTER_FREE -> PhotoSupport.UNKNOWN
        ProviderProtocol.OPENAI_COMPATIBLE -> cloudEngine?.photoSupport(model) ?: PhotoSupport.UNKNOWN
    }
