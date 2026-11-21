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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.settings.GraphicsQuality
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.data.settings.ThemeVariant
import dev.mrsad.app.ui.components.LocalBottomBarClearance
import dev.mrsad.app.ui.components.MetalIcon
import dev.mrsad.app.ui.components.ModuleSculpture3D
import dev.mrsad.app.ui.components.ModuleSculptureType
import dev.mrsad.app.ui.components.MrsadChip
import dev.mrsad.app.ui.components.MrsadDivider
import dev.mrsad.app.ui.components.MrsadIcons
import dev.mrsad.app.ui.components.MrsadSurface
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.components.ObsidianCollapsingTopBar
import dev.mrsad.app.ui.components.ObsidianIconBox
import dev.mrsad.app.ui.components.ObsidianSegmentedBar
import dev.mrsad.app.ui.components.SectionHeader
import dev.mrsad.app.ui.components.SegmentedBarItem
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.ObsidianSpectrum
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.colorsFor
import dev.mrsad.app.ui.theme.formatNumber
import kotlinx.coroutines.launch

/**
 * OBSIDIAN SPECTRUM — MRSAD STUDIO (Master Prompt §12).
 */
@Composable
fun StudioScreen(
    container: AppContainer,
    settings: MrsadSettings,
    onBack: (() -> Unit)? = null,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val scope = rememberCoroutineScope()
    val repo = container.settingsRepository
    val scrollState = rememberScrollState()
    val isCollapsed by remember { derivedStateOf { scrollState.value > 28 } }

    AmbientBackground(PageEnvironment.STUDIO) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = LocalBottomBarClearance.current),
        ) {
            ObsidianCollapsingTopBar(
                title = "استودیو طراحی و نمایش",
                subtitle = "شخصی‌سازی OBSIDIAN SPECTRUM، تایپوگرافی و کیفیت رندر",
                collapsed = isCollapsed,
                spectralColor = ObsidianSpectrum.Lavender,
                onBack = onBack,
                leading = {
                    ObsidianIconBox(
                        size = if (isCollapsed) 38.dp else 48.dp,
                        accent = ObsidianSpectrum.Lavender,
                    ) {
                        MetalIcon(
                            MrsadIcons.Studio,
                            accentOverride = ObsidianSpectrum.Lavender,
                            modifier = Modifier.size(if (isCollapsed) 20.dp else 24.dp),
                        )
                    }
                },
                trailing = {
                    MrsadChip(
                        text = "OBSIDIAN",
                        tint = ObsidianSpectrum.Lavender,
                        compact = true,
                    )
                },
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
                    Modifier.fillMaxWidth(),
                    elevated = true,
                    accent = ObsidianSpectrum.Lavender,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MetalIcon(
                                MrsadIcons.Spark,
                                accentOverride = ObsidianSpectrum.Lavender,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                            Text("طیف هویت ۴ ستون مهارت", style = type.label, color = colors.textSecondary)
                        }
                        MrsadChip(
                            text = "۴ طیف نوری",
                            tint = ObsidianSpectrum.BrightBlue,
                        )
                    }

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
                    Text(
                        "پایهٔ یکپارچهٔ ابسیدین (#090810) همراه با هویت طیفی مستقل برای تایپ (آبی الکتریک)، خواندن (بنفش عمیق)، چشم (فیروزه‌ای) و ذهن (سرخابی).",
                        style = type.caption,
                        color = colors.textMuted,
                    )
                }

                MrsadSurface(
                    Modifier.fillMaxWidth(),
                    accent = ObsidianSpectrum.BrightBlue,
                ) {
                    SectionHeader(title = "حالت طیف رنگی (OBSIDIAN SPECTRUM)")
                    ThemeVariant.entries.forEach { variant ->
                        val palette = colorsFor(variant)
                        val selected = settings.theme == variant
                        val shape = RoundedCornerShape(MrsadTokens.Radius.md)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(
                                    if (selected) {
                                        ObsidianSpectrum.DeepViolet.copy(alpha = 0.25f)
                                    } else {
                                        colors.surfaceHigh.copy(alpha = 0.45f)
                                    },
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) palette.accent else colors.border,
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
                                    MrsadChip(text = "فعال", tint = palette.accent)
                                }
                            }
                            Text(
                                variant.subtitleFa,
                                style = type.caption,
                                color = colors.textSecondary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    ObsidianSpectrum.ObsidianBlack,
                                    ObsidianSpectrum.ElevatedSurface,
                                    ObsidianSpectrum.BrightBlue,
                                    ObsidianSpectrum.Lavender,
                                    ObsidianSpectrum.Aqua,
                                    ObsidianSpectrum.Pink,
                                ).forEach { c ->
                                    Box(
                                        Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(c)
                                            .border(1.dp, palette.borderStrong, RoundedCornerShape(6.dp)),
                                    )
                                }
                            }
                        }
                    }
                }

                MrsadSurface(
                    Modifier.fillMaxWidth(),
                    accent = ObsidianSpectrum.Aqua,
                ) {
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
                            thumbColor = ObsidianSpectrum.SoftWhite,
                            activeTrackColor = ObsidianSpectrum.Aqua,
                            inactiveTrackColor = colors.surfaceHigh,
                        ),
                    )
                    MrsadDivider()
                    Text("کیفیت جلوه‌های بصری", style = type.label, color = colors.textSecondary)
                    ObsidianSegmentedBar(
                        items = GraphicsQuality.entries.map { quality ->
                            SegmentedBarItem(
                                id = quality.id,
                                label = when (quality) {
                                    GraphicsQuality.HIGH -> "بالا"
                                    GraphicsQuality.BALANCED -> "متعادل"
                                    GraphicsQuality.PERFORMANCE -> "عملکرد"
                                },
                                accent = ObsidianSpectrum.Aqua,
                            )
                        },
                        selectedId = settings.quality.id,
                        onSelect = { id ->
                            GraphicsQuality.fromId(id).let { q -> scope.launch { repo.setQuality(q) } }
                        },
                    )
                }

                Spacer(Modifier.height(96.dp))
            }
        }
    }
}
