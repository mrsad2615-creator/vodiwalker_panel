package ir.aegis.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class MarkerCategory {
    SHELTER,
    HOSPITAL,
    FIRE_STATION,
    POLICE,
    WATER_POINT,
    ASSEMBLY_POINT,
    HAZARD,
    CUSTOM,
}

@Serializable
data class OfflineMapPackage(
    val id: String,
    val regionName: String,
    val regionCode: String,
    val description: String,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val sizeBytes: Long,
    val tileCount: Int,
    val versionTag: String,
    val sha256Checksum: String,
    val isBundled: Boolean,
    val isInstalled: Boolean,
    val updatedAtMillis: Long,
)

@Serializable
data class MapMarker(
    val id: String,
    val packageId: String,
    val title: String,
    val description: String,
    val category: MarkerCategory,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double = 0.0,
    val isEmergencyResource: Boolean = false,
    val isCustom: Boolean = true,
    val createdAtMillis: Long,
)

@Serializable
data class LatLonPoint(
    val latitude: Double,
    val longitude: Double,
    val label: String = "",
)

@Serializable
data class SavedRoute(
    val id: String,
    val packageId: String,
    val name: String,
    val description: String,
    val waypoints: List<LatLonPoint>,
    val totalDistanceMeters: Double,
    val estimatedWalkMinutes: Int,
    val hazardLevel: String,
    val createdAtMillis: Long,
)
