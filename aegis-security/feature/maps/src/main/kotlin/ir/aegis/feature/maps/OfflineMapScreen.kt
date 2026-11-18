package ir.aegis.feature.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.aegis.core.maps.GeoMath
import ir.aegis.core.maps.Projection3D
import ir.aegis.core.model.*
import ir.aegis.core.ui.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@SuppressLint("MissingPermission")
@Composable
fun OfflineMapScreen(
    repository: AegisRepository,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalObsidianColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val packages by repository.observeMapPackages().collectAsState(initial = emptyList())
    val markers by repository.observeMapMarkers().collectAsState(initial = emptyList())
    val routes by repository.observeSavedRoutes().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) }
    var is3DMode by remember { mutableStateOf(true) }
    var activePackId by remember { mutableStateOf("pack-tehran-grid") }
    var yawDeg by remember { mutableFloatStateOf(24f) }
    var pitchDeg by remember { mutableFloatStateOf(46f) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    var showEmergencyLayer by remember { mutableStateOf(true) }
    var showCustomLayer by remember { mutableStateOf(true) }
    var showRoutesLayer by remember { mutableStateOf(true) }
    var categoryFilter by remember { mutableStateOf<MarkerCategory?>(null) }

    var rulerMode by remember { mutableStateOf(false) }
    var rulerStart by remember { mutableStateOf<MapMarker?>(null) }
    var rulerEnd by remember { mutableStateOf<MapMarker?>(null) }

    var compassAzimuth by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val g = FloatArray(3)
        val m = FloatArray(3)
        val rMat = FloatArray(9)
        val orient = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) System.arraycopy(event.values, 0, g, 0, 3)
                if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) System.arraycopy(event.values, 0, m, 0, 3)
                if (SensorManager.getRotationMatrix(rMat, null, g, m)) {
                    SensorManager.getOrientation(rMat, orient)
                    compassAzimuth = ((Math.toDegrees(orient[0].toDouble()) + 360.0) % 360.0).toFloat()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (accel != null && mag != null) {
            sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
            sm.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sm?.unregisterListener(listener) }
    }

    var hasLocationPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    var liveGpsLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPerm = granted
    }
    DisposableEffect(hasLocationPerm) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val listener = LocationListener { loc: Location -> liveGpsLocation = loc.latitude to loc.longitude }
        if (hasLocationPerm && lm != null) {
            runCatching {
                val last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (last != null) liveGpsLocation = last.latitude to last.longitude
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 4000L, 5f, listener)
            }
        }
        onDispose { runCatching { lm?.removeUpdates(listener) } }
    }

    val activePackage = packages.firstOrNull { it.id == activePackId } ?: packages.firstOrNull()
    val visibleMarkers = remember(markers, activePackage, showEmergencyLayer, showCustomLayer, categoryFilter) {
        markers.filter { mk ->
            val packOk = activePackage == null || mk.packageId == activePackage.id
            val layerOk = (mk.isEmergencyResource && showEmergencyLayer) || (mk.isCustom && showCustomLayer) || (!mk.isEmergencyResource && !mk.isCustom)
            val catOk = categoryFilter == null || mk.category == categoryFilter
            packOk && layerOk && catOk
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            module = AegisModule.MAPS,
            onBack = onBack,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            extraBadge = if (liveGpsLocation != null) "GPS FIX ACTIVE" else "OFFLINE VECTOR GRID · NO SIGNAL NEEDED",
        )
        GlassTabRow(
            tabs = listOf(
                tr("2D/3D Map", "نقشه ۲D/۳D"),
                tr("Offline Packs", "بسته‌های آفلاین"),
                tr("Add Marker / Route", "نقطه و مسیر"),
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )
        Spacer(Modifier.height(8.dp))

        when (selectedTab) {
            0 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        ObsidianGlassCard(elevated = true, contentPadding = PaddingValues(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(activePackage?.regionName ?: "Offline Grid", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(selected = is3DMode, onClick = { is3DMode = !is3DMode }, label = { Text(if (is3DMode) "3D HYBRID" else "2D TOPO") })
                                    FilterChip(selected = rulerMode, onClick = { rulerMode = !rulerMode; if (!rulerMode) { rulerStart = null; rulerEnd = null } }, label = { Text(tr("Ruler", "خط‌کش")) })
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            val centerLat = activePackage?.let { (it.minLat + it.maxLat) / 2.0 } ?: 35.6892
                            val centerLon = activePackage?.let { (it.minLon + it.maxLon) / 2.0 } ?: 51.3890
                            val spanDeg = activePackage?.let { (it.maxLat - it.minLat).coerceAtLeast(0.06) } ?: 0.12

                            Box(modifier = Modifier.fillMaxWidth().height(275.dp)) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(is3DMode, yawDeg) {
                                            detectDragGestures { _, dragAmount ->
                                                if (is3DMode) {
                                                    yawDeg = (yawDeg + dragAmount.x * 0.2f) % 360f
                                                    pitchDeg = (pitchDeg + dragAmount.y * 0.15f).coerceIn(10f, 60f)
                                                } else {
                                                    panX += dragAmount.x
                                                    panY += dragAmount.y
                                                }
                                            }
                                        }
                                        .pointerInput(visibleMarkers, rulerMode) {
                                            detectTapGestures { tapOffset ->
                                                if (rulerMode && visibleMarkers.isNotEmpty()) {
                                                    val nearest = visibleMarkers.minByOrNull { mk ->
                                                        val p = projectGeo(mk.latitude, mk.longitude, mk.elevationMeters, centerLat, centerLon, is3DMode, yawDeg, pitchDeg, spanDeg, size.width.toFloat(), size.height.toFloat(), zoomScale, panX, panY)
                                                        (p.first.x - tapOffset.x) * (p.first.x - tapOffset.x) + (p.first.y - tapOffset.y) * (p.first.y - tapOffset.y)
                                                    }
                                                    if (nearest != null) {
                                                        if (rulerStart == null) rulerStart = nearest else rulerEnd = nearest
                                                    }
                                                }
                                            }
                                        },
                                ) {
                                    for (gx in -4..4) {
                                        for (gy in -4..4) {
                                            val lat1 = centerLat + gx * (spanDeg * 0.12)
                                            val lon1 = centerLon + gy * (spanDeg * 0.12)
                                            val lat2 = centerLat + (gx + 1) * (spanDeg * 0.12)
                                            val lon2 = centerLon + (gy + 1) * (spanDeg * 0.12)
                                            val p1 = projectGeo(lat1, lon1, 0.0, centerLat, centerLon, is3DMode, yawDeg, pitchDeg, spanDeg, size.width, size.height, zoomScale, panX, panY).first
                                            val p2 = projectGeo(lat2, lon1, 0.0, centerLat, centerLon, is3DMode, yawDeg, pitchDeg, spanDeg, size.width, size.height, zoomScale, panX, panY).first
                                            val p3 = projectGeo(lat1, lon2, 0.0, centerLat, centerLon, is3DMode, yawDeg, pitchDeg, spanDeg, size.width, size.height, zoomScale, panX, panY).first
                                            drawLine(colors.accentCyan.copy(alpha = 0.14f), p1, p2, strokeWidth = 1.2f)
                                            drawLine(colors.accentCyan.copy(alpha = 0.14f), p1, p3, strokeWidth = 1.2f)
                                        }
                                    }

                                    if (is3DMode) {
                                        val ridgePath = Path()
                                        for (step in -5..5) {
                                            val lat = centerLat + spanDeg * 0.25
                                            val lon = centerLon + step * (spanDeg * 0.08)
                                            val elev = 450.0 * abs(sin(step * 0.8))
                                            val pt = projectGeo(lat, lon, elev, centerLat, centerLon, true, yawDeg, pitchDeg, spanDeg, size.width, size.height, zoomScale, panX, panY).second
                                            if (step == -5) ridgePath.moveTo(pt.x, pt.y) else ridgePath.lineTo(pt.x, pt.y)
                                        }
                                        drawPath(ridgePath, colors.accentViolet.copy(alpha = 0.45f), style = Stroke(width = 3f))
                                    }

                                    if (showRoutesLayer) {
                                        routes.filter { activePackage == null || it.packageId == activePackage.id }.forEach { route ->
                                            val pts = route.waypoints.map { wp ->
                                                projectGeo(wp.latitude, wp.longitude, 120.0, centerLat, centerLon, is3DMode, yawDeg, pitchDeg, spanDeg, size.width, size.height, zoomScale, panX, panY).second
                                            }
                                            for (i in 0 until pts.size - 1) {
                                                drawLine(colors.accentTeal, pts[i], pts[i + 1], strokeWidth = 4.5f)
                                            }
                                        }
                                    }

                                    visibleMarkers.forEach { mk ->
                                        val pair = projectGeo(mk.latitude, mk.longitude, mk.elevationMeters.coerceAtLeast(180.0), centerLat, centerLon, is3DMode, yawDeg, pitchDeg, spanDeg, size.width, size.height, zoomScale, panX, panY)
                                        val mkColor = when (mk.category) {
                                            MarkerCategory.HOSPITAL, MarkerCategory.HAZARD -> colors.accentCrimson
                                            MarkerCategory.SHELTER, MarkerCategory.ASSEMBLY_POINT -> colors.accentEmerald
                                            MarkerCategory.FIRE_STATION -> colors.accentAmber
                                            else -> colors.accentCyan
                                        }
                                        if (is3DMode) {
                                            drawLine(mkColor.copy(alpha = 0.7f), pair.first, pair.second, strokeWidth = 3f)
                                        }
                                        drawCircle(mkColor.copy(alpha = 0.25f), radius = 14f, center = pair.second)
                                        drawCircle(mkColor, radius = 6.5f, center = pair.second)
                                    }

                                    val s = rulerStart
                                    val e = rulerEnd
                                    if (s != null && e != null) {
                                        val ps = projectGeo(s.latitude, s.longitude, s.elevationMeters, centerLat, centerLon, is3DMode, yawDeg, pitchDeg, spanDeg, size.width, size.height, zoomScale, panX, panY).second
                                        val pe = projectGeo(e.latitude, e.longitude, e.elevationMeters, centerLat, centerLon, is3DMode, yawDeg, pitchDeg, spanDeg, size.width, size.height, zoomScale, panX, panY).second
                                        drawLine(colors.accentAmber, ps, pe, strokeWidth = 4f)
                                    }

                                    val hudCenter = Offset(size.width - 36f, 36f)
                                    drawCircle(colors.glassElevated, radius = 24f, center = hudCenter)
                                    drawCircle(colors.accentCyan, radius = 24f, center = hudCenter, style = Stroke(width = 2f))
                                    val rad = Math.toRadians((-compassAzimuth).toDouble())
                                    drawLine(colors.accentCrimson, hudCenter, Offset(hudCenter.x + (18f * sin(rad)).toFloat(), hudCenter.y - (18f * cos(rad)).toFloat()), strokeWidth = 3.5f)
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("Zoom", "بزرگنمایی"), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                Slider(value = zoomScale, onValueChange = { zoomScale = it }, valueRange = 0.6f..2.4f, modifier = Modifier.weight(1f))
                                Text(GeoMath.formatBearing(compassAzimuth.toDouble()), style = MaterialTheme.typography.labelMedium, color = colors.accentCyan)
                            }

                            if (rulerMode) {
                                val distText = if (rulerStart != null && rulerEnd != null) {
                                    val d = GeoMath.haversineMeters(rulerStart!!.latitude, rulerStart!!.longitude, rulerEnd!!.latitude, rulerEnd!!.longitude)
                                    val brg = GeoMath.bearingDegrees(rulerStart!!.latitude, rulerStart!!.longitude, rulerEnd!!.latitude, rulerEnd!!.longitude)
                                    "${rulerStart!!.title} -> ${rulerEnd!!.title}: ${GeoMath.formatDistance(d)} (${GeoMath.formatBearing(brg)})"
                                } else tr("Ruler active: tap two map markers to measure distance & bearing.", "حالت خط‌کش: دو نقطه را لمس کنید.")
                                Text(distText, style = MaterialTheme.typography.labelMedium, color = colors.accentAmber)
                            }
                        }
                    }

                    item {
                        ObsidianGlassCard {
                            val dispLat = liveGpsLocation?.first ?: (activePackage?.let { (it.minLat + it.maxLat) / 2.0 } ?: 35.6892)
                            val dispLon = liveGpsLocation?.second ?: (activePackage?.let { (it.minLon + it.maxLon) / 2.0 } ?: 51.3890)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (liveGpsLocation != null) tr("Live Hardware GPS Fix", "موقعیت زنده GPS") else tr("Offline Grid Reference (No GPS Fix)", "مرجع شبکه آفلاین (بدون GPS)"),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (liveGpsLocation != null) colors.accentEmerald else colors.accentAmber,
                                    )
                                    Text("DMS: ${GeoMath.formatDms(dispLat, dispLon)}", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                    Text("UTM: ${GeoMath.formatUtmApprox(dispLat, dispLon)}", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                }
                                if (!hasLocationPerm) {
                                    TextButton(onClick = { permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                                        Text(tr("Enable GPS", "فعال‌سازی GPS"))
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(selected = showEmergencyLayer, onClick = { showEmergencyLayer = !showEmergencyLayer }, label = { Text(tr("Emergency", "اضطراری")) })
                                FilterChip(selected = showCustomLayer, onClick = { showCustomLayer = !showCustomLayer }, label = { Text(tr("Custom", "سفارشی")) })
                                FilterChip(selected = showRoutesLayer, onClick = { showRoutesLayer = !showRoutesLayer }, label = { Text(tr("Routes", "مسیرها")) })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(null, MarkerCategory.SHELTER, MarkerCategory.HOSPITAL, MarkerCategory.FIRE_STATION).forEach { cat ->
                                    FilterChip(selected = categoryFilter == cat, onClick = { categoryFilter = cat }, label = { Text(cat?.name ?: tr("All", "همه"), style = MaterialTheme.typography.labelSmall) })
                                }
                            }
                        }
                    }

                    items(visibleMarkers, key = { it.id }) { mk ->
                        ObsidianGlassCard {
                            Text(mk.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Text("${mk.category.name} · ${GeoMath.formatDecimalDegrees(mk.latitude, mk.longitude)} · ${mk.elevationMeters.toInt()}m", style = MaterialTheme.typography.labelSmall, color = colors.accentTeal)
                            if (mk.description.isNotBlank()) Text(mk.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        }
                    }
                }
            }

            1 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(packages, key = { it.id }) { pack ->
                        ObsidianGlassCard(elevated = pack.id == activePackId) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pack.regionName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                    Text("${pack.regionCode} · ${pack.tileCount} tiles · ${(pack.sizeBytes / 1_048_576)} MB · ${pack.versionTag}", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                    Text("SHA256: ${pack.sha256Checksum.take(18)}...", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    TextButton(onClick = { activePackId = pack.id; selectedTab = 0 }) { Text(tr("Open", "باز کردن")) }
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                repository.upsertMapPackage(pack.copy(isInstalled = !pack.isInstalled, updatedAtMillis = System.currentTimeMillis()))
                                            }
                                        },
                                    ) {
                                        Text(
                                            if (pack.isInstalled) tr("Uninstall", "حذف بسته") else tr("Install Local", "نصب آفلاین"),
                                            color = if (pack.isInstalled) colors.accentAmber else colors.accentEmerald,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                var mkTitle by remember { mutableStateOf("") }
                var mkDesc by remember { mutableStateOf("") }
                var mkCat by remember { mutableStateOf(MarkerCategory.CUSTOM) }
                var mkLat by remember { mutableStateOf("35.7240") }
                var mkLon by remember { mutableStateOf("51.3390") }
                var rtName by remember { mutableStateOf("") }

                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        ObsidianGlassCard {
                            Text(tr("Add Custom Offline Marker", "افزودن نقطه جدید روی نقشه"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(mkTitle, { mkTitle = it }, tr("Marker Title", "عنوان مکان"))
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(mkDesc, { mkDesc = it }, tr("Notes / Safe Resource Details", "توضیحات"))
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassTextField(mkLat, { mkLat = it }, tr("Latitude", "عرض"), modifier = Modifier.weight(1f))
                                GlassTextField(mkLon, { mkLon = it }, tr("Longitude", "طول"), modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(MarkerCategory.CUSTOM, MarkerCategory.SHELTER, MarkerCategory.WATER_POINT, MarkerCategory.HAZARD).forEach { c ->
                                    FilterChip(selected = mkCat == c, onClick = { mkCat = c }, label = { Text(c.name, style = MaterialTheme.typography.labelSmall) })
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            GlassButton(
                                label = tr("Save Offline Marker", "ذخیره نقطه"),
                                enabled = mkTitle.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        repository.upsertMapMarker(
                                            MapMarker(
                                                id = "mk-${UUID.randomUUID().toString().take(8)}",
                                                packageId = activePackId,
                                                title = mkTitle.trim(),
                                                description = mkDesc.trim(),
                                                category = mkCat,
                                                latitude = mkLat.toDoubleOrNull() ?: 35.724,
                                                longitude = mkLon.toDoubleOrNull() ?: 51.339,
                                                elevationMeters = 260.0,
                                                isEmergencyResource = mkCat != MarkerCategory.CUSTOM,
                                                isCustom = true,
                                                createdAtMillis = System.currentTimeMillis(),
                                            ),
                                        )
                                        mkTitle = ""; mkDesc = ""
                                        selectedTab = 0
                                    }
                                },
                            )
                        }
                    }

                    item {
                        ObsidianGlassCard {
                            Text(tr("Save Evacuation Route from Visible Markers", "ذخیره مسیر تخلیه از نقاط فعال"), style = MaterialTheme.typography.titleMedium, color = colors.accentTeal)
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(rtName, { rtName = it }, tr("Route Name", "نام مسیر"))
                            Spacer(Modifier.height(10.dp))
                            GlassButton(
                                label = tr("Compute & Save Route", "محاسبه و ذخیره مسیر"),
                                accent = colors.accentTeal,
                                enabled = rtName.isNotBlank() && visibleMarkers.size >= 2,
                                onClick = {
                                    scope.launch {
                                        val pts = visibleMarkers.take(4).map { LatLonPoint(it.latitude, it.longitude, it.title) }
                                        val dist = GeoMath.computeRouteDistanceMeters(pts)
                                        repository.upsertSavedRoute(
                                            SavedRoute(
                                                id = "rt-${UUID.randomUUID().toString().take(8)}",
                                                packageId = activePackId,
                                                name = rtName.trim(),
                                                description = "Offline route connecting ${pts.size} markers",
                                                waypoints = pts,
                                                totalDistanceMeters = dist,
                                                estimatedWalkMinutes = GeoMath.estimateWalkingMinutes(dist),
                                                hazardLevel = "LOW",
                                                createdAtMillis = System.currentTimeMillis(),
                                            ),
                                        )
                                        rtName = ""
                                        selectedTab = 0
                                    }
                                },
                            )
                        }
                    }

                    items(routes, key = { it.id }) { r ->
                        ObsidianGlassCard {
                            Text(r.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Text("${GeoMath.formatDistance(r.totalDistanceMeters)} · ~${r.estimatedWalkMinutes} min walk · ${r.waypoints.size} waypoints", style = MaterialTheme.typography.labelSmall, color = colors.accentTeal)
                        }
                    }
                }
            }
        }
    }
}

private fun projectGeo(
    lat: Double,
    lon: Double,
    elev: Double,
    centerLat: Double,
    centerLon: Double,
    is3D: Boolean,
    yawDeg: Float,
    pitchDeg: Float,
    spanDeg: Double,
    w: Float,
    h: Float,
    zoom: Float,
    panX: Float,
    panY: Float,
): Pair<Offset, Offset> {
    val p = Projection3D.project(
        lat = lat,
        lon = lon,
        elevationMeters = if (is3D) elev else 0.0,
        centerLat = centerLat,
        centerLon = centerLon,
        widthPx = w,
        heightPx = h,
        spanDeg = spanDeg,
        zoom = zoom,
        yawDeg = if (is3D) yawDeg else 0f,
        pitchDeg = if (is3D) pitchDeg else 0f,
    )
    return Offset(p.x + panX, p.y + panY) to Offset(p.x + panX, p.extrudedTopY + panY)
}
