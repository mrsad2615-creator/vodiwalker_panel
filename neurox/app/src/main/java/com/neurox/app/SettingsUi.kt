@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.neurox.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neurox.data.SavedThemeEntity
import com.neurox.design.*
import com.neurox.domain.*

@Composable
internal fun ProfileScreen(state: AppState, vm: AppViewModel) {
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val active = requireNotNull(state.profile)
    LaunchedEffect(active.id) { showCreate = false }
    LaunchedEffect(state.profiles) { if (state.profiles.none { it.id == deleting }) deleting = null }
    Page {
        SectionTitle("NEUROX IDENTITY", tr("مسیر شخصی شما", "Your personal journey"), tr("هر پروفایل تاریخچه، تنظیمات و علاقه‌مندی‌های جداگانه دارد.", "Each profile has separate history, preferences and favorites."))
        state.profiles.forEach { profile -> Panel {
            Text(if (profile.guest) tr("مهمان", "Guest") else profile.name, style = MaterialTheme.typography.titleLarge)
            if (active.id == profile.id) Text(tr("پروفایل فعال", "Active profile"), color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (active.id != profile.id) Button(onClick = { vm.selectProfile(profile.id) }, enabled = !state.busy) { Text(tr("انتخاب", "Switch")) }
                if (!profile.guest) TextButton(onClick = { deleting = profile.id }, enabled = !state.busy) { Text(tr("حذف پروفایل", "Delete profile"), color = MaterialTheme.colorScheme.error) }
            }
        } }
        Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) { Text(tr("ساخت پروفایل", "Create profile")) }
        Text(tr("داده‌ها داخل فضای خصوصی برنامه می‌مانند. قفل PIN و حفاظت مبتنی بر Keystore هنوز پیاده‌سازی نشده‌اند؛ تغییر پروفایل در این نسخه مرز امنیتی در برابر کسی که به دستگاه دسترسی دارد نیست.", "Data stays in the app sandbox. PIN and Keystore protection are not implemented yet. Profile switching is not a security boundary against a person with device access in this milestone."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showCreate) AlertDialog(onDismissRequest = { showCreate = false }, title = { Text(tr("پروفایل جدید", "New profile")) }, text = {
        OutlinedTextField(value = name, onValueChange = { if (it.length <= 40) name = it }, label = { Text(tr("نام نمایشی", "Display name")) }, singleLine = true)
    }, confirmButton = { TextButton(onClick = { vm.createProfile(name) }, enabled = name.trim().isNotEmpty() && name.none { it.isISOControl() } && !state.busy) { Text(tr("ساخت", "Create")) } }, dismissButton = { TextButton(onClick = { showCreate = false }) { Text(tr("انصراف", "Cancel")) } })
    if (deleting != null) AlertDialog(onDismissRequest = { deleting = null }, title = { Text(tr("پروفایل حذف شود؟", "Delete this profile?")) }, text = { Text(tr("همهٔ جلسات، علاقه‌مندی‌ها و پوسته‌های این پروفایل برای همیشه حذف می‌شوند. بازگشت ممکن نیست.", "All sessions, favorites and saved themes for this profile will be deleted. This cannot be undone.")) }, confirmButton = { TextButton(onClick = { deleting?.let(vm::deleteProfile) }, enabled = !state.busy) { Text(tr("حذف دائمی", "Delete permanently")) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("انصراف", "Cancel")) } })
}

@Composable
internal fun SettingsScreen(state: AppState, vm: AppViewModel) {
    val profile = requireNotNull(state.profile)
    var textScale by rememberSaveable(profile.id, profile.textScale) { mutableFloatStateOf(profile.textScale) }
    Page {
        SectionTitle("PREFERENCES", tr("مطابق با شما", "Make it yours"))
        Panel {
            Text(tr("زبان و جهت نوشتار", "Language & direction"), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = profile.language == "FA", onClick = { vm.updateProfile(profile.copy(language = "FA")) }, enabled = !state.busy, label = { Text("فارسی") })
                FilterChip(selected = profile.language == "EN", onClick = { vm.updateProfile(profile.copy(language = "EN")) }, enabled = !state.busy, label = { Text("English") })
            }
        }
        Panel {
            Text(tr("روشنایی پوسته", "Appearance"), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DARK" to tr("تیره", "Dark"), "LIGHT" to tr("روشن", "Light"), "SYSTEM" to tr("سیستم", "System")).forEach { (value, label) ->
                    FilterChip(selected = profile.theme == value, onClick = { vm.updateProfile(profile.copy(theme = value)) }, label = { Text(label) }, enabled = !state.busy)
                }
            }
            OutlinedButton(onClick = { vm.navigate(Screen.COLOR) }, modifier = Modifier.fillMaxWidth()) { Text(tr("استودیو رنگ", "Open Color Studio")) }
        }
        Panel {
            Text(tr("اندازهٔ نوشته: ${number(textScale * 100)}٪", "Text scale: ${number(textScale * 100)}%"))
            Slider(value = textScale, onValueChange = { textScale = it }, onValueChangeFinished = { vm.updateProfile(profile.copy(textScale = textScale)) }, valueRange = 0.85f..1.5f, enabled = !state.busy)
            Text(tr("اندازهٔ قلم سیستم نیز رعایت می‌شود.", "System font scaling is also respected."), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("حرکت کمتر", "Reduced motion"), modifier = Modifier.weight(1f))
                Switch(checked = profile.reducedMotion, onCheckedChange = { vm.updateProfile(profile.copy(reducedMotion = it)) }, enabled = !state.busy)
            }
            Text(tr("در این نسخه انیمیشن تزئینی فعال نیست.", "Decorative animation is not enabled in this milestone."), style = MaterialTheme.typography.bodySmall)
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("پیشنهاد تطبیقی آفلاین", "Offline adaptive suggestions"), modifier = Modifier.weight(1f))
                Switch(checked = profile.adaptive, onCheckedChange = { vm.updateProfile(profile.copy(adaptive = it)) }, enabled = !state.busy)
            }
            Text(tr("تغییر سطح خودکار اعمال نمی‌شود. می‌توانید هر پیشنهاد را رد کنید.", "Difficulty does not change without your approval. You can reject any suggestion."), style = MaterialTheme.typography.bodySmall)
        }
        Panel {
            Text("NEUROX · 0.2.0-dev", style = MaterialTheme.typography.titleMedium)
            Text(tr("نسخهٔ توسعه؛ آمادهٔ انتشار نهایی نیست. بدون مجوز اینترنت، بدون سرویس ابری و بدون آمار ساختگی.", "Development milestone. Not a production release. No internet permission, cloud service or invented statistics."))
            Text(tr("پیش از پاک‌کردن برنامه توجه کنید: رابط پشتیبان‌گیری و بازیابی هنوز آماده نیست.", "Before uninstalling: the backup and restore interface is not available yet."), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun ColorStudio(state: AppState, vm: AppViewModel) {
    val profile = requireNotNull(state.profile)
    var darkHex by rememberSaveable { mutableStateOf(String.format("%06X", profile.accent and 0xFFFFFF)) }
    var lightHex by rememberSaveable { mutableStateOf(String.format("%06X", profile.lightAccent and 0xFFFFFF)) }
    var previewDark by rememberSaveable { mutableStateOf(true) }
    var themeName by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<SavedThemeEntity?>(null) }
    val dark = ColorMath.parseHex(darkHex)
    val light = ColorMath.parseHex(lightHex)
    val validName = themeName.trim().length in 1..30 && themeName.none { it.isISOControl() }
    Page {
        SectionTitle("COLOR STUDIO", tr("رنگِ تمرکز شما", "Your color of focus"), tr("رنگ‌ها تغییر می‌کنند؛ منطق تمرین و چیدمان ثابت می‌مانند.", "Colors change. Training logic and layout stay the same."))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = previewDark, onClick = { previewDark = true }, label = { Text(tr("پیش‌نمایش تیره", "Dark preview")) })
            FilterChip(selected = !previewDark, onClick = { previewDark = false }, label = { Text(tr("پیش‌نمایش روشن", "Light preview")) })
        }
        NeuroxTheme(if (previewDark) "DARK" else "LIGHT", (if (previewDark) dark else light) ?: profile.accent) {
            Surface(color = MaterialTheme.colorScheme.background, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    NeuroxEmblem()
                    Text("NEUROX / VISION", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text(tr("دقت در جزئیات", "Precision in every detail"), color = MaterialTheme.colorScheme.primary)
                    LinearProgressIndicator(progress = { 0.5f }, modifier = Modifier.fillMaxWidth())
                    Text(tr("نمونهٔ نمایشی رنگ؛ این نوار آمار تمرین نیست.", "Theme sample only. This bar is not training data."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AccentPresets.forEach { preset -> FilterChip(selected = dark == preset.dark && light == preset.light, onClick = {
                darkHex = String.format("%06X", preset.dark and 0xFFFFFF); lightHex = String.format("%06X", preset.light and 0xFFFFFF)
            }, label = { Text(tr(preset.fa, preset.en)) }, leadingIcon = { Box(Modifier.size(12.dp).background(Color(preset.dark), MaterialTheme.shapes.small)) }) }
        }
        Panel {
            Text(tr("رنگ سفارشی · RGB", "Custom accent · RGB"), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = darkHex, onValueChange = { if (it.length <= 7) darkHex = it }, label = { Text(tr("رنگ تیره، مثل D4B982", "Dark accent, e.g. D4B982")) }, isError = dark == null, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = lightHex, onValueChange = { if (it.length <= 7) lightHex = it }, label = { Text(tr("رنگ روشن، مثل 765A28", "Light accent, e.g. 765A28")) }, isError = light == null, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(tr("برای خوانایی، رنگ نهایی ممکن است روشن‌تر یا تیره‌تر شود.", "The applied color can be adjusted to maintain readable contrast."), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { vm.updateProfile(profile.copy(accent = requireNotNull(dark), lightAccent = requireNotNull(light))) }, enabled = dark != null && light != null && !state.busy, modifier = Modifier.fillMaxWidth()) { Text(tr("اعمال در برنامه", "Apply to application")) }
            OutlinedTextField(value = themeName, onValueChange = { if (it.length <= 30) themeName = it }, label = { Text(tr("نام پوسته", "Theme name")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedButton(onClick = { vm.saveTheme(themeName, requireNotNull(dark), requireNotNull(light), editingId) }, enabled = validName && dark != null && light != null && !state.busy) { Text(if (editingId == null) tr("ذخیرهٔ پوسته", "Save theme") else tr("ثبت تغییرات و نام", "Save changes & name")) }
            if (editingId != null) TextButton(onClick = { editingId = null; themeName = "" }) { Text(tr("ساخت پوستهٔ جدید", "Create a new theme")) }
        }
        if (state.themes.isNotEmpty()) Text(tr("پوسته‌های ذخیره‌شده", "SAVED THEMES"), style = MaterialTheme.typography.titleMedium)
        state.themes.forEach { theme -> Panel {
            Text(theme.name, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.updateProfile(profile.copy(accent = theme.darkAccent, lightAccent = theme.lightAccent)) }, enabled = !state.busy) { Text(tr("اعمال", "Apply")) }
                TextButton(onClick = { editingId = theme.id; themeName = theme.name; darkHex = String.format("%06X", theme.darkAccent and 0xFFFFFF); lightHex = String.format("%06X", theme.lightAccent and 0xFFFFFF) }) { Text(tr("ویرایش و تغییر نام", "Edit & rename")) }
                TextButton(onClick = { deleting = theme }) { Text(tr("حذف", "Delete")) }
            }
        } }
        TextButton(onClick = { darkHex = "D4B982"; lightHex = "765A28"; vm.updateProfile(profile.copy(accent = 0xFFD4B982, lightAccent = 0xFF765A28)) }, enabled = !state.busy) { Text(tr("بازگردانی رنگ پیش‌فرض", "Restore default colors")) }
    }
    deleting?.let { theme -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(tr("پوسته حذف شود؟", "Delete theme?")) }, text = { Text(theme.name) }, confirmButton = { TextButton(onClick = { vm.deleteTheme(theme.id); if (editingId == theme.id) { editingId = null; themeName = "" }; deleting = null }) { Text(tr("حذف", "Delete")) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("انصراف", "Cancel")) } }) }
}
