package ai.dana.domain

import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.flow.Flow

/** A grant only approves this exact set of accounts, models and types of data. It never contains a message or key. */
@JvmInline
value class SendGrant private constructor(val id: String) {
    companion object {
        fun chat(targets: List<ProviderProfile>, parallel: Boolean, context: Boolean, memory: Boolean,
            attachments: Boolean, masked: Boolean): SendGrant = digest(
            listOf("chat-v1", parallel.toString(), context.toString(), memory.toString(), attachments.toString(), masked.toString()), targets,
        )

        fun comparison(targets: List<ProviderProfile>): SendGrant = digest(listOf("compare-v1"), targets)

        fun image(profile: ProviderProfile, model: String): SendGrant = digest(listOf("image-v1", model), listOf(profile))

        private fun digest(scope: List<String>, targets: List<ProviderProfile>): SendGrant {
            val parts = scope + targets.sortedBy { it.id }.flatMap { profile ->
                with(profile.config) { listOf(profile.id, protocol.name, network.name, baseUrl, model, profile.credentialRevision) }
            }
            // Size prefixes avoid collisions between adjacent fields, including values from custom profiles.
            val encoded = parts.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)
            return SendGrant(Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(encoded)))
        }
    }
}

interface DirectSendSettings {
    val preferredEngine: Flow<String?>
    suspend fun permits(grant: SendGrant): Boolean
    suspend fun remember(grant: SendGrant)
    suspend fun forgetAll()
    /** User has explicitly tested and selected a working engine. Update preference and network setting together. */
    suspend fun activate(profile: ProviderProfile, allowDirectText: Boolean)
}
