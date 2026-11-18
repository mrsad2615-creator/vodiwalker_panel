package ir.aegis.feature.personal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.aegis.core.crypto.CryptoEngine
import ir.aegis.core.model.*
import ir.aegis.core.ui.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun PersonalSecurityScreen(
    repository: AegisRepository,
    reminderScheduler: ReminderScheduler,
    preferences: AppPreferences,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
) {
    val colors = LocalObsidianColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val profile by repository.observeSecurityProfile().collectAsState(initial = null)
    val card by repository.observeEmergencyCard().collectAsState(initial = null)
    val vaultItems by repository.observeVaultItems().collectAsState(initial = emptyList())
    val contacts by repository.observeTrustedContacts().collectAsState(initial = emptyList())
    val plans by repository.observeSafetyPlans().collectAsState(initial = emptyList())
    val reminders by repository.observeReminders().collectAsState(initial = emptyList())
    val journal by repository.observeJournalEntries().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        tr("Profile & ID", "پروفیل و کارت"),
        tr("AES Vault", "گاوصندوق"),
        tr("Contacts", "مخاطبین"),
        tr("Plans", "برنامه‌ها"),
        tr("Reminders", "یادآورها"),
        tr("Journal", "دفترچه"),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            module = AegisModule.PERSONAL,
            onBack = onBack,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            showBackButton = showBackButton,
        )
        GlassTabRow(tabs = tabs, selectedIndex = selectedTab, onSelect = { selectedTab = it })
        Spacer(Modifier.height(10.dp))

        when (selectedTab) {
            0 -> ProfileAndIdTab(
                profile = profile,
                card = card,
                onSaveProfile = { updated ->
                    scope.launch {
                        repository.upsertSecurityProfile(updated)
                        repository.logActivity("PROFILE_UPDATE", "Personal Security", "Updated security profile (${updated.aliasOrCallsign})")
                    }
                },
                onSaveCard = { updated ->
                    scope.launch {
                        repository.upsertEmergencyCard(updated)
                        repository.logActivity("ICE_CARD_UPDATE", "Personal Security", "Updated Emergency Identity Card (${updated.bloodType})")
                    }
                },
            )

            1 -> EncryptedVaultTab(
                items = vaultItems,
                onAddVaultItem = { title, cat, secret, key ->
                    scope.launch {
                        val enc = CryptoEngine.encryptText(secret, key)
                        val now = System.currentTimeMillis()
                        repository.upsertVaultItem(
                            VaultItem(
                                id = "vault-${UUID.randomUUID().toString().take(8)}",
                                title = title,
                                category = cat,
                                encryptedPayloadBase64 = enc.ciphertextBase64,
                                ivBase64 = enc.ivBase64,
                                saltBase64 = enc.saltBase64,
                                sha256Checksum = enc.plaintextSha256,
                                createdAtMillis = now,
                                updatedAtMillis = now,
                            ),
                        )
                        repository.logActivity("VAULT_ADD", "Personal Security", "Encrypted vault entry added: $title")
                    }
                },
                onDeleteItem = { id ->
                    scope.launch {
                        repository.deleteVaultItem(id)
                        repository.logActivity("VAULT_DELETE", "Personal Security", "Removed vault entry $id")
                    }
                },
            )

            2 -> TrustedContactsTab(
                contacts = contacts,
                onAddContact = { name, rel, phone, zone ->
                    scope.launch {
                        repository.upsertTrustedContact(
                            TrustedContact(
                                id = "tc-${UUID.randomUUID().toString().take(8)}",
                                name = name,
                                relation = rel,
                                phone = phone,
                                addressOrZone = zone,
                                priorityRank = contacts.size + 1,
                                canReceiveSosMessage = true,
                                createdAtMillis = System.currentTimeMillis(),
                            ),
                        )
                        repository.logActivity("CONTACT_ADD", "Personal Security", "Added trusted contact: $name")
                    }
                },
                onDeleteContact = { id -> scope.launch { repository.deleteTrustedContact(id) } },
                onDial = { phone ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
            )

            3 -> SafetyPlansTab(
                plans = plans,
                onAddPlan = { title, scenario, trigger, stepsText, primaryLoc, backupLoc ->
                    scope.launch {
                        val steps = stepsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        repository.upsertSafetyPlan(
                            PersonalSafetyPlan(
                                id = "sp-${UUID.randomUUID().toString().take(8)}",
                                title = title,
                                scenarioType = scenario,
                                triggerConditions = trigger,
                                actionSteps = steps.ifEmpty { listOf("Execute safe zone move") },
                                primarySafeLocation = primaryLoc,
                                backupSafeLocation = backupLoc,
                                updatedAtMillis = System.currentTimeMillis(),
                            ),
                        )
                        repository.logActivity("PLAN_SAVE", "Personal Security", "Saved personal safety plan: $title")
                    }
                },
                onDeletePlan = { id -> scope.launch { repository.deleteSafetyPlan(id) } },
            )

            4 -> SecurityRemindersTab(
                reminders = reminders,
                onAddReminder = { title, category, minutesFromNow, recurDays ->
                    scope.launch {
                        val rem = SecurityReminder(
                            id = "rem-${UUID.randomUUID().toString().take(8)}",
                            title = title,
                            category = category,
                            dueAtMillis = System.currentTimeMillis() + minutesFromNow * 60_000L,
                            recurrenceDays = recurDays,
                            enabled = true,
                            completed = false,
                            createdAtMillis = System.currentTimeMillis(),
                        )
                        repository.upsertReminder(rem)
                        reminderScheduler.scheduleReminder(rem, preferences.sensitiveNotificationRedacted)
                        repository.logActivity("REMINDER_ADD", "Personal Security", "Scheduled WorkManager reminder: $title")
                    }
                },
                onToggleCompleted = { rem ->
                    scope.launch {
                        val updated = rem.copy(completed = !rem.completed)
                        repository.upsertReminder(updated)
                        reminderScheduler.scheduleReminder(updated, preferences.sensitiveNotificationRedacted)
                    }
                },
                onDeleteReminder = { id ->
                    scope.launch {
                        reminderScheduler.cancelReminder(id)
                        repository.deleteReminder(id)
                    }
                },
            )

            5 -> PrivateJournalTab(
                entries = journal,
                onAddEntry = { title, body, threat, passphrase ->
                    scope.launch {
                        val now = System.currentTimeMillis()
                        val useEnc = passphrase.length >= 4
                        val entry = if (useEnc) {
                            val enc = CryptoEngine.encryptText(body, passphrase)
                            PrivateJournalEntry(
                                id = "jrn-${UUID.randomUUID().toString().take(8)}",
                                title = title,
                                content = enc.ciphertextBase64,
                                isEncrypted = true,
                                ivBase64 = enc.ivBase64,
                                saltBase64 = enc.saltBase64,
                                threatLevel = threat,
                                createdAtMillis = now,
                                updatedAtMillis = now,
                            )
                        } else {
                            PrivateJournalEntry(
                                id = "jrn-${UUID.randomUUID().toString().take(8)}",
                                title = title,
                                content = body,
                                isEncrypted = false,
                                threatLevel = threat,
                                createdAtMillis = now,
                                updatedAtMillis = now,
                            )
                        }
                        repository.upsertJournalEntry(entry)
                        repository.logActivity("JOURNAL_SAVE", "Personal Security", "Saved private journal note: $title")
                    }
                },
                onDeleteEntry = { id -> scope.launch { repository.deleteJournalEntry(id) } },
            )
        }
    }
}

@Composable
private fun ProfileAndIdTab(
    profile: SecurityProfile?,
    card: EmergencyIdentityCard?,
    onSaveProfile: (SecurityProfile) -> Unit,
    onSaveCard: (EmergencyIdentityCard) -> Unit,
) {
    val colors = LocalObsidianColors.current
    var fullName by remember(profile) { mutableStateOf(profile?.fullName.orEmpty()) }
    var callsign by remember(profile) { mutableStateOf(profile?.aliasOrCallsign.orEmpty()) }
    var homeZone by remember(profile) { mutableStateOf(profile?.homeRegionLabel.orEmpty()) }
    var safeCode by remember(profile) { mutableStateOf(profile?.safeCheckInCode.orEmpty()) }
    var duressCode by remember(profile) { mutableStateOf(profile?.duressCode.orEmpty()) }

    var bloodType by remember(card) { mutableStateOf(card?.bloodType ?: "O+") }
    var allergies by remember(card) { mutableStateOf(card?.allergies.orEmpty()) }
    var meds by remember(card) { mutableStateOf(card?.medications.orEmpty()) }
    var iceName by remember(card) { mutableStateOf(card?.primaryIceContactName.orEmpty()) }
    var icePhone by remember(card) { mutableStateOf(card?.primaryIceContactPhone.orEmpty()) }
    var savedBanner by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ObsidianGlassCard(elevated = true, accentBorderColor = colors.accentCrimson) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tr("EMERGENCY IDENTITY CARD (ICE)", "کارت هویت اضطراری (ICE)"), style = MaterialTheme.typography.labelMedium, color = colors.accentCrimson)
                        Spacer(Modifier.height(4.dp))
                        Text(fullName.ifBlank { "Unnamed Guardian" }, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                        Text("${tr("Blood", "گروه خونی")}: $bloodType · ${tr("ICE", "تماس اضطراری")}: $iceName ($icePhone)", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        Text("${tr("Allergies", "حساسیت")}: ${allergies.ifBlank { "None" }}", style = MaterialTheme.typography.bodyMedium, color = colors.accentAmber)
                    }
                    Spacer(Modifier.width(10.dp))
                    DeterministicIceMatrix(seedText = "$fullName|$bloodType|$allergies|$icePhone")
                }
            }
        }

        item {
            ObsidianGlassCard {
                Text(tr("Security Profile", "پروفیل امنیت شخصی"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                Spacer(Modifier.height(10.dp))
                GlassTextField(fullName, { fullName = it }, tr("Full Name", "نام کامل"))
                Spacer(Modifier.height(8.dp))
                GlassTextField(callsign, { callsign = it }, tr("Callsign / Alias", "کد شناسایی"))
                Spacer(Modifier.height(8.dp))
                GlassTextField(homeZone, { homeZone = it }, tr("Home Safe Zone", "منطقه امن خانه"))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField(safeCode, { safeCode = it }, tr("Safe Code", "کد امن"), modifier = Modifier.weight(1f))
                    GlassTextField(duressCode, { duressCode = it }, tr("Duress Code", "کد اضطرار"), modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    label = tr("Save Security Profile", "ذخیره پروفیل"),
                    onClick = {
                        val base = profile ?: SecurityProfile()
                        onSaveProfile(
                            base.copy(
                                fullName = fullName.trim(),
                                aliasOrCallsign = callsign.trim(),
                                homeRegionLabel = homeZone.trim(),
                                safeCheckInCode = safeCode.trim(),
                                duressCode = duressCode.trim(),
                                updatedAtMillis = System.currentTimeMillis(),
                            ),
                        )
                        savedBanner = true
                    },
                )
            }
        }

        item {
            ObsidianGlassCard {
                Text(tr("Medical & ICE Card Details", "مشخصات پزشکی و تماس اضطراری"), style = MaterialTheme.typography.titleMedium, color = colors.accentTeal)
                Spacer(Modifier.height(10.dp))
                GlassTextField(bloodType, { bloodType = it }, tr("Blood Type (e.g. O+)", "گروه خونی"))
                Spacer(Modifier.height(8.dp))
                GlassTextField(allergies, { allergies = it }, tr("Allergies", "حساسیت‌ها"))
                Spacer(Modifier.height(8.dp))
                GlassTextField(meds, { meds = it }, tr("Active Medications", "داروهای مصرفی"))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField(iceName, { iceName = it }, tr("ICE Contact Name", "نام تماس اضطراری"), modifier = Modifier.weight(1f))
                    GlassTextField(icePhone, { icePhone = it }, tr("ICE Phone", "شماره تماس"), modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    label = tr("Update Emergency Card", "به‌روزرسانی کارت اضطراری"),
                    accent = colors.accentTeal,
                    onClick = {
                        val base = card ?: EmergencyIdentityCard()
                        onSaveCard(
                            base.copy(
                                fullName = fullName.trim(),
                                bloodType = bloodType.trim(),
                                allergies = allergies.trim(),
                                medications = meds.trim(),
                                primaryIceContactName = iceName.trim(),
                                primaryIceContactPhone = icePhone.trim(),
                                updatedAtMillis = System.currentTimeMillis(),
                            ),
                        )
                        savedBanner = true
                    },
                )
                if (savedBanner) {
                    Spacer(Modifier.height(6.dp))
                    Text(tr("Saved offline in Room SQLite.", "به‌صورت آفلاین ذخیره شد."), style = MaterialTheme.typography.labelMedium, color = colors.accentEmerald)
                }
            }
        }
    }
}

@Composable
private fun DeterministicIceMatrix(seedText: String) {
    val colors = LocalObsidianColors.current
    val digest = remember(seedText) { CryptoEngine.sha256Hex(seedText) }
    Canvas(modifier = Modifier.size(64.dp)) {
        val cells = 8
        val cellW = size.width / cells
        for (r in 0 until cells) {
            for (c in 0 until cells) {
                val nibble = digest[(r * cells + c) % digest.length].digitToIntOrNull(16) ?: 0
                val isCorner = (r < 2 && c < 2) || (r < 2 && c > 5) || (r > 5 && c < 2)
                if (isCorner || nibble % 2 == 0) {
                    drawRect(
                        color = if (isCorner) colors.accentCyan else colors.textPrimary,
                        topLeft = Offset(c * cellW + 1f, r * cellW + 1f),
                        size = Size(cellW - 2f, cellW - 2f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EncryptedVaultTab(
    items: List<VaultItem>,
    onAddVaultItem: (String, VaultCategory, String, String) -> Unit,
    onDeleteItem: (String) -> Unit,
) {
    val colors = LocalObsidianColors.current
    val lang = LocalAppLanguage.current
    var title by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf(VaultCategory.IDENTITY) }
    var unlockKey by remember { mutableStateOf("") }
    var decryptedPreview by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var decryptError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ObsidianGlassCard(elevated = true, accentBorderColor = colors.accentViolet) {
                Text(tr("AES-256-GCM Encrypted Emergency Vault", "گاوصندوق رمزنگاری‌شده AES-256-GCM"), style = MaterialTheme.typography.titleMedium, color = colors.accentViolet)
                Spacer(Modifier.height(8.dp))
                GlassTextField(title, { title = it }, tr("Document / Record Title", "عنوان سند"))
                Spacer(Modifier.height(8.dp))
                ChipFlowGroup {
                    listOf(VaultCategory.IDENTITY, VaultCategory.MEDICAL, VaultCategory.ACCESS_KEY, VaultCategory.FINANCIAL).forEach { cat ->
                        AegisFilterPill(
                            label = vaultCategoryLabel(cat, lang),
                            selected = selectedCat == cat,
                            onClick = { selectedCat = cat },
                            accent = colors.accentViolet,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                GlassTextField(secret, { secret = it }, tr("Sensitive Payload (ID, Medical Code, Backup Key)", "محتوای حساس"), singleLine = false, minLines = 2)
                Spacer(Modifier.height(8.dp))
                GlassTextField(passphrase, { passphrase = it }, tr("Encryption Passphrase (min 4 chars)", "کلید رمزنگاری"))
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    label = tr("Encrypt & Store Offline", "رمزنگاری و ذخیره آفلاین"),
                    accent = colors.accentViolet,
                    enabled = title.isNotBlank() && secret.isNotBlank() && passphrase.length >= 4,
                    onClick = {
                        onAddVaultItem(title.trim(), selectedCat, secret.trim(), passphrase)
                        title = ""
                        secret = ""
                        passphrase = ""
                    },
                )
            }
        }

        item {
            ObsidianGlassCard {
                Text(tr("Decrypt Vault Entry", "رمزگشایی اسناد گاوصندوق"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                Spacer(Modifier.height(6.dp))
                GlassTextField(unlockKey, { unlockKey = it; decryptError = null }, tr("Enter Vault Passphrase to Unlock", "رمز گاوصندوق را وارد کنید"))
                if (decryptError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(decryptError!!, color = colors.accentCrimson, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        items(items, key = { it.id }) { item ->
            ObsidianGlassCard {
                Text(item.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text("${vaultCategoryLabel(item.category, lang)} · SHA256: ${item.sha256Checksum.take(12)}...", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                val unlocked = decryptedPreview[item.id]
                if (unlocked != null) {
                    Spacer(Modifier.height(6.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = colors.accentEmerald.copy(alpha = 0.14f)) {
                        Text(unlocked, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium, color = colors.accentEmerald)
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text("Ciphertext: ${item.encryptedPayloadBase64.take(36)}...", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                }
                Spacer(Modifier.height(8.dp))
                ChipFlowGroup {
                    GlassButton(
                        label = tr("Decrypt", "باز کردن"),
                        accent = colors.accentCyan,
                        onClick = {
                            if (unlockKey.length < 4) {
                                decryptError = trText(lang, "Enter vault passphrase above first.", "ابتدا رمز گاوصندوق را در کادر بالا وارد کنید.")
                            } else {
                                runCatching {
                                    CryptoEngine.decryptText(item.encryptedPayloadBase64, item.ivBase64, item.saltBase64, unlockKey)
                                }.onSuccess { plain ->
                                    decryptedPreview = decryptedPreview + (item.id to plain)
                                    decryptError = null
                                }.onFailure {
                                    decryptError = trText(lang, "Decryption failed for '${item.title}'.", "رمزگشایی '${item.title}' ناموفق بود.")
                                }
                            }
                        },
                    )
                    GlassButton(
                        label = tr("Delete", "حذف"),
                        accent = colors.accentCrimson,
                        onClick = { onDeleteItem(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrustedContactsTab(
    contacts: List<TrustedContact>,
    onAddContact: (String, String, String, String) -> Unit,
    onDeleteContact: (String) -> Unit,
    onDial: (String) -> Unit,
) {
    val colors = LocalObsidianColors.current
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ObsidianGlassCard {
                Text(tr("Add Trusted Emergency Contact", "افزودن مخاطب مورد اعتماد"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField(name, { name = it }, tr("Name", "نام"), modifier = Modifier.weight(1f))
                    GlassTextField(relation, { relation = it }, tr("Relation", "نسبت"), modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField(phone, { phone = it }, tr("Phone", "تلفن"), modifier = Modifier.weight(1f))
                    GlassTextField(zone, { zone = it }, tr("Neighborhood / Zone", "محدوده"), modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    label = tr("Add Trusted Contact", "ذخیره مخاطب"),
                    enabled = name.isNotBlank() && phone.isNotBlank(),
                    onClick = {
                        onAddContact(name.trim(), relation.ifBlank { "Trusted" }, phone.trim(), zone.trim())
                        name = ""; relation = ""; phone = ""; zone = ""
                    },
                )
            }
        }

        items(contacts, key = { it.id }) { contact ->
            ObsidianGlassCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("#${contact.priorityRank} · ${contact.name}", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Text("${contact.relation} · ${contact.phone}", style = MaterialTheme.typography.bodyMedium, color = colors.accentTeal)
                        if (contact.addressOrZone.isNotBlank()) {
                            Text(contact.addressOrZone, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                        }
                    }
                    Row {
                        TextButton(onClick = { onDial(contact.phone) }) { Text(tr("Dial", "تماس")) }
                        TextButton(onClick = { onDeleteContact(contact.id) }) { Text(tr("Remove", "حذف"), color = colors.accentCrimson) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SafetyPlansTab(
    plans: List<PersonalSafetyPlan>,
    onAddPlan: (String, String, String, String, String, String) -> Unit,
    onDeletePlan: (String) -> Unit,
) {
    val colors = LocalObsidianColors.current
    var title by remember { mutableStateOf("") }
    var scenario by remember { mutableStateOf("EVACUATION") }
    var trigger by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    var primary by remember { mutableStateOf("") }
    var backup by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ObsidianGlassCard {
                Text(tr("New Personal Safety Plan", "برنامه ایمنی شخصی جدید"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                Spacer(Modifier.height(8.dp))
                GlassTextField(title, { title = it }, tr("Plan Title", "عنوان برنامه"))
                Spacer(Modifier.height(8.dp))
                GlassTextField(scenario, { scenario = it }, tr("Scenario (Earthquake / Fire / Personal Threat)", "سناریو"))
                Spacer(Modifier.height(8.dp))
                GlassTextField(trigger, { trigger = it }, tr("Trigger Condition", "شرط فعال‌سازی"))
                Spacer(Modifier.height(8.dp))
                GlassTextField(steps, { steps = it }, tr("Action Steps (one per line)", "مراحل اقدام (هر خط یک گام)"), singleLine = false, minLines = 3)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField(primary, { primary = it }, tr("Primary Safe Zone", "پناهگاه اول"), modifier = Modifier.weight(1f))
                    GlassTextField(backup, { backup = it }, tr("Backup Safe Zone", "پناهگاه دوم"), modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    label = tr("Save Safety Plan", "ذخیره برنامه ایمنی"),
                    enabled = title.isNotBlank(),
                    onClick = {
                        onAddPlan(title.trim(), scenario.trim(), trigger.trim(), steps, primary.trim(), backup.trim())
                        title = ""; trigger = ""; steps = ""; primary = ""; backup = ""
                    },
                )
            }
        }

        items(plans, key = { it.id }) { plan ->
            ObsidianGlassCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(plan.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onDeletePlan(plan.id) }) { Text(tr("Delete", "حذف"), color = colors.accentCrimson) }
                }
                Text("${plan.scenarioType} · ${tr("Trigger", "محرک")}: ${plan.triggerConditions}", style = MaterialTheme.typography.labelMedium, color = colors.accentAmber)
                Spacer(Modifier.height(6.dp))
                plan.actionSteps.forEachIndexed { idx, step ->
                    Text("${idx + 1}. $step", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
                Spacer(Modifier.height(6.dp))
                Text("${tr("Safe Locations", "مکان‌های امن")}: ${plan.primarySafeLocation} / ${plan.backupSafeLocation}", style = MaterialTheme.typography.labelSmall, color = colors.accentTeal)
            }
        }
    }
}

@Composable
private fun SecurityRemindersTab(
    reminders: List<SecurityReminder>,
    onAddReminder: (String, String, Long, Int) -> Unit,
    onToggleCompleted: (SecurityReminder) -> Unit,
    onDeleteReminder: (String) -> Unit,
) {
    val colors = LocalObsidianColors.current
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Kit Check") }
    var minutesStr by remember { mutableStateOf("60") }
    var recurDaysStr by remember { mutableStateOf("7") }
    val fmt = remember { SimpleDateFormat("MMM dd · HH:mm", Locale.US) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ObsidianGlassCard {
                Text(tr("Schedule Offline WorkManager Reminder", "زمان‌بندی یادآور آفلاین"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                Spacer(Modifier.height(8.dp))
                GlassTextField(title, { title = it }, tr("Reminder Title", "عنوان یادآور"))
                Spacer(Modifier.height(8.dp))
                GlassTextField(category, { category = it }, tr("Category", "دسته‌بندی"))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField(minutesStr, { minutesStr = it }, tr("Due (mins)", "دقیقه تا یادآوری"), modifier = Modifier.weight(1f))
                    GlassTextField(recurDaysStr, { recurDaysStr = it }, tr("Repeat (days)", "تکرار (روز)"), modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    label = tr("Schedule Reminder", "ثبت یادآور"),
                    enabled = title.isNotBlank(),
                    onClick = {
                        onAddReminder(title.trim(), category.trim(), minutesStr.toLongOrNull() ?: 60L, recurDaysStr.toIntOrNull() ?: 0)
                        title = ""
                    },
                )
            }
        }

        items(reminders, key = { it.id }) { rem ->
            ObsidianGlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rem.completed, onCheckedChange = { onToggleCompleted(rem) })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rem.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Text("${rem.category} · ${fmt.format(Date(rem.dueAtMillis))} · Repeat ${rem.recurrenceDays}d", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    }
                    TextButton(onClick = { onDeleteReminder(rem.id) }) { Text(tr("Delete", "حذف"), color = colors.accentCrimson) }
                }
            }
        }
    }
}

@Composable
private fun PrivateJournalTab(
    entries: List<PrivateJournalEntry>,
    onAddEntry: (String, String, ThreatMoodLevel, String) -> Unit,
    onDeleteEntry: (String) -> Unit,
) {
    val colors = LocalObsidianColors.current
    val lang = LocalAppLanguage.current
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var threat by remember { mutableStateOf(ThreatMoodLevel.CAUTIOUS) }
    var encPass by remember { mutableStateOf("") }
    var unlockPass by remember { mutableStateOf("") }
    var decryptedMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ObsidianGlassCard {
                Text(tr("Private Security Incident Journal", "دفترچه خصوصی رخدادها"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                Spacer(Modifier.height(8.dp))
                GlassTextField(title, { title = it }, tr("Entry Title", "عنوان یادداشت"))
                Spacer(Modifier.height(8.dp))
                GlassTextField(body, { body = it }, tr("Private Observation / Log", "شرح محرمانه"), singleLine = false, minLines = 3)
                Spacer(Modifier.height(8.dp))
                ChipFlowGroup {
                    ThreatMoodLevel.entries.forEach { lvl ->
                        AegisFilterPill(
                            label = threatMoodLabel(lvl, lang),
                            selected = threat == lvl,
                            onClick = { threat = lvl },
                            accent = if (lvl == ThreatMoodLevel.CRITICAL) colors.accentCrimson else colors.accentCyan,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                GlassTextField(encPass, { encPass = it }, tr("Optional AES Lock Passphrase (>=4 chars)", "رمز اختیاری AES"))
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    label = tr("Save Journal Entry", "ذخیره در دفترچه"),
                    enabled = title.isNotBlank() && body.isNotBlank(),
                    onClick = {
                        onAddEntry(title.trim(), body.trim(), threat, encPass)
                        title = ""; body = ""; encPass = ""
                    },
                )
            }
        }

        if (entries.any { it.isEncrypted }) {
            item {
                ObsidianGlassCard {
                    GlassTextField(unlockPass, { unlockPass = it }, tr("Passphrase to Unlock Encrypted Entries", "رمز بازگشایی یادداشت‌های قفل‌شده"))
                }
            }
        }

        items(entries, key = { it.id }) { entry ->
            ObsidianGlassCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Text("${threatMoodLabel(entry.threatLevel, lang)} · ${if (entry.isEncrypted) tr("AES-256 Encrypted", "رمزنگاری AES-256") else tr("Local Note", "یادداشت محلی")}", style = MaterialTheme.typography.labelSmall, color = colors.accentAmber)
                    }
                    Row {
                        if (entry.isEncrypted) {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        CryptoEngine.decryptText(entry.content, entry.ivBase64, entry.saltBase64, unlockPass)
                                    }.onSuccess { decryptedMap = decryptedMap + (entry.id to it) }
                                },
                            ) { Text(tr("Unlock", "گشودن")) }
                        }
                        TextButton(onClick = { onDeleteEntry(entry.id) }) { Text(tr("Delete", "حذف"), color = colors.accentCrimson) }
                    }
                }
                val text = if (!entry.isEncrypted) entry.content else decryptedMap[entry.id] ?: "[Encrypted payload — enter passphrase above]"
                Spacer(Modifier.height(4.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
        }
    }
}
