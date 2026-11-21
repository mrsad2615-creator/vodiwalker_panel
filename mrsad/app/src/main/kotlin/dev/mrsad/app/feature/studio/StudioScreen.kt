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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.settings.GraphicsQuality
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.data.settings.ThemeVariant
import dev.mrsad.app.ui.components.LocalBottomBarClearance
import dev.mrsad.app.ui.components.MetalIcon
import dev.mrsad.app.ui.components.ModuleSculpture3D
import dev.mrsad.app.ui.components.ModuleSculptureType
import dev.mrsad.app.ui.components.MrsadChip
import dev.mrsad.app.ui.components.MrsadCore
import dev.mrsad.app.ui.components.MrsadDivider
import dev.mrsad.app.ui.components.MrsadIcons
import dev.mrsad.app.ui.components.MrsadSurface
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.components.SectionHeader
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.colorsFor
import dev.mrsad.app.ui.theme.formatNumber
import kotlinx.coroutines.launch

/**
 * MRSAD STUDIO — Dual-System Theme Manager (Master Prompt §1 & §3):
 *
 * Allows instant runtime switching between the two independent visual
 * identities:
 * - **THEME 01 — OBSIDIAN** (Luxury Dark 3D Spatial)
 * - **THEME 02 — NOIR** (Minimal Black & White Editorial)
 */
@Composable
fun StudioScreen(
    container: AppContainer,
    settings: MrsadSettings,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val isNoir = colors.isNoir
    val scope = rememberCoroutineScope()
    val repo = container.settingsRepository

    AmbientBackground(PageEnvironment.STUDIO) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = LocalBottomBarClearance.current)
                .verticalScroll(rememberScrollState())
                .padding(MrsadTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
        ) {
            Text("MRSAD STUDIO", style = type.headline.copy(letterSpacing = 1.sp), color = colors.textPrimary)
            Text(
                "معماری دوگانهٔ سیستم طراحی (OBSIDIAN × NOIR) و پیش‌نمایش زنده",
                style = type.caption,
                color = colors.textSecondary,
            )

            // Live preview surface
            MrsadSurface(Modifier.fillMaxWidth(), elevated = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MetalIcon(MrsadIcons.Spark, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                        Text("پیش‌نمایش زندهٔ سیستم طراحی", style = type.label, color = colors.textSecondary)
                    }
                    MrsadChip(
                        text = if (isNoir) "NOIR // 2D SWISS" else "OBSIDIAN // 3D SPATIAL",
                        tint = colors.accent,
                    )
                }

                if (isNoir) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = MrsadTokens.Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ModuleSculpture3D(type = ModuleSculptureType.TYPING, modifier = Modifier.size(64.dp))
                        ModuleSculpture3D(type = ModuleSculptureType.READING, modifier = Modifier.size(64.dp))
                        ModuleSculpture3D(type = ModuleSculptureType.EYE, modifier = Modifier.size(64.dp))
                        ModuleSculpture3D(type = ModuleSculptureType.BRAIN, modifier = Modifier.size(64.dp))
                    }
                } else {
                    Box(Modifier.fillMaxWidth().height(200.dp)) {
                        MrsadCore(
                            energy = 0.65f,
                            modifier = Modifier.fillMaxSize(),
                            streakDays = 12,
                        )
                    }
                }
                Text(
                    "هر دو تم، دو زبان طراحی کاملاً مستقل هستند؛ تغییر تم کل ساختار کارت‌ها، آیکون‌ها، نمودارها و المان‌های بصری را در لحظه دگرگون می‌کند.",
                    style = type.caption,
                    color = colors.textMuted,
                )
            }

            // Dual-System Theme Selector
            MrsadSurface(Modifier.fillMaxWidth()) {
                SectionHeader(title = "انتخاب سیستم طراحی (دو تم مستقل)")
                ThemeVariant.entries.forEach { variant ->
                    val palette = colorsFor(variant)
                    val selected = settings.theme == variant
                    val shape = if (isNoir) RoundedCornerShape(2.dp) else RoundedCornerShape(MrsadTokens.Radius.md)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(if (selected) colors.accentSoft else colors.surfaceHigh.copy(alpha = 0.45f))
                            .border(
                                width = 1.dp,
                                color = if (selected) colors.borderStrong else colors.border,
                                shape = shape,
                            )
                            .clickable(role = Role.RadioButton) { scope.launch { repo.setTheme(variant) } }
                            .padding(MrsadTokens.Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                variant.labelFa,
                                style = type.title,
                                color = colors.textPrimary,
                            )
                            if (selected) {
                                MrsadChip(text = "تم فعال", tint = colors.accent)
                            }
                        }
                        Text(
                            variant.subtitleFa,
                            style = type.caption,
                            color = colors.textSecondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                palette.background,
                                palette.surface,
                                palette.surfaceHigh,
                                palette.metalLight,
                                palette.textPrimary,
                            ).forEach { c ->
                                Box(
                                    Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(if (variant == ThemeVariant.NOIR) 2.dp else 6.dp))
                                        .background(c)
                                        .border(
                                            1.dp,
                                            palette.borderStrong,
                                            RoundedCornerShape(if (variant == ThemeVariant.NOIR) 2.dp else 6.dp),
                                        ),
                                )
                            }
                        }
                    }
                }
            }

            MrsadSurface(Modifier.fillMaxWidth()) {
                SectionHeader(title = "تایپوگرافی و کیفیت جلوه‌ها")
                Text(
                    "مقیاس فونت: ${formatNumber((settings.fontScale * 100).toInt().toString(), settings.persianNumerals)}٪",
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
                MrsadDivider()
                Text("کیفیت جلوه‌های بصری", style = type.label, color = colors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
                    GraphicsQuality.entries.forEach { quality ->
                        val selected = settings.quality == quality
                        val qShape = if (isNoir) RoundedCornerShape(2.dp) else RoundedCornerShape(MrsadTokens.Radius.sm)
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(qShape)
                                .background(if (selected) colors.accentSoft else colors.surfaceHigh.copy(alpha = 0.5f))
                                .border(1.dp, if (selected) colors.borderStrong else colors.border, qShape)
                                .clickable(role = Role.RadioButton) { scope.launch { repo.setQuality(quality) } }
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
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}
