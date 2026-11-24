@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.neurox.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neurox.data.SessionEntity
import com.neurox.design.*
import com.neurox.domain.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun List<SessionEntity>.trainingFacts(): List<TrainingFact> = mapNotNull { session ->
    runCatching { TrainingFact(session.id, Exercise.valueOf(session.exercise), session.startedAt,
        session.durationMs, session.accuracy, session.valid, session.status == "COMPLETE", session.baseline) }.getOrNull()
}

@Composable
internal fun moduleTitle(module: Module): String = when (module) {
    Module.VISION -> tr("دیدن با تمرکز", "See with intention.")
    Module.REFLEX -> tr("پاسخ دقیق‌تر", "Make your move.")
    Module.READ -> tr("بخوان و درک کن", "Read. Understand.")
    Module.TYPE -> tr("دقت در هر کلید", "Precision in every key.")
}

@Composable
internal fun StudiosScreen(vm: AppViewModel) {
    Page {
        SectionTitle(tr("چهار سیستم، یک کیفیت", "FOUR SYSTEMS / ONE STANDARD"), tr("امروز چه چیزی تمرین می‌کنید؟", "What will you practice today?"),
            tr("یک سیستم را انتخاب کنید. راهنما را بخوانید و با سطح مناسب خودتان شروع کنید.", "Choose a system. Read the instructions. Begin at a comfortable level."))
        Module.entries.forEach { module ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NeuroxSpace.md)) {
                    NeuroxIcon(glyph(module), Modifier.size(36.dp), moduleColor(module))
                    Column { Text(moduleName(module), color = moduleColor(module), style = MaterialTheme.typography.labelMedium); Text(moduleTitle(module), style = MaterialTheme.typography.titleLarge) }
                }
                Text(tr("${number(Catalogue.runnable.count { it.exercise.module == module })} تمرین موجود · تنظیم سطح · تاریخچهٔ محلی", "${number(Catalogue.runnable.count { it.exercise.module == module })} available exercises · adjustable level · local history"))
                OutlinedButton(onClick = { vm.openModule(module) }, modifier = Modifier.fillMaxWidth()) { Text(tr("ورود به استودیوی ${moduleName(module)}", "Open ${module.name} studio")) }
            }
        }
        Text(tr("این تمرین‌ها جایگزین مراقبت پزشکی نیستند و نتیجهٔ آن‌ها معیار هوش عمومی نیست.", "These exercises do not replace medical care. Their results are not a measure of general intelligence."), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun ModuleOverview(state: AppState, vm: AppViewModel) {
    val selected by vm.selectedModule.collectAsStateWithLifecycle()
    val module = Module.valueOf(selected)
    val now = remember(state.sessions) { System.currentTimeMillis() }
    val zone = ZoneId.systemDefault()
    val facts = remember(state.sessions) { state.sessions.trainingFacts() }
    val insights = remember(facts, module, now, zone) { TrainingInsights.summarize(facts, now, zone, module = module) }
    val recommendation = remember(facts, module, now, state.profile?.adaptive) {
        if (state.profile?.adaptive == false) Catalogue.primary.first { it.exercise.module == module }.exercise
        else TrainingInsights.nextExercise(facts, now, module)
    }
    val exercises = Catalogue.runnable.filter { it.exercise.module == module }
    Page {
        SectionTitle(tr("استودیوی ${moduleName(module)}", "${module.name} / STUDIO"), moduleTitle(module), tr("تمرین هدفمند، اندازه‌گیری واقعی، انتخاب آگاهانه.", "Purposeful practice. Actual measurements. Informed choices."))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(NeuroxSpace.xs)) {
            Module.entries.forEach { entry -> FilterChip(selected = entry == module, onClick = { vm.openModule(entry) }, label = { Text(moduleName(entry)) }) }
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NeuroxSpace.lg)) {
                NeuroxIcon(glyph(module), Modifier.size(48.dp), moduleColor(module))
                Column { Text(tr("هفت روز اخیر", "PAST SEVEN DAYS"), style = MaterialTheme.typography.labelSmall); Text(moduleName(module), style = MaterialTheme.typography.titleLarge) }
            }
            if (insights.sessions == 0) Text(tr("هنوز جلسهٔ معتبر در این بازه ندارید. اولین نتیجه پس از تمرین نمایش داده می‌شود.", "No valid session in this period. Your first result appears after training."))
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(NeuroxSpace.xl)) {
                Metric(tr("جلسهٔ معتبر", "Valid sessions"), number(insights.sessions))
                Metric(tr("دقیقهٔ فعال", "Active minutes"), number(insights.activeMs / 60_000.0, 1))
                Metric(tr("میانگین دقت جلسات", "Mean session accuracy"), "${number((insights.meanSessionAccuracy ?: 0.0) * 100, 1)}٪")
            }
            ActivityChart(insights.days, module)
        }
        Panel {
            Text(tr("پیشنهاد شفاف", "A TRANSPARENT SUGGESTION"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            Text(exerciseName(recommendation), style = MaterialTheme.typography.titleLarge)
            Text(if (state.profile?.adaptive == false) tr("پیشنهاد تطبیقی خاموش است؛ اولین تمرین این سیستم نمایش داده می‌شود.", "Adaptive suggestions are off. The first exercise in this system is shown.")
                else tr("این تکلیف در سابقهٔ معتبر شما کمتر تمرین شده است. سطح را در صفحهٔ تنظیمات تمرین خودتان انتخاب می‌کنید.", "This task has fewer valid sessions in your history. Choose its level yourself on the setup screen."))
            Button(onClick = { vm.chooseExercise(recommendation) }, modifier = Modifier.fillMaxWidth()) { Text(tr("تنظیم تمرین پیشنهادی", "Configure suggested practice")) }
        }
        Text(tr("تمرین‌های این استودیو", "IN THIS STUDIO"), style = MaterialTheme.typography.titleMedium)
        exercises.forEach { definition ->
            Panel {
                Text(exerciseName(definition.exercise), style = MaterialTheme.typography.titleMedium)
                Text(tr(definition.instructionFa, definition.instructionEn))
                OutlinedButton(onClick = { vm.chooseExercise(definition.exercise) }, modifier = Modifier.fillMaxWidth()) { Text(tr("راهنما و تنظیمات", "Instructions & setup")) }
            }
        }
        if (module == Module.VISION) Text(tr("در صورت ناراحتی چشم یا سرگیجه توقف کنید. از فاصلهٔ راحت و نور مناسب استفاده کنید. کنترل اندازه و کنتراست محرک در همهٔ تمرین‌ها هنوز تکمیل نشده است.", "Stop if you feel eye discomfort or dizziness. Use a comfortable distance and suitable lighting. Stimulus-size and contrast controls are not complete for every exercise."), style = MaterialTheme.typography.bodySmall)
        if (module == Module.REFLEX) Text(tr("زمان واکنش از انتشار نرم‌افزاری نشانه حساب می‌شود. نمایشگر، لمس و ابزار کمکی بر مقایسه اثر دارند؛ این شاخص هوش نیست.", "Reaction time starts at software publication. Display, touch and assistive tools affect comparisons. This is not an intelligence score."), style = MaterialTheme.typography.bodySmall)
        if (module == Module.READ) Text(tr("سرعت خواندن را کنار پاسخ درک مطلب ببینید. کتابخانهٔ متن شخصی و ورود فایل هنوز در دست ساخت است.", "Read pace together with comprehension answers. A personal text library and file import remain in development."), style = MaterialTheme.typography.bodySmall)
        if (module == Module.TYPE) Text(tr("کیبورد مجازی و فیزیکی رفتار متفاوتی دارند. متن واردشده در تاریخچه ذخیره نمی‌شود. راهنمای کامل انگشت‌گذاری هنوز موجود نیست.", "Virtual and physical keyboards differ. Entered text is not saved in history. Full finger-placement guidance is not available yet."), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { vm.navigate(Screen.STUDIOS) }, modifier = Modifier.fillMaxWidth()) { Text(tr("همهٔ استودیوها", "All studios")) }
    }
}

@Composable
internal fun ActivityChart(days: List<DailyActivity>, module: Module? = null) {
    val labels = days.map { "${number(it.date.monthValue)}/${number(it.date.dayOfMonth)}" }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val summary = tr("دقایق فعال روزانه: ", "Daily active minutes: ") + days.mapIndexed { index, day -> "${labels[index]}: ${number(day.activeMs / 60_000.0, 1)}" }.joinToString(tr("؛ ", "; "))
    val tint = module?.let { moduleColor(it) } ?: MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val maximum = days.maxOfOrNull { it.activeMs }?.coerceAtLeast(1L) ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(NeuroxSpace.xs)) {
        Canvas(Modifier.fillMaxWidth().height(88.dp).semantics { contentDescription = summary }) {
            if (days.isNotEmpty()) {
                val slot = size.width / days.size
                days.forEachIndexed { index, day ->
                    val h = (size.height * day.activeMs.toFloat() / maximum).coerceAtLeast(2.dp.toPx())
                    drawRoundRect(if (day.activeMs > 0) tint else empty, Offset(slot * (if (rtl) days.lastIndex - index else index) + slot * .2f, size.height - h), Size(slot * .6f, h), CornerRadius(3.dp.toPx()))
                }
            }
        }
        // Values are textual as well as graphical; zero days are never invented progress.
        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun AchievementsScreen(state: AppState) {
    val facts = remember(state.sessions) { state.sessions.trainingFacts() }
    val milestones = remember(facts) { TrainingInsights.milestones(facts, System.currentTimeMillis(), ZoneId.systemDefault()) }
    Page {
        SectionTitle(tr("مسیر پیشرفت و دستاوردها", "QUEST / MILESTONES"), tr("گام‌های کوچک، دستاوردهای واقعی", "Small steps. Real milestones."), tr("بدون جریمه برای استراحت. دستاوردها از جلسات معتبر محلی محاسبه می‌شوند و XP اضافه ایجاد نمی‌کنند.", "No penalty for rest. Milestones come from valid local sessions and do not create extra XP."))
        milestones.forEach { progress ->
            val title = when (progress.id) {
                "first_session" -> tr("اولین گام", "First step")
                "four_systems" -> tr("چهار نگاه", "Four perspectives")
                "ten_sessions" -> tr("ده جلسهٔ هدفمند", "Ten purposeful sessions")
                "seven_days" -> tr("هفت روز تمرین", "Seven training days")
                "one_hour" -> tr("یک ساعت توجه", "An hour of attention")
                else -> tr("پنج جلسهٔ دقیق", "Five careful sessions")
            }
            val description = when (progress.id) {
                "first_session" -> tr("یک جلسهٔ معتبر کامل کنید.", "Complete one valid session.")
                "four_systems" -> tr("در هر چهار سیستم جلسهٔ معتبر ثبت کنید.", "Complete a valid session in all four systems.")
                "ten_sessions" -> tr("ده جلسهٔ معتبر کامل کنید.", "Complete ten valid sessions.")
                "seven_days" -> tr("در هفت روز محلی متفاوت تمرین کنید؛ لازم نیست پیوسته باشند.", "Train on seven different local days. They need not be consecutive.")
                "one_hour" -> tr("شصت دقیقهٔ فعال در جلسات معتبر ثبت کنید.", "Accumulate sixty active minutes in valid sessions.")
                else -> tr("پنج جلسه با دقت حداقل ۹۵٪ ثبت کنید. این شاخص فقط به تکلیف مربوط است.", "Complete five sessions with at least 95% accuracy. This is task-specific.")
            }
            Panel {
                Text(if (progress.unlocked) tr("به‌دست‌آمده", "EARNED") else tr("در مسیر", "IN PROGRESS"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(description)
                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                if (progress.id == "one_hour") Text(tr("${number(progress.current / 60_000.0, 1)} از ۶۰ دقیقه", "${number(progress.current / 60_000.0, 1)} of 60 minutes"))
                else Text("${number(progress.current)} / ${number(progress.target)}")
                progress.earnedAt?.let { earned -> Text(tr("تاریخ تحقق: ", "Reached: ") + java.time.Instant.ofEpochMilli(earned).atZone(ZoneId.systemDefault()).toLocalDate().toString(), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
