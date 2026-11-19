package ai.dana.domain

/** The release operator enables these catalog entries. An email identity alone is not an entitlement. */
enum class ManagedKind { CHAT, IMAGE }

data class ManagedModel(val id: String, val title: String, val provider: String, val kind: ManagedKind, val units: Int, val quality: String?)

data class ManagedCatalog(val models: List<ManagedModel>, val planner: ManagedModel?, val dailyRemaining: Int)

enum class ManagedProblem { SIGN_IN, NOT_ENABLED, QUOTA, BUSY, DUPLICATE, MODEL_UNAVAILABLE, OFFLINE, TRANSPORT, RESPONSE }

class ManagedFailure(val problem: ManagedProblem) : Exception(problem.name)

/** No provider credentials, custom destinations or Android effects are part of this contract. */
interface ManagedCloud {
    companion object {
        const val CHAT_PROMPT_LIMIT = 2_400
        const val COMMAND_LIMIT = 600
        const val ANSWER_LIMIT = 16_000

        fun acceptsText(text: String, limit: Int): Boolean = text.length in 1..limit && text.isNotBlank() && text.isSafeText()
        fun acceptsChat(input: ConversationInput): Boolean =
            acceptsText(input.prompt, CHAT_PROMPT_LIMIT) && input.photo == null && input.facts.isEmpty() && !input.probe && !input.forMessageReview &&
                input.history.size in setOf(0, 2, 4, 6) && input.history.sumOf { it.body.length } <= ChatLimits.CONTEXT_CHARS &&
                input.history.withIndex().all { (i, turn) -> turn.role == (if (i % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT) &&
                    acceptsText(turn.body, ChatLimits.CONTEXT_CHARS) }
    }

    val configured: Boolean
    suspend fun catalog(): ManagedCatalog
    suspend fun chat(model: ManagedModel, input: ConversationInput, requestId: String): String
    suspend fun image(model: ManagedModel, prompt: String, shape: ArtShape, requestId: String): ArtImage
    suspend fun command(model: ManagedModel, prompt: String, requestId: String): PhoneIntent
}
