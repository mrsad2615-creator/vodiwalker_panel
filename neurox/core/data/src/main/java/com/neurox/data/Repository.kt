package com.neurox.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.neurox.domain.*
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.neuroxPreferences by preferencesDataStore("neurox_application")

class NeuroxRepository(context: Context, private val database: NeuroxDatabase = NeuroxDatabase.create(context.applicationContext)) {
    private val applicationContext = context.applicationContext
    private val dao = database.dao()
    private val activeKey = stringPreferencesKey("active_profile")
    val activeProfileId = applicationContext.neuroxPreferences.data
        .map { it[activeKey] ?: "guest" }
    val profiles = dao.profiles()
    fun sessions(profileId: String) = dao.sessions(profileId)
    fun favorites(profileId: String) = dao.favorites(profileId)
    fun themes(profileId: String) = dao.themes(profileId)
    fun customExercises(profileId: String) = dao.customExercises(profileId)
    suspend fun saveCustomExercise(profileId: String, blueprint: ExerciseBlueprint, id: String = UUID.randomUUID().toString()): String = database.withTransaction {
        require(blueprint.validate().isEmpty()) { "Invalid exercise configuration" }
        require(UUID.fromString(id).toString() == id)
        requireNotNull(dao.profile(profileId))
        val existing = dao.customExercise(id)
        require(existing == null || existing.profileId == profileId) { "Exercise belongs to another profile" }
        val now = System.currentTimeMillis()
        dao.saveCustomExercise(CustomExerciseEntity(id, profileId, blueprint.name.trim(), blueprint.template.name,
            blueprint.module.name, blueprint.difficulty, blueprint.rounds, blueprint.responseLimitMs,
            blueprint.targetAccuracyPercent, blueprint.errorAllowance, blueprint.schemaVersion,
            existing?.createdAt ?: now, maxOf(existing?.createdAt ?: now, now)))
        id
    }
    suspend fun duplicateCustomExercise(profileId: String, id: String): String {
        val source = requireNotNull(dao.customExercise(id))
        require(source.profileId == profileId)
        return saveCustomExercise(profileId, source.blueprint().copy(name = source.name.take(53) + " · copy"))
    }
    suspend fun deleteCustomExercise(profileId: String, id: String) = database.withTransaction {
        dao.unfavorite(profileId, "custom:$id")
        dao.deleteCustomExercise(id, profileId)
    }
    suspend fun setCustomFavorite(profileId: String, id: String, value: Boolean) {
        customExercise(profileId, id)
        if (value) dao.favorite(FavoriteEntity(profileId, "custom:$id")) else dao.unfavorite(profileId, "custom:$id")
    }
    suspend fun customExercise(profileId: String, id: String): CustomExerciseEntity {
        val value = requireNotNull(dao.customExercise(id)) { "Exercise not found" }
        require(value.profileId == profileId) { "Exercise belongs to another profile" }
        return value
    }

    suspend fun initialize() { dao.insertProfile(ProfileEntity("guest", "Guest", guest = true)) }
    suspend fun selectProfile(id: String) {
        require(dao.profile(id) != null) { "Profile does not exist" }
        applicationContext.neuroxPreferences.edit { it[activeKey] = id }
    }
    suspend fun createProfile(name: String): String {
        val safe = name.trim()
        require(safe.length in 1..40 && safe.none { it.isISOControl() }) { "Use 1 to 40 printable characters" }
        val id = UUID.randomUUID().toString()
        dao.insertProfile(ProfileEntity(id, safe))
        return id
    }
    suspend fun deleteProfile(id: String) {
        require(id != "guest") { "The guest profile cannot be deleted" }
        selectProfile("guest")
        dao.deleteProfile(id)
    }
    suspend fun updateProfile(profile: ProfileEntity) {
        require(profile.language in listOf("FA", "EN") && profile.theme in listOf("DARK", "LIGHT", "SYSTEM"))
        require(profile.textScale in 0.85f..1.5f && profile.accent in 0xFF000000..0xFFFFFFFF && profile.lightAccent in 0xFF000000..0xFFFFFFFF)
        val existing = requireNotNull(dao.profile(profile.id))
        require(existing.guest == profile.guest)
        dao.updateProfile(profile)
    }
    suspend fun startSession(profileId: String, config: TrainingConfig): SessionEntity = database.withTransaction {
        requireNotNull(dao.profile(profileId))
        check(dao.activeSession(profileId) == null) { "Resume or discard the interrupted session first" }
        val checkpoint = Checkpoint(config, emptyList(), 0, false)
        SessionEntity(UUID.randomUUID().toString(), profileId, config.exercise.name, config.exercise.module.name,
            System.currentTimeMillis(), checkpoint = CheckpointCodec.encode(checkpoint), difficulty = config.difficulty,
            baseline = config.baseline).also { dao.insertSession(it) }
    }
    suspend fun activeSession(profileId: String) = dao.activeSession(profileId)
    suspend fun saveSession(id: String, profileId: String, checkpoint: Checkpoint, aborted: Boolean = false) {
        database.withTransaction {
            val old = requireNotNull(dao.session(id, profileId)) { "Session does not belong to this profile" }
            // Idempotent completion. Repeated callbacks never mint additional rewards.
            if (old.status != "ACTIVE") return@withTransaction
            require(old.exercise == checkpoint.config.exercise.name && old.baseline == checkpoint.config.baseline)
            val previous = CheckpointCodec.decode(old.checkpoint)
            if (!CheckpointProgress.accept(previous, checkpoint)) return@withTransaction
            val summary = Scoring.summarize(checkpoint)
            val status = if (aborted) "ABORTED" else if (checkpoint.completed) "COMPLETE" else "ACTIVE"
            dao.updateSession(old.copy(
                checkpoint = CheckpointCodec.encode(checkpoint), status = status,
                endedAt = if (status == "ACTIVE") null else System.currentTimeMillis(),
                valid = summary.valid && !aborted, durationMs = checkpoint.elapsedMs, accuracy = summary.accuracy,
                responseMs = summary.meanResponseMs, responseDeviationMs = summary.responseDeviationMs,
                wpm = summary.wpm, xp = if (aborted) 0 else summary.xp,
            ))
        }
    }
    /** Abort the latest durable row, not a possibly stale caller snapshot. */
    suspend fun abortSession(id: String, profileId: String) = database.withTransaction {
        val latest = requireNotNull(dao.session(id, profileId)) { "Session does not belong to this profile" }
        if (latest.status == "ACTIVE") dao.updateSession(latest.copy(
            status = "ABORTED", endedAt = System.currentTimeMillis(), valid = false, xp = 0,
        ))
    }
    suspend fun setFavorite(profileId: String, exercise: Exercise, value: Boolean) {
        if (value) dao.favorite(FavoriteEntity(profileId, exercise.name)) else dao.unfavorite(profileId, exercise.name)
    }
    suspend fun saveTheme(profileId: String, name: String, dark: Long, light: Long, id: String = UUID.randomUUID().toString()) {
        require(name.trim().length in 1..30 && name.none { it.isISOControl() })
        require(dark in 0xFF000000..0xFFFFFFFF && light in 0xFF000000..0xFFFFFFFF)
        database.withTransaction {
            val existing = dao.theme(id)
            require(existing == null || existing.profileId == profileId) { "Theme does not belong to this profile" }
            dao.saveTheme(SavedThemeEntity(id, profileId, name.trim(), dark, light))
        }
    }
    suspend fun deleteTheme(profileId: String, id: String) = dao.deleteTheme(id, profileId)
    suspend fun recordAdaptation(profileId: String, exercise: Exercise, current: Int, recommendation: Recommendation, accepted: Boolean) {
        dao.recordAdaptation(AdaptationEntity(UUID.randomUUID().toString(), profileId, exercise.name,
            System.currentTimeMillis(), current, recommendation.difficulty, recommendation.reason.name, accepted))
    }
}
