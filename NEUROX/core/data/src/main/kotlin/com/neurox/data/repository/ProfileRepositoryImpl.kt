package com.neurox.data.repository

import com.neurox.common.result.DomainError
import com.neurox.common.result.Outcome
import com.neurox.data.db.ProfileDao
import com.neurox.data.db.ProfileEntity
import com.neurox.data.map.Mappers
import com.neurox.domain.model.ProfileSummary
import com.neurox.domain.repository.ProfileRepository
import com.neurox.domain.repository.Stream
import com.neurox.security.pin.PinHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Profile repository.
 *
 * ISOLATION AND SAFETY
 * - Profile count is capped by [MAX_PROFILES], so the database stays small and the
 *   switcher stays usable.
 * - Deleting the last profile is refused; guest mode exists for a temporary user.
 * - Names are trimmed, length-limited and rejected when blank.
 * - A PIN is stored only as a salted hash. A wrong PIN fails switchProfile, and the
 *   active profile does not change.
 */
class ProfileRepositoryImpl(
    private val dao: ProfileDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : ProfileRepository {

    override fun observeProfiles(): Stream<List<ProfileSummary>> =
        Stream { collector ->
            dao.observeAll().collect { rows -> collector(rows.map(Mappers::profile)) }
        }

    /**
     * The concrete Flow is exposed for Android callers that need the full Flow API.
     * Both views read the same query, so they cannot disagree.
     */
    fun profilesFlow(): Flow<List<ProfileSummary>> =
        dao.observeAll().map { rows -> rows.map(Mappers::profile) }

    override fun observeActiveProfile(): Stream<ProfileSummary?> =
        Stream { collector ->
            dao.observeActive().collect { row -> collector(row?.let(Mappers::profile)) }
        }

    fun activeProfileFlow(): Flow<ProfileSummary?> =
        dao.observeActive().map { row -> row?.let(Mappers::profile) }

    override suspend fun getActiveProfile(): ProfileSummary? = dao.active()?.let(Mappers::profile)

    override suspend fun getProfile(id: String): ProfileSummary? = dao.byId(id)?.let(Mappers::profile)

    override suspend fun createProfile(displayName: String, isGuest: Boolean): Outcome<ProfileSummary> {
        val name = displayName.trim()
        if (name.isEmpty()) return Outcome.Err(DomainError.ProfileNameBlank)
        if (name.length > MAX_NAME_LENGTH) return Outcome.Err(DomainError.ProfileNameTooLong)
        if (dao.count() >= MAX_PROFILES) return Outcome.Err(DomainError.ProfileLimitReached)
        val id = idGenerator()
        val created = now()
        val entity = ProfileEntity(
            id = id,
            displayName = name,
            isGuest = isGuest,
            avatarId = 0,
            createdAtMillis = created,
            themeId = DEFAULT_THEME_ID,
            languageTag = DEFAULT_LANGUAGE,
            pinHash = null,
            active = true,
            lastActiveAtMillis = created,
        )
        // Exactly one profile is active after creation.
        dao.clearActive()
        dao.insert(entity)
        dao.setActive(id, created)
        return Outcome.Ok(Mappers.profile(entity))
    }

    override suspend fun renameProfile(id: String, newName: String): Outcome<ProfileSummary> {
        val existing = dao.byId(id) ?: return Outcome.Err(DomainError.ProfileNotFound)
        val name = newName.trim()
        if (name.isEmpty()) return Outcome.Err(DomainError.ProfileNameBlank)
        if (name.length > MAX_NAME_LENGTH) return Outcome.Err(DomainError.ProfileNameTooLong)
        dao.updateName(id, name)
        return Outcome.Ok(Mappers.profile(existing.copy(displayName = name)))
    }

    override suspend fun updateAvatar(id: String, avatarId: Int): Outcome<ProfileSummary> {
        val existing = dao.byId(id) ?: return Outcome.Err(DomainError.ProfileNotFound)
        val safeAvatar = avatarId.coerceIn(0, MAX_AVATAR_ID)
        dao.updateAvatar(id, safeAvatar)
        return Outcome.Ok(Mappers.profile(existing.copy(avatarId = safeAvatar)))
    }

    override suspend fun updateSettingsPatch(
        id: String,
        themeId: String?,
        languageTag: String?,
    ): Outcome<ProfileSummary> {
        val existing = dao.byId(id) ?: return Outcome.Err(DomainError.ProfileNotFound)
        if (languageTag != null && languageTag !in SUPPORTED_LANGUAGES) {
            return Outcome.Err(DomainError.ConfigurationField("language"))
        }
        themeId?.let { dao.updateTheme(id, it) }
        languageTag?.let { dao.updateLanguage(id, it) }
        return Outcome.Ok(
            Mappers.profile(
                existing.copy(
                    themeId = themeId ?: existing.themeId,
                    languageTag = languageTag ?: existing.languageTag,
                ),
            ),
        )
    }

    override suspend fun switchProfile(id: String, pin: String?): Outcome<ProfileSummary> {
        val target = dao.byId(id) ?: return Outcome.Err(DomainError.ProfileNotFound)
        if (target.pinHash != null) {
            if (pin == null) return Outcome.Err(DomainError.PinRequired)
            val stored = target.pinHash
            if (!PinHasher.verify(pin, stored)) return Outcome.Err(DomainError.PinInvalid)
        }
        dao.switchActive(id, now())
        return Outcome.Ok(Mappers.profile(target.copy(active = true)))
    }

    override suspend fun setPin(id: String, pin: String?): Outcome<Unit> {
        val existing = dao.byId(id) ?: return Outcome.Err(DomainError.ProfileNotFound)
        if (pin == null) {
            dao.updatePin(existing.id, null)
            return Outcome.Ok(Unit)
        }
        if (!PinHasher.isValidPin(pin)) return Outcome.Err(DomainError.PinInvalid)
        dao.updatePin(existing.id, PinHasher.hash(pin))
        return Outcome.Ok(Unit)
    }

    override suspend fun verifyPin(id: String, pin: String): Boolean {
        val stored = dao.byId(id)?.pinHash ?: return false
        return PinHasher.verify(pin, stored)
    }

    override suspend fun deleteProfile(id: String): Outcome<Unit> {
        val existing = dao.byId(id) ?: return Outcome.Err(DomainError.ProfileNotFound)
        if (dao.count() <= 1) return Outcome.Err(DomainError.ProfileLimitReached)
        val wasActive = existing.active
        // Cascade deletes sessions, achievements, favorites, settings, plans and the
        // checkpoint, so no orphan row can survive.
        dao.delete(id)
        if (wasActive) {
            // Always leave exactly one active profile, otherwise the app opens with
            // no identity and every query returns nothing.
            val successor = dao.firstByCreation()
            if (successor != null) dao.setActive(successor.id, now())
        }
        return Outcome.Ok(Unit)
    }

    override suspend fun countProfiles(): Int = dao.count()

    companion object {
        const val MAX_PROFILES = 8
        const val MAX_NAME_LENGTH = 32
        const val MAX_AVATAR_ID = 11
        const val DEFAULT_THEME_ID = "neurox.obsidian"
        const val DEFAULT_LANGUAGE = "fa"
        val SUPPORTED_LANGUAGES = setOf("fa", "en")
    }
}
