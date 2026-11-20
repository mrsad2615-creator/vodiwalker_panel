package com.mrsad.ambient.presentation.diagnostics

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrsad.ambient.data.local.SessionUsageEntity
import com.mrsad.ambient.designsystem.MrsadColors
import com.mrsad.ambient.designsystem.NumeralFormatter

@Composable
fun DiagnosticsScreen(
    sessions: List<SessionUsageEntity>,
    usePersian: Boolean,
    onClearReports: () -> Unit
) {
    val totalSessions = sessions.size
    val totalSeconds = sessions.sumOf { it.durationSeconds }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = if (usePersian) "مرکز گزارش‌ها و عیب‌یابی" else "Reports & Diagnostics",
            color = MrsadColors.PureWhite,
            fontSize = 16.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricBox(
                modifier = Modifier.weight(1f),
                label = if (usePersian) "تعداد جلسات (اندازه‌گیری‌شده)" else "Sessions (Measured)",
                value = NumeralFormatter.format("$totalSessions", usePersian)
            )
            MetricBox(
                modifier = Modifier.weight(1f),
                label = if (usePersian) "مجموع زمان (ثانیه)" else "Total Duration (s)",
                value = NumeralFormatter.format("$totalSeconds", usePersian)
            )
        }

        Button(
            onClick = onClearReports,
            colors = ButtonDefaults.buttonColors(
                containerColor = MrsadColors.GraphiteElevated,
                contentColor = MrsadColors.PureWhite
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (usePersian) "پاک کردن گزارش‌های ذخیره‌شده" else "Clear Stored Reports")
        }

        sessions.take(20).forEach { s ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MrsadColors.BorderSubtle, RoundedCornerShape(6.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "${s.profileName} · ${s.triggerSource}", color = MrsadColors.PureWhite, fontSize = 12.sp)
                Text(text = "${s.durationSeconds}s", color = MrsadColors.GraySecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .border(1.dp, MrsadColors.BorderSubtle, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, color = MrsadColors.GraySecondary, fontSize = 11.sp)
        Text(text = value, color = MrsadColors.PureWhite, fontSize = 20.sp)
    }
}
