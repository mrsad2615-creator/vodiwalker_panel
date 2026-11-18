package ir.aegis.core.maps

import ir.aegis.core.crypto.CryptoEngine
import ir.aegis.core.model.LatLonPoint
import ir.aegis.core.model.MapMarker
import ir.aegis.core.model.MarkerCategory
import ir.aegis.core.model.OfflineMapPackage
import ir.aegis.core.model.SavedRoute

object OfflineMapSeeds {
    private const val SEED_TIME = 1763400000000L

    fun bundledPackages(): List<OfflineMapPackage> {
        val raw = listOf(
            OfflineMapPackage(
                id = "pack-tehran-grid",
                regionName = "Tehran Central Emergency Grid (Offline Bundle)",
                regionCode = "IR-THR-01",
                description = "Bundled offline vector grid: hospitals, shelters, water hubs, fire stations & assembly corridors.",
                minLat = 35.6400,
                maxLat = 35.7550,
                minLon = 51.3100,
                maxLon = 51.4600,
                sizeBytes = 14_820_000L,
                tileCount = 420,
                versionTag = "2026.11-OFFLINE",
                sha256Checksum = "",
                isBundled = true,
                isInstalled = true,
                updatedAtMillis = SEED_TIME,
            ),
            OfflineMapPackage(
                id = "pack-bavaria-nuremberg",
                regionName = "Nuremberg / Franconia Emergency Grid (Offline Bundle)",
                regionCode = "DE-BY-NUE",
                description = "Bundled European regional offline pack with flood zones, shelters & clinic coordinates.",
                minLat = 49.4000,
                maxLat = 49.5000,
                minLon = 11.0100,
                maxLon = 11.1500,
                sizeBytes = 11_450_000L,
                tileCount = 312,
                versionTag = "2026.11-OFFLINE",
                sha256Checksum = "",
                isBundled = true,
                isInstalled = true,
                updatedAtMillis = SEED_TIME,
            ),
            OfflineMapPackage(
                id = "pack-alborz-corridor",
                regionName = "Alborz Northern Ridge Evacuation Pack",
                regionCode = "IR-ALB-02",
                description = "Mountain rescue trails, high-ground assembly shelters & seismic safe plateaus.",
                minLat = 35.7600,
                maxLat = 35.8700,
                minLon = 51.3400,
                maxLon = 51.4900,
                sizeBytes = 9_640_000L,
                tileCount = 268,
                versionTag = "2026.11-OFFLINE",
                sha256Checksum = "",
                isBundled = true,
                isInstalled = false,
                updatedAtMillis = SEED_TIME,
            ),
        )
        return raw.map { pkg ->
            pkg.copy(sha256Checksum = CryptoEngine.sha256Hex("${pkg.id}|${pkg.regionCode}|${pkg.versionTag}|${pkg.tileCount}"))
        }
    }

    fun bundledMarkers(): List<MapMarker> = listOf(
        MapMarker(
            id = "mk-thr-hosp-1",
            packageId = "pack-tehran-grid",
            title = "Central Trauma Hospital (Offline POI)",
            description = "24/7 trauma & triage emergency point (bundled offline reference)",
            category = MarkerCategory.HOSPITAL,
            latitude = 35.7024,
            longitude = 51.3912,
            elevationMeters = 1198.0,
            isEmergencyResource = true,
            isCustom = false,
            createdAtMillis = SEED_TIME,
        ),
        MapMarker(
            id = "mk-thr-shelter-1",
            packageId = "pack-tehran-grid",
            title = "Laleh Park Open Assembly Shelter",
            description = "Seismic open-ground assembly area away from tall glass facades",
            category = MarkerCategory.ASSEMBLY_POINT,
            latitude = 35.7106,
            longitude = 51.3919,
            elevationMeters = 1225.0,
            isEmergencyResource = true,
            isCustom = false,
            createdAtMillis = SEED_TIME,
        ),
        MapMarker(
            id = "mk-thr-fire-1",
            packageId = "pack-tehran-grid",
            title = "Fire Station Sector 4",
            description = "Urban search & rescue hub",
            category = MarkerCategory.FIRE_STATION,
            latitude = 35.6941,
            longitude = 51.3755,
            elevationMeters = 1185.0,
            isEmergencyResource = true,
            isCustom = false,
            createdAtMillis = SEED_TIME,
        ),
        MapMarker(
            id = "mk-thr-water-1",
            packageId = "pack-tehran-grid",
            title = "Municipal Emergency Water Cistern",
            description = "Reserve potable distribution point",
            category = MarkerCategory.WATER_POINT,
            latitude = 35.6845,
            longitude = 51.4040,
            elevationMeters = 1164.0,
            isEmergencyResource = true,
            isCustom = false,
            createdAtMillis = SEED_TIME,
        ),
        MapMarker(
            id = "mk-thr-shelter-2",
            packageId = "pack-tehran-grid",
            title = "South Underground Reinforced Shelter",
            description = "Covered shelter hub for extreme weather / fallout protection",
            category = MarkerCategory.SHELTER,
            latitude = 35.6762,
            longitude = 51.3678,
            elevationMeters = 1140.0,
            isEmergencyResource = true,
            isCustom = false,
            createdAtMillis = SEED_TIME,
        ),
        MapMarker(
            id = "mk-thr-police-1",
            packageId = "pack-tehran-grid",
            title = "District Civil Security Post",
            description = "Civil emergency coordination post",
            category = MarkerCategory.POLICE,
            latitude = 35.6898,
            longitude = 51.4180,
            elevationMeters = 1179.0,
            isEmergencyResource = true,
            isCustom = false,
            createdAtMillis = SEED_TIME,
        ),
        MapMarker(
            id = "mk-nue-hosp-1",
            packageId = "pack-bavaria-nuremberg",
            title = "Klinikum Emergency Center",
            description = "Offline regional medical facility marker",
            category = MarkerCategory.HOSPITAL,
            latitude = 49.4590,
            longitude = 11.0630,
            elevationMeters = 315.0,
            isEmergencyResource = true,
            isCustom = false,
            createdAtMillis = SEED_TIME,
        ),
        MapMarker(
            id = "mk-nue-shelter-1",
            packageId = "pack-bavaria-nuremberg",
            title = "Wöhrder Wiese High-Ground Assembly",
            description = "Elevated assembly zone above Pegnitz flood plain",
            category = MarkerCategory.ASSEMBLY_POINT,
            latitude = 49.4525,
            longitude = 11.0955,
            elevationMeters = 328.0,
            isEmergencyResource = true,
            isCustom = false,
            createdAtMillis = SEED_TIME,
        ),
        MapMarker(
            id = "mk-nue-fire-1",
            packageId = "pack-bavaria-nuremberg",
            title = "Feuerwache Central Dispatch",
            description = "Fire & rescue station (offline reference)",
            category = MarkerCategory.FIRE_STATION,
            latitude = 49.4465,
            longitude = 11.0760,
            elevationMeters = 312.0,
            isEmergencyResource = true,
            isCustom = false,
            createdAtMillis = SEED_TIME,
        ),
        MapMarker(
            id = "mk-alb-shelter-1",
            packageId = "pack-alborz-corridor",
            title = "Ridge Basecamp Safe Shelter",
            description = "High-elevation trail shelter & radio relay point",
            category = MarkerCategory.SHELTER,
            latitude = 35.8120,
            longitude = 51.4100,
            elevationMeters = 1860.0,
            isEmergencyResource = true,
            isCustom = false,
            createdAtMillis = SEED_TIME,
        ),
    )

    fun bundledRoutes(): List<SavedRoute> {
        val thrPts = listOf(
            LatLonPoint(35.6892, 51.3890, "City Center Anchor"),
            LatLonPoint(35.6941, 51.3755, "Fire Sector 4"),
            LatLonPoint(35.7024, 51.3912, "Central Trauma Hospital"),
            LatLonPoint(35.7106, 51.3919, "Laleh Assembly Park"),
        )
        val thrDist = GeoMath.computeRouteDistanceMeters(thrPts)

        val nuePts = listOf(
            LatLonPoint(49.4465, 11.0760, "Central Fire Dispatch"),
            LatLonPoint(49.4525, 11.0955, "Wöhrder High Ground"),
            LatLonPoint(49.4590, 11.0630, "Klinikum Emergency"),
        )
        val nueDist = GeoMath.computeRouteDistanceMeters(nuePts)

        return listOf(
            SavedRoute(
                id = "route-thr-evac",
                packageId = "pack-tehran-grid",
                name = "Primary North Evacuation Corridor",
                description = "Wide-boulevard foot evacuation avoiding narrow historic alleys",
                waypoints = thrPts,
                totalDistanceMeters = thrDist,
                estimatedWalkMinutes = GeoMath.estimateWalkingMinutes(thrDist),
                hazardLevel = "LOW",
                createdAtMillis = SEED_TIME,
            ),
            SavedRoute(
                id = "route-nue-flood",
                packageId = "pack-bavaria-nuremberg",
                name = "Pegnitz High-Ground Route",
                description = "Elevated bypass route during river flood alert",
                waypoints = nuePts,
                totalDistanceMeters = nueDist,
                estimatedWalkMinutes = GeoMath.estimateWalkingMinutes(nueDist),
                hazardLevel = "LOW",
                createdAtMillis = SEED_TIME,
            ),
        )
    }
}
