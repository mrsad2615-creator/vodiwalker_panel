package ir.aegis.core.maps

import ir.aegis.core.model.MapMarker
import ir.aegis.core.model.MarkerCategory
import ir.aegis.core.model.OfflineMapPackage
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

data class GeoBBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
)

data class IndexedPolygonRing(
    val points: List<GeoCoord>,
    val bbox: GeoBBox,
)

data class IndexedAtlasCountry(
    val name: String,
    val rings: List<IndexedPolygonRing>,
    val bbox: GeoBBox,
)

data class IndexedAtlasProvince(
    val nameEn: String,
    val nameFa: String,
    val centerLat: Double,
    val centerLon: Double,
    val rings: List<IndexedPolygonRing>,
    val bbox: GeoBBox,
)

data class IndexedBaseAtlas(
    val countries: List<IndexedAtlasCountry> = emptyList(),
    val iranProvinces: List<IndexedAtlasProvince> = emptyList(),
    val cities: List<AtlasCity> = emptyList(),
    val nationalHighways: List<List<GeoCoord>> = emptyList(),
)

object VectorMapEngine {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, VectorMapBundle>()

    @Volatile
    private var atlasCache: BaseAtlasBundle? = null

    @Volatile
    private var indexedAtlasCache: IndexedBaseAtlas? = null

    @Volatile
    private var assetOpener: ((String) -> InputStream?)? = null

    fun registerAssetOpener(opener: (String) -> InputStream?) {
        assetOpener = opener
    }

    private fun openStream(relativePath: String): InputStream? {
        val clean = relativePath.removePrefix("/")
        return assetOpener?.invoke(clean)
            ?: VectorMapEngine::class.java.getResourceAsStream("/$clean")
            ?: Thread.currentThread().contextClassLoader?.getResourceAsStream(clean)
    }

    fun loadBundle(packageId: String): VectorMapBundle {
        return cache.getOrPut(packageId) {
            val stream = openStream("maps/$packageId.json")
                ?: return@getOrPut VectorMapBundle(packageId = packageId)
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            runCatching { json.decodeFromString<VectorMapBundle>(text) }
                .getOrElse { VectorMapBundle(packageId = packageId) }
        }
    }

    fun loadBaseAtlas(): BaseAtlasBundle {
        atlasCache?.let { return it }
        val stream = openStream("maps/base_atlas.json")
            ?: return BaseAtlasBundle()
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val decoded = runCatching { json.decodeFromString<BaseAtlasBundle>(text) }
            .getOrElse { BaseAtlasBundle() }
        atlasCache = decoded
        return decoded
    }

    fun loadIndexedBaseAtlas(): IndexedBaseAtlas {
        indexedAtlasCache?.let { return it }
        val raw = loadBaseAtlas()
        val indexedCountries = raw.countries.mapNotNull { c ->
            val irings = c.rings.mapNotNull { r ->
                if (r.size < 3) null else IndexedPolygonRing(r, computeBBox(r))
            }
            if (irings.isEmpty()) null else IndexedAtlasCountry(c.name, irings, mergeBBoxes(irings.map { it.bbox }))
        }
        val indexedProvs = raw.iranProvinces.mapNotNull { p ->
            val irings = p.rings.mapNotNull { r ->
                if (r.size < 3) null else IndexedPolygonRing(r, computeBBox(r))
            }
            if (irings.isEmpty()) null else IndexedAtlasProvince(p.nameEn, p.nameFa, p.centerLat, p.centerLon, irings, mergeBBoxes(irings.map { it.bbox }))
        }
        val built = IndexedBaseAtlas(
            countries = indexedCountries,
            iranProvinces = indexedProvs,
            cities = raw.cities,
            nationalHighways = raw.nationalHighways,
        )
        indexedAtlasCache = built
        return built
    }

    private fun computeBBox(pts: List<GeoCoord>): GeoBBox {
        var minLat = 90.0
        var maxLat = -90.0
        var minLon = 180.0
        var maxLon = -180.0
        for (p in pts) {
            if (p.lat < minLat) minLat = p.lat
            if (p.lat > maxLat) maxLat = p.lat
            if (p.lon < minLon) minLon = p.lon
            if (p.lon > maxLon) maxLon = p.lon
        }
        return GeoBBox(minLat, maxLat, minLon, maxLon)
    }

    private fun mergeBBoxes(boxes: List<GeoBBox>): GeoBBox {
        var minLat = 90.0
        var maxLat = -90.0
        var minLon = 180.0
        var maxLon = -180.0
        for (b in boxes) {
            if (b.minLat < minLat) minLat = b.minLat
            if (b.maxLat > maxLat) maxLat = b.maxLat
            if (b.minLon < minLon) minLon = b.minLon
            if (b.maxLon > maxLon) maxLon = b.maxLon
        }
        return GeoBBox(minLat, maxLat, minLon, maxLon)
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
        indexedAtlasCache = null
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
            MarkerCategory.POLICE to ("Nearest Security Hub" to "نزدیک‌ترین مرکز امنیتی / انتظامی"),
            MarkerCategory.HOSPITAL to ("Nearest Medical Emergency" to "نزدیک‌ترین بیمارستان / اورژانس"),
            MarkerCategory.SHELTER to ("Nearest Safe Zone" to "نزدیک‌ترین نقطه امن / پناهگاه"),
            MarkerCategory.ASSEMBLY_POINT to ("Nearest Open Ground" to "نزدیک‌ترین فضای باز امن"),
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
