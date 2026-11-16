package com.mesad.time.platform.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * MESAD Glance AppWidget — live clock on home screen.
 * Shows time, date (Gregorian + Jalali), next alarm.
 */
class MesadWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val now = Instant.now()
                val zone = ZoneId.systemDefault()
                val timeStr = now.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
                val dateStr = now.atZone(zone).format(DateTimeFormatter.ofPattern("EEE, MMM d"))

                Column(
                    modifier = androidx.glance.GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(androidx.compose.ui.graphics.Color(0xFF12161F)))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "MESAD",
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(androidx.compose.ui.graphics.Color(0xFFE6C79C))
                        )
                    )
                    Spacer(modifier = androidx.glance.GlanceModifier.height(4.dp))
                    Text(
                        text = timeStr,
                        style = TextStyle(
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(androidx.compose.ui.graphics.Color(0xFF06070B))
                        )
                    )
                    Text(
                        text = dateStr,
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = ColorProvider(androidx.compose.ui.graphics.Color(0xFF6B7280))
                        )
                    )
                }
            }
        }
    }
}

class MesadWidgetReceiver : androidx.glance.appwidget.GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MesadWidget()
}
