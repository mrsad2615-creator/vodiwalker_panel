package com.neurox.feature.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.neurox.designsystem.components.NxSecondaryButton
import com.neurox.designsystem.components.NxSectionHeader
import com.neurox.designsystem.tokens.NxSize
import com.neurox.designsystem.tokens.NxSpacing
import com.neurox.designsystem.components.NxStat
import com.neurox.designsystem.components.NxSurface
import com.neurox.designsystem.theme.NxTheme
import com.neurox.designsystem.theme.NxTypography
import com.neurox.designsystem.components.nxAccentBrush
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.model.Module
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.screen.HomeState
import com.neurox.presentation.screen.MissionCard
import com.neurox.presentation.screen.ModuleCard
import com.neurox.presentation.screen.RecommendationCard
import com.neurox.presentation.screen.SessionRow

/**
 * Home.
 *
 * THE SHAPE OF THE SCREEN: one hero card that answers "what should I do right
 * now", then progress, then missions, then the four modules. A new profile sees an
 * empty state that explains what will happen, never a dashboard of zeros.
 *
 * Every number on this screen comes from [HomeState], which is built from stored
 * sessions. Nothing is placeheld or invented.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    onStartRecommendation: (ExerciseTemplate, Int) -> Unit,
    onStartBaseline: () -> Unit,
    onOpenModule: (Module) -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenLibrary: () -> Unit,
    onResumeSession: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = state.strings
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = NxSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NxSpacing.lg),
    ) {
        item { HomeHeader(state = state, strings = strings, onOpenProfile = onOpenProfile) }

        if (state.hasCheckpoint) {
            item {
                NxSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(NxSpacing.lg)) {
                        Text(
                            text = strings.raw("home.continuePrompt"),
                            style = NxTypography.title,
                            color = NxTheme.colors.textPrimary,
                        )
                        Spacer(Modifier.height(NxSpacing.sm))
                        NxPrimaryButton(
                            label = strings.raw("common.continue"),
                            onClick = onResumeSession,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        if (state.isEmpty) {
            item {
                NxEmptyState(
                    title = strings.raw("home.empty.title"),
                    body = strings.raw("home.empty.body"),
                    actionLabel = strings.raw("home.empty.action"),
                    onAction = { onStartRecommendation(state.recommendation.template, state.recommendation.difficulty) },
                )
            }
        } else {
            item {
                RecommendationHero(
                    recommendation = state.recommendation,
                    strings = strings,
                    onStart = { onStartRecommendation(state.recommendation.template, state.recommendation.difficulty) },
                )
            }
            item { ProgressStrip(state = state, strings = strings, onOpenAnalytics = onOpenAnalytics) }
        }

        if (state.showBaselinePrompt) {
            item {
                NxSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(NxSpacing.lg)) {
                        NxSectionHeader(title = strings.raw("home.baselinePrompt"))
                        Text(
                            text = strings.raw("home.baselineBody"),
                            style = NxTypography.caption,
                            color = NxTheme.colors.textSecondary,
                        )
                        Spacer(Modifier.height(NxSpacing.md))
                        NxSecondaryButton(
                            label = strings.raw("home.baselineStart"),
                            onClick = onStartBaseline,
                        )
                    }
                }
            }
        }

        state.recoveryHintKey?.let { hintKey ->
            item {
                NxSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(NxSpacing.lg)) {
                        Text(
                            text = strings.raw("home.recovery"),
                            style = NxTypography.label,
                            color = NxTheme.colors.warning,
                        )
                        Text(
                            text = strings.raw(hintKey),
                            style = NxTypography.caption,
                            color = NxTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }

        if (state.missions.isNotEmpty()) {
            item { NxSectionHeader(title = strings.raw("home.quest.daily")) }
            items(state.missions) { mission ->
                MissionRow(mission = mission, strings = strings)
            }
        }

        item {
            NxSectionHeader(
                title = strings.raw("home.modules"),
                actionLabel = strings.raw("library.title"),
                onAction = onOpenLibrary,
            )
        }
        items(state.moduleCards) { card ->
            ModuleRow(card = card, strings = strings, onClick = { onOpenModule(card.module) })
        }

        if (state.recentSessions.isNotEmpty()) {
            item { NxSectionHeader(title = strings.raw("home.recent")) }
            items(state.recentSessions) { row -> RecentRow(row = row, strings = strings) }
        }

        item { Spacer(Modifier.height(NxSpacing.xxl)) }
    }
}

@Composable
private fun HomeHeader(state: HomeState, strings: Strings, onOpenProfile: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = NxSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = strings.format("home.greeting", state.profile.displayName),
                style = NxTypography.headline,
                color = NxTheme.colors.textPrimary,
            )
            Text(
                text = strings.raw("home.title"),
                style = NxTypography.caption,
                color = NxTheme.colors.textTertiary,
            )
        }
        Box(
            modifier = Modifier
                .size(NxSize.avatarMd)
                .clip(CircleShape)
                .background(nxAccentBrush())
                .semantics { contentDescription = strings.format("a11y.avatar", state.profile.avatarId) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.profile.displayName.take(1).uppercase(),
                style = NxTypography.label,
                color = NxTheme.colors.onAccent,
            )
        }
        Spacer(Modifier.width(NxSpacing.sm))
        NxSecondaryButton(
            label = strings.raw("profile.title"),
            onClick = onOpenProfile,
        )
    }
}

@Composable
private fun RecommendationHero(
    recommendation: RecommendationCard,
    strings: Strings,
    onStart: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NxRadius.sheet))
            .background(
                Brush.verticalGradient(
                    listOf(
                        NxTheme.colors.surfaceElevated,
                        NxTheme.colors.surface,
                    ),
                ),
            ),
    ) {
        Column(modifier = Modifier.padding(NxSpacing.xl)) {
            Text(
                text = strings.raw("home.recommended"),
                style = NxTypography.caption,
                color = NxTheme.colors.accent,
            )
            Spacer(Modifier.height(NxSpacing.xs))
            Text(
                text = strings.raw(recommendation.template.displayKey),
                style = NxTypography.display,
                color = NxTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(NxSpacing.xs))
            Text(
                text = strings.raw(recommendation.template.descriptionKey),
                style = NxTypography.body,
                color = NxTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(NxSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                NxChip(text = strings.raw("difficulty.${recommendation.difficulty}"), tone = ChipTone.ACCENT)
                NxChip(text = strings.format("home.minutes", recommendation.minutes))
                NxChip(text = strings.raw("module.${recommendation.template.module.id}"))
            }
            Spacer(Modifier.height(NxSpacing.lg))
            NxPrimaryButton(
                label = strings.format("home.startSession", strings.raw(recommendation.template.displayKey)),
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(NxSpacing.xs))
            Text(
                text = strings.raw("home.recommendedReason.${recommendation.reasonKey.substringAfterLast('.')}"),
                style = NxTypography.caption,
                color = NxTheme.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun ProgressStrip(state: HomeState, strings: Strings, onOpenAnalytics: () -> Unit) {
    NxSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(NxSpacing.lg)) {
            Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.xl)) {
                NxStat(
                    label = strings.raw("home.level"),
                    value = strings.decimal(state.level.toLong()),
                    supporting = strings.format("home.xpToNext", state.xpToNextLevel, state.level + 1),
                )
                NxStat(
                    label = strings.raw("home.streak"),
                    value = if (state.streakDays == 0) {
                        strings.raw("home.streakNone")
                    } else {
                        strings.format("home.streakValue", state.streakDays)
                    },
                )
            }
            Spacer(Modifier.height(NxSpacing.md))
            NxProgressBar(
                progress = state.levelProgress.toFloat(),
                label = strings.raw("home.level"),
                valueText = strings.percent(state.levelProgress),
            )
            Spacer(Modifier.height(NxSpacing.md))
            NxSecondaryButton(label = strings.raw("analytics.title"), onClick = onOpenAnalytics)
        }
    }
}

@Composable
private fun MissionRow(mission: MissionCard, strings: Strings) {
    NxSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(NxSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.format(mission.titleKey, mission.target),
                    style = NxTypography.label,
                    color = NxTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                NxChip(
                    text = if (mission.complete) {
                        strings.raw("quest.complete")
                    } else {
                        strings.format("unit.xp", mission.xpReward)
                    },
                    tone = if (mission.complete) ChipTone.SUCCESS else ChipTone.NEUTRAL,
                )
            }
            Spacer(Modifier.height(NxSpacing.sm))
            NxProgressBar(
                progress = mission.fraction.toFloat(),
                valueText = strings.format("quest.progress", mission.current, mission.target),
            )
        }
    }
}

@Composable
private fun ModuleRow(card: ModuleCard, strings: Strings, onClick: () -> Unit) {
    val label = strings.raw("module.${card.module.id}")
    NxSurface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = strings.format("a11y.moduleCard", label, strings.raw("module.${card.module.id}.skill"))
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NxSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(moduleColor(card.module)),
            )
            Spacer(Modifier.width(NxSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = NxTypography.title, color = NxTheme.colors.textPrimary)
                Text(
                    text = strings.raw("module.${card.module.id}.skill"),
                    style = NxTypography.caption,
                    color = NxTheme.colors.textTertiary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (card.hasData) {
                        strings.percent(card.accuracy)
                    } else {
                        strings.raw("common.none")
                    },
                    style = NxTypography.label,
                    color = if (card.hasData) NxTheme.colors.textPrimary else NxTheme.colors.textTertiary,
                )
                Text(
                    text = strings.format("unit.sessions", card.sessionCount),
                    style = NxTypography.caption,
                    color = NxTheme.colors.textTertiary,
                )
            }
            Spacer(Modifier.width(NxSpacing.md))
            NxSecondaryButton(label = strings.raw("common.start"), onClick = onClick)
        }
    }
}

@Composable
private fun RecentRow(row: SessionRow, strings: Strings) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NxSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.template?.let { strings.raw(it.displayKey) } ?: row.module.id,
                style = NxTypography.body,
                color = NxTheme.colors.textPrimary,
            )
            Text(
                text = strings.format("unit.percent", strings.percent(row.accuracy)),
                style = NxTypography.caption,
                color = NxTheme.colors.textTertiary,
            )
        }
        Text(
            // A session that does not count says so, instead of showing an XP of 0
            // as if the user had earned nothing for a normal run.
            text = if (row.counts) strings.format("unit.xp", row.xp) else strings.raw("session.result.aborted"),
            style = NxTypography.caption,
            color = if (row.counts) NxTheme.colors.accent else NxTheme.colors.textTertiary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun moduleColor(module: Module) = when (module) {
    Module.VISION -> NxTheme.colors.vision
    Module.REFLEX -> NxTheme.colors.reflex
    Module.READ -> NxTheme.colors.read
    Module.TYPE -> NxTheme.colors.type
}
