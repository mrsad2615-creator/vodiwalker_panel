package com.typflow.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typflow.app.data.TypflowStore
import com.typflow.app.theme.AllThemes
import com.typflow.app.theme.TypflowTheme

@Composable
fun SettingsScreen(theme: TypflowTheme, store: TypflowStore) {
    val lang = store.lang
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle(theme, tr(lang, "تنظیمات", "Settings"))

        // ---- Themes ----
        SectionTitle(theme, tr(lang, "🎨 تم‌های ظاهری", "🎨 Appearance themes"))
        AllThemes.forEach { t ->
            val selected = store.themeId == t.id
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(t.surface)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) t.primary else t.primary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clickable { store.setTheme(t.id) }
                    .padding(16.dp)
            ) {
                Column {
                    // Preview strip
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.horizontalGradient(t.gradient))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .background(t.background.copy(alpha = 0.55f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(10.dp)
                                        .fillMaxWidth(0.35f)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(t.primary)
                                )
                                Box(
                                    modifier = Modifier
                                        .height(10.dp)
                                        .fillMaxWidth(0.2f)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(t.secondary)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            if (lang == "fa") {
                                Text(
                                    text = t.name,
                                    color = t.primary.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 2.sp
                                )
                            }
                            Text(
                                text = if (lang == "fa") t.nameFa else t.name,
                                color = t.onBackground,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (lang == "fa") t.descriptionFa else t.descriptionEn,
                                color = t.onSurface.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                        if (selected) {
                            Text(
                                text = "✓",
                                color = t.primary,
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp
                            )
                        }
                    }
                }
            }
        }

        // ---- Language ----
        SectionTitle(theme, tr(lang, "🌐 زبان", "🌐 Language"))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip(theme, "فارسی 🇮🇷", lang == "fa") { store.updateLang("fa") }
            Chip(theme, "English 🇬🇧", lang == "en") { store.updateLang("en") }
        }

        // ---- Daily goal ----
        GlassCard(theme, Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = tr(lang, "هدف روزانه (دقیقه)", "Daily goal (minutes)"),
                        color = theme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = store.dailyGoal.toString(),
                        color = theme.primary,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }
                Slider(
                    value = store.dailyGoal.toFloat(),
                    onValueChange = { store.updateDailyGoal(it.toInt()) },
                    valueRange = 5f..60f,
                    steps = 10,
                    colors = SliderDefaults.colors(
                        thumbColor = theme.primary,
                        activeTrackColor = theme.primary,
                        inactiveTrackColor = theme.surfaceVariant
                    )
                )
            }
        }

        // ---- Toggles ----
        GlassCard(theme, Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr(lang, "🔊 صدا", "🔊 Sound"),
                        color = theme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(
                        checked = store.soundOn,
                        onCheckedChange = { store.setSound(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = theme.primary
                        )
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr(lang, "📳 لرزش", "📳 Vibration"),
                        color = theme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(
                        checked = store.vibrationOn,
                        onCheckedChange = { store.setVibration(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = theme.primary
                        )
                    )
                }
            }
        }

        // ---- Danger zone ----
        if (!confirmReset) {
            GhostButton(
                theme,
                tr(lang, "🗑 حذف همه داده‌ها", "🗑 Reset all data"),
                { confirmReset = true },
                Modifier.fillMaxWidth()
            )
        } else {
            GradientButton(
                theme,
                tr(lang, "⚠️ مطمئن هستید؟ برای تأیید بزنید", "⚠️ Are you sure? Tap to confirm"),
                {
                    store.resetAll()
                    confirmReset = false
                },
                Modifier.fillMaxWidth()
            )
        }

        // ---- About ----
        GlassCard(theme, Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HoloText("TYPFLOW", theme, 22)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = tr(lang, "نسخه ۱.۰.۰ — ساخته‌شده توسط mrsad", "Version 1.0.0 — crafted by mrsad"),
                    color = theme.onSurface.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
                Text(
                    text = tr(lang, "تمرین تایپ و تندخوانی با طراحی سینمایی", "Typing & speed-reading trainer with cinematic design"),
                    color = theme.onSurface.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }
    }
}
