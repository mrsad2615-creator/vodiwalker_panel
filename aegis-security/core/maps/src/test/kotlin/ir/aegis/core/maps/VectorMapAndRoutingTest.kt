package ir.aegis.core.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorMapAndRoutingTest {

    @Test
    fun baseAtlasAndAllCityVectorPacksLoadCleanlyOffline() {
        val atlas = VectorMapEngine.loadBaseAtlas()
        assertTrue(atlas.countries.size >= 150)
        assertTrue(atlas.iranProvinces.size >= 28)
        assertTrue(atlas.cities.size >= 25)
        assertTrue(atlas.nationalHighways.isNotEmpty())

        for (pkg in OfflineMapSeeds.bundledPackages()) {
            val bundle = VectorMapEngine.loadBundle(pkg.id)
            assertTrue("Roads missing in ${pkg.id}", bundle.roads.isNotEmpty())
            assertTrue("Graph nodes missing in ${pkg.id}", bundle.graphNodes.isNotEmpty())
            assertTrue("Graph edges missing in ${pkg.id}", bundle.graphEdges.isNotEmpty())
        }
    }

    @Test
    fun nearestPackAutoSelectAndSmartMapAdvisorWorkAccurately() {
        val pkgs = OfflineMapSeeds.bundledPackages()
        val mashhadPack = VectorMapEngine.autoSelectNearestPack(36.2972, 59.6067, pkgs)
        assertNotNull(mashhadPack)
        assertEquals("pack-mashhad-grid", mashhadPack?.id)

        val nuePack = VectorMapEngine.autoSelectNearestPack(49.4521, 11.0767, pkgs)
        assertNotNull(nuePack)
        assertEquals("pack-bavaria-nuremberg", nuePack?.id)

        val recs = MapAdvisorEngine.suggestNearestDestinations(
            userLat = 35.7010,
            userLon = 51.3911,
            markers = OfflineMapSeeds.bundledMarkers().filter { it.packageId == "pack-tehran-grid" },
            limit = 3,
        )
        assertTrue(recs.size == 3)
        assertTrue(recs[0].distanceMeters <= recs[1].distanceMeters)
    }

    @Test
    fun offlineDijkstraRoutingFindsMultiHopCorridorAndDetoursAroundHazards() {
        val tehran = VectorMapEngine.loadBundle("pack-tehran-grid")
        val direct = OfflineRoutingEngine.computeSafeRoute(
            bundle = tehran,
            startLat = 35.6997,
            startLon = 51.3380,
            startLabelEn = "Azadi Square",
            startLabelFa = "میدان آزادی",
            destLat = 35.7106,
            destLon = 51.3919,
            destLabelEn = "Laleh Park",
            destLabelFa = "پارک لاله",
        )
        assertTrue(direct.isGraphRouted)
        assertTrue(direct.waypoints.size >= 4)
        assertTrue(direct.steps.isNotEmpty())
        assertTrue(direct.totalDistanceMeters > 3_500.0)
        assertEquals(0, direct.avoidedHazardCount)

        val detoured = OfflineRoutingEngine.computeSafeRoute(
            bundle = tehran,
            startLat = 35.6997,
            startLon = 51.3380,
            startLabelEn = "Azadi Square",
            startLabelFa = "میدان آزادی",
            destLat = 35.7106,
            destLon = 51.3919,
            destLabelEn = "Laleh Park",
            destLabelFa = "پارک لاله",
            hazards = listOf(GeoCoord(35.7010, 51.3911)),
            avoidHazards = true,
        )
        assertTrue(detoured.isGraphRouted)
        assertTrue(detoured.avoidedHazardCount >= 1)
        assertFalse(detoured.waypoints.any { it.label == "Enghelab Square" })
    }
}
