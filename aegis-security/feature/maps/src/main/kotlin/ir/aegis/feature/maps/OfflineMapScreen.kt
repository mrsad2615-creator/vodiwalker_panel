package ir.aegis.feature.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import androidx.core.location.LocationManagerCompat
import ir.aegis.core.maps.*
import ir.aegis.core.model.*
import ir.aegis.core.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    val dbPackages by repository.observeMapPackages().collectAsState(initial = emptyList())
    val packages = remember(dbPackages) { dbPackages.ifEmpty { OfflineMapSeeds.bundledPackages() } }
    val dbMarkers by repository.observeMapMarkers().collectAsState(initial = emptyList())
    val markers = remember(dbMarkers) { dbMarkers.ifEmpty { OfflineMapSeeds.bundledMarkers() } }
    val routes by repository.observeSavedRoutes().collectAsState(initial = emptyList())
    val incidents by repository.observeIncidents().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) } // 0=Full Interactive Map, 1=Places Directory, 2=City Packs
    var is3DMode by remember { mutableStateOf(false) }
    var activePackId by remember { mutableStateOf("pack-tehran-grid") }
    var viewMode by remember { mutableStateOf("CITY") } // CITY, IRAN, WORLD
    var yawDeg by remember { mutableFloatStateOf(16f) }
    var pitchDeg by remember { mutableFloatStateOf(42f) }
    var zoomScale by remember { mutableFloatStateOf(1.18f) }
    var centerLat by remember { mutableDoubleStateOf(35.7010) }
    var centerLon by remember { mutableDoubleStateOf(51.3911) }

    var showContours by remember { mutableStateOf(true) }
    var showBuildings by remember { mutableStateOf(true) }
    var showStreets by remember { mutableStateOf(true) }
    var showRoutesLayer by remember { mutableStateOf(true) }
    var avoidHazards by remember { mutableStateOf(true) }
    var showLayersMenu by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedMarker by remember { mutableStateOf<MapMarker?>(null) }
    var activeRoute by remember { mutableStateOf<OfflineRouteResult?>(null) }
    var activeRouteTitle by remember { mutableStateOf<String?>(null) }
    var showAllRouteSteps by remember { mutableStateOf(false) }

    var rulerMode by remember { mutableStateOf(false) }
    var rulerStart by remember { mutableStateOf<MapMarker?>(null) }
    var rulerEnd by remember { mutableStateOf<MapMarker?>(null) }

    // Async map loading off UI Thread (never freezes UI!)
    var indexedAtlas by remember { mutableStateOf(IndexedBaseAtlas()) }
    var vectorBundle by remember { mutableStateOf(VectorMapBundle(packageId = activePackId)) }
    LaunchedEffect(Unit) {
        indexedAtlas = withContext(Dispatchers.IO) { VectorMapEngine.loadIndexedBaseAtlas() }
    }
    LaunchedEffect(activePackId) {
        vectorBundle = withContext(Dispatchers.IO) { VectorMapEngine.loadBundle(activePackId) }
    }

    // Magnetometer Compass Heading
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

    // Real Standard Hardware GPS / GNSS Locator
    var currentLat by remember { mutableDoubleStateOf(35.7010) }
    var currentLon by remember { mutableDoubleStateOf(51.3911) }
    var gpsAccuracyMeters by remember { mutableFloatStateOf(24f) }
    var satsUsed by remember { mutableIntStateOf(0) }
    var satsVisible by remember { mutableIntStateOf(0) }
    var hasLiveFix by remember { mutableStateOf(false) }
    var gpsProviderDisabled by remember { mutableStateOf(false) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        )
    }

    fun applyLocation(loc: Location, recenter: Boolean) {
        currentLat = loc.latitude
        currentLon = loc.longitude
        gpsAccuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 25f
        hasLiveFix = true
        val nearest = VectorMapEngine.autoSelectNearestPack(loc.latitude, loc.longitude, packages)
        if (nearest != null) {
            activePackId = nearest.id
        }
        if (recenter) {
            viewMode = "CITY"
            centerLat = loc.latitude
            centerLon = loc.longitude
            zoomScale = 1.3f
        }
    }

    DisposableEffect(hasLocationPermission) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        gpsProviderDisabled = lm != null && !LocationManagerCompat.isLocationEnabled(lm)
        if (!hasLocationPermission || lm == null) return@DisposableEffect onDispose {}

        val locListener = LocationListener { loc ->
            gpsProviderDisabled = false
            applyLocation(loc, recenter = !hasLiveFix)
        }
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
            val providers = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
                add(LocationManager.GPS_PROVIDER)
                add(LocationManager.NETWORK_PROVIDER)
                add(LocationManager.PASSIVE_PROVIDER)
            }
            val bestLast = providers.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .minByOrNull { it.accuracy }
            if (bestLast != null) applyLocation(bestLast, recenter = true)

            providers.forEach { p ->
                if (runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)) {
                    runCatching { lm.requestLocationUpdates(p, 2500L, 2f, locListener, Looper.getMainLooper()) }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching { lm.registerGnssStatusCallback(gnssCallback, android.os.Handler(Looper.getMainLooper())) }
            }
        }
        onDispose {
            runCatching { lm.removeUpdates(locListener) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching { lm.unregisterGnssStatusCallback(gnssCallback) }
            }
        }
    }

    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasLocationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    val activePack = packages.firstOrNull { it.id == activePackId } ?: packages.firstOrNull()
    val spanDeg = when (viewMode) {
        "WORLD" -> 76.0
        "IRAN" -> 16.5
        else -> activePack?.let { maxOf(abs(it.maxLat - it.minLat), abs(it.maxLon - it.minLon)) } ?: 0.14
    }

    val filteredMarkers = remember(markers, activePackId, searchQuery) {
        markers.filter { mk ->
            (mk.packageId == activePackId || viewMode != "CITY") &&
                (searchQuery.isBlank() || mk.title.contains(searchQuery, ignoreCase = true) || mk.description.contains(searchQuery, ignoreCase = true))
        }
    }

    val openHazards = remember(markers, incidents) {
        markers.filter { it.category == MarkerCategory.HAZARD }.map { GeoCoord(it.latitude, it.longitude) } +
            incidents.filter { it.latitude != null && it.longitude != null && it.status != IncidentStatus.RESOLVED && (it.priority == IncidentPriority.CRITICAL || it.priority == IncidentPriority.HIGH) }
                .map { GeoCoord(it.latitude!!, it.longitude!!) }
    }

    val smartRecommendations = remember(currentLat, currentLon, markers, activePackId) {
        val scoped = markers.filter { it.packageId == activePackId && it.category != MarkerCategory.HAZARD }
            .ifEmpty { markers.filter { it.category != MarkerCategory.HAZARD } }
        MapAdvisorEngine.suggestNearestDestinations(currentLat, currentLon, scoped, limit = 3)
    }

    fun selectCityPack(pack: OfflineMapPackage) {
        viewMode = "CITY"
        activePackId = pack.id
        centerLat = (pack.minLat + pack.maxLat) / 2.0
        centerLon = (pack.minLon + pack.maxLon) / 2.0
        if (!hasLiveFix) {
            currentLat = centerLat
            currentLon = centerLon
        }
        zoomScale = 1.18f
        activeRoute = null
        selectedMarker = null
    }

    fun routeToMarker(mk: MapMarker) {
        viewMode = "CITY"
        if (mk.packageId != activePackId) activePackId = mk.packageId
        scope.launch {
            val targetBundle = withContext(Dispatchers.IO) { VectorMapEngine.loadBundle(mk.packageId) }
            val result = OfflineRoutingEngine.computeSafeRoute(
                bundle = targetBundle,
                startLat = currentLat,
                startLon = currentLon,
                startLabelEn = "Your Location",
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
            centerLat = (mk.latitude + currentLat) / 2.0
            centerLon = (mk.longitude + currentLon) / 2.0
            zoomScale = 1.05f
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            module = AegisModule.MAPS,
            onBack = onBack,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            extraBadge = if (hasLiveFix) "GPS ±${gpsAccuracyMeters.toInt()}m · $satsUsed/$satsVisible" else activePack?.regionCode,
            showBackButton = showBackButton,
        )

        GlassTabRow(
            tabs = listOf(
                tr("Live Map", "نقشه زنده"),
                tr("Directory (${filteredMarkers.size})", "مکان‌ها (${filteredMarkers.size})"),
                tr("Cities (${packages.size})", "شهرهای آفلاین (${packages.size})"),
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )

        Spacer(modifier = Modifier.height(6.dp))

        when (selectedTab) {
            // TAB 0: TRUE FULL-SCREEN INTERACTIVE CANVAS (NO LazyColumn STEALING TOUCHES!)
            0 -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    OfflineVectorMapCanvas(
                        atlas = indexedAtlas,
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
                            val newZoom = (zoomScale * zoom).coerceIn(0.42f, 6.0f)
                            zoomScale = newZoom
                            val degPerPx = spanDeg / (340.0 * newZoom)
                            centerLat = (centerLat + pan.y * degPerPx).coerceIn(-75.0, 75.0)
                            centerLon = (centerLon - pan.x * degPerPx).coerceIn(-179.0, 179.0)
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
                        onLongPressMap = { lat, lon ->
                            currentLat = lat
                            currentLon = lon
                            hasLiveFix = false
                            val nearest = VectorMapEngine.autoSelectNearestPack(lat, lon, packages)
                            if (nearest != null) activePackId = nearest.id
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Floating Frosted Top Scale & City Switcher
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                MapFloatingPill(
                                    label = tr("All Iran", "کل ایران"),
                                    selected = viewMode == "IRAN",
                                    onClick = {
                                        viewMode = "IRAN"
                                        centerLat = 32.5
                                        centerLon = 53.6
                                        zoomScale = 1.0f
                                    },
                                )
                            }
                            item {
                                MapFloatingPill(
                                    label = tr("World", "کل جهان"),
                                    selected = viewMode == "WORLD",
                                    onClick = {
                                        viewMode = "WORLD"
                                        centerLat = 29.0
                                        centerLon = 42.0
                                        zoomScale = 1.0f
                                    },
                                )
                            }
                            items(packages, key = { it.id }) { pack ->
                                MapFloatingPill(
                                    label = pack.regionName.substringBefore(" ("),
                                    selected = viewMode == "CITY" && pack.id == activePackId,
                                    onClick = { selectCityPack(pack) },
                                )
                            }
                        }

                        if (gpsProviderDisabled) {
                            Surface(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = colors.accentAmber.copy(alpha = 0.92f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(Icons.Rounded.GpsOff, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = tr("Phone GPS is off — Tap to enable", "GPS گوشی خاموش است — برای روشن کردن بزنید"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    // Floating Frosted Right Map Controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        MapCircleIconButton(
                            onClick = { is3DMode = !is3DMode },
                            active = is3DMode,
                        ) {
                            Text(
                                text = if (is3DMode) "3D" else "2D",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (is3DMode) Color(0xFF031713) else Color.White,
                            )
                        }
                        MapCircleIconButton(onClick = { zoomScale = (zoomScale * 1.38f).coerceAtMost(6.0f) }) {
                            Icon(Icons.Rounded.Add, contentDescription = "Zoom in", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        MapCircleIconButton(onClick = { zoomScale = (zoomScale / 1.38f).coerceAtLeast(0.42f) }) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Zoom out", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        MapCircleIconButton(
                            onClick = {
                                if (!hasLocationPermission) {
                                    locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                } else {
                                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                                    gpsProviderDisabled = lm != null && !LocationManagerCompat.isLocationEnabled(lm)
                                    val last = lm?.let {
                                        listOfNotNull(
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) runCatching { it.getLastKnownLocation(LocationManager.FUSED_PROVIDER) }.getOrNull() else null,
                                            runCatching { it.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
                                            runCatching { it.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull(),
                                        ).minByOrNull { loc -> loc.accuracy }
                                    }
                                    if (last != null) {
                                        applyLocation(last, recenter = true)
                                    } else {
                                        viewMode = "CITY"
                                        centerLat = currentLat
                                        centerLon = currentLon
                                        zoomScale = 1.3f
                                    }
                                }
                            },
                            active = hasLiveFix,
                        ) {
                            Icon(
                                Icons.Rounded.MyLocation,
                                contentDescription = "Locate",
                                tint = if (hasLiveFix) Color(0xFF031713) else colors.accentCyan,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        MapCircleIconButton(
                            onClick = { showLayersMenu = !showLayersMenu },
                            active = showLayersMenu,
                        ) {
                            Icon(
                                Icons.Rounded.Layers,
                                contentDescription = "Layers",
                                tint = if (showLayersMenu) Color(0xFF031713) else Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // Expandable Compact Layers Popup
                    if (showLayersMenu) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp)
                                .widthIn(max = 240.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xEC0C141C),
                            border = BorderStroke(1.dp, colors.accentCyan.copy(alpha = 0.4f)),
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(tr("Map Layers", "لایه‌های نقشه"), style = MaterialTheme.typography.labelLarge, color = colors.accentCyan)
                                ChipFlowGroup {
                                    AegisFilterPill(tr("Streets", "خیابان"), showStreets, { showStreets = !showStreets })
                                    AegisFilterPill(tr("Buildings", "ساختمان"), showBuildings, { showBuildings = !showBuildings })
                                    AegisFilterPill(tr("Contours", "ارتفاع"), showContours, { showContours = !showContours })
                                    AegisFilterPill(tr("Ruler", "خط‌کش"), rulerMode, {
                                        rulerMode = !rulerMode
                                        if (!rulerMode) { rulerStart = null; rulerEnd = null }
                                    }, accent = colors.accentAmber)
                                }
                            }
                        }
                    }

                    // Floating Frosted Bottom Dock: Route / Selected POI / #1 Smart Advisor
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = if (colors.isDark) Color(0xEB0B1219) else Color(0xF5FFFFFF),
                        border = BorderStroke(1.dp, colors.accentCyan.copy(alpha = 0.36f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            when {
                                rulerMode && rulerStart != null && rulerEnd != null -> {
                                    val d = GeoMath.haversineMeters(rulerStart!!.latitude, rulerStart!!.longitude, rulerEnd!!.latitude, rulerEnd!!.longitude)
                                    val b = GeoMath.bearingDegrees(rulerStart!!.latitude, rulerStart!!.longitude, rulerEnd!!.latitude, rulerEnd!!.longitude)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Rounded.Straighten, contentDescription = null, tint = colors.accentAmber)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${rulerStart!!.title} ↔ ${rulerEnd!!.title}", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary, maxLines = 1)
                                            Text("${GeoMath.formatDistance(d)} · ${GeoMath.formatBearing(b)}", style = MaterialTheme.typography.titleMedium, color = colors.accentAmber, fontWeight = FontWeight.Bold)
                                        }
                                        IconButton(onClick = { rulerStart = null; rulerEnd = null; rulerMode = false }) {
                                            Icon(Icons.Rounded.Close, contentDescription = null, tint = colors.textMuted)
                                        }
                                    }
                                }

                                activeRoute != null -> {
                                    val route = activeRoute!!
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Rounded.Navigation, contentDescription = null, tint = colors.accentCyan)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = activeRouteTitle ?: tr("Offline Route", "مسیر آفلاین"),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "${GeoMath.formatDistance(route.totalDistanceMeters)} · ${route.estimatedWalkMinutes} ${tr("min", "دقیقه")}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = colors.accentCyan,
                                            )
                                        }
                                        TextButton(onClick = { showAllRouteSteps = !showAllRouteSteps }) {
                                            Text(if (showAllRouteSteps) tr("Hide", "بستن") else tr("Steps", "مراحل"))
                                        }
                                        IconButton(onClick = { activeRoute = null; activeRouteTitle = null }) {
                                            Icon(Icons.Rounded.Close, contentDescription = null, tint = colors.textMuted)
                                        }
                                    }
                                    if (showAllRouteSteps) {
                                        route.steps.take(5).forEachIndexed { idx, s ->
                                            Text(
                                                text = "${idx + 1}. ${if (lang == AppLanguage.FA) s.instructionFa else s.instructionEn}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.textSecondary,
                                            )
                                        }
                                    }
                                }

                                selectedMarker != null -> {
                                    val mk = selectedMarker!!
                                    val d = GeoMath.haversineMeters(currentLat, currentLon, mk.latitude, mk.longitude)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(markerColor(mk.category, colors)))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(mk.title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${markerCategoryLabel(mk.category, lang)} · ${GeoMath.formatDistance(d)}", style = MaterialTheme.typography.labelMedium, color = colors.accentCyan)
                                        }
                                        GlassButton(
                                            label = tr("Route", "مسیریابی"),
                                            onClick = { routeToMarker(mk) },
                                            accent = colors.accentCyan,
                                        )
                                        IconButton(onClick = { selectedMarker = null }) {
                                            Icon(Icons.Rounded.Close, contentDescription = null, tint = colors.textMuted)
                                        }
                                    }
                                }

                                else -> {
                                    val topRec = smartRecommendations.firstOrNull()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (hasLiveFix) Icons.Rounded.GpsFixed else Icons.Rounded.NearMe,
                                            contentDescription = null,
                                            tint = colors.accentCyan,
                                            modifier = Modifier.size(24.dp),
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = topRec?.let { "${tr("Nearest:", "نزدیک‌ترین:")} ${it.marker.title}" }
                                                    ?: tr("Long-press anywhere to pin your location", "نگه‌داشتن روی نقشه برای تعیین مبدأ"),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = topRec?.let {
                                                    "${GeoMath.formatDistance(it.distanceMeters)} · ${it.walkMinutes} ${tr("min", "دقیقه")} · ${GeoMath.formatBearing(compassAzimuth.toDouble())}"
                                                } ?: "${"%.4f".format(currentLat)}, ${"%.4f".format(currentLon)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.accentCyan,
                                            )
                                        }
                                        if (topRec != null) {
                                            GlassButton(
                                                label = tr("Go", "مسیر"),
                                                onClick = { routeToMarker(topRec.marker) },
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

            // TAB 1: Places Directory & Smart Nearest Destinations
            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("map_tab_lazy_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        GlassTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = tr("Search places & safe zones...", "جستجوی مراکز درمانی، امنیتی و پناهگاه..."),
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
                            Text(mk.description, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            ChipFlowGroup {
                                GlassButton(
                                    label = tr("Route on Map", "مسیریابی روی نقشه"),
                                    onClick = { routeToMarker(mk); selectedTab = 0 },
                                    accent = colors.accentCyan,
                                )
                            }
                        }
                    }
                }
            }

            // TAB 2: Bundled Offline Cities
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(packages, key = { it.id }) { pack ->
                        ObsidianGlassCard(elevated = pack.id == activePackId, accentBorderColor = if (pack.id == activePackId) colors.accentCyan else null) {
                            SectionHeader(title = pack.regionName, badge = pack.regionCode)
                            Text(pack.description, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            Text("${pack.tileCount} tiles · ${(pack.sizeBytes / 1_000_000.0).let { "%.1f MB".format(it) }}", style = MaterialTheme.typography.labelSmall, color = colors.accentTeal)
                            Spacer(Modifier.height(4.dp))
                            GlassButton(
                                label = if (pack.id == activePackId && viewMode == "CITY") tr("Active on Map", "فعال روی نقشه") else tr("Open City", "نمایش شهر"),
                                onClick = { selectCityPack(pack); selectedTab = 0 },
                                accent = colors.accentCyan,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapFloatingPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalObsidianColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) colors.accentCyan else Color(0xDC0B121A),
        border = BorderStroke(1.dp, if (selected) colors.accentCyan else Color(0x38FFFFFF)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color(0xFF031713) else Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun MapCircleIconButton(
    onClick: () -> Unit,
    active: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalObsidianColors.current
    Surface(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = if (active) colors.accentCyan else Color(0xDC0B121A),
        border = BorderStroke(1.dp, if (active) colors.accentCyan else Color(0x38FFFFFF)),
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}
