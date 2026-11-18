package ir.aegis.core.maps

import ir.aegis.core.model.MapMarker
import ir.aegis.core.model.MarkerCategory
import ir.aegis.core.model.OfflineMapPackage
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

object VectorMapEngine {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, VectorMapBundle>()
    @Volatile
    private var atlasCache: BaseAtlasBundle? = null

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

    fun loadBaseAtlas(): BaseAtlasBundle {
        atlasCache?.let { return it }
        val stream = VectorMapEngine::class.java.getResourceAsStream("/maps/base_atlas.json")
            ?: return BaseAtlasBundle()
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val decoded = runCatching { json.decodeFromString<BaseAtlasBundle>(text) }
            .getOrElse { BaseAtlasBundle() }
        atlasCache = decoded
        return decoded
    }

    fun autoSelectNearestPack(
        lat: Double,
        lon: Double,
        packages: List<OfflineMapPackage>,
    ): OfflineMapPackage? {
        if (packages.isEmpty()) return null
        val inside = packages.firstOrNull { lat in it.minLat..it.maxLat && lon in it.minLon..it.maxLon }
        if (inside != null) return inside
        return packages.minByOrNull { pkg ->
            val cLat = (pkg.minLat + pkg.maxLat) / 2.0
            val cLon = (pkg.minLon + pkg.maxLon) / 2.0
            GeoMath.haversineMeters(lat, lon, cLat, cLon)
        }
    }

    fun clearCache() {
        cache.clear()
        atlasCache = null
    }
}

object MapAdvisorEngine {

    fun suggestNearestDestinations(
        userLat: Double,
        userLon: Double,
        markers: List<MapMarker>,
        limit: Int = 3,
    ): List<SmartMapRecommendation> {
        if (markers.isEmpty()) return emptyList()
        val desiredCategories = listOf(
            MarkerCategory.POLICE to ("Nearest Security / Cyber Support Hub" to "نزدیک‌ترین مرکز امنیتی / انتظامی"),
            MarkerCategory.HOSPITAL to ("Nearest Emergency Medical Center" to "نزدیک‌ترین مرکز درمانی / اورژانس"),
            MarkerCategory.SHELTER to ("Nearest Safe Zone / Shelter" to "نزدیک‌ترین نقطه امن و پناهگاه"),
            MarkerCategory.ASSEMBLY_POINT to ("Nearest Open Safe Ground" to "نزدیک‌ترین فضای باز امن"),
        )
        val recs = mutableListOf<SmartMapRecommendation>()
        val usedIds = mutableSetOf<String>()

        for ((cat, reasons) in desiredCategories) {
            val best = markers
                .filter { it.category == cat && it.id !in usedIds }
                .minByOrNull { GeoMath.haversineMeters(userLat, userLon, it.latitude, it.longitude) }
            if (best != null) {
                usedIds += best.id
                val d = GeoMath.haversineMeters(userLat, userLon, best.latitude, best.longitude)
                val brg = GeoMath.bearingDegrees(userLat, userLon, best.latitude, best.longitude)
                recs += SmartMapRecommendation(
                    marker = best,
                    reasonEn = reasons.first,
                    reasonFa = reasons.second,
                    distanceMeters = d,
                    bearingDegrees = brg,
                    walkMinutes = GeoMath.estimateWalkingMinutes(d),
                )
            }
        }
        return recs.sortedBy { it.distanceMeters }.take(limit)
    }
}
