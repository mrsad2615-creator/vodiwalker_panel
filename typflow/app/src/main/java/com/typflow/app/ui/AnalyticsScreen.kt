package com.typflow.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typflow.app.data.TypflowStore
import com.typflow.app.theme.TypflowTheme

@Composable
fun AnalyticsScreen(theme: TypflowTheme, store: TypflowStore) {
    val lang = store.lang
    val series = store.typingSeries(18)
    val days = store.dailyCounts(7)
    val maxDay = (days.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    val problems = store.topProblemKeys(8)
    val avgAcc = run {
        val list = store.history.filter { it.type == "typing" }
        if (list.isEmpty()) 0 else list.sumOf { it.acc } / list.size
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 28.dp)
    ) {
        ScreenHeader(
            theme = theme,
            index = "04",
            title = tr(lang, "آمار و نمودار", "Analytics"),
            subtitle = tr(
                lang,
                "همه چیز محلی و روی گوشی خودت ذخیره می شود.",
                "Everything stored locally on your device."
            )
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Panel(theme, Modifier.weight(1f)) {
                MicroLabel(theme, tr(lang, "جلسات تایپ", "TYPING"))
                Spacer(Modifier.height(8.dp))
                BigFigure(
                    theme,
                    store.history.count { it.type == "typing" }.toString(),
                    size = 32
                )
            }
            Panel(theme, Modifier.weight(1f)) {
                MicroLabel(theme, tr(lang, "جلسات مطالعه", "READING"))
                Spacer(Modifier.height(8.dp))
                BigFigure(
                    theme,
                    store.history.count { it.type == "reading" }.toString(),
                    size = 32
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Panel(theme, Modifier.weight(1f)) {
                MicroLabel(theme, tr(lang, "میانگین دقت", "AVG ACCURACY"))
                Spacer(Modifier.height(8.dp))
                BigFigure(theme, avgAcc.toString(), "%", size = 32)
            }
            Panel(theme, Modifier.weight(1f)) {
                MicroLabel(theme, tr(lang, "کل زمان", "TOTAL TIME"))
                Spacer(Modifier.height(8.dp))
                BigFigure(theme, store.totalMinutes.toString(), "MIN", size = 32)
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel(theme, tr(lang, "روند سرعت تایپ (WPM)", "TYPING SPEED TREND"))
        Spacer(Modifier.height(12.dp))

        Panel(theme, Modifier.fillMaxWidth()) {
            if (series.size < 2) {
                Text(
                    text = tr(
                        lang,
                        "برای دیدن نمودار حداقل ۲ جلسه تایپ انجام دهید.",
                        "Run at least 2 typing sessions to draw the chart."
                    ),
                    color = theme.dim,
                    fontSize = 13.sp,
                    lineHeight = 24.sp
                )
            } else {
                val maxV = series.max().coerceAtLeast(1)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    val stepX = size.width / (series.size - 1).coerceAtLeast(1)
                    // horizontal hairlines
                    for (i in 0..4) {
                        val y = size.height * i / 4f
                        drawLine(
                            color = theme.line,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            1f
                        )
                    }
                    val pts = series.mapIndexed { i, v ->
                        Offset(
                            x = i * stepX,
                            y = size.height - (v.toFloat() / maxV) * (size.height - 8f) - 4f
                        )
                    }
                    pts.forEachIndexed { i, p ->
                        if (i > 0) {
                            drawLine(theme.ink, pts[i - 1], p, 2f)
                        }
                    }
                    pts.forEach { p ->
                        drawRect(
                            color = theme.ink,
                            topLeft = Offset(p.x - 2f, p.y - 2f),
                            size = androidx.compose.ui.geometry.Size(4f, 4f)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MicroLabel(theme, "${series.first()} WPM")
                    MicroLabel(theme, "MAX ${maxV} WPM", color = theme.ink)
                    MicroLabel(theme, "${series.last()} WPM")
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel(theme, tr(lang, "فعالیت ۷ روز اخیر", "LAST 7 DAYS"))
        Spacer(Modifier.height(12.dp))

        Panel(theme, Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEach { (label, count) ->
                    val h = (count.toFloat() / maxDay * 70f).dp.coerceAtLeast(3.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = count.toString(),
                            color = if (count > 0) theme.ink else theme.dim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(h)
                                .background(if (count > 0) theme.ink else theme.line)
                        )
                        Spacer(Modifier.height(8.dp))
                        MicroLabel(theme, label, size = 9)
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel(theme, tr(lang, "کلیدهای مشکل‌دار", "PROBLEM KEYS"))
        Spacer(Modifier.height(12.dp))

        Panel(theme, Modifier.fillMaxWidth()) {
            if (problems.isEmpty()) {
                Text(
                    text = tr(
                        lang,
                        "هنوز خطایی ثبت نشده. با یک جلسه تایپ شروع کن.",
                        "No errors recorded yet. Start a typing session."
                    ),
                    color = theme.dim,
                    fontSize = 13.sp,
                    lineHeight = 24.sp
                )
            } else {
                val top = problems.first().second.coerceAtLeast(1)
                problems.forEachIndexed { index, (ch, count) ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(30.dp)
                                .height(30.dp)
                                .border(1.dp, theme.line),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(ch.toString(), color = theme.ink, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) {
                            Bar(theme, count.toFloat() / top, height = 2.dp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = count.toString(),
                            color = theme.dim,
                            fontSize = 11.sp,
                            fontFamily = uiFont(theme)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel(theme, tr(lang, "تاریخچه اخیر", "RECENT HISTORY"))
        Spacer(Modifier.height(12.dp))

        if (store.history.isEmpty()) {
            Panel(theme, Modifier.fillMaxWidth()) {
                Text(
                    text = tr(
                        lang,
                        "هنوز فعالیتی ثبت نشده است. اولین تمرین را شروع کن!",
                        "No activity yet. Start your first session!"
                    ),
                    color = theme.dim,
                    fontSize = 13.sp,
                    lineHeight = 24.sp
                )
            }
        } else {
            store.history.take(8).forEachIndexed { index, entry ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, theme.line)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MicroLabel(
                        theme,
                        if (entry.type == "typing") tr(lang, "تایپ", "TYPE")
                        else tr(lang, "خواندن", "READ"),
                        color = theme.ink
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = "${entry.wpm} WPM",
                        color = theme.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${entry.acc}%",
                        color = theme.dim,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.weight(1f))
                    MicroLabel(theme, entry.date, size = 9)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}
