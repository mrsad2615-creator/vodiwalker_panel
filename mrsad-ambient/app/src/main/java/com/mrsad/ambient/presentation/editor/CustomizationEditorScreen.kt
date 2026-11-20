package com.mrsad.ambient.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrsad.ambient.data.local.UserPreferences
import com.mrsad.ambient.designsystem.MrsadColors
import com.mrsad.ambient.domain.model.AmbientProfile
import com.mrsad.ambient.domain.model.BatteryState
import com.mrsad.ambient.domain.model.ClockConfig
import com.mrsad.ambient.domain.model.ClockStyle
import com.mrsad.ambient.domain.model.ProfileType
import com.mrsad.ambient.presentation.ambient.AmbientDisplayScreen

@Composable
fun CustomizationEditorScreen(
    prefs: UserPreferences,
    battery: BatteryState,
    onUpdateClock: (ClockConfig) -> Unit,
    onSelectProfile: (ProfileType) -> Unit
) {
    val usePersian = prefs.language == "fa"
    var previewProfile by remember(prefs.activeProfile) { mutableStateOf(prefs.activeProfile) }
    val profileObj = AmbientProfile.defaults().find { it.type == previewProfile } ?: AmbientProfile.defaults().first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (usePersian) "ویرایشگر زنده (شبیه‌سازی Poco X3 Pro)" else "Live Editor (Poco X3 Pro Simulation)",
            color = MrsadColors.PureWhite,
            fontSize = 16.sp
        )

        // Device-proportional preview (20:9 aspect ratio for Poco X3 Pro)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(9f / 20f)
                .align(Alignment.CenterHorizontally)
                .background(MrsadColors.PureBlack, RoundedCornerShape(16.dp))
                .border(1.5.dp, MrsadColors.BorderCrisp, RoundedCornerShape(16.dp))
        ) {
            AmbientDisplayScreen(
                clockConfig = prefs.clockConfig.copy(scale = prefs.clockConfig.scale * 0.65f),
                profile = profileObj,
                battery = battery,
                usePersian = usePersian
            )
        }

        // Clock Style Selector
        Text(
            text = if (usePersian) "سبک ساعت" else "Clock Style",
            color = MrsadColors.GraySecondary,
            fontSize = 12.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ClockStyle.entries.forEach { style ->
                val selected = prefs.clockConfig.style == style
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            width = 1.dp,
                            color = if (selected) MrsadColors.PureWhite else MrsadColors.BorderSubtle,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onUpdateClock(prefs.clockConfig.copy(style = style)) }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (usePersian) style.titleFa else style.titleEn,
                        color = if (selected) MrsadColors.PureWhite else MrsadColors.GrayMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Scale Slider
        Text(
            text = if (usePersian) "اندازه ساعت" else "Clock Scale",
            color = MrsadColors.GraySecondary,
            fontSize = 12.sp
        )
        Slider(
            value = prefs.clockConfig.scale,
            onValueChange = { onUpdateClock(prefs.clockConfig.copy(scale = it)) },
            valueRange = 0.7f..1.3f
        )

        // Toggles
        EditorToggleRow(
            label = if (usePersian) "اعداد فارسی" else "Persian Numerals",
            checked = prefs.clockConfig.usePersianNumerals,
            onCheckedChange = { onUpdateClock(prefs.clockConfig.copy(usePersianNumerals = it)) }
        )
        EditorToggleRow(
            label = if (usePersian) "نمایش ثانیه" else "Show Seconds",
            checked = prefs.clockConfig.showSeconds,
            onCheckedChange = { onUpdateClock(prefs.clockConfig.copy(showSeconds = it)) }
        )
        EditorToggleRow(
            label = if (usePersian) "قالب ۲۴ ساعته" else "24-Hour Format",
            checked = prefs.clockConfig.use24Hour,
            onCheckedChange = { onUpdateClock(prefs.clockConfig.copy(use24Hour = it)) }
        )

        // Profile Preview Switcher
        Text(
            text = if (usePersian) "پیش‌نمایش پروفایل" else "Profile Preview",
            color = MrsadColors.GraySecondary,
            fontSize = 12.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(ProfileType.DAY, ProfileType.NIGHT, ProfileType.BATTERY_SAVER).forEach { p ->
                val selected = previewProfile == p
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            1.dp,
                            if (selected) MrsadColors.PureWhite else MrsadColors.BorderSubtle,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable {
                            previewProfile = p
                            onSelectProfile(p)
                        }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (usePersian) p.titleFa else p.titleEn,
                        color = if (selected) MrsadColors.PureWhite else MrsadColors.GrayMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MrsadColors.BorderSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = MrsadColors.PureWhite, fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
