package ir.aegis.feature.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.aegis.core.maps.GeoMath
import ir.aegis.core.model.*
import ir.aegis.core.ui.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdvancedAnalyticsScreen(
    repository: AegisRepository,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
) {
    val colors = LocalObsidianColors.current
    val lang = LocalAppLanguage.current
    val context = LocalContext.current

    val allIncidents by repository.observeIncidents(includeArchived = true).collectAsState(initial = emptyList())
    val inventory by repository.observeInventoryItems().collectAsState(initial = emptyList())

    var daysWindow by remember { mutableIntStateOf(30) }
    var categoryFilter by remember { mutableStateOf<IncidentCategory?>(null) }
    var exportNotice by remember { mutableStateOf<String?>(null) }

    val cutoffMillis = remember(daysWindow) {
        if (daysWindow >= 3650) 0L else System.currentTimeMillis() - daysWindow * 86_400_000L
    }
    val filtered = remember(allIncidents, cutoffMillis, categoryFilter) {
        allIncidents.filter { inc ->
            inc.createdAtMillis >= cutoffMillis && (categoryFilter == null || inc.category == categoryFilter)
        }
    }

    val categoryCounts = remember(filtered) {
        filtered.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }
    }
    val priorityCounts = remember(filtered) {
        filtered.groupingBy { it.priority }.eachCount()
    }
    val heatmapMatrix = remember(filtered) {
        val pts = filtered.mapNotNull { inc ->
            val la = inc.latitude
            val lo = inc.longitude
            if (la != null && lo != null) Triple(la, lo, if (inc.priority == IncidentPriority.CRITICAL) 2.5f else 1f) else null
        }
        if (pts.isEmpty()) emptyMap() else {
            val minLat = pts.minOf { it.first }
            val maxLat = (pts.maxOf { it.first }).let { if (it == minLat) it + 0.01 else it }
            val minLon = pts.minOf { it.second }
            val maxLon = (pts.maxOf { it.second }).let { if (it == minLon) it + 0.01 else it }
            val weights = mutableMapOf<Pair<Int, Int>, Float>()
            pts.forEach { (la, lo, w) ->
                val cell = GeoMath.gridCell(la, lo, minLat, maxLat, minLon, maxLon, rows = 6, cols = 6)
                weights[cell] = (weights[cell] ?: 0f) + w
            }
            val maxW = (weights.values.maxOrNull() ?: 1f).coerceAtLeast(1f)
            weights.mapValues { (_, v) -> v / maxW }
        }
    }
    val timeBuckets = remember(filtered) {
        val fmt = SimpleDateFormat("MM/dd", Locale.US)
        filtered.groupBy { fmt.format(Date(it.createdAtMillis)) }
            .entries
            .sortedBy { it.key }
            .takeLast(7)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            module = AegisModule.ANALYTICS,
            onBack = onBack,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            showBackButton = showBackButton,
        )

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                ObsidianGlassCard {
                    Text(tr("Custom Analytics Filters", "فیلترهای تحلیلی سفارشی"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                    Spacer(Modifier.height(6.dp))
                    ChipFlowGroup {
                        listOf(
                            7 to tr("7 Days", "۷ روز"),
                            30 to tr("30 Days", "۳۰ روز"),
                            3650 to tr("All Time", "همه"),
                        ).forEach { (d, lbl) ->
                            AegisFilterPill(label = lbl, selected = daysWindow == d, onClick = { daysWindow = d })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    ChipFlowGroup {
                        AegisFilterPill(
                            label = tr("All Categories", "همه دسته‌ها"),
                            selected = categoryFilter == null,
                            onClick = { categoryFilter = null },
                        )
                        listOf(IncidentCategory.FIRE, IncidentCategory.EARTHQUAKE, IncidentCategory.FLOOD, IncidentCategory.SECURITY_THREAT).forEach { cat ->
                            AegisFilterPill(
                                label = categoryLabel(cat, lang),
                                selected = categoryFilter == cat,
                                onClick = { categoryFilter = cat },
                            )
                        }
                    }
                }
            }

            item {
                ObsidianGlassCard(elevated = true) {
                    val active = filtered.count { it.status != IncidentStatus.RESOLVED && !it.isArchived }
                    val critical = priorityCounts[IncidentPriority.CRITICAL] ?: 0
                    val lowStock = inventory.count { it.quantity < it.minimumThreshold }
                    Text(tr("Monthly & Period Executive Summary", "گزارش خلاصه ماهانه و دوره‌ای"), style = MaterialTheme.typography.titleMedium, color = colors.accentTeal)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Incidents: ${filtered.size} · Active: $active · Critical: $critical · Inventory Alerts: $lowStock",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    ChipFlowGroup {
                        GlassButton(
                            label = tr("Export PDF Report", "خروجی PDF"),
                            accent = colors.accentCyan,
                            onClick = {
                                val file = LocalFileExporter.exportPdfReport(
                                    context = context,
                                    fileNamePrefix = "analytics",
                                    title = "AegisGuard Offline Security Analytics",
                                    subtitle = "Window: Last $daysWindow days | Filter: ${categoryFilter?.name ?: "ALL"}",
                                    sections = listOf(
                                        "Executive KPIs" to listOf(
                                            "Total Incidents: ${filtered.size}",
                                            "Active / Unresolved: $active",
                                            "Critical Severity Count: $critical",
                                            "Low Supply Alerts: $lowStock",
                                        ),
                                        "Category Breakdown" to categoryCounts.map { "${it.key.name}: ${it.value}" },
                                        "Incident Records" to filtered.map { "${it.id} | ${it.title} | ${it.category} | ${it.priority} | ${it.status}" },
                                    ),
                                )
                                exportNotice = "Saved PDF: ${file.name}"
                                LocalFileExporter.shareFile(context, file, "application/pdf", "Share Analytics PDF")
                            },
                        )
                        GlassButton(
                            label = tr("Export CSV", "خروجی CSV"),
                            accent = colors.accentTeal,
                            onClick = {
                                val csv = buildString {
                                    appendLine("id,title,category,priority,status,location,latitude,longitude,created_epoch_ms")
                                    filtered.forEach { i ->
                                        val safeTitle = i.title.replace("\"", "\"\"")
                                        val safeLoc = i.locationLabel.replace("\"", "\"\"")
                                        appendLine("\"${i.id}\",\"$safeTitle\",${i.category},${i.priority},${i.status},\"$safeLoc\",${i.latitude ?: ""},${i.longitude ?: ""},${i.createdAtMillis}")
                                    }
                                }
                                val file = LocalFileExporter.exportTextFile(context, "exports", "aegis_analytics_${System.currentTimeMillis()}.csv", csv)
                                exportNotice = "Saved CSV: ${file.name}"
                                LocalFileExporter.shareFile(context, file, "text/csv", "Share Analytics CSV")
                            },
                        )
                    }
                    if (exportNotice != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(exportNotice!!, style = MaterialTheme.typography.labelSmall, color = colors.accentEmerald)
                    }
                }
            }

            item {
                ObsidianGlassCard {
                    Text(tr("Time-Based Incident Trend", "نمودار زمانی رخدادها"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                    Spacer(Modifier.height(8.dp))
                    if (timeBuckets.isEmpty()) {
                        Text(tr("No incidents in selected filter range.", "داده‌ای در بازه انتخابی وجود ندارد."), color = colors.textMuted)
                    } else {
                        val maxCount = (timeBuckets.maxOfOrNull { it.value.size } ?: 1).coerceAtLeast(1)
                        Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
                            val barW = (size.width / (timeBuckets.size * 1.6f)).coerceAtMost(48f)
                            val spacing = size.width / timeBuckets.size
                            timeBuckets.forEachIndexed { idx, bucket ->
                                val h = (bucket.value.size.toFloat() / maxCount) * (size.height - 16f)
                                drawRoundRect(
                                    color = colors.accentCyan,
                                    topLeft = Offset(idx * spacing + (spacing - barW) / 2f, size.height - h),
                                    size = Size(barW, h),
                                    cornerRadius = CornerRadius(8f, 8f),
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            timeBuckets.forEach { b ->
                                Text("${b.key}(${b.value.size})", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                            }
                        }
                    }
                }
            }

            item {
                ObsidianGlassCard {
                    Text(tr("Category Distribution Analysis", "تحلیل توزیع دسته‌بندی‌ها"), style = MaterialTheme.typography.titleMedium, color = colors.accentViolet)
                    Spacer(Modifier.height(8.dp))
                    val total = filtered.size.coerceAtLeast(1)
                    categoryCounts.forEach { (cat, count) ->
                        val frac = count.toFloat() / total
                        Text("${categoryLabel(cat, lang)}: $count (${(frac * 100).toInt()}%)", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                        LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = colors.accentViolet)
                    }
                }
            }

            item {
                ObsidianGlassCard {
                    Text(tr("Local Spatial Incident Heatmap (6x6 Grid)", "نقشه حرارتی محلی نقاط حادثه‌خیز"), style = MaterialTheme.typography.titleMedium, color = colors.accentAmber)
                    Spacer(Modifier.height(8.dp))
                    if (heatmapMatrix.isEmpty()) {
                        Text(tr("No geotagged incidents in filter.", "رخداد دارای مختصات یافت نشد."), color = colors.textMuted)
                    } else {
                        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                            val cellW = size.width / 6f
                            val cellH = size.height / 6f
                            for (r in 0 until 6) {
                                for (c in 0 until 6) {
                                    val intensity = heatmapMatrix[r to c] ?: 0f
                                    val alpha = if (intensity == 0f) 0.08f else (0.20f + 0.75f * intensity).coerceIn(0.15f, 0.95f)
                                    drawRoundRect(
                                        color = if (intensity == 0f) colors.accentCyan.copy(alpha = alpha) else colors.accentCrimson.copy(alpha = alpha),
                                        topLeft = Offset(c * cellW + 3f, r * cellH + 3f),
                                        size = Size(cellW - 6f, cellH - 6f),
                                        cornerRadius = CornerRadius(8f, 8f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
