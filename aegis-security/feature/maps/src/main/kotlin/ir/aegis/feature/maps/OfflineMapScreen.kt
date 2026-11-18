package ir.aegis.feature.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.aegis.core.maps.*
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

    var selectedTab by remember { mutableIntStateOf(0) } // 0=Map + Smart Advisor, 1=Places, 2=Regions & Saved
    var is3DMode by remember { mutableStateOf(false) }
    var activePackId by remember { mutableStateOf("pack-tehran-grid") }
    var isNationalViewMode by remember { mutableStateOf(false) } // Can zoom out to full Iran or World Atlas!
    var viewMode by remember { mutableStateOf("CITY") } // "CITY", "IRAN", "WORLD"
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

    // Hardware Compass + Magnetometer Bearing
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

    // Standard Hardware GPS / GNSS Locator Telemetry
    var currentLat by remember { mutableDoubleStateOf(35.7010) }
    var currentLon by remember { mutableDoubleStateOf(51.3911) }
    var gpsAccuracyMeters by remember { mutableFloatStateOf(24f) }
    var gpsAltitudeMeters by remember { mutableDoubleStateOf(1190.0) }
    var gpsSpeedKmh by remember { mutableFloatStateOf(0f) }
    var satsUsed by remember { mutableIntStateOf(0) }
    var satsVisible by remember { mutableIntStateOf(0) }
    var hasLiveFix by remember { mutableStateOf(false) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        )
    }

    fun applyGpsLocation(loc: Location) {
        currentLat = loc.latitude
        currentLon = loc.longitude
        gpsAccuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 20f
        if (loc.hasAltitude()) gpsAltitudeMeters = loc.altitude
        if (loc.hasSpeed()) gpsSpeedKmh = loc.speed * 3.6f
        hasLiveFix = true
        val packPool = packages.ifEmpty { OfflineMapSeeds.bundledPackages() }
        val nearestPack = VectorMapEngine.autoSelectNearestPack(loc.latitude, loc.longitude, packPool)?.id
        if (nearestPack != null && nearestPack != activePackId) {
            activePackId = nearestPack
            panLatOffset = 0.0
            panLonOffset = 0.0
        }
    }

    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@DisposableEffect onDispose {}
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val locListener = LocationListener { loc -> applyGpsLocation(loc) }
        val gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var used = 0
                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) used++
                }
                satsVisible = status.satelliteCount
                satsUsed = used
            }
        }
        runCatching {
            val lastGps = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val best = listOfNotNull(lastGps, lastNet).minByOrNull { it.accuracy }
            if (best != null) applyGpsLocation(best)

            if (lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2500L, 2f, locListener, Looper.getMainLooper())
            }
            if (lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4000L, 5f, locListener, Looper.getMainLooper())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                lm?.registerGnssStatusCallback(gnssCallback, android.os.Handler(Looper.getMainLooper()))
            }
        }
        onDispose {
            runCatching { lm?.removeUpdates(locListener) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching { lm?.unregisterGnssStatusCallback(gnssCallback) }
            }
        }
    }

    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPermission = granted
    }

    var pinTitle by remember { mutableStateOf("") }
    var pinCategory by remember { mutableStateOf(MarkerCategory.CUSTOM) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val baseAtlas = remember { VectorMapEngine.loadBaseAtlas() }
    val activePack = packages.firstOrNull { it.id == activePackId } ?: packages.firstOrNull()
    val vectorBundle = remember(activePackId) { VectorMapEngine.loadBundle(activePackId) }

    val (baseLat, baseLon, spanDeg) = when (viewMode) {
        "WORLD" -> Triple(28.0, 38.0, 72.0)
        "IRAN" -> Triple(32.5, 53.6, 13.5)
        else -> Triple(
            ((activePack?.minLat ?: 35.64) + (activePack?.maxLat ?: 35.755)) / 2.0,
            ((activePack?.minLon ?: 51.31) + (activePack?.maxLon ?: 51.46)) / 2.0,
            activePack?.let { maxOf(abs(it.maxLat - it.minLat), abs(it.maxLon - it.minLon)) } ?: 0.14,
        )
    }
    val centerLat = baseLat + panLatOffset
    val centerLon = baseLon + panLonOffset

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

    // Smart Offline Destination Recommendations relative to live user position
    val smartRecommendations = remember(currentLat, currentLon, markers, activePackId) {
        val scoped = markers.filter { it.packageId == activePackId && it.category != MarkerCategory.HAZARD }
            .ifEmpty { markers.filter { it.category != MarkerCategory.HAZARD } }
        MapAdvisorEngine.suggestNearestDestinations(
            userLat = currentLat,
            userLon = currentLon,
            markers = scoped,
            limit = 3,
        )
    }

    fun routeToMarker(mk: MapMarker) {
        viewMode = "CITY"
        if (mk.packageId != activePackId) {
            activePackId = mk.packageId
        }
        val targetBundle = VectorMapEngine.loadBundle(mk.packageId)
        val result = OfflineRoutingEngine.computeSafeRoute(
            bundle = targetBundle,
            startLat = currentLat,
            startLon = currentLon,
            startLabelEn = "Your GPS Anchor",
            startLabelFa = "موقعیت فعلی شما",
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
        val p = packages.firstOrNull { it.id == mk.packageId }
        val pLat = ((p?.minLat ?: 35.64) + (p?.maxLat ?: 35.755)) / 2.0
        val pLon = ((p?.minLon ?: 51.31) + (p?.maxLon ?: 51.46)) / 2.0
        panLatOffset = ((mk.latitude + currentLat) / 2.0) - pLat
        panLonOffset = ((mk.longitude + currentLon) / 2.0) - pLon
    }

    val gpsStatusLabel = if (hasLiveFix) {
        tr(
            "GPS ±${gpsAccuracyMeters.toInt()}m · $satsUsed/$satsVisible sats",
            "GPS فعال ±${gpsAccuracyMeters.toInt()}m · $satsUsed/$satsVisible ماهواره",
        )
    } else {
        tr("Offline GNSS Anchor", "مکان‌یاب آفلاین آماده")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            module = AegisModule.MAPS,
            onBack = onBack,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            extraBadge = gpsStatusLabel,
            showBackButton = showBackButton,
        )

        GlassTabRow(
            tabs = listOf(
                tr("Pro Map & GNSS", "نقشه کامل و مکان‌یاب"),
                tr("Directory (${filteredMarkers.size})", "فهرست مکان‌ها (${filteredMarkers.size})"),
                tr("City Packs (${packages.size})", "بسته‌های نقشه (${packages.size})"),
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
                    // Scale Selector (City / Iran Provinces / World Atlas) + City Selector + Search
                    item {
                        ObsidianGlassCard(contentPadding = PaddingValues(14.dp)) {
                            GlassTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = tr("Search hospital, police, safe zone, street...", "جستجوی مرکز درمانی، امنیت، منطقه امن یا خیابان..."),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            ChipFlowGroup {
                                AegisFilterPill(
                                    label = tr("World Atlas", "نقشه کل جهان"),
                                    selected = viewMode == "WORLD",
                                    onClick = {
                                        viewMode = "WORLD"
                                        panLatOffset = 0.0
                                        panLonOffset = 0.0
                                        zoomScale = 1.0f
                                    },
                                    accent = colors.accentViolet,
                                )
                                AegisFilterPill(
                                    label = tr("All Iran (30 Prov)", "کل ایران (۳۰ استان)"),
                                    selected = viewMode == "IRAN",
                                    onClick = {
                                        viewMode = "IRAN"
                                        panLatOffset = 0.0
                                        panLonOffset = 0.0
                                        zoomScale = 1.0f
                                    },
                                    accent = colors.accentTeal,
                                )
                                packages.forEach { pack ->
                                    AegisFilterPill(
                                        label = pack.regionCode,
                                        selected = viewMode == "CITY" && pack.id == activePackId,
                                        onClick = {
                                            viewMode = "CITY"
                                            activePackId = pack.id
                                            panLatOffset = 0.0
                                            panLonOffset = 0.0
                                            zoomScale = 1.15f
                                            activeRoute = null
                                            selectedMarker = null
                                            if (!hasLiveFix) {
                                                currentLat = (pack.minLat + pack.maxLat) / 2.0
                                                currentLon = (pack.minLon + pack.maxLon) / 2.0
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // FULL MULTI-SCALE INTERACTIVE MAP VIEWPORT (470dp tall!)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(470.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .border(1.2.dp, colors.accentCyan.copy(alpha = 0.42f), RoundedCornerShape(24.dp)),
                        ) {
                            OfflineVectorMapCanvas(
                                atlas = baseAtlas,
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
                                gpsAccuracyMeters = gpsAccuracyMeters,
                                compassAzimuthDeg = compassAzimuth,
                                selectedMarkerId = selectedMarker?.id,
                                rulerStart = rulerStart,
                                rulerEnd = rulerEnd,
                                onTransformGesture = { pan, zoom ->
                                    val newZoom = (zoomScale * zoom).coerceIn(0.45f, 5.5f)
                                    zoomScale = newZoom
                                    val degPerPx = spanDeg / (340.0 * newZoom)
                                    val maxPan = (spanDeg * 0.9).coerceAtLeast(0.15)
                                    panLatOffset = (panLatOffset + pan.y * degPerPx).coerceIn(-maxPan, maxPan)
                                    panLonOffset = (panLonOffset - pan.x * degPerPx).coerceIn(-maxPan, maxPan)
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

                            // Top-start Frosted GPS & Compass HUD
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xCC0A1118),
                                border = BorderStroke(1.dp, colors.accentCyan.copy(alpha = 0.35f)),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
                                    Text(
                                        text = "${if (viewMode == "WORLD") tr("WORLD", "جهان") else if (viewMode == "IRAN") tr("IRAN", "ایران") else activePack?.regionCode ?: "CITY"} · ${GeoMath.formatBearing(compassAzimuth.toDouble())}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.accentCyan,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "${"%.4f".format(currentLat)}, ${"%.4f".format(currentLon)} · ${gpsAltitudeMeters.toInt()}m",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFD5E1EC),
                                    )
                                }
                            }

                            // Top-end Frosted HUD Controls (2D/3D, +, -, Locate GPS)
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                Surface(
                                    onClick = { is3DMode = !is3DMode },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (is3DMode) colors.accentCyan else Color(0xCC0A1118),
                                    border = BorderStroke(1.dp, colors.accentCyan),
                                ) {
                                    Text(
                                        text = if (is3DMode) "3D" else "2D",
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (is3DMode) Color(0xFF041412) else Color.White,
                                    )
                                }
                                Surface(
                                    onClick = { zoomScale = (zoomScale * 1.35f).coerceAtMost(5.5f) },
                                    shape = CircleShape,
                                    color = Color(0xCC0A1118),
                                    border = BorderStroke(1.dp, colors.glassRimEnd),
                                ) {
                                    Text("+", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Surface(
                                    onClick = { zoomScale = (zoomScale / 1.35f).coerceAtLeast(0.45f) },
                                    shape = CircleShape,
                                    color = Color(0xCC0A1118),
                                    border = BorderStroke(1.dp, colors.glassRimEnd),
                                ) {
                                    Text("−", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Surface(
                                    onClick = {
                                        if (!hasLocationPermission) {
                                            locLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                        } else {
                                            viewMode = "CITY"
                                            val packPool = packages.ifEmpty { OfflineMapSeeds.bundledPackages() }
                                            val nearest = VectorMapEngine.autoSelectNearestPack(currentLat, currentLon, packPool)?.id ?: activePackId
                                            activePackId = nearest
                                            val p = packPool.firstOrNull { it.id == nearest }
                                            val pLat = ((p?.minLat ?: currentLat) + (p?.maxLat ?: currentLat)) / 2.0
                                            val pLon = ((p?.minLon ?: currentLon) + (p?.maxLon ?: currentLon)) / 2.0
                                            panLatOffset = currentLat - pLat
                                            panLonOffset = currentLon - pLon
                                            zoomScale = 1.35f
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xCC0A1118),
                                    border = BorderStroke(1.dp, colors.accentCyan),
                                ) {
                                    Text("◎", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = colors.accentCyan, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Bottom-end Map Legend
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xCC0A1118),
                                border = BorderStroke(1.dp, colors.divider),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LegendDot(colors.accentCrimson, tr("Hosp", "درمانی"))
                                    LegendDot(colors.accentEmerald, tr("Safe", "منطقه امن"))
                                    LegendDot(colors.accentCyan, tr("Sec", "امنیتی"))
                                }
                            }
                        }
                    }

                    // SMART SELF-DIRECTED RECOMMENDATIONS CARD (پیشنهاد هوشمند مقصد و مکان‌یابی)
                    item {
                        ObsidianGlassCard(elevated = true, accentBorderColor = colors.accentCyan) {
                            SectionHeader(
                                title = tr("Smart Offline Navigator", "پیشنهاد هوشمند مکان‌یاب آفلاین"),
                                badge = if (hasLiveFix) tr("GPS Live", "GPS دقیق") else tr("Anchor Mode", "حالت آفلاین"),
                            )
                            Text(
                                text = tr(
                                    "Nearest hazard-screened sanctuaries calculated locally from your coordinates (${"%.4f".format(currentLat)}, ${"%.4f".format(currentLon)}):",
                                    "بهترین نقاط امن و مراکز امدادی نزدیک موقعیت فعلی شما با فیلتر مسیر امن:",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            smartRecommendations.forEachIndexed { idx, rec ->
                                val tag = if (lang == AppLanguage.FA) rec.reasonFa else rec.reasonEn
                                Surface(
                                    onClick = { routeToMarker(rec.marker) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = colors.glassElevated.copy(alpha = 0.72f),
                                    border = BorderStroke(1.dp, if (idx == 0) colors.accentCyan.copy(alpha = 0.55f) else colors.divider),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(markerColor(rec.marker.category, colors)),
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(rec.marker.title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                            Text(
                                                "$tag · ${GeoMath.formatDistance(rec.distanceMeters)} · ~${rec.walkMinutes} ${tr("min", "دقیقه")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.accentCyan,
                                            )
                                        }
                                        Text(
                                            text = tr("Route →", "مسیر ←"),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.accentEmerald,
                                        )
                                    }
                                }
                            }
                            if (!hasLocationPermission) {
                                Spacer(modifier = Modifier.height(6.dp))
                                GlassButton(
                                    label = tr("Enable Hardware GPS / GNSS Fix", "فعال‌سازی مکان‌یاب استاندارد GPS/GNSS"),
                                    onClick = { locLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                                    accent = colors.accentCyan,
                                )
                            }
                        }
                    }

                    // Active Selected POI Card
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
                                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(markerColor(mk.category, colors)))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(mk.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                        Text(
                                            "${markerCategoryLabel(mk.category, lang)} · ${GeoMath.formatDistance(dist)} · ${GeoMath.formatBearing(brg)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.accentTeal,
                                        )
                                    }
                                    TextButton(onClick = { selectedMarker = null }) { Text("✕", color = colors.textMuted) }
                                }
                                Text(mk.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                                Text("${GeoMath.formatDms(mk.latitude, mk.longitude)} · ${mk.elevationMeters.toInt()} m", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                Spacer(modifier = Modifier.height(6.dp))
                                ChipFlowGroup {
                                    GlassButton(
                                        label = tr("Route Offline Here", "مسیریابی آفلاین به اینجا"),
                                        onClick = { routeToMarker(mk) },
                                        accent = colors.accentCyan,
                                    )
                                    GlassButton(
                                        label = tr("Set as My Anchor", "تنظیم به‌عنوان مبدأ من"),
                                        onClick = {
                                            currentLat = mk.latitude
                                            currentLon = mk.longitude
                                            activeRoute = null
                                        },
                                        accent = colors.accentEmerald,
                                    )
                                }
                            }
                        }
                    }

                    // Active Turn-by-Turn Route Summary
                    activeRoute?.let { route ->
                        item {
                            ObsidianGlassCard(elevated = true, accentBorderColor = colors.accentCyan) {
                                SectionHeader(
                                    title = tr("Offline Corridor Route", "مسیر ایمن آفلاین") + (activeRouteTitle?.let { " ← $it" } ?: ""),
                                    badge = "${GeoMath.formatDistance(route.totalDistanceMeters)} · ${route.estimatedWalkMinutes} ${tr("min", "دقیقه")}",
                                )
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
                                            label = if (showStepsDialog) tr("Compact", "خلاصه") else tr("All ${route.steps.size} Steps", "تمام ${route.steps.size} مرحله"),
                                            onClick = { showStepsDialog = !showStepsDialog },
                                            accent = colors.accentTeal,
                                        )
                                    }
                                    GlassButton(
                                        label = tr("Clear Route", "پاک کردن مسیر"),
                                        onClick = { activeRoute = null; activeRouteTitle = null },
                                        accent = colors.accentAmber,
                                    )
                                }
                            }
                        }
                    }

                    // Layers & Custom Pin Card
                    item {
                        ObsidianGlassCard {
                            SectionHeader(
                                title = tr("Layers, 3D Orbit & Geodesic Ruler", "لایه‌های نقشه، سه‌بعدی و خط‌کش"),
                                badge = "${gpsSpeedKmh.toInt()} km/h",
                            )
                            if (is3DMode) {
                                Text(tr("Yaw (${yawDeg.toInt()}°) & Pitch (${pitchDeg.toInt()}°)", "چرخش (${yawDeg.toInt()}°) و شیب (${pitchDeg.toInt()}°)"), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                Slider(value = yawDeg, onValueChange = { yawDeg = it }, valueRange = -180f..180f)
                                Slider(value = pitchDeg, onValueChange = { pitchDeg = it }, valueRange = 15f..60f)
                            }
                            ChipFlowGroup {
                                AegisFilterPill(tr("Streets", "خیابان‌ها"), showStreets, { showStreets = !showStreets })
                                AegisFilterPill(tr("3D Blocks", "ساختمان‌ها"), showBuildings, { showBuildings = !showBuildings })
                                AegisFilterPill(tr("Contours", "خطوط تراز"), showContours, { showContours = !showContours })
                                AegisFilterPill(tr("Avoid Risk", "دوری از ریسک"), avoidHazards, { avoidHazards = !avoidHazards }, accent = colors.accentEmerald)
                                AegisFilterPill(tr("Ruler", "خط‌کش"), rulerMode, {
                                    rulerMode = !rulerMode
                                    if (!rulerMode) { rulerStart = null; rulerEnd = null }
                                }, accent = colors.accentAmber)
                            }
                            if (rulerMode && rulerStart != null && rulerEnd != null) {
                                val dist = GeoMath.haversineMeters(rulerStart!!.latitude, rulerStart!!.longitude, rulerEnd!!.latitude, rulerEnd!!.longitude)
                                val brg = GeoMath.bearingDegrees(rulerStart!!.latitude, rulerStart!!.longitude, rulerEnd!!.latitude, rulerEnd!!.longitude)
                                Text(
                                    text = "${rulerStart!!.title} ↔ ${rulerEnd!!.title}: ${GeoMath.formatDistance(dist)} · ${GeoMath.formatBearing(brg)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.accentAmber,
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            GlassTextField(value = pinTitle, onValueChange = { pinTitle = it }, label = tr("Save custom pin at map center", "عنوان نشانگر جدید روی مرکز نقشه"))
                            Spacer(modifier = Modifier.height(6.dp))
                            GlassButton(
                                label = tr("Save Pin Offline", "ذخیره نشانگر در نقشه آفلاین"),
                                onClick = {
                                    if (pinTitle.isNotBlank()) {
                                        scope.launch {
                                            val mk = MapMarker(
                                                id = "mk-${UUID.randomUUID().toString().take(8)}",
                                                packageId = activePackId,
                                                title = pinTitle.trim(),
                                                description = GeoMath.formatDecimalDegrees(centerLat, centerLon),
                                                category = pinCategory,
                                                latitude = centerLat,
                                                longitude = centerLon,
                                                elevationMeters = vectorBundle.baseElevationMeters + 15.0,
                                                isEmergencyResource = true,
                                                isCustom = true,
                                                createdAtMillis = System.currentTimeMillis(),
                                            )
                                            repository.upsertMapMarker(mk)
                                            selectedMarker = mk
                                            pinTitle = ""
                                            statusMsg = trText(lang, "Saved locally.", "نشانگر با موفقیت ذخیره شد.")
                                        }
                                    }
                                },
                                accent = colors.accentEmerald,
                            )
                            statusMsg?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.accentEmerald) }
                        }
                    }
                }
            }

            // Tab 1: Searchable Places Directory
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
                            label = tr("Filter locations across selected city...", "جستجو در نقاط امن، مراکز درمانی و امنیتی..."),
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
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(markerColor(mk.category, colors)))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(mk.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                    Text(
                                        "${markerCategoryLabel(mk.category, lang)} · ${GeoMath.formatDistance(dist)} · ${GeoMath.formatBearing(brg)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.accentCyan,
                                    )
                                }
                            }
                            Text(mk.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            ChipFlowGroup {
                                GlassButton(
                                    label = tr("Route on Map", "مسیریابی روی نقشه"),
                                    onClick = { routeToMarker(mk); selectedTab = 0 },
                                    accent = colors.accentCyan,
                                )
                                if (mk.isCustom) {
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

            // Tab 2: All 8 Bundled Iran & Global Offline Packs
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { SectionHeader(title = tr("Offline Map Packs (${packages.size} Cities)", "بسته‌های نقشه آفلاین (${packages.size} شهر و منطقه)")) }
                    items(packages, key = { it.id }) { pack ->
                        ObsidianGlassCard(elevated = pack.id == activePackId, accentBorderColor = if (pack.id == activePackId) colors.accentCyan else null) {
                            Text(pack.regionName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Text(pack.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            Text(
                                "${pack.regionCode} · ${pack.tileCount} tiles · ${(pack.sizeBytes / 1_000_000.0).let { "%.1f MB".format(it) }}",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.accentTeal,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            ChipFlowGroup {
                                GlassButton(
                                    label = if (viewMode == "CITY" && pack.id == activePackId) tr("Viewing", "در حال نمایش") else tr("Open City Map", "نمایش روی نقشه"),
                                    onClick = {
                                        viewMode = "CITY"
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
                                    label = if (pack.isInstalled) tr("Offline Ready", "نصب شده آفلاین") else tr("Enable Pack", "فعال‌سازی"),
                                    onClick = {
                                        scope.launch {
                                            repository.upsertMapPackage(pack.copy(isInstalled = !pack.isInstalled, updatedAtMillis = System.currentTimeMillis()))
                                        }
                                    },
                                    accent = if (pack.isInstalled) colors.accentEmerald else colors.accentAmber,
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
