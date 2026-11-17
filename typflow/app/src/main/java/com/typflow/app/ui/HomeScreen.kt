package com.typflow.app.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typflow.app.data.TypflowStore
import com.typflow.app.theme.TypflowTheme
import com.typflow.app.theme.themeById

@Composable
fun HomeScreen(
    theme: TypflowTheme,
    store: TypflowStore,
    onNavigate: (String) -> Unit
) {
    val lang = store.lang
    val todayMinutes = store.minutesToday()
    val goalProgress = if (store.dailyGoal > 0) todayMinutes.toFloat() / store.dailyGoal.toFloat() else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = tr(lang, "خوش آمدید 👋", "Welcome 👋"),
                    color = theme.onSurface.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
                HoloText("TYPFLOW", theme, 30)
            }
            Box(contentAlignment = Alignment.Center) {
                ProgressRing(theme, goalProgress, size = 74.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(goalProgress * 100).toInt()}%",
                        color = theme.onBackground,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                    Text(
                        text = tr(lang, "هدف امروز", "Today"),
                        color = theme.onSurface.copy(alpha = 0.7f),
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Big stats
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(theme, tr(lang, "سرعت تایپ", "Typing speed"), store.bestWpm, "WPM", Modifier.weight(1f))
            StatCard(theme, tr(lang, "سرعت مطالعه", "Reading speed"), store.bestReadingWpm, "WPM", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(theme, tr(lang, "بهترین دقت", "Best accuracy"), store.bestAccuracy, "%", Modifier.weight(1f))
            StatCard(theme, tr(lang, "جلسات تمرین", "Sessions"), store.totalSessions, tr(lang, "جلسه", "sessions"), Modifier.weight(1f))
        }

        // Quick actions
        SectionTitle(theme, tr(lang, "دسترسی سریع", "Quick access"))
        GradientButton(
            theme,
            tr(lang, "⌨️  شروع تمرین تایپ", "⌨️  Start typing practice"),
            { onNavigate("typing") },
            Modifier.fillMaxWidth()
        )
        GradientButton(
            theme,
            tr(lang, "📖  شروع تندخوانی RSVP", "📖  Start RSVP reading"),
            { onNavigate("reading") },
            Modifier.fillMaxWidth()
        )

        // Daily progress detail
        GlassCard(theme, Modifier.fillMaxWidth()) {
            Column {
                SectionTitle(theme, tr(lang, "پیشرفت روزانه", "Daily progress"))
                Spacer(Modifier.height(10.dp))
                Text(
                    text = tr(lang, "امروز ", "Today ") + todayMinutes.toString() +
                        tr(lang, " دقیقه از ", " min of ") + store.dailyGoal.toString() +
                        tr(lang, " دقیقه هدف", " min goal"),
                    color = theme.onSurface,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { goalProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = theme.primary,
                    trackColor = theme.surfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = tr(lang, "مجموع زمان تمرین: ", "Total practice time: ") +
                        store.totalMinutes.toString() + tr(lang, " دقیقه", " min"),
                    color = theme.onSurface.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
        }

        // Current theme chip
        GlassCard(theme, Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = tr(lang, "تم فعلی", "Current theme"),
                        color = theme.onSurface.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = if (lang == "fa") theme.nameFa else theme.name,
                        color = theme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                GhostButton(theme, tr(lang, "تغییر تم", "Change theme"), { onNavigate("settings") })
            }
        }

        // Footer credit
        Text(
            text = "TYPFLOW  •  by mrsad",
            color = theme.onSurface.copy(alpha = 0.4f),
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
    }
}
