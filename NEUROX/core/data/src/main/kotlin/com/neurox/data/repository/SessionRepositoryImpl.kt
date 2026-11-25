package com.neurox.data.repository

import com.neurox.common.result.DomainError
import com.neurox.common.result.Outcome
import com.neurox.data.db.CheckpointDao
import com.neurox.data.db.SessionDao
import com.neurox.data.map.Mappers
import com.neurox.domain.model.Module
import com.neurox.domain.model.SessionKind
import com.neurox.domain.model.SessionRecord
import com.neurox.domain.repository.SessionRepository
import com.neurox.domain.repository.Stream

/**
 * Session persistence.
 *
 * IDEMPOTENCE: saving a record twice with the same id replaces the row, so XP and
 * records cannot be duplicated by a retry. The record's id is generated once per
 * session and carried through the checkpoint.
 *
 * ISOLATION: every read takes profileId and filters in SQL.
 */
class SessionRepositoryImpl(
    private val dao: SessionDao,
    private val checkpointDao: CheckpointDao,
) : SessionRepository {

    override suspend fun save(record: SessionRecord): Outcome<SessionRecord> = try {
        dao.upsert(Mappers.sessionEntity(record))
        Outcome.Ok(record)
    } catch (e: Exception) {
        Outcome.Err(DomainError.StorageFailure)
    }

    /**
     * Reads one session by id.
     *
     * The profile is part of the key on purpose: a caller cannot fetch another
     * profile's session by guessing an id, so isolation does not depend on the UI.
     */
    override suspend fun getById(profileId: String, id: String): SessionRecord? =
        dao.byId(profileId, id)?.let(Mappers::session)

    override fun observeForProfile(profileId: String, module: Module?): Stream<List<SessionRecord>> =
        Stream { collector ->
            val flow = if (module == null) {
                dao.observeAll(profileId)
            } else {
                dao.observeByModule(profileId, module.id)
            }
            flow.collect { rows -> collector(rows.map(Mappers::session)) }
        }

    override fun observeSince(
        profileId: String,
        sinceMillis: Long,
        kind: SessionKind?,
    ): Stream<List<SessionRecord>> = Stream { collector ->
        val flow = if (kind == null) {
            dao.observeSince(profileId, sinceMillis)
        } else {
            dao.observeSinceByKind(profileId, sinceMillis, kind.name)
        }
        flow.collect { rows -> collector(rows.map(Mappers::session)) }
    }

    override suspend fun listRecent(profileId: String, limit: Int, kind: SessionKind?): List<SessionRecord> {
        val rows = if (kind == null) {
            dao.recent(profileId, limit)
        } else {
            dao.recentByKind(profileId, kind.name, limit)
        }
        return rows.map(Mappers::session)
    }

    override suspend fun listAll(profileId: String): List<SessionRecord> =
        dao.all(profileId).map(Mappers::session)

    override suspend fun countCompleted(profileId: String): Int = dao.completedCount(profileId)

    override suspend fun closeDanglingSessions(profileId: String, endedAtMillis: Long): Int =
        dao.closeDangling(profileId, endedAtMillis)

    override suspend fun saveCheckpoint(profileId: String, json: String) {
        val now = System.currentTimeMillis()
        val existing = checkpointDao.load(profileId)
        checkpointDao.save(
            com.neurox.data.db.CheckpointEntity(
                profileId = profileId,
                sessionId = existing?.sessionId ?: "checkpoint-$now",
                exerciseId = existing?.exerciseId ?: "unknown",
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now,
                payloadJson = json,
            ),
        )
    }

    override suspend fun loadCheckpoint(profileId: String): String? =
        checkpointDao.load(profileId)?.payloadJson

    override suspend fun clearCheckpoint(profileId: String) {
        checkpointDao.clear(profileId)
    }
}

/**
 * Queries used by Home and Analytics that need counts the generic repository does
 * not expose. They stay here so the domain interface stays small.
 */
class SessionQueries(
    private val dao: SessionDao,
) {
    suspend fun completedDayKeys(profileId: String): Set<Long> =
        dao.completedDayKeys(profileId).toSet()

    suspend fun highestDifficulty(profileId: String): Int =
        dao.highestDifficulty(profileId) ?: 1

    suspend fun latestBaseline(profileId: String): SessionRecord? =
        dao.latestBaseline(profileId)?.let(Mappers::session)

    suspend fun historyForExercise(profileId: String, exerciseId: String, limit: Int): List<SessionRecord> =
        dao.historyForExercise(profileId, exerciseId, limit).map(Mappers::session)
}
