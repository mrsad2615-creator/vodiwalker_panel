package dev.mrsad.app.feature.settings

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.settings.GraphicsQuality
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.data.settings.ThemeVariant
import dev.mrsad.app.ui.components.MrsadPrimaryButton
import dev.mrsad.app.ui.components.MrsadSecondaryButton
import dev.mrsad.app.ui.components.MrsadSurface
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.colorsFor
import kotlinx.coroutines.launch

/**
 * Real settings: every toggle writes to DataStore and is applied app-wide
 * through [MrsadTheme] on the next frame (spec §8: theme changes must actually
 * change the application, not just a preview).
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    settings: MrsadSettings,
    onBack: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val scope = rememberCoroutineScope()
    val repo = container.settingsRepository

    AmbientBackground(PageEnvironment.SETTINGS) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MrsadTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    // RTL: back points to the right.
                    Icon(Icons.Outlined.ArrowForward, contentDescription = "بازگشت", tint = colors.textPrimary)
                }
                Spacer(Modifier.size(MrsadTokens.Spacing.xs))
                Text("تنظیمات", style = type.headline, color = colors.textPrimary)
            }

            MrsadSurface(Modifier.fillMaxWidth()) {
                Text("تم بصری", style = type.title, color = colors.textPrimary)
                Text(
                    "تم انتخابی روی همهٔ صفحات اعمال می‌شود.",
                    style = type.caption,
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(MrsadTokens.Spacing.xs))
                // Live swatches: each shows the ACTUAL palette of that theme.
                ThemeVariant.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
                        row.forEach { variant ->
                            ThemeSwatch(
                                variant = variant,
                                selected = settings.theme == variant,
                                modifier = Modifier.weight(1f),
                                onClick = { scope.launch { repo.setTheme(variant) } },
                            )
                        }
                    }
                }
            }

            MrsadSurface(Modifier.fillMaxWidth()) {
                Text("کیفیت گرافیکی", style = type.title, color = colors.textPrimary)
                Text(
                    "سطح جلوه‌های بصری، مصرف باتری را متعادل می‌کند.",
                    style = type.caption,
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(MrsadTokens.Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
                    GraphicsQuality.entries.forEach { quality ->
                        QualityChoice(
                            label = when (quality) {
                                GraphicsQuality.HIGH -> "بالا"
                                GraphicsQuality.BALANCED -> "متعادل"
                                GraphicsQuality.PERFORMANCE -> "عملکرد"
                            },
                            selected = settings.quality == quality,
                            modifier = Modifier.weight(1f),
                            onClick = { scope.launch { repo.setQuality(quality) } },
                        )
                    }
                }
            }

            MrsadSurface(Modifier.fillMaxWidth()) {
                Text("نمايش و ورودی", style = type.title, color = colors.textPrimary)
                ToggleRow(
                    title = "ارقام فارسی",
                    subtitle = "نمایش اعداد به‌صورت ۱۲۳ به‌جای 123",
                    checked = settings.persianNumerals,
                    onCheckedChange = { scope.launch { repo.setPersianNumerals(it) } },
                )
                ToggleRow(
                    title = "بازخورد لمسی",
                    subtitle = "لرزش کوتاه هنگام کلید و تکمیل تمرین",
                    checked = settings.hapticsEnabled,
                    onCheckedChange = { scope.launch { repo.setHaptics(it) } },
                )
                ToggleRow(
                    title = "کاهش حرکت",
                    subtitle = "خاموش‌کردن انیمیشن‌های پس‌زمینه (دسترس‌پذیری)",
                    checked = settings.reduceMotion,
                    onCheckedChange = { scope.launch { repo.setReduceMotion(it) } },
                )
                ToggleRow(
                    title = "افکت صوتی",
                    subtitle = "صدای بازخورد تمرین (به‌زودی فعال می‌شود)",
                    checked = settings.soundEnabled,
                    enabled = false,
                    onCheckedChange = { },
                )
            }

            MrsadSurface(Modifier.fillMaxWidth()) {
                Text("حریم خصوصی", style = type.title, color = colors.textPrimary)
                Text(
                    "همهٔ داده‌های تمرین فقط روی همین دستگاه ذخیره می‌شوند. مرصاد مجوز اینترنت ندارد و هیچ اطلاعاتی ارسال نمی‌کند.",
                    style = type.body,
                    color = colors.textSecondary,
                )
            }

            MrsadSurface(Modifier.fillMaxWidth()) {
                Text("دربارهٔ مرصاد", style = type.title, color = colors.textPrimary)
                Text("نسخهٔ ۱٫۰٫۰ — پروژهٔ MRSAD", style = type.caption, color = colors.textMuted)
                Text(
                    "فونت‌ها: Vazirmatn و Estedad با لایسنس SIL OFL 1.1.",
                    style = type.caption,
                    color = colors.textMuted,
                )
            }

            MrsadSecondaryButton(
                text = "بازگردانی تنظیمات پیش‌فرض",
                onClick = { scope.launch { repo.restoreDefaults() } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun ThemeSwatch(
    variant: ThemeVariant,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = colorsFor(variant)
    val type = MrsadTheme.type
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(MrsadTokens.Radius.md))
            .border(
                1.dp,
                if (selected) MrsadTheme.colors.borderStrong else MrsadTheme.colors.border,
                RoundedCornerShape(MrsadTokens.Radius.md),
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                .background(
                    Brush.linearGradient(
                        listOf(palette.background, palette.surface, palette.accent.copy(alpha = 0.55f))
                    )
                )
        )
        Text(
            variant.labelFa,
            style = type.label,
            color = MrsadTheme.colors.textPrimary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun QualityChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MrsadTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
            .background(if (selected) colors.accentSoft else colors.surfaceHigh.copy(alpha = 0.5f))
            .border(1.dp, if (selected) colors.borderStrong else colors.border, RoundedCornerShape(MrsadTokens.Radius.sm))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MrsadTheme.type.label, color = if (selected) colors.textPrimary else colors.textSecondary)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = type.body, color = if (enabled) colors.textPrimary else colors.textMuted)
            Text(subtitle, style = type.caption, color = colors.textMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.background,
                checkedTrackColor = colors.metalMid,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.surfaceHigh,
            ),
        )
    }
}
