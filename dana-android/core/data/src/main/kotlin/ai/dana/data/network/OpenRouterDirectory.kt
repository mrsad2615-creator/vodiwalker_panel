package ai.dana.data.network

import ai.dana.data.network.EngineProtocol.string
import ai.dana.domain.ProviderProtocol
import ai.dana.domain.RouterFreeModel
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull
import ai.dana.domain.EngineFailure
import ai.dana.domain.EngineProblem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

class OpenRouterDirectory internal constructor(private val client: OkHttpClient) {
    /** Public list only: never includes a bearer, conversation content, or local model data. */
    suspend fun freeTextModels(): List<RouterFreeModel> = withTimeoutOrNull(22.seconds) {
        val request = Request.Builder().url("https://openrouter.ai/api/v1/models?output_modalities=text&limit=500")
            .header("Accept", "application/json").build()
        client.json(request, ProviderProtocol.OPENROUTER_FREE, 5_242_880) { root ->
            val rows = root["data"] as? JsonArray ?: throw EngineFailure(EngineProblem.PROTOCOL)
            rows.mapNotNull { item ->
                val row = item as? JsonObject ?: return@mapNotNull null
                val id = row["id"].string() ?: return@mapNotNull null
                val name = row["name"].string() ?: return@mapNotNull null
                val arch = row["architecture"] as? JsonObject ?: return@mapNotNull null
                val input = arch["input_modalities"] as? JsonArray ?: return@mapNotNull null
                val output = arch["output_modalities"] as? JsonArray ?: return@mapNotNull null
                val endpoints = row["endpoints"] as? JsonArray
                val pricing = row["pricing"] as? JsonObject ?: return@mapNotNull null
                fun zero(key: String): Boolean = pricing[key].string()?.toBigDecimalOrNull()?.signum() == 0
                if (input.none { it.string() == "text" } || output.none { it.string() == "text" } ||
                    endpoints?.none { it.string() == "/api/v1/chat/completions" } == true || !zero("prompt") || !zero("completion") ||
                    ("request" in pricing && !zero("request"))) return@mapNotNull null
                RouterFreeModel.fromCatalog(id, name)
            }.distinctBy { it.id }.sortedBy { it.name }.take(100)
        }
    } ?: throw EngineFailure(EngineProblem.TIMEOUT)
}
