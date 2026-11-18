package ir.aegis.feature.emergency

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.aegis.core.ai.LocalKnowledgeBase
import ir.aegis.core.maps.GeoMath
import ir.aegis.core.model.*
import ir.aegis.core.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

@Composable
fun EmergencyResponseScreen(
    repository: AegisRepository,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
) {
    val colors = LocalObsidianColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val card by repository.observeEmergencyCard().collectAsState(initial = null)
    val contacts by repository.observeTrustedContacts().collectAsState(initial = emptyList())
    val profile by repository.observeSecurityProfile().collectAsState(initial = null)
    val checklists by repository.observeChecklists().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) }
    var sosActive by remember { mutableStateOf(false) }
    var audibleSiren by remember { mutableStateOf(false) }
    var customSituation by remember { mutableStateOf("Need immediate evacuation assistance") }
    var clipboardFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sosActive, audibleSiren) {
        if (!sosActive) return@LaunchedEffect
        val camMgr = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        val torchCamId = runCatching {
            camMgr?.cameraIdList?.firstOrNull { id ->
                camMgr.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()

        val morseUnits = listOf(1, 1, 1, 3, 3, 3, 1, 1, 1)
        try {
            while (sosActive) {
                for (units in morseUnits) {
                    if (!sosActive) break
                    if (torchCamId != null) runCatching { camMgr?.setTorchMode(torchCamId, true) }
                    if (audibleSiren) {
                        withContext(Dispatchers.Default) { playShortTone(units * 140) }
                    } else {
                        delay(units * 140L)
                    }
                    if (torchCamId != null) runCatching { camMgr?.setTorchMode(torchCamId, false) }
                    delay(140L)
                }
                delay(650L)
            }
        } finally {
            if (torchCamId != null) runCatching { camMgr?.setTorchMode(torchCamId, false) }
        }
    }

    val lat = profile?.homeLatitude ?: 35.6892
    val lon = profile?.homeLongitude ?: 51.3890
    val generatedMsg = remember(profile, card, customSituation, lat, lon) {
        buildString {
            append("[AEGIS SOS] ${profile?.fullName?.ifBlank { "User" } ?: "User"}")
            if (!card?.bloodType.isNullOrBlank()) append(" (Blood: ${card?.bloodType})")
            append("\nStatus: $customSituation")
            append("\nCoords (Ref): ${GeoMath.formatDecimalDegrees(lat, lon)}")
            append("\nDMS: ${GeoMath.formatDms(lat, lon)}")
            if (!card?.primaryIceContactPhone.isNullOrBlank()) {
                append("\nICE: ${card?.primaryIceContactName} ${card?.primaryIceContactPhone}")
            }
            append("\nSent from offline local template.")
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            module = AegisModule.EMERGENCY,
            onBack = onBack,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            extraBadge = if (sosActive) tr("SOS BEACON ACTIVE", "آژیر SOS فعال") else tr("AIRPLANE-MODE READY", "آماده در حالت هواپیما"),
            showBackButton = showBackButton,
        )
        GlassTabRow(
            tabs = listOf(
                tr("SOS & Dial", "SOS و تماس"),
                tr("First Aid", "کمک‌های اولیه"),
                tr("Disaster Guides", "راهنمای بلایا"),
                tr("Equipment Kit", "تجهیزات بقا"),
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )
        Spacer(Modifier.height(10.dp))

        when (selectedTab) {
            0 -> {
                val sosAccent by animateColorAsState(if (sosActive) colors.accentCrimson else colors.accentCyan, label = "sos-color")
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        ObsidianGlassCard(elevated = true, accentBorderColor = sosAccent) {
                            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (sosActive) tr("SOS MORSE BEACON TRANSMITTING", "چراغ و هشدار SOS فعال است") else tr("OFFLINE SOS RESPONSE PANEL", "پنل پاسخ اضطراری آفلاین"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = sosAccent,
                                )
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        sosActive = !sosActive
                                        if (sosActive) {
                                            scope.launch { repository.logActivity("SOS_TRIGGER", "Emergency Response", "Activated offline SOS Morse beacon") }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (sosActive) colors.accentCrimson else colors.accentCrimson.copy(alpha = 0.25f),
                                        contentColor = if (sosActive) Color.White else colors.accentCrimson,
                                    ),
                                ) {
                                    Text(
                                        if (sosActive) tr("STOP SOS BEACON", "توقف SOS") else tr("ACTIVATE SOS BEACON (TORCH + TONE)", "فعال‌سازی آژیر و چراغ SOS"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(checked = audibleSiren, onCheckedChange = { audibleSiren = it })
                                    Spacer(Modifier.width(8.dp))
                                    Text(tr("Include Audible 2.6 kHz Whistle Pulse", "همراه با سوت صوتی ۲.۶ کیلوهرتز"), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                                }
                            }
                        }
                    }

                    item {
                        ObsidianGlassCard {
                            Text(tr("Quick Emergency Dialer (Native Phone App)", "شماره‌گیری سریع امدادی"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                            Spacer(Modifier.height(8.dp))
                            ChipFlowGroup {
                                listOf(
                                    "112" to tr("SOS / Red Crescent", "امداد ۱۱۲"),
                                    "115" to tr("Medical EMS", "اورژانس ۱۱۵"),
                                    "125" to tr("Fire Dept", "آتش‌نشانی ۱۲۵"),
                                    "110" to tr("Police", "پلیس ۱۱۰"),
                                ).forEach { (num, label) ->
                                    GlassButton(
                                        label = "$label ($num)",
                                        accent = colors.accentCrimson,
                                        onClick = {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            }
                                        },
                                    )
                                }
                            }
                            if (contacts.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                contacts.take(3).forEach { tc ->
                                    TextButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${tc.phone}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            }
                                        },
                                    ) { Text("${tr("Call", "تماس با")} ${tc.name} (${tc.phone})") }
                                }
                            }
                        }
                    }

                    item {
                        ObsidianGlassCard {
                            Text(tr("Emergency Info & Reference Coordinates", "اطلاعات پزشکی و مختصات مرجع"), style = MaterialTheme.typography.titleMedium, color = colors.accentTeal)
                            Spacer(Modifier.height(4.dp))
                            Text("${tr("Name", "نام")}: ${card?.fullName ?: "Not set"} · ${tr("Blood", "گروه خونی")}: ${card?.bloodType ?: "Unknown"}", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                            Text("${tr("Allergies", "حساسیت")}: ${card?.allergies ?: "None"} · ${tr("Conditions", "بیماری")}: ${card?.medicalConditions ?: "None"}", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            Text("Grid Ref: ${GeoMath.formatDms(lat, lon)} (${GeoMath.formatUtmApprox(lat, lon)})", style = MaterialTheme.typography.labelSmall, color = colors.accentAmber)
                        }
                    }

                    item {
                        ObsidianGlassCard {
                            Text(tr("Emergency Message Generator", "تولید پیام اضطراری"), style = MaterialTheme.typography.titleMedium, color = colors.accentAmber)
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(customSituation, { customSituation = it }, tr("Situation Note", "وضعیت فعلی"))
                            Spacer(Modifier.height(8.dp))
                            Surface(shape = RoundedCornerShape(10.dp), color = colors.glassElevated) {
                                Text(generatedMsg, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                            }
                            Spacer(Modifier.height(8.dp))
                            val copiedText = tr("Copied to clipboard offline.", "در حافظه موقت کپی شد.")
                            ChipFlowGroup {
                                GlassButton(
                                    label = tr("Copy Message", "کپی پیام"),
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Aegis SOS", generatedMsg))
                                        clipboardFeedback = copiedText
                                    },
                                )
                                GlassButton(
                                    label = tr("Compose SMS", "ارسال پیامک"),
                                    accent = colors.accentTeal,
                                    onClick = {
                                        val recipient = contacts.firstOrNull { it.canReceiveSosMessage }?.phone.orEmpty()
                                        runCatching {
                                            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$recipient")).apply {
                                                putExtra("sms_body", generatedMsg)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(smsIntent)
                                        }
                                    },
                                )
                            }
                            if (clipboardFeedback != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(clipboardFeedback!!, style = MaterialTheme.typography.labelSmall, color = colors.accentEmerald)
                            }
                        }
                    }
                }
            }

            1 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        ObsidianGlassCard(accentBorderColor = colors.accentEmerald) {
                            Text(tr("Reviewed Offline First-Aid Protocols", "پروتکل‌های بازبینی‌شده کمک‌های اولیه"), style = MaterialTheme.typography.titleMedium, color = colors.accentEmerald)
                            Text(LocalKnowledgeBase.firstAidGuides.first().reviewedSource, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                        }
                    }
                    items(LocalKnowledgeBase.firstAidGuides, key = { it.id }) { guide ->
                        ObsidianGlassCard {
                            Text("${guide.title} [${guide.urgency}]", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Text("${guide.category} · ${guide.reviewedSource}", style = MaterialTheme.typography.labelSmall, color = colors.accentCyan)
                            Spacer(Modifier.height(6.dp))
                            guide.steps.forEachIndexed { i, step ->
                                Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            }
                            if (guide.doNotDo.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                guide.doNotDo.forEach { warn ->
                                    Text("! $warn", style = MaterialTheme.typography.labelSmall, color = colors.accentAmber)
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(LocalKnowledgeBase.disasterGuides, key = { it.id }) { guide ->
                        ObsidianGlassCard {
                            Text(guide.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Text("${guide.hazardType} · ${guide.reviewedSource}", style = MaterialTheme.typography.labelSmall, color = colors.accentAmber)
                            Spacer(Modifier.height(6.dp))
                            Text(tr("During:", "حین حادثه:"), style = MaterialTheme.typography.labelMedium, color = colors.accentCrimson)
                            guide.duringSteps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary) }
                            Spacer(Modifier.height(4.dp))
                            Text(tr("After:", "پس از حادثه:"), style = MaterialTheme.typography.labelMedium, color = colors.accentTeal)
                            guide.afterSteps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary) }
                        }
                    }
                }
            }

            3 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        ObsidianGlassCard(elevated = true) {
                            Text(tr("Evacuation Preparedness & Equipment Checklist", "چک‌لیست تجهیزات اضطراری و تخلیه"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                            Text(tr("Persisted offline in Room SQLite.", "ذخیره‌سازی دائمی و آفلاین."), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                        }
                    }
                    items(checklists, key = { it.checklist.id }) { bundle ->
                        val done = bundle.items.count { it.isChecked }
                        val pct = if (bundle.items.isNotEmpty()) (done * 100 / bundle.items.size) else 0
                        ObsidianGlassCard {
                            Text("${bundle.checklist.title} ($pct%)", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = colors.accentTeal)
                            bundle.items.forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = item.isChecked, onCheckedChange = { checked -> scope.launch { repository.toggleChecklistItem(item.id, checked) } })
                                    Text(item.label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun playShortTone(durationMs: Int) {
    val sampleRate = 8000
    val numSamples = (durationMs * sampleRate) / 1000
    val samples = ShortArray(numSamples)
    val freq = 2600.0
    for (i in 0 until numSamples) {
        samples[i] = (sin(2.0 * Math.PI * i / (sampleRate / freq)) * Short.MAX_VALUE * 0.45).toInt().toShort()
    }
    val track = runCatching {
        AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
    }.getOrNull()
    if (track != null) {
        runCatching {
            track.write(samples, 0, samples.size)
            track.play()
            delay(durationMs.toLong())
            track.stop()
            track.release()
        }
    } else {
        delay(durationMs.toLong())
    }
}
