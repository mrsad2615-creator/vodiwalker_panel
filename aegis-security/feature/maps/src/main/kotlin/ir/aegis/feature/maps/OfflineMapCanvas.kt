package ir.aegis.feature.maps

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import ir.aegis.core.maps.BaseAtlasBundle
import ir.aegis.core.maps.OfflineRouteResult
import ir.aegis.core.maps.Projection3D
import ir.aegis.core.maps.VectorBuilding
import ir.aegis.core.maps.VectorMapBundle
import ir.aegis.core.model.AppLanguage
import ir.aegis.core.model.Incident
import ir.aegis.core.model.MapMarker
import ir.aegis.core.model.MarkerCategory
import ir.aegis.core.model.SavedRoute
import ir.aegis.core.ui.LocalAppLanguage
import ir.aegis.core.ui.LocalObsidianColors
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal data class ProjectedMarkerHit(
    val marker: MapMarker,
    val x: Float,
    val y: Float,
)

@Composable
internal fun OfflineVectorMapCanvas(
    atlas: BaseAtlasBundle,
    bundle: VectorMapBundle,
    centerLat: Double,
    centerLon: Double,
    spanDeg: Double,
    zoomScale: Float,
    yawDeg: Float,
    pitchDeg: Float,
    is3DMode: Boolean,
    showContours: Boolean,
    showBuildings: Boolean,
    showStreets: Boolean,
    showRoutesLayer: Boolean,
    markers: List<MapMarker>,
    routes: List<SavedRoute>,
    activeRoute: OfflineRouteResult?,
    incidents: List<Incident>,
    userLat: Double,
    userLon: Double,
    gpsAccuracyMeters: Float,
    compassAzimuthDeg: Float,
    selectedMarkerId: String?,
    rulerStart: MapMarker?,
    rulerEnd: MapMarker?,
    onTransformGesture: (panDelta: Offset, zoomFactor: Float) -> Unit,
    onSelectMarker: (MapMarker) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalObsidianColors.current
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val isDark = colors.isDark

    val mapTypeface = remember(context) {
        runCatching {
            ResourcesCompat.getFont(context, ir.aegis.core.ui.R.font.vazirmatn_medium)
        }.getOrNull() ?: Typeface.DEFAULT
    }
    val mapBoldTypeface = remember(context) {
        runCatching {
            ResourcesCompat.getFont(context, ir.aegis.core.ui.R.font.vazirmatn_bold)
        }.getOrNull() ?: Typeface.DEFAULT_BOLD
    }

    val hitTargets = remember { mutableListOf<ProjectedMarkerHit>() }

    // Minimalist Smoked Jade & Porcelain Cartographic Palette
    val oceanBg = if (isDark) Color(0xFF070D14) else Color(0xFFD9E6F0)
    val landFill = if (isDark) Color(0xFF101822) else Color(0xFFF3F6F9)
    val landBorder = if (isDark) Color(0xFF273748) else Color(0xFFB5C4D4)
    val iranProvinceFill = if (isDark) Color(0xCC13202B) else Color(0xDCE9F0F4)
    val iranProvinceBorder = if (isDark) Color(0xFF2D595B) else Color(0xFF6B9699)
    val nationalHwyColor = if (isDark) Color(0xAA4DB6AC) else Color(0xCC0F766E)
    val gridColor = if (isDark) Color(0x28334155) else Color(0x3894A3B8)

    val parkFill = if (isDark) Color(0xBA113A32) else Color(0xCCBCE5CD)
    val parkBorder = if (isDark) Color(0xFF34997D) else Color(0xFF4C9B6E)
    val waterFill = if (isDark) Color(0xFF113E5A) else Color(0xFF83C5E5)
    val contourColor = if (isDark) Color(0x4064748B) else Color(0x457C8BA1)
    val hwyCasing = if (isDark) Color(0xFF0B1117) else Color(0xFF78350F)
    val hwyFill = if (isDark) Color(0xFFEAB308) else Color(0xFFF59E0B)
    val primaryCasing = if (isDark) Color(0xFF0A0F14) else Color(0xFF94A3B8)
    val primaryFill = if (isDark) Color(0xFFD6E2EC) else Color(0xFFFFFFFF)
    val secondaryFill = if (isDark) Color(0xFF47586B) else Color(0xFFCBD5E1)
    val trailColor = if (isDark) Color(0xFF5EEAD4) else Color(0xFF0F766E)

    val haloColorArgb = (if (isDark) Color(0xF0070C12) else Color(0xF4FFFFFF)).toArgb()
    val streetTextArgb = (if (isDark) Color(0xFFE2ECF5) else Color(0xFF1E293B)).toArgb()
    val districtTextArgb = (if (isDark) Color(0xFF5EEAD4) else Color(0xFF0F766E)).toArgb()
    val provinceTextArgb = (if (isDark) Color(0xFF94B4C6) else Color(0xFF3B5A70)).toArgb()
    val parkTextArgb = (if (isDark) Color(0xFF6EE7B7) else Color(0xFF15803D)).toArgb()

    val haloPaint = remember(mapTypeface, haloColorArgb) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5.5f
            strokeJoin = Paint.Join.ROUND
            color = haloColorArgb
            typeface = mapTypeface
            textAlign = Paint.Align.CENTER
        }
    }
    val textPaint = remember(mapTypeface) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            typeface = mapTypeface
            textAlign = Paint.Align.CENTER
        }
    }
    val boldHaloPaint = remember(mapBoldTypeface, haloColorArgb) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6.5f
            strokeJoin = Paint.Join.ROUND
            color = haloColorArgb
            typeface = mapBoldTypeface
            textAlign = Paint.Align.CENTER
        }
    }
    val boldTextPaint = remember(mapBoldTypeface) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            typeface = mapBoldTypeface
            textAlign = Paint.Align.CENTER
        }
    }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onTransformGesture(pan, zoom)
                }
            }
            .pointerInput(markers, zoomScale, yawDeg, pitchDeg, centerLat, centerLon, spanDeg) {
                detectTapGestures { tap ->
                    val hit = hitTargets.minByOrNull { hypot(it.x - tap.x, it.y - tap.y) }
                    if (hit != null && hypot(hit.x - tap.x, hit.y - tap.y) <= 46f) {
                        onSelectMarker(hit.marker)
                    }
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val pitch = if (is3DMode) pitchDeg else 0f
        val yaw = if (is3DMode) yawDeg else 0f

        val effectiveDegSpan = spanDeg / zoomScale.toDouble().coerceAtLeast(0.05)
        val isCityDetailMode = effectiveDegSpan < 1.25 && bundle.packageId !in setOf("atlas-iran", "atlas-world")
        val isNationalView = effectiveDegSpan in 0.45..28.0

        fun proj(lat: Double, lon: Double, heightMeters: Double = 0.0) = Projection3D.project(
            lat = lat,
            lon = lon,
            elevationMeters = heightMeters,
            centerLat = centerLat,
            centerLon = centerLon,
            widthPx = w,
            heightPx = h,
            spanDeg = spanDeg,
            zoom = zoomScale,
            yawDeg = yaw,
            pitchDeg = pitch,
        )

        fun pointInView(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): Boolean {
            val viewLatHalf = effectiveDegSpan * 0.9
            val viewLonHalf = effectiveDegSpan * 1.15
            return !(maxLat < centerLat - viewLatHalf || minLat > centerLat + viewLatHalf ||
                maxLon < centerLon - viewLonHalf || minLon > centerLon + viewLonHalf)
        }

        // 1. Ocean / Deep Atmospheric Background
        drawRect(color = oceanBg)

        // 2. Global Country Polygons (Natural Base Layer — always keeps land/coastline context!)
        val marginView = effectiveDegSpan * 1.4
        atlas.countries.forEach { country ->
            val rings = country.rings
            for (ring in rings) {
                if (ring.size < 3) continue
                if (effectiveDegSpan < 20.0) {
                    val anyNear = ring.any { pt ->
                        kotlin.math.abs(pt.lat - centerLat) <= marginView * 3.0 &&
                            kotlin.math.abs(pt.lon - centerLon) <= marginView * 3.0
                    }
                    if (!anyNear) continue
                }
                val path = Path()
                ring.forEachIndexed { idx, pt ->
                    val p = proj(pt.lat, pt.lon)
                    if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(path, landFill, style = Fill)
                drawPath(path, landBorder, style = Stroke(width = if (isNationalView) 1.5f else 1.0f))
            }
        }

        // 3. All 30 Iranian Provinces (clean multi-scale administration borders)
        if (effectiveDegSpan < 40.0) {
            atlas.iranProvinces.forEach { prov ->
                val rings = prov.rings
                for (ring in rings) {
                    if (ring.size < 3) continue
                    val path = Path()
                    ring.forEachIndexed { idx, pt ->
                        val p = proj(pt.lat, pt.lon)
                        if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    path.close()
                    drawPath(path, iranProvinceFill, style = Fill)
                    drawPath(path, iranProvinceBorder, style = Stroke(width = if (isNationalView) 1.8f else 1.2f))
                }
            }
        }

        // 4. National Highway Corridors across Iran
        if (effectiveDegSpan > 0.35 && effectiveDegSpan < 35.0) {
            atlas.nationalHighways.forEach { hwy ->
                if (hwy.size >= 2) {
                    val path = Path()
                    hwy.forEachIndexed { idx, pt ->
                        val p = proj(pt.lat, pt.lon)
                        if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    drawPath(
                        path = path,
                        color = nationalHwyColor,
                        style = Stroke(width = if (effectiveDegSpan < 4.0) 4.2f else 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }

        // 5. Adaptive Graticule Grid
        val gridStep = when {
            effectiveDegSpan > 40.0 -> 15.0
            effectiveDegSpan > 12.0 -> 5.0
            effectiveDegSpan > 2.5 -> 1.0
            effectiveDegSpan > 0.4 -> 0.25
            else -> 0.025
        }
        val minGridLat = ((centerLat - effectiveDegSpan) / gridStep).toInt() * gridStep
        val maxGridLat = centerLat + effectiveDegSpan
        var gLat = minGridLat
        while (gLat <= maxGridLat) {
            val p1 = proj(gLat, centerLon - effectiveDegSpan * 1.4)
            val p2 = proj(gLat, centerLon + effectiveDegSpan * 1.4)
            drawLine(gridColor, Offset(p1.x, p1.y), Offset(p2.x, p2.y), strokeWidth = 1f)
            gLat += gridStep
        }
        val minGridLon = ((centerLon - effectiveDegSpan * 1.4) / gridStep).toInt() * gridStep
        val maxGridLon = centerLon + effectiveDegSpan * 1.4
        var gLon = minGridLon
        while (gLon <= maxGridLon) {
            val p1 = proj(centerLat - effectiveDegSpan, gLon)
            val p2 = proj(centerLat + effectiveDegSpan, gLon)
            drawLine(gridColor, Offset(p1.x, p1.y), Offset(p2.x, p2.y), strokeWidth = 1f)
            gLon += gridStep
        }

        // 6. City-Scale Vector Layer (Contours, Parks, Waterways, Streets, 2D/3D Blocks)
        if (isCityDetailMode) {
            if (showContours) {
                bundle.contours.forEach { contour ->
                    if (contour.points.size >= 2) {
                        val path = Path()
                        val elev = (contour.elevationMeters - bundle.baseElevationMeters).coerceAtLeast(0.0)
                        contour.points.forEachIndexed { idx, pt ->
                            val p = proj(pt.lat, pt.lon, if (is3DMode) elev * 0.25 else 0.0)
                            val py = if (is3DMode) p.extrudedTopY else p.y
                            if (idx == 0) path.moveTo(p.x, py) else path.lineTo(p.x, py)
                        }
                        drawPath(
                            path = path,
                            color = contourColor,
                            style = Stroke(width = 1.8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 7f))),
                        )
                    }
                }
            }

            bundle.parks.forEach { poly ->
                if (poly.points.size >= 3) {
                    val path = Path()
                    poly.points.forEachIndexed { idx, pt ->
                        val p = proj(pt.lat, pt.lon)
                        if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    path.close()
                    drawPath(path = path, color = parkFill, style = Fill)
                    drawPath(path = path, color = parkBorder, style = Stroke(width = 2.0f))
                }
            }

            bundle.waterBodies.forEach { wb ->
                if (wb.points.size >= 3) {
                    val path = Path()
                    wb.points.forEachIndexed { idx, pt ->
                        val p = proj(pt.lat, pt.lon)
                        if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    path.close()
                    drawPath(path = path, color = waterFill, style = Fill)
                }
            }
            bundle.waterways.forEach { stream ->
                if (stream.points.size >= 2) {
                    val path = Path()
                    stream.points.forEachIndexed { idx, pt ->
                        val p = proj(pt.lat, pt.lon)
                        if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    drawPath(
                        path = path,
                        color = waterFill,
                        style = Stroke(
                            width = (stream.widthMeters * 0.26f * zoomScale).coerceIn(3.5f, 16f),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }

            if (showStreets) {
                val orderedRoads = bundle.roads.sortedBy { roadTierOrder(it.tier) }
                orderedRoads.forEach { road ->
                    if (road.points.size >= 2) {
                        val path = Path()
                        road.points.forEachIndexed { idx, pt ->
                            val p = proj(pt.lat, pt.lon)
                            if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                        }
                        when (road.tier) {
                            "HIGHWAY" -> {
                                val width = (6.8f * zoomScale).coerceIn(5.0f, 15f)
                                drawPath(path, hwyCasing, style = Stroke(width + 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                drawPath(path, hwyFill, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                            "PRIMARY" -> {
                                val width = (4.6f * zoomScale).coerceIn(3.4f, 11f)
                                drawPath(path, primaryCasing, style = Stroke(width + 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                drawPath(path, primaryFill, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                            "TRAIL" -> {
                                drawPath(
                                    path,
                                    trailColor,
                                    style = Stroke(
                                        width = (3.0f * zoomScale).coerceIn(2.4f, 6.5f),
                                        cap = StrokeCap.Round,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                                    ),
                                )
                            }
                            else -> {
                                drawPath(
                                    path,
                                    secondaryFill,
                                    style = Stroke((2.5f * zoomScale).coerceIn(1.8f, 7f), cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                            }
                        }
                    }
                }
            }

            if (showBuildings) {
                data class BuildingProjection(
                    val building: VectorBuilding,
                    val basePts: List<Offset>,
                    val roofPts: List<Offset>,
                    val avgDepth: Float,
                )

                val projectedBuildings = bundle.buildings.mapNotNull { b ->
                    if (b.footprint.size < 3) return@mapNotNull null
                    val pts = b.footprint.map { pt -> proj(pt.lat, pt.lon, b.heightMeters) }
                    BuildingProjection(
                        building = b,
                        basePts = pts.map { Offset(it.x, it.y) },
                        roofPts = pts.map { Offset(it.x, if (is3DMode) it.extrudedTopY else it.y) },
                        avgDepth = pts.map { it.depth }.average().toFloat(),
                    )
                }.sortedBy { it.avgDepth }

                projectedBuildings.forEach { bp ->
                    val (wallColor, roofColor, edgeColor) = buildingColors(bp.building.kind, isDark)
                    if (is3DMode) {
                        for (i in bp.basePts.indices) {
                            val next = (i + 1) % bp.basePts.size
                            val side = Path().apply {
                                moveTo(bp.basePts[i].x, bp.basePts[i].y)
                                lineTo(bp.basePts[next].x, bp.basePts[next].y)
                                lineTo(bp.roofPts[next].x, bp.roofPts[next].y)
                                lineTo(bp.roofPts[i].x, bp.roofPts[i].y)
                                close()
                            }
                            drawPath(side, wallColor, style = Fill)
                            drawPath(side, edgeColor.copy(alpha = 0.4f), style = Stroke(1.1f))
                        }
                    }
                    val roofPath = Path().apply {
                        bp.roofPts.forEachIndexed { idx, pt ->
                            if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                        }
                        close()
                    }
                    drawPath(roofPath, roofColor, style = Fill)
                    drawPath(roofPath, edgeColor, style = Stroke(1.6f))
                }
            }
        }

        // 7. Saved Offline Routes
        if (showRoutesLayer) {
            routes.forEach { route ->
                if (route.waypoints.size >= 2) {
                    val path = Path()
                    route.waypoints.forEachIndexed { idx, wp ->
                        val p = proj(wp.latitude, wp.longitude, 6.0)
                        val py = if (is3DMode) p.extrudedTopY else p.y
                        if (idx == 0) path.moveTo(p.x, py) else path.lineTo(p.x, py)
                    }
                    drawPath(
                        path = path,
                        color = colors.accentTeal.copy(alpha = 0.60f),
                        style = Stroke(width = 4.0f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
                    )
                }
            }
        }

        // 8. Active Safe-Corridor Route
        if (activeRoute != null && activeRoute.waypoints.size >= 2) {
            val routePath = Path()
            val projectedWps = activeRoute.waypoints.map { wp ->
                val p = proj(wp.latitude, wp.longitude, 12.0)
                Offset(p.x, if (is3DMode) p.extrudedTopY else p.y)
            }
            projectedWps.forEachIndexed { idx, pt ->
                if (idx == 0) routePath.moveTo(pt.x, pt.y) else routePath.lineTo(pt.x, pt.y)
            }
            drawPath(routePath, Color(0x555EEAD4), style = Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(routePath, Color(0xFF0A1416), style = Stroke(width = 9.0f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(routePath, colors.accentCyan, style = Stroke(width = 5.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))

            projectedWps.forEachIndexed { idx, pt ->
                val isEndpoint = idx == 0 || idx == projectedWps.lastIndex
                drawCircle(
                    color = if (isEndpoint) colors.accentEmerald else Color.White,
                    radius = if (isEndpoint) 8f else 4f,
                    center = pt,
                )
            }
        }

        // 9. Active Incident Alert Zones
        incidents.filter { it.latitude != null && it.longitude != null && it.status.name != "RESOLVED" }.forEach { inc ->
            val p = proj(inc.latitude!!, inc.longitude!!, 15.0)
            val py = if (is3DMode) p.extrudedTopY else p.y
            if (p.x in -60f..(w + 60f) && py in -60f..(h + 60f)) {
                drawCircle(colors.accentCrimson.copy(alpha = 0.22f), radius = 32f * zoomScale.coerceIn(0.8f, 1.5f), center = Offset(p.x, py))
                drawCircle(colors.accentCrimson, radius = 32f * zoomScale.coerceIn(0.8f, 1.5f), center = Offset(p.x, py), style = Stroke(2.0f))
                drawCircle(colors.accentCrimson, radius = 7.5f, center = Offset(p.x, py))
            }
        }

        // 10. Ruler Line
        if (rulerStart != null && rulerEnd != null) {
            val s = proj(rulerStart.latitude, rulerStart.longitude, 10.0)
            val e = proj(rulerEnd.latitude, rulerEnd.longitude, 10.0)
            val sy = if (is3DMode) s.extrudedTopY else s.y
            val ey = if (is3DMode) e.extrudedTopY else e.y
            drawLine(colors.accentAmber, Offset(s.x, sy), Offset(e.x, ey), strokeWidth = 4.8f, cap = StrokeCap.Round)
            drawCircle(colors.accentAmber, radius = 10f, center = Offset(s.x, sy))
            drawCircle(colors.accentAmber, radius = 10f, center = Offset(e.x, ey))
        }

        // 11. Standard GPS / GNSS Locator: Accuracy Ring + Directional Compass Cone + Crisp Beacon
        val mpp = Projection3D.metersPerPixel(centerLat, spanDeg, w, h, zoomScale)
        run {
            val me = proj(userLat, userLon, 8.0)
            val mey = if (is3DMode) me.extrudedTopY else me.y
            val centerPt = Offset(me.x, mey)

            // True metric horizontal accuracy halo
            val accuracyRadiusPx = (gpsAccuracyMeters / mpp.toFloat()).coerceIn(22f, 145f)
            drawCircle(
                color = colors.accentCyan.copy(alpha = 0.14f),
                radius = accuracyRadiusPx,
                center = centerPt,
            )
            drawCircle(
                color = colors.accentCyan.copy(alpha = 0.45f),
                radius = accuracyRadiusPx,
                center = centerPt,
                style = Stroke(width = 1.6f),
            )

            // Directional Compass Heading Cone (Canvas 0 deg = East; North = -90 deg)
            val coneRadius = 56f
            val sweepStart = compassAzimuthDeg - 90f - 26f
            drawArc(
                brush = Brush.radialGradient(
                    listOf(colors.accentCyan.copy(alpha = 0.55f), Color.Transparent),
                    center = centerPt,
                    radius = coneRadius,
                ),
                startAngle = sweepStart,
                sweepAngle = 52f,
                useCenter = true,
                topLeft = Offset(centerPt.x - coneRadius, centerPt.y - coneRadius),
                size = Size(coneRadius * 2f, coneRadius * 2f),
            )

            // Center crisp beacon
            drawCircle(Color.White, radius = 11.5f, center = centerPt)
            drawCircle(colors.accentCyan, radius = 8f, center = centerPt)
            drawCircle(Color.White, radius = 2.6f, center = centerPt)
        }

        // 12. POIs & Markers + Hit Targets
        hitTargets.clear()
        val projectedMarkers = markers.map { mk ->
            val relElev = (mk.elevationMeters - bundle.baseElevationMeters).coerceIn(10.0, 260.0)
            val p = proj(mk.latitude, mk.longitude, relElev)
            val py = if (is3DMode) p.extrudedTopY else p.y
            Triple(mk, p, Offset(p.x, py))
        }.sortedBy { it.second.depth }

        projectedMarkers.forEach { (mk, baseProj, topPt) ->
            if (topPt.x !in -40f..(w + 40f) || topPt.y !in -40f..(h + 40f)) return@forEach
            val mkColor = markerColor(mk.category, colors)
            val isSelected = mk.id == selectedMarkerId || mk.id == rulerStart?.id || mk.id == rulerEnd?.id

            if (is3DMode) {
                drawLine(mkColor.copy(alpha = 0.72f), Offset(baseProj.x, baseProj.y), topPt, strokeWidth = 2.4f)
            }
            if (isSelected) {
                drawCircle(mkColor.copy(alpha = 0.30f), radius = 24f, center = topPt)
                drawCircle(Color.White, radius = 14f, center = topPt)
            } else {
                drawCircle(Color(0xDD0B131B), radius = 11.5f, center = topPt)
            }
            drawCircle(mkColor, radius = if (isSelected) 10.5f else 8.5f, center = topPt)
            drawCircle(Color.White, radius = if (isSelected) 3.5f else 2.8f, center = topPt)

            hitTargets += ProjectedMarkerHit(mk, topPt.x, topPt.y)
        }

        // 13. Multi-Scale Collision-Free Persian/English Labels (Native Canvas + Vazirmatn)
        val placedLabels = mutableListOf<RectF>()
        val nativeCanvas = drawContext.canvas.nativeCanvas

        fun tryDrawLabel(
            label: String,
            x: Float,
            y: Float,
            textSizeSp: Float,
            colorArgb: Int,
            bold: Boolean = false,
            padX: Float = 10f,
            padY: Float = 6f,
        ) {
            if (x < 28f || x > w - 28f || y < 22f || y > h - 34f) return
            val hp = if (bold) boldHaloPaint else haloPaint
            val tp = if (bold) boldTextPaint else textPaint
            hp.textSize = textSizeSp
            tp.textSize = textSizeSp
            tp.color = colorArgb

            val textW = tp.measureText(label)
            val fm = tp.fontMetrics
            val box = RectF(
                x - textW / 2f - padX,
                y + fm.ascent - padY,
                x + textW / 2f + padX,
                y + fm.descent + padY,
            )
            if (placedLabels.any { RectF.intersects(it, box) }) return
            placedLabels += box
            nativeCanvas.drawText(label, x, y, hp)
            nativeCanvas.drawText(label, x, y, tp)
        }

        // Selected POI label
        projectedMarkers.firstOrNull { it.first.id == selectedMarkerId }?.let { (mk, _, topPt) ->
            tryDrawLabel(
                label = mk.title,
                x = topPt.x,
                y = topPt.y - 24f,
                textSizeSp = 31f,
                colorArgb = if (isDark) Color.White.toArgb() else Color(0xFF0F172A).toArgb(),
                bold = true,
            )
        }

        // City / Atlas Labels across Iran & World at wide zoom
        if (effectiveDegSpan >= 0.45) {
            atlas.cities.sortedBy { it.tier }.forEach { city ->
                if (effectiveDegSpan > 22.0 && city.tier > 2) return@forEach
                val p = proj(city.lat, city.lon)
                val name = if (lang == AppLanguage.FA) city.nameFa else city.nameEn
                tryDrawLabel(
                    label = name,
                    x = p.x,
                    y = p.y - 6f,
                    textSizeSp = if (city.tier == 1) 30f else 24f,
                    colorArgb = districtTextArgb,
                    bold = city.tier == 1,
                )
            }
            if (effectiveDegSpan in 2.2..24.0) {
                atlas.iranProvinces.forEach { prov ->
                    val p = proj(prov.centerLat, prov.centerLon)
                    val label = if (lang == AppLanguage.FA) prov.nameFa else prov.nameEn
                    tryDrawLabel(label, p.x, p.y, 22f, provinceTextArgb, bold = false)
                }
            }
        }

        if (isCityDetailMode) {
            bundle.districts.sortedBy { it.importance }.forEach { d ->
                val p = proj(d.lat, d.lon, 0.0)
                val text = if (lang == AppLanguage.FA) d.nameFa else d.nameEn
                tryDrawLabel(
                    label = text,
                    x = p.x,
                    y = p.y - 4f,
                    textSizeSp = if (d.importance == 1) 29f else 24f,
                    colorArgb = districtTextArgb,
                    bold = d.importance == 1,
                )
            }

            projectedMarkers.forEach { (mk, _, topPt) ->
                val shortTitle = if (mk.title.length > 26) mk.title.take(24) + "…" else mk.title
                tryDrawLabel(
                    label = shortTitle,
                    x = topPt.x,
                    y = topPt.y - 18f,
                    textSizeSp = 23f,
                    colorArgb = markerColor(mk.category, colors).toArgb(),
                    bold = false,
                )
            }

            bundle.parks.forEach { park ->
                if (park.points.isNotEmpty()) {
                    val cLat = park.points.map { it.lat }.average()
                    val cLon = park.points.map { it.lon }.average()
                    val p = proj(cLat, cLon)
                    val label = if (lang == AppLanguage.FA) park.nameFa else park.nameEn
                    tryDrawLabel(label, p.x, p.y, 23f, parkTextArgb, bold = false)
                }
            }

            if (showStreets) {
                bundle.roads.filter { it.tier == "HIGHWAY" || it.tier == "PRIMARY" || zoomScale >= 1.4f }.forEach { road ->
                    if (road.points.size >= 2) {
                        val mid = road.points[road.points.size / 2]
                        val p = proj(mid.lat, mid.lon)
                        val label = if (lang == AppLanguage.FA) road.nameFa else road.nameEn
                        tryDrawLabel(label, p.x, p.y - 6f, 21f, streetTextArgb, bold = road.tier == "HIGHWAY")
                    }
                }
            }
        }

        // 14. Dynamic Metric Scale Bar
        val targetPx = 140.0
        val rawMeters = mpp * targetPx
        val niceMeters = listOf(100, 200, 500, 1000, 2000, 5000, 10000, 25000, 50000, 100000, 250000, 500000, 1000000)
            .minByOrNull { kotlin.math.abs(it - rawMeters) } ?: 1000
        val barWidthPx = (niceMeters / mpp).toFloat().coerceIn(54f, 220f)
        val barStart = Offset(24f, h - 22f)
        val barEnd = Offset(24f + barWidthPx, h - 22f)
        val scaleColor = if (isDark) Color(0xFFE2ECF5) else Color(0xFF0F172A)
        drawLine(scaleColor, barStart, barEnd, strokeWidth = 3.8f, cap = StrokeCap.Square)
        drawLine(scaleColor, Offset(barStart.x, barStart.y - 8f), barStart, strokeWidth = 2.8f)
        drawLine(scaleColor, Offset(barEnd.x, barEnd.y - 8f), barEnd, strokeWidth = 2.8f)

        val scaleLabel = if (niceMeters >= 1000) "${niceMeters / 1000} km" else "$niceMeters m"
        boldHaloPaint.textSize = 23f
        boldTextPaint.textSize = 23f
        boldTextPaint.color = scaleColor.toArgb()
        nativeCanvas.drawText(scaleLabel, barStart.x + barWidthPx / 2f, barStart.y - 11f, boldHaloPaint)
        nativeCanvas.drawText(scaleLabel, barStart.x + barWidthPx / 2f, barStart.y - 11f, boldTextPaint)
    }
}

private fun roadTierOrder(tier: String): Int = when (tier) {
    "SECONDARY" -> 0
    "TRAIL" -> 1
    "PRIMARY" -> 2
    "HIGHWAY" -> 3
    else -> 0
}

private fun buildingColors(kind: String, isDark: Boolean): Triple<Color, Color, Color> = when (kind) {
    "hospital" -> if (isDark) Triple(Color(0xCC581C2F), Color(0xEE9F1239), Color(0xFFFB7185)) else Triple(Color(0xFFFDA4AF), Color(0xFFFECDD3), Color(0xFFE11D48))
    "landmark" -> if (isDark) Triple(Color(0xCC5C4516), Color(0xEE926917), Color(0xFFFCD34D)) else Triple(Color(0xFFFDE68A), Color(0xFFFEF3C7), Color(0xFFB45309))
    "shelter" -> if (isDark) Triple(Color(0xCC11453B), Color(0xEE0F766E), Color(0xFF5EEAD4)) else Triple(Color(0xFFA7F3D0), Color(0xFFD1FAE5), Color(0xFF0F766E))
    else -> if (isDark) Triple(Color(0xCC17212C), Color(0xEE263445), Color(0xFF52667A)) else Triple(Color(0xFFCBD5E1), Color(0xFFE2E8F0), Color(0xFF94A3B8))
}

internal fun markerColor(category: MarkerCategory, colors: ir.aegis.core.ui.ObsidianColors): Color = when (category) {
    MarkerCategory.SHELTER -> colors.accentEmerald
    MarkerCategory.HOSPITAL -> colors.accentCrimson
    MarkerCategory.POLICE -> colors.accentCyan
    MarkerCategory.FIRE_STATION -> colors.accentAmber
    MarkerCategory.WATER_POINT -> colors.accentTeal
    MarkerCategory.ASSEMBLY_POINT -> colors.accentViolet
    MarkerCategory.HAZARD -> colors.accentCrimson
    MarkerCategory.CUSTOM -> colors.accentAmber
}
