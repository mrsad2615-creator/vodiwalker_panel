package com.neurox.feature.vision

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.neurox.designsystem.components.ChipTone
import com.neurox.designsystem.components.NxChip
import com.neurox.designsystem.components.NxPrimaryButton
import com.neurox.designsystem.components.NxSecondaryButton
import com.neurox.designsystem.components.NxSectionHeader
import com.neurox.designsystem.tokens.NxSpacing
import com.neurox.designsystem.components.NxSurface
import com.neurox.designsystem.theme.NxTheme
import com.neurox.designsystem.theme.NxTypography
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.exercise.Skill
import com.neurox.domain.model.Module
import com.neurox.presentation.i18n.Strings

/**
 * One module's exercise list.
 *
 * WHY ONE FILE SERVES ALL FOUR MODULES: VISION, REFLEX, READ and TYPE differ in what
 * they measure, not in how a module is browsed. Four near-identical screens would
 * be four places to fix a layout bug, and four chances for one module to drift out
 * of parity — exactly what "all four modules are equal" is supposed to prevent.
 *
 * Each exercise card states the skill it trains and the difficulty that will be
 * used, so the list is informative before the user starts anything.
 */
@Composable
fun ModuleBrowserScreen(
    module: Module,
    templates: List<ExerciseTemplate>,
    strings: Strings,
    accent: Color,
    bestScores: Map<String, Double>,
    lastPlayed: Map<String, Long>,
    recommendedId: String?,
    onStart: (ExerciseTemplate) -> Unit,
    onOpenDetail: (ExerciseTemplate) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = NxSpacing.lg),
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(NxSpacing.md),
    ) {
        item {
            Column(modifier = Modifier.padding(top = NxSpacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                    Spacer(Modifier.size(NxSpacing.sm))
                    Text(
                        text = strings.raw("module.${module.id}"),
                        style = NxTypography.headline,
                        color = NxTheme.colors.textPrimary,
                    )
                }
                Text(
                    text = strings.raw("module.${module.id}.skill"),
                    style = NxTypography.caption,
                    color = NxTheme.colors.textTertiary,
                )
            }
        }

        if (templates.isEmpty()) {
            item {
                NxSurface(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = strings.raw("common.empty"),
                        style = NxTypography.body,
                        color = NxTheme.colors.textSecondary,
                        modifier = Modifier.padding(NxSpacing.lg),
                    )
                }
            }
        }

        items(templates) { template ->
            ExerciseListCard(
                template = template,
                strings = strings,
                accent = accent,
                bestScore = bestScores[template.id],
                lastPlayedMillis = lastPlayed[template.id],
                recommended = template.id == recommendedId,
                onStart = { onStart(template) },
                onOpenDetail = { onOpenDetail(template) },
            )
        }
    }
}

@Composable
private fun ExerciseListCard(
    template: ExerciseTemplate,
    strings: Strings,
    accent: Color,
    bestScore: Double?,
    lastPlayedMillis: Long?,
    recommended: Boolean,
    onStart: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val name = strings.raw(template.displayKey)
    NxSurface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = strings.format(
                    "a11y.exerciseCard",
                    name,
                    strings.raw("module.${template.module.id}"),
                )
            },
    ) {
        Column(modifier = Modifier.padding(NxSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name, style = NxTypography.title, color = NxTheme.colors.textPrimary)
                    Text(
                        text = strings.raw(template.descriptionKey),
                        style = NxTypography.caption,
                        color = NxTheme.colors.textSecondary,
                    )
                }
                if (recommended) {
                    NxChip(text = strings.raw("home.recommended"), tone = ChipTone.ACCENT)
                }
            }
            Spacer(Modifier.height(NxSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.xs)) {
                template.skills.take(2).forEach { skill ->
                    NxChip(text = skillsLabel(skill, strings))
                }
                NxChip(text = strings.format("home.minutes", (template.defaultDurationSeconds / 60).coerceAtLeast(1)))
            }
            Spacer(Modifier.height(NxSpacing.xs))
            Text(
                text = when {
                    bestScore == null && lastPlayedMillis == null -> strings.raw("home.empty.title")
                    bestScore == null -> strings.raw("home.empty.title")
                    else -> strings.format("unit.of", strings.decimal(bestScore.toLong()), strings.raw("session.score"))
                },
                style = NxTypography.caption,
                color = NxTheme.colors.textTertiary,
            )
            Spacer(Modifier.height(NxSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                NxPrimaryButton(
                    label = strings.format("a11y.startButton", name),
                    onClick = onStart,
                )
                NxSecondaryButton(
                    label = strings.raw("common.learnMore"),
                    onClick = onOpenDetail,
                )
            }
        }
    }
}

@Composable
private fun skillsLabel(skill: Skill, strings: Strings): String =
    strings.raw("skill.${skill.id}")

/** Detail sheet content: what the exercise measures and how difficulty behaves. */
@Composable
fun ExerciseDetailSection(
    template: ExerciseTemplate,
    strings: Strings,
    difficulty: Int,
    trained: Int,
    averageAccuracy: Double?,
    onStart: (ExerciseTemplate) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(NxSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NxSpacing.md),
    ) {
        Text(
            text = strings.raw(template.displayKey),
            style = NxTypography.headline,
            color = NxTheme.colors.textPrimary,
        )
        Text(
            text = strings.raw(template.descriptionKey),
            style = NxTypography.body,
            color = NxTheme.colors.textSecondary,
        )
        NxSectionHeader(title = strings.raw("difficulty.hint"))
        Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.xs)) {
            (1..5).forEach { level ->
                NxChip(
                    text = strings.decimal(level.toLong()),
                    selected = level == difficulty,
                    tone = if (level == difficulty) ChipTone.ACCENT else ChipTone.NEUTRAL,
                )
            }
        }
        Text(
            text = strings.format("unit.sessions", trained),
            style = NxTypography.caption,
            color = NxTheme.colors.textTertiary,
        )
        if (averageAccuracy != null) {
            Text(
                text = strings.percent(averageAccuracy),
                style = NxTypography.label,
                color = NxTheme.colors.textPrimary,
            )
        } else {
            // No history is stated as no history, not as 0% accuracy.
            Text(
                text = strings.raw("home.empty.title"),
                style = NxTypography.caption,
                color = NxTheme.colors.textTertiary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
            template.skills.forEach { skill ->
                NxChip(text = skillsLabel(skill, strings))
            }
        }
        NxPrimaryButton(
            label = strings.format("a11y.startButton", strings.raw(template.displayKey)),
            onClick = { onStart(template) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
