package ir.aegis.feature.ai

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ir.aegis.core.ai.AiAssistantResponse
import ir.aegis.core.ai.LocalAiEngine
import ir.aegis.core.crypto.CryptoEngine
import ir.aegis.core.model.*
import ir.aegis.core.ui.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
fun LocalAiScreen(
    repository: AegisRepository,
    preferences: AppPreferences,
    onModelImported: (String, String, String) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalObsidianColors.current
    val lang = LocalAppLanguage.current
    val isFa = lang == AppLanguage.FA
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val incidents by repository.observeIncidents(includeArchived = false).collectAsState(initial = emptyList())
    val family by repository.observeFamilyMembers().collectAsState(initial = emptyList())
    val inventory by repository.observeInventoryItems().collectAsState(initial = emptyList())
    val academyProgress by repository.observeAcademyProgress().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) }
    var assistantQuery by remember { mutableStateOf("") }
    var assistantReply by remember { mutableStateOf<AiAssistantResponse?>(null) }
    var kbQuery by remember { mutableStateOf("") }
    var draftText by remember { mutableStateOf("Thick smoke inside stairwell on floor 3, two neighbors coughing") }
    var checklistCategory by remember { mutableStateOf(IncidentCategory.EARTHQUAKE) }
    var checklistSavedNotice by remember { mutableStateOf<String?>(null) }

    val modelsDir = remember { File(context.filesDir, "models").apply { mkdirs() } }
    var localModelFiles by remember { mutableStateOf(modelsDir.listFiles()?.toList().orEmpty()) }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
                } ?: "local_model_${System.currentTimeMillis()}.gguf"
                val dest = File(modelsDir, name.replace("[^A-Za-z0-9._-]".toRegex(), "_"))
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { out -> input.copyTo(out) }
                }
                val sha = CryptoEngine.sha256Hex(dest.readBytes())
                localModelFiles = modelsDir.listFiles()?.toList().orEmpty()
                onModelImported(dest.absolutePath, dest.name, sha)
                repository.logActivity("LOCAL_MODEL_IMPORT", "Local AI", "Imported on-device model file ${dest.name}")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            module = AegisModule.AI,
            onBack = onBack,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            extraBadge = "ON-DEVICE NLP & BM25 · ZERO NETWORK",
        )
        GlassTabRow(
            tabs = listOf(
                tr("Assistant", "دستیار"),
                tr("KB Search", "جستجوی دانش"),
                tr("Analyze & Tools", "تحلیل و ابزارها"),
                tr("Stats & SLM", "آمار و مدل محلی"),
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )
        Spacer(Modifier.height(10.dp))

        when (selectedTab) {
            0 -> {
                LazyColumn(
                    modifier = Modifier.testTag("ai_assistant_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        AiSafetyBanner(
                            text = tr(
                                "Outputs below are generated offline from local heuristics & reviewed guides. They are suggestions ONLY — verify critical actions with reviewed guides or local emergency services.",
                                "خروجی‌های زیر به‌صورت آفلاین تولید می‌شوند و صرفاً پیشنهاد هستند؛ برای اقدامات حیاتی به راهنماهای بازبینی‌شده و نیروهای امدادی مراجعه کنید.",
                            ),
                        )
                    }
                    item {
                        ObsidianGlassCard(elevated = true) {
                            Text(tr("Ask Offline Assistant", "پرسش از دستیار آفلاین"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(assistantQuery, { assistantQuery = it }, tr("Ask about bleeding, earthquake, evacuation, fire...", "سوال خود را بنویسید..."))
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("earthquake", "bleeding", "fire", "flood").forEach { preset ->
                                    AssistChip(
                                        onClick = {
                                            assistantQuery = preset
                                            assistantReply = LocalAiEngine.answerQuery(preset, preferences.localModelName, isFa)
                                        },
                                        label = { Text(preset) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            GlassButton(
                                label = tr("Run Local Inference", "تحلیل آفلاین"),
                                enabled = assistantQuery.isNotBlank(),
                                onClick = {
                                    assistantReply = LocalAiEngine.answerQuery(assistantQuery, preferences.localModelName, isFa)
                                },
                            )
                        }
                    }
                    assistantReply?.let { reply ->
                        item {
                            ObsidianGlassCard(accentBorderColor = colors.accentTeal) {
                                Text("${tr("Suggestion Summary", "خلاصه پیشنهادی")} (${reply.confidencePercent}%)", style = MaterialTheme.typography.titleMedium, color = colors.accentTeal)
                                Text(reply.engineLabel, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                Spacer(Modifier.height(4.dp))
                                Text(reply.safetyDisclaimer, style = MaterialTheme.typography.labelSmall, color = colors.accentAmber)
                                Spacer(Modifier.height(6.dp))
                                Text(reply.summaryAnswer, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                                if (reply.suggestedSteps.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(tr("Suggested Action Steps:", "مراحل پیشنهادی:"), style = MaterialTheme.typography.labelMedium, color = colors.accentCyan)
                                    reply.suggestedSteps.forEachIndexed { idx, s ->
                                        Text("${idx + 1}. $s", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                                    }
                                }
                                if (reply.citedSources.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(tr("Referenced Reviewed Protocols:", "پروتکل‌های بازبینی‌شده مرتبط:"), style = MaterialTheme.typography.labelMedium, color = colors.accentEmerald)
                                    reply.citedSources.forEach { src ->
                                        Text("• $src", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                val hits = remember(kbQuery) { LocalAiEngine.searchKnowledge(kbQuery, limit = 8) }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        ObsidianGlassCard {
                            GlassTextField(kbQuery, { kbQuery = it }, tr("BM25 Search Reviewed Guides (EN / FA keywords)...", "جستجو در کتابخانه دانش..."))
                        }
                    }
                    items(hits, key = { it.doc.id }) { hit ->
                        ObsidianGlassCard {
                            Text("${hit.doc.title}  [Score ${"%.2f".format(hit.score)}]", style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                            Text("${hit.doc.domain} · ${hit.doc.reviewedSource}", style = MaterialTheme.typography.labelSmall, color = colors.accentEmerald)
                            Spacer(Modifier.height(6.dp))
                            hit.doc.actionChecklist.forEachIndexed { idx, step ->
                                Text("${idx + 1}. $step", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            }
                        }
                    }
                }
            }

            2 -> {
                val cls = remember(draftText, isFa) { LocalAiEngine.classifyIncident(draftText, "", isFa) }
                val summary = remember(draftText) { LocalAiEngine.summarizeText("Draft Incident", draftText, maxSentences = 2) }
                val duplicates = remember(draftText, cls, incidents) {
                    LocalAiEngine.findPotentialDuplicates(draftText, "", cls.suggestedCategory, null, null, incidents)
                }
                val genChecklistPair = remember(checklistCategory, family, inventory) {
                    LocalAiEngine.generateChecklist(checklistCategory, family, inventory)
                }

                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        ObsidianGlassCard {
                            Text(tr("Classifier, Summarizer & Duplicate Detector", "دسته‌بند، خلاصه‌ساز و تشخیص تکرار"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(draftText, { draftText = it }, tr("Paste Incident Report Text", "متن گزارش را وارد کنید"), singleLine = false, minLines = 3)
                            Spacer(Modifier.height(8.dp))
                            Text("Suggested: ${cls.suggestedCategory.name} / ${cls.suggestedPriority.name} (${cls.confidencePercent}%)", style = MaterialTheme.typography.labelLarge, color = colors.accentAmber)
                            Text("Signals: ${cls.matchedSignals.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                            Spacer(Modifier.height(6.dp))
                            Text("Extractive Summary: $summary", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (duplicates.isEmpty()) "Duplicate Scan: No similar active reports in Room."
                                else "Potential duplicates: ${duplicates.joinToString { "${it.incident.title} (${it.similarityPercent}%)" }}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (duplicates.isEmpty()) colors.accentEmerald else colors.accentCrimson,
                            )
                        }
                    }

                    item {
                        ObsidianGlassCard {
                            Text(tr("Emergency Checklist Generator (Draft Suggestion)", "تولیدگر چک‌لیست اضطراری"), style = MaterialTheme.typography.titleMedium, color = colors.accentTeal)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(IncidentCategory.EARTHQUAKE, IncidentCategory.FIRE, IncidentCategory.FLOOD, IncidentCategory.MEDICAL).forEach { cat ->
                                    FilterChip(selected = checklistCategory == cat, onClick = { checklistCategory = cat }, label = { Text(cat.name, style = MaterialTheme.typography.labelSmall) })
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            genChecklistPair.second.forEachIndexed { idx, line ->
                                Text("${idx + 1}. ${line.label}", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            }
                            Spacer(Modifier.height(8.dp))
                            GlassButton(
                                label = tr("Save Generated Checklist to Room", "ذخیره چک‌لیست"),
                                accent = colors.accentTeal,
                                onClick = {
                                    scope.launch {
                                        val id = "cl-ai-${UUID.randomUUID().toString().take(8)}"
                                        val finalPair = LocalAiEngine.generateChecklist(checklistCategory, family, inventory, checklistId = id)
                                        repository.upsertChecklistWithItems(finalPair.first, finalPair.second)
                                        checklistSavedNotice = "Saved to Crisis Checklists."
                                    }
                                },
                            )
                            if (checklistSavedNotice != null) {
                                Text(checklistSavedNotice!!, style = MaterialTheme.typography.labelSmall, color = colors.accentEmerald)
                            }
                        }
                    }
                }
            }

            3 -> {
                val report = remember(incidents, inventory, family, academyProgress, isFa) {
                    LocalAiEngine.analyzeStatistics(incidents, inventory, family, academyProgress, isPersian = isFa)
                }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        ObsidianGlassCard(elevated = true) {
                            Text(tr("Local Statistical Risk & Trend Analysis", "تحلیل آماری محلی داده‌ها"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                            Spacer(Modifier.height(6.dp))
                            Text(report.safetyDisclaimer, style = MaterialTheme.typography.labelSmall, color = colors.accentAmber)
                            Spacer(Modifier.height(6.dp))
                            Text("Total: ${report.totalIncidents} · Open: ${report.openCount} · Critical: ${report.criticalCount} · Resolution: ${report.resolutionRatePercent}%", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                            Text("Preparedness Index: ${report.preparednessIndex}% · Top Category: ${report.topCategory?.name ?: "None"}", style = MaterialTheme.typography.bodyMedium, color = colors.accentTeal)
                            Spacer(Modifier.height(6.dp))
                            report.insights.forEach { line ->
                                Text("• $line", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            }
                        }
                    }

                    item {
                        ObsidianGlassCard {
                            Text(tr("Optional On-Device Local Model (SLM Pack)", "مدل زبانی اختیاری روی دستگاه"), style = MaterialTheme.typography.titleMedium, color = colors.accentViolet)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                tr("Import an offline GGUF/TFLite model from local storage (zero downloads). Built-in BM25 & deterministic NLP always run reliably without an external model.", "می‌توانید فایل مدل آفلاین (.gguf/.tflite) را از حافظه دستگاه وارد کنید. موتور محلی بدون مدل خارجی نیز کاملاً فعال است."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                            if (preferences.localModelName.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text("Active Model: ${preferences.localModelName} (${preferences.localModelSha256.take(12)}...)", style = MaterialTheme.typography.labelMedium, color = colors.accentEmerald)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassButton(
                                    label = tr("Import Local Model (.gguf / .bin)", "انتخاب فایل مدل آفلاین"),
                                    accent = colors.accentViolet,
                                    onClick = { modelPicker.launch(arrayOf("*/*")) },
                                )
                                if (preferences.localModelName.isNotBlank()) {
                                    GlassButton(
                                        label = tr("Clear Model", "حذف مدل"),
                                        accent = colors.accentAmber,
                                        onClick = { onModelImported("", "", "") },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            if (localModelFiles.isEmpty()) {
                                Text(tr("No external SLM file imported yet (Built-in deterministic engine active).", "مدل خارجی نصب نشده — موتور هوشمند داخلی فعال است."), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                            } else {
                                localModelFiles.forEach { mf ->
                                    Text("• ${mf.name} (${mf.length() / 1024} KB)", style = MaterialTheme.typography.labelSmall, color = colors.accentEmerald)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
