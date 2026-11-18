package ai.dana.data.network

import ai.dana.data.network.EngineProtocol.string
import ai.dana.domain.APPLE_MAC_MODEL
import ai.dana.domain.ApiCredential
import ai.dana.domain.DirectoryModel
import ai.dana.domain.EngineFailure
import ai.dana.domain.EngineProblem
import ai.dana.domain.ModelDirectory
import ai.dana.domain.OPENROUTER_FREE_MODEL
import ai.dana.domain.ProviderConfig
import ai.dana.domain.ProviderEndpoint
import ai.dana.domain.ProviderNetwork
import ai.dana.domain.ProviderProtocol
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class OkHttpModelDirectory(
    private val client: OkHttpClient,
    private val lanClient: OkHttpClient = client.privateLanClient(),
) : ModelDirectory {
    override suspend fun list(endpoint: ProviderEndpoint, key: ApiCredential?): List<DirectoryModel> =
        withTimeoutOrNull(40_000) { collect(endpoint, key) } ?: throw EngineFailure(EngineProblem.TIMEOUT)

    private suspend fun collect(endpoint: ProviderEndpoint, key: ApiCredential?): List<DirectoryModel> {
        val protocol = endpoint.protocol
        if (protocol == ProviderProtocol.OPENROUTER_FREE) {
            // Credential check only: this route has one fixed model and requires no priced catalog.
            val builder = Request.Builder().url(EngineProtocol.url(endpoint).addPathSegment("key").build())
                .get().header("Accept", "application/json")
            EngineProtocol.authenticate(builder, protocol, key)
            val root = client.readCatalog(builder.build(), protocol)
            if (root["data"] !is JsonObject) throw EngineFailure(EngineProblem.PROTOCOL)
            return listOf(DirectoryModel(OPENROUTER_FREE_MODEL, "OpenRouter Free"))
        }
        val gemini = protocol == ProviderProtocol.GEMINI_NATIVE
        val models = LinkedHashMap<String, DirectoryModel>()
        val usedTokens = mutableSetOf<String>()
        var token: String? = null
        repeat(MAX_PAGES) {
            val url = EngineProtocol.url(endpoint).addPathSegment("models")
            if (gemini) {
                url.addQueryParameter("pageSize", "100")
                token?.let { url.addQueryParameter("pageToken", it) }
            }
            val builder = Request.Builder().url(url.build()).get().header("Accept", "application/json")
            EngineProtocol.authenticate(builder, protocol, key)
            val http = if (endpoint.network == ProviderNetwork.LOCAL_NETWORK) lanClient else client
            val root = http.readCatalog(builder.build(), protocol)
            val field = root[if (gemini) "models" else "data"]
            val entries = when {
                field is JsonArray -> field
                // An empty repeated field may be omitted by Protobuf REST serialization.
                gemini && field == null -> JsonArray(emptyList())
                else -> throw EngineFailure(EngineProblem.PROTOCOL)
            }
            for (entry in entries) {
                val model = (entry as? JsonObject)?.toModel(protocol) ?: continue
                models.putIfAbsent(model.id, model)
                if (models.size > MAX_MODELS) throw EngineFailure(EngineProblem.PROTOCOL)
            }
            token = if (gemini) root["nextPageToken"].string()?.takeUnless { it.isEmpty() } else null
            val next = token ?: return models.values.sortedBy { it.id }
            if (next.length > MAX_TOKEN_CHARS || next.any(Char::isISOControl) || !usedTokens.add(next)) {
                throw EngineFailure(EngineProblem.PROTOCOL)
            }
        }
        throw EngineFailure(EngineProblem.PROTOCOL)
    }

    private fun JsonObject.toModel(protocol: ProviderProtocol): DirectoryModel? {
        val gemini = protocol == ProviderProtocol.GEMINI_NATIVE
        val id = this[if (gemini) "name" else "id"].string() ?: return null
        if (id.isEmpty() || id.length > ProviderConfig.MODEL_LIMIT || id.any { it.isWhitespace() || it.isISOControl() || it in BIDI }) return null
        if (gemini) {
            if (!id.startsWith("models/") || !EngineProtocol.geminiId.matches(id.removePrefix("models/"))) return null
            val methods = this["supportedGenerationMethods"] as? JsonArray ?: return null
            if (methods.none { it.string() == "generateContent" || it.string() == "streamGenerateContent" }) return null
        }
        if (protocol == ProviderProtocol.APPLE_MAC && id != APPLE_MAC_MODEL) return null
        val label = this["displayName"].string()?.takeUnless { it.isBlank() || it.any { c -> c.isISOControl() || c in BIDI } }
        return DirectoryModel(id, label?.take(120) ?: id)
    }

    private suspend fun OkHttpClient.readCatalog(request: Request, protocol: ProviderProtocol): JsonObject =
        suspendCancellableCoroutine { completion ->
            val call = newCall(request)
            completion.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    completion.resumeWith(Result.failure(e.toEngineFailure()))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use {
                            it.requireSuccess(protocol)
                            val type = it.body.contentType()
                            if (type?.subtype != "json") throw EngineFailure(EngineProblem.PROTOCOL)
                            Result.success(EngineProtocol.decodeObject(it.body.boundedUtf8(1_048_576)))
                        }
                    } catch (error: EngineFailure) {
                        Result.failure(error)
                    } catch (error: IOException) {
                        Result.failure(error.toEngineFailure())
                    } catch (_: Exception) {
                        Result.failure(EngineFailure(EngineProblem.PROTOCOL))
                    }
                    completion.resumeWith(result)
                }
            })
        }

    companion object {
        private const val MAX_PAGES = 6
        private const val MAX_MODELS = 600
        private const val MAX_TOKEN_CHARS = 1_024
        private val BIDI = ('\u202A'..'\u202E').toSet() + ('\u2066'..'\u2069')
    }
}
