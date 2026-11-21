package dev.mrsad.app.feature.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.ui.components.BarRow
import dev.mrsad.app.ui.components.EmptyState
import dev.mrsad.app.ui.components.LocalBottomBarClearance
import dev.mrsad.app.ui.components.MetalIcon
import dev.mrsad.app.ui.components.MetricValue
import dev.mrsad.app.ui.components.MrsadChip
import dev.mrsad.app.ui.components.MrsadIcons
import dev.mrsad.app.ui.components.MrsadSurface
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.components.ObsidianCollapsingTopBar
import dev.mrsad.app.ui.components.ObsidianSegmentedBar
import dev.mrsad.app.ui.components.SectionHeader
import dev.mrsad.app.ui.components.SegmentedBarItem
import dev.mrsad.app.ui.components.SparkLine
import dev.mrsad.app.ui.components.StreakCalendarMatrix
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.ObsidianSpectrum
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.formatInt
import dev.mrsad.app.ui.theme.formatNumber
import dev.mrsad.app.ui.theme.formatPercent

/**
 * OBSIDIAN SPECTRUM — MRSAD ANALYTICS (Master Prompt §12).
 *
 * Groups analytical sections inside a unified `ObsidianSegmentedBar` with an
 * auto-collapsing top bar.
 */
@Composable
fun AnalyticsScreen(
    container: AppContainer,
    settings: MrsadSettings,
) {
    val viewModel: AnalyticsViewModel = viewModel(key = "analytics", factory = AnalyticsViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val motion = MrsadTheme.motion
    val scrollState = rememberScrollState()
    val isCollapsed by remember { derivedStateOf { scrollState.value > 28 } }
    var selectedTab by rememberSaveable { mutableStateOf("trends") }

    AmbientBackground(PageEnvironment.ANALYTICS) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = LocalBottomBarClearance.current),
        ) {
            ObsidianCollapsingTopBar(
                title = "MRSAD ANALYTICS",
                subtitle = "تحلیل عملکرد چندبعدی بر داده‌های واقعی",
                collapsed = isCollapsed,
                spectralColor = ObsidianSpectrum.Aqua,
                leading = {
                    MetalIcon(
                        MrsadIcons.Analytics,
                        accentOverride = ObsidianSpectrum.Aqua,
                        modifier = Modifier.size(if (isCollapsed) 26.dp else 32.dp),
                    )
                },
                trailing = {
                    MrsadChip(text = "۷ روز اخیر", tint = ObsidianSpectrum.Aqua)
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
                    accent = ObsidianSpectrum.Aqua,
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MetricValue(
                            value = formatInt(state.totalMinutes, settings.persianNumerals),
                            label = "دقیقهٔ تمرین",
                            size = type.headline.fontSize,
                            accent = ObsidianSpectrum.BrightBlue,
                        )
                        MetricValue(
                            value = formatInt(state.activeDays, settings.persianNumerals),
                            label = "روزِ فعال",
                            size = type.headline.fontSize,
                            accent = ObsidianSpectrum.Aqua,
                        )
                        MetricValue(
                            value = formatInt(state.currentStreak, settings.persianNumerals),
                            label = "پیوستگی",
                            size = type.headline.fontSize,
                            accent = ObsidianSpectrum.Pink,
                        )
                    }
                }

                ObsidianSegmentedBar(
                    items = listOf(
                        SegmentedBarItem("trends", "روندها و نمودار", ObsidianSpectrum.BrightBlue),
                        SegmentedBarItem("modules", "توزیع ماژول‌ها", ObsidianSpectrum.Lavender),
                        SegmentedBarItem("calendar", "استمرار و روزها", ObsidianSpectrum.Aqua),
                    ),
                    selectedId = selectedTab,
                    onSelect = { selectedTab = it },
                )

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn(animationSpec = motion.effectsDefault()) togetherWith
                            fadeOut(animationSpec = motion.effectsFast())
                    },
                    label = "analyticsSectionTab",
                ) { tab ->
                    when (tab) {
                        "trends" -> Column(verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md)) {
                            MrsadSurface(Modifier.fillMaxWidth(), accent = ObsidianSpectrum.BrightBlue) {
                                SectionHeader(title = "روند زمان تمرین")
                                SparkLine(
                                    values = state.minuteSeries,
                                    tint = ObsidianSpectrum.BrightBlue,
                                    label = if (state.minuteSeries.size >= 2) {
                                        "مجموع ${formatInt(state.totalMinutes, settings.persianNumerals)} دقیقه در ${formatInt(state.minuteSeries.size, settings.persianNumerals)} روز"
                                    } else {
                                        "برای نمودار به دادهٔ بیشتری نیاز است"
                                    },
                                )
                            }

                            MrsadSurface(Modifier.fillMaxWidth(), accent = ObsidianSpectrum.Aqua) {
                                SectionHeader(title = "روند سرعت تایپ")
                                SparkLine(
                                    values = state.typingSeries,
                                    tint = ObsidianSpectrum.Aqua,
                                    label = if (state.typingDays >= 2) {
                                        "میانگین ${formatNumber(state.averageWpm.toInt().toString(), settings.persianNumerals)} کلمه در دقیقه"
                                    } else {
                                        "حداقل دو روز تمرین تایپ برای روند لازم است"
                                    },
                                )
                            }

                            if (state.hasTrendChange) {
                                MrsadSurface(Modifier.fillMaxWidth(), accent = ObsidianSpectrum.Lavender) {
                                    SectionHeader(title = "مقایسه با هفتهٔ قبل")
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (state.wpmChange >= 0) "▲" else "▼",
                                            style = type.title,
                                            color = if (state.wpmChange >= 0) ObsidianSpectrum.Aqua else ObsidianSpectrum.Pink,
                                        )
                                        Spacer(Modifier.width(MrsadTokens.Spacing.xxs))
                                        Text(
                                            "${formatPercent(kotlin.math.abs(state.wpmChange), settings.persianNumerals, 1)} " +
                                                if (state.wpmChange >= 0) "بهبود در سرعت تایپ" else "کاهش در سرعت تایپ",
                                            style = type.body,
                                            color = colors.textSecondary,
                                        )
                                    }
                                    Text(
                                        "مقایسه بر میانگین جلسات واقعی دو هفته اخیر محاسبه شده است.",
                                        style = type.caption,
                                        color = colors.textMuted,
                                    )
                                }
                            }
                        }

                        "modules" -> Column(verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md)) {
                            MrsadSurface(Modifier.fillMaxWidth(), accent = ObsidianSpectrum.Lavender) {
                                SectionHeader(title = "توزیع فعالیت بر اساس ماژول")
                                if (state.moduleMinutes.isEmpty()) {
                                    EmptyState(
                                        title = "هنوز فعالیتی ثبت نشده",
                                        body = "پس از چند جلسهٔ تمرین، توزیع زمانی بین تایپ، خواندن، چشم، ذهن و تمرکز اینجا نمایش داده می‌شود.",
                                    )
                                } else {
                                    val max = state.moduleMinutes.values.max().toDouble()
                                    state.moduleMinutes.entries.forEachIndexed { idx, (label, minutes) ->
                                        val barTint = when (idx % 4) {
                                            0 -> ObsidianSpectrum.BrightBlue
                                            1 -> ObsidianSpectrum.Lavender
                                            2 -> ObsidianSpectrum.Aqua
                                            else -> ObsidianSpectrum.Pink
                                        }
                                        BarRow(
                                            label = label,
                                            value = minutes.toDouble(),
                                            maxValue = max,
                                            valueLabel = "${formatInt(minutes, settings.persianNumerals)} دقیقه",
                                            tint = barTint,
                                        )
                                    }
                                }
                            }

                            MrsadSurface(Modifier.fillMaxWidth(), accent = ObsidianSpectrum.Pink) {
                                SectionHeader(title = "شاخص‌های کلیدی")
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    MetricValue(
                                        value = state.bestWpm?.let { formatInt(it, settings.persianNumerals) } ?: "—",
                                        label = "رکورد سرعت تایپ",
                                        size = type.title.fontSize,
                                        accent = ObsidianSpectrum.BrightBlue,
                                    )
                                    MetricValue(
                                        value = if (state.averageAccuracy <= 0) "—" else formatPercent(state.averageAccuracy, settings.persianNumerals, 1),
                                        label = "میانگین دقت",
                                        size = type.title.fontSize,
                                        accent = ObsidianSpectrum.Aqua,
                                    )
                                    MetricValue(
                                        value = if (state.mindAverageScore <= 0) "—" else formatPercent(state.mindAverageScore, settings.persianNumerals),
                                        label = "میانگین تمرین ذهنی",
                                        size = type.title.fontSize,
                                        accent = ObsidianSpectrum.Pink,
                                    )
                                }
                            }
                        }

                        else -> Column(verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md)) {
                            if (state.calendarDays.isNotEmpty()) {
                                MrsadSurface(Modifier.fillMaxWidth(), accent = ObsidianSpectrum.Aqua) {
                                    SectionHeader(title = "ماتریس استمرار ۲۸ روزه")
                                    StreakCalendarMatrix(days = state.calendarDays)
                                }
                            }

                            MrsadSurface(Modifier.fillMaxWidth(), accent = ObsidianSpectrum.BrightBlue) {
                                SectionHeader(title = "جزئیات روزانه")
                                state.daily.forEach { day ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            day.labelFa,
                                            style = type.label,
                                            color = colors.textSecondary,
                                            modifier = Modifier.width(84.dp),
                                        )
                                        Text(
                                            "${formatInt(day.minutes, settings.persianNumerals)} دقیقه",
                                            style = type.label,
                                            color = if (day.minutes > 0) colors.textPrimary else colors.textMuted,
                                            modifier = Modifier.weight(1f),
                                        )
                                        MrsadChip(
                                            text = "${formatInt(day.sessions, settings.persianNumerals)} جلسه",
                                            tint = if (day.sessions > 0) ObsidianSpectrum.Aqua else colors.textMuted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(100.dp))
            }
        }
    }
}
