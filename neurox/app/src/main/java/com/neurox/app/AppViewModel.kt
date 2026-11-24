package com.neurox.app

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neurox.data.*
import com.neurox.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class Screen { HOME, TRAINING, ANALYTICS, LIBRARY, PROFILE, SETTINGS, COLOR, BUILDER, RUN, RESULT }
data class AppState(
    val loading: Boolean = true,
    val profile: ProfileEntity? = null,
    val profiles: List<ProfileEntity> = emptyList(),
    val sessions: List<SessionEntity> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val themes: List<SavedThemeEntity> = emptyList(),
    val customExercises: List<CustomExerciseEntity> = emptyList(),
    val customExercisesLoaded: Boolean = false,
    val interrupted: SessionEntity? = null,
    val error: String? = null,
    val busy: Boolean = false,
)

class AppViewModel(private val repository: NeuroxRepository, private val saved: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(AppState())
    val state = _state.asStateFlow()
    val screen = saved.getStateFlow("screen", Screen.HOME.name)
    val selectedExercise = saved.getStateFlow("exercise", Exercise.FOCUS_LOCK.name)
    fun chooseExercise(exercise: Exercise) { saved["exercise"] = exercise.name; navigate(Screen.TRAINING) }
    val libraryArea = saved.getStateFlow("libraryArea", "ORIGINAL")
    fun selectLibraryArea(value: String) { require(value in listOf("ORIGINAL", "CUSTOM", "FAVORITES")); saved["libraryArea"] = value }
    val builderId = saved.getStateFlow<String?>("builderId", null)
    fun openBuilder(id: String? = null) { saved["builderId"] = id; navigate(Screen.BUILDER) }
    private val _training = MutableStateFlow<SessionView?>(null)
    val training = _training.asStateFlow()
    private val _result = MutableStateFlow<SessionSummary?>(null)
    val result = _result.asStateFlow()
    private var engine: TrainingEngine? = null
    private var session: SessionEntity? = null
    private data class SaveRequest(val session: SessionEntity, val checkpoint: Checkpoint)
    private val saves = Channel<SaveRequest>(64)
    private val mutex = Mutex()
    private var observer: Job? = null
    private var ticker: Job? = null
    private var failedAction: (suspend () -> Unit)? = null

    init { load() }
    fun load() {
        observer?.cancel()
        observer = viewModelScope.launch {
            try {
                repository.initialize()
                combine(repository.profiles, repository.activeProfileId) { profiles, id -> profiles to (profiles.firstOrNull { it.id == id } ?: profiles.first { it.guest }) }
                    .collectLatest { (profiles, selected) ->
                        val previousProfileId = _state.value.profile?.id
                        if (previousProfileId != selected.id) {
                            failedAction = null
                            _state.value = AppState(profile = selected, profiles = profiles)
                            engine = null; session = null; _training.value = null; _result.value = null
                            if (screen.value in listOf(Screen.RUN.name, Screen.RESULT.name) || (previousProfileId != null && screen.value == Screen.BUILDER.name)) navigate(Screen.HOME)
                        }
                        val interrupted = repository.activeSession(selected.id)
                        _state.update { it.copy(loading = false, profiles = profiles, profile = selected, interrupted = interrupted) }
                        coroutineScope {
                            launch { repository.sessions(selected.id).collect { values -> _state.update { it.copy(sessions = values, interrupted = values.firstOrNull { s -> s.status == "ACTIVE" }) } } }
                            launch { repository.favorites(selected.id).collect { values -> _state.update { it.copy(favorites = values.map { f -> f.exercise }.toSet()) } } }
                            launch { repository.customExercises(selected.id).collect { values -> _state.update { it.copy(customExercises = values, customExercisesLoaded = true) } } }
                            launch { repository.themes(selected.id).collect { values -> _state.update { it.copy(themes = values) } } }
                        }
                    }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { _state.update { it.copy(loading = false, error = "storage") } }
        }
        if (screen.value in listOf(Screen.RUN.name, Screen.RESULT.name)) navigate(Screen.HOME)
    }
    init {
        viewModelScope.launch {
            for (request in saves) {
                try {
                    repository.saveSession(request.session.id, request.session.profileId, request.checkpoint)
                    if (request.checkpoint.completed && session?.id == request.session.id) {
                        _result.value = Scoring.summarize(request.checkpoint)
                        if (screen.value == Screen.RUN.name) navigate(Screen.RESULT)
                    }
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) {
                    if (session?.id == request.session.id) {
                        engine?.pause(); _training.value = engine?.view()
                        _state.update { it.copy(error = "storage") }
                    }
                }
            }
        }
    }
    private fun startTicker() {
        ticker?.cancel()
        val expected = engine ?: return
        ticker = viewModelScope.launch {
            while (isActive && engine === expected) {
                val before = expected.view()
                if (before.phase !in listOf(Phase.WAITING, Phase.ACTIVE, Phase.QUESTION)) break
                expected.tick()
                val after = expected.view()
                if (after.attempts.size != before.attempts.size || after.phase != before.phase || after.phrase?.index != before.phrase?.index) publish(expected)
                else if (after.elapsedMs / 100 != (_training.value?.elapsedMs ?: 0) / 100) _training.value = after
                delay(if (expected.config.exercise.module == Module.REFLEX && after.phase in listOf(Phase.WAITING, Phase.ACTIVE)) 8 else 50)
            }
        }
    }
    private fun publish(e: TrainingEngine) {
        _training.value = e.view()
        val active = session ?: return
        if (saves.trySend(SaveRequest(active, e.checkpoint())).isFailure) {
            e.pause(); _training.value = e.view(); _state.update { it.copy(error = "storage") }
        }
    }
    fun navigate(destination: Screen) { saved["screen"] = destination.name }
    fun retrySave() {
        val retry = failedAction
        action {
            if (retry != null) retry() else engine?.let { persistAndPublish(it) }
            if (observer?.isActive != true) load()
        }
    }
    fun clearError() { _state.update { it.copy(error = null) } }
    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutex.withLock {
                _state.update { it.copy(busy = true) }
                try { block(); failedAction = null }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) {
                    failedAction = block
                    engine?.pause(); _training.value = engine?.view()
                    _state.update { it.copy(error = "operation") }
                } finally { _state.update { it.copy(busy = false) } }
            }
        }
    }
    private fun requireProfile() = requireNotNull(_state.value.profile)
    private suspend fun launchSession(config: TrainingConfig) {
        val profile = requireProfile()
        val created = repository.startSession(profile.id, config)
        session = created
        engine = TrainingEngine(config, MonotonicClock(SystemClock::elapsedRealtime)).also { it.resume() }
        _result.value = null; _training.value = engine!!.view()
        navigate(Screen.RUN)
        startTicker()
    }
    fun start(exercise: Exercise, rounds: Int, difficulty: Int, baseline: Boolean = false) = action {
        launchSession(TrainingConfig(exercise, Language.valueOf(requireProfile().language), difficulty, rounds, baseline = baseline))
    }
    fun saveBlueprint(blueprint: ExerciseBlueprint, id: String?) = action {
        if (id == null) repository.saveCustomExercise(requireProfile().id, blueprint)
        else repository.saveCustomExercise(requireProfile().id, blueprint, id)
        selectLibraryArea("CUSTOM")
        navigate(Screen.LIBRARY)
    }
    fun customFavorite(id: String, value: Boolean) = action { repository.setCustomFavorite(requireProfile().id, id, value) }
    fun duplicateBlueprint(id: String) = action { repository.duplicateCustomExercise(requireProfile().id, id) }
    fun deleteBlueprint(id: String) = action { repository.deleteCustomExercise(requireProfile().id, id) }
    fun startCustom(id: String) = action {
        val profile = requireProfile()
        val value = repository.customExercise(profile.id, id)
        launchSession(value.blueprint().configuration(Language.valueOf(profile.language), kotlin.random.Random.nextLong(), value.id))
    }
    fun testBlueprint(blueprint: ExerciseBlueprint, sourceId: String?) = action {
        require(blueprint.validate().isEmpty())
        launchSession(blueprint.configuration(Language.valueOf(requireProfile().language), kotlin.random.Random.nextLong(),
            sourceId ?: java.util.UUID.randomUUID().toString(), practice = true))
    }
    fun restore() = action {
        val found = repository.activeSession(requireProfile().id) ?: return@action
        val checkpoint = CheckpointCodec.decode(found.checkpoint)
        session = found
        engine = TrainingEngine(checkpoint.config, MonotonicClock(SystemClock::elapsedRealtime), checkpoint = checkpoint)
        _training.value = engine!!.view(); navigate(Screen.RUN)
    }
    fun discard() = action {
        val found = repository.activeSession(requireProfile().id) ?: return@action
        ticker?.cancel()
        if (session?.id == found.id) engine?.let { active ->
            active.pause()
            repository.saveSession(found.id, found.profileId, active.checkpoint())
        }
        // Queued checkpoints may have committed since activeSession(). Abort the
        // latest durable row transactionally; queued writes cannot reactivate it.
        repository.abortSession(found.id, found.profileId)
        engine = null; session = null; _training.value = null; navigate(Screen.HOME)
    }
    private suspend fun persistAndPublish(e: TrainingEngine) {
        val found = requireNotNull(session)
        repository.saveSession(found.id, found.profileId, e.checkpoint())
        _training.value = e.view()
        if (_training.value?.phase == Phase.COMPLETE) {
            _result.value = Scoring.summarize(e.checkpoint()); if (screen.value == Screen.RUN.name) navigate(Screen.RESULT)
        }
    }
    fun pause() { ticker?.cancel(); engine?.let { it.pause(); publish(it) } }
    fun resume() { engine?.let { it.resume(); publish(it); startTicker() } }
    fun tap(cell: Int? = null, revision: Long) { engine?.let { it.tap(cell, revision); publish(it) } }
    fun question(revision: Long) { engine?.let { it.showQuestion(revision); publish(it) } }
    fun answer(option: Int, revision: Long) { engine?.let { it.answer(option, revision); publish(it) } }
    fun type(text: String, revision: Long) { engine?.let { it.submitTyping(text, revision); publish(it) } }
    fun favorite(exercise: Exercise, value: Boolean) = action { repository.setFavorite(requireProfile().id, exercise, value) }
    fun updateProfile(value: ProfileEntity) = action { require(value.id == requireProfile().id); repository.updateProfile(value) }
    fun selectProfile(id: String) = action {
        engine?.let { it.pause(); persistAndPublish(it) }
        repository.selectProfile(id); navigate(Screen.HOME)
    }
    fun createProfile(name: String) {
        var createdId: String? = null
        action {
            val id = createdId ?: repository.createProfile(name).also { createdId = it }
            repository.selectProfile(id)
        }
    }
    fun deleteProfile(id: String) = action { repository.deleteProfile(id) }
    fun saveTheme(name: String, dark: Long, light: Long, id: String? = null) = action {
        val profileId = requireProfile().id
        if (id == null) repository.saveTheme(profileId, name, dark, light)
        else repository.saveTheme(profileId, name, dark, light, id)
    }
    fun deleteTheme(id: String) = action { repository.deleteTheme(requireProfile().id, id) }
    fun acceptRecommendation(exercise: Exercise, current: Int, recommendation: Recommendation, accepted: Boolean) = action {
        repository.recordAdaptation(requireProfile().id, exercise, current, recommendation, accepted)
    }
}
