package ir.aegis.core.maps

import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

object VectorMapEngine {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, VectorMapBundle>()

    fun loadBundle(packageId: String): VectorMapBundle {
        return cache.getOrPut(packageId) {
            val resourcePath = "/maps/$packageId.json"
            val stream = VectorMapEngine::class.java.getResourceAsStream(resourcePath)
                ?: return@getOrPut VectorMapBundle(packageId = packageId)
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            runCatching { json.decodeFromString<VectorMapBundle>(text) }
                .getOrElse { VectorMapBundle(packageId = packageId) }
        }
    }

    fun clearCache() {
        cache.clear()
    }
}
