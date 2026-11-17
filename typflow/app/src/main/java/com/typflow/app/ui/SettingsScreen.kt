package com.typflow.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typflow.app.data.TypflowStore
import com.typflow.app.theme.AllThemes
import com.typflow.app.theme.TypflowTheme

@Composable
fun SettingsScreen(
    theme: TypflowTheme,
    store: TypflowStore,
    onBack: () -> Unit
) {
    val lang = store.lang
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 28.dp)
    ) {
        ScreenHeader(
            theme = theme,
            index = "06",
            title = tr(lang, "تنظیمات", "Settings"),
            subtitle = tr(
                lang,
                "چهار تم سیاه و سفید، زبان و رفتار تمرین.",
                "Four monochrome themes, language, behaviour."
            )
        )

        Spacer(Modifier.height(16.dp))
        SectionLabel(theme, tr(lang, "تم‌ها", "THEMES"))
        Spacer(Modifier.height(12.dp))

        AllThemes.forEach { item ->
            val selected = store.themeId == item.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) item.surface else theme.bg)
                    .border(
                        1.dp,
                        if (selected) theme.ink else theme.line
                    )
                    .clickable { store.setTheme(item.id) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // monochrome preview swatch
                Row(
                    modifier = Modifier
                        .width(64.dp)
                        .height(40.dp)
                        .background(item.bg)
                        .border(1.dp, item.line),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .width(20.dp)
                            .height(20.dp)
                            .background(item.ink)
                    )
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Box(Modifier.width(18.dp).height(2.dp).background(item.dim))
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.width(12.dp).height(2.dp).background(item.line))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        color = theme.ink,
                        fontFamily = uiFont(theme),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.6.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (lang == "fa") item.nameFa + " · " + item.descriptionFa
                        else item.descriptionEn,
                        color = theme.dim,
                        fontSize = 11.sp,
                        lineHeight = 18.sp
                    )
                }
                if (selected) {
                    Box(
                        Modifier
                            .width(14.dp)
                            .height(14.dp)
                            .background(theme.ink)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel(theme, tr(lang, "زبان", "LANGUAGE"))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MonoChip(theme, "فارسی", lang == "fa", onClick = { store.updateLang("fa") })
            MonoChip(theme, "ENGLISH", lang == "en", onClick = { store.updateLang("en") })
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel(theme, tr(lang, "تمرین", "PRACTICE"))
        Spacer(Modifier.height(12.dp))

        Panel(theme, Modifier.fillMaxWidth(), padding = 14.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MicroLabel(theme, tr(lang, "هدف روزانه (جلسه)", "DAILY GOAL (SESSIONS)"))
                MicroLabel(theme, store.dailyGoal.toString(), color = theme.ink)
            }
            Slider(
                value = store.dailyGoal.toFloat(),
                onValueChange = { store.updateDailyGoal(it.toInt()) },
                valueRange = 1f..20f,
                colors = SliderDefaults.colors(
                    thumbColor = theme.ink,
                    activeTrackColor = theme.ink,
                    inactiveTrackColor = theme.line
                )
            )
            Spacer(Modifier.height(8.dp))
            Hairline(theme)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MicroLabel(theme, tr(lang, "اندازه متن", "TEXT SIZE"))
                MicroLabel(
                    theme,
                    "${(store.textScale * 100).toInt()}%",
                    color = theme.ink
                )
            }
            Slider(
                value = store.textScale,
                onValueChange = { store.updateTextScale(it) },
                valueRange = 0.85f..1.35f,
                colors = SliderDefaults.colors(
                    thumbColor = theme.ink,
                    activeTrackColor = theme.ink,
                    inactiveTrackColor = theme.line
                )
            )
        }

        Spacer(Modifier.height(12.dp))

        Panel(theme, Modifier.fillMaxWidth(), padding = 14.dp) {
            ToggleRow(
                theme = theme,
                label = tr(lang, "صدای کلید", "Key sound"),
                hint = tr(lang, "کلیک کوتاه هنگام تایپ", "Short click while typing"),
                checked = store.soundOn,
                onChange = { store.setSound(it) }
            )
            Spacer(Modifier.height(10.dp))
            Hairline(theme)
            Spacer(Modifier.height(10.dp))
            ToggleRow(
                theme = theme,
                label = tr(lang, "لرزش", "Haptics"),
                hint = tr(lang, "بازخورد لمسی", "Vibration feedback"),
                checked = store.vibrationOn,
                onChange = { store.setVibration(it) }
            )
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel(theme, tr(lang, "داده‌ها", "DATA"))
        Spacer(Modifier.height(12.dp))

        Panel(theme, Modifier.fillMaxWidth(), padding = 14.dp) {
            Text(
                text = tr(
                    lang,
                    "همه داده‌ها فقط روی همین گوشی ذخیره می شوند. هیچ چیزی به اینترنت ارسال نمی شود.",
                    "All data lives on this device only. Nothing is uploaded."
                ),
                color = theme.dim,
                fontSize = 12.sp,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(12.dp))
            LineButton(
                theme = theme,
                text = if (confirmReset) tr(lang, "مطمئنی؟ دوباره بزن", "SURE? TAP AGAIN")
                else tr(lang, "پاک کردن همه داده‌ها", "RESET ALL DATA"),
                onClick = {
                    if (confirmReset) {
                        store.resetAll()
                        confirmReset = false
                    } else {
                        confirmReset = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel(theme, tr(lang, "درباره", "ABOUT"))
        Spacer(Modifier.height(12.dp))
        Panel(theme, Modifier.fillMaxWidth(), padding = 14.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MicroLabel(theme, "TYPFLOW", color = theme.ink)
                MicroLabel(theme, "v1.1.0")
            }
            Spacer(Modifier.height(8.dp))
            MicroLabel(
                theme,
                tr(
                    lang,
                    "ساخته شده با Kotlin و Jetpack Compose",
                    "Built with Kotlin & Jetpack Compose"
                )
            )
            Spacer(Modifier.height(4.dp))
            MicroLabel(theme, "BY MRSAD · OFFLINE FIRST")
        }

        Spacer(Modifier.height(20.dp))
        SolidButton(
            theme = theme,
            text = tr(lang, "بازگشت", "BACK"),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ToggleRow(
    theme: TypflowTheme,
    label: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, color = theme.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            MicroLabel(theme, hint)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = theme.bg,
                checkedTrackColor = theme.ink,
                uncheckedThumbColor = theme.dim,
                uncheckedTrackColor = theme.surface,
                uncheckedBorderColor = theme.line
            )
        )
    }
}
