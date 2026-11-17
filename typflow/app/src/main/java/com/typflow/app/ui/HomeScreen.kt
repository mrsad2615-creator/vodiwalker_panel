package com.typflow.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import com.typflow.app.data.Texts
import com.typflow.app.data.TypflowStore
import com.typflow.app.theme.TypflowTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    theme: TypflowTheme,
    store: TypflowStore,
    onNavigate: (Tab) -> Unit,
    onOpenSettings: () -> Unit
) {
    val lang = store.lang
    val today = SimpleDateFormat("EEE · d MMM", Locale.US).format(Date())
    val quoteList = if (lang == "fa") Texts.quotesFa else Texts.quotesEn
    val quote = quoteList[store.totalSessions % quoteList.size]
    val goalPct = (store.goalFraction() * 100).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 28.dp)
    ) {
        ScreenHeader(
            theme = theme,
            index = "01",
            title = tr(lang, "داشبورد", "Dashboard"),
            subtitle = "$today · ${if (lang == "fa") theme.nameFa else theme.name}"
        )

        Spacer(Modifier.height(18.dp))

        // ---- today / goal ----
        Panel(theme, Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MicroLabel(theme, tr(lang, "هدف امروز", "TODAY'S GOAL"))
                MicroLabel(theme, "${store.sessionsToday()} / ${store.dailyGoal}", color = theme.ink)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                BigFigure(theme, goalPct.toString(), "%", size = 56)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    MicroLabel(
                        theme,
                        tr(lang, "از هدف روزانه", "of daily goal"),
                        color = theme.dim
                    )
                    Spacer(Modifier.height(4.dp))
                    MicroLabel(
                        theme,
                        "${store.minutesToday()} " + tr(lang, "دقیقه تمرین", "min practiced"),
                        color = theme.dim
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Bar(theme, store.goalFraction())
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MicroLabel(
                    theme,
                    tr(lang, "زنجیره", "STREAK") + " · ${store.streak} " + tr(lang, "روز", "d"),
                    color = theme.dim
                )
                MicroLabel(
                    theme,
                    tr(lang, "بهترین", "BEST") + " · ${store.bestStreak}",
                    color = theme.dim
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- headline records ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Panel(theme, Modifier.weight(1f)) {
                MicroLabel(theme, tr(lang, "سرعت تایپ", "TYPING"))
                Spacer(Modifier.height(10.dp))
                BigFigure(theme, store.bestWpm.toString(), "WPM", size = 34)
                Spacer(Modifier.height(8.dp))
                MicroLabel(theme, tr(lang, "دقت", "ACC") + " ${store.bestAccuracy}%")
            }
            Panel(theme, Modifier.weight(1f)) {
                MicroLabel(theme, tr(lang, "سرعت مطالعه", "READING"))
                Spacer(Modifier.height(10.dp))
                BigFigure(theme, store.bestReadingWpm.toString(), "WPM", size = 34)
                Spacer(Modifier.height(8.dp))
                MicroLabel(theme, tr(lang, "درک", "COMP") + " ${store.bestComprehension}%")
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel(theme, tr(lang, "دسترسی سریع", "QUICK ACCESS"))
        Spacer(Modifier.height(12.dp))

        SolidButton(
            theme = theme,
            text = tr(lang, "شروع تمرین تایپ", "START TYPING"),
            onClick = { onNavigate(Tab.TYPING) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        LineButton(
            theme = theme,
            text = tr(lang, "شروع تندخوانی RSVP", "START RSVP READING"),
            onClick = { onNavigate(Tab.READING) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(22.dp))
        SectionLabel(theme, tr(lang, "چالش روز", "DAILY CHALLENGE"))
        Spacer(Modifier.height(12.dp))

        Panel(theme, Modifier.fillMaxWidth()) {
            val target = 3
            val done = store.sessionsToday().coerceAtMost(target)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = tr(lang, "۳ جلسه تمرین امروز", "3 sessions today"),
                    color = theme.ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                MicroLabel(theme, "$done / $target", color = theme.ink)
            }
            Spacer(Modifier.height(12.dp))
            Bar(theme, done.toFloat() / target)
            Spacer(Modifier.height(10.dp))
            MicroLabel(
                theme,
                if (done >= target) tr(lang, "تکمیل شد", "COMPLETED")
                else tr(lang, "در حال پیشرفت", "IN PROGRESS"),
                color = theme.dim
            )
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel(theme, tr(lang, "تمرکز امروز", "FOCUS LINE"))
        Spacer(Modifier.height(12.dp))
        Text(
            text = quote,
            color = theme.ink,
            fontSize = 15.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(22.dp))
        Hairline(theme)
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MicroLabel(theme, "TYPFLOW · BY MRSAD", color = theme.dim)
            LineButton(
                theme = theme,
                text = tr(lang, "تنظیمات", "SETTINGS"),
                onClick = onOpenSettings,
                modifier = Modifier.height(36.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}
