package com.typflow.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typflow.app.data.TypflowStore
import com.typflow.app.theme.TypflowTheme

private data class Achievement(val icon: String, val titleFa: String, val titleEn: String, val unlocked: Boolean)

@Composable
fun ProfileScreen(
    theme: TypflowTheme,
    store: TypflowStore,
    onNavigate: (String) -> Unit
) {
    val lang = store.lang
    val level = store.totalSessions / 5 + 1
    val levelProgress = ((store.totalSessions % 5) / 5f)

    val achievements = listOf(
        Achievement("🚀", "اولین قدم", "First step", store.totalSessions >= 1),
        Achievement("⚡", "سرعت 30 WPM", "30 WPM speed", store.bestWpm >= 30),
        Achievement("🔥", "سرعت 50 WPM", "50 WPM speed", store.bestWpm >= 50),
        Achievement("💎", "سرعت 70 WPM", "70 WPM speed", store.bestWpm >= 70),
        Achievement("🎯", "دقت 90٪", "90% accuracy", store.bestAccuracy >= 90),
        Achievement("📚", "تندخوان 300 WPM", "300 WPM reading", store.bestReadingWpm >= 300),
        Achievement("🏅", "10 جلسه تمرین", "10 sessions", store.totalSessions >= 10),
        Achievement("👑", "50 جلسه تمرین", "50 sessions", store.totalSessions >= 50)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle(theme, tr(lang, "پروفایل", "Profile"))

        // Identity card
        GlassCard(theme, Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(theme.gradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "M",
                        color = Color.Black,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.padding(horizontal = 8.dp))
                Column {
                    Text(
                        text = "mrsad",
                        color = theme.onBackground,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = tr(lang, "سطح ", "Level ") + level.toString(),
                        color = theme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { levelProgress },
                        modifier = Modifier.fillMaxWidth(0.55f),
                        color = theme.primary,
                        trackColor = theme.surfaceVariant
                    )
                }
            }
        }

        // Records
        SectionTitle(theme, tr(lang, "رکوردها", "Records"))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(theme, tr(lang, "بهترین سرعت تایپ", "Best typing"), store.bestWpm, "WPM", Modifier.weight(1f))
            StatCard(theme, tr(lang, "بهترین مطالعه", "Best reading"), store.bestReadingWpm, "WPM", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(theme, tr(lang, "بهترین دقت", "Best accuracy"), store.bestAccuracy, "%", Modifier.weight(1f))
            StatCard(theme, tr(lang, "مجموع تمرین", "Total practice"), store.totalMinutes, tr(lang, "دقیقه", "min"), Modifier.weight(1f))
        }

        // Achievements
        SectionTitle(theme, tr(lang, "دستاوردها", "Achievements"))
        achievements.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { a ->
                    GlassCard(theme, Modifier.weight(1f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = a.icon,
                                fontSize = 30.sp,
                                modifier = Modifier.alpha(if (a.unlocked) 1f else 0.35f)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (lang == "fa") a.titleFa else a.titleEn,
                                color = if (a.unlocked) theme.onBackground else theme.onSurface.copy(alpha = 0.45f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (a.unlocked) tr(lang, "باز شده ✅", "Unlocked ✅") else tr(lang, "قفل 🔒", "Locked 🔒"),
                                color = if (a.unlocked) theme.success else theme.onSurface.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        GradientButton(
            theme,
            tr(lang, "⚙️ تنظیمات و تم‌ها", "⚙️ Settings & themes"),
            { onNavigate("settings") },
            Modifier.fillMaxWidth()
        )
    }
}

private fun Modifier.alphaCompat(unlocked: Boolean): Modifier =
    if (unlocked) this else this.then(Modifier.alphaHalf())

private fun Modifier.alphaHalf(): Modifier = this
