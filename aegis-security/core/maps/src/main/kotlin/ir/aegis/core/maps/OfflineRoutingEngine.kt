package ir.aegis.core.maps

import ir.aegis.core.model.LatLonPoint
import java.util.PriorityQueue
import kotlin.math.roundToInt

object OfflineRoutingEngine {

    /**
     * Engine 2: Pure-offline Dijkstra shortest-corridor pathfinder across bundled street intersection graphs.
     * Supports dynamic hazard avoidance (penalizing edges near active hazard markers or open critical incidents).
     */
    fun computeSafeRoute(
        bundle: VectorMapBundle,
        startLat: Double,
        startLon: Double,
        startLabelEn: String,
        startLabelFa: String,
        destLat: Double,
        destLon: Double,
        destLabelEn: String,
        destLabelFa: String,
        hazards: List<GeoCoord> = emptyList(),
        hazardRadiusMeters: Double = 420.0,
        avoidHazards: Boolean = true,
    ): OfflineRouteResult {
        val nodes = bundle.graphNodes
        val edges = bundle.graphEdges
        if (nodes.isEmpty() || edges.isEmpty()) {
            return directFallback(
                startLat, startLon, startLabelEn, startLabelFa,
                destLat, destLon, destLabelEn, destLabelFa,
            )
        }

        val nodeMap = nodes.associateBy { it.id }
        val startAnchor = nodes.minByOrNull { GeoMath.haversineMeters(startLat, startLon, it.lat, it.lon) }
            ?: return directFallback(startLat, startLon, startLabelEn, startLabelFa, destLat, destLon, destLabelEn, destLabelFa)
        val destAnchor = nodes.minByOrNull { GeoMath.haversineMeters(destLat, destLon, it.lat, it.lon) }
            ?: return directFallback(startLat, startLon, startLabelEn, startLabelFa, destLat, destLon, destLabelEn, destLabelFa)

        val adjacency = mutableMapOf<String, MutableList<Pair<RoadGraphEdge, String>>>()
        for (edge in edges) {
            if (nodeMap.containsKey(edge.fromId) && nodeMap.containsKey(edge.toId)) {
                adjacency.getOrPut(edge.fromId) { mutableListOf() }.add(edge to edge.toId)
                adjacency.getOrPut(edge.toId) { mutableListOf() }.add(edge to edge.fromId)
            }
        }

        var avoidedCount = 0
        val hazardNodeIds = if (avoidHazards && hazards.isNotEmpty()) {
            nodes.filter { node ->
                hazards.any { h -> GeoMath.haversineMeters(node.lat, node.lon, h.lat, h.lon) <= hazardRadiusMeters }
            }.map { it.id }.toSet().also { avoidedCount = it.size }
        } else {
            emptySet()
        }

        val dist = mutableMapOf<String, Double>().withDefault { Double.POSITIVE_INFINITY }
        val prev = mutableMapOf<String, Pair<String, RoadGraphEdge>>()
        val pq = PriorityQueue<Pair<String, Double>>(compareBy { it.second })

        dist[startAnchor.id] = 0.0
        pq.add(startAnchor.id to 0.0)

        while (pq.isNotEmpty()) {
            val (uId, currentD) = pq.poll()
            if (currentD > dist.getValue(uId)) continue
            if (uId == destAnchor.id) break

            val uNode = nodeMap[uId] ?: continue
            for ((edge, vId) in adjacency[uId].orEmpty()) {
                val vNode = nodeMap[vId] ?: continue
                var weight = GeoMath.haversineMeters(uNode.lat, uNode.lon, vNode.lat, vNode.lon)
                if (!edge.isWideCorridor) weight *= 1.18
                if (vId in hazardNodeIds || uId in hazardNodeIds) {
                    weight += 12_000.0
                }
                val alt = currentD + weight
                if (alt < dist.getValue(vId)) {
                    dist[vId] = alt
                    prev[vId] = uId to edge
                    pq.add(vId to alt)
                }
            }
        }

        if (destAnchor.id != startAnchor.id && !prev.containsKey(destAnchor.id)) {
            return directFallback(
                startLat, startLon, startLabelEn, startLabelFa,
                destLat, destLon, destLabelEn, destLabelFa,
            )
        }

        val chainNodes = mutableListOf<RoadGraphNode>()
        val chainEdges = mutableListOf<RoadGraphEdge>()
        var curr = destAnchor.id
        while (curr != startAnchor.id) {
            val node = nodeMap[curr] ?: break
            chainNodes += node
            val p = prev[curr] ?: break
            chainEdges += p.second
            curr = p.first
        }
        chainNodes += startAnchor
        chainNodes.reverse()
        chainEdges.reverse()

        val waypoints = mutableListOf<LatLonPoint>()
        val steps = mutableListOf<OfflineNavigationStep>()

        waypoints += LatLonPoint(startLat, startLon, startLabelEn)
        val firstDist = GeoMath.haversineMeters(startLat, startLon, startAnchor.lat, startAnchor.lon)
        if (firstDist > 35.0) {
            val brg = GeoMath.bearingDegrees(startLat, startLon, startAnchor.lat, startAnchor.lon)
            steps += OfflineNavigationStep(
                instructionEn = "Head ${GeoMath.compassCardinal(brg)} (${GeoMath.formatDistance(firstDist)}) to ${startAnchor.labelEn}",
                instructionFa = "به سمت ${cardinalFa(GeoMath.compassCardinal(brg))} (${firstDist.roundToInt()} متر) به تقاطع ${startAnchor.labelFa} بروید",
                distanceMeters = firstDist,
                bearingDegrees = brg,
            )
        }

        chainNodes.forEach { n ->
            waypoints += LatLonPoint(n.lat, n.lon, n.labelEn)
        }

        for (i in chainEdges.indices) {
            val from = chainNodes[i]
            val to = chainNodes[i + 1]
            val edge = chainEdges[i]
            val segDist = GeoMath.haversineMeters(from.lat, from.lon, to.lat, to.lon)
            val brg = GeoMath.bearingDegrees(from.lat, from.lon, to.lat, to.lon)
            steps += OfflineNavigationStep(
                instructionEn = "Continue ${GeoMath.compassCardinal(brg)} on ${edge.roadNameEn} for ${GeoMath.formatDistance(segDist)} toward ${to.labelEn}",
                instructionFa = "در مسیر ${edge.roadNameFa} به سمت ${cardinalFa(GeoMath.compassCardinal(brg))} (${GeoMath.formatDistance(segDist)}) تا ${to.labelFa} حرکت کنید",
                distanceMeters = segDist,
                bearingDegrees = brg,
            )
        }

        val lastDist = GeoMath.haversineMeters(destAnchor.lat, destAnchor.lon, destLat, destLon)
        if (lastDist > 25.0) {
            val brg = GeoMath.bearingDegrees(destAnchor.lat, destAnchor.lon, destLat, destLon)
            steps += OfflineNavigationStep(
                instructionEn = "Proceed ${GeoMath.formatDistance(lastDist)} ${GeoMath.compassCardinal(brg)} to destination: $destLabelEn",
                instructionFa = "${lastDist.roundToInt()} متر دیگر به مقصد برسید: $destLabelFa",
                distanceMeters = lastDist,
                bearingDegrees = brg,
            )
        }
        waypoints += LatLonPoint(destLat, destLon, destLabelEn)

        val totalDist = GeoMath.computeRouteDistanceMeters(waypoints)
        return OfflineRouteResult(
            waypoints = waypoints,
            steps = steps.ifEmpty {
                directFallback(startLat, startLon, startLabelEn, startLabelFa, destLat, destLon, destLabelEn, destLabelFa).steps
            },
            totalDistanceMeters = totalDist,
            estimatedWalkMinutes = GeoMath.estimateWalkingMinutes(totalDist),
            avoidedHazardCount = avoidedCount,
            isGraphRouted = true,
        )
    }

    private fun directFallback(
        startLat: Double,
        startLon: Double,
        startLabelEn: String,
        startLabelFa: String,
        destLat: Double,
        destLon: Double,
        destLabelEn: String,
        destLabelFa: String,
    ): OfflineRouteResult {
        val d = GeoMath.haversineMeters(startLat, startLon, destLat, destLon)
        val brg = GeoMath.bearingDegrees(startLat, startLon, destLat, destLon)
        return OfflineRouteResult(
            waypoints = listOf(LatLonPoint(startLat, startLon, startLabelEn), LatLonPoint(destLat, destLon, destLabelEn)),
            steps = listOf(
                OfflineNavigationStep(
                    instructionEn = "Direct bearing ${GeoMath.formatBearing(brg)} (${GeoMath.formatDistance(d)}) to $destLabelEn",
                    instructionFa = "مسیر مستقیم به سمت ${cardinalFa(GeoMath.compassCardinal(brg))} (${GeoMath.formatDistance(d)}) تا $destLabelFa",
                    distanceMeters = d,
                    bearingDegrees = brg,
                ),
            ),
            totalDistanceMeters = d,
            estimatedWalkMinutes = GeoMath.estimateWalkingMinutes(d),
            avoidedHazardCount = 0,
            isGraphRouted = false,
        )
    }

    private fun cardinalFa(card: String): String = when (card) {
        "N" -> "شمال"
        "NE" -> "شمال‌شرق"
        "E" -> "شرق"
        "SE" -> "جنوب‌شرق"
        "S" -> "جنوب"
        "SW" -> "جنوب‌غرب"
        "W" -> "غرب"
        "NW" -> "شمال‌غرب"
        else -> card
    }
}
