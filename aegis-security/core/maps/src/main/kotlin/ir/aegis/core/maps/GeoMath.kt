package ir.aegis.core.maps

import ir.aegis.core.model.LatLonPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object GeoMath {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val a = sin(dLat / 2).pow(2) + cos(rLat1) * cos(rLat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        val theta = Math.toDegrees(atan2(y, x))
        return (theta + 360.0) % 360.0
    }

    fun compassCardinal(bearingDegrees: Double): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val normalized = ((bearingDegrees % 360.0) + 360.0) % 360.0
        val index = ((normalized + 22.5) / 45.0).toInt() % 8
        return dirs[index]
    }

    fun computeRouteDistanceMeters(waypoints: List<LatLonPoint>): Double {
        if (waypoints.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until waypoints.lastIndex) {
            val a = waypoints[i]
            val b = waypoints[i + 1]
            total += haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        return total
    }

    fun estimateWalkingMinutes(distanceMeters: Double, speedMetersPerMinute: Double = 75.0): Int {
        if (distanceMeters <= 0.0) return 0
        return kotlin.math.ceil(distanceMeters / speedMetersPerMinute).toInt().coerceAtLeast(1)
    }

    fun formatDecimalDegrees(lat: Double, lon: Double): String {
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lon >= 0) "E" else "W"
        return "%.5f° %s, %.5f° %s".format(abs(lat), ns, abs(lon), ew)
    }

    fun formatDms(lat: Double, lon: Double): String = "${toDmsPart(lat, true)}  ${toDmsPart(lon, false)}"

    private fun toDmsPart(value: Double, isLat: Boolean): String {
        val dir = if (isLat) {
            if (value >= 0) "N" else "S"
        } else {
            if (value >= 0) "E" else "W"
        }
        val absVal = abs(value)
        val deg = floor(absVal).toInt()
        val minFull = (absVal - deg) * 60.0
        val min = floor(minFull).toInt()
        val sec = (minFull - min) * 60.0
        return "%d°%02d'%04.1f\"%s".format(deg, min, sec, dir)
    }

    fun formatUtmApprox(lat: Double, lon: Double): String {
        val clampedLat = lat.coerceIn(-80.0, 84.0)
        val zone = (((lon + 180.0) / 6.0).toInt() + 1).coerceIn(1, 60)
        val bandLetters = "CDEFGHJKLMNPQRSTUVWX"
        val bandIndex = (((clampedLat + 80.0) / 8.0).toInt()).coerceIn(0, bandLetters.lastIndex)
        val band = bandLetters[bandIndex]

        val a = 6378137.0
        val f = 1.0 / 298.257223563
        val e2 = f * (2 - f)
        val ePrime2 = e2 / (1 - e2)
        val k0 = 0.9996
        val latRad = Math.toRadians(clampedLat)
        val lonRad = Math.toRadians(lon)
        val lonOrigin = Math.toRadians((zone - 1) * 6.0 - 180.0 + 3.0)

        val n = a / sqrt(1 - e2 * sin(latRad).pow(2))
        val t = tan(latRad).pow(2)
        val c = ePrime2 * cos(latRad).pow(2)
        val aa = cos(latRad) * (lonRad - lonOrigin)
        val m = a * (
            (1 - e2 / 4 - 3 * e2 * e2 / 64) * latRad -
                (3 * e2 / 8 + 3 * e2 * e2 / 32) * sin(2 * latRad) +
                (15 * e2 * e2 / 256) * sin(4 * latRad)
            )
        val easting = k0 * n * (aa + (1 - t + c) * aa.pow(3) / 6) + 500_000.0
        var northing = k0 * (m + n * tan(latRad) * (aa.pow(2) / 2 + (5 - t + 9 * c + 4 * c * c) * aa.pow(4) / 24))
        if (clampedLat < 0) northing += 10_000_000.0
        return "${zone}$band ${easting.roundToInt()}E ${northing.roundToInt()}N"
    }

    fun formatDistance(meters: Double): String = if (meters < 1000.0) {
        "${meters.roundToInt()} m"
    } else {
        "%.2f km".format(meters / 1000.0)
    }

    fun formatBearing(degrees: Double): String {
        val norm = ((degrees % 360.0) + 360.0) % 360.0
        return "%03d° %s".format(norm.roundToInt() % 360, compassCardinal(norm))
    }

    fun gridCell(
        lat: Double,
        lon: Double,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        rows: Int,
        cols: Int,
    ): Pair<Int, Int> {
        val latSpan = (maxLat - minLat).coerceAtLeast(1e-6)
        val lonSpan = (maxLon - minLon).coerceAtLeast(1e-6)
        val row = (((maxLat - lat) / latSpan) * rows).toInt().coerceIn(0, rows - 1)
        val col = (((lon - minLon) / lonSpan) * cols).toInt().coerceIn(0, cols - 1)
        return row to col
    }
}

data class ProjectedPoint(
    val x: Float,
    val y: Float,
    val depth: Float,
    val extrudedTopY: Float,
)

object Projection3D {
    fun project(
        lat: Double,
        lon: Double,
        elevationMeters: Double,
        centerLat: Double,
        centerLon: Double,
        widthPx: Float,
        heightPx: Float,
        spanDeg: Double,
        zoom: Float,
        yawDeg: Float,
        pitchDeg: Float,
    ): ProjectedPoint {
        val aspect = cos(Math.toRadians(centerLat)).coerceAtLeast(0.3)
        val unitPx = (minOf(widthPx, heightPx) * 0.95f * zoom).coerceAtLeast(1f)
        val normX = (((lon - centerLon) * aspect) / spanDeg).toFloat() * unitPx
        val normY = ((centerLat - lat) / spanDeg).toFloat() * unitPx

        val yawRad = Math.toRadians(yawDeg.toDouble())
        val rx = (normX * cos(yawRad) - normY * sin(yawRad)).toFloat()
        val ry = (normX * sin(yawRad) + normY * cos(yawRad)).toFloat()

        val clampedPitch = pitchDeg.coerceIn(0f, 62f)
        val pitchRad = Math.toRadians(clampedPitch.toDouble())
        val tiltScaleY = cos(pitchRad).toFloat()
        val depth = (ry * sin(pitchRad)).toFloat()
        val perspective = (1.0f / (1.0f - (depth / (heightPx * 2.2f)).coerceIn(-0.45f, 0.55f))).coerceIn(0.65f, 1.55f)

        val sx = widthPx * 0.5f + rx * perspective
        val sy = heightPx * 0.52f + ry * tiltScaleY * perspective
        val extrusionScale = (0.045f + (clampedPitch / 58f) * 0.085f) * zoom * perspective
        val extrudedTopY = if (clampedPitch <= 0.5f) sy else sy - (elevationMeters.toFloat().coerceAtLeast(0f) * extrusionScale)

        return ProjectedPoint(
            x = sx,
            y = sy,
            depth = depth,
            extrudedTopY = extrudedTopY,
        )
    }

    fun metersPerPixel(
        centerLat: Double,
        spanDeg: Double,
        widthPx: Float,
        heightPx: Float,
        zoom: Float,
    ): Double {
        val unitPx = (minOf(widthPx, heightPx) * 0.95f * zoom).coerceAtLeast(1f)
        val metersPerSpanLat = 111_195.0 * spanDeg
        return metersPerSpanLat / unitPx
    }
}
