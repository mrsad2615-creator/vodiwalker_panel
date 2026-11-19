package dev.mrsad.app.feature.analytics

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.ui.components.BarRow
import dev.mrsad.app.ui.components.EmptyState
import dev.mrsad.app.ui.components.MrsadChip
import dev.mrsad.app.ui.components.MrsadSecondaryButton
import dev.mrsad.app.ui.components.MrsadSurface
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.components.MetricValue
import dev.mrsad.app.ui.components.SectionHeader
import dev.mrsad.app.ui.components.SparkLine
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.formatInt
import dev.mrsad.app.ui.theme.formatNumber
import dev.mrsad.app.ui.theme.formatPercent

/**
 * MRSAD ANALYTICS — Data Universe.
 *
 * Charts are rendered strictly from the seven-day activity aggregate. When a
 * metric has fewer than two data points, the chart is replaced with an honest
 * "needs more data" label instead of a decorative fake line.
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

    AmbientBackground(PageEnvironment.ANALYTICS) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MrsadTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MRSAD ANALYTICS", style = type.headline.copy(letterSpacing = 1.sp), color = colors.textPrimary)
                    Text("تحلیل عملکرد بر داده‌های واقعی", style = type.caption, color = colors.textSecondary)
                }
                MrsadChip(text = "۷ روز اخیر", tint = colors.metalMid)
            }

            MrsadSurface(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MetricValue(
                        value = formatInt(state.totalMinutes, settings.persianNumerals),
                        label = "دقیقهٔ تمرین",
                        size = type.headline.fontSize,
                    )
                    MetricValue(
                        value = formatInt(state.activeDays, settings.persianNumerals),
                        label = "روزِ فعال",
                        size = type.headline.fontSize,
                    )
                    MetricValue(
                        value = formatInt(state.currentStreak, settings.persianNumerals),
                        label = "پیوستگی",
                        size = type.headline.fontSize,
                    )
                }
            }

            MrsadSurface(Modifier.fillMaxWidth()) {
                SectionHeader(title = "روند زمان تمرین")
                SparkLine(
                    values = state.minuteSeries,
                    tint = colors.accent,
                    label = if (state.minuteSeries.size >= 2)
                        "مجموع ${formatInt(state.totalMinutes, settings.persianNumerals)} دقیقه در ${formatInt(state.minuteSeries.size, settings.persianNumerals)} روز"
                    else "برای نمودار به دادهٔ بیشتری نیاز است",
                )
            }

            MrsadSurface(Modifier.fillMaxWidth()) {
                SectionHeader(title = "روند سرعت تایپ")
                SparkLine(
                    values = state.typingSeries,
                    tint = colors.metalLight,
                    label = if (state.typingSeries.size >= 2)
                        "میانگین ${formatNumber(state.averageWpm.toInt().toString(), settings.persianNumerals)} کلمه در دقیقه"
                    else "حداقل دو روز تمرین تایپ برای روند لازم است",
                )
            }

            MrsadSurface(Modifier.fillMaxWidth()) {
                SectionHeader(title = "توزیع فعالیت بر اساس ماژول")
                if (state.moduleMinutes.isEmpty()) {
                    EmptyState(
                        title = "هنوز فعالیتی ثبت نشده",
                        body = "پس از چند جلسهٔ تمرین، توزیع زمانی بین تایپ، خواندن، ذهن و تمرکز اینجا نمایش داده می‌شود.",
                    )
                } else {
                    val max = state.moduleMinutes.values.max().toDouble()
                    state.moduleMinutes.forEach { (label, minutes) ->
                        BarRow(
                            label = label,
                            value = minutes.toDouble(),
                            maxValue = max,
                            valueLabel = "${formatInt(minutes, settings.persianNumerals)} دقیقه",
                        )
                    }
                }
            }

            MrsadSurface(Modifier.fillMaxWidth()) {
                SectionHeader(title = "شاخص‌های کلیدی")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MetricValue(
                        value = state.bestWpm?.let { formatInt(it, settings.persianNumerals) } ?: "—",
                        label = "رکورد سرعت تایپ",
                        size = type.title.fontSize,
                    )
                    MetricValue(
                        value = if (state.averageAccuracy <= 0) "—" else formatPercent(state.averageAccuracy, settings.persianNumerals, 1),
                        label = "میانگین دقت",
                        size = type.title.fontSize,
                    )
                    MetricValue(
                        value = if (state.mindAverageScore <= 0) "—" else formatPercent(state.mindAverageScore, settings.persianNumerals),
                        label = "میانگین تمرین ذهنی",
                        size = type.title.fontSize,
                    )
                }
            }

            if (state.hasTrendChange) {
                MrsadSurface(Modifier.fillMaxWidth()) {
                    SectionHeader(title = "مقایسه با هفتهٔ قبل")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (state.wpmChange >= 0) "▲" else "▼",
                            style = type.title,
                            color = if (state.wpmChange >= 0) colors.success else colors.warning,
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

            MrsadSurface(Modifier.fillMaxWidth()) {
                SectionHeader(title = "جزئیات روزانه")
                state.daily.forEach { day ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(day.labelFa, style = type.label, color = colors.textSecondary, modifier = Modifier.width(84.dp))
                        Text(
                            "${formatInt(day.minutes, settings.persianNumerals)} دقیقه",
                            style = type.label,
                            color = if (day.minutes > 0) colors.textPrimary else colors.textMuted,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${formatInt(day.sessions, settings.persianNumerals)} جلسه",
                            style = type.caption,
                            color = colors.textMuted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(120.dp))
        }
    }
}
