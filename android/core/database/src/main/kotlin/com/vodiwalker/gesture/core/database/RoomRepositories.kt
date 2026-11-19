package com.vodiwalker.gesture.core.database

import com.vodiwalker.gesture.core.database.EntityMappers.toDomain
import com.vodiwalker.gesture.core.database.EntityMappers.toEntity
import com.vodiwalker.gesture.core.model.ActionGraphRepository
import com.vodiwalker.gesture.core.model.GestureTemplate
import com.vodiwalker.gesture.core.model.GestureTemplateRepository
import com.vodiwalker.gesture.core.model.SmartActionGraph
import com.vodiwalker.gesture.core.model.TriggerRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [GestureTemplateRepository] offering both suspend snapshots (used by the recognition
 * hot path, which must never await an unbounded Flow) and reactive [Flow] observation for Compose.
 *
 * All work executes on [Dispatchers.IO] so SQLite page reads never block the accessibility
 * service main thread.
 */
@Singleton
class RoomGestureTemplateRepository @Inject constructor(
    private val dao: GestureTemplateDao,
) : GestureTemplateRepository {

    val observableTemplates: Flow<List<GestureTemplate>> =
        dao.observeAllTemplates().map { entities -> entities.map { it.toDomain() } }

    val observableEnabledTemplates: Flow<List<GestureTemplate>> =
        dao.observeEnabledTemplates().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAllTemplates(): List<GestureTemplate> = withContext(Dispatchers.IO) {
        dao.getAllTemplates().map { it.toDomain() }
    }

    override suspend fun getEnabledTemplates(): List<GestureTemplate> = withContext(Dispatchers.IO) {
        dao.getEnabledTemplates().map { it.toDomain() }
    }

    override suspend fun getTemplateById(id: String): GestureTemplate? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun upsertTemplate(template: GestureTemplate) {
        withContext(Dispatchers.IO) { dao.upsert(template.toEntity()) }
    }

    override suspend fun deleteTemplate(id: String) {
        withContext(Dispatchers.IO) { dao.deleteById(id) }
    }

    override suspend fun incrementUsageCount(id: String, usedAtMillis: Long) {
        withContext(Dispatchers.IO) {
            require(usedAtMillis >= 0L) { "usedAtMillis must be non-negative" }
            dao.incrementUsage(id)
        }
    }
}

/**
 * Room-backed [TriggerRuleRepository] resolving gesture → app scope bindings.
 */
@Singleton
class RoomTriggerRuleRepository @Inject constructor(
    private val dao: TriggerAndGraphDao,
) : TriggerRuleRepository {

    override suspend fun getEnabledRules() = withContext(Dispatchers.IO) {
        dao.getEnabledRules().map { it.toDomain() }
    }

    override suspend fun getRulesForGesture(gestureId: String) = withContext(Dispatchers.IO) {
        dao.getRulesForGesture(gestureId).map { it.toDomain() }
    }

    override suspend fun upsertRule(rule: com.vodiwalker.gesture.core.model.AppTriggerRule) {
        withContext(Dispatchers.IO) { dao.upsertRule(rule.toEntity()) }
    }

    override suspend fun deleteRule(id: String) {
        withContext(Dispatchers.IO) { dao.deleteRule(id) }
    }
}

/**
 * Room-backed [ActionGraphRepository] persisting validated Smart-Action DAGs.
 */
@Singleton
class RoomActionGraphRepository @Inject constructor(
    private val dao: TriggerAndGraphDao,
) : ActionGraphRepository {

    override suspend fun getGraphById(id: String): SmartActionGraph? = withContext(Dispatchers.IO) {
        dao.getGraphById(id)?.toDomain()
    }

    override suspend fun getAllGraphs(): List<SmartActionGraph> = withContext(Dispatchers.IO) {
        dao.getAllGraphs().map { it.toDomain() }
    }

    override suspend fun upsertGraph(graph: SmartActionGraph) {
        withContext(Dispatchers.IO) { dao.upsertGraph(graph.toEntity()) }
    }

    override suspend fun deleteGraph(id: String) {
        withContext(Dispatchers.IO) { dao.deleteGraph(id) }
    }
}
