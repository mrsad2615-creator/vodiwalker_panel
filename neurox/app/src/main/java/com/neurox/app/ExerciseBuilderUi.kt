@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.neurox.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neurox.domain.*

@Composable
internal fun ExerciseBuilderScreen(state: AppState, vm: AppViewModel) {
    val id by vm.builderId.collectAsStateWithLifecycle()
    val profile = requireNotNull(state.profile)
    val saved = state.customExercises.firstOrNull { it.id == id }
    if (id != null && !state.customExercisesLoaded) {
        Page { CircularProgressIndicator(); Text(tr("خواندن تمرین محلی…", "Loading local exercise…")) }
        return
    }
    val initial = remember(saved) { saved?.let { runCatching { it.blueprint() }.getOrNull() } }
    if (id != null && initial == null) {
        Page { Text(tr("تمرین موجود نیست یا قالب آن قابل خواندن نیست.", "This exercise is missing or its schema cannot be read.")); Button(onClick = { vm.navigate(Screen.LIBRARY) }) { Text(tr("بازگشت", "Back")) } }
        return
    }
    var name by rememberSaveable(profile.id, id) { mutableStateOf(initial?.name ?: "") }
    var templateName by rememberSaveable(profile.id, id) { mutableStateOf(initial?.template?.name ?: Exercise.FOCUS_LOCK.name) }
    var difficulty by rememberSaveable(profile.id, id) { mutableIntStateOf(initial?.difficulty ?: 1) }
    var rounds by rememberSaveable(profile.id, id) { mutableIntStateOf(initial?.rounds ?: 5) }
    var windowSeconds by rememberSaveable(profile.id, id) { mutableIntStateOf(((initial?.responseLimitMs ?: 5000) / 1000).toInt()) }
    var goal by rememberSaveable(profile.id, id) { mutableIntStateOf(initial?.targetAccuracyPercent ?: 80) }
    var allowance by rememberSaveable(profile.id, id) { mutableIntStateOf((initial?.errorAllowance ?: rounds).coerceAtMost(rounds)) }
    var showPreview by rememberSaveable(profile.id, id) { mutableStateOf(false) }
    val template = Exercise.valueOf(templateName)
    val blueprint = ExerciseBlueprint(name, template, difficulty, rounds, windowSeconds * 1000L, goal, allowance)
    val errors = blueprint.validate()
    val language = Language.valueOf(profile.language)
    Page {
        SectionTitle("MY EXERCISES / BUILDER", if (id == null) tr("تمرین خودت را بساز", "Build your practice") else tr("ویرایش تمرین", "Edit exercise"),
            tr("فقط قالب‌های آزموده‌شدهٔ هسته اجرا می‌شوند؛ هیچ کد دلخواهی اجرا نمی‌شود.", "Only finite built-in templates run. No user code is executed."))
        OutlinedTextField(value = name, onValueChange = { if (it.length <= 60) name = it }, label = { Text(tr("نام تمرین", "Exercise name")) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            isError = name.isNotEmpty() && "invalid_name" in errors)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Module.entries.forEach { module -> FilterChip(selected = template.module == module, onClick = {
                templateName = Catalogue.primary.first { it.exercise.module == module }.exercise.name
                if (templateName == Exercise.COMPREHENSION.name) difficulty = 1
            }, label = { Text(module.name) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Catalogue.runnable.filter { it.exercise.module == template.module }.forEach { definition ->
                FilterChip(selected = template == definition.exercise, onClick = {
                    templateName = definition.exercise.name
                    if (definition.exercise == Exercise.COMPREHENSION) difficulty = 1
                }, label = { Text(exerciseName(definition.exercise)) })
            }
        }
        Panel {
            Text(tr(Catalogue.get(template).instructionFa, Catalogue.get(template).instructionEn))
            Text(tr("دورها: ${number(rounds)}", "Rounds: ${number(rounds)}"))
            Slider(value = rounds.toFloat(), onValueChange = { rounds = it.toInt(); allowance = allowance.coerceAtMost(rounds) }, valueRange = 1f..30f, steps = 28)
            if (template != Exercise.COMPREHENSION) {
                Text(tr("دشواری: ${number(difficulty)}", "Difficulty: ${number(difficulty)}"))
                Slider(value = difficulty.toFloat(), onValueChange = { difficulty = it.toInt() }, valueRange = 1f..5f, steps = 3)
            }
            if (template.module in listOf(Module.VISION, Module.REFLEX)) {
                Text(tr("پنجرهٔ پاسخ پایه: ${number(windowSeconds)} ثانیه", "Base response window: ${number(windowSeconds)} seconds"))
                Slider(value = windowSeconds.toFloat(), onValueChange = { windowSeconds = it.toInt() }, valueRange = 1f..60f, steps = 58)
                Text(tr("زمان مؤثر با دشواری: ${number(blueprint.configuration(language, 0).effectiveResponseLimitMs / 1000.0, 1)} ثانیه", "Effective window at this level: ${number(blueprint.configuration(language, 0).effectiveResponseLimitMs / 1000.0, 1)} seconds"), style = MaterialTheme.typography.bodySmall)
            } else Text(tr("مدت نهایی به زمان خواندن یا تایپ شما وابسته است؛ پایان بر اساس تکمیل دورها است.", "Final duration depends on your reading or typing time. All rounds must be completed."), style = MaterialTheme.typography.bodySmall)
        }
        Panel {
            Text(tr("معیار موفقیت", "Success criteria"), style = MaterialTheme.typography.titleMedium)
            Text(tr("دقت هدف: ${number(goal)}٪", "Target accuracy: ${number(goal)}%"))
            Slider(value = goal.toFloat(), onValueChange = { goal = it.toInt() }, valueRange = 1f..100f, steps = 98)
            Text(tr("حداکثر دور ناموفق: ${number(allowance)}", "Maximum unsuccessful rounds: ${number(allowance)}"))
            Slider(value = allowance.toFloat(), onValueChange = { allowance = it.toInt() }, valueRange = 0f..rounds.toFloat(), steps = (rounds - 1).coerceAtLeast(0))
            Text(tr("معیار موفقیت نتیجهٔ اندازه‌گیری را تغییر نمی‌دهد. محاسبهٔ XP همان قاعدهٔ مشترک برنامه است.", "Success criteria do not alter measurements. XP uses the shared application rules."), style = MaterialTheme.typography.bodySmall)
        }
        if (errors.isNotEmpty()) Text(tr("نام معتبر و مقادیر مجاز را وارد کنید. ذخیره و اجرا تا رفع خطا غیرفعال است.", "Enter a valid name and allowed values. Save and run remain disabled until validation passes."), color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showPreview = !showPreview }, enabled = errors.isEmpty()) { Text(tr("پیش‌نمایش", "Preview")) }
            OutlinedButton(onClick = { vm.testBlueprint(blueprint, id) }, enabled = errors.isEmpty() && !state.busy && state.interrupted == null) { Text(tr("اجرای آزمایشی", "Test run")) }
        }
        if (showPreview && errors.isEmpty()) {
            val preview = remember(blueprint, language) {
                TrainingEngine(blueprint.configuration(language, 42), MonotonicClock { 0 }).also { it.resume() }.view()
            }
            Panel {
                Text(tr("نمونهٔ دور اول · پیش‌نمایش بدون امتیاز", "First-round sample · not a scored session"), style = MaterialTheme.typography.labelLarge)
                when (template.module) {
                    Module.TYPE -> Text(preview.typingTarget)
                    Module.READ -> Text(preview.phrase?.text ?: preview.passage.text)
                    Module.VISION -> if (preview.pattern != null) PatternGrid(requireNotNull(preview.pattern).sample, Modifier.size(130.dp)) else {
                        Text(tr("خانهٔ هدف نمونه: ${number(preview.targetCell + 1)}", "Sample target cell: ${number(preview.targetCell + 1)}"))
                        if (template == Exercise.VISUAL_SEARCH) SearchShape(SearchMark.T_UP, Modifier.size(48.dp))
                    }
                    Module.REFLEX -> Text(tr("نشانه پس از مکث تصادفی نمایش داده می‌شود. برای آزمون ورودی، اجرای آزمایشی را بزنید.", "The signal follows a random wait. Use Test run to test input handling."))
                }
            }
        }
        if (state.interrupted != null) Text(tr("برای اجرای آزمایشی، ابتدا جلسهٔ ناتمام را از خانه ادامه دهید یا کنار بگذارید.", "Resume or discard the interrupted session from Home before a test run."))
        Button(onClick = { vm.saveBlueprint(blueprint, id) }, enabled = errors.isEmpty() && !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(tr("ذخیرهٔ تمرین", "Save exercise")) }
        Text(tr("در این نسخه تنظیم ابعاد هدف، موسیقی و امتیازدهی دلخواه در تمرین‌ساز هنوز ارائه نشده است.", "Target geometry, audio and custom scoring rules are not exposed by this builder milestone."), style = MaterialTheme.typography.bodySmall)
    }
}
