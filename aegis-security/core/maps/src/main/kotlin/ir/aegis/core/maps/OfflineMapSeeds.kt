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
                regionName = "Tehran Central Vector Grid (تهران مرکزی)",
                regionCode = "IR-THR-01",
                description = "Multi-layer offline streets, highways, 3D landmarks, hospitals, shelters, water hubs & safe routing graph.",
                minLat = 35.6400,
                maxLat = 35.7550,
                minLon = 51.3100,
                maxLon = 51.4600,
                sizeBytes = 14_820_000L,
                tileCount = 420,
                versionTag = "2026.11-VECTOR-V2",
                sha256Checksum = "",
                isBundled = true,
                isInstalled = true,
                updatedAtMillis = SEED_TIME,
            ),
            OfflineMapPackage(
                id = "pack-bavaria-nuremberg",
                regionName = "Nuremberg / Franconia Grid (نورنبرگ)",
                regionCode = "DE-BY-NUE",
                description = "Full vector streets, Pegnitz flood contours, Wöhrder Wiese assembly zone & Klinikum emergency corridors.",
                minLat = 49.4000,
                maxLat = 49.5000,
                minLon = 11.0100,
                maxLon = 11.1500,
                sizeBytes = 11_450_000L,
                tileCount = 312,
                versionTag = "2026.11-VECTOR-V2",
                sha256Checksum = "",
                isBundled = true,
                isInstalled = true,
                updatedAtMillis = SEED_TIME,
            ),
            OfflineMapPackage(
                id = "pack-alborz-corridor",
                regionName = "Alborz Northern Ridge Trails (البرز و دربند)",
                regionCode = "IR-ALB-02",
                description = "Alpine elevation contours (1650m–2750m), Darband/Tochal rescue trails & high-ground shelters.",
                minLat = 35.7600,
                maxLat = 35.8700,
                minLon = 51.3400,
                maxLon = 51.4900,
                sizeBytes = 9_640_000L,
                tileCount = 268,
                versionTag = "2026.11-VECTOR-V2",
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
        // Tehran Vector POIs
        MapMarker("mk-thr-hosp-1", "pack-tehran-grid", "Imam Khomeini Trauma Hospital", "24/7 Level-1 Trauma & Emergency Complex (Keshavarz West)", MarkerCategory.HOSPITAL, 35.7062, 51.3775, 1198.0, true, false, SEED_TIME),
        MapMarker("mk-thr-hosp-2", "pack-tehran-grid", "Shariati Medical Center", "Emergency triage & ICU facility near Karegar North", MarkerCategory.HOSPITAL, 35.7220, 51.3885, 1214.0, true, false, SEED_TIME),
        MapMarker("mk-thr-hosp-3", "pack-tehran-grid", "Sina Emergency Trauma Center", "Historic central emergency trauma hospital (Imam Khomeini St)", MarkerCategory.HOSPITAL, 35.6862, 51.4182, 1168.0, true, false, SEED_TIME),
        MapMarker("mk-thr-shelter-1", "pack-tehran-grid", "Laleh Park Open Assembly", "Primary seismic open-ground assembly area away from tall glass facades", MarkerCategory.ASSEMBLY_POINT, 35.7106, 51.3919, 1208.0, true, false, SEED_TIME),
        MapMarker("mk-thr-shelter-2", "pack-tehran-grid", "South Underground Shelter", "Reinforced underground civil shelter (Navvab South)", MarkerCategory.SHELTER, 35.6762, 51.3678, 1142.0, true, false, SEED_TIME),
        MapMarker("mk-thr-shelter-3", "pack-tehran-grid", "Park-e Shahr Safe Assembly", "Large open assembly park near central district", MarkerCategory.ASSEMBLY_POINT, 35.6836, 51.4112, 1158.0, true, false, SEED_TIME),
        MapMarker("mk-thr-shelter-4", "pack-tehran-grid", "Pardisan High-Ground Shelter", "Wide elevated open meadow away from dense blocks", MarkerCategory.SHELTER, 35.7475, 51.3560, 1280.0, true, false, SEED_TIME),
        MapMarker("mk-thr-fire-1", "pack-tehran-grid", "Fire & Rescue Station 4", "Heavy urban search & rescue depot (Azadi / Navvab)", MarkerCategory.FIRE_STATION, 35.6941, 51.3755, 1185.0, true, false, SEED_TIME),
        MapMarker("mk-thr-fire-2", "pack-tehran-grid", "Fire Dispatch Haft-e Tir", "Rapid fire suppression unit near Karimkhan", MarkerCategory.FIRE_STATION, 35.7156, 51.4258, 1212.0, true, false, SEED_TIME),
        MapMarker("mk-thr-water-1", "pack-tehran-grid", "Valiasr Potable Water Cistern", "Municipal reserve potable water distribution hub", MarkerCategory.WATER_POINT, 35.7010, 51.4068, 1192.0, true, false, SEED_TIME),
        MapMarker("mk-thr-water-2", "pack-tehran-grid", "Milad Sector Water Reservoir", "High-pressure northern emergency cistern", MarkerCategory.WATER_POINT, 35.7448, 51.3755, 1295.0, true, false, SEED_TIME),
        MapMarker("mk-thr-police-1", "pack-tehran-grid", "Baharestan Civil Security Hub", "Emergency civil coordination post", MarkerCategory.POLICE, 35.6898, 51.4305, 1179.0, true, false, SEED_TIME),

        // Nuremberg Vector POIs
        MapMarker("mk-nue-hosp-1", "pack-bavaria-nuremberg", "Klinikum Nord Emergency", "Regional trauma & medical emergency hub", MarkerCategory.HOSPITAL, 49.4590, 11.0630, 315.0, true, false, SEED_TIME),
        MapMarker("mk-nue-shelter-1", "pack-bavaria-nuremberg", "Wöhrder Wiese High-Ground", "Elevated assembly zone above Pegnitz flood plain", MarkerCategory.ASSEMBLY_POINT, 49.4525, 11.0955, 328.0, true, false, SEED_TIME),
        MapMarker("mk-nue-shelter-2", "pack-bavaria-nuremberg", "Kaiserburg Safe Plateau", "High sandstone elevation above Altstadt", MarkerCategory.SHELTER, 49.4578, 11.0760, 352.0, true, false, SEED_TIME),
        MapMarker("mk-nue-shelter-3", "pack-bavaria-nuremberg", "Stadtpark Assembly Zone", "Open green park assembly center", MarkerCategory.ASSEMBLY_POINT, 49.4648, 11.0882, 324.0, true, false, SEED_TIME),
        MapMarker("mk-nue-fire-1", "pack-bavaria-nuremberg", "Feuerwache Central Dispatch", "Central fire & civil rescue station", MarkerCategory.FIRE_STATION, 49.4465, 11.0760, 312.0, true, false, SEED_TIME),
        MapMarker("mk-nue-water-1", "pack-bavaria-nuremberg", "Hauptbahnhof Supply Hub", "Emergency drinking water & transit hub", MarkerCategory.WATER_POINT, 49.4458, 11.0825, 311.0, true, false, SEED_TIME),

        // Alborz Corridor Vector POIs
        MapMarker("mk-alb-shelter-1", "pack-alborz-corridor", "Ridge Basecamp Safe Shelter", "High-elevation trail shelter & radio relay point", MarkerCategory.SHELTER, 35.8120, 51.4100, 1860.0, true, false, SEED_TIME),
        MapMarker("mk-alb-shelter-2", "pack-alborz-corridor", "Shirpala Mountain Refuge", "Alpine reinforced shelter at 2750m contour", MarkerCategory.SHELTER, 35.8445, 51.4185, 2750.0, true, false, SEED_TIME),
        MapMarker("mk-alb-assembly-1", "pack-alborz-corridor", "Jamshidieh Safe Plateau", "Open terraced stone plateau clear of buildings", MarkerCategory.ASSEMBLY_POINT, 35.8170, 51.4585, 1820.0, true, false, SEED_TIME),
        MapMarker("mk-alb-water-1", "pack-alborz-corridor", "Darband Spring Cistern", "Mountain freshwater point near trailhead", MarkerCategory.WATER_POINT, 35.8240, 51.4250, 1740.0, true, false, SEED_TIME),
    )

    fun bundledRoutes(): List<SavedRoute> {
        val thrPts = listOf(
            LatLonPoint(35.6892, 51.3890, "City Center Anchor"),
            LatLonPoint(35.6941, 51.3755, "Fire Sector 4"),
            LatLonPoint(35.7062, 51.3775, "Imam Khomeini Hospital"),
            LatLonPoint(35.7106, 51.3919, "Laleh Assembly Park"),
        )
        val thrDist = GeoMath.computeRouteDistanceMeters(thrPts)

        val nuePts = listOf(
            LatLonPoint(49.4458, 11.0825, "Hauptbahnhof Hub"),
            LatLonPoint(49.4512, 11.0772, "Altstadt"),
            LatLonPoint(49.4525, 11.0955, "Wöhrder High Ground"),
            LatLonPoint(49.4648, 11.0882, "Stadtpark Assembly"),
        )
        val nueDist = GeoMath.computeRouteDistanceMeters(nuePts)

        return listOf(
            SavedRoute(
                id = "route-thr-evac",
                packageId = "pack-tehran-grid",
                name = "Primary Keshavarz-Laleh Evacuation Corridor",
                description = "Wide-boulevard foot evacuation connecting Navvab, Trauma Hospital & Laleh Open Shelter",
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
