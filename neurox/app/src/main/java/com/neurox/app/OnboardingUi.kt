@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.neurox.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neurox.design.*
import com.neurox.domain.Module

/** Real local preferences. No account, external service or mandatory assessment. */
@Composable
internal fun OnboardingScreen(state: AppState, vm: AppViewModel) {
    val profile = requireNotNull(state.profile)
    var step by rememberSaveable { mutableIntStateOf(0) }
    var goal by rememberSaveable(profile.id, state.dailyGoalMinutes) { mutableIntStateOf(state.dailyGoalMinutes) }
    Page {
        LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())
        Text(tr("آشنایی ${number(step + 1)} از ۳", "WELCOME ${number(step + 1)} OF 3"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        when (step) {
            0 -> {
                NeuroxEmblem(size = 64.dp)
                SectionTitle(tr("تمرین دقیق، پیشرفت آگاهانه", "TRAIN YOUR MIND. MASTER YOUR SPEED."), tr("تمرینی برای شما، با سرعت خودتان", "A practice. Entirely yours."),
                    tr("توجه، پاسخ دقیق، خواندن و تایپ را بدون نیاز به حساب یا اینترنت تمرین کنید.", "Practice attention, response, reading and typing without an account or internet."))
                Panel {
                    Module.entries.forEach { module ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NeuroxSpace.md)) {
                            NeuroxIcon(glyph(module), tint = moduleColor(module))
                            Column { Text(moduleName(module), style = MaterialTheme.typography.labelMedium); Text(moduleTitle(module)) }
                        }
                    }
                }
                Text(tr("تمرین‌ها تشخیص یا درمان پزشکی نیستند و نمرهٔ هوش عمومی ارائه نمی‌کنند.", "Exercises are not medical diagnosis or treatment and do not provide an intelligence score."))
            }
            1 -> {
                SectionTitle(tr("تجربه‌ای مطابق با شما", "MAKE YOURSELF COMFORTABLE"), tr("مطابق با شما", "Make yourself comfortable."))
                Panel {
                    Text(tr("زبان و جهت نوشتار", "Language & direction"), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(NeuroxSpace.xs)) {
                        listOf("FA" to "فارسی", "EN" to "English").forEach { (value, label) -> FilterChip(selected = profile.language == value,
                            onClick = { vm.updateProfile(profile.copy(language = value)) }, enabled = !state.busy, label = { Text(label) }) }
                    }
                    Text(tr("پوسته", "Appearance"), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(NeuroxSpace.xs)) {
                        listOf("DARK" to tr("تیره", "Dark"), "LIGHT" to tr("روشن", "Light"), "SYSTEM" to tr("سیستم", "System")).forEach { (value, label) ->
                            FilterChip(selected = profile.theme == value, onClick = { vm.updateProfile(profile.copy(theme = value)) }, enabled = !state.busy, label = { Text(label) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("حرکت کمتر", "Reduced motion"), modifier = Modifier.weight(1f))
                        Switch(checked = profile.reducedMotion, onCheckedChange = { vm.updateProfile(profile.copy(reducedMotion = it)) }, enabled = !state.busy)
                    }
                    Text(tr("تغییرها در همین دستگاه ذخیره می‌شوند و بعداً در تنظیمات قابل تغییرند.", "Changes are stored on this device. You can change them later in Settings."), style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> {
                SectionTitle(tr("ریتم شما، حریم خصوصی شما", "YOUR PACE / YOUR PRIVACY"), tr("ریتمی قابل ادامه", "A rhythm you can keep."))
                Panel {
                    Text(tr("هدف روزانهٔ اختیاری: ${number(goal)} دقیقه", "Optional daily goal: ${number(goal)} minutes"), style = MaterialTheme.typography.titleMedium)
                    Slider(value = goal.toFloat(), onValueChange = { goal = it.toInt() }, onValueChangeFinished = { vm.setDailyGoal(goal) }, valueRange = 5f..60f, steps = 54, enabled = !state.busy)
                    Text(tr("هدف، یک راهنما است نه تعهد. استراحت جریمه ندارد.", "A goal is a guide, not an obligation. Rest has no penalty."))
                }
                Panel {
                    Text(tr("داده‌ها محلی می‌مانند", "Your training stays local"), style = MaterialTheme.typography.titleMedium)
                    Text(tr("بدون حساب ابری، رهگیری یا ارسال دادهٔ تمرین. متن واردشدهٔ تایپ در تاریخچه ذخیره نمی‌شود. قفل PIN و خروجی/بازیابی یکپارچه هنوز آماده نیستند.", "No cloud account, telemetry or training-data upload. Entered typing text is not saved in history. PIN and integrated export/restore are not ready yet."))
                    Text(tr("سنجش پایه اختیاری است و XP ندارد. می‌توانید با یک سنجش دیداری شروع کنید؛ راهنمای سنجش چهارمرحله‌ای کامل هنوز آماده نیست.", "Baseline training is optional and earns no XP. You can start with a visual baseline. The full guided four-system assessment is not ready yet."))
                    OutlinedButton(onClick = vm::beginWelcomeBaseline, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(tr("تنظیم سنجش پایهٔ دیداری", "Set up a visual baseline")) }
                }
            }
        }
        if (step < 2) Button(onClick = { step++ }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(tr("ادامهٔ آشنایی", "Continue introduction")) }
        else Button(onClick = vm::completeWelcome, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(tr("شروع با سرعت خودم", "Begin at my own pace")) }
        if (step > 0) TextButton(onClick = { step-- }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(tr("مرحلهٔ قبل", "Previous step")) }
        TextButton(onClick = vm::completeWelcome, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(tr("رد کردن آشنایی", "Skip introduction")) }
    }
}
