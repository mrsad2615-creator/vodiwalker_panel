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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.aegis.core.maps.GeoCoord
import ir.aegis.core.maps.GeoMath
import ir.aegis.core.maps.OfflineRouteResult
import ir.aegis.core.maps.OfflineRoutingEngine
import ir.aegis.core.maps.VectorMapEngine
import ir.aegis.core.model.*
import ir.aegis.core.ui.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

@SuppressLint("MissingPermission")
@Composable
fun OfflineMapScreen(
    repository: AegisRepository,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
) {
    val colors = LocalObsidianColors.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val packages by repository.observeMapPackages().collectAsState(initial = emptyList())
    val markers by repository.observeMapMarkers().collectAsState(initial = emptyList())
    val routes by repository.observeSavedRoutes().collectAsState(initial = emptyList())
    val incidents by repository.observeIncidents().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) } // 0=Vector Map, 1=Directory & Search, 2=Regions & Routes
    var is3DMode by remember { mutableStateOf(false) } // Default crisp 2D Topo so labels/streets are immediately crystal clear; 1 tap to 3D!
    var activePackId by remember { mutableStateOf("pack-tehran-grid") }
    var yawDeg by remember { mutableFloatStateOf(16f) }
    var pitchDeg by remember { mutableFloatStateOf(44f) }
    var zoomScale by remember { mutableFloatStateOf(1.15f) }
    var panLatOffset by remember { mutableDoubleStateOf(0.0) }
    var panLonOffset by remember { mutableDoubleStateOf(0.0) }

    var showContours by remember { mutableStateOf(true) }
    var showBuildings by remember { mutableStateOf(true) }
    var showStreets by remember { mutableStateOf(true) }
    var showEmergencyLayer by remember { mutableStateOf(true) }
    var showCustomLayer by remember { mutableStateOf(true) }
    var showRoutesLayer by remember { mutableStateOf(true) }
    var avoidHazards by remember { mutableStateOf(true) }

    var searchQuery by remember { mutableStateOf("") }
    var showCategoryFilters by remember { mutableStateOf(false) }
    var categoryFilter by remember { mutableStateOf<MarkerCategory?>(null) }
    var selectedMarker by remember { mutableStateOf<MapMarker?>(null) }
    var activeRoute by remember { mutableStateOf<OfflineRouteResult?>(null) }
    var activeRouteTitle by remember { mutableStateOf<String?>(null) }
    var showStepsDialog by remember { mutableStateOf(false) }

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
                    compassAzimuth = ((Math.toDegrees(orient[0].toDouble()).toFloat()) + 360f) % 360f
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

    var currentLat by remember { mutableDoubleStateOf(35.7010) }
    var currentLon by remember { mutableDoubleStateOf(51.3911) }
    var gpsStatus by remember { mutableStateOf("Offline Vector Anchor") }

    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val last = runCatching { lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
            if (last != null) {
                currentLat = last.latitude
                currentLon = last.longitude
                gpsStatus = "GPS Fix (±${last.accuracy.toInt()}m)"
            } else {
                runCatching {
                    lm?.requestSingleUpdate(LocationManager.GPS_PROVIDER, object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            currentLat = location.latitude
                            currentLon = location.longitude
                            gpsStatus = "GPS Fix (±${location.accuracy.toInt()}m)"
                        }
                    }, null)
                }
            }
        }
    }

    var pinTitle by remember { mutableStateOf("") }
    var pinCategory by remember { mutableStateOf(MarkerCategory.CUSTOM) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val activePack = packages.firstOrNull { it.id == activePackId } ?: packages.firstOrNull()
    val vectorBundle = remember(activePackId) { VectorMapEngine.loadBundle(activePackId) }
    val baseLat = ((activePack?.minLat ?: 35.64) + (activePack?.maxLat ?: 35.755)) / 2.0
    val baseLon = ((activePack?.minLon ?: 51.31) + (activePack?.maxLon ?: 51.46)) / 2.0
    val centerLat = baseLat + panLatOffset
    val centerLon = baseLon + panLonOffset
    val spanDeg = activePack?.let { maxOf(abs(it.maxLat - it.minLat), abs(it.maxLon - it.minLon)) } ?: 0.14

    val filteredMarkers = remember(markers, activePackId, showEmergencyLayer, showCustomLayer, categoryFilter, searchQuery) {
        markers.filter { mk ->
            (mk.packageId == activePackId || activePack == null) &&
                ((mk.isEmergencyResource && showEmergencyLayer) || (!mk.isEmergencyResource && showCustomLayer)) &&
                (categoryFilter == null || mk.category == categoryFilter) &&
                (searchQuery.isBlank() || mk.title.contains(searchQuery, ignoreCase = true) || mk.description.contains(searchQuery, ignoreCase = true))
        }
    }

    val openHazards = remember(markers, incidents) {
        markers.filter { it.category == MarkerCategory.HAZARD }.map { GeoCoord(it.latitude, it.longitude) } +
            incidents.filter { it.latitude != null && it.longitude != null && it.status != IncidentStatus.RESOLVED && (it.priority == IncidentPriority.CRITICAL || it.priority == IncidentPriority.HIGH) }
                .map { GeoCoord(it.latitude!!, it.longitude!!) }
    }

    fun routeToMarker(mk: MapMarker) {
        val result = OfflineRoutingEngine.computeSafeRoute(
            bundle = vectorBundle,
            startLat = currentLat,
            startLon = currentLon,
            startLabelEn = "Your Position",
            startLabelFa = "موقعیت شما",
            destLat = mk.latitude,
            destLon = mk.longitude,
            destLabelEn = mk.title,
            destLabelFa = mk.title,
            hazards = openHazards,
            avoidHazards = avoidHazards,
        )
        activeRoute = result
        activeRouteTitle = mk.title
        selectedMarker = mk
        panLatOffset = ((mk.latitude + currentLat) / 2.0) - baseLat
        panLonOffset = ((mk.longitude + currentLon) / 2.0) - baseLon
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            module = AegisModule.MAPS,
            onBack = onBack,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            extraBadge = activePack?.regionCode ?: "OFFLINE GIS",
            showBackButton = showBackButton,
        )

        GlassTabRow(
            tabs = listOf(
                tr("Interactive Map", "نقشه تعاملی"),
                tr("Places (${filteredMarkers.size})", "مکان‌ها (${filteredMarkers.size})"),
                tr("Regions & Saved", "بسته‌ها و مسیرها"),
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedTab) {
            0 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("map_tab_lazy_list"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Compact Search + Pack Switcher + Expandable Category Filters
                    item {
                        ObsidianGlassCard(contentPadding = PaddingValues(12.dp)) {
                            GlassTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = tr("Search hospitals, shelters, streets...", "جستجوی بیمارستان، پناهگاه، خیابان..."),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            ChipFlowGroup {
                                packages.forEach { pack ->
                                    AegisFilterPill(
                                        label = pack.regionCode,
                                        selected = pack.id == activePackId,
                                        onClick = {
                                            activePackId = pack.id
                                            panLatOffset = 0.0
                                            panLonOffset = 0.0
                                            zoomScale = 1.15f
                                            activeRoute = null
                                            selectedMarker = null
                                            if (!gpsStatus.startsWith("GPS Fix")) {
                                                currentLat = (pack.minLat + pack.maxLat) / 2.0
                                                currentLon = (pack.minLon + pack.maxLon) / 2.0
                                            }
                                        },
                                    )
                                }
                                AegisFilterPill(
                                    label = if (categoryFilter == null) tr("Filter POIs", "فیلتر نقاط") else markerCategoryLabel(categoryFilter!!, lang),
                                    selected = showCategoryFilters || categoryFilter != null,
                                    onClick = { showCategoryFilters = !showCategoryFilters },
                                    accent = colors.accentTeal,
                                )
                            }
                            if (showCategoryFilters) {
                                Spacer(modifier = Modifier.height(6.dp))
                                ChipFlowGroup {
                                    AegisFilterPill(
                                        label = tr("All", "همه"),
                                        selected = categoryFilter == null,
                                        onClick = { categoryFilter = null },
                                    )
                                    listOf(
                                        MarkerCategory.HOSPITAL,
                                        MarkerCategory.SHELTER,
                                        MarkerCategory.ASSEMBLY_POINT,
                                        MarkerCategory.WATER_POINT,
                                        MarkerCategory.FIRE_STATION,
                                    ).forEach { cat ->
                                        AegisFilterPill(
                                            label = markerCategoryLabel(cat, lang),
                                            selected = categoryFilter == cat,
                                            onClick = { categoryFilter = if (categoryFilter == cat) null else cat },
                                            accent = markerColor(cat, colors),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // FULL HIGH-CONTRAST INTERACTIVE CARTOGRAPHIC CANVAS
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(430.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .border(1.5.dp, colors.accentCyan.copy(alpha = 0.45f), RoundedCornerShape(20.dp)),
                        ) {
                            OfflineVectorMapCanvas(
                                bundle = vectorBundle,
                                centerLat = centerLat,
                                centerLon = centerLon,
                                spanDeg = spanDeg,
                                zoomScale = zoomScale,
                                yawDeg = yawDeg,
                                pitchDeg = pitchDeg,
                                is3DMode = is3DMode,
                                showContours = showContours,
                                showBuildings = showBuildings,
                                showStreets = showStreets,
                                showRoutesLayer = showRoutesLayer,
                                markers = filteredMarkers,
                                routes = routes.filter { it.packageId == activePackId },
                                activeRoute = activeRoute,
                                incidents = incidents,
                                userLat = currentLat,
                                userLon = currentLon,
                                selectedMarkerId = selectedMarker?.id,
                                rulerStart = rulerStart,
                                rulerEnd = rulerEnd,
                                onTransformGesture = { pan, zoom ->
                                    val newZoom = (zoomScale * zoom).coerceIn(0.65f, 4.5f)
                                    zoomScale = newZoom
                                    val degPerPx = spanDeg / (340.0 * newZoom)
                                    panLatOffset = (panLatOffset + pan.y * degPerPx).coerceIn(-0.08, 0.08)
                                    panLonOffset = (panLonOffset - pan.x * degPerPx).coerceIn(-0.08, 0.08)
                                },
                                onSelectMarker = { mk ->
                                    if (rulerMode) {
                                        if (rulerStart == null || (rulerStart != null && rulerEnd != null)) {
                                            rulerStart = mk
                                            rulerEnd = null
                                        } else {
                                            rulerEnd = mk
                                        }
                                    } else {
                                        selectedMarker = mk
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )

                            // Top-left live Compass & Region badge
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xCC0A1220),
                                border = BorderStroke(1.dp, colors.accentCyan.copy(alpha = 0.4f)),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text(
                                        text = "${activePack?.regionCode ?: "MAP"} · ${GeoMath.formatBearing(compassAzimuth.toDouble())}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "${vectorBundle.roads.size} ${tr("streets", "خیابان")} · ${filteredMarkers.size} POI",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFCBD5E1),
                                    )
                                }
                            }

                            // Top-right HUD Controls (2D/3D, +, -, Center)
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                Surface(
                                    onClick = { is3DMode = !is3DMode },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (is3DMode) colors.accentCyan else Color(0xCC0A1220),
                                    border = BorderStroke(1.dp, colors.accentCyan),
                                ) {
                                    Text(
                                        text = if (is3DMode) "3D" else "2D",
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (is3DMode) Color(0xFF04121E) else Color.White,
                                    )
                                }
                                Surface(
                                    onClick = { zoomScale = (zoomScale * 1.32f).coerceAtMost(4.5f) },
                                    shape = CircleShape,
                                    color = Color(0xCC0A1220),
                                    border = BorderStroke(1.dp, colors.glassRimEnd),
                                ) {
                                    Text("+", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Surface(
                                    onClick = { zoomScale = (zoomScale / 1.32f).coerceAtLeast(0.65f) },
                                    shape = CircleShape,
                                    color = Color(0xCC0A1220),
                                    border = BorderStroke(1.dp, colors.glassRimEnd),
                                ) {
                                    Text("−", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Surface(
                                    onClick = {
                                        panLatOffset = 0.0
                                        panLonOffset = 0.0
                                        zoomScale = 1.15f
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                                            val last = runCatching { lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
                                            if (last != null) {
                                                currentLat = last.latitude
                                                currentLon = last.longitude
                                                gpsStatus = "GPS Fix (±${last.accuracy.toInt()}m)"
                                            }
                                        } else {
                                            locLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xCC0A1220),
                                    border = BorderStroke(1.dp, colors.accentTeal),
                                ) {
                                    Text("◎", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = colors.accentTeal, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Bottom-right quick Legend Pill
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xCC0A1220),
                                border = BorderStroke(1.dp, colors.divider),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LegendDot(colors.accentCrimson, tr("Hosp", "بیمارستان"))
                                    LegendDot(colors.accentEmerald, tr("Safe", "پناهگاه"))
                                    LegendDot(colors.accentTeal, tr("Water", "آب"))
                                }
                            }
                        }
                    }

                    // Active Selected POI Callout Card + 1-Tap Offline Routing
                    selectedMarker?.let { mk ->
                        item {
                            val dist = GeoMath.haversineMeters(currentLat, currentLon, mk.latitude, mk.longitude)
                            val brg = GeoMath.bearingDegrees(currentLat, currentLon, mk.latitude, mk.longitude)
                            ObsidianGlassCard(elevated = true, accentBorderColor = markerColor(mk.category, colors)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(markerColor(mk.category, colors)),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(mk.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                        Text(
                                            "${markerCategoryLabel(mk.category, lang)} · ${GeoMath.formatDistance(dist)} · ${GeoMath.formatBearing(brg)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.accentTeal,
                                        )
                                    }
                                    TextButton(onClick = { selectedMarker = null }) {
                                        Text("✕", color = colors.textMuted)
                                    }
                                }
                                Text(mk.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                                Text(
                                    "${GeoMath.formatDms(mk.latitude, mk.longitude)} · ${mk.elevationMeters.toInt()} m",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textMuted,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                ChipFlowGroup {
                                    GlassButton(
                                        label = tr("Route Offline Here", "مسیریابی آفلاین به اینجا"),
                                        onClick = { routeToMarker(mk) },
                                        accent = colors.accentCyan,
                                    )
                                    GlassButton(
                                        label = tr("Set as Anchor", "تنظیم به‌عنوان مبدأ"),
                                        onClick = {
                                            currentLat = mk.latitude
                                            currentLon = mk.longitude
                                            gpsStatus = mk.title
                                            activeRoute = null
                                        },
                                        accent = colors.accentEmerald,
                                    )
                                }
                            }
                        }
                    }

                    // Active Route Summary & Turn-by-Turn Steps
                    activeRoute?.let { route ->
                        item {
                            ObsidianGlassCard(elevated = true, accentBorderColor = colors.accentCyan) {
                                SectionHeader(
                                    title = tr("Safe Corridor Route", "مسیر ایمن آفلاین") + (activeRouteTitle?.let { " → $it" } ?: ""),
                                    badge = "${GeoMath.formatDistance(route.totalDistanceMeters)} · ${route.estimatedWalkMinutes} ${tr("min", "دقیقه")}",
                                )
                                if (route.avoidedHazardCount > 0) {
                                    Text(
                                        text = tr(
                                            "Detoured around ${route.avoidedHazardCount} active hazard/incident node(s).",
                                            "مسیر از کنار ${route.avoidedHazardCount} نقطه پرخطر دور شد.",
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.accentEmerald,
                                    )
                                }
                                route.steps.take(if (showStepsDialog) 20 else 3).forEachIndexed { idx, step ->
                                    Text(
                                        text = "${idx + 1}. ${if (lang == AppLanguage.FA) step.instructionFa else step.instructionEn}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textPrimary,
                                    )
                                }
                                ChipFlowGroup {
                                    if (route.steps.size > 3) {
                                        GlassButton(
                                            label = if (showStepsDialog) tr("Compact Steps", "خلاصه مراحل") else tr("All ${route.steps.size} Steps", "تمام ${route.steps.size} مرحله"),
                                            onClick = { showStepsDialog = !showStepsDialog },
                                            accent = colors.accentTeal,
                                        )
                                    }
                                    GlassButton(
                                        label = tr("Clear Route", "پاک کردن مسیر"),
                                        onClick = {
                                            activeRoute = null
                                            activeRouteTitle = null
                                        },
                                        accent = colors.accentAmber,
                                    )
                                }
                            }
                        }
                    }

                    // Map Tool & Layer Controls Card (Zero Overlap FlowRow!)
                    item {
                        ObsidianGlassCard {
                            SectionHeader(
                                title = tr("Layers, 3D Orbit & Ruler", "لایه‌ها، نمای سه‌بعدی و خط‌کش"),
                                badge = gpsStatus,
                            )
                            if (is3DMode) {
                                Text(
                                    tr("Camera Yaw (${yawDeg.toInt()}°) & Pitch (${pitchDeg.toInt()}°)", "زاویه افق (${yawDeg.toInt()}°) و شیب (${pitchDeg.toInt()}°)"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                )
                                Slider(value = yawDeg, onValueChange = { yawDeg = it }, valueRange = -180f..180f)
                                Slider(value = pitchDeg, onValueChange = { pitchDeg = it }, valueRange = 15f..60f)
                            }
                            ChipFlowGroup {
                                AegisFilterPill(tr("Streets", "خیابان‌ها"), showStreets, { showStreets = !showStreets })
                                AegisFilterPill(tr("3D Blocks", "ساختمان‌ها"), showBuildings, { showBuildings = !showBuildings })
                                AegisFilterPill(tr("Contours", "تراز ارتفاع"), showContours, { showContours = !showContours })
                                AegisFilterPill(tr("Avoid Hazards", "دوری از خطر"), avoidHazards, { avoidHazards = !avoidHazards }, accent = colors.accentEmerald)
                                AegisFilterPill(tr("Ruler Mode", "حالت خط‌کش"), rulerMode, {
                                    rulerMode = !rulerMode
                                    if (!rulerMode) {
                                        rulerStart = null
                                        rulerEnd = null
                                    }
                                }, accent = colors.accentAmber)
                            }
                            if (rulerMode) {
                                Spacer(modifier = Modifier.height(6.dp))
                                if (rulerStart != null && rulerEnd != null) {
                                    val dist = GeoMath.haversineMeters(rulerStart!!.latitude, rulerStart!!.longitude, rulerEnd!!.latitude, rulerEnd!!.longitude)
                                    val brg = GeoMath.bearingDegrees(rulerStart!!.latitude, rulerStart!!.longitude, rulerEnd!!.latitude, rulerEnd!!.longitude)
                                    Text(
                                        text = "${rulerStart!!.title} ↔ ${rulerEnd!!.title}: ${GeoMath.formatDistance(dist)} · ${GeoMath.formatBearing(brg)} (~${GeoMath.estimateWalkingMinutes(dist)} ${tr("min walk", "دقیقه پیاده")})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.accentAmber,
                                    )
                                } else {
                                    Text(
                                        text = tr("Tap two markers on the map to measure geodesic distance & bearing.", "دو نقطه روی نقشه لمس کنید تا فاصله و جهت دقیق محاسبه شود."),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.accentAmber,
                                    )
                                }
                            }
                        }
                    }

                    // Quick Custom Pin Adder
                    item {
                        ObsidianGlassCard {
                            SectionHeader(title = tr("Drop Offline Marker Pin", "ثبت نشانگر جدید روی نقشه"))
                            GlassTextField(
                                value = pinTitle,
                                onValueChange = { pinTitle = it },
                                label = tr("Marker name (saved locally)", "عنوان نقطه امن یا هشدار"),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ChipFlowGroup {
                                listOf(MarkerCategory.CUSTOM, MarkerCategory.SHELTER, MarkerCategory.WATER_POINT, MarkerCategory.HAZARD).forEach { cat ->
                                    AegisFilterPill(
                                        label = markerCategoryLabel(cat, lang),
                                        selected = pinCategory == cat,
                                        onClick = { pinCategory = cat },
                                        accent = markerColor(cat, colors),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            GlassButton(
                                label = tr("Save Pin at Map Center", "ذخیره نشانگر در مرکز نقشه"),
                                onClick = {
                                    if (pinTitle.isNotBlank()) {
                                        scope.launch {
                                            val mk = MapMarker(
                                                id = "mk-${UUID.randomUUID().toString().take(8)}",
                                                packageId = activePackId,
                                                title = pinTitle.trim(),
                                                description = "Custom offline marker (${GeoMath.formatDecimalDegrees(centerLat, centerLon)})",
                                                category = pinCategory,
                                                latitude = centerLat,
                                                longitude = centerLon,
                                                elevationMeters = vectorBundle.baseElevationMeters + 15.0,
                                                isEmergencyResource = pinCategory != MarkerCategory.CUSTOM,
                                                isUserCreated = true,
                                                createdAtMillis = System.currentTimeMillis(),
                                            )
                                            repository.saveMapMarker(mk)
                                            selectedMarker = mk
                                            pinTitle = ""
                                            statusMsg = tr("Pin saved to offline Room map storage.", "نشانگر با موفقیت در نقشه آفلاین ذخیره شد.")
                                        }
                                    }
                                },
                                accent = colors.accentEmerald,
                            )
                            statusMsg?.let { msg ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(msg, style = MaterialTheme.typography.labelMedium, color = colors.accentEmerald)
                            }
                        }
                    }
                }
            }

            // Tab 1: Searchable Places & Distances Directory
            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        GlassTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = tr("Filter shelters, hospitals, water points...", "فیلتر پناهگاه‌ها، بیمارستان‌ها و منابع آب..."),
                        )
                    }
                    items(filteredMarkers, key = { it.id }) { mk ->
                        val dist = GeoMath.haversineMeters(currentLat, currentLon, mk.latitude, mk.longitude)
                        val brg = GeoMath.bearingDegrees(currentLat, currentLon, mk.latitude, mk.longitude)
                        ObsidianGlassCard(accentBorderColor = markerColor(mk.category, colors)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(markerColor(mk.category, colors)),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(mk.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                    Text(
                                        "${markerCategoryLabel(mk.category, lang)} · ${GeoMath.formatDistance(dist)} · ${GeoMath.formatBearing(brg)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.accentTeal,
                                    )
                                }
                            }
                            Text(mk.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            ChipFlowGroup {
                                GlassButton(
                                    label = tr("Show & Route on Map", "نمایش و مسیریابی روی نقشه"),
                                    onClick = {
                                        routeToMarker(mk)
                                        selectedTab = 0
                                    },
                                    accent = colors.accentCyan,
                                )
                                if (mk.isUserCreated) {
                                    GlassButton(
                                        label = tr("Delete", "حذف"),
                                        onClick = { scope.launch { repository.deleteMapMarker(mk.id) } },
                                        accent = colors.accentCrimson,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tab 2: Bundled Offline Vector Regions & Saved Corridor Routes
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        SectionHeader(title = tr("Offline Vector Map Packs", "بسته‌های نقشه وکتور آفلاین"))
                    }
                    items(packages, key = { it.id }) { pack ->
                        ObsidianGlassCard(elevated = pack.id == activePackId, accentBorderColor = if (pack.id == activePackId) colors.accentCyan else null) {
                            Text(pack.regionName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Text(pack.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            Text(
                                "${pack.regionCode} · ${pack.tileCount} tiles · ${(pack.sizeBytes / 1_000_000.0).let { "%.1f MB".format(it) }}",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.accentTeal,
                            )
                            Text(
                                "SHA-256: ${pack.sha256Checksum.take(20)}…",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            ChipFlowGroup {
                                GlassButton(
                                    label = if (pack.id == activePackId) tr("Viewing on Map", "در حال نمایش") else tr("Open on Map", "باز کردن روی نقشه"),
                                    onClick = {
                                        activePackId = pack.id
                                        panLatOffset = 0.0
                                        panLonOffset = 0.0
                                        currentLat = (pack.minLat + pack.maxLat) / 2.0
                                        currentLon = (pack.minLon + pack.maxLon) / 2.0
                                        activeRoute = null
                                        selectedTab = 0
                                    },
                                    accent = colors.accentCyan,
                                )
                                GlassButton(
                                    label = if (pack.isInstalled) tr("Installed Offline", "نصب شده آفلاین") else tr("Enable Pack", "فعال‌سازی بسته"),
                                    onClick = { scope.launch { repository.toggleMapPackageInstalled(pack.id, !pack.isInstalled) } },
                                    accent = if (pack.isInstalled) colors.accentEmerald else colors.accentAmber,
                                )
                            }
                        }
                    }

                    item {
                        SectionHeader(title = tr("Saved Evacuation Corridors", "مسیرهای تخلیه ذخیره‌شده"))
                    }
                    items(routes, key = { it.id }) { route ->
                        ObsidianGlassCard {
                            Text(route.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Text(route.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            Text(
                                "${GeoMath.formatDistance(route.totalDistanceMeters)} · ~${route.estimatedWalkMinutes} ${tr("min", "دقیقه")} · ${route.waypoints.size} ${tr("waypoints", "نقطه")}",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.accentTeal,
                            )
                            ChipFlowGroup {
                                GlassButton(
                                    label = tr("Focus Corridor on Map", "نمایش مسیر روی نقشه"),
                                    onClick = {
                                        activePackId = route.packageId
                                        panLatOffset = 0.0
                                        panLonOffset = 0.0
                                        selectedTab = 0
                                    },
                                    accent = colors.accentCyan,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
