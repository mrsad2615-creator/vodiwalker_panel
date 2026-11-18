package ir.aegis.core.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorMapAndRoutingTest {

    @Test
    fun bundledVectorPacksLoadWithStreetsParksBuildingsAndGraph() {
        val tehran = VectorMapEngine.loadBundle("pack-tehran-grid")
        assertTrue(tehran.roads.size >= 12)
        assertTrue(tehran.parks.size >= 4)
        assertTrue(tehran.buildings.size >= 8)
        assertTrue(tehran.contours.size >= 3)
        assertTrue(tehran.graphNodes.size >= 15)
        assertTrue(tehran.graphEdges.size >= 20)

        val nuremberg = VectorMapEngine.loadBundle("pack-bavaria-nuremberg")
        assertTrue(nuremberg.waterways.isNotEmpty())
        assertTrue(nuremberg.roads.size >= 5)

        val alborz = VectorMapEngine.loadBundle("pack-alborz-corridor")
        assertTrue(alborz.contours.size >= 3)
        assertTrue(alborz.graphNodes.size >= 5)
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

        // Place hazard right on Enghelab Square (35.7010, 51.3911) and verify route detours
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
