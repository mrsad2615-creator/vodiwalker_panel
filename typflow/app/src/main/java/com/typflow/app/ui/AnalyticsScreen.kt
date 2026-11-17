package com.typflow.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typflow.app.data.TypflowStore
import com.typflow.app.theme.TypflowTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun AnalyticsScreen(theme: TypflowTheme, store: TypflowStore) {
    val lang = store.lang
    val typingHistory = store.history.filter { it.type == "typing" }.reversed()
    val readingHistory = store.history.filter { it.type == "reading" }.reversed()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle(theme, tr(lang, "آمار و نمودارها", "Analytics"))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(theme, tr(lang, "جلسات تایپ", "Typing sessions"), typingHistory.size, "", Modifier.weight(1f))
            StatCard(theme, tr(lang, "جلسات مطالعه", "Reading sessions"), readingHistory.size, "", Modifier.weight(1f))
        }

        // WPM line chart
        GlassCard(theme, Modifier.fillMaxWidth()) {
            Column {
                SectionTitle(theme, tr(lang, "روند سرعت تایپ (WPM)", "Typing speed trend (WPM)"))
                Spacer(Modifier.height(12.dp))
                if (typingHistory.size < 2) {
                    Text(
                        text = tr(lang, "برای دیدن نمودار حداقل ۲ جلسه تایپ انجام دهید.", "Complete at least 2 typing sessions to see the chart."),
                        color = theme.onSurface.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                } else {
                    val values = typingHistory.map { it.wpm.toFloat() }
                    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                        val maxV = (values.maxOrNull() ?: 1f).coerceAtLeast(10f)
                        val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
                        val points = values.mapIndexed { i, v ->
                            Offset(i * stepX, size.height - (v / maxV) * (size.height - 16f) - 8f)
                        }
                        // Area fill
                        val fillPath = Path().apply {
                            moveTo(points.first().x, size.height)
                            points.forEach { lineTo(it.x, it.y) }
                            lineTo(points.last().x, size.height)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                listOf(theme.primary.copy(alpha = 0.35f), Color.Transparent)
                            )
                        )
                        // Line
                        val linePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = linePath,
                            brush = Brush.horizontalGradient(theme.gradient),
                            style = Stroke(width = 3.5f)
                        )
                        // Dots
                        points.forEach { p ->
                            drawCircle(color = theme.primary, radius = 5f, center = p)
                            drawCircle(color = theme.background, radius = 2.2f, center = p)
                        }
                    }
                }
            }
        }

        // Last 7 days bar chart
        GlassCard(theme, Modifier.fillMaxWidth()) {
            Column {
                SectionTitle(theme, tr(lang, "فعالیت ۷ روز اخیر", "Last 7 days activity"))
                Spacer(Modifier.height(12.dp))
                val dayLabels = mutableListOf<String>()
                val dayCounts = mutableListOf<Int>()
                val cal = Calendar.getInstance()
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dayFmt = SimpleDateFormat("EEE", Locale.US)
                repeat(7) { offset ->
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                    val d = fmt.format(cal.time)
                    dayLabels.add(dayFmt.format(cal.time))
                    dayCounts.add(store.history.count { it.date == d })
                }
                dayLabels.reverse()
                dayCounts.reverse()
                val maxC = (dayCounts.maxOrNull() ?: 0).coerceAtLeast(1)
                Row(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    dayCounts.forEachIndexed { i, c ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = c.toString(),
                                color = theme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .width(26.dp)
                                    .height((c.toFloat() / maxC * 58f).dp.coerceAtLeast(4.dp))
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
                                    .background(Brush.verticalGradient(theme.gradient))
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = dayLabels[i],
                                color = theme.onSurface.copy(alpha = 0.6f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // Recent history
        SectionTitle(theme, tr(lang, "تاریخچه اخیر", "Recent history"))
        if (store.history.isEmpty()) {
            GlassCard(theme, Modifier.fillMaxWidth()) {
                Text(
                    text = tr(lang, "هنوز فعالیتی ثبت نشده است. اولین تمرین را شروع کنید!", "No activity yet. Start your first practice!"),
                    color = theme.onSurface.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        } else {
            store.history.take(10).forEach { h ->
                GlassCard(theme, Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (h.type == "typing") tr(lang, "⌨️ تمرین تایپ", "⌨️ Typing") else tr(lang, "📖 تندخوانی", "📖 Reading"),
                                color = theme.onBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = h.date,
                                color = theme.onSurface.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = h.wpm.toString() + " WPM",
                                color = theme.primary,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                            Text(
                                text = h.acc.toString() + "%",
                                color = theme.onSurface.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
