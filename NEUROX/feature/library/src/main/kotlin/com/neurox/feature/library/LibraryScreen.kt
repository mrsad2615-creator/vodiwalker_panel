package com.neurox.feature.library

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
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
import com.neurox.designsystem.components.NxEmptyState
import com.neurox.designsystem.components.NxPrimaryButton
import com.neurox.designsystem.tokens.NxRadius
import com.neurox.designsystem.components.NxSecondaryButton
import com.neurox.designsystem.components.NxSectionHeader
import com.neurox.designsystem.tokens.NxSpacing
import com.neurox.designsystem.components.NxSurface
import com.neurox.designsystem.theme.NxTheme
import com.neurox.designsystem.theme.NxTypography
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.model.Module
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.screen.CustomExerciseRow
import com.neurox.presentation.screen.LibraryItem
import com.neurox.presentation.screen.LibraryState

/**
 * Exercise library.
 *
 * The library is the catalogue plus the user's own exercises. Search accepts the
 * localized name *or* the technical id, so a Persian user and a power user both
 * find what they want. Favorites are stored per profile; the list says so when it
 * is empty rather than showing an unexplained blank section.
 */
@Composable
fun LibraryScreen(
    state: LibraryState,
    strings: Strings,
    onQueryChange: (String) -> Unit,
    onSelectModule: (Module?) -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onStart: (ExerciseTemplate) -> Unit,
    onToggleFavorite: (ExerciseTemplate) -> Unit,
    onCreateExercise: () -> Unit,
    onOpenBuilder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = NxSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NxSpacing.md),
    ) {
        item {
            Column(modifier = Modifier.padding(top = NxSpacing.lg)) {
                Text(
                    text = strings.raw("library.title"),
                    style = NxTypography.headline,
                    color = NxTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(NxSpacing.md))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.raw("library.search"), style = NxTypography.caption) },
                )
                Spacer(Modifier.height(NxSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.xs)) {
                    NxChip(
                        text = strings.raw("common.all"),
                        selected = state.moduleFilter == null,
                        tone = if (state.moduleFilter == null) ChipTone.ACCENT else ChipTone.NEUTRAL,
                        onClick = { onSelectModule(null) },
                    )
                    Module.entries.forEach { module ->
                        NxChip(
                            text = strings.raw("module.${module.id}"),
                            selected = state.moduleFilter == module,
                            tone = if (state.moduleFilter == module) ChipTone.ACCENT else ChipTone.NEUTRAL,
                            onClick = { onSelectModule(module) },
                        )
                    }
                    NxChip(
                        text = strings.raw("library.favorites"),
                        selected = state.onlyFavorites,
                        tone = if (state.onlyFavorites) ChipTone.ACCENT else ChipTone.NEUTRAL,
                        onClick = onToggleFavoritesFilter,
                    )
                }
            }
        }

        if (state.isSearchEmpty) {
            item {
                NxEmptyState(
                    title = strings.raw("library.search"),
                    body = strings.format("library.searchEmpty", state.query),
                )
            }
        }

        if (state.custom.isNotEmpty()) {
            item {
                NxSectionHeader(
                    title = strings.raw("library.custom"),
                    actionLabel = strings.raw("library.builder.start"),
                    onAction = onCreateExercise,
                )
            }
            items(state.custom) { custom ->
                CustomExerciseCard(custom = custom, strings = strings, onStart = onStart)
            }
        } else if (state.isEmptyQuery && !state.onlyFavorites) {
            item {
                NxSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(NxSpacing.lg)) {
                        Text(
                            text = strings.raw("library.custom"),
                            style = NxTypography.title,
                            color = NxTheme.colors.textPrimary,
                        )
                        Spacer(Modifier.height(NxSpacing.xs))
                        Text(
                            text = strings.raw("library.customEmpty"),
                            style = NxTypography.caption,
                            color = NxTheme.colors.textSecondary,
                        )
                        Spacer(Modifier.height(NxSpacing.md))
                        NxSecondaryButton(
                            label = strings.raw("library.builder.start"),
                            onClick = onOpenBuilder,
                        )
                    }
                }
            }
        }

        if (state.onlyFavorites && state.totalItems == 0) {
            item {
                NxEmptyState(
                    title = strings.raw("library.favorites"),
                    body = strings.raw("library.favoritesEmpty"),
                )
            }
        }

        items(state.groups) { group ->
            Column {
                NxSectionHeader(title = strings.raw("module.${group.module.id}"))
                Column(verticalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                    group.items.forEach { item ->
                        ExerciseCard(
                            item = item,
                            strings = strings,
                            onStart = { onStart(item.template) },
                            onToggleFavorite = { onToggleFavorite(item.template) },
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(NxSpacing.xxl)) }
    }
}

@Composable
private fun ExerciseCard(
    item: LibraryItem,
    strings: Strings,
    onStart: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    NxSurface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = strings.format(
                    "a11y.exerciseCard",
                    strings.raw(item.template.displayKey),
                    strings.raw("module.${item.template.module.id}"),
                )
            },
    ) {
        Column(modifier = Modifier.padding(NxSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(moduleColor(item.template.module)),
                )
                Spacer(Modifier.size(NxSpacing.sm))
                Text(
                    text = strings.raw(item.template.displayKey),
                    style = NxTypography.title,
                    color = NxTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                // A property cannot be smart-cast across module boundaries, so the
                // value is captured once instead of being read twice.
                val best = item.bestScore
                if (best != null) {
                    NxChip(
                        text = strings.decimal(best.toLong()),
                        tone = ChipTone.ACCENT,
                    )
                }
            }
            Spacer(Modifier.height(NxSpacing.xs))
            Text(
                text = strings.raw(item.template.descriptionKey),
                style = NxTypography.caption,
                color = NxTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(NxSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                NxPrimaryButton(
                    label = strings.format("a11y.startButton", strings.raw(item.template.displayKey)),
                    onClick = onStart,
                )
                NxSecondaryButton(
                    label = strings.raw(
                        if (item.favorite) "library.favorite.remove" else "library.favorite.add",
                    ),
                    onClick = onToggleFavorite,
                )
            }
        }
    }
}

@Composable
private fun CustomExerciseCard(custom: CustomExerciseRow, strings: Strings, onStart: (ExerciseTemplate) -> Unit) {
    NxSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NxSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = custom.name, style = NxTypography.title, color = NxTheme.colors.textPrimary)
                Text(
                    text = strings.format(
                        "unit.of",
                        strings.raw(custom.template.displayKey),
                        strings.raw("difficulty.${custom.difficulty}"),
                    ),
                    style = NxTypography.caption,
                    color = NxTheme.colors.textTertiary,
                )
            }
            NxSecondaryButton(label = strings.raw("library.start"), onClick = { onStart(custom.template) })
        }
    }
}

@Composable
private fun moduleColor(module: Module): Color = when (module) {
    Module.VISION -> NxTheme.colors.vision
    Module.REFLEX -> NxTheme.colors.reflex
    Module.READ -> NxTheme.colors.read
    Module.TYPE -> NxTheme.colors.type
}
