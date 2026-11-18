package ir.aegis.feature.family

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.aegis.core.crypto.CryptoEngine
import ir.aegis.core.maps.GeoMath
import ir.aegis.core.model.*
import ir.aegis.core.ui.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val shareJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Composable
fun FamilySafetyScreen(
    repository: AegisRepository,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
) {
    val colors = LocalObsidianColors.current
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val members by repository.observeFamilyMembers().collectAsState(initial = emptyList())
    val plan by repository.observeFamilyPlan().collectAsState(initial = null)
    val points by repository.observeMeetingPoints().collectAsState(initial = emptyList())
    val checklists by repository.observeChecklists().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            module = AegisModule.FAMILY,
            onBack = onBack,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            showBackButton = showBackButton,
        )
        GlassTabRow(
            tabs = listOf(
                tr("Members & Cards", "اعضا و کارت‌ها"),
                tr("Family Plan", "برنامه خانواده"),
                tr("Rally Points", "نقاط تجمع"),
                tr("Consent Share", "اشتراک محلی"),
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )
        Spacer(Modifier.height(10.dp))

        when (selectedTab) {
            0 -> {
                var fullName by remember { mutableStateOf("") }
                var relation by remember { mutableStateOf("") }
                var ageGroup by remember { mutableStateOf("Adult") }
                var bloodType by remember { mutableStateOf("O+") }
                var allergies by remember { mutableStateOf("") }
                var meds by remember { mutableStateOf("") }
                var phone by remember { mutableStateOf("") }
                var specialNeeds by remember { mutableStateOf("") }

                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        ObsidianGlassCard {
                            Text(tr("Add Family Member Profile & ICE Card", "افزودن عضو خانواده و کارت پزشکی"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassTextField(fullName, { fullName = it }, tr("Full Name", "نام کامل"), modifier = Modifier.weight(1f))
                                GlassTextField(relation, { relation = it }, tr("Relation", "نسبت"), modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassTextField(ageGroup, { ageGroup = it }, tr("Age Group", "رده سنی"), modifier = Modifier.weight(1f))
                                GlassTextField(bloodType, { bloodType = it }, tr("Blood Type", "گروه خونی"), modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassTextField(phone, { phone = it }, tr("Phone", "تلفن"), modifier = Modifier.weight(1f))
                                GlassTextField(allergies, { allergies = it }, tr("Allergies", "حساسیت"), modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(meds, { meds = it }, tr("Medications", "داروها"))
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(specialNeeds, { specialNeeds = it }, tr("Special Needs / Notes", "نیازهای ویژه"))
                            Spacer(Modifier.height(10.dp))
                            GlassButton(
                                label = tr("Save Family Profile", "ثبت پروفیل عضو"),
                                enabled = fullName.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        val now = System.currentTimeMillis()
                                        repository.upsertFamilyMember(
                                            FamilyMemberProfile(
                                                id = "fm-${UUID.randomUUID().toString().take(8)}",
                                                fullName = fullName.trim(),
                                                relation = relation.ifBlank { "Family" },
                                                ageGroup = ageGroup.ifBlank { "Adult" },
                                                bloodType = bloodType.trim(),
                                                allergies = allergies.trim(),
                                                medications = meds.trim(),
                                                specialNeeds = specialNeeds.trim(),
                                                phone = phone.trim(),
                                                emergencyNote = "",
                                                checkInStatus = FamilyCheckInStatus.SAFE,
                                                lastCheckInMillis = now,
                                                updatedAtMillis = now,
                                            ),
                                        )
                                        fullName = ""; relation = ""; allergies = ""; meds = ""; phone = ""; specialNeeds = ""
                                    }
                                },
                            )
                        }
                    }

                    items(members, key = { it.id }) { m ->
                        val stColor = when (m.checkInStatus) {
                            FamilyCheckInStatus.SAFE -> colors.accentEmerald
                            FamilyCheckInStatus.UNKNOWN, FamilyCheckInStatus.EVACUATING -> colors.accentAmber
                            FamilyCheckInStatus.NEED_HELP -> colors.accentCrimson
                        }
                        ObsidianGlassCard(accentBorderColor = stColor) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${m.fullName} (${m.relation} · ${m.ageGroup})", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                    Text("ICE Card · Blood ${m.bloodType} · Allergies: ${m.allergies.ifBlank { "None" }}", style = MaterialTheme.typography.labelSmall, color = colors.accentTeal)
                                    if (m.specialNeeds.isNotBlank() || m.medications.isNotBlank()) {
                                        Text("Meds: ${m.medications.ifBlank { "-" }} · Note: ${m.specialNeeds.ifBlank { "-" }}", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                    }
                                }
                                if (m.phone.isNotBlank()) {
                                    TextButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${m.phone}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            }
                                        },
                                    ) { Text(tr("Call", "تماس")) }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            ChipFlowGroup {
                                FamilyCheckInStatus.entries.forEach { st ->
                                    AegisFilterPill(
                                        label = familyStatusLabel(st, lang),
                                        selected = m.checkInStatus == st,
                                        onClick = {
                                            scope.launch {
                                                val now = System.currentTimeMillis()
                                                repository.upsertFamilyMember(m.copy(checkInStatus = st, lastCheckInMillis = now, updatedAtMillis = now))
                                            }
                                        },
                                        accent = when (st) {
                                            FamilyCheckInStatus.SAFE -> colors.accentEmerald
                                            FamilyCheckInStatus.NEED_HELP -> colors.accentCrimson
                                            else -> colors.accentAmber
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                var planTitle by remember(plan) { mutableStateOf(plan?.planTitle.orEmpty()) }
                var evacProtocol by remember(plan) { mutableStateOf(plan?.evacuationProtocol.orEmpty()) }
                var commProtocol by remember(plan) { mutableStateOf(plan?.communicationProtocol.orEmpty()) }
                var relayName by remember(plan) { mutableStateOf(plan?.outOfAreaContactName.orEmpty()) }
                var relayPhone by remember(plan) { mutableStateOf(plan?.outOfAreaContactPhone.orEmpty()) }
                var saved by remember { mutableStateOf(false) }

                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        ObsidianGlassCard(elevated = true) {
                            Text(tr("Family Emergency & Communication Plan", "برنامه اضطراری و ارتباطی خانواده"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(planTitle, { planTitle = it }, tr("Plan Title", "عنوان برنامه"))
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(evacProtocol, { evacProtocol = it }, tr("Evacuation Protocol", "راهبرد تخلیه"), singleLine = false, minLines = 3)
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(commProtocol, { commProtocol = it }, tr("Communication Protocol", "کانال ارتباطی"))
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassTextField(relayName, { relayName = it }, tr("Out-of-Area Relay Name", "نام رابط خارج از منطقه"), modifier = Modifier.weight(1f))
                                GlassTextField(relayPhone, { relayPhone = it }, tr("Relay Phone", "تلفن رابط"), modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            GlassButton(
                                label = tr("Save Family Plan", "ذخیره برنامه خانواده"),
                                onClick = {
                                    scope.launch {
                                        repository.upsertFamilyPlan(
                                            FamilyPlan(
                                                id = plan?.id ?: "family-plan-main",
                                                planTitle = planTitle.trim(),
                                                evacuationProtocol = evacProtocol.trim(),
                                                communicationProtocol = commProtocol.trim(),
                                                outOfAreaContactName = relayName.trim(),
                                                outOfAreaContactPhone = relayPhone.trim(),
                                                rallyWindowMinutes = plan?.rallyWindowMinutes ?: 45,
                                                updatedAtMillis = System.currentTimeMillis(),
                                            ),
                                        )
                                        saved = true
                                    }
                                },
                            )
                            if (saved) Text(tr("Family plan persisted offline.", "برنامه خانواده ذخیره شد."), color = colors.accentEmerald, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    items(checklists.take(2), key = { it.checklist.id }) { cl ->
                        ObsidianGlassCard {
                            Text(cl.checklist.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            cl.items.forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = item.isChecked, onCheckedChange = { c -> scope.launch { repository.toggleChecklistItem(item.id, c) } })
                                    Text(item.label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                var mpName by remember { mutableStateOf("") }
                var mpAddress by remember { mutableStateOf("") }
                var mpLandmark by remember { mutableStateOf("") }
                var mpInstr by remember { mutableStateOf("") }
                var mpLat by remember { mutableStateOf("35.7250") }
                var mpLon by remember { mutableStateOf("51.3380") }
                var mpPrimary by remember { mutableStateOf(true) }

                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        ObsidianGlassCard {
                            Text(tr("Meeting Point Planner", "طراح نقاط تجمع خانواده"), style = MaterialTheme.typography.titleMedium, color = colors.accentTeal)
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(mpName, { mpName = it }, tr("Rally Point Name", "نام نقطه تجمع"))
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(mpAddress, { mpAddress = it }, tr("Address / Area Note", "آدرس و محدوده"))
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(mpLandmark, { mpLandmark = it }, tr("Distinctive Landmark", "نشانه شاخص"))
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(mpInstr, { mpInstr = it }, tr("Check-in Instructions", "دستورالعمل"))
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassTextField(mpLat, { mpLat = it }, tr("Latitude", "عرض"), modifier = Modifier.weight(1f))
                                GlassTextField(mpLon, { mpLon = it }, tr("Longitude", "طول"), modifier = Modifier.weight(1f))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = mpPrimary, onCheckedChange = { mpPrimary = it })
                                Text(tr("Primary Family Meeting Point", "نقطه تجمع اصلی"), color = colors.textPrimary)
                            }
                            GlassButton(
                                label = tr("Add Meeting Point", "ثبت نقطه تجمع"),
                                accent = colors.accentTeal,
                                enabled = mpName.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        repository.upsertMeetingPoint(
                                            FamilyMeetingPoint(
                                                id = "mp-${UUID.randomUUID().toString().take(8)}",
                                                name = mpName.trim(),
                                                isPrimary = mpPrimary,
                                                latitude = mpLat.toDoubleOrNull() ?: 35.725,
                                                longitude = mpLon.toDoubleOrNull() ?: 51.338,
                                                addressNote = mpAddress.trim(),
                                                landmarkNote = mpLandmark.trim(),
                                                instructions = mpInstr.trim(),
                                            ),
                                        )
                                        mpName = ""; mpAddress = ""; mpLandmark = ""; mpInstr = ""
                                    }
                                },
                            )
                        }
                    }

                    items(points, key = { it.id }) { pt ->
                        ObsidianGlassCard(accentBorderColor = if (pt.isPrimary) colors.accentEmerald else null) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${if (pt.isPrimary) tr("[PRIMARY]", "[اصلی]") else tr("[BACKUP]", "[پشتیبان]")} ${pt.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { scope.launch { repository.deleteMeetingPoint(pt.id) } }) {
                                    Text(tr("Delete", "حذف"), color = colors.accentCrimson)
                                }
                            }
                            Text("${pt.addressNote} · Landmark: ${pt.landmarkNote}", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            Text("Grid: ${GeoMath.formatDms(pt.latitude, pt.longitude)}", style = MaterialTheme.typography.labelSmall, color = colors.accentCyan)
                        }
                    }
                }
            }

            3 -> {
                var consentChecked by remember { mutableStateOf(false) }
                var senderCallsign by remember { mutableStateOf("Family-Device-A") }
                var shareNote by remember { mutableStateOf("All members accounted for.") }
                var generatedPacket by remember { mutableStateOf("") }
                var incomingPacketText by remember { mutableStateOf("") }
                var importStatus by remember { mutableStateOf<String?>(null) }

                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        ObsidianGlassCard(elevated = true, accentBorderColor = colors.accentViolet) {
                            Text(tr("Optional Consent-Based Local Sharing", "اشتراک‌گذاری اختیاری و محلی با رضایت کاربر"), style = MaterialTheme.typography.titleMedium, color = colors.accentViolet)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tr("Zero background discovery, zero cloud servers. Only generates or verifies a signed offline JSON packet when you explicitly consent and share via local transfer.", "هیچ سرویس آنلاین یا اسکن خودکاری فعال نیست. بسته اطلاعاتی فقط با رضایت صریح شما ساخته و بررسی می‌شود."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(senderCallsign, { senderCallsign = it }, tr("Sender Device Callsign", "شناسه دستگاه فرستنده"))
                            Spacer(Modifier.height(6.dp))
                            GlassTextField(shareNote, { shareNote = it }, tr("Emergency Status Note", "یادداشت وضعیت"))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = consentChecked, onCheckedChange = { consentChecked = it })
                                Text(tr("I explicitly consent to export selected family status & meeting points locally.", "با صدور محلی وضعیت و نقاط تجمع موافقم."), style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
                            }
                            ChipFlowGroup {
                                GlassButton(
                                    label = tr("Generate Local Share Packet", "ساخت بسته محلی"),
                                    accent = colors.accentViolet,
                                    enabled = consentChecked,
                                    onClick = {
                                        val now = System.currentTimeMillis()
                                        val unsigned = ConsentLocalSharePacket(
                                            exportedAtMillis = now,
                                            senderCallsign = senderCallsign.trim(),
                                            members = members,
                                            meetingPoints = points,
                                            emergencyNote = shareNote.trim(),
                                            sha256Checksum = "",
                                        )
                                        val signed = CryptoEngine.signLocalSharePacket(unsigned)
                                        generatedPacket = shareJson.encodeToString(signed)
                                        scope.launch {
                                            repository.logActivity("CONSENT_LOCAL_SHARE", "Family Safety", "Generated consent-based offline share packet (${signed.sha256Checksum.take(10)})")
                                        }
                                    },
                                )
                                if (generatedPacket.isNotBlank()) {
                                    GlassButton(
                                        label = tr("Local Share", "ارسال محلی"),
                                        accent = colors.accentTeal,
                                        onClick = {
                                            val file = LocalFileExporter.exportTextFile(context, "shares", "aegis_family_share.json", generatedPacket)
                                            LocalFileExporter.shareFile(context, file, "application/json", "Share Family Safety Packet Locally")
                                        },
                                    )
                                }
                            }
                            if (generatedPacket.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Surface(shape = RoundedCornerShape(10.dp), color = colors.glassElevated) {
                                    Text(generatedPacket.take(260) + "...", modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.labelSmall, color = colors.accentEmerald)
                                }
                            }
                        }
                    }

                    item {
                        ObsidianGlassCard {
                            Text(tr("Verify & Import Received Local Share Packet", "بررسی و ورود بسته دریافتی"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(incomingPacketText, { incomingPacketText = it }, tr("Paste Received JSON Share Packet", "متن JSON بسته را وارد کنید"), singleLine = false, minLines = 3)
                            Spacer(Modifier.height(8.dp))
                            GlassButton(
                                label = tr("Verify Integrity & Merge", "بررسی اصالت و ثبت"),
                                enabled = incomingPacketText.isNotBlank(),
                                onClick = {
                                    runCatching {
                                        val decoded = shareJson.decodeFromString<ConsentLocalSharePacket>(incomingPacketText.trim())
                                        require(CryptoEngine.verifyLocalSharePacket(decoded)) { "Checksum mismatch" }
                                        decoded
                                    }.onSuccess { decoded ->
                                        scope.launch {
                                            decoded.meetingPoints.forEach { repository.upsertMeetingPoint(it) }
                                            decoded.members.forEach { repository.upsertFamilyMember(it) }
                                            importStatus = "Verified SHA-256 & imported ${decoded.members.size} member(s) from ${decoded.senderCallsign}."
                                        }
                                    }.onFailure { err ->
                                        importStatus = "Rejected invalid packet: ${err.message}"
                                    }
                                },
                            )
                            if (importStatus != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(importStatus!!, style = MaterialTheme.typography.labelMedium, color = colors.accentAmber)
                            }
                        }
                    }
                }
            }
        }
    }
}
