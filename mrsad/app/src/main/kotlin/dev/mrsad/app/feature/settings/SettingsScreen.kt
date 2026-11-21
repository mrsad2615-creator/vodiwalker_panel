package dev.mrsad.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.mrsad.app.BuildConfig
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.settings.GraphicsQuality
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.data.settings.ThemeVariant
import dev.mrsad.app.testing.MrsadTestTags
import dev.mrsad.app.ui.components.MrsadMarkView
import dev.mrsad.app.ui.components.MrsadSecondaryButton
import dev.mrsad.app.ui.components.MrsadSurface
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.components.ObsidianCollapsingTopBar
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.ObsidianSpectrum
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.colorsFor
import kotlinx.coroutines.launch

/**
 * OBSIDIAN SPECTRUM — Settings Screen (Master Prompt §12).
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
    val scrollState = rememberScrollState()
    val isCollapsed by remember { derivedStateOf { scrollState.value > 28 } }

    AmbientBackground(PageEnvironment.SETTINGS) {
        Column(
            Modifier
                .testTag(MrsadTestTags.SETTINGS_ROOT)
                .fillMaxSize(),
        ) {
            ObsidianCollapsingTopBar(
                title = "تنظیمات",
                subtitle = "شخصی‌سازی OBSIDIAN SPECTRUM، کیفیت رندر و دسترس‌پذیری",
                collapsed = isCollapsed,
                spectralColor = ObsidianSpectrum.BrightBlue,
                onBack = onBack,
            )

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = MrsadTokens.Spacing.md),
                verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
            ) {
                Spacer(Modifier.height(MrsadTokens.Spacing.xs))

                MrsadSurface(
                    Modifier
                        .testTag(MrsadTestTags.SETTINGS_THEME_SECTION)
                        .fillMaxWidth(),
                    accent = ObsidianSpectrum.Lavender,
                ) {
                    Text("تم بصری", style = type.title, color = colors.textPrimary)
                    Text(
                        "تم انتخابی روی همهٔ صفحات اعمال می‌شود.",
                        style = type.caption,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(MrsadTokens.Spacing.xs))
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

                MrsadSurface(
                    Modifier
                        .testTag(MrsadTestTags.SETTINGS_QUALITY_SECTION)
                        .fillMaxWidth(),
                    accent = ObsidianSpectrum.Aqua,
                ) {
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
                                modifier = Modifier
                                    .testTag(MrsadTestTags.settingsQuality(quality.id))
                                    .weight(1f),
                                onClick = { scope.launch { repo.setQuality(quality) } },
                            )
                        }
                    }
                }

                MrsadSurface(
                    Modifier
                        .testTag(MrsadTestTags.SETTINGS_TOGGLES)
                        .fillMaxWidth(),
                    accent = ObsidianSpectrum.BrightBlue,
                ) {
                    Text("نمايش و ورودی", style = type.title, color = colors.textPrimary)
                    ToggleRow(
                        title = "ارقام فارسی",
                        subtitle = "نمایش اعداد به‌صورت ۱۲۳ به‌جای 123",
                        checked = settings.persianNumerals,
                        onCheckedChange = { scope.launch { repo.setPersianNumerals(it) } },
                        modifier = Modifier.testTag(MrsadTestTags.SETTINGS_PERSIAN_NUMERALS),
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
                        modifier = Modifier.testTag(MrsadTestTags.SETTINGS_REDUCE_MOTION),
                    )
                    ToggleRow(
                        title = "افکت صوتی",
                        subtitle = "صدای ظریف بازخورد کلیدها و تکمیل جلسهٔ تمرین",
                        checked = settings.soundEnabled,
                        enabled = true,
                        onCheckedChange = { scope.launch { repo.setSound(it) } },
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

                MrsadSurface(
                    Modifier.fillMaxWidth(),
                    accent = ObsidianSpectrum.Pink,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MrsadMarkView(
                            size = 44.dp,
                            modifier = Modifier.testTag(MrsadTestTags.SETTINGS_BRAND_MARK),
                            contentDescription = "MRSAD",
                        )
                        Spacer(Modifier.width(MrsadTokens.Spacing.sm))
                        Column(Modifier.weight(1f)) {
                            Text("دربارهٔ مرصاد", style = type.title, color = colors.textPrimary)
                            Text(
                                "نسخهٔ ${BuildConfig.VERSION_NAME} — OBSIDIAN SPECTRUM",
                                style = type.caption,
                                color = colors.textMuted,
                            )
                        }
                    }
                    Text(
                        "فونت‌ها: Vazirmatn، Estedad و Space Grotesk با لایسنس SIL OFL 1.1.",
                        style = type.caption,
                        color = colors.textMuted,
                    )
                }

                MrsadSecondaryButton(
                    text = "بازگردانی تنظیمات پیش‌فرض",
                    onClick = { scope.launch { repo.restoreDefaults() } },
                    modifier = Modifier
                        .testTag(MrsadTestTags.SETTINGS_RESTORE_DEFAULTS)
                        .fillMaxWidth(),
                )
                Spacer(Modifier.height(96.dp))
            }
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
            .testTag(MrsadTestTags.settingsTheme(variant.id))
            .clip(RoundedCornerShape(MrsadTokens.Radius.md))
            .border(
                1.dp,
                if (selected) palette.accent else MrsadTheme.colors.border,
                RoundedCornerShape(MrsadTokens.Radius.md),
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                .background(
                    Brush.linearGradient(
                        listOf(palette.surfaceHigh, palette.accent.copy(alpha = 0.55f)),
                    ),
                ),
        )
        Text(
            variant.labelFa,
            style = type.label,
            color = MrsadTheme.colors.textPrimary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            variant.subtitleFa,
            style = type.caption,
            color = MrsadTheme.colors.textMuted,
            maxLines = 2,
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
            .background(
                if (selected) {
                    ObsidianSpectrum.Teal.copy(alpha = 0.25f)
                } else {
                    colors.surfaceHigh.copy(alpha = 0.5f)
                },
            )
            .border(
                1.dp,
                if (selected) ObsidianSpectrum.Aqua else colors.border,
                RoundedCornerShape(MrsadTokens.Radius.sm),
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
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
    modifier: Modifier = Modifier,
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
                checkedTrackColor = ObsidianSpectrum.BrightBlue,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.surfaceHigh,
            ),
            modifier = modifier,
        )
    }
}
