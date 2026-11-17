package ai.dana.domain

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import kotlinx.coroutines.flow.Flow

enum class ProviderProtocol {
    OPENAI_COMPATIBLE,
    GEMINI_NATIVE,
    APPLE_MAC,
}

enum class ProviderNetwork {
    INTERNET,
    LOCAL_NETWORK,
}

const val GOOGLE_GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
const val APPLE_MAC_MODEL = "apple-foundation-on-device"

/** Validated independently of the model, so discovery cannot require a guessed model ID. */
@ConsistentCopyVisibility
data class ProviderEndpoint private constructor(
    val protocol: ProviderProtocol,
    val network: ProviderNetwork,
    val baseUrl: String,
) {
    val credentialScope: String get() = "${protocol.name}\u0000${network.name}\u0000$baseUrl"

    companion object {
        private val pathSegment = Regex("[a-zA-Z0-9._~-]+")

        fun create(protocol: ProviderProtocol, network: ProviderNetwork, baseUrl: String): ProviderEndpoint {
            if (protocol == ProviderProtocol.APPLE_MAC && network != ProviderNetwork.LOCAL_NETWORK) {
                reject(InputIssue.INVALID_BASE_URL)
            }
            return ProviderEndpoint(protocol, network, canonicalUrl(baseUrl))
        }

        private fun canonicalUrl(raw: String): String {
            val url = raw.trim()
            if (url.length !in 1..ProviderConfig.URL_LIMIT || url.any { it.isWhitespace() || it.isISOControl() }) {
                reject(InputIssue.INVALID_BASE_URL)
            }
            val uri = try {
                URI(url).parseServerAuthority()
            } catch (_: URISyntaxException) {
                reject(InputIssue.INVALID_BASE_URL)
            }
            val host = uri.host?.lowercase(Locale.ROOT) ?: reject(InputIssue.INVALID_BASE_URL)
            val authority = uri.rawAuthority ?: reject(InputIssue.INVALID_BASE_URL)
            if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null ||
                uri.rawQuery != null || uri.rawFragment != null || authority.endsWith(':') ||
                '%' in authority || host.endsWith('.') || (uri.port != -1 && uri.port !in 1..65_535)
            ) {
                reject(InputIssue.INVALID_BASE_URL)
            }
            val path = uri.rawPath.orEmpty().removeSuffix("/")
            if (path.isNotEmpty() && path.drop(1).split('/').any {
                    !pathSegment.matches(it) || it == "." || it == ".."
                }
            ) {
                reject(InputIssue.INVALID_BASE_URL)
            }
            val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
            return "https://$host$port$path"
        }
    }
}

@ConsistentCopyVisibility
data class ProviderConfig private constructor(
    val name: String,
    val protocol: ProviderProtocol,
    val network: ProviderNetwork,
    val baseUrl: String,
    val model: String,
) {
    val endpoint: ProviderEndpoint get() = ProviderEndpoint.create(protocol, network, baseUrl)
    // Preserve the original v0.2 credential scope for already stored Gemini/custom profiles.
    val credentialScope: String get() = "${protocol.name}\u0000${network.name}\u0000$baseUrl"

    companion object {
        const val NAME_LIMIT = 80
        const val MODEL_LIMIT = 256
        const val URL_LIMIT = 2_048

        fun create(
            name: String,
            protocol: ProviderProtocol,
            network: ProviderNetwork,
            baseUrl: String,
            model: String,
        ): ProviderConfig {
            val cleanName = name.trim()
            if (cleanName.isEmpty() || cleanName.length > NAME_LIMIT || cleanName.any(Char::isISOControl)) {
                reject(InputIssue.INVALID_PROVIDER_NAME)
            }
            val cleanModel = model.trim()
            if (cleanModel.isEmpty() || cleanModel.length > MODEL_LIMIT ||
                cleanModel.any { it.isWhitespace() || it.isISOControl() }
            ) {
                reject(InputIssue.INVALID_MODEL)
            }
            if (protocol == ProviderProtocol.APPLE_MAC && cleanModel != APPLE_MAC_MODEL) {
                reject(InputIssue.INVALID_MODEL)
            }
            val endpoint = ProviderEndpoint.create(protocol, network, baseUrl)
            return ProviderConfig(cleanName, protocol, network, endpoint.baseUrl, cleanModel)
        }
    }
}

/** Safe to place in observable UI state. hasCredential describes presence, not server validity. */
data class ProviderProfile(val id: String, val config: ProviderConfig, val hasCredential: Boolean)

class ApiCredential private constructor(val value: String) {
    override fun toString(): String = "ApiCredential([redacted])"

    companion object {
        const val LIMIT = 2_048

        fun parse(raw: String): ApiCredential {
            val candidate = raw.trim()
            if (candidate.length !in 1..LIMIT || candidate.any { it !in '!'..'~' }) {
                reject(InputIssue.INVALID_API_KEY)
            }
            return ApiCredential(candidate)
        }
    }
}

sealed interface CredentialChange {
    data object Keep : CredentialChange
    data object Remove : CredentialChange
    class Replace(val credential: ApiCredential) : CredentialChange
}

/** Changing provider, URL (including path), or network mode requires reentry/removal of a saved key. */
fun CredentialChange.validateFor(current: ProviderProfile?, destination: ProviderConfig) {
    if (this == CredentialChange.Keep && current?.hasCredential == true &&
        current.config.credentialScope != destination.credentialScope
    ) {
        reject(InputIssue.CREDENTIAL_DESTINATION_CHANGED)
    }
}

/** A read-only lookup must not forward a stored secret after the edit form changes destination. */
fun CredentialChange.validateForLookup(current: ProviderProfile?, endpoint: ProviderEndpoint) {
    if (this == CredentialChange.Keep && current?.hasCredential == true && current.config.credentialScope != endpoint.credentialScope) {
        reject(InputIssue.CREDENTIAL_DESTINATION_CHANGED)
    }
}

class CredentialStoreUnavailable(cause: Throwable? = null) :
    IllegalStateException("Credential vault unavailable", cause)

interface ProviderRepository {
    val profiles: Flow<List<ProviderProfile>>
    suspend fun save(id: String?, config: ProviderConfig, key: CredentialChange): String
    suspend fun delete(id: String)

    /** Intended for a transport with an already selected profile, never for display in the UI. */
    suspend fun credentialFor(profile: ProviderProfile): ApiCredential?
}
