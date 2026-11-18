package ir.aegis.core.maps

import ir.aegis.core.model.LatLonPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathAndProjectionTest {

    @Test
    fun haversineDistanceMatchesKnownGeodesicDistance() {
        // 1 degree of latitude is ~111.19 km
        val meters = GeoMath.haversineMeters(35.0, 51.0, 36.0, 51.0)
        assertTrue(meters in 110_500.0..111_500.0)
    }

    @Test
    fun bearingAndCardinalPointNorthEastSouthWest() {
        val north = GeoMath.bearingDegrees(35.0, 51.0, 36.0, 51.0)
        assertEquals("N", GeoMath.compassCardinal(north))
        val east = GeoMath.bearingDegrees(0.0, 10.0, 0.0, 11.0)
        assertEquals("E", GeoMath.compassCardinal(east))
        val south = GeoMath.bearingDegrees(36.0, 51.0, 35.0, 51.0)
        assertEquals("S", GeoMath.compassCardinal(south))
    }

    @Test
    fun routeDistanceAndUtmFormattingWorkOffline() {
        val pts = listOf(
            LatLonPoint(35.6892, 51.3890, "A"),
            LatLonPoint(35.7024, 51.3912, "B"),
        )
        val dist = GeoMath.computeRouteDistanceMeters(pts)
        assertTrue(dist > 1300.0 && dist < 1700.0)
        assertTrue(GeoMath.estimateWalkingMinutes(dist) >= 15)
        val utm = GeoMath.formatUtmApprox(35.6892, 51.3890)
        assertTrue(utm.startsWith("39S"))
        assertTrue(GeoMath.formatDms(35.6892, 51.3890).contains("°"))
    }

    @Test
    fun projection3dExtrudesElevationWhenPitched() {
        val flat = Projection3D.project(
            lat = 35.70,
            lon = 51.39,
            elevationMeters = 1200.0,
            centerLat = 35.70,
            centerLon = 51.39,
            widthPx = 1000f,
            heightPx = 800f,
            spanDeg = 0.1,
            zoom = 1f,
            yawDeg = 0f,
            pitchDeg = 0f,
        )
        assertEquals(flat.y, flat.extrudedTopY, 0.01f)

        val pitched = Projection3D.project(
            lat = 35.70,
            lon = 51.39,
            elevationMeters = 1200.0,
            centerLat = 35.70,
            centerLon = 51.39,
            widthPx = 1000f,
            heightPx = 800f,
            spanDeg = 0.1,
            zoom = 1f,
            yawDeg = 20f,
            pitchDeg = 45f,
        )
        assertTrue(pitched.extrudedTopY < pitched.y)
    }
}
