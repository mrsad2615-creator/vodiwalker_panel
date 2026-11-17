package com.typflow.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

private data class Badge(
    val code: String,
    val fa: String,
    val en: String,
    val unlocked: Boolean
)

@Composable
fun ProfileScreen(
    theme: TypflowTheme,
    store: TypflowStore,
    onOpenSettings: () -> Unit
) {
    val lang = store.lang

    val badges = listOf(
        Badge("01", "اولین جلسه", "First session", store.totalSessions >= 1),
        Badge("02", "10 جلسه", "10 sessions", store.totalSessions >= 10),
        Badge("03", "50 جلسه", "50 sessions", store.totalSessions >= 50),
        Badge("04", "سرعت 30", "30 WPM", store.bestWpm >= 30),
        Badge("05", "سرعت 50", "50 WPM", store.bestWpm >= 50),
        Badge("06", "سرعت 80", "80 WPM", store.bestWpm >= 80),
        Badge("07", "دقت 90٪", "90% acc", store.bestAccuracy >= 90),
        Badge("08", "دقت 98٪", "98% acc", store.bestAccuracy >= 98),
        Badge("09", "خواندن 300", "Read 300", store.bestReadingWpm >= 300),
        Badge("10", "خواندن 500", "Read 500", store.bestReadingWpm >= 500),
        Badge("11", "درک 100٪", "100% comp", store.bestComprehension >= 100),
        Badge("12", "زنجیره 7", "7-day streak", store.bestStreak >= 7)
    )
    val unlocked = badges.count { it.unlocked }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 28.dp)
    ) {
        ScreenHeader(
            theme = theme,
            index = "05",
            title = tr(lang, "پروفایل", "Profile"),
            subtitle = tr(
                lang,
                "سطح، رکوردها و دستاوردها.",
                "Level, records and achievements."
            )
        )

        Spacer(Modifier.height(16.dp))

        // ---- identity ----
        Panel(theme, Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "mrsad",
                        color = theme.ink,
                        fontFamily = uiFont(theme),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    )
                    Spacer(Modifier.height(4.dp))
                    MicroLabel(theme, tr(lang, "بازیکن تایپ‌فلو", "TYPFLOW PLAYER"))
                }
                Box(
                    modifier = Modifier
                        .width(54.dp)
                        .height(54.dp)
                        .border(1.dp, theme.ink),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "M",
                        color = theme.ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                MicroLabel(theme, tr(lang, "سطح", "LEVEL"), color = theme.ink)
                Text(
                    text = "${store.level}",
                    color = theme.ink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = uiFont(theme)
                )
            }
            Spacer(Modifier.height(8.dp))
            Bar(theme, store.levelProgress, height = 3.dp)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row {
                    MicroLabel(theme, "XP")
                    Spacer(Modifier.width(6.dp))
                    MicroLabel(theme, store.xp.toString(), color = theme.ink)
                }
                Row {
                    MicroLabel(theme, tr(lang, "تا سطح بعد", "TO NEXT"))
                    Spacer(Modifier.width(6.dp))
                    MicroLabel(
                        theme,
                        (250 - store.xp % 250).toString(),
                        color = theme.ink
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Panel(theme, Modifier.weight(1f)) {
                MicroLabel(theme, tr(lang, "جلسات", "SESSIONS"))
                Spacer(Modifier.height(8.dp))
                BigFigure(theme, store.totalSessions.toString(), size = 28)
            }
            Panel(theme, Modifier.weight(1f)) {
                MicroLabel(theme, tr(lang, "دقیقه", "MINUTES"))
                Spacer(Modifier.height(8.dp))
                BigFigure(theme, store.totalMinutes.toString(), size = 28)
            }
            Panel(theme, Modifier.weight(1f)) {
                MicroLabel(theme, tr(lang, "زنجیره", "STREAK"))
                Spacer(Modifier.height(8.dp))
                BigFigure(theme, store.streak.toString(), size = 28)
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel(theme, tr(lang, "رکوردها", "RECORDS"))
        Spacer(Modifier.height(12.dp))

        Panel(theme, Modifier.fillMaxWidth()) {
            RecordRow(theme, tr(lang, "بهترین سرعت تایپ", "Best typing"), store.bestWpm, "WPM")
            Spacer(Modifier.height(12.dp))
            Hairline(theme)
            Spacer(Modifier.height(12.dp))
            RecordRow(theme, tr(lang, "بهترین دقت", "Best accuracy"), store.bestAccuracy, "%")
            Spacer(Modifier.height(12.dp))
            Hairline(theme)
            Spacer(Modifier.height(12.dp))
            RecordRow(theme, tr(lang, "بهترین سرعت مطالعه", "Best reading"), store.bestReadingWpm, "WPM")
            Spacer(Modifier.height(12.dp))
            Hairline(theme)
            Spacer(Modifier.height(12.dp))
            RecordRow(theme, tr(lang, "بهترین درک مطلب", "Best comprehension"), store.bestComprehension, "%")
        }

        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel(theme, tr(lang, "دستاوردها", "ACHIEVEMENTS"), Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            MicroLabel(theme, "$unlocked / ${badges.size}", color = theme.ink)
        }
        Spacer(Modifier.height(12.dp))

        badges.chunked(2).forEach { rowBadges ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowBadges.forEach { badge ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (badge.unlocked) theme.ink else theme.bg)
                            .border(1.dp, if (badge.unlocked) theme.ink else theme.line)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Column {
                            Text(
                                text = badge.code,
                                color = if (badge.unlocked) theme.bg else theme.dim,
                                fontFamily = uiFont(theme),
                                fontSize = 9.sp,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (lang == "fa") badge.fa else badge.en,
                                color = if (badge.unlocked) theme.bg else theme.dim,
                                fontSize = 12.sp,
                                fontWeight = if (badge.unlocked) FontWeight.Bold
                                else FontWeight.Normal
                            )
                        }
                    }
                }
                if (rowBadges.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(12.dp))
        LineButton(
            theme = theme,
            text = tr(lang, "تنظیمات و تم‌ها", "SETTINGS & THEMES"),
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RecordRow(theme: TypflowTheme, label: String, value: Int, unit: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = theme.dim,
            fontSize = 12.sp
        )
        BigFigure(theme, value.toString(), unit, size = 22)
    }
}
