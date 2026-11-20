package com.mrsad.ambient.presentation.ambient

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrsad.ambient.designsystem.MrsadColors
import com.mrsad.ambient.designsystem.MrsadIcon
import com.mrsad.ambient.designsystem.MrsadIconType
import com.mrsad.ambient.designsystem.NumeralFormatter
import com.mrsad.ambient.domain.model.AmbientProfile
import com.mrsad.ambient.domain.model.BatteryState
import com.mrsad.ambient.domain.model.ClockConfig
import com.mrsad.ambient.domain.model.InformationDensity
import com.mrsad.ambient.system.PersianCalendarHelper

@Composable
fun AmbientDisplayScreen(
    clockConfig: ClockConfig,
    profile: AmbientProfile,
    battery: BatteryState,
    usePersian: Boolean,
    modifier: Modifier = Modifier
) {
    val date = remember { PersianCalendarHelper.now() }
    val dateText = if (usePersian) {
        "${date.weekdayFa} ${NumeralFormatter.format("${date.day}", true)} ${date.monthNameFa} ${NumeralFormatter.format("${date.year}", true)}"
    } else {
        "${date.weekdayEn} · ${date.gregorianFormatted}"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HybridClock(config = clockConfig)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = dateText,
                color = MrsadColors.GraySecondary,
                fontSize = 14.sp
            )
        }

        if (profile.density != InformationDensity.ESSENTIAL) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AmbientInfoCard(
                    modifier = Modifier.weight(1f),
                    icon = MrsadIconType.BATTERY,
                    title = if (usePersian) "باتری" else "Battery",
                    value = NumeralFormatter.format("${battery.percentage}%", clockConfig.usePersianNumerals),
                    subtitle = if (battery.isCharging) {
                        if (usePersian) "در حال شارژ (${battery.chargingSource})" else "Charging (${battery.chargingSource})"
                    } else {
                        if (usePersian) "آماده" else "Standby"
                    }
                )
                AmbientInfoCard(
                    modifier = Modifier.weight(1f),
                    icon = MrsadIconType.SENSORS,
                    title = if (usePersian) "پروفایل" else "Profile",
                    value = if (usePersian) profile.type.titleFa else profile.type.titleEn,
                    subtitle = if (usePersian) "${profile.sessionTimeoutSeconds} ثانیه" else "${profile.sessionTimeoutSeconds}s timeout"
                )
            }
        } else {
            Text(
                text = NumeralFormatter.format("${battery.percentage}%", clockConfig.usePersianNumerals),
                color = MrsadColors.GrayMuted,
                fontSize = 13.sp
            )
        }

        Text(
            text = if (usePersian) "برای خروج دو بار لمس کنید" else "Double-tap to close",
            color = MrsadColors.GrayMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun AmbientInfoCard(
    icon: MrsadIconType,
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .border(1.dp, MrsadColors.BorderSubtle, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MrsadIcon(type = icon, size = 16.dp, tint = MrsadColors.GraySecondary)
            Text(text = title, color = MrsadColors.GraySecondary, fontSize = 11.sp)
        }
        Text(text = value, color = MrsadColors.PureWhite, fontSize = 18.sp)
        Text(text = subtitle, color = MrsadColors.GrayMuted, fontSize = 11.sp)
    }
}
