package com.mrsad.ambient.presentation.themes

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrsad.ambient.designsystem.MrsadColors
import com.mrsad.ambient.domain.model.ClockConfig
import com.mrsad.ambient.domain.model.ClockStyle

data class ThemePreset(
    val id: String,
    val nameFa: String,
    val nameEn: String,
    val descFa: String,
    val descEn: String,
    val clockConfig: ClockConfig
)

@Composable
fun ThemeLibraryScreen(
    usePersian: Boolean,
    onApplyPreset: (ClockConfig) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val presets = remember {
        listOf(
            ThemePreset(
                "arch_mono",
                "معماری تک‌رنگ",
                "Architectural Mono",
                "ساعت هیبریدی با حلقه دقیق ۱۲ تایی",
                "Hybrid clock with 12-tick precision ring",
                ClockConfig(style = ClockStyle.HYBRID_ARCHITECTURAL, showAnalogTicks = true, showSeconds = false)
            ),
            ThemePreset(
                "pure_minimal",
                "مینیمال خالص",
                "Pure Minimal",
                "دیجیتال خالص با فضای منفی حداکثری",
                "Pure digital with maximum negative space",
                ClockConfig(style = ClockStyle.MINIMAL_DIGITAL, showAnalogTicks = false, showSeconds = false)
            ),
            ThemePreset(
                "chrono_precision",
                "کرونوگراف دقیق",
                "Chrono Precision",
                "نمایش ثانیه و حلقه آنالوگ فعال",
                "Active seconds with analog ring",
                ClockConfig(style = ClockStyle.ANALOG_RING, showAnalogTicks = true, showSeconds = true)
            )
        )
    }

    val filtered = presets.filter {
        query.isBlank() || it.nameFa.contains(query, true) || it.nameEn.contains(query, true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = if (usePersian) "کتابخانه پوسته‌ها و ساعت‌ها" else "Themes & Clock Library",
            color = MrsadColors.PureWhite,
            fontSize = 16.sp
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(if (usePersian) "جستجو..." else "Search...") },
            singleLine = true
        )

        filtered.forEach { preset ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MrsadColors.BorderSubtle, RoundedCornerShape(8.dp))
                    .clickable { onApplyPreset(preset.clockConfig) }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (usePersian) preset.nameFa else preset.nameEn,
                    color = MrsadColors.PureWhite,
                    fontSize = 15.sp
                )
                Text(
                    text = if (usePersian) preset.descFa else preset.descEn,
                    color = MrsadColors.GraySecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
