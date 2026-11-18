package ir.aegis.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val academyProgress by repository.observeAcademyProgress().collectAsState(initial = emptyList())
    val mapMarkers by repository.observeMapMarkers().collectAsState(initial = emptyList())
    val mapPackages by repository.observeMapPackages().collectAsState(initial = emptyList())
    val auditLogs by repository.observeAuditLogs().collectAsState(initial = emptyList())

    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val openThreatsCount = remember(incidents) { incidents.count { it.status != IncidentStatus.RESOLVED } }
    val criticalCount = remember(incidents) { incidents.count { it.priority == IncidentPriority.CRITICAL && it.status != IncidentStatus.RESOLVED } }

    val securityPostureScore = remember(checklists, academyProgress, preferences, incidents) {
        val allChecklistItems = checklists.flatMap { it.items }
        val clPart = if (allChecklistItems.isNotEmpty()) (allChecklistItems.count { it.isChecked } * 30) / allChecklistItems.size else 18
        val acaPart = (academyProgress.count { it.badgeEarned } * 5).coerceAtMost(20)
        val pinPart = if (preferences.pinEnabled) 18 else 8
        val flagPart = if (preferences.flagSecureEnabled) 15 else 5
        val openCriticalPenalty = (criticalCount * 8).coerceAtMost(20)
        (22 + clPart + acaPart + pinPart + flagPart - openCriticalPenalty).coerceIn(15, 100)
    }

    val allModules = remember {
        listOf(
            AegisModule.EMERGENCY,
            AegisModule.UTILITIES,
            AegisModule.AI,
            AegisModule.PRIVACY,
            AegisModule.PERSONAL,
            AegisModule.MAPS,
            AegisModule.CRISIS,
            AegisModule.ANALYTICS,
            AegisModule.ACADEMY,
            AegisModule.FAMILY,
        )
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Sleek Minimalist Top Bar (Vector Icons Only)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(colors.accentCyan.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = colors.accentCyan,
                        modifier = Modifier.size(25.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = tr("AegisGuard", "ایجیس‌گارد"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.textPrimary,
                    )
                    OfflineStatusPill(text = tr("100% OFFLINE", "کاملاً آفلاین"))
                }
                HeaderIconChip(
                    onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" },
                    active = searchOpen,
                ) {
                    Icon(
                        imageVector = if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                HeaderIconChip(onClick = onToggleTheme) {
                    Icon(
                        imageVector = when (preferences.themeMode) {
                            ThemeMode.DARK -> Icons.Rounded.DarkMode
                            ThemeMode.LIGHT -> Icons.Rounded.LightMode
                            ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                        },
                        contentDescription = "Theme",
                        tint = colors.accentCyan,
                        modifier = Modifier.size(20.dp),
                    )
                }
                HeaderIconChip(onClick = onToggleLanguage) {
                    Text(
                        text = if (lang == AppLanguage.FA) "EN" else "فا",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentTeal,
                    )
                }
            }
        }

        // Optional Expandable Search
        item {
            AnimatedVisibility(visible = searchOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = tr("Search modules, threats, guides...", "جستجوی ابزار، تهدید یا راهنما..."),
                    )
                    if (searchQuery.isNotBlank()) {
                        ObsidianGlassCard(elevated = true) {
                            searchMatches.first.forEach { mod ->
                                TextButton(onClick = { onOpenModule(mod) }) { Text(moduleTitle(mod, lang)) }
                            }
                            searchMatches.second.forEach { inc ->
                                TextButton(onClick = { onOpenModule(AegisModule.CRISIS) }) { Text(inc.title) }
                            }
                            searchMatches.third.forEach { hit ->
                                TextButton(onClick = { onOpenModule(AegisModule.AI) }) { Text(hit.doc.title) }
                            }
                            if (searchMatches.first.isEmpty() && searchMatches.second.isEmpty() && searchMatches.third.isEmpty()) {
                                Text(tr("No results", "موردی یافت نشد"), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                            }
                        }
                    }
                }
            }
        }

        // 2. Hero Score Card with Giant Tabular Number & Quick Audit CTA
        item {
            ObsidianGlassCard(
                elevated = true,
                accentBorderColor = if (openThreatsCount > 0) colors.accentAmber else colors.accentCyan,
                contentPadding = PaddingValues(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Hero3DSecurityShield(
                        readinessScore = securityPostureScore,
                        activeAlertsCount = criticalCount,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = tr("System Posture", "وضعیت کلی امنیت"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            MiniHeroNumber(
                                value = "$openThreatsCount",
                                label = tr("Threats", "تهدید"),
                                accent = if (openThreatsCount > 0) colors.accentCrimson else colors.accentEmerald,
                            )
                            MiniHeroNumber(
                                value = "${auditLogs.size}",
                                label = tr("Audits", "بازرسی"),
                                accent = colors.accentCyan,
                            )
                            MiniHeroNumber(
                                value = "${mapPackages.size}",
                                label = tr("Cities", "شهر"),
                                accent = colors.accentTeal,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlassButton(
                        label = tr("Anti-Hack Audit", "بازرسی ضد هک"),
                        accent = colors.accentCrimson,
                        onClick = { onOpenModule(AegisModule.EMERGENCY) },
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(
                        label = tr("Malware Scan", "آنتی‌ویروس"),
                        accent = colors.accentCyan,
                        onClick = { onOpenModule(AegisModule.UTILITIES) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 3. Large Bento Metric Tiles Row 1 (2x1)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BentoMetricTile(
                    value = "6",
                    label = tr("OS Guards · Anti-Hack", "سپر ضد هک سیستم"),
                    icon = Icons.Rounded.GppGood,
                    accent = colors.accentCrimson,
                    onClick = { onOpenModule(AegisModule.EMERGENCY) },
                    modifier = Modifier.weight(1f),
                )
                BentoMetricTile(
                    value = "${mapMarkers.size}",
                    label = tr("Offline Map · GPS", "نقطه نقشه آفلاین"),
                    icon = Icons.Rounded.Explore,
                    accent = colors.accentTeal,
                    onClick = { onOpenModule(AegisModule.MAPS) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 4. Clean Horizontal Quick Action Bar (Preserves testTag for E2E)
        item {
            LazyRow(
                modifier = Modifier.testTag("quick_actions_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    QuickNavPill(
                        label = tr("Phishing Lab", "ضد فیشینگ"),
                        icon = Icons.Rounded.AutoAwesome,
                        accent = colors.accentViolet,
                        onClick = { onOpenModule(AegisModule.AI) },
                    )
                }
                item {
                    QuickNavPill(
                        label = tr("Permissions", "دسترسی و قفل"),
                        icon = Icons.Rounded.EnhancedEncryption,
                        accent = colors.accentCyan,
                        onClick = { onOpenModule(AegisModule.PRIVACY) },
                    )
                }
                item {
                    QuickNavPill(
                        label = tr("Vault", "گاوصندوق"),
                        icon = Icons.Rounded.Key,
                        accent = colors.accentEmerald,
                        onClick = { onOpenModule(AegisModule.PERSONAL) },
                    )
                }
                item {
                    QuickNavPill(
                        label = tr("Incidents", "ثبت رخداد"),
                        icon = Icons.Rounded.ReportProblem,
                        accent = colors.accentAmber,
                        onClick = { onOpenModule(AegisModule.CRISIS) },
                    )
                }
            }
        }

        // 5. Minimalist Categorized Bento Suite (4 Clean Categories!)
        item {
            SectionHeader(
                title = tr("Security Categories", "دسته‌بندی ابزارهای امنیتی"),
                badge = "4",
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BentoCategoryBlock(
                    title = tr("Device Defense & Antivirus", "دفاع سیستم و آنتی‌ویروس"),
                    metricValue = "2",
                    metricUnit = tr("Engines", "ابزار"),
                    icon = Icons.Rounded.Security,
                    accent = colors.accentCrimson,
                    items = listOf(
                        AegisModule.EMERGENCY to tr("Anti-Hack", "بازرسی ضد هک"),
                        AegisModule.UTILITIES to tr("App Scanner", "اسکن بدافزار"),
                    ),
                    onOpenModule = onOpenModule,
                )

                BentoCategoryBlock(
                    title = tr("Identity, Privacy & Vault", "حریم خصوصی، فیشینگ و رمز"),
                    metricValue = "AES",
                    metricUnit = "256-GCM",
                    icon = Icons.Rounded.Lock,
                    accent = colors.accentViolet,
                    items = listOf(
                        AegisModule.AI to tr("Phishing & Pass", "فیشینگ و رمز"),
                        AegisModule.PRIVACY to tr("Permissions", "دسترسی و بکاپ"),
                        AegisModule.PERSONAL to tr("Key Vault", "گاوصندوق"),
                    ),
                    onOpenModule = onOpenModule,
                )

                BentoCategoryBlock(
                    title = tr("Offline Maps & GNSS", "نقشه برداری و مسیریابی آفلاین"),
                    metricValue = "${mapPackages.size}",
                    metricUnit = tr("Cities + World", "شهر + جهان"),
                    icon = Icons.Rounded.Map,
                    accent = colors.accentTeal,
                    items = listOf(
                        AegisModule.MAPS to tr("Open Offline Map", "باز کردن نقشه زنده"),
                    ),
                    onOpenModule = onOpenModule,
                )

                BentoCategoryBlock(
                    title = tr("Forensics, Analytics & Learning", "گزارش رخداد، آمار و آموزش"),
                    metricValue = "${academyProgress.count { it.badgeEarned }}/${academyProgress.size.coerceAtLeast(8)}",
                    metricUnit = tr("Badges", "نشان"),
                    icon = Icons.Rounded.Insights,
                    accent = colors.accentAmber,
                    items = listOf(
                        AegisModule.CRISIS to tr("Incidents", "رخدادها"),
                        AegisModule.ANALYTICS to tr("Risk Stats", "آمار خطر"),
                        AegisModule.ACADEMY to tr("Academy", "آکادمی"),
                        AegisModule.FAMILY to tr("Family (${family.size})", "خانواده (${family.size})"),
                    ),
                    onOpenModule = onOpenModule,
                )
            }
        }
    }
}

@Composable
private fun HeaderIconChip(
    onClick: () -> Unit,
    active: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalObsidianColors.current
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (active) colors.accentCyan.copy(alpha = 0.22f) else colors.glassElevated,
        border = BorderStroke(1.dp, if (active) colors.accentCyan else colors.glassRimStart.copy(alpha = 0.4f)),
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}

@Composable
private fun MiniHeroNumber(
    value: String,
    label: String,
    accent: Color,
) {
    val colors = LocalObsidianColors.current
    Column {
        Text(
            text = value,
            style = TextStyle(
                fontFamily = AegisFontFamily,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFeatureSettings = "tnum",
            ),
            color = accent,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun QuickNavPill(
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
) {
    val colors = LocalObsidianColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = colors.glassSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BentoCategoryBlock(
    title: String,
    metricValue: String,
    metricUnit: String,
    icon: ImageVector,
    accent: Color,
    items: List<Pair<AegisModule, String>>,
    onOpenModule: (AegisModule) -> Unit,
) {
    val colors = LocalObsidianColors.current
    ObsidianGlassCard(
        elevated = false,
        accentBorderColor = accent,
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metricUnit,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            Text(
                text = metricValue,
                style = TextStyle(
                    fontFamily = AegisFontFamily,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFeatureSettings = "tnum",
                ),
                color = accent,
            )
        }
        Spacer(Modifier.height(8.dp))
        ChipFlowGroup {
            items.forEach { (mod, label) ->
                Surface(
                    onClick = { onOpenModule(mod) },
                    shape = RoundedCornerShape(12.dp),
                    color = colors.glassElevated,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AegisModuleIcon(module = mod, tint = accent, size = 16.dp)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                        )
                    }
                }
            }
        }
    }
}
