package ai.dana.domain

/** The release operator enables these catalog entries. An email identity alone is not an entitlement. */
enum class ManagedKind { CHAT, IMAGE }

data class ManagedModel(val id: String, val title: String, val provider: String, val kind: ManagedKind, val units: Int, val quality: String?)

data class ManagedCatalog(val models: List<ManagedModel>, val planner: ManagedModel?, val dailyRemaining: Int)

enum class ManagedProblem { SIGN_IN, NOT_ENABLED, QUOTA, BUSY, DUPLICATE, MODEL_UNAVAILABLE, OFFLINE, TRANSPORT, RESPONSE }

class ManagedFailure(val problem: ManagedProblem) : Exception(problem.name)

/** No provider credentials, custom destinations or Android effects are part of this contract. */
interface ManagedCloud {
    companion object { const val CHAT_PROMPT_LIMIT = 2_400; const val COMMAND_LIMIT = 600 }

    val configured: Boolean
    suspend fun catalog(): ManagedCatalog
    suspend fun chat(model: ManagedModel, input: ConversationInput, requestId: String): String
    suspend fun image(model: ManagedModel, prompt: String, shape: ArtShape, requestId: String): ArtImage
    suspend fun command(model: ManagedModel, prompt: String, requestId: String): PhoneIntent
}
