package com.neurox.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.neurox.presentation.di.AppServices
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.model.Module
import com.neurox.domain.model.ProfileSummary
import com.neurox.domain.model.SessionStatus
import com.neurox.domain.repository.CustomExercise
import com.neurox.domain.repository.first
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.screen.CustomExerciseRow
import com.neurox.presentation.screen.ScreenBuilders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stateful entry point for the library.
 *
 * CORRECTNESS NOTES
 * - Favorites and custom exercises are per profile and are read through repository
 *   calls that take the profile id; the screen cannot show another profile's rows.
 * - A favorite toggle writes first and updates the list afterwards. Optimistic UI is
 *   deliberately avoided: a star that silently fails to persist is worse than a star
 *   that appears 80 ms later.
 * - The two data sources are collected in two effects, so a never-ending stream does
 *   not block the other one.
 */
@Composable
fun LibraryRoute(
    services: AppServices,
    strings: Strings,
    onStartExercise: (ExerciseTemplate) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var moduleFilter by remember { mutableStateOf<Module?>(null) }
    var onlyFavorites by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf<Set<String>>(emptySet()) }
    var custom by remember { mutableStateOf<List<CustomExerciseRow>>(emptyList()) }
    var bestScores by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var profile by remember { mutableStateOf<ProfileSummary?>(null) }
    var reloadToken by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    // Profile, first: every other read is keyed by it.
    LaunchedEffect(Unit) {
        profile = withContext(Dispatchers.IO) { services.profileRepository.getActiveProfile() }
    }

    // Favorites stream. Stays open while the screen is composed.
    LaunchedEffect(profile?.id, reloadToken) {
        val id = profile?.id ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            services.exerciseRepository.observeFavorites(id).collect { favorites = it }
        }
    }

    // Custom exercises and personal bests, one snapshot per reload.
    LaunchedEffect(profile?.id, reloadToken) {
        val id = profile?.id ?: return@LaunchedEffect
        // first() takes the current value and returns. Collecting this stream to
        // completion never happens (it is a Room flow), so the old code hung here and
        // neither the custom list nor the personal bests below were ever set.
        val snapshot = withContext(Dispatchers.IO) {
            services.exerciseRepository.observeCustomExercises(id).first().orEmpty()
        }
        custom = snapshot.map { exercise ->
            CustomExerciseRow(
                id = exercise.id,
                name = exercise.name,
                template = ExerciseTemplate.fromId(exercise.templateId) ?: ExerciseTemplate.FOCUS_LOCK,
                difficulty = exercise.difficulty,
                rounds = exercise.config.rounds,
            )
        }
        bestScores = withContext(Dispatchers.IO) {
            services.sessionRepository.listAll(id)
                .filter { it.status == SessionStatus.COMPLETED }
                .groupBy { it.exerciseId }
                .mapValues { (_, list) -> list.maxOf { it.score } }
        }
    }

    val state = ScreenBuilders.library(
        strings = strings,
        query = query,
        moduleFilter = moduleFilter,
        onlyFavorites = onlyFavorites,
        favorites = favorites,
        custom = custom,
        personalBests = bestScores,
        lastPlayed = emptyMap(),
    )

    LibraryScreen(
        state = state,
        strings = strings,
        onQueryChange = { query = it },
        onSelectModule = { moduleFilter = it },
        onToggleFavoritesFilter = { onlyFavorites = !onlyFavorites },
        onStart = onStartExercise,
        onToggleFavorite = { template ->
            val id = profile?.id
            if (id != null) {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        services.exerciseRepository.toggleFavorite(id, template.id)
                    }
                    reloadToken += 1
                }
            }
        },
        onCreateExercise = { reloadToken += 1 },
        onOpenBuilder = { reloadToken += 1 },
    )
}
