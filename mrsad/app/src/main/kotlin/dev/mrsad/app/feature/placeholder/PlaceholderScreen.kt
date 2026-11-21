package dev.mrsad.app.feature.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.feature.home.HomeViewModel
import dev.mrsad.app.ui.components.LocalBottomBarClearance
import dev.mrsad.app.ui.components.MetalIcon
import dev.mrsad.app.ui.components.MrsadChip
import dev.mrsad.app.ui.components.MrsadIcons
import dev.mrsad.app.ui.components.MrsadProgressBar
import dev.mrsad.app.ui.components.MrsadSecondaryButton
import dev.mrsad.app.ui.components.MrsadSurface
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.components.ObsidianCollapsingTopBar
import dev.mrsad.app.ui.components.SectionHeader
import dev.mrsad.app.ui.components.StreakCalendarMatrix
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.ObsidianSpectrum
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.formatInt

/**
 * OBSIDIAN SPECTRUM — MRSAD ARENA (Progression, XP, Achievements & Challenges).
 */
@Composable
fun ArenaScreen(
    container: AppContainer,
    settings: MrsadSettings,
    onBack: () -> Unit,
) {
    val homeVm: HomeViewModel = viewModel(
        key = "home",
        factory = HomeViewModel.Factory(container),
    )
    val state by homeVm.state.collectAsStateWithLifecycle()
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val prog = state.progression
    val scrollState = rememberScrollState()
    val isCollapsed by remember { derivedStateOf { scrollState.value > 28 } }

    AmbientBackground(PageEnvironment.ARENA) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = LocalBottomBarClearance.current),
        ) {
            ObsidianCollapsingTopBar(
                title = "MRSAD ARENA",
                subtitle = "سطح، امتیاز تجربه (XP)، دستاوردها و چالش‌های هفتگی",
                collapsed = isCollapsed,
                spectralColor = ObsidianSpectrum.Pink,
                onBack = onBack,
                leading = {
                    MetalIcon(
                        MrsadIcons.Trophy,
                        accentOverride = ObsidianSpectrum.Pink,
                        modifier = Modifier.size(if (isCollapsed) 24.dp else 30.dp),
                    )
                },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = MrsadTokens.Spacing.md),
                verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
            ) {
                Spacer(Modifier.height(MrsadTokens.Spacing.xs))

                if (prog != null) {
                    val lvl = prog.levelInfo
                    MrsadSurface(
                        Modifier.fillMaxWidth(),
                        accent = ObsidianSpectrum.BrightBlue,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    "سطح ${formatInt(lvl.level, settings.persianNumerals)} — ${lvl.titleFa}",
                                    style = type.title,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    "مجموع امتیاز تجربه: ${formatInt(lvl.totalXp, settings.persianNumerals)} XP",
                                    style = type.caption,
                                    color = colors.textSecondary,
                                )
                            }
                            MrsadChip(
                                text = "${formatInt(prog.unlockedCount, settings.persianNumerals)} / ${formatInt(prog.achievements.size, settings.persianNumerals)} نشان",
                                tint = ObsidianSpectrum.Lavender,
                            )
                        }
                        MrsadProgressBar(progress = lvl.progressFraction, tint = ObsidianSpectrum.BrightBlue)
                    }

                    MrsadSurface(
                        Modifier.fillMaxWidth(),
                        accent = ObsidianSpectrum.Lavender,
                    ) {
                        SectionHeader(title = "چالش‌های هفتگی")
                        prog.weeklyChallenges.forEach { ch ->
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        ch.titleFa,
                                        style = type.body.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.textPrimary,
                                    )
                                    Text(
                                        "+${formatInt(ch.rewardXp, settings.persianNumerals)} XP",
                                        style = type.caption,
                                        color = if (ch.completed) ObsidianSpectrum.Aqua else ObsidianSpectrum.Lavender,
                                    )
                                }
                                Text(ch.subtitleFa, style = type.caption, color = colors.textSecondary)
                                MrsadProgressBar(
                                    progress = ch.progressFraction,
                                    tint = if (ch.completed) ObsidianSpectrum.Aqua else ObsidianSpectrum.Lavender,
                                )
                            }
                        }
                    }

                    MrsadSurface(
                        Modifier.fillMaxWidth(),
                        accent = ObsidianSpectrum.Pink,
                    ) {
                        SectionHeader(title = "نشان‌ها و دستاوردهای مهارتی")
                        prog.achievements.forEach { badge ->
                            val shape = RoundedCornerShape(MrsadTokens.Radius.sm)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(
                                        if (badge.unlocked) {
                                            ObsidianSpectrum.Magenta.copy(alpha = 0.25f)
                                        } else {
                                            colors.surfaceHigh.copy(alpha = 0.4f)
                                        },
                                    )
                                    .border(
                                        1.dp,
                                        if (badge.unlocked) ObsidianSpectrum.Pink else colors.border,
                                        shape,
                                    )
                                    .padding(MrsadTokens.Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MetalIcon(
                                    facets = if (badge.unlocked) MrsadIcons.Trophy else MrsadIcons.Target,
                                    accentOverride = if (badge.unlocked) ObsidianSpectrum.Pink else colors.textMuted,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(MrsadTokens.Spacing.sm))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        badge.titleFa,
                                        style = type.body.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.textPrimary,
                                    )
                                    Text(badge.descriptionFa, style = type.caption, color = colors.textSecondary)
                                }
                                Text(
                                    badge.progressLabelFa,
                                    style = type.caption,
                                    color = if (badge.unlocked) ObsidianSpectrum.Aqua else colors.textMuted,
                                )
                            }
                        }
                    }

                    MrsadSurface(
                        Modifier.fillMaxWidth(),
                        accent = ObsidianSpectrum.Aqua,
                    ) {
                        SectionHeader(title = "تقویم استمرار ۲۸ روزه")
                        StreakCalendarMatrix(days = prog.calendarDays)
                    }
                }

                MrsadSecondaryButton(
                    text = "بازگشت",
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(64.dp))
            }
        }
    }
}

@Composable
fun PlaceholderScreen(
    title: String,
    environment: PageEnvironment,
    description: String,
    onBack: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    AmbientBackground(environment) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(MrsadTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.sm, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = type.display, color = colors.textPrimary, textAlign = TextAlign.Center)
            Text(description, style = type.body, color = colors.textSecondary, textAlign = TextAlign.Center)
            MrsadSecondaryButton(
                text = "بازگشت",
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                onClick = onBack,
            )
        }
    }
}
