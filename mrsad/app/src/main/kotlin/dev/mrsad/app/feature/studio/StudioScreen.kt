package dev.mrsad.app.feature.studio

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
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.settings.GraphicsQuality
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.data.settings.ThemeVariant
import dev.mrsad.app.ui.components.MrsadCore
import dev.mrsad.app.ui.components.MrsadSurface
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.colorsFor
import dev.mrsad.app.ui.theme.formatNumber
import kotlinx.coroutines.launch

/**
 * MRSAD STUDIO — live visual customisation.
 *
 * Everything here writes real settings and is applied instantly to the whole
 * app (the theme wraps the entire navigation graph). The live preview uses the
 * exact same [MrsadCore] and token system as Home, so what you preview is what
 * you get.
 */
@Composable
fun StudioScreen(
    container: AppContainer,
    settings: MrsadSettings,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val scope = rememberCoroutineScope()
    val repo = container.settingsRepository

    AmbientBackground(PageEnvironment.STUDIO) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MrsadTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
        ) {
            Text("MRSAD STUDIO", style = type.headline.copy(letterSpacing = 1.sp), color = colors.textPrimary)
            Text("پیش‌نمایش زنده و شخصی‌سازی ظاهر", style = type.caption, color = colors.textSecondary)

            // Live preview surface.
            MrsadSurface(Modifier.fillMaxWidth(), elevated = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DarkMode, null, tint = colors.metalMid, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(MrsadTokens.Spacing.xs))
                    Text("پیش‌نمایش زنده", style = type.label, color = colors.textSecondary)
                }
                Box(Modifier.fillMaxWidth().height(210.dp)) {
                    MrsadCore(
                        energy = 0.55f,
                        modifier = Modifier.fillMaxSize(),
                        streakDays = 12,
                    )
                }
                Text(
                    "تغییرات همین لحظه روی همهٔ صفحات اعمال می‌شوند؛ پیش‌نمایش بالا از همان سیستم طراحی زندهٔ برنامه استفاده می‌کند.",
                    style = type.caption,
                    color = colors.textMuted,
                )
            }

            MrsadSurface(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Palette, null, tint = colors.metalMid, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(MrsadTokens.Spacing.xs))
                    Text("پالت رنگ", style = type.title, color = colors.textPrimary)
                }
                ThemeVariant.entries.forEach { variant ->
                    val palette = colorsFor(variant)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                            .border(
                                1.dp,
                                if (settings.theme == variant) colors.borderStrong else androidx.compose.ui.graphics.Color.Transparent,
                                RoundedCornerShape(MrsadTokens.Radius.sm),
                            )
                            .clickable { scope.launch { repo.setTheme(variant) } }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(palette.background, palette.surface, palette.accent, palette.metalLight).forEach { c ->
                                Box(
                                    Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(c)
                                        .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                                )
                            }
                        }
                        Spacer(Modifier.size(MrsadTokens.Spacing.sm))
                        Text(
                            variant.labelFa,
                            style = type.body,
                            color = colors.textPrimary,
                            fontWeight = if (settings.theme == variant) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (settings.theme == variant) {
                            Text("فعال", style = type.caption, color = colors.accent)
                        }
                    }
                }
            }

            MrsadSurface(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Tune, null, tint = colors.metalMid, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(MrsadTokens.Spacing.xs))
                    Text("تنظیمات ظاهری", style = type.title, color = colors.textPrimary)
                }
                Text(
                    "اندازهٔ فونت: ${formatNumber((settings.fontScale * 100).toInt().toString(), settings.persianNumerals)}٪",
                    style = type.label,
                    color = colors.textSecondary,
                )
                Slider(
                    value = settings.fontScale,
                    onValueChange = { scope.launch { repo.setFontScale(it) } },
                    valueRange = 0.85f..1.3f,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.metalLight,
                        activeTrackColor = colors.metalMid,
                        inactiveTrackColor = colors.surfaceHigh,
                    ),
                )
                Text("کیفیت جلوه‌ها", style = type.label, color = colors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
                    GraphicsQuality.entries.forEach { quality ->
                        val selected = settings.quality == quality
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                                .background(if (selected) colors.accentSoft else colors.surfaceHigh.copy(alpha = 0.5f))
                                .clickable { scope.launch { repo.setQuality(quality) } }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                when (quality) {
                                    GraphicsQuality.HIGH -> "بالا"
                                    GraphicsQuality.BALANCED -> "متعادل"
                                    GraphicsQuality.PERFORMANCE -> "عملکرد"
                                },
                                style = type.label,
                                color = if (selected) colors.textPrimary else colors.textSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(MrsadTokens.Spacing.xs))
                Text(
                    "پس‌زمینهٔ ستاره‌ای و حرکت‌های محیطی فقط در حالت‌های «بالا» و «متعادل» فعال‌اند؛ در حالت «عملکرد» همهٔ انیمیشن‌های محیطی متوقف می‌شوند.",
                    style = type.caption,
                    color = colors.textMuted,
                )
            }

            Spacer(Modifier.height(120.dp))
        }
    }
}
