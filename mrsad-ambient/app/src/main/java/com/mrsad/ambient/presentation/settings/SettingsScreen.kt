package com.mrsad.ambient.presentation.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrsad.ambient.BuildConfig
import com.mrsad.ambient.data.local.UserPreferences
import com.mrsad.ambient.designsystem.MrsadColors

@Composable
fun SettingsScreen(
    prefs: UserPreferences,
    onToggleLanguage: () -> Unit
) {
    val context = LocalContext.current
    val usePersian = prefs.language == "fa"
    var search by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (usePersian) "تنظیمات" else "Settings",
            color = MrsadColors.PureWhite,
            fontSize = 16.sp
        )

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(if (usePersian) "جستجو در تنظیمات..." else "Search settings...") },
            singleLine = true
        )

        SettingRow(
            title = if (usePersian) "زبان برنامه (فارسی / English)" else "Language (Persian / English)",
            subtitle = if (usePersian) "فعلی: فارسی (RTL)" else "Current: English (LTR)",
            onClick = onToggleLanguage
        )

        SettingRow(
            title = if (usePersian) "دسترسی اعلان‌ها" else "Notification Listener Access",
            subtitle = if (usePersian) "باز کردن تنظیمات رسمی اندروید" else "Open official Android settings",
            onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        )

        SettingRow(
            title = if (usePersian) "راهنمای بهینه‌سازی باتری MIUI" else "MIUI Battery Optimization Guide",
            subtitle = if (usePersian) "برای عملکرد پایدار در پس‌زمینه، محدودیت باتری را در MIUI روی بدون محدودیت بگذارید." else "For reliable background triggers on MIUI, set battery saver to No restrictions.",
            onClick = {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        )

        SettingRow(
            title = if (usePersian) "ذخیره‌سازی محلی و حریم خصوصی" else "Local Storage & Privacy",
            subtitle = if (usePersian) "تمامی داده‌ها فقط روی دستگاه ذخیره می‌شوند. بدون حساب کاربری و بدون سرور ابری." else "All data is stored locally on device. No accounts, no cloud.",
            onClick = {}
        )

        SettingRow(
            title = if (usePersian) "درباره مرصاد امبینت" else "About MRSAD Ambient",
            subtitle = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Free · Poco X3 Pro / Android 12",
            onClick = {}
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MrsadColors.BorderSubtle, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = title, color = MrsadColors.PureWhite, fontSize = 14.sp)
        Text(text = subtitle, color = MrsadColors.GraySecondary, fontSize = 12.sp)
    }
}
