package dev.mrsad.app.feature.placeholder

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.feature.home.HomeViewModel
import dev.mrsad.app.ui.components.LocalBottomBarClearance
import dev.mrsad.app.ui.components.MetalIcon
import dev.mrsad.app.ui.components.MetricValue
import dev.mrsad.app.ui.components.MrsadChip
import dev.mrsad.app.ui.components.MrsadDivider
import dev.mrsad.app.ui.components.MrsadIcons
import dev.mrsad.app.ui.components.MrsadProgressBar
import dev.mrsad.app.ui.components.MrsadSecondaryButton
import dev.mrsad.app.ui.components.MrsadSurface
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.components.SectionHeader
import dev.mrsad.app.ui.components.StreakCalendarMatrix
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.formatInt

/**
 * MRSAD ARENA — Progression, XP, Achievements, Weekly Challenges & Streak Calendar.
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

    AmbientBackground(PageEnvironment.ARENA) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = LocalBottomBarClearance.current)
                .verticalScroll(rememberScrollState())
                .padding(MrsadTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetalIcon(MrsadIcons.Trophy, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                Column(Modifier.weight(1f)) {
                    Text("MRSAD ARENA", style = type.headline.copy(letterSpacing = 1.sp), color = colors.textPrimary)
                    Text("سطح، امتیاز تجربه (XP)، دستاوردها و چالش‌های هفتگی", style = type.caption, color = colors.textSecondary)
                }
            }

            if (prog != null) {
                val lvl = prog.levelInfo
                MrsadSurface(Modifier.fillMaxWidth()) {
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
                            tint = colors.accent,
                        )
                    }
                    MrsadProgressBar(progress = lvl.progressFraction)
                }

                MrsadSurface(Modifier.fillMaxWidth()) {
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
                                Text(ch.titleFa, style = type.body.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                                Text(
                                    "+${formatInt(ch.rewardXp, settings.persianNumerals)} XP",
                                    style = type.caption,
                                    color = if (ch.completed) colors.success else colors.accent,
                                )
                            }
                            Text(ch.subtitleFa, style = type.caption, color = colors.textSecondary)
                            MrsadProgressBar(
                                progress = ch.progressFraction,
                                tint = if (ch.completed) colors.success else colors.accent,
                            )
                        }
                    }
                }

                MrsadSurface(Modifier.fillMaxWidth()) {
                    SectionHeader(title = "نشان‌ها و دستاوردهای مهارتی")
                    prog.achievements.forEach { badge ->
                        val shape = if (colors.isNoir) RoundedCornerShape(2.dp) else RoundedCornerShape(MrsadTokens.Radius.sm)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(if (badge.unlocked) colors.accentSoft else colors.surfaceHigh.copy(alpha = 0.4f))
                                .border(1.dp, if (badge.unlocked) colors.accent else colors.border, shape)
                                .padding(MrsadTokens.Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MetalIcon(
                                facets = if (badge.unlocked) MrsadIcons.Trophy else MrsadIcons.Target,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(MrsadTokens.Spacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(badge.titleFa, style = type.body.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                                Text(badge.descriptionFa, style = type.caption, color = colors.textSecondary)
                            }
                            Text(
                                badge.progressLabelFa,
                                style = type.caption,
                                color = if (badge.unlocked) colors.success else colors.textMuted,
                            )
                        }
                    }
                }

                MrsadSurface(Modifier.fillMaxWidth()) {
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
