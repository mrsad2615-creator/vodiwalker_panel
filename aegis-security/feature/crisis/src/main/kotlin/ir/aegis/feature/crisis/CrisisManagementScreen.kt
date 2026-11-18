package ir.aegis.feature.crisis

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import ir.aegis.core.ai.LocalAiEngine
import ir.aegis.core.crypto.CryptoEngine
import ir.aegis.core.model.*
import ir.aegis.core.ui.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun CrisisManagementScreen(
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

    var searchQuery by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    var filterCategory by remember { mutableStateOf<IncidentCategory?>(null) }
    var filterPriority by remember { mutableStateOf<IncidentPriority?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedIncidentId by remember { mutableStateOf<String?>(null) }

    val incidents by repository.observeIncidents(includeArchived = showArchived).collectAsState(initial = emptyList())
    val checklists by repository.observeChecklists().collectAsState(initial = emptyList())
    val selectedIncident = incidents.firstOrNull { it.id == selectedIncidentId }
    val timeline by remember(selectedIncidentId) {
        if (selectedIncidentId != null) repository.observeIncidentTimeline(selectedIncidentId!!) else flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val attachments by remember(selectedIncidentId) {
        if (selectedIncidentId != null) repository.observeIncidentAttachments(selectedIncidentId!!) else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val filteredIncidents = remember(incidents, searchQuery, filterCategory, filterPriority) {
        incidents.filter { inc ->
            val qMatch = searchQuery.isBlank() ||
                inc.title.contains(searchQuery, ignoreCase = true) ||
                inc.description.contains(searchQuery, ignoreCase = true) ||
                inc.locationLabel.contains(searchQuery, ignoreCase = true)
            val catMatch = filterCategory == null || inc.category == filterCategory
            val prioMatch = filterPriority == null || inc.priority == filterPriority
            qMatch && catMatch && prioMatch
        }
    }

    var exportStatus by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetId = selectedIncidentId ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
                } ?: "evidence_${System.currentTimeMillis()}"
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val evDir = File(context.filesDir, "evidence").apply { mkdirs() }
                val dest = File(evDir, "${targetId}_${name.replace("[^A-Za-z0-9._-]".toRegex(), "_")}")
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                dest.writeBytes(bytes)
                val sha = CryptoEngine.sha256Hex(bytes)
                val now = System.currentTimeMillis()
                repository.addIncidentAttachment(
                    IncidentAttachment(
                        id = "att-${UUID.randomUUID().toString().take(8)}",
                        incidentId = targetId,
                        displayName = name,
                        mimeType = mime,
                        localFilePath = dest.absolutePath,
                        sha256Checksum = sha,
                        sizeBytes = bytes.size.toLong(),
                        createdAtMillis = now,
                    ),
                )
                repository.addTimelineEvent(
                    IncidentTimelineEvent(
                        id = "tl-${UUID.randomUUID().toString().take(8)}",
                        incidentId = targetId,
                        eventType = "EVIDENCE_ATTACHED",
                        summary = "Attached local file '$name' (SHA256 ${sha.take(10)}...)",
                        createdAtMillis = now,
                    ),
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            module = AegisModule.CRISIS,
            onBack = onBack,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            showBackButton = showBackButton,
        )

        GlassTabRow(
            tabs = listOf(
                tr("Incidents (${filteredIncidents.size})", "رخدادها (${filteredIncidents.size})"),
                tr("Register New", "ثبت رخداد"),
                tr("Dossier & Evidence", "پرونده و شواهد"),
                tr("Checklists", "چک‌لیست‌ها"),
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )
        Spacer(Modifier.height(10.dp))

        when (selectedTab) {
            0 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        ObsidianGlassCard {
                            GlassTextField(searchQuery, { searchQuery = it }, tr("Search incidents, locations, keywords...", "جستجوی رخدادها..."))
                            Spacer(Modifier.height(8.dp))
                            ChipFlowGroup {
                                AegisFilterPill(
                                    label = tr("Include Archive", "نمایش آرشیو"),
                                    selected = showArchived,
                                    onClick = { showArchived = !showArchived },
                                )
                                AegisFilterPill(
                                    label = tr("Critical Only", "فقط بحرانی"),
                                    selected = filterPriority == IncidentPriority.CRITICAL,
                                    onClick = {
                                        filterPriority = if (filterPriority == IncidentPriority.CRITICAL) null else IncidentPriority.CRITICAL
                                    },
                                    accent = colors.accentCrimson,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            ChipFlowGroup {
                                AegisFilterPill(
                                    label = tr("All", "همه"),
                                    selected = filterCategory == null,
                                    onClick = { filterCategory = null },
                                )
                                listOf(
                                    IncidentCategory.FIRE,
                                    IncidentCategory.EARTHQUAKE,
                                    IncidentCategory.FLOOD,
                                    IncidentCategory.MEDICAL,
                                    IncidentCategory.SECURITY_THREAT,
                                ).forEach { cat ->
                                    AegisFilterPill(
                                        label = categoryLabel(cat, lang),
                                        selected = filterCategory == cat,
                                        onClick = { filterCategory = if (filterCategory == cat) null else cat },
                                    )
                                }
                            }
                        }
                    }

                    items(filteredIncidents, key = { it.id }) { inc ->
                        val prioColor = when (inc.priority) {
                            IncidentPriority.CRITICAL -> colors.accentCrimson
                            IncidentPriority.HIGH -> colors.accentAmber
                            IncidentPriority.MEDIUM -> colors.accentCyan
                            IncidentPriority.LOW -> colors.accentTeal
                        }
                        ObsidianGlassCard(
                            accentBorderColor = prioColor,
                            onClick = {
                                selectedIncidentId = inc.id
                                selectedTab = 2
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(inc.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = prioColor.copy(alpha = 0.16f),
                                ) {
                                    Text(
                                        priorityLabel(inc.priority, lang),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = prioColor,
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(inc.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 2)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${categoryLabel(inc.category, lang)} · ${statusLabel(inc.status, lang)} · ${inc.locationLabel.ifBlank { tr("No GPS", "بدون مختصات") }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                            )
                        }
                    }
                }
            }

            1 -> {
                var title by remember { mutableStateOf("") }
                var desc by remember { mutableStateOf("") }
                var category by remember { mutableStateOf(IncidentCategory.SECURITY_THREAT) }
                var priority by remember { mutableStateOf(IncidentPriority.MEDIUM) }
                var locLabel by remember { mutableStateOf("") }
                var latStr by remember { mutableStateOf("35.7219") }
                var lonStr by remember { mutableStateOf("51.3347") }

                val aiClass = remember(title, desc) {
                    if (title.length + desc.length >= 6) LocalAiEngine.classifyIncident(title, desc) else null
                }
                val dupCandidates = remember(title, desc, category, incidents) {
                    if (title.length >= 4) {
                        LocalAiEngine.findPotentialDuplicates(
                            title = title,
                            description = desc,
                            category = category,
                            latitude = latStr.toDoubleOrNull(),
                            longitude = lonStr.toDoubleOrNull(),
                            existingIncidents = incidents,
                        )
                    } else emptyList()
                }

                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        ObsidianGlassCard(elevated = true) {
                            Text(tr("Register Crisis Incident", "ثبت رخداد بحرانی جدید"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(title, { title = it }, tr("Incident Title", "عنوان رخداد"))
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(desc, { desc = it }, tr("Detailed Description", "شرح دقیق"), singleLine = false, minLines = 3)
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(locLabel, { locLabel = it }, tr("Location Label", "نام مکان"))
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassTextField(latStr, { latStr = it }, tr("Latitude", "عرض جغرافیایی"), modifier = Modifier.weight(1f))
                                GlassTextField(lonStr, { lonStr = it }, tr("Longitude", "طول جغرافیایی"), modifier = Modifier.weight(1f))
                            }

                            if (aiClass != null) {
                                Spacer(Modifier.height(10.dp))
                                Surface(shape = RoundedCornerShape(12.dp), color = colors.accentAmber.copy(alpha = 0.14f)) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            tr("AI Suggestion (${aiClass.confidencePercent}%): ${aiClass.suggestedCategory.name} / ${aiClass.suggestedPriority.name}", "پیشنهاد هوش مصنوعی: ${aiClass.suggestedCategory.name} / ${aiClass.suggestedPriority.name}"),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.accentAmber,
                                        )
                                        Text(aiClass.matchedSignals.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                        TextButton(
                                            onClick = {
                                                category = aiClass.suggestedCategory
                                                priority = aiClass.suggestedPriority
                                            },
                                        ) { Text(tr("Apply AI Classification", "اعمال پیشنهاد")) }
                                    }
                                }
                            }

                            if (dupCandidates.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Surface(shape = RoundedCornerShape(12.dp), color = colors.accentCrimson.copy(alpha = 0.14f)) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(tr("Potential Duplicate Reports Detected Offline:", "گزارش مشابه یافت شد:"), style = MaterialTheme.typography.labelMedium, color = colors.accentCrimson)
                                        dupCandidates.forEach { d ->
                                            Text("• ${d.incident.title} (${d.similarityPercent}% match)", style = MaterialTheme.typography.labelSmall, color = colors.textPrimary)
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Text(tr("Category", "دسته‌بندی"), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                            Spacer(Modifier.height(4.dp))
                            ChipFlowGroup {
                                listOf(IncidentCategory.FIRE, IncidentCategory.EARTHQUAKE, IncidentCategory.FLOOD, IncidentCategory.MEDICAL, IncidentCategory.SECURITY_THREAT).forEach { cat ->
                                    AegisFilterPill(
                                        label = categoryLabel(cat, lang),
                                        selected = category == cat,
                                        onClick = { category = cat },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(tr("Priority", "اولویت"), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                            Spacer(Modifier.height(4.dp))
                            ChipFlowGroup {
                                IncidentPriority.entries.forEach { p ->
                                    AegisFilterPill(
                                        label = priorityLabel(p, lang),
                                        selected = priority == p,
                                        onClick = { priority = p },
                                        accent = if (p == IncidentPriority.CRITICAL) colors.accentCrimson else colors.accentCyan,
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            GlassButton(
                                label = tr("Create Incident + Auto Checklist", "ثبت رخداد و چک‌لیست"),
                                accent = colors.accentCyan,
                                enabled = title.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        val now = System.currentTimeMillis()
                                        val incId = "inc-${UUID.randomUUID().toString().take(8)}"
                                        val inc = Incident(
                                            id = incId,
                                            title = title.trim(),
                                            description = desc.trim(),
                                            category = category,
                                            priority = priority,
                                            status = IncidentStatus.OPEN,
                                            latitude = latStr.toDoubleOrNull(),
                                            longitude = lonStr.toDoubleOrNull(),
                                            locationLabel = locLabel.trim(),
                                            duplicateOfId = dupCandidates.firstOrNull()?.incident?.id,
                                            aiSuggestedCategory = aiClass?.suggestedCategory?.name,
                                            createdAtMillis = now,
                                            updatedAtMillis = now,
                                        )
                                        val genPair = LocalAiEngine.generateChecklist(
                                            category = category,
                                            familyMembers = emptyList(),
                                            inventoryItems = emptyList(),
                                            checklistId = "cl-${UUID.randomUUID().toString().take(8)}",
                                            nowMillis = now,
                                        )
                                        repository.createIncident(inc, initialNote = "Incident logged (${priority.name} ${category.name})")
                                        repository.upsertChecklistWithItems(
                                            checklist = genPair.first.copy(title = "Response: ${title.trim()}", linkedIncidentId = incId),
                                            items = genPair.second,
                                        )
                                        repository.logActivity("INCIDENT_CREATE", "Crisis Management", "Logged incident ${inc.title}")
                                        selectedIncidentId = incId
                                        title = ""; desc = ""; locLabel = ""
                                        selectedTab = 2
                                    }
                                },
                            )
                        }
                    }
                }
            }

            2 -> {
                val target = selectedIncident ?: incidents.firstOrNull()
                if (target == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(tr("Select or create an incident first.", "ابتدا یک رخداد را انتخاب یا ثبت کنید."), color = colors.textSecondary)
                    }
                } else {
                    if (selectedIncidentId == null) selectedIncidentId = target.id
                    var timelineNote by remember { mutableStateOf("") }
                    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            ObsidianGlassCard(elevated = true) {
                                Text(target.title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                                Text("${categoryLabel(target.category, lang)} · ${priorityLabel(target.priority, lang)} · ${statusLabel(target.status, lang)}", style = MaterialTheme.typography.labelMedium, color = colors.accentCyan)
                                Spacer(Modifier.height(6.dp))
                                Text(target.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                                Spacer(Modifier.height(10.dp))

                                Text(tr("Update Status:", "تغییر وضعیت:"), style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                                Spacer(Modifier.height(4.dp))
                                ChipFlowGroup {
                                    listOf(IncidentStatus.OPEN, IncidentStatus.INVESTIGATING, IncidentStatus.CONTAINED, IncidentStatus.RESOLVED).forEach { st ->
                                        AegisFilterPill(
                                            label = statusLabel(st, lang),
                                            selected = target.status == st,
                                            onClick = {
                                                scope.launch {
                                                    val now = System.currentTimeMillis()
                                                    repository.updateIncident(target.copy(status = st, updatedAtMillis = now), timelineNote = "Status updated to ${st.name}")
                                                }
                                            },
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))
                                ChipFlowGroup {
                                    GlassButton(
                                        label = if (target.isArchived) tr("Restore", "بازیابی") else tr("Archive", "آرشیو"),
                                        accent = colors.accentAmber,
                                        onClick = {
                                            scope.launch {
                                                repository.updateIncident(
                                                    target.copy(isArchived = !target.isArchived, updatedAtMillis = System.currentTimeMillis()),
                                                    timelineNote = if (target.isArchived) "Restored from archive" else "Moved to archive",
                                                )
                                            }
                                        },
                                    )
                                    GlassButton(
                                        label = tr("Export PDF Dossier", "خروجی PDF"),
                                        accent = colors.accentTeal,
                                        onClick = {
                                            val pdf = LocalFileExporter.exportPdfReport(
                                                context = context,
                                                fileNamePrefix = "crisis_${target.id}",
                                                title = "AegisGuard Incident Report: ${target.title}",
                                                subtitle = "Generated offline on ${fmt.format(Date())} | ${target.category} | ${target.priority}",
                                                sections = listOf(
                                                    "Incident Details" to listOf(
                                                        "Status: ${target.status}",
                                                        "Location: ${target.locationLabel} (${target.latitude ?: "?"}, ${target.longitude ?: "?"})",
                                                        "Description: ${target.description}",
                                                    ),
                                                    "Timeline" to timeline.map { "${fmt.format(Date(it.createdAtMillis))} [${it.eventType}] ${it.summary}" },
                                                    "Evidence Files (SHA-256 Verified)" to attachments.map { "${it.displayName} (${it.sizeBytes}B) SHA256=${it.sha256Checksum}" },
                                                ),
                                            )
                                            exportStatus = "PDF saved: ${pdf.name}"
                                            LocalFileExporter.shareFile(context, pdf, "application/pdf", "Share Incident PDF")
                                        },
                                    )
                                }
                                if (exportStatus != null) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(exportStatus!!, style = MaterialTheme.typography.labelSmall, color = colors.accentEmerald)
                                }
                            }
                        }

                        item {
                            ObsidianGlassCard {
                                Text(tr("Evidence Attachments (${attachments.size})", "فایل‌های شواهد (${attachments.size})"), style = MaterialTheme.typography.titleMedium, color = colors.accentViolet)
                                Spacer(Modifier.height(8.dp))
                                ChipFlowGroup {
                                    GlassButton(
                                        label = tr("Attach Device File", "پیوست فایل"),
                                        accent = colors.accentViolet,
                                        onClick = { filePicker.launch(arrayOf("*/*")) },
                                    )
                                    GlassButton(
                                        label = tr("Snapshot Field Log", "ثبت فایل متنی"),
                                        onClick = {
                                            scope.launch {
                                                val evDir = File(context.filesDir, "evidence").apply { mkdirs() }
                                                val f = File(evDir, "${target.id}_snapshot_${System.currentTimeMillis()}.txt")
                                                val content = "Incident: ${target.title}\nStatus: ${target.status}\nSummary: ${target.description}"
                                                f.writeText(content)
                                                val sha = CryptoEngine.sha256Hex(content)
                                                val now = System.currentTimeMillis()
                                                repository.addIncidentAttachment(
                                                    IncidentAttachment(
                                                        id = "att-${UUID.randomUUID().toString().take(8)}",
                                                        incidentId = target.id,
                                                        displayName = f.name,
                                                        mimeType = "text/plain",
                                                        localFilePath = f.absolutePath,
                                                        sha256Checksum = sha,
                                                        sizeBytes = content.length.toLong(),
                                                        createdAtMillis = now,
                                                    ),
                                                )
                                            }
                                        },
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                attachments.forEach { att ->
                                    Text("• ${att.displayName} (${att.sizeBytes}B) SHA256: ${att.sha256Checksum.take(14)}...", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                }
                            }
                        }

                        item {
                            ObsidianGlassCard {
                                Text(tr("Incident Timeline", "خط زمانی رخداد"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    GlassTextField(timelineNote, { timelineNote = it }, tr("Add Timeline Field Event", "ثبت رویداد جدید"), modifier = Modifier.weight(1f))
                                    GlassButton(
                                        label = tr("Add", "افزودن"),
                                        enabled = timelineNote.isNotBlank(),
                                        onClick = {
                                            scope.launch {
                                                repository.addTimelineEvent(
                                                    IncidentTimelineEvent(
                                                        id = "tl-${UUID.randomUUID().toString().take(8)}",
                                                        incidentId = target.id,
                                                        eventType = "FIELD_UPDATE",
                                                        summary = timelineNote.trim(),
                                                        createdAtMillis = System.currentTimeMillis(),
                                                    ),
                                                )
                                                timelineNote = ""
                                            }
                                        },
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                timeline.forEach { ev ->
                                    Text("${fmt.format(Date(ev.createdAtMillis))} · [${ev.eventType}] ${ev.summary}", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(checklists, key = { it.checklist.id }) { bundle ->
                        val cl = bundle.checklist
                        val done = bundle.items.count { it.isChecked }
                        ObsidianGlassCard {
                            Text(cl.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Text("${cl.category} · $done / ${bundle.items.size} completed ${if (cl.isAiGenerated) "· AI-Assisted Draft" else ""}", style = MaterialTheme.typography.labelSmall, color = colors.accentTeal)
                            Spacer(Modifier.height(6.dp))
                            bundle.items.forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = item.isChecked,
                                        onCheckedChange = { checked ->
                                            scope.launch { repository.toggleChecklistItem(item.id, checked) }
                                        },
                                    )
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
