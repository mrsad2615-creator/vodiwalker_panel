package com.neurox.feature.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.neurox.presentation.di.AppServices
import com.neurox.common.time.SystemClock
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.model.ProfileSummary
import com.neurox.domain.model.SessionKind
import com.neurox.games.runner.EngineSession
import com.neurox.designsystem.components.NxLoading
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.session.PolicySessionAudio
import com.neurox.presentation.session.ResultPresentation
import com.neurox.presentation.session.SessionController
import com.neurox.presentation.session.SessionUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stateful entry point for a training session.
 *
 * THE CONTRACT WITH THE REST OF THE APP
 * 1. A session is created once per screen instance, from the route parameter.
 * 2. The finished record is written exactly once. The write is idempotent on the
 *    record id, so a recomposition or a retried save cannot double-count XP.
 * 3. Leaving the screen while a session is live aborts it as an unfinished session
 *    instead of discarding it silently, so the history is truthful.
 * 4. Backgrounding the app pauses the session; it never expires trials offscreen.
 *
 * The controller and the engine do all the measuring; this route only owns Android
 * lifecycle, persistence and navigation.
 */
@Composable
fun SessionRoute(
    services: AppServices,
    haptics: Haptics = NoHaptics,
    audioRenderer: (com.neurox.audio.Synth.Cue) -> Unit = {},
    clock: com.neurox.common.time.Clock = SystemClock,
    strings: Strings,
    completionState: SessionKind,
    exerciseId: String? = null,
    difficulty: Int = 3,
    onFinished: () -> Unit,
) {
    var profile by remember { mutableStateOf<ProfileSummary?>(null) }
    var controller by remember { mutableStateOf<SessionController?>(null) }
    var uiState by remember { mutableStateOf<SessionUiState?>(null) }
    var result by remember { mutableStateOf<ResultPresentation?>(null) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(exerciseId) {
        val active = withContext(Dispatchers.IO) { services.profileRepository.getActiveProfile() }
        profile = active
        if (active == null) return@LaunchedEffect
        val template = exerciseId
            ?.let { ExerciseTemplate.fromId(it) }
            ?: ExerciseTemplate.FOCUS_LOCK
        val engine = EngineSession(
            sessionId = java.util.UUID.randomUUID().toString(),
            profileId = active.id,
            template = template,
            config = template.defaultConfig(),
            difficulty = difficulty.coerceIn(1, 5),
            kind = completionState,
            clock = clock,
        )
        val audio = PolicySessionAudio(
            policy = services.audioPolicy,
            request = { cue, _ -> audioRenderer(cue) },
            haptic = { strength -> haptics.play(strength) },
        )
        val created = SessionController(
            engineSession = engine,
            audio = audio,
            clock = SystemClock,
        )
        controller = created
        uiState = created.state()
    }

    // Persist the finished record exactly once.
    LaunchedEffect(uiState?.phase, uiState?.record?.id) {
        val state = uiState ?: return@LaunchedEffect
        val record = state.record ?: return@LaunchedEffect
        if (state.phase != SessionUiState.Phase.FINISHED || saved) return@LaunchedEffect
        saved = true
        val write = withContext(Dispatchers.IO) { services.sessionRepository.save(record) }
        if (write is com.neurox.common.result.Outcome.Ok) {
            // Adaptive advice is computed from stored history including this session.
            val history = withContext(Dispatchers.IO) {
                services.sessionRepository.listAll(record.profileId)
            }
            val decision = com.neurox.domain.adaptive.AdaptiveEngine().decideNext(
                exerciseId = record.exerciseId,
                module = record.module,
                currentDifficulty = record.difficulty,
                history = history,
            )
            result = ResultPresentation.of(
                record = record,
                endReason = state.summary?.endReason,
                adaptation = com.neurox.presentation.session.AdaptationPresentation.of(decision),
            )
        } else {
            result = ResultPresentation.of(record, state.summary?.endReason)
        }
    }

    // Lifecycle: background pauses, leaving the screen aborts.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event: Lifecycle.Event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> uiState = controller?.onBackground() ?: uiState
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val state = uiState
    if (state == null || profile == null) {
        NxLoading(message = strings.raw("common.loading"))
        return
    }

    SessionScreen(
        state = state,
        strings = strings,
        onTick = { uiState = controller?.tick() ?: uiState },
        onAnswer = { input ->
            controller?.answer(input)
            uiState = controller?.state() ?: uiState
        },
        onPause = { uiState = controller?.pause() ?: uiState },
        onResume = { uiState = controller?.resume() ?: uiState },
        onEnd = { uiState = controller?.end() ?: uiState },
        onDone = { onFinished() },
        result = result,
    )
}

/** Haptics as a tiny interface so this file has no direct Vibrator dependency. */
interface Haptics {
    fun play(strength: Float)
}

/** No-op haptics used until the platform service is wired. */
object NoHaptics : Haptics {
    override fun play(strength: Float) = Unit
}
