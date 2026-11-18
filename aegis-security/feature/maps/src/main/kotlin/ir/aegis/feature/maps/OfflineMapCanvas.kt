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
import ir.aegis.core.maps.GeoCoord
import ir.aegis.core.maps.GeoMath
import ir.aegis.core.maps.OfflineRouteResult
import ir.aegis.core.maps.Projection3D
import ir.aegis.core.maps.VectorBuilding
import ir.aegis.core.maps.VectorMapBundle
import ir.aegis.core.model.AppLanguage
import ir.aegis.core.model.CrisisIncident
import ir.aegis.core.model.MapMarker
import ir.aegis.core.model.MarkerCategory
import ir.aegis.core.model.SavedRoute
import ir.aegis.core.ui.LocalAppLanguage
import ir.aegis.core.ui.LocalObsidianColors
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class ProjectedMarkerHit(
    val marker: MapMarker,
    val x: Float,
    val y: Float,
)

@Composable
internal fun OfflineVectorMapCanvas(
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
    incidents: List<CrisisIncident>,
    userLat: Double,
    userLon: Double,
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

    // Cartographic theme colors
    val mapBg = if (isDark) Color(0xFF0A1322) else Color(0xFFE7EDF4)
    val gridColor = if (isDark) Color(0xFF1A2942) else Color(0xFFCFD9E6)
    val parkFill = if (isDark) Color(0xCC123D32) else Color(0xCCBFE5C8)
    val parkBorder = if (isDark) Color(0xFF287D64) else Color(0xFF5DA872)
    val waterFill = if (isDark) Color(0xFF15466B) else Color(0xFF81C3E8)
    val contourColor = if (isDark) Color(0x4064748B) else Color(0x457C8BA1)
    val hwyCasing = if (isDark) Color(0xFF0F172A) else Color(0xFF92400E)
    val hwyFill = if (isDark) Color(0xFFD97706) else Color(0xFFF59E0B)
    val primaryCasing = if (isDark) Color(0xFF0B111E) else Color(0xFF94A3B8)
    val primaryFill = if (isDark) Color(0xFFCBD5E1) else Color(0xFFFFFFFF)
    val secondaryFill = if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1)
    val trailColor = if (isDark) Color(0xFF34D399) else Color(0xFF047857)
    val haloColorArgb = (if (isDark) Color(0xF0070D19) else Color(0xF2FFFFFF)).toArgb()
    val streetTextArgb = (if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)).toArgb()
    val districtTextArgb = (if (isDark) Color(0xFF7DD3FC) else Color(0xFF0369A1)).toArgb()
    val parkTextArgb = (if (isDark) Color(0xFF6EE7B7) else Color(0xFF065F46)).toArgb()

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
            .pointerInput(markers, zoomScale, yawDeg, pitchDeg, centerLat, centerLon) {
                detectTapGestures { tap ->
                    val hit = hitTargets.minByOrNull { hypot(it.x - tap.x, it.y - tap.y) }
                    if (hit != null && hypot(hit.x - tap.x, hit.y - tap.y) <= 44f) {
                        onSelectMarker(hit.marker)
                    }
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val pitch = if (is3DMode) pitchDeg else 0f
        val yaw = if (is3DMode) yawDeg else 0f

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

        // 1. Background cartographic surface
        drawRect(color = mapBg)

        // 2. Clean geographic graticule
        val latStep = 0.02
        val lonStep = 0.025
        val baseLat = ((centerLat - spanDeg) / latStep).toInt() * latStep
        val endLat = centerLat + spanDeg
        var gLat = baseLat
        while (gLat <= endLat) {
            val p1 = proj(gLat, centerLon - spanDeg * 1.4)
            val p2 = proj(gLat, centerLon + spanDeg * 1.4)
            drawLine(gridColor, Offset(p1.x, p1.y), Offset(p2.x, p2.y), strokeWidth = 1f)
            gLat += latStep
        }
        val baseLon = ((centerLon - spanDeg * 1.4) / lonStep).toInt() * lonStep
        val endLon = centerLon + spanDeg * 1.4
        var gLon = baseLon
        while (gLon <= endLon) {
            val p1 = proj(centerLat - spanDeg, gLon)
            val p2 = proj(centerLat + spanDeg, gLon)
            drawLine(gridColor, Offset(p1.x, p1.y), Offset(p2.x, p2.y), strokeWidth = 1f)
            gLon += lonStep
        }

        // 3. Topographic elevation contours
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
                        style = Stroke(
                            width = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f)),
                        ),
                    )
                }
            }
        }

        // 4. Parks & Open Assembly Greens
        bundle.parks.forEach { poly ->
            if (poly.points.size >= 3) {
                val path = Path()
                poly.points.forEachIndexed { idx, pt ->
                    val p = proj(pt.lat, pt.lon)
                    if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(path = path, color = parkFill, style = Fill)
                drawPath(path = path, color = parkBorder, style = Stroke(width = 2.2f))
            }
        }

        // 5. Waterways & Water Bodies
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
                        width = (stream.widthMeters * 0.28f * zoomScale).coerceIn(4f, 18f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }

        // 6. Multi-tier Road & Highway Network
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
                            val width = (7.2f * zoomScale).coerceIn(5.5f, 16f)
                            drawPath(path, hwyCasing, style = Stroke(width + 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            drawPath(path, hwyFill, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        "PRIMARY" -> {
                            val width = (4.8f * zoomScale).coerceIn(3.8f, 12f)
                            drawPath(path, primaryCasing, style = Stroke(width + 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            drawPath(path, primaryFill, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        "TRAIL" -> {
                            drawPath(
                                path,
                                trailColor,
                                style = Stroke(
                                    width = (3.4f * zoomScale).coerceIn(2.6f, 7f),
                                    cap = StrokeCap.Round,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                                ),
                            )
                        }
                        else -> {
                            drawPath(
                                path,
                                secondaryFill,
                                style = Stroke((2.8f * zoomScale).coerceIn(2f, 8f), cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )
                        }
                    }
                }
            }
        }

        // 7. 3D Extruded City Blocks & Landmarks (sorted back-to-front in 3D!)
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
                        drawPath(side, edgeColor.copy(alpha = 0.45f), style = Stroke(1.2f))
                    }
                }
                val roofPath = Path().apply {
                    bp.roofPts.forEachIndexed { idx, pt ->
                        if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                    }
                    close()
                }
                drawPath(roofPath, roofColor, style = Fill)
                drawPath(roofPath, edgeColor, style = Stroke(1.8f))
            }
        }

        // 8. Saved Offline Routes (subtle dashed)
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
                        style = Stroke(width = 4.2f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
                    )
                }
            }
        }

        // 9. Active Computed Safe-Corridor Route (glowing navigation path!)
        if (activeRoute != null && activeRoute.waypoints.size >= 2) {
            val routePath = Path()
            val projectedWps = activeRoute.waypoints.map { wp ->
                val p = proj(wp.latitude, wp.longitude, 12.0)
                Offset(p.x, if (is3DMode) p.extrudedTopY else p.y)
            }
            projectedWps.forEachIndexed { idx, pt ->
                if (idx == 0) routePath.moveTo(pt.x, pt.y) else routePath.lineTo(pt.x, pt.y)
            }
            drawPath(routePath, Color(0x6606B6D4), style = Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(routePath, Color(0xFF0F172A), style = Stroke(width = 9.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(routePath, Color(0xFF22D3EE), style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))

            projectedWps.forEachIndexed { idx, pt ->
                val isEndpoint = idx == 0 || idx == projectedWps.lastIndex
                drawCircle(
                    color = if (isEndpoint) Color(0xFF34D399) else Color.White,
                    radius = if (isEndpoint) 8f else 4f,
                    center = pt,
                )
            }
        }

        // 10. Live Crisis Incident Zones (Multi-Engine Link with Crisis Management!)
        incidents.filter { it.latitude != null && it.longitude != null && it.status.name != "RESOLVED" }.forEach { inc ->
            val p = proj(inc.latitude!!, inc.longitude!!, 15.0)
            val py = if (is3DMode) p.extrudedTopY else p.y
            if (p.x in -60f..(w + 60f) && py in -60f..(h + 60f)) {
                drawCircle(colors.accentCrimson.copy(alpha = 0.22f), radius = 34f * zoomScale.coerceIn(0.8f, 1.5f), center = Offset(p.x, py))
                drawCircle(colors.accentCrimson, radius = 34f * zoomScale.coerceIn(0.8f, 1.5f), center = Offset(p.x, py), style = Stroke(2.2f))
                drawCircle(colors.accentCrimson, radius = 8f, center = Offset(p.x, py))
            }
        }

        // 11. Ruler Line
        if (rulerStart != null && rulerEnd != null) {
            val s = proj(rulerStart.latitude, rulerStart.longitude, 10.0)
            val e = proj(rulerEnd.latitude, rulerEnd.longitude, 10.0)
            val sy = if (is3DMode) s.extrudedTopY else s.y
            val ey = if (is3DMode) e.extrudedTopY else e.y
            drawLine(colors.accentAmber, Offset(s.x, sy), Offset(e.x, ey), strokeWidth = 5f, cap = StrokeCap.Round)
            drawCircle(colors.accentAmber, radius = 11f, center = Offset(s.x, sy))
            drawCircle(colors.accentAmber, radius = 11f, center = Offset(e.x, ey))
        }

        // 12. User GPS / Offline Anchor Pulse Marker
        run {
            val me = proj(userLat, userLon, 8.0)
            val mey = if (is3DMode) me.extrudedTopY else me.y
            drawCircle(Color(0x3838BDF8), radius = 26f, center = Offset(me.x, mey))
            drawCircle(Color.White, radius = 11f, center = Offset(me.x, mey))
            drawCircle(Color(0xFF0284C7), radius = 7.5f, center = Offset(me.x, mey))
        }

        // 13. Emergency & Custom POI Markers + Hit Boxes
        hitTargets.clear()
        val projectedMarkers = markers.map { mk ->
            val relElev = (mk.elevationMeters - bundle.baseElevationMeters).coerceIn(10.0, 260.0)
            val p = proj(mk.latitude, mk.longitude, relElev)
            val py = if (is3DMode) p.extrudedTopY else p.y
            Triple(mk, p, Offset(p.x, py))
        }.sortedBy { it.second.depth }

        projectedMarkers.forEach { (mk, baseProj, topPt) ->
            val mkColor = markerColor(mk.category, colors)
            val isSelected = mk.id == selectedMarkerId || mk.id == rulerStart?.id || mk.id == rulerEnd?.id

            if (is3DMode) {
                drawLine(
                    color = mkColor.copy(alpha = 0.72f),
                    start = Offset(baseProj.x, baseProj.y),
                    end = topPt,
                    strokeWidth = 2.6f,
                )
            }
            if (isSelected) {
                drawCircle(mkColor.copy(alpha = 0.30f), radius = 24f, center = topPt)
                drawCircle(Color.White, radius = 14.5f, center = topPt)
            } else {
                drawCircle(Color(0xDD0B1324), radius = 12f, center = topPt)
            }
            drawCircle(mkColor, radius = if (isSelected) 11f else 9f, center = topPt)
            drawCircle(Color.White, radius = if (isSelected) 3.8f else 3f, center = topPt)

            hitTargets += ProjectedMarkerHit(mk, topPt.x, topPt.y)
        }

        // 14. Collision-Free Bilingual Map Labels (Native Canvas with Vazirmatn + Halo)
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
            if (x < 30f || x > w - 30f || y < 24f || y > h - 38f) return
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

        // Priority 1: Selected POI label
        projectedMarkers.firstOrNull { it.first.id == selectedMarkerId }?.let { (mk, _, topPt) ->
            tryDrawLabel(
                label = mk.title,
                x = topPt.x,
                y = topPt.y - 24f,
                textSizeSp = 31f,
                colorArgb = Color.White.takeIf { isDark }?.toArgb() ?: Color(0xFF0F172A).toArgb(),
                bold = true,
            )
        }

        // Priority 2: Top Districts & Major Landmarks
        bundle.districts.sortedBy { it.importance }.forEach { d ->
            val p = proj(d.lat, d.lon, 0.0)
            val text = if (lang == AppLanguage.FA) d.nameFa else d.nameEn
            tryDrawLabel(
                label = text,
                x = p.x,
                y = p.y - 4f,
                textSizeSp = if (d.importance == 1) 30f else 25f,
                colorArgb = districtTextArgb,
                bold = true,
            )
        }

        // Priority 3: Emergency POI Marker Labels
        projectedMarkers.forEach { (mk, _, topPt) ->
            val shortTitle = if (mk.title.length > 26) mk.title.take(24) + "…" else mk.title
            tryDrawLabel(
                label = shortTitle,
                x = topPt.x,
                y = topPt.y - 18f,
                textSizeSp = 24f,
                colorArgb = markerColor(mk.category, colors).toArgb(),
                bold = false,
            )
        }

        // Priority 4: Parks
        bundle.parks.forEach { park ->
            if (park.points.isNotEmpty()) {
                val cLat = park.points.map { it.lat }.average()
                val cLon = park.points.map { it.lon }.average()
                val p = proj(cLat, cLon)
                val label = if (lang == AppLanguage.FA) park.nameFa else park.nameEn
                tryDrawLabel(label, p.x, p.y, 24f, parkTextArgb, bold = false)
            }
        }

        // Priority 5: Major Street & Highway Names (when zoomed or primary)
        if (showStreets) {
            bundle.roads.filter { it.tier == "HIGHWAY" || it.tier == "PRIMARY" || zoomScale >= 1.4f }.forEach { road ->
                if (road.points.size >= 2) {
                    val mid = road.points[road.points.size / 2]
                    val p = proj(mid.lat, mid.lon)
                    val label = if (lang == AppLanguage.FA) road.nameFa else road.nameEn
                    tryDrawLabel(
                        label = label,
                        x = p.x,
                        y = p.y - 6f,
                        textSizeSp = 22f,
                        colorArgb = streetTextArgb,
                        bold = road.tier == "HIGHWAY",
                    )
                }
            }
        }

        // Priority 6: Dynamic Scale Bar (bottom-left)
        val mpp = Projection3D.metersPerPixel(centerLat, spanDeg, w, h, zoomScale)
        val targetPx = 140.0
        val rawMeters = mpp * targetPx
        val niceMeters = listOf(100, 200, 500, 1000, 2000, 5000, 10000).minByOrNull { kotlin.math.abs(it - rawMeters) } ?: 1000
        val barWidthPx = (niceMeters / mpp).toFloat().coerceIn(56f, 220f)
        val barStart = Offset(24f, h - 24f)
        val barEnd = Offset(24f + barWidthPx, h - 24f)
        val scaleColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF0F172A)
        drawLine(scaleColor, barStart, barEnd, strokeWidth = 4f, cap = StrokeCap.Square)
        drawLine(scaleColor, Offset(barStart.x, barStart.y - 8f), barStart, strokeWidth = 3f)
        drawLine(scaleColor, Offset(barEnd.x, barEnd.y - 8f), barEnd, strokeWidth = 3f)

        val scaleLabel = if (niceMeters >= 1000) "${niceMeters / 1000} km" else "$niceMeters m"
        boldHaloPaint.textSize = 24f
        boldTextPaint.textSize = 24f
        boldTextPaint.color = scaleColor.toArgb()
        nativeCanvas.drawText(scaleLabel, barStart.x + barWidthPx / 2f, barStart.y - 12f, boldHaloPaint)
        nativeCanvas.drawText(scaleLabel, barStart.x + barWidthPx / 2f, barStart.y - 12f, boldTextPaint)
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
    "landmark" -> if (isDark) Triple(Color(0xCC6B4E16), Color(0xEEB45309), Color(0xFFFBBF24)) else Triple(Color(0xFFFDE68A), Color(0xFFFEF3C7), Color(0xFFD97706))
    "shelter" -> if (isDark) Triple(Color(0xCC114B3C), Color(0xEE047857), Color(0xFF34D399)) else Triple(Color(0xFFA7F3D0), Color(0xFFD1FAE5), Color(0xFF059669))
    else -> if (isDark) Triple(Color(0xCC1E293B), Color(0xEE334155), Color(0xFF64748B)) else Triple(Color(0xFFCBD5E1), Color(0xFFE2E8F0), Color(0xFF94A3B8))
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
