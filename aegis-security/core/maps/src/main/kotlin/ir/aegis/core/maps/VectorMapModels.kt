package ir.aegis.core.maps

import ir.aegis.core.model.LatLonPoint
import ir.aegis.core.model.MapMarker
import kotlinx.serialization.Serializable

@Serializable
data class GeoCoord(
    val lat: Double,
    val lon: Double,
)

@Serializable
data class VectorPolyline(
    val id: String,
    val nameEn: String,
    val nameFa: String,
    val widthMeters: Float = 14f,
    val points: List<GeoCoord>,
)

@Serializable
data class VectorPolygon(
    val id: String,
    val nameEn: String,
    val nameFa: String,
    val kind: String = "park",
    val points: List<GeoCoord>,
)

@Serializable
data class ElevationContour(
    val elevationMeters: Int,
    val points: List<GeoCoord>,
)

@Serializable
data class VectorBuilding(
    val id: String,
    val nameEn: String,
    val nameFa: String,
    val kind: String = "civic",
    val heightMeters: Double = 28.0,
    val footprint: List<GeoCoord>,
)

@Serializable
data class VectorRoad(
    val id: String,
    val nameEn: String,
    val nameFa: String,
    val tier: String,
    val points: List<GeoCoord>,
)

@Serializable
data class DistrictLabel(
    val nameEn: String,
    val nameFa: String,
    val lat: Double,
    val lon: Double,
    val importance: Int = 2,
)

@Serializable
data class RoadGraphNode(
    val id: String,
    val labelEn: String,
    val labelFa: String,
    val lat: Double,
    val lon: Double,
)

@Serializable
data class RoadGraphEdge(
    val fromId: String,
    val toId: String,
    val roadNameEn: String,
    val roadNameFa: String,
    val isWideCorridor: Boolean = true,
)

@Serializable
data class VectorMapBundle(
    val packageId: String,
    val baseElevationMeters: Double = 1150.0,
    val waterways: List<VectorPolyline> = emptyList(),
    val waterBodies: List<VectorPolygon> = emptyList(),
    val parks: List<VectorPolygon> = emptyList(),
    val contours: List<ElevationContour> = emptyList(),
    val buildings: List<VectorBuilding> = emptyList(),
    val roads: List<VectorRoad> = emptyList(),
    val districts: List<DistrictLabel> = emptyList(),
    val graphNodes: List<RoadGraphNode> = emptyList(),
    val graphEdges: List<RoadGraphEdge> = emptyList(),
)

@Serializable
data class AtlasCountry(
    val name: String,
    val rings: List<List<GeoCoord>>,
)

@Serializable
data class AtlasProvince(
    val nameEn: String,
    val nameFa: String,
    val centerLat: Double,
    val centerLon: Double,
    val rings: List<List<GeoCoord>>,
)

@Serializable
data class AtlasCity(
    val nameEn: String,
    val nameFa: String,
    val lat: Double,
    val lon: Double,
    val tier: Int = 2,
)

@Serializable
data class BaseAtlasBundle(
    val countries: List<AtlasCountry> = emptyList(),
    val iranProvinces: List<AtlasProvince> = emptyList(),
    val cities: List<AtlasCity> = emptyList(),
    val nationalHighways: List<List<GeoCoord>> = emptyList(),
)

data class OfflineNavigationStep(
    val instructionEn: String,
    val instructionFa: String,
    val distanceMeters: Double,
    val bearingDegrees: Double,
)

data class OfflineRouteResult(
    val waypoints: List<LatLonPoint>,
    val steps: List<OfflineNavigationStep>,
    val totalDistanceMeters: Double,
    val estimatedWalkMinutes: Int,
    val avoidedHazardCount: Int,
    val isGraphRouted: Boolean,
)

data class SmartMapRecommendation(
    val marker: MapMarker,
    val reasonEn: String,
    val reasonFa: String,
    val distanceMeters: Double,
    val bearingDegrees: Double,
    val walkMinutes: Int,
)
