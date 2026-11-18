package ir.aegis.feature.utilities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.aegis.core.maps.GeoMath
import ir.aegis.core.model.*
import ir.aegis.core.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

@SuppressLint("MissingPermission")
@Composable
fun SensorsAndFieldTab(repository: AegisRepository) {
    val colors = LocalObsidianColors.current
    val context = LocalContext.current

    var torchOn by remember { mutableStateOf(false) }
    var strobeOn by remember { mutableStateOf(false) }
    var screenTorch by remember { mutableStateOf(false) }

    val camMgr = remember { context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager }
    val torchCamId = remember(camMgr) {
        runCatching {
            camMgr?.cameraIdList?.firstOrNull { id ->
                camMgr.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    LaunchedEffect(torchOn, strobeOn) {
        if (torchCamId == null) return@LaunchedEffect
        if (!torchOn) {
            runCatching { camMgr?.setTorchMode(torchCamId, false) }
            return@LaunchedEffect
        }
        if (!strobeOn) {
            runCatching { camMgr?.setTorchMode(torchCamId, true) }
        } else {
            while (torchOn && strobeOn) {
                runCatching { camMgr?.setTorchMode(torchCamId, true) }
                delay(180)
                runCatching { camMgr?.setTorchMode(torchCamId, false) }
                delay(180)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (torchCamId != null) runCatching { camMgr?.setTorchMode(torchCamId, false) } }
    }

    var azimuth by remember { mutableFloatStateOf(0f) }
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
                    azimuth = ((Math.toDegrees(orient[0].toDouble()) + 360.0) % 360.0).toFloat()
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

    var hasLocPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    var gpsFix by remember { mutableStateOf<Location?>(null) }
    val locPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasLocPerm = it }
    DisposableEffect(hasLocPerm) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val listener = LocationListener { loc -> gpsFix = loc }
        if (hasLocPerm && lm != null) {
            runCatching {
                gpsFix = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 2f, listener)
            }
        }
        onDispose { runCatching { lm?.removeUpdates(listener) } }
    }

    val profile by repository.observeSecurityProfile().collectAsState(initial = null)
    val refLat = gpsFix?.latitude ?: profile?.homeLatitude ?: 35.6892
    val refLon = gpsFix?.longitude ?: profile?.homeLongitude ?: 51.3890

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ObsidianGlassCard(elevated = true) {
                Text(tr("1. Hardware Flashlight & Screen Lux Light", "۱. چراغ‌قوه سخت‌افزاری و نور صفحه"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(label = if (torchOn) tr("Torch OFF", "خاموش") else tr("Torch ON", "روشن"), accent = if (torchOn) colors.accentAmber else colors.accentCyan, onClick = { torchOn = !torchOn })
                    GlassButton(label = if (strobeOn) tr("Strobe ON", "چشمک‌زن فعال") else tr("Strobe", "چشمک‌زن"), accent = colors.accentViolet, onClick = { strobeOn = !strobeOn })
                    GlassButton(label = tr("White Panel", "نور صفحه"), accent = colors.accentTeal, onClick = { screenTorch = !screenTorch })
                }
                if (torchCamId == null) {
                    Spacer(Modifier.height(4.dp))
                    Text(tr("No rear flash LED detected; use White Panel light.", "فلش دوربین در دسترس نیست؛ از نور صفحه استفاده کنید."), style = MaterialTheme.typography.labelSmall, color = colors.accentAmber)
                }
                if (screenTorch) {
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(90.dp).background(Color.White, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Text("MAX WHITE LIGHT", color = Color.Black)
                    }
                }
            }
        }

        item {
            ObsidianGlassCard {
                Text(tr("2. Field Compass", "۲. قطب‌نمای میدانی"), style = MaterialTheme.typography.titleMedium, color = colors.accentTeal)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Canvas(modifier = Modifier.size(110.dp)) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = size.minDimension * 0.44f
                        drawCircle(colors.accentCyan.copy(alpha = 0.25f), r, c, style = Stroke(width = 4f))
                        val rad = Math.toRadians((-azimuth).toDouble())
                        drawLine(colors.accentCrimson, c, Offset(c.x + (r * 0.85f * sin(rad)).toFloat(), c.y - (r * 0.85f * cos(rad)).toFloat()), strokeWidth = 5f)
                        drawLine(colors.textSecondary, c, Offset(c.x - (r * 0.55f * sin(rad)).toFloat(), c.y + (r * 0.55f * cos(rad)).toFloat()), strokeWidth = 3f)
                    }
                    Column {
                        Text(GeoMath.formatBearing(azimuth.toDouble()), style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
                        Text(tr("Calibrated via Magnetometer + Accelerometer", "کالیبره‌شده با حسگرهای دستگاه"), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    }
                }
            }
        }

        item {
            ObsidianGlassCard {
                Text(tr("3. Offline GPS Telemetry & Coordinates", "۳. مختصات GPS آفلاین"), style = MaterialTheme.typography.titleMedium, color = colors.accentAmber)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (gpsFix != null) "LIVE HARDWARE GPS FIX (±${gpsFix!!.accuracy.toInt()}m, Alt ${gpsFix!!.altitude.toInt()}m)"
                    else "NO SATELLITE FIX — USING SAVED HOME REFERENCE",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (gpsFix != null) colors.accentEmerald else colors.accentAmber,
                )
                Text("Decimal: ${GeoMath.formatDecimalDegrees(refLat, refLon)}", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                Text("DMS: ${GeoMath.formatDms(refLat, refLon)}", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                Text("UTM: ${GeoMath.formatUtmApprox(refLat, refLon)}", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                if (!hasLocPerm) {
                    Spacer(Modifier.height(6.dp))
                    GlassButton(label = tr("Grant GPS Permission", "مجوز GPS"), onClick = { locPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) })
                }
            }
        }
    }
}

@Composable
fun VoiceAndScannerTab(repository: AegisRepository) {
    val colors = LocalObsidianColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val voiceNotes by repository.observeVoiceNotes().collectAsState(initial = emptyList())
    var voiceTitle by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var recordStartMs by remember { mutableLongStateOf(0L) }
    var currentFile by remember { mutableStateOf<File?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingId by remember { mutableStateOf<String?>(null) }

    var hasAudioPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val audioPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasAudioPerm = it }

    val scanDir = remember { File(context.filesDir, "scanned_docs").apply { mkdirs() } }
    var scannedFiles by remember { mutableStateOf(scanDir.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()) }
    var scanNotice by remember { mutableStateOf<String?>(null) }

    val cameraScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            scope.launch {
                val processed = applyHighContrastDocumentFilter(bmp)
                val outPdf = File(scanDir, "scan_${System.currentTimeMillis()}.pdf")
                val doc = PdfDocument()
                val page = doc.startPage(PdfDocument.PageInfo.Builder(processed.width, processed.height, 1).create())
                page.canvas.drawBitmap(processed, 0f, 0f, null)
                doc.finishPage(page)
                outPdf.outputStream().use { doc.writeTo(it) }
                doc.close()
                scannedFiles = scanDir.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
                scanNotice = "Scanned & contrast-enhanced offline PDF: ${outPdf.name}"
                repository.logActivity("DOC_SCAN", "Advanced Utilities", "Scanned document offline to ${outPdf.name}")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.release() }
            runCatching { player?.release() }
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ObsidianGlassCard(elevated = true) {
                Text(tr("4. Offline Voice Notes (M4A Local Storage)", "۴. ضبط یادداشت صوتی آفلاین"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                Spacer(Modifier.height(8.dp))
                GlassTextField(voiceTitle, { voiceTitle = it }, tr("Voice Note Title", "عنوان یادداشت صوتی"))
                Spacer(Modifier.height(8.dp))
                if (!hasAudioPerm) {
                    GlassButton(label = tr("Grant Microphone Permission", "مجوز میکروفون"), onClick = { audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO) })
                } else {
                    GlassButton(
                        label = if (isRecording) tr("Stop & Save Recording", "توقف و ذخیره صدا") else tr("Start Offline Recording", "شروع ضبط صدا"),
                        accent = if (isRecording) colors.accentCrimson else colors.accentCyan,
                        onClick = {
                            if (!isRecording) {
                                val dir = File(context.filesDir, "voice_notes").apply { mkdirs() }
                                val f = File(dir, "vn_${System.currentTimeMillis()}.m4a")
                                val mr = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
                                runCatching {
                                    mr.setAudioSource(MediaRecorder.AudioSource.MIC)
                                    mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                    mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                    mr.setOutputFile(f.absolutePath)
                                    mr.prepare()
                                    mr.start()
                                    recorder = mr
                                    currentFile = f
                                    recordStartMs = System.currentTimeMillis()
                                    isRecording = true
                                }
                            } else {
                                runCatching {
                                    recorder?.stop()
                                    recorder?.release()
                                }
                                recorder = null
                                isRecording = false
                                val savedFile = currentFile
                                if (savedFile != null) {
                                    val dur = (System.currentTimeMillis() - recordStartMs).coerceAtLeast(500L)
                                    scope.launch {
                                        repository.upsertVoiceNote(
                                            VoiceNote(
                                                id = "vn-${UUID.randomUUID().toString().take(8)}",
                                                title = voiceTitle.ifBlank { "Field Memo" },
                                                localFilePath = savedFile.absolutePath,
                                                durationMillis = dur,
                                                createdAtMillis = System.currentTimeMillis(),
                                            ),
                                        )
                                        voiceTitle = ""
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }

        items(voiceNotes, key = { it.id }) { vn ->
            ObsidianGlassCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(vn.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Text("${vn.durationMillis / 1000}s · Local M4A", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    }
                    Row {
                        TextButton(
                            onClick = {
                                if (playingId == vn.id) {
                                    runCatching { player?.stop(); player?.release() }
                                    player = null
                                    playingId = null
                                } else {
                                    runCatching {
                                        player?.release()
                                        val mp = MediaPlayer().apply {
                                            setDataSource(vn.localFilePath)
                                            prepare()
                                            setOnCompletionListener { playingId = null }
                                            start()
                                        }
                                        player = mp
                                        playingId = vn.id
                                    }
                                }
                            },
                        ) { Text(if (playingId == vn.id) tr("Stop", "توقف") else tr("Play", "پخش")) }
                        TextButton(onClick = { scope.launch { repository.deleteVoiceNote(vn.id) } }) {
                            Text(tr("Delete", "حذف"), color = colors.accentCrimson)
                        }
                    }
                }
            }
        }

        item {
            ObsidianGlassCard {
                Text(tr("5. Offline Document Scanner (High-Contrast PDF)", "۵. اسکنر اسناد آفلاین (خروجی PDF)"), style = MaterialTheme.typography.titleMedium, color = colors.accentViolet)
                Spacer(Modifier.height(8.dp))
                GlassButton(label = tr("Capture & Scan Document to PDF", "اسکن مدرک با دوربین"), accent = colors.accentViolet, onClick = { cameraScanLauncher.launch(null) })
                if (scanNotice != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(scanNotice!!, style = MaterialTheme.typography.labelSmall, color = colors.accentEmerald)
                }
                scannedFiles.forEach { sf ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${sf.name} (${sf.length() / 1024} KB)", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                        TextButton(onClick = { LocalFileExporter.shareFile(context, sf, "application/pdf", "Share Scanned PDF") }) {
                            Text(tr("Share", "اشتراک"))
                        }
                    }
                }
            }
        }
    }
}

private fun applyHighContrastDocumentFilter(src: Bitmap): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(out)
    val cm = ColorMatrix().apply { setSaturation(0f) }
    val contrast = ColorMatrix(
        floatArrayOf(
            1.45f, 0f, 0f, 0f, -35f,
            0f, 1.45f, 0f, 0f, -35f,
            0f, 0f, 1.45f, 0f, -35f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    cm.postConcat(contrast)
    val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
    canvas.drawBitmap(src, 0f, 0f, paint)
    return out
}

@Composable
fun NotesAndInventoryTab(repository: AegisRepository) {
    val colors = LocalObsidianColors.current
    val scope = rememberCoroutineScope()

    val notes by repository.observeOfflineNotes().collectAsState(initial = emptyList())
    val inventory by repository.observeInventoryItems().collectAsState(initial = emptyList())

    var noteTitle by remember { mutableStateOf("") }
    var noteBody by remember { mutableStateOf("") }

    var invName by remember { mutableStateOf("") }
    var invCat by remember { mutableStateOf("Water") }
    var invQty by remember { mutableStateOf("10") }
    var invUnit by remember { mutableStateOf("Liters") }
    var invMin by remember { mutableStateOf("12") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ObsidianGlassCard {
                Text(tr("6. Offline Notes", "۶. یادداشت‌های آفلاین"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                Spacer(Modifier.height(8.dp))
                GlassTextField(noteTitle, { noteTitle = it }, tr("Note Title", "عنوان"))
                Spacer(Modifier.height(6.dp))
                GlassTextField(noteBody, { noteBody = it }, tr("Note Details", "متن یادداشت"), singleLine = false, minLines = 2)
                Spacer(Modifier.height(8.dp))
                GlassButton(
                    label = tr("Save Offline Note", "ذخیره یادداشت"),
                    enabled = noteTitle.isNotBlank(),
                    onClick = {
                        scope.launch {
                            repository.upsertOfflineNote(
                                OfflineNote(
                                    id = "note-${UUID.randomUUID().toString().take(8)}",
                                    title = noteTitle.trim(),
                                    body = noteBody.trim(),
                                    category = "Field",
                                    updatedAtMillis = System.currentTimeMillis(),
                                ),
                            )
                            noteTitle = ""; noteBody = ""
                        }
                    },
                )
            }
        }

        items(notes, key = { it.id }) { n ->
            ObsidianGlassCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text((if (n.isPinned) "★ " else "") + n.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Row {
                        TextButton(onClick = { scope.launch { repository.upsertOfflineNote(n.copy(isPinned = !n.isPinned)) } }) { Text(if (n.isPinned) "Unpin" else "Pin") }
                        TextButton(onClick = { scope.launch { repository.deleteOfflineNote(n.id) } }) { Text(tr("Delete", "حذف"), color = colors.accentCrimson) }
                    }
                }
                Text(n.body, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
        }

        item {
            ObsidianGlassCard(elevated = true) {
                Text(tr("7. Emergency Inventory Tracker", "۷. مدیریت موجودی اضطراری"), style = MaterialTheme.typography.titleMedium, color = colors.accentTeal)
                Spacer(Modifier.height(8.dp))
                GlassTextField(invName, { invName = it }, tr("Supply Name", "نام کالا"))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField(invQty, { invQty = it }, tr("Qty", "مقدار"), modifier = Modifier.weight(1f))
                    GlassTextField(invUnit, { invUnit = it }, tr("Unit", "واحد"), modifier = Modifier.weight(1f))
                    GlassTextField(invMin, { invMin = it }, tr("Min", "حداقل"), modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Water", "Food", "Medical", "Power").forEach { cat ->
                        FilterChip(selected = invCat == cat, onClick = { invCat = cat }, label = { Text(cat, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                GlassButton(
                    label = tr("Add Supply Item", "افزودن به انبار"),
                    accent = colors.accentTeal,
                    enabled = invName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            repository.upsertInventoryItem(
                                InventoryItem(
                                    id = "inv-${UUID.randomUUID().toString().take(8)}",
                                    name = invName.trim(),
                                    category = invCat,
                                    quantity = invQty.toDoubleOrNull() ?: 1.0,
                                    unit = invUnit.ifBlank { "units" },
                                    minimumThreshold = invMin.toDoubleOrNull() ?: 1.0,
                                    expiryAtMillis = null,
                                    storageLocation = "Go-Bag / Home",
                                    updatedAtMillis = System.currentTimeMillis(),
                                ),
                            )
                            invName = ""
                        }
                    },
                )
            }
        }

        items(inventory, key = { it.id }) { item ->
            val low = item.quantity < item.minimumThreshold
            ObsidianGlassCard(accentBorderColor = if (low) colors.accentAmber else null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Text(
                            "${item.quantity} ${item.unit} (Min ${item.minimumThreshold}) · ${if (low) "LOW STOCK" else "OK"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (low) colors.accentAmber else colors.accentEmerald,
                        )
                    }
                    Row {
                        TextButton(onClick = { scope.launch { repository.upsertInventoryItem(item.copy(quantity = (item.quantity - 1).coerceAtLeast(0.0))) } }) { Text("-1") }
                        TextButton(onClick = { scope.launch { repository.upsertInventoryItem(item.copy(quantity = item.quantity + 1)) } }) { Text("+1") }
                    }
                }
            }
        }
    }
}

@Composable
fun CalculatorCalendarTimerUnitsTab(
    repository: AegisRepository,
    reminderScheduler: ReminderScheduler,
    preferences: AppPreferences,
) {
    val colors = LocalObsidianColors.current
    val scope = rememberCoroutineScope()
    val reminders by repository.observeReminders().collectAsState(initial = emptyList())

    var peopleCount by remember { mutableStateOf("4") }
    var daysCount by remember { mutableStateOf("5") }
    val p = peopleCount.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val d = daysCount.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val drinkWaterLiters = p * d * 3.0
    val hygieneWaterLiters = p * d * 2.0
    val kcalTotal = p * d * 2100
    val powerWh = p * d * 45

    var quickRemTitle by remember { mutableStateOf("") }
    var timerSecondsLeft by remember { mutableIntStateOf(300) }
    var timerRunning by remember { mutableStateOf(false) }
    LaunchedEffect(timerRunning) {
        while (timerRunning && timerSecondsLeft > 0) {
            delay(1000L)
            timerSecondsLeft--
            if (timerSecondsLeft == 0) timerRunning = false
        }
    }

    var unitValStr by remember { mutableStateOf("10") }
    var convDomain by remember { mutableIntStateOf(0) }
    val v = unitValStr.toDoubleOrNull() ?: 0.0

    val cal = remember { Calendar.getInstance() }
    val fmt = remember { SimpleDateFormat("EEEE, yyyy-MM-dd", Locale.US) }
    val jalaliText = remember(cal) { gregorianToJalali(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ObsidianGlassCard(elevated = true) {
                Text(tr("8. Emergency Resource Calculator", "۸. محاسبه‌گر منابع بقا"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField(peopleCount, { peopleCount = it }, tr("People", "تعداد افراد"), modifier = Modifier.weight(1f))
                    GlassTextField(daysCount, { daysCount = it }, tr("Days", "روز"), modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text("Drinking Water: ${drinkWaterLiters.toInt()} L · Hygiene Water: ${hygieneWaterLiters.toInt()} L", style = MaterialTheme.typography.bodyMedium, color = colors.accentTeal)
                Text("Nutrition Reserve: $kcalTotal kcal · Battery Reserve: $powerWh Wh", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
            }
        }

        item {
            ObsidianGlassCard {
                Text(tr("9 & 10. Dual Calendar (Gregorian + Jalali) & Quick Reminder", "۹ و ۱۰. تقویم میلادی/شمسی و یادآور"), style = MaterialTheme.typography.titleMedium, color = colors.accentViolet)
                Spacer(Modifier.height(6.dp))
                Text("Gregorian: ${fmt.format(cal.time)}", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                Text("Solar Hijri (Jalali): $jalaliText", style = MaterialTheme.typography.bodyLarge, color = colors.accentAmber)
                Text("${tr("Active Scheduled Reminders", "یادآورهای فعال")}: ${reminders.count { !it.completed }}", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassTextField(quickRemTitle, { quickRemTitle = it }, tr("Quick Check-In Reminder", "یادآور سریع"), modifier = Modifier.weight(1f))
                    GlassButton(
                        label = tr("Add 1h", "ثبت ۱ ساعت"),
                        enabled = quickRemTitle.isNotBlank(),
                        onClick = {
                            scope.launch {
                                val rem = SecurityReminder(
                                    id = "rem-${UUID.randomUUID().toString().take(8)}",
                                    title = quickRemTitle.trim(),
                                    category = "Utility",
                                    dueAtMillis = System.currentTimeMillis() + 3_600_000L,
                                    createdAtMillis = System.currentTimeMillis(),
                                )
                                repository.upsertReminder(rem)
                                reminderScheduler.scheduleReminder(rem, preferences.sensitiveNotificationRedacted)
                                quickRemTitle = ""
                            }
                        },
                    )
                }
            }
        }

        item {
            ObsidianGlassCard {
                Text(tr("11. Tactical Countdown Timer", "۱۱. تایمر میدانی"), style = MaterialTheme.typography.titleMedium, color = colors.accentAmber)
                Spacer(Modifier.height(6.dp))
                val mm = timerSecondsLeft / 60
                val ss = timerSecondsLeft % 60
                Text("%02d:%02d".format(mm, ss), style = MaterialTheme.typography.headlineLarge, color = colors.textPrimary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(label = if (timerRunning) tr("Pause", "توقف") else tr("Start", "شروع"), onClick = { timerRunning = !timerRunning })
                    GlassButton(label = "5m", accent = colors.accentTeal, onClick = { timerRunning = false; timerSecondsLeft = 300 })
                    GlassButton(label = "15m", accent = colors.accentTeal, onClick = { timerRunning = false; timerSecondsLeft = 900 })
                }
            }
        }

        item {
            ObsidianGlassCard {
                Text(tr("12. Offline Field Unit Converter", "۱۲. مبدل واحدهای میدانی"), style = MaterialTheme.typography.titleMedium, color = colors.accentTeal)
                Spacer(Modifier.height(8.dp))
                GlassTextField(unitValStr, { unitValStr = it }, tr("Input Value", "مقدار ورودی"))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Dist", "Temp", "Mass", "Vol", "Speed").forEachIndexed { idx, lbl ->
                        FilterChip(selected = convDomain == idx, onClick = { convDomain = idx }, label = { Text(lbl, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                val res = when (convDomain) {
                    0 -> "$v km = ${"%.2f".format(v * 0.621371)} mi = ${"%.2f".format(v * 0.539957)} nmi"
                    1 -> "$v °C = ${"%.1f".format(v * 9.0 / 5.0 + 32.0)} °F = ${"%.1f".format(v + 273.15)} K"
                    2 -> "$v kg = ${"%.2f".format(v * 2.20462)} lb = ${"%.0f".format(v * 1000.0)} g"
                    3 -> "$v L = ${"%.2f".format(v * 0.264172)} US gal = ${"%.0f".format(v * 1000.0)} mL"
                    else -> "$v km/h = ${"%.2f".format(v / 3.6)} m/s = ${"%.2f".format(v * 0.539957)} knots"
                }
                Text(res, style = MaterialTheme.typography.bodyLarge, color = colors.accentCyan)
            }
        }
    }
}

private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): String {
    val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gDaysInMonth[gm - 1]
    var jy = -1595 + (33 * (days / 12053))
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm = if (days < 186) 1 + (days / 31) else 7 + ((days - 186) / 30)
    val jd = 1 + if (days < 186) (days % 31) else ((days - 186) % 30)
    return "%04d/%02d/%02d".format(jy, jm, jd)
}
