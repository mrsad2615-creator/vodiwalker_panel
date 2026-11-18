package ir.aegis.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.aegis.core.ai.KnowledgeSearchHit
import ir.aegis.core.ai.LocalAiEngine
import ir.aegis.core.model.*
import ir.aegis.core.ui.*

@Composable
fun CommandHomeScreen(
    repository: AegisRepository,
    preferences: AppPreferences,
    onOpenModule: (AegisModule) -> Unit,
    onToggleTheme: () -> Unit,
    onToggleLanguage: () -> Unit,
    onToggleWidget: (DashboardWidgetId) -> Unit,
) {
    val colors = LocalObsidianColors.current
    val lang = LocalAppLanguage.current

    val incidents by repository.observeIncidents(includeArchived = false).collectAsState(initial = emptyList())
    val family by repository.observeFamilyMembers().collectAsState(initial = emptyList())
    val checklists by repository.observeChecklists().collectAsState(initial = emptyList())
    val inventory by repository.observeInventoryItems().collectAsState(initial = emptyList())
    val reminders by repository.observeReminders().collectAsState(initial = emptyList())
    val academyProgress by repository.observeAcademyProgress().collectAsState(initial = emptyList())
    val mapMarkers by repository.observeMapMarkers().collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var customizeWidgetsOpen by remember { mutableStateOf(false) }

    val readinessScore = remember(checklists, family, academyProgress, preferences, inventory) {
        val allChecklistItems = checklists.flatMap { it.items }
        val clPart = if (allChecklistItems.isNotEmpty()) (allChecklistItems.count { it.isChecked } * 30) / allChecklistItems.size else 15
        val famPart = if (family.isNotEmpty()) (family.count { it.checkInStatus == FamilyCheckInStatus.SAFE } * 20) / family.size else 10
        val acaPart = (academyProgress.count { it.badgeEarned } * 5).coerceAtMost(20)
        val invPart = if (inventory.isNotEmpty()) (inventory.count { it.quantity >= it.minimumThreshold } * 15) / inventory.size else 10
        val secPart = if (preferences.pinEnabled) 15 else 8
        (clPart + famPart + acaPart + invPart + secPart).coerceIn(10, 100)
    }

    val allModules = remember { AegisModule.entries.filter { it != AegisModule.HOME } }
    val favoriteModules = remember(preferences.favoriteModules) {
        allModules.filter { it in preferences.favoriteModules }
    }

    val searchMatches = remember(searchQuery, incidents, lang) {
        if (searchQuery.isBlank()) {
            Triple(emptyList<AegisModule>(), emptyList<Incident>(), emptyList<KnowledgeSearchHit>())
        } else {
            val modHits = allModules.filter {
                moduleTitle(it, lang).contains(searchQuery, ignoreCase = true) ||
                    moduleSubtitle(it, lang).contains(searchQuery, ignoreCase = true)
            }
            val incHits = incidents.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true)
            }.take(4)
            val kbHits = LocalAiEngine.searchKnowledge(searchQuery, limit = 3)
            Triple(modHits, incHits, kbHits)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("home_lazy_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Clean Top Executive Bar (Zero Crowding)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "AegisGuard",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.textPrimary,
                    )
                    OfflineStatusPill()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        onClick = onToggleTheme,
                        shape = RoundedCornerShape(12.dp),
                        color = colors.glassElevated,
                        border = BorderStroke(1.dp, colors.glassRimEnd),
                    ) {
                        Text(
                            text = when (preferences.themeMode) {
                                ThemeMode.DARK -> "☾"
                                ThemeMode.LIGHT -> "☀"
                                ThemeMode.SYSTEM -> "◐"
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            color = colors.accentCyan,
                        )
                    }
                    Surface(
                        onClick = onToggleLanguage,
                        shape = RoundedCornerShape(12.dp),
                        color = colors.glassElevated,
                        border = BorderStroke(1.dp, colors.glassRimEnd),
                    ) {
                        Text(
                            text = if (lang == AppLanguage.FA) "EN" else "فا",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accentTeal,
                        )
                    }
                }
            }
        }

        // 2. Global Offline Search
        item {
            GlassTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = tr("Search modules, incidents, offline guides...", "جستجوی سراسری ماژول‌ها، رخدادها، راهنماها..."),
            )
        }

        if (searchQuery.isNotBlank()) {
            item {
                ObsidianGlassCard(elevated = true) {
                    Text(tr("Offline Global Search Results", "نتایج جستجوی سراسری آفلاین"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                    Spacer(Modifier.height(6.dp))
                    searchMatches.first.forEach { mod ->
                        TextButton(onClick = { onOpenModule(mod) }) { Text("${tr("Module", "ماژول")}: ${moduleTitle(mod, lang)}") }
                    }
                    searchMatches.second.forEach { inc ->
                        TextButton(onClick = { onOpenModule(AegisModule.CRISIS) }) {
                            Text("${tr("Incident", "رخداد")}: ${inc.title} (${priorityLabel(inc.priority, lang)})")
                        }
                    }
                    searchMatches.third.forEach { hit ->
                        TextButton(onClick = { onOpenModule(AegisModule.AI) }) { Text("${tr("Guide", "راهنما")}: ${hit.doc.title}") }
                    }
                    if (searchMatches.first.isEmpty() && searchMatches.second.isEmpty() && searchMatches.third.isEmpty()) {
                        Text(tr("No offline matches found.", "موردی یافت نشد."), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                    }
                }
            }
        }

        // 3. Spacious Hero Security Readiness Card
        if (DashboardWidgetId.PREPAREDNESS_SCORE !in preferences.disabledWidgets) {
            item {
                ObsidianGlassCard(elevated = true, accentBorderColor = colors.accentCyan) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Hero3DSecurityShield(
                            readinessScore = readinessScore,
                            activeAlertsCount = incidents.count { it.priority == IncidentPriority.CRITICAL && it.status != IncidentStatus.RESOLVED },
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = tr("Offline Readiness Index", "شاخص آمادگی آفلاین"),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "${incidents.count { it.status != IncidentStatus.RESOLVED }} ${tr("active alerts", "هشدار فعال")} · ${family.count { it.checkInStatus == FamilyCheckInStatus.SAFE }}/${family.size} ${tr("family safe", "خانواده امن")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                            Text(
                                text = "${mapMarkers.size} ${tr("offline shelters & POIs ready", "پناهگاه و نقطه امدادی آماده")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accentTeal,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ChipFlowGroup {
                        GlassButton(
                            label = tr("Emergency SOS Center", "مرکز اضطراری SOS"),
                            accent = colors.accentCrimson,
                            onClick = { onOpenModule(AegisModule.EMERGENCY) },
                        )
                        GlassButton(
                            label = tr("Open Offline Map", "باز کردن نقشه آفلاین"),
                            accent = colors.accentTeal,
                            onClick = { onOpenModule(AegisModule.MAPS) },
                        )
                    }
                }
            }
        }

        // 4. Quick Launch Row (Preserved E2E testTag & crisp labels)
        item {
            LazyRow(
                modifier = Modifier.testTag("quick_actions_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { GlassButton(label = tr("Quick SOS", "SOS فوری"), accent = colors.accentCrimson, onClick = { onOpenModule(AegisModule.EMERGENCY) }) }
                item { GlassButton(label = tr("Offline Map", "نقشه آفلاین"), accent = colors.accentTeal, onClick = { onOpenModule(AegisModule.MAPS) }) }
                item { GlassButton(label = tr("Log Incident", "ثبت رخداد"), accent = colors.accentAmber, onClick = { onOpenModule(AegisModule.CRISIS) }) }
                item { GlassButton(label = tr("Local AI", "هوش محلی"), accent = colors.accentViolet, onClick = { onOpenModule(AegisModule.AI) }) }
                item { GlassButton(label = tr("Torch & Tools", "ابزارها"), accent = colors.accentCyan, onClick = { onOpenModule(AegisModule.UTILITIES) }) }
            }
        }

        // 5. Pinned Favorites
        if (favoriteModules.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("Pinned Favorites ★", "علاقه‌مندی‌ها ★"), style = MaterialTheme.typography.titleMedium, color = colors.accentAmber)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(favoriteModules, key = { it.name }) { mod ->
                            ObsidianGlassCard(
                                cornerRadius = 14.dp,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                onClick = { onOpenModule(mod) },
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AegisModuleIcon(module = mod, tint = colors.accentAmber, size = 18.dp)
                                    Text(moduleTitle(mod, lang), style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Live Offline Widgets (100% Bilingual FA + EN)
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Live Offline Widgets", "ویجت‌های زنده آفلاین"), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                TextButton(onClick = { customizeWidgetsOpen = !customizeWidgetsOpen }) {
                    Text(if (customizeWidgetsOpen) tr("Done", "بستن") else tr("Customize", "شخصی‌سازی"))
                }
            }
            if (customizeWidgetsOpen) {
                ObsidianGlassCard {
                    Text(tr("Toggle Dashboard Widgets:", "انتخاب ویجت‌های صفحه اصلی:"), style = MaterialTheme.typography.labelMedium, color = colors.accentCyan)
                    Spacer(Modifier.height(6.dp))
                    DashboardWidgetId.entries.forEach { w ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = w !in preferences.disabledWidgets, onCheckedChange = { onToggleWidget(w) })
                            Text(widgetLabel(w, lang), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (DashboardWidgetId.CRISIS_PULSE !in preferences.disabledWidgets) {
                    val topInc = incidents.firstOrNull { it.status != IncidentStatus.RESOLVED }
                    ObsidianGlassCard(onClick = { onOpenModule(AegisModule.CRISIS) }) {
                        Text(tr("Active Crisis Feed", "وضعیت بحران‌های فعال"), style = MaterialTheme.typography.labelMedium, color = colors.accentCrimson)
                        Text(
                            topInc?.let { "${it.title} (${priorityLabel(it.priority, lang)} · ${statusLabel(it.status, lang)})" }
                                ?: tr("No open incidents — stay vigilant.", "هیچ بحران بازی ثبت نشده است."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                    }
                }
                if (DashboardWidgetId.FAMILY_CHECKIN !in preferences.disabledWidgets) {
                    ObsidianGlassCard(onClick = { onOpenModule(AegisModule.FAMILY) }) {
                        Text(tr("Family Check-In", "وضعیت خانواده"), style = MaterialTheme.typography.labelMedium, color = colors.accentTeal)
                        Text(
                            "${family.count { it.checkInStatus == FamilyCheckInStatus.SAFE }} ${tr("Safe", "امن")} · ${family.count { it.checkInStatus != FamilyCheckInStatus.SAFE }} ${tr("Pending/Help", "در انتظار / نیازمند کمک")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                    }
                }
                if (DashboardWidgetId.REMINDERS_AND_INVENTORY !in preferences.disabledWidgets) {
                    val lowCount = inventory.count { it.quantity < it.minimumThreshold }
                    val nextRem = reminders.firstOrNull { !it.completed }
                    ObsidianGlassCard(onClick = { onOpenModule(AegisModule.UTILITIES) }) {
                        Text(tr("Supplies & Next Reminder", "تجهیزات و یادآور بعدی"), style = MaterialTheme.typography.labelMedium, color = colors.accentAmber)
                        Text(
                            "${if (lowCount > 0) tr("$lowCount low supply item(s)", "$lowCount قلم موجودی کم") else tr("All supplies stocked", "تمامی اقلام تکمیل")} · ${nextRem?.title ?: tr("No pending reminders", "یادآور فعالی وجود ندارد")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                    }
                }
                if (DashboardWidgetId.INCIDENT_SPARKLINE !in preferences.disabledWidgets) {
                    ObsidianGlassCard(onClick = { onOpenModule(AegisModule.ANALYTICS) }) {
                        Text(tr("Analytics Snapshot", "خلاصه تحلیلی"), style = MaterialTheme.typography.labelMedium, color = colors.accentViolet)
                        Text(
                            "${incidents.size} ${tr("total logged", "کل ثبت‌شده")} · ${incidents.count { it.priority == IncidentPriority.CRITICAL }} ${tr("critical", "بحرانی")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                    }
                }
            }
        }

        // 7. Full-Width Spacious Module Directory Cards (ZERO cramped 2-column clipping!)
        item {
            SectionHeader(
                title = tr("Security & Crisis Ecosystem Modules", "ماژول‌های اکوسیستم امنیت و بحران"),
                badge = "${allModules.size} ${tr("Modules", "ماژول")}",
            )
        }
        items(allModules, key = { it.name }) { mod ->
            val accent = when (mod) {
                AegisModule.EMERGENCY, AegisModule.CRISIS -> colors.accentCrimson
                AegisModule.MAPS, AegisModule.FAMILY -> colors.accentTeal
                AegisModule.AI, AegisModule.PRIVACY -> colors.accentViolet
                AegisModule.ANALYTICS, AegisModule.ACADEMY -> colors.accentAmber
                else -> colors.accentCyan
            }
            ObsidianGlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentBorderColor = accent,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                onClick = { onOpenModule(mod) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AegisModuleIcon(module = mod, tint = accent, size = 24.dp)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = moduleTitle(mod, lang),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = moduleSubtitle(mod, lang),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (mod in preferences.favoriteModules) {
                        Text("★", style = MaterialTheme.typography.titleMedium, color = colors.accentAmber)
                    }
                    Text(
                        text = if (lang == AppLanguage.FA) "‹" else "›",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }
}

private fun widgetLabel(id: DashboardWidgetId, lang: AppLanguage): String = when (id) {
    DashboardWidgetId.PREPAREDNESS_SCORE -> if (lang == AppLanguage.FA) "شاخص آمادگی آفلاین" else "Preparedness Score"
    DashboardWidgetId.CRISIS_PULSE -> if (lang == AppLanguage.FA) "وضعیت بحران‌های فعال" else "Active Crisis Feed"
    DashboardWidgetId.FAMILY_CHECKIN -> if (lang == AppLanguage.FA) "وضعیت خانواده" else "Family Check-In"
    DashboardWidgetId.REMINDERS_AND_INVENTORY -> if (lang == AppLanguage.FA) "تجهیزات و یادآورها" else "Supplies & Reminders"
    DashboardWidgetId.INCIDENT_SPARKLINE -> if (lang == AppLanguage.FA) "خلاصه تحلیلی" else "Analytics Snapshot"
}
