package com.neurox.feature.vision

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.neurox.presentation.di.AppServices
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.model.Module
import com.neurox.domain.model.SessionStatus
import com.neurox.presentation.i18n.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stateful entry point for a module browser.
 *
 * Personal bests and last-played times are read for the *active profile only*, through
 * repository calls that take the profile id. A module screen therefore cannot show
 * another profile's records even if a stale state object were reused.
 */
@Composable
fun ModuleBrowserRoute(
    services: AppServices,
    module: Module,
    strings: Strings,
    onStartExercise: (ExerciseTemplate) -> Unit,
    onOpenReadingStudio: () -> Unit,
) {
    var bestScores by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var lastPlayed by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var recommendedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(module) {
        withContext(Dispatchers.IO) {
            val active = services.profileRepository.getActiveProfile() ?: return@withContext
            val records = services.sessionRepository.listAll(active.id)
                .filter { it.status == SessionStatus.COMPLETED }
            bestScores = records.groupBy { it.exerciseId }
                .mapValues { (_, list) -> list.maxOf { it.score } }
            lastPlayed = records.groupBy { it.exerciseId }
                .mapValues { (_, list) -> list.maxOf { it.endedAtMillis } }
            val suggestion = com.neurox.domain.progression.Recommender.suggest(
                history = records,
                untrainedModules = Module.entries.filter { m ->
                    records.none { it.module == m }
                }.toSet(),
                nowMillis = System.currentTimeMillis(),
            )
            recommendedId = suggestion.template.takeIf { it.module == module }?.id
        }
    }

    ModuleBrowserScreen(
        module = module,
        templates = ExerciseTemplate.forModule(module),
        strings = strings,
        accent = moduleAccent(module),
        bestScores = bestScores,
        lastPlayed = lastPlayed,
        recommendedId = recommendedId,
        onStart = onStartExercise,
        onOpenDetail = { template ->
            if (template == ExerciseTemplate.RSVP_READER) onOpenReadingStudio()
        },
    )
}

/**
 * Accent per module.
 *
 * The exact colors come from the design system; this indirection exists so the route
 * file does not reach into Compose theming from a non-composable context.
 */
private fun moduleAccent(module: Module): Color = when (module) {
    Module.VISION -> Color(0xFF8FB4E8)
    Module.REFLEX -> Color(0xFFE8A06A)
    Module.READ -> Color(0xFF9BCB9A)
    Module.TYPE -> Color(0xFFC79FD6)
}
