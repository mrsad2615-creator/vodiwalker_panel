package com.neurox.feature.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neurox.designsystem.components.ChipTone
import com.neurox.designsystem.components.NxChip
import com.neurox.designsystem.components.NxEmptyState
import com.neurox.designsystem.components.NxPrimaryButton
import com.neurox.designsystem.components.NxProgressBar
import com.neurox.designsystem.tokens.NxRadius
import com.neurox.designsystem.components.NxSectionHeader
import com.neurox.designsystem.tokens.NxSpacing
import com.neurox.designsystem.components.NxStat
import com.neurox.designsystem.components.NxSurface
import com.neurox.designsystem.theme.NxTheme
import com.neurox.designsystem.theme.NxTypography
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.screen.AnalyticsState
import com.neurox.presentation.screen.BaselineComparison
import com.neurox.presentation.screen.ErrorRow
import com.neurox.domain.analytics.ModuleAnalytics
import com.neurox.presentation.screen.RecordRow
import com.neurox.presentation.screen.WeakSkillRow

/**
 * Progress.
 *
 * HONESTY RULES APPLIED HERE
 * - No data means an empty state with a call to action, not an empty chart frame.
 * - A trend line needs at least [AnalyticsState.MIN_SESSIONS_FOR_TREND] completed
 *   sessions. Below that, the screen says so and shows the raw sessions instead.
 * - Reaction times are never compared across modules; the comparability note is
 *   part of the screen, not a tooltip nobody opens.
 */
@Composable
fun AnalyticsScreen(
    state: AnalyticsState,
    strings: Strings,
    onSelectRange: (AnalyticsState.Range) -> Unit,
    onStartTraining: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = NxSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NxSpacing.lg),
    ) {
        item {
            Column(modifier = Modifier.padding(top = NxSpacing.lg)) {
                Text(
                    text = strings.raw("analytics.title"),
                    style = NxTypography.headline,
                    color = NxTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(NxSpacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                    AnalyticsState.Range.entries.forEach { range ->
                        NxChip(
                            text = strings.raw(range.labelKey),
                            selected = range == state.range,
                            tone = if (range == state.range) ChipTone.ACCENT else ChipTone.NEUTRAL,
                            onClick = { onSelectRange(range) },
                        )
                    }
                }
            }
        }

        if (!state.hasData) {
            item {
                NxEmptyState(
                    title = strings.raw("analytics.empty.title"),
                    body = strings.raw("analytics.empty.body"),
                    actionLabel = strings.raw("analytics.empty.action"),
                    onAction = onStartTraining,
                )
            }
            item { Spacer(Modifier.height(NxSpacing.xxl)) }
            return@LazyColumn
        }

        item {
            NxSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(NxSpacing.lg)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.xl)) {
                        NxStat(strings.raw("analytics.sessions"), strings.decimal(state.completedCount.toLong()))
                        NxStat(strings.raw("analytics.activeMinutes"), strings.decimal(state.activeMinutes.toLong()))
                        NxStat(strings.raw("analytics.xp"), strings.decimal(state.totalXp.toLong()))
                    }
                    Spacer(Modifier.height(NxSpacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.xl)) {
                        NxStat(strings.raw("analytics.accuracy"), strings.percent(state.meanAccuracy))
                        NxStat(strings.raw("analytics.streak"), strings.format("unit.days", state.streakDays))
                        NxStat(strings.raw("analytics.bestStreak"), strings.format("unit.days", state.longestStreakDays))
                    }
                    if (state.abandonedCount > 0) {
                        Spacer(Modifier.height(NxSpacing.sm))
                        Text(
                            text = strings.format(
                                "analytics.unfinishedCount",
                                strings.format("unit.sessions", state.abandonedCount),
                            ),
                            style = NxTypography.caption,
                            color = NxTheme.colors.textTertiary,
                        )
                    }
                }
            }
        }

        item { NxSectionHeader(title = strings.raw("analytics.daily"), supporting = strings.raw("analytics.range.${state.range.name.lowercase()}")) }

        if (state.hasTrend) {
            item {
                NxSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(NxSpacing.lg)) {
                        ActivityChart(
                            values = state.dailySeries.map { it.value },
                            strings = strings,
                            label = strings.raw("analytics.daily"),
                        )
                    }
                }
            }
        } else {
            item {
                NxSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(NxSpacing.lg)) {
                        Text(
                            text = strings.raw("analytics.adaptiveNotEnough"),
                            style = NxTypography.body,
                            color = NxTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }

        item { NxSectionHeader(title = strings.raw("analytics.byModule")) }
        items(state.perModule) { moduleStats -> ModuleStatsRow(moduleStats, strings) }

        item {
            Text(
                text = strings.raw("analytics.comparabilityNote"),
                style = NxTypography.caption,
                color = NxTheme.colors.textTertiary,
            )
        }

        state.baselineComparison?.let { baseline ->
            item { NxSectionHeader(title = strings.raw("analytics.baseline")) }
            item { BaselineCard(baseline, strings) }
        }

        if (state.personalRecords.isNotEmpty()) {
            item { NxSectionHeader(title = strings.raw("analytics.personalRecords")) }
            items(state.personalRecords) { record -> RecordRowView(record, strings) }
        }

        if (state.weakSkills.isNotEmpty()) {
            item { NxSectionHeader(title = strings.raw("analytics.weakSkills")) }
            items(state.weakSkills) { skill -> WeakSkillRowView(skill, strings) }
        }

        if (state.errorBreakdown.isNotEmpty()) {
            item { NxSectionHeader(title = strings.raw("analytics.errorBreakdown")) }
            items(state.errorBreakdown) { row -> ErrorRowView(row, strings) }
        }

        item { Spacer(Modifier.height(NxSpacing.xxl)) }
    }
}

@Composable
private fun ActivityChart(values: List<Double>, strings: Strings, label: String) {
    val accent = NxTheme.colors.accent
    val grid = NxTheme.colors.border
    val max = values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .semantics { contentDescription = strings.format("a11y.chart", label) },
    ) {
        // Baseline and midpoint grid: enough structure to read a value, not so much
        // that the data disappears behind a grid.
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
        drawLine(grid, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 1f)

        if (values.size < 2) return@Canvas
        val path = Path()
        val stepX = size.width / (values.size - 1).toFloat()
        values.forEachIndexed { index, value ->
            val x = stepX * index
            val y = size.height - (value / max * size.height * 0.9f).toFloat() - size.height * 0.05f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = accent, style = Stroke(width = 2.5f))
    }
}

@Composable
private fun ModuleStatsRow(stats: ModuleAnalytics, strings: Strings) {
    NxSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(NxSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.raw("module.${stats.module.id}"),
                    style = NxTypography.title,
                    color = NxTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                NxChip(
                    text = when {
                        stats.sessions == 0 -> strings.raw("common.none")
                        stats.trend > 0.02 -> strings.raw("analytics.trendUp")
                        stats.trend < -0.02 -> strings.raw("analytics.trendDown")
                        else -> strings.raw("analytics.trendFlat")
                    },
                    tone = when {
                        stats.sessions == 0 -> ChipTone.NEUTRAL
                        stats.trend > 0.02 -> ChipTone.SUCCESS
                        stats.trend < -0.02 -> ChipTone.WARNING
                        else -> ChipTone.NEUTRAL
                    },
                )
            }
            if (stats.sessions == 0) {
                Text(
                    text = strings.raw("home.empty.title"),
                    style = NxTypography.caption,
                    color = NxTheme.colors.textTertiary,
                )
                return@Column
            }
            Spacer(Modifier.height(NxSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.xl)) {
                NxStat(strings.raw("analytics.sessions"), strings.decimal(stats.sessions.toLong()))
                NxStat(strings.raw("analytics.accuracy"), strings.percent(stats.accuracy))
                NxStat(
                    strings.raw("analytics.reaction"),
                    if (stats.meanResponseMillis > 0) strings.millis(stats.meanResponseMillis) else "-",
                )
            }
            Spacer(Modifier.height(NxSpacing.sm))
            Text(
                text = strings.format("analytics.baselineDelta", strings.decimal(stats.bestScore.toLong())),
                style = NxTypography.caption,
                color = NxTheme.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun BaselineCard(baseline: BaselineComparison, strings: Strings) {
    NxSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(NxSpacing.lg)) {
            baseline.rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = NxSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.raw(row.template.displayKey),
                        style = NxTypography.body,
                        color = NxTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = strings.decimal(row.currentScore.toLong()),
                        style = NxTypography.label,
                        color = NxTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.size(NxSpacing.sm))
                    val delta = row.delta()
                    Text(
                        text = (if (delta >= 0) "+" else "") + strings.decimal(delta.toLong()),
                        style = NxTypography.caption,
                        color = if (row.improved) NxTheme.colors.success else NxTheme.colors.warning,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordRowView(record: RecordRow, strings: Strings) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NxSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = strings.raw(record.template.displayKey),
                style = NxTypography.body,
                color = NxTheme.colors.textPrimary,
            )
            Text(
                text = strings.raw("difficulty.${record.difficulty}"),
                style = NxTypography.caption,
                color = NxTheme.colors.textTertiary,
            )
        }
        Text(
            text = strings.decimal(record.bestScore.toLong()),
            style = NxTypography.title,
            color = NxTheme.colors.accent,
        )
    }
}

@Composable
private fun WeakSkillRowView(skill: WeakSkillRow, strings: Strings) {
    NxSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(NxSpacing.lg)) {
            Text(
                text = strings.raw(skill.template.displayKey),
                style = NxTypography.label,
                color = NxTheme.colors.textPrimary,
            )
            Text(
                text = strings.format("unit.of", strings.percent(skill.accuracy), strings.format("unit.sessions", skill.sessions)),
                style = NxTypography.caption,
                color = NxTheme.colors.textTertiary,
            )
            Spacer(Modifier.height(NxSpacing.sm))
            NxProgressBar(progress = skill.accuracy.toFloat())
        }
    }
}

@Composable
private fun ErrorRowView(row: ErrorRow, strings: Strings) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NxSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(NxRadius.xs))
                .background(moduleAccent(row.module.id)),
        )
        Spacer(Modifier.size(NxSpacing.sm))
        Text(
            text = strings.raw(row.template.displayKey),
            style = NxTypography.body,
            color = NxTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = strings.decimal(row.errorCount.toLong()),
            style = NxTypography.label,
            color = NxTheme.colors.textSecondary,
        )
        Spacer(Modifier.size(NxSpacing.sm))
        Text(
            text = strings.percent(row.share),
            style = NxTypography.caption,
            color = NxTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun moduleAccent(id: String): Color = when (id) {
    "vision" -> NxTheme.colors.vision
    "reflex" -> NxTheme.colors.reflex
    "read" -> NxTheme.colors.read
    else -> NxTheme.colors.type
}
