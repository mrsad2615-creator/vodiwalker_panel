@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.neurox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neurox.domain.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun AnalyticsScreen(state: AppState) {
    var module by rememberSaveable { mutableStateOf("ALL") }
    var days by rememberSaveable { mutableIntStateOf(7) }
    var baseline by rememberSaveable { mutableStateOf(false) }
    val threshold = LocalDate.now().minusDays(days.toLong() - 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val values = state.sessions.filter { it.status == "COMPLETE" && it.valid && it.baseline == baseline && (module == "ALL" || it.module == module) && it.startedAt >= threshold }
    Page {
        SectionTitle("PERFORMANCE / INSIGHTS", tr("پیشرفت، با شواهد", "Progress, with evidence"), tr("هر شاخص فقط در زمینهٔ خودش معنا دارد؛ سرعت خواندن و زمان واکنش با هم مقایسه نمی‌شوند.", "Metrics stay in their own context. Reading speed is not compared with reaction time."))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = module == "ALL", onClick = { module = "ALL" }, label = { Text(tr("همه", "All")) })
            Module.entries.forEach { FilterChip(selected = module == it.name, onClick = { module = it.name }, label = { Text(it.name) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 365, 3650).forEach { d -> FilterChip(selected = days == d, onClick = { days = d }, label = { Text(if (d == 3650) tr("ده سال", "10 years") else tr("${number(d)} روز", "${number(d)} days")) }) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tr("نمایش سنجش‌های پایه", "Show baselines"))
            Switch(checked = baseline, onCheckedChange = { baseline = it })
        }
        if (values.isEmpty()) EmptyState() else {
            Panel {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Metric(tr("جلسهٔ معتبر", "Valid sessions"), number(values.size))
                    Metric(tr("دقیقهٔ فعال", "Active minutes"), number(values.sumOf { it.durationMs } / 60_000.0, 1))
                }
            }
            Module.entries.filter { m -> module == "ALL" || module == m.name }.forEach { m ->
                val subset = values.filter { it.module == m.name }
                if (subset.isNotEmpty()) Panel {
                    Text(m.name, style = MaterialTheme.typography.titleMedium)
                    val mean = subset.map { it.accuracy }.average()
                    Text(tr("میانگین دقت جلسات: ${number(mean * 100, 1)}٪", "Mean session accuracy: ${number(mean * 100, 1)}%"))
                    LinearProgressIndicator(progress = { mean.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    val weak = subset.flatMap { session -> runCatching { com.neurox.data.CheckpointCodec.decode(session.checkpoint).attempts }.getOrDefault(emptyList()) }
                        .flatMap { it.weakKeys.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }.take(5)
                    if (m == Module.TYPE && weak.isNotEmpty()) Text(tr("کلیدهای نیازمند تمرین: ", "Keys to practice: ") + weak.joinToString(" · ") { "${it.key}: ${it.value}" })
                }
            }
            Text(tr("تاریخچهٔ اندازه‌گیری", "MEASUREMENT HISTORY"), style = MaterialTheme.typography.titleMedium)
            values.take(100).forEach { SessionRow(it) }
            if (values.size > 100) Text(tr("۱۰۰ جلسهٔ اخیر نمایش داده می‌شود. برای جزئیات، بازهٔ کوتاه‌تری انتخاب کنید.", "Showing the latest 100 sessions. Select a shorter date range for detail."))
        }
        val unfinished = state.sessions.count { it.status == "ABORTED" }
        if (unfinished > 0) Text(tr("${number(unfinished)} جلسهٔ ناتمام از پاداش و آمار عملکرد کنار گذاشته شده است.", "${number(unfinished)} aborted sessions are excluded from rewards and performance summaries."), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun LibraryScreen(state: AppState, vm: AppViewModel) {
    var search by rememberSaveable { mutableStateOf("") }
    val area by vm.libraryArea.collectAsStateWithLifecycle()
    var module by rememberSaveable { mutableStateOf("ALL") }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val values = Catalogue.runnable.filter {
        area != "CUSTOM" && (module == "ALL" || module == it.exercise.module.name) &&
            (area != "FAVORITES" || it.exercise.name in state.favorites) &&
            (search.isBlank() || it.en.contains(search, true) || it.fa.contains(search) || it.exercise.module.name.contains(search, true))
    }
    val custom = state.customExercises.filter {
        area != "ORIGINAL" && (module == "ALL" || module == it.module) &&
            (area != "FAVORITES" || "custom:${it.id}" in state.favorites) &&
            (search.isBlank() || it.name.contains(search, true) || it.module.contains(search, true) || it.template.contains(search, true))
    }
    LaunchedEffect(state.customExercises) { if (state.customExercises.none { it.id == deleting }) deleting = null }
    Page {
        SectionTitle("NEUROX LIBRARY", tr("کتابخانهٔ تمرین", "Your practice library"), tr("تمرین‌های اصلی و تمرین‌های شخصی، همگی محلی اجرا می‌شوند.", "Original and personal exercises run locally."))
        OutlinedTextField(value = search, onValueChange = { if (it.length <= 100) search = it }, label = { Text(tr("جست‌وجو", "Search")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ORIGINAL" to tr("اصلی", "Original"), "CUSTOM" to tr("تمرین‌های من", "My exercises"), "FAVORITES" to tr("علاقه‌مندی‌ها", "Favorites")).forEach { (value, label) ->
                FilterChip(selected = area == value, onClick = { vm.selectLibraryArea(value) }, label = { Text(label) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = module == "ALL", onClick = { module = "ALL" }, label = { Text(tr("همه", "All")) })
            Module.entries.forEach { m -> FilterChip(selected = module == m.name, onClick = { module = m.name }, label = { Text(m.name) }) }
        }
        Button(onClick = { vm.openBuilder() }, modifier = Modifier.fillMaxWidth()) { Text(tr("ساخت تمرین شخصی", "Build a personal exercise")) }
        if (values.isEmpty() && custom.isEmpty()) Text(tr("تمرینی با این فیلتر پیدا نشد.", "No exercises match these filters."))
        values.forEach { definition -> Panel {
            Text(definition.exercise.module.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(exerciseName(definition.exercise), style = MaterialTheme.typography.titleLarge)
            Text(tr(definition.instructionFa, definition.instructionEn))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.chooseExercise(definition.exercise) }) { Text(tr("تنظیم و اجرا", "Configure & train")) }
                TextButton(onClick = { vm.favorite(definition.exercise, definition.exercise.name !in state.favorites) }, enabled = !state.busy) {
                    Text(if (definition.exercise.name in state.favorites) tr("حذف از علاقه‌مندی", "Unfavorite") else tr("ذخیره در علاقه‌مندی", "Favorite"))
                }
            }
        } }
        custom.forEach { value -> Panel {
            val blueprint = remember(value) { runCatching { value.blueprint() }.getOrNull() }
            Text(value.module + " / MY EXERCISES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(value.name, style = MaterialTheme.typography.titleLarge)
            if (blueprint == null) Text(tr("قالب تمرین معتبر نیست. اجرای آن برای حفظ داده‌ها مسدود شد.", "The saved template is invalid. Execution is blocked to protect your data."), color = MaterialTheme.colorScheme.error)
            else {
                Text(exerciseName(blueprint.template))
                Text(tr("${number(blueprint.rounds)} دور · سطح ${number(blueprint.difficulty)} · دقت هدف ${number(blueprint.targetAccuracyPercent)}٪", "${number(blueprint.rounds)} rounds · Level ${number(blueprint.difficulty)} · Target ${number(blueprint.targetAccuracyPercent)}%"))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.startCustom(value.id) }, enabled = blueprint != null && state.interrupted == null && !state.busy) { Text(tr("آغاز", "Start")) }
                TextButton(onClick = { vm.openBuilder(value.id) }, enabled = blueprint != null) { Text(tr("ویرایش", "Edit")) }
                TextButton(onClick = { vm.duplicateBlueprint(value.id) }, enabled = blueprint != null && !state.busy) { Text(tr("ساخت نسخهٔ دیگر", "Duplicate")) }
                TextButton(onClick = { vm.customFavorite(value.id, "custom:${value.id}" !in state.favorites) }, enabled = blueprint != null && !state.busy) {
                    Text(if ("custom:${value.id}" in state.favorites) tr("حذف از علاقه‌مندی", "Unfavorite") else tr("علاقه‌مندی", "Favorite"))
                }
                TextButton(onClick = { deleting = value.id }, enabled = !state.busy) { Text(tr("حذف", "Delete")) }
            }
        } }
        if (area != "ORIGINAL" && state.interrupted != null) Text(tr("برای آغاز تمرین تازه، جلسهٔ ناتمام را از خانه ادامه دهید یا کنار بگذارید.", "Resume or discard the interrupted session from Home before starting another."))
    }
    deleting?.let { id -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(tr("تمرین حذف شود؟", "Delete exercise?")) },
        text = { Text(tr("پیکربندی و علاقه‌مندی حذف می‌شوند. تاریخچهٔ جلسات قبلی حفظ می‌شود.", "The configuration and favorite are removed. Previous session history is preserved.")) },
        confirmButton = { TextButton(onClick = { vm.deleteBlueprint(id) }, enabled = !state.busy) { Text(tr("حذف", "Delete")) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("انصراف", "Cancel")) } }) }
}
