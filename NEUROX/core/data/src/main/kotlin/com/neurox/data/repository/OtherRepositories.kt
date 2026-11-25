package com.neurox.data.repository

import com.neurox.common.json.JsonWriter
import com.neurox.common.result.DomainError
import com.neurox.common.result.Outcome
import com.neurox.data.db.AchievementDao
import com.neurox.data.db.ExerciseDao
import com.neurox.data.db.FavoriteEntity
import com.neurox.data.db.PlanDao
import com.neurox.data.db.SettingDao
import com.neurox.data.map.Mappers
import com.neurox.domain.model.AudioSettings
import com.neurox.domain.plan.PlanValidator
import com.neurox.domain.plan.TrainingPlan
import com.neurox.domain.repository.AchievementRepository
import com.neurox.domain.repository.AudioRepository
import com.neurox.domain.repository.CustomExercise
import com.neurox.domain.repository.ExerciseRepository
import com.neurox.domain.repository.PlanRepository
import com.neurox.domain.repository.SettingsRepository
import com.neurox.domain.repository.Stream

/**
 * Achievements. Granting is idempotent at the database level: the primary key is
 * (profileId, achievementId), so a duplicate grant inserts nothing and the reward
 * is paid once.
 */
class AchievementRepositoryImpl(
    private val dao: AchievementDao,
) : AchievementRepository {

    override fun observeEarnedIds(profileId: String): Stream<Set<String>> = Stream { collector ->
        dao.observeIds(profileId).collect { ids -> collector(ids.toSet()) }
    }

    override suspend fun earnedIds(profileId: String): Set<String> = dao.ids(profileId).toSet()

    override suspend fun grant(profileId: String, achievementId: String, earnedAtMillis: Long): Boolean =
        dao.insert(
            com.neurox.data.db.AchievementEntity(
                profileId = profileId,
                achievementId = achievementId,
                earnedAtMillis = earnedAtMillis,
            ),
        ) != -1L
}

/**
 * Settings stored as typed key/value rows.
 *
 * Audio settings are serialized into these keys, so a schema change to the audio
 * model does not require a database migration.
 */
class SettingsRepositoryImpl(
    private val dao: SettingDao,
) : SettingsRepository, AudioRepository {

    override fun observeSettings(profileId: String): Stream<Map<String, String>> = Stream { collector ->
        dao.observe(profileId).collect { rows -> collector(rows.associate { it.key to it.value }) }
    }

    override suspend fun get(profileId: String, key: String): String? = dao.get(profileId, key)

    override suspend fun put(profileId: String, key: String, value: String) {
        dao.put(Mappers.setting(profileId, key, value))
    }

    override suspend fun putAll(profileId: String, values: Map<String, String>) {
        dao.putAll(values.map { (key, value) -> Mappers.setting(profileId, key, value) })
    }

    override fun observe(profileId: String): Stream<AudioSettings> = Stream { collector ->
        dao.observe(profileId).collect { rows ->
            collector(AudioSettingsCodec.decode(rows.associate { it.key to it.value }))
        }
    }

    override suspend fun update(profileId: String, settings: AudioSettings) {
        dao.putAll(AudioSettingsCodec.encode(settings).map { (key, value) -> Mappers.setting(profileId, key, value) })
    }

    suspend fun allAudioKeys(profileId: String): Map<String, String> =
        AudioSettingsCodec.encode(AudioSettingsCodec.decode(dao.all(profileId).associate { it.key to it.value }))
}

/**
 * Audio settings <-> settings keys.
 *
 * STABLE KEYS: the key names are part of the persisted contract and never change.
 * A missing key falls back to the model default, so a new setting is additive.
 */
object AudioSettingsCodec {

    const val KEY_PREFIX = "audio."
    const val MUSIC_ENABLED = "audio.music.enabled"
    const val MUSIC_VOLUME = "audio.music.volume"
    const val UI_ENABLED = "audio.ui.enabled"
    const val UI_VOLUME = "audio.ui.volume"
    const val CUE_ENABLED = "audio.cue.enabled"
    const val CUE_VOLUME = "audio.cue.volume"
    const val SUCCESS_ENABLED = "audio.success.enabled"
    const val SUCCESS_VOLUME = "audio.success.volume"
    const val ERROR_ENABLED = "audio.error.enabled"
    const val ERROR_VOLUME = "audio.error.volume"
    const val HAPTICS_ENABLED = "audio.haptics.enabled"
    const val HAPTIC_INTENSITY = "audio.haptics.intensity"
    const val PACK = "audio.pack"
    const val FOCUS_MODE = "audio.focus_mode"
    const val SILENT_MODE = "audio.silent_mode"

    fun encode(settings: AudioSettings): Map<String, String> = mapOf(
        MUSIC_ENABLED to settings.musicEnabled.toString(),
        MUSIC_VOLUME to settings.musicVolume.toString(),
        UI_ENABLED to settings.interfaceSoundsEnabled.toString(),
        UI_VOLUME to settings.interfaceVolume.toString(),
        CUE_ENABLED to settings.trainingCuesEnabled.toString(),
        CUE_VOLUME to settings.trainingCueVolume.toString(),
        SUCCESS_ENABLED to settings.successEnabled.toString(),
        SUCCESS_VOLUME to settings.successVolume.toString(),
        ERROR_ENABLED to settings.errorEnabled.toString(),
        ERROR_VOLUME to settings.errorVolume.toString(),
        HAPTICS_ENABLED to settings.hapticsEnabled.toString(),
        HAPTIC_INTENSITY to settings.hapticIntensity.toString(),
        PACK to settings.soundPackId,
        FOCUS_MODE to settings.focusMode.toString(),
        SILENT_MODE to settings.silentMode.toString(),
    )

    fun decode(values: Map<String, String>): AudioSettings {
        val defaults = AudioSettings()
        fun bool(key: String, fallback: Boolean): Boolean =
            values[key]?.toBooleanStrictOrNull() ?: fallback
        fun float(key: String, fallback: Float): Float =
            values[key]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: fallback
        return AudioSettings(
            musicEnabled = bool(MUSIC_ENABLED, defaults.musicEnabled),
            musicVolume = float(MUSIC_VOLUME, defaults.musicVolume),
            interfaceSoundsEnabled = bool(UI_ENABLED, defaults.interfaceSoundsEnabled),
            interfaceVolume = float(UI_VOLUME, defaults.interfaceVolume),
            trainingCuesEnabled = bool(CUE_ENABLED, defaults.trainingCuesEnabled),
            trainingCueVolume = float(CUE_VOLUME, defaults.trainingCueVolume),
            successEnabled = bool(SUCCESS_ENABLED, defaults.successEnabled),
            successVolume = float(SUCCESS_VOLUME, defaults.successVolume),
            errorEnabled = bool(ERROR_ENABLED, defaults.errorEnabled),
            errorVolume = float(ERROR_VOLUME, defaults.errorVolume),
            hapticsEnabled = bool(HAPTICS_ENABLED, defaults.hapticsEnabled),
            hapticIntensity = float(HAPTIC_INTENSITY, defaults.hapticIntensity),
            soundPackId = values[PACK] ?: defaults.soundPackId,
            focusMode = bool(FOCUS_MODE, defaults.focusMode),
            silentMode = bool(SILENT_MODE, defaults.silentMode),
        )
    }

    /** Codes used by the export path. */
    fun toJson(settings: AudioSettings): String =
        JsonWriter.write(com.neurox.common.json.jsonObject {
            "musicEnabled" bool settings.musicEnabled
            "musicVolume" number settings.musicVolume.toDouble()
            "focusMode" bool settings.focusMode
            "silentMode" bool settings.silentMode
        })
}

/** Custom exercises and favorites. */
class ExerciseRepositoryImpl(
    private val dao: ExerciseDao,
    private val now: () -> Long = System::currentTimeMillis,
) : ExerciseRepository {

    override fun observeCustomExercises(profileId: String): Stream<List<CustomExercise>> = Stream { collector ->
        dao.observeCustom(profileId).collect { rows -> collector(rows.map(Mappers::exercise)) }
    }

    override suspend fun saveCustom(profileId: String, exercise: CustomExercise): Outcome<CustomExercise> {
        if (exercise.profileId != profileId) return Outcome.Err(DomainError.ProfileInactive)
        val name = exercise.name.trim()
        if (name.isEmpty()) return Outcome.Err(DomainError.ProfileNameBlank)
        if (name.length > MAX_EXERCISE_NAME) return Outcome.Err(DomainError.ProfileNameTooLong)
        if (!exercise.config.isValid()) return Outcome.Err(DomainError.InvalidConfiguration)
        if (exercise.difficulty !in 1..5) return Outcome.Err(DomainError.InvalidConfiguration)
        val saved = exercise.copy(name = name, updatedAtMillis = now())
        dao.upsertCustom(Mappers.exerciseEntity(saved))
        return Outcome.Ok(saved)
    }

    override suspend fun deleteCustom(profileId: String, exerciseId: String): Outcome<Unit> {
        dao.deleteCustom(profileId, exerciseId)
        return Outcome.Ok(Unit)
    }

    override fun observeFavorites(profileId: String): Stream<Set<String>> = Stream { collector ->
        dao.observeFavorites(profileId).collect { ids -> collector(ids.toSet()) }
    }

    override suspend fun toggleFavorite(profileId: String, exerciseId: String): Outcome<Boolean> {
        val existing = dao.favorite(profileId, exerciseId)
        return if (existing == null) {
            dao.addFavorite(FavoriteEntity(profileId, exerciseId, now()))
            Outcome.Ok(true)
        } else {
            dao.removeFavorite(profileId, exerciseId)
            Outcome.Ok(false)
        }
    }

    companion object {
        const val MAX_EXERCISE_NAME = 48
    }
}

/** Training plans: history plus one active plan per profile. */
class PlanRepositoryImpl(
    private val dao: PlanDao,
) : PlanRepository {

    override fun observeActivePlan(profileId: String): Stream<TrainingPlan?> = Stream { collector ->
        dao.observeActive(profileId).collect { row -> collector(row?.let(Mappers::plan)) }
    }

    override suspend fun save(profileId: String, plan: TrainingPlan): Outcome<Unit> {
        // Re-validate here so a plan can never bypass the domain rules through the
        // repository. A rejected plan never reaches the database.
        val validated = PlanValidator.validate(plan)
        if (validated is Outcome.Err) return Outcome.Err(validated.error)
        val entity = Mappers.planEntity(profileId, plan)
        if (plan.active) dao.replaceActive(entity) else dao.upsert(entity)
        return Outcome.Ok(Unit)
    }

    override suspend fun listHistory(profileId: String): List<TrainingPlan> =
        dao.history(profileId).map(Mappers::plan)
}
