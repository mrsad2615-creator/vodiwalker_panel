package ir.aegis.feature.maps

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import ir.aegis.core.maps.GeoBBox
import ir.aegis.core.maps.IndexedBaseAtlas
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
import kotlin.math.hypot

internal data class ProjectedMarkerHit(
    val marker: MapMarker,
    val x: Float,
    val y: Float,
)

@Composable
internal fun OfflineVectorMapCanvas(
    atlas: IndexedBaseAtlas,
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
    onLongPressMap: ((lat: Double, lon: Double) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalObsidianColors.current
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val isDark = colors.isDark

    val currentTransformCb by rememberUpdatedState(onTransformGesture)
    val currentSelectMarkerCb by rememberUpdatedState(onSelectMarker)
    val currentLongPressCb by rememberUpdatedState(onLongPressMap)
    val currentCenterLat by rememberUpdatedState(centerLat)
    val currentCenterLon by rememberUpdatedState(centerLon)
    val currentSpanDeg by rememberUpdatedState(spanDeg)
    val currentZoomScale by rememberUpdatedState(zoomScale)

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

    // High-clarity Cartographic Palette tailored for Frosted Glass
    val oceanBg = if (isDark) Color(0xFF070E16) else Color(0xFFD5E3EF)
    val landFill = if (isDark) Color(0xFF101923) else Color(0xFFF3F6FA)
    val landBorder = if (isDark) Color(0xFF283A4D) else Color(0xFFB3C3D4)
    val iranProvinceFill = if (isDark) Color(0xCC13222E) else Color(0xDDEAF1F6)
    val iranProvinceBorder = if (isDark) Color(0xFF2C6B66) else Color(0xFF5D928E)
    val nationalHwyColor = if (isDark) Color(0xBB34D399) else Color(0xCC0F766E)
    val gridColor = if (isDark) Color(0x22334155) else Color(0x3294A3B8)

    val parkFill = if (isDark) Color(0xBA113C33) else Color(0xCCBCE6CE)
    val parkBorder = if (isDark) Color(0xFF2E9C7B) else Color(0xFF47996B)
    val waterFill = if (isDark) Color(0xFF10405F) else Color(0xFF7CC1E4)
    val contourColor = if (isDark) Color(0x3E64748B) else Color(0x457C8BA1)
    val hwyCasing = if (isDark) Color(0xFF0A1016) else Color(0xFF78350F)
    val hwyFill = if (isDark) Color(0xFFF59E0B) else Color(0xFFF59E0B)
    val primaryCasing = if (isDark) Color(0xFF0A0F14) else Color(0xFF94A3B8)
    val primaryFill = if (isDark) Color(0xFFDCE7F0) else Color(0xFFFFFFFF)
    val secondaryFill = if (isDark) Color(0xFF47596C) else Color(0xFFCBD5E1)
    val trailColor = if (isDark) Color(0xFF5EEAD4) else Color(0xFF0F766E)

    val haloColorArgb = (if (isDark) Color(0xF2060B11) else Color(0xF6FFFFFF)).toArgb()
    val streetTextArgb = (if (isDark) Color(0xFFE4EEF7) else Color(0xFF1E293B)).toArgb()
    val districtTextArgb = (if (isDark) Color(0xFF5EEAD4) else Color(0xFF0F766E)).toArgb()
    val provinceTextArgb = (if (isDark) Color(0xFF8DB6C9) else Color(0xFF33556D)).toArgb()
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
                    currentTransformCb(pan, zoom)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tap ->
                        val hit = hitTargets.minByOrNull { hypot(it.x - tap.x, it.y - tap.y) }
                        if (hit != null && hypot(hit.x - tap.x, hit.y - tap.y) <= 58f) {
                            currentSelectMarkerCb(hit.marker)
                        }
                    },
                    onLongPress = { tap ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val aspect = kotlin.math.cos(Math.toRadians(currentCenterLat)).coerceAtLeast(0.3)
                        val unitPx = (minOf(w, h) * 0.95f * currentZoomScale).coerceAtLeast(1f)
                        val lon = currentCenterLon + ((tap.x - w * 0.5f) / unitPx) * (currentSpanDeg / aspect)
                        val lat = currentCenterLat - ((tap.y - h * 0.52f) / unitPx) * currentSpanDeg
                        currentLongPressCb?.invoke(lat.coerceIn(-85.0, 85.0), lon.coerceIn(-180.0, 180.0))
                    },
                )
            },
    ) {
        val w = size.width
        val h = size.height
        val pitch = if (is3DMode) pitchDeg else 0f
        val yaw = if (is3DMode) yawDeg else 0f

        val effectiveDegSpan = spanDeg / zoomScale.toDouble().coerceAtLeast(0.05)
        val isCityDetailMode = effectiveDegSpan < 1.25 && bundle.packageId !in setOf("atlas-iran", "atlas-world")
        val isNationalView = effectiveDegSpan in 0.45..28.0

        val viewLatHalf = effectiveDegSpan * 1.15
        val viewLonHalf = effectiveDegSpan * 1.45
        val viewBox = GeoBBox(
            minLat = centerLat - viewLatHalf,
            maxLat = centerLat + viewLatHalf,
            minLon = centerLon - viewLonHalf,
            maxLon = centerLon + viewLonHalf,
        )

        fun intersectsView(b: GeoBBox): Boolean =
            !(b.maxLat < viewBox.minLat || b.minLat > viewBox.maxLat || b.maxLon < viewBox.minLon || b.minLon > viewBox.maxLon)

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

        // 1. Ocean / Land Canvas Background
        // When deep inside a city, fill with landFill so street tiles never flash ocean blue during fast pans!
        drawRect(color = if (effectiveDegSpan < 0.85) landFill else oceanBg)

        // 2. O(1) BBox-Culled Global Country Polygons
        if (effectiveDegSpan >= 0.45) {
            atlas.countries.forEach { country ->
                if (!intersectsView(country.bbox)) return@forEach
                for (ring in country.rings) {
                    if (!intersectsView(ring.bbox)) continue
                    val path = Path()
                    val step = if (effectiveDegSpan > 30.0 && ring.points.size > 40) 2 else 1
                    var idx = 0
                    while (idx < ring.points.size) {
                        val pt = ring.points[idx]
                        val p = proj(pt.lat, pt.lon)
                        if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                        idx += step
                    }
                    path.close()
                    drawPath(path, landFill, style = Fill)
                    drawPath(path, landBorder, style = Stroke(width = if (isNationalView) 1.5f else 1.0f))
                }
            }
        }

        // 3. O(1) BBox-Culled Iranian Provinces (30 provinces)
        if (effectiveDegSpan in 0.25..40.0) {
            atlas.iranProvinces.forEach { prov ->
                if (!intersectsView(prov.bbox)) return@forEach
                for (ring in prov.rings) {
                    if (!intersectsView(ring.bbox)) continue
                    val path = Path()
                    ring.points.forEachIndexed { idx, pt ->
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
        if (effectiveDegSpan in 0.35..35.0) {
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
                        style = Stroke(width = if (effectiveDegSpan < 4.0) 4.2f else 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
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

        // 7. Saved Routes
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
                        color = colors.accentTeal.copy(alpha = 0.55f),
                        style = Stroke(width = 3.8f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
                    )
                }
            }
        }

        // 8. Active Dijkstra Route
        if (activeRoute != null && activeRoute.waypoints.size >= 2) {
            val routePath = Path()
            val projectedWps = activeRoute.waypoints.map { wp ->
                val p = proj(wp.latitude, wp.longitude, 12.0)
                Offset(p.x, if (is3DMode) p.extrudedTopY else p.y)
            }
            projectedWps.forEachIndexed { idx, pt ->
                if (idx == 0) routePath.moveTo(pt.x, pt.y) else routePath.lineTo(pt.x, pt.y)
            }
            drawPath(routePath, colors.accentCyan.copy(alpha = 0.36f), style = Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
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

        // 9. Incident Zones
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

        // 11. GPS Accuracy Halo + Directional Compass Cone + User Marker
        val mpp = Projection3D.metersPerPixel(centerLat, spanDeg, w, h, zoomScale)
        run {
            val me = proj(userLat, userLon, 8.0)
            val mey = if (is3DMode) me.extrudedTopY else me.y
            val centerPt = Offset(me.x, mey)

            if (centerPt.x in -200f..(w + 200f) && centerPt.y in -200f..(h + 200f)) {
                val accuracyRadiusPx = (gpsAccuracyMeters / mpp.toFloat()).coerceIn(24f, 130f)
                drawCircle(color = colors.accentCyan.copy(alpha = 0.14f), radius = accuracyRadiusPx, center = centerPt)
                drawCircle(color = colors.accentCyan.copy(alpha = 0.45f), radius = accuracyRadiusPx, center = centerPt, style = Stroke(width = 1.6f))

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

                drawCircle(Color.White, radius = 12f, center = centerPt)
                drawCircle(colors.accentCyan, radius = 8.5f, center = centerPt)
                drawCircle(Color.White, radius = 2.8f, center = centerPt)
            }
        }

        // 12. POI Markers + Hit Targets
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
                drawCircle(mkColor.copy(alpha = 0.30f), radius = 25f, center = topPt)
                drawCircle(Color.White, radius = 14.5f, center = topPt)
            } else {
                drawCircle(Color(0xDD0B131B), radius = 12f, center = topPt)
            }
            drawCircle(mkColor, radius = if (isSelected) 11f else 9f, center = topPt)
            drawCircle(Color.White, radius = if (isSelected) 3.8f else 3f, center = topPt)

            hitTargets += ProjectedMarkerHit(mk, topPt.x, topPt.y)
        }

        // 13. Collision-Free Labels
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
            if (x < 30f || x > w - 30f || y < 90f || y > h - 140f) return
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

        projectedMarkers.firstOrNull { it.first.id == selectedMarkerId }?.let { (mk, _, topPt) ->
            tryDrawLabel(
                label = mk.title,
                x = topPt.x,
                y = topPt.y - 24f,
                textSizeSp = 32f,
                colorArgb = if (isDark) Color.White.toArgb() else Color(0xFF0F172A).toArgb(),
                bold = true,
            )
        }

        if (effectiveDegSpan >= 0.45) {
            atlas.cities.sortedBy { it.tier }.forEach { city ->
                if (effectiveDegSpan > 22.0 && city.tier > 2) return@forEach
                val p = proj(city.lat, city.lon)
                val name = if (lang == AppLanguage.FA) city.nameFa else city.nameEn
                tryDrawLabel(name, p.x, p.y - 6f, if (city.tier == 1) 30f else 24f, districtTextArgb, bold = city.tier == 1)
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
                tryDrawLabel(text, p.x, p.y - 4f, if (d.importance == 1) 29f else 24f, districtTextArgb, bold = d.importance == 1)
            }

            projectedMarkers.forEach { (mk, _, topPt) ->
                val shortTitle = if (mk.title.length > 26) mk.title.take(24) + "…" else mk.title
                tryDrawLabel(shortTitle, topPt.x, topPt.y - 18f, 23f, markerColor(mk.category, colors).toArgb(), bold = false)
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
        val targetPx = 130.0
        val rawMeters = mpp * targetPx
        val niceMeters = listOf(100, 200, 500, 1000, 2000, 5000, 10000, 25000, 50000, 100000, 250000, 500000, 1000000)
            .minByOrNull { kotlin.math.abs(it - rawMeters) } ?: 1000
        val barWidthPx = (niceMeters / mpp).toFloat().coerceIn(54f, 200f)
        val barStart = Offset(24f, h - 180f)
        val barEnd = Offset(24f + barWidthPx, h - 180f)
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
