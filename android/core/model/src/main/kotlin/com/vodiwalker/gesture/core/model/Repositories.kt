package com.vodiwalker.gesture.core.model

/**
 * Domain repository contracts enabling strict Clean Architecture dependency inversion.
 * Presentation and Engine layers depend only on these abstractions; `:core:database` and
 * `:core:datastore` supply concrete Room/DataStore implementations via Hilt `@Binds`.
 */
interface GestureTemplateRepository {
    /**
     * Every registered template, including disabled ones, ordered by usage recency.
     */
    suspend fun getAllTemplates(): List<GestureTemplate>
    /**
     * Only enabled templates — the hot-path corpus for the recognition engine.
     */
    suspend fun getEnabledTemplates(): List<GestureTemplate>
    /**
     * Single template lookup used when replaying a saved trigger binding.
     */
    suspend fun getTemplateById(id: String): GestureTemplate?
    /**
     * Inserts or replaces a calibrated template (idempotent on [GestureTemplate.id]).
     */
    suspend fun upsertTemplate(template: GestureTemplate)
    /**
     * Deletes a template; dependent trigger rules cascade at the storage layer.
     */
    suspend fun deleteTemplate(id: String)
    /**
     * Increments the usage counter and stamps the last-used time for ranking.
     */
    suspend fun incrementUsageCount(id: String, usedAtMillis: Long)
}

/**
 * Read/write port for app-scoped trigger bindings (gesture → Smart-Action graph).
 */
interface TriggerRuleRepository {
    /**
     * All enabled rules, ranked by specificity inputs (priority).
     */
    suspend fun getEnabledRules(): List<AppTriggerRule>
    /**
     * Rules bound to one gesture template, used by the binding editor.
     */
    suspend fun getRulesForGesture(gestureId: String): List<AppTriggerRule>
    /**
     * Inserts or replaces a trigger rule.
     */
    suspend fun upsertRule(rule: AppTriggerRule)
    /**
     * Removes a trigger rule and its cooldown bookkeeping.
     */
    suspend fun deleteRule(id: String)
}

/**
 * Read/write port for validated Smart-Action DAGs.
 */
interface ActionGraphRepository {
    /**
     * Loads a graph for execution; returns null when the id is unknown.
     */
    suspend fun getGraphById(id: String): SmartActionGraph?
    /**
     * All graphs for the trigger matrix overview.
     */
    suspend fun getAllGraphs(): List<SmartActionGraph>
    /**
     * Persists a graph that already passed [ActionGraphValidator].
     */
    suspend fun upsertGraph(graph: SmartActionGraph)
    /**
     * Deletes a graph and cascades its trigger rules.
     */
    suspend fun deleteGraph(id: String)
}

/**
 * Append-only, zero-PII execution journal with bounded retention.
 */
interface ExecutionTelemetryRepository {
    /**
     * Records one graph run (status, node count, duration) with no screen content.
     */
    suspend fun recordExecution(report: ExecutionReport, timestampMillis: Long)
    /**
     * Drops rows older than [cutoffMillis] and keeps at most [maxRetainedRows] entries.
     */
    suspend fun pruneOlderThan(cutoffMillis: Long, maxRetainedRows: Int = 500)
}
