package com.neurox.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.neurox.presentation.di.AppServices
import com.neurox.common.json.asObj
import com.neurox.common.json.asStringOrNull
import com.neurox.common.result.DomainError
import com.neurox.designsystem.components.NxErrorState
import com.neurox.designsystem.components.NxLoading
import com.neurox.domain.model.Module
import com.neurox.domain.model.SessionRecord
import com.neurox.domain.model.SessionStatus
import com.neurox.domain.model.ProfileSummary
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.screen.HomeState
import com.neurox.presentation.screen.ScreenBuilders
import com.neurox.presentation.screen.ScreenState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the exercise id out of a stored session checkpoint.
 *
 * The checkpoint is JSON written by the session layer; this reads a single field and
 * returns null for anything unexpected, because a corrupt checkpoint must degrade to
 * "start a new session", never to a crash on the home screen.
 */
internal fun decodeExerciseId(checkpointJson: String): String? = runCatching {
    com.neurox.common.json.JsonParser.parse(checkpointJson).asObj()["exerciseId"].asStringOrNull()
}.getOrNull()

/**
 * Stateful entry point for Home.
 *
 * ORDERING RULE: the app container is an Android-only type, so it is referenced here
 * (feature modules are Android libraries) and never inside core:presentation. This
 * route loads data on the IO dispatcher, builds a [HomeState] with the pure builder,
 * and hands the result to the stateless screen.
 *
 * STALE DATA: the load is re-run whenever the profile changes, because a profile
 * switch must never leave another person's numbers on screen even for one frame.
 */
@Composable
fun HomeRoute(
    services: AppServices,
    strings: Strings,
    onOpenModule: (Module) -> Unit,
    onStartExercise: (com.neurox.domain.exercise.ExerciseTemplate) -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    var state by remember { mutableStateOf<ScreenState<HomeState>>(ScreenState.Loading) }
    var profile by remember { mutableStateOf<ProfileSummary?>(null) }
    // The checkpoint JSON carries the exercise the user was in; the route decodes it
    // so "continue" resumes that exercise instead of guessing one.
    var resumeExerciseId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profile?.id, profile?.displayName) {
        state = ScreenState.Loading
        state = withContext(Dispatchers.IO) {
            runCatching {
                val active = profile ?: services.profileRepository.getActiveProfile()
                profile = active
                if (active == null) {
                    return@runCatching ScreenState.Error(DomainError.ProfileNotFound, retryable = false)
                }
                val records: List<SessionRecord> = services.sessionRepository.listAll(active.id)
                val earned = services.achievementRepository.earnedIds(active.id)
                val baselineDone = records.any {
                    it.kind == com.neurox.domain.model.SessionKind.BASELINE &&
                        it.status == SessionStatus.COMPLETED
                }
                val checkpoint = services.sessionRepository.loadCheckpoint(active.id)
                resumeExerciseId = checkpoint?.let { decodeExerciseId(it) }
                ScreenState.Ready(
                    ScreenBuilders.home(
                        profile = active,
                        strings = strings,
                        records = records,
                        earnedAchievementIds = earned,
                        baselineComplete = baselineDone,
                        hasCheckpoint = checkpoint != null,
                        nowMillis = System.currentTimeMillis(),
                    ),
                )
            }.getOrElse { failure ->
                ScreenState.Error(DomainError.Unknown(failure.message ?: "load"), retryable = true)
            }
        }
    }

    when (val current = state) {
        is ScreenState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            NxLoading(message = strings.raw("common.loading"))
        }
        is ScreenState.Error -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            NxErrorState(
                title = strings.raw("common.error"),
                message = strings.raw(current.messageKey),
                technicalCode = current.code,
                retryLabel = strings.raw("common.retry"),
                onRetry = { profile = null },
            )
        }
        is ScreenState.Empty -> NxLoading(message = strings.raw("common.loading"))
        is ScreenState.Ready -> HomeScreen(
            state = current.value,
            onStartRecommendation = { template, _ -> onStartExercise(template) },
            onStartBaseline = {
                // The baseline is one short session per module; the first one is the
                // vision entry. The session route receives the BASELINE kind so the
                // record is stored separately from training.
                onStartExercise(com.neurox.domain.exercise.ExerciseTemplate.FOCUS_LOCK)
            },
            onOpenModule = onOpenModule,
            onOpenAnalytics = onOpenAnalytics,
            onOpenLibrary = onOpenLibrary,
            onResumeSession = {
                val template = resumeExerciseId
                    ?.let { com.neurox.domain.exercise.ExerciseTemplate.fromId(it) }
                if (template != null) {
                    onStartExercise(template)
                } else {
                    onStartExercise(current.value.recommendation.template)
                }
            },
            onOpenProfile = onOpenProfile,
        )
    }
}
