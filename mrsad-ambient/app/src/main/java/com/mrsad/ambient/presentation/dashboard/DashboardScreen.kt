package com.mrsad.ambient.presentation.dashboard

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrsad.ambient.data.local.UserPreferences
import com.mrsad.ambient.designsystem.MrsadColors
import com.mrsad.ambient.designsystem.MrsadIcon
import com.mrsad.ambient.designsystem.MrsadIconType
import com.mrsad.ambient.designsystem.MrsadWordmark
import com.mrsad.ambient.designsystem.NumeralFormatter
import com.mrsad.ambient.display.AmbientDisplayActivity
import com.mrsad.ambient.domain.model.BatteryState
import com.mrsad.ambient.domain.model.ProfileType
import com.mrsad.ambient.domain.model.SensorStatus
import com.mrsad.ambient.domain.recommendation.RecommendationEngine
import com.mrsad.ambient.presentation.ambient.HybridClock
import com.mrsad.ambient.system.PersianCalendarHelper

@Composable
fun DashboardScreen(
    prefs: UserPreferences,
    battery: BatteryState,
    sensors: SensorStatus,
    onToggleAmbient: (Boolean) -> Unit,
    onSelectProfile: (ProfileType) -> Unit,
    onOpenEditor: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val usePersian = prefs.language == "fa"
    val date = remember { PersianCalendarHelper.now() }
    val recommendations = remember(battery, prefs.activeProfile) {
        RecommendationEngine.evaluate(battery, prefs.activeProfile)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MrsadWordmark()
            Switch(
                checked = prefs.ambientEnabled,
                onCheckedChange = onToggleAmbient
            )
        }

        // Clock & Date Preview
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MrsadColors.BorderSubtle, RoundedCornerShape(10.dp))
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HybridClock(config = prefs.clockConfig.copy(scale = 0.85f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (usePersian) {
                    "${date.weekdayFa} ${NumeralFormatter.format("${date.day}", true)} ${date.monthNameFa}"
                } else {
                    "${date.weekdayEn} · ${date.gregorianFormatted}"
                },
                color = MrsadColors.GraySecondary,
                fontSize = 13.sp
            )
        }

        // Launch Ambient Session
        Button(
            onClick = {
                val intent = Intent(context, AmbientDisplayActivity::class.java).apply {
                    putExtra("trigger", "MANUAL")
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MrsadColors.PureWhite,
                contentColor = MrsadColors.PureBlack
            )
        ) {
            Text(
                text = if (usePersian) "اجرای نمایشگر امبینت" else "Launch Ambient Session",
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        // Status Row: Profile & Battery
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusTile(
                modifier = Modifier.weight(1f),
                icon = MrsadIconType.CLOCK,
                label = if (usePersian) "پروفایل فعال" else "Active Profile",
                value = if (usePersian) prefs.activeProfile.titleFa else prefs.activeProfile.titleEn
            )
            StatusTile(
                modifier = Modifier.weight(1f),
                icon = MrsadIconType.BATTERY,
                label = if (usePersian) "باتری" else "Battery",
                value = NumeralFormatter.format("${battery.percentage}%", prefs.clockConfig.usePersianNumerals)
            )
        }

        // Sensor Availability
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MrsadColors.BorderSubtle, RoundedCornerShape(8.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MrsadIcon(type = MrsadIconType.SENSORS, size = 16.dp)
                Text(
                    text = if (usePersian) "وضعیت حسگرها" else "Hardware Sensors",
                    color = MrsadColors.PureWhite,
                    fontSize = 13.sp
                )
            }
            val sensorSummary = buildString {
                append(if (usePersian) "مجاورت: " else "Proximity: ")
                append(if (sensors.hasProximity) "✓  " else "✗  ")
                append(if (usePersian) "نور: " else "Light: ")
                append(if (sensors.hasLightSensor) "✓  " else "✗  ")
                append(if (usePersian) "شتاب‌سنج: " else "Accel: ")
                append(if (sensors.hasAccelerometer) "✓" else "✗")
            }
            Text(text = sensorSummary, color = MrsadColors.GraySecondary, fontSize = 12.sp)
        }

        // Rule-based Recommendations
        recommendations.forEach { rec ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MrsadColors.BorderCrisp, RoundedCornerShape(8.dp))
                    .clickable {
                        rec.suggestedProfile?.let(onSelectProfile)
                    }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (usePersian) rec.titleFa else rec.titleEn,
                    color = MrsadColors.PureWhite,
                    fontSize = 13.sp
                )
                Text(
                    text = if (usePersian) rec.descriptionFa else rec.descriptionEn,
                    color = MrsadColors.GraySecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Shortcuts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShortcutButton(
                modifier = Modifier.weight(1f),
                title = if (usePersian) "سفارشی‌سازی" else "Customize",
                onClick = onOpenEditor
            )
            ShortcutButton(
                modifier = Modifier.weight(1f),
                title = if (usePersian) "تنظیمات" else "Settings",
                onClick = onOpenSettings
            )
        }

        // IPS LCD Hardware Notice
        Text(
            text = if (usePersian) {
                "توجه سخت‌افزاری: نمایشگر Poco X3 Pro از نوع IPS LCD است؛ جلسات نمایش با زمان‌بندی کوتاه و خاموشی خودکار اجرا می‌شوند."
            } else {
                "Hardware Notice: Poco X3 Pro uses an IPS LCD panel; sessions use controlled short wakeups and automatic screen-off."
            },
            color = MrsadColors.GrayMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun StatusTile(
    icon: MrsadIconType,
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MrsadIcon(type = icon, size = 15.dp, tint = MrsadColors.GraySecondary)
            Text(text = label, color = MrsadColors.GraySecondary, fontSize = 11.sp)
        }
        Text(text = value, color = MrsadColors.PureWhite, fontSize = 15.sp)
    }
}

@Composable
private fun ShortcutButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .border(1.dp, MrsadColors.BorderSubtle, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, color = MrsadColors.PureWhite, fontSize = 13.sp)
    }
}
