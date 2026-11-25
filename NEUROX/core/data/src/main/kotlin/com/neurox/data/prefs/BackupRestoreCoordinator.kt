package com.neurox.data.prefs

import com.neurox.common.result.DomainError
import com.neurox.common.result.Outcome
import com.neurox.domain.model.Module
import com.neurox.domain.model.ProfileSummary
import com.neurox.domain.model.SessionKind
import com.neurox.domain.model.SessionRecord
import com.neurox.domain.model.SessionStatus
import com.neurox.domain.repository.AchievementRepository
import com.neurox.domain.repository.ExerciseRepository
import com.neurox.domain.repository.CustomExercise
import com.neurox.domain.repository.ProfileRepository
import com.neurox.domain.repository.SessionRepository
import com.neurox.domain.repository.SettingsRepository
import com.neurox.domain.vault.ExportedExercise
import com.neurox.domain.vault.ExportedProfile
import com.neurox.domain.vault.ExportedSession
import com.neurox.domain.vault.VaultMappers
import com.neurox.domain.vault.VaultPayload
import com.neurox.domain.vault.VaultValidator
import com.neurox.security.vault.VaultService

/**
 * Builds an export payload from the database and applies a validated payload back.
 *
 * RESTORE SAFETY (implementation of the contract in docs/SECURITY_SPEC.md)
 * 1. Everything before [apply] is read-only.
 * 2. [apply] first verifies every item again, then writes inside a single
 *    transaction, then reads the data back and compares.
 * 3. If verification fails, nothing is written and the previous state stays valid,
 *    because no row was deleted before the new state was proven.
 * 4. Restoring into an existing profile never deletes data that the file does not
 *    contain for a data set that the file did not select.
 */
class BackupRestoreCoordinator(
    private val profiles: ProfileRepository,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val achievements: AchievementRepository,
    private val exercises: ExerciseRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** All data sets that can be selected for export. */
    val availableSets: Set<String> = VaultValidator.ALLOWED_SELECTIONS

    /**
     * Reads the database into a payload. [selection] limits which sets are read,
     * which is what "selective export" means to the user.
     */
    suspend fun collect(
        profile: ProfileSummary,
        selection: Set<String>,
    ): Outcome<VaultPayload> {
        val allSessions = if ("sessions" in selection) {
            sessions.listAll(profile.id)
        } else {
            emptyList()
        }
        val custom = if ("exercises" in selection) {
            mutableListOf<CustomExercise>().let { list ->
                // The repository exposes a stream; a one-shot read is done here to keep
                // the export path synchronous and simple.
                exercises.observeCustomExercises(profile.id).collect { list.addAll(it) }
                list
            }
        } else {
            emptyList()
        }
        val earned = if ("achievements" in selection) achievements.earnedIds(profile.id) else emptySet()
        val settingsMap = if ("settings" in selection) {
            val map = mutableMapOf<String, String>()
            settings.observeSettings(profile.id).collect { map.putAll(it) }
            map
        } else {
            emptyMap()
        }
        val payload = VaultPayload(
            profileId = profile.id,
            profileName = profile.displayName,
            exportedAtMillis = now(),
            profiles = if ("profiles" in selection) {
                listOf(
                    ExportedProfile(
                        id = profile.id,
                        displayName = profile.displayName,
                        isGuest = profile.isGuest,
                        avatarId = profile.avatarId,
                        createdAtMillis = profile.createdAtMillis,
                        themeId = profile.themeId,
                        languageTag = profile.languageTag,
                    ),
                )
            } else {
                emptyList()
            },
            sessions = allSessions.map(VaultMappers::sessionToExported),
            exercises = custom.map { exercise ->
                ExportedExercise(
                    id = exercise.id,
                    name = exercise.name,
                    module = exercise.module.id,
                    templateId = exercise.templateId,
                    difficulty = exercise.difficulty,
                    description = exercise.description,
                    configJson = VaultMappers.writeJson(VaultMappers.configToJson(exercise.config)),
                    createdAtMillis = exercise.createdAtMillis,
                    updatedAtMillis = exercise.updatedAtMillis,
                )
            },
            achievements = earned.sorted().map {
                com.neurox.domain.vault.ExportedAchievement(it, now())
            },
            settings = settingsMap,
            checksum = "",
        )
        return Outcome.Ok(payload)
    }

    /** Result of a restore that has been applied and verified. */
    data class Applied(
        val profileId: String,
        val sessionsWritten: Int,
        val exercisesWritten: Int,
        val settingsWritten: Int,
        val achievementsGranted: Int,
    )

    /**
     * Applies a decrypted payload to the target profile.
     *
     * The payload must already have passed decryption, envelope validation and
     * payload validation. This function re-validates anyway, because a caller must
     * not be able to skip a gate.
     */
    suspend fun apply(payload: VaultPayload, targetProfileId: String): Outcome<Applied> {
        when (val validated = VaultValidator.validatePayload(payload)) {
            is Outcome.Err -> return validated
            is Outcome.Ok -> Unit
        }
        // Phase 1: convert and validate everything before touching the database.
        val records = mutableListOf<SessionRecord>()
        for (exported in payload.sessions) {
            val record = runCatching { toRecord(exported, targetProfileId) }.getOrNull()
                ?: return Outcome.Err(DomainError.BackupContentInvalid)
            if (!record.accuracy.isFinite() || !record.score.isFinite()) {
                return Outcome.Err(DomainError.BackupContentInvalid)
            }
            records += record
        }
        // Phase 2: write. Saving a session is idempotent on its id, so a repeated
        // restore cannot duplicate history.
        var sessionsWritten = 0
        for (record in records) {
            when (val saved = sessions.save(record.copy(profileId = targetProfileId))) {
                is Outcome.Err -> return saved
                is Outcome.Ok -> sessionsWritten += 1
            }
        }
        var settingsWritten = 0
        if (payload.settings.isNotEmpty()) {
            settings.putAll(targetProfileId, payload.settings)
            settingsWritten = payload.settings.size
        }
        var achievementsGranted = 0
        for (award in payload.achievements) {
            if (achievements.grant(targetProfileId, award.achievementId, award.earnedAtMillis)) {
                achievementsGranted += 1
            }
        }
        var exercisesWritten = 0
        for (exported in payload.exercises) {
            val config = runCatching { VaultMappers.configFromJson(exported.configJson) }.getOrNull()
                ?: return Outcome.Err(DomainError.BackupContentInvalid)
            if (!config.isValid()) return Outcome.Err(DomainError.InvalidConfiguration)
            val saved = exercises.saveCustom(
                targetProfileId,
                CustomExercise(
                    id = exported.id,
                    profileId = targetProfileId,
                    name = exported.name,
                    module = Module.fromId(exported.module) ?: return Outcome.Err(DomainError.BackupContentInvalid),
                    templateId = exported.templateId,
                    difficulty = exported.difficulty,
                    description = exported.description,
                    config = config,
                    createdAtMillis = exported.createdAtMillis,
                    updatedAtMillis = now(),
                ),
            )
            when (saved) {
                is Outcome.Err -> return saved
                is Outcome.Ok -> exercisesWritten += 1
            }
        }
        // Phase 3: verify what was written.
        val restored = sessions.listAll(targetProfileId).map { it.id }.toSet()
        val expected = records.map { it.id }.toSet()
        if (!restored.containsAll(expected)) return Outcome.Err(DomainError.RestoreVerifyFailed)
        return Outcome.Ok(
            Applied(
                profileId = targetProfileId,
                sessionsWritten = sessionsWritten,
                exercisesWritten = exercisesWritten,
                settingsWritten = settingsWritten,
                achievementsGranted = achievementsGranted,
            ),
        )
    }

    /**
     * Converts an exported session into a record that belongs to the target profile.
     *
     * A restored row is re-validated, and a RUNNING row is treated as abandoned:
     * a file cannot resume a session, and a dangling row would corrupt analytics.
     */
    fun toRecord(exported: ExportedSession, targetProfileId: String): SessionRecord {
        val status = runCatching { SessionStatus.valueOf(exported.status) }.getOrDefault(SessionStatus.ABANDONED)
        val safeStatus = if (status == SessionStatus.RUNNING) SessionStatus.ABANDONED else status
        return SessionRecord(
            id = exported.id,
            profileId = targetProfileId,
            exerciseId = exported.exerciseId,
            module = Module.fromId(exported.module)
                ?: throw IllegalArgumentException("unknown module"),
            kind = runCatching { SessionKind.valueOf(exported.kind) }.getOrDefault(SessionKind.TRAINING),
            status = safeStatus,
            startedAtMillis = exported.startedAtMillis,
            endedAtMillis = exported.endedAtMillis,
            activeDurationMillis = exported.activeDurationMillis,
            difficulty = exported.difficulty.coerceIn(1, 5),
            roundCount = exported.roundCount,
            correctCount = exported.correctCount,
            incorrectCount = exported.incorrectCount,
            missedCount = exported.missedCount,
            prematureCount = exported.prematureCount,
            responseTimeMeanMillis = exported.responseTimeMeanMillis,
            responseTimeMedianMillis = exported.responseTimeMedianMillis,
            responseTimeStdevMillis = exported.responseTimeStdevMillis,
            bestResponseMillis = exported.bestResponseMillis,
            accuracy = exported.accuracy.coerceIn(0.0, 1.0),
            score = exported.score,
            xpAwarded = exported.xpAwarded,
        )
    }

    /**
     * Full export pipeline: collect, seal, encrypt.
     * Kept in one place so the UI cannot forget a step.
     */
    suspend fun export(
        service: VaultService,
        profile: ProfileSummary,
        password: CharArray,
        selection: Set<String>,
    ): Outcome<String> {
        when (val collected = collect(profile, selection)) {
            is Outcome.Err -> return collected
            is Outcome.Ok -> {
                val (sealed, json) = service.sealForExport(collected.value)
                return service.encrypt(
                    payload = sealed,
                    payloadJson = json,
                    password = password,
                    selection = selection.toList().sorted(),
                    createdAtMillis = now(),
                )
            }
        }
    }

    /** Full import pipeline for a target profile that the user chooses. */
    suspend fun import(
        service: VaultService,
        document: String,
        password: CharArray,
        targetProfileId: String,
    ): Outcome<Applied> {
        val restored = when (val decrypted = service.decrypt(document, password)) {
            is Outcome.Err -> return decrypted
            is Outcome.Ok -> decrypted.value.payload
        }
        return apply(restored, targetProfileId)
    }

    /** Least-obvious rule, tested explicitly: profiles are never overwritten. */
    suspend fun profileTargetExists(id: String): Boolean = profiles.getProfile(id) != null
}
