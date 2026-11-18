package com.vodiwalker.gesture.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Room entity storing calibrated gesture templates with compact binary/JSON point payloads and
 * pre-computed kinematic feature vectors for instant O(1) startup hydration.
 */
@Entity(
    tableName = "gesture_templates",
    indices = [
        Index(value = ["is_enabled"]),
        Index(value = ["category"]),
    ],
)
data class GestureTemplateEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "points_json") val pointsJson: String,
    @ColumnInfo(name = "features_json") val featuresJson: String,
    @ColumnInfo(name = "orientation_sensitive") val orientationSensitive: Boolean,
    @ColumnInfo(name = "min_confidence") val minConfidence: Float,
    @ColumnInfo(name = "haptic_pattern_id") val hapticPatternId: String,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean,
    @ColumnInfo(name = "sample_count") val sampleCount: Int,
    @ColumnInfo(name = "usage_count") val usageCount: Long,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
)

/**
 * Room entity representing a validated Smart-Action Directed Acyclic Graph.
 */
@Entity(tableName = "action_graphs")
data class ActionGraphEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "entry_node_id") val entryNodeId: String,
    @ColumnInfo(name = "nodes_json") val nodesJson: String,
    @ColumnInfo(name = "max_execution_time_millis") val maxExecutionTimeMillis: Long,
)

/**
 * App-scoped or global binding linking a gesture template to a Smart-Action graph with cascading
 * referential integrity and composite lookup indexes for sub-millisecond foreground dispatch.
 */
@Entity(
    tableName = "app_trigger_rules",
    foreignKeys = [
        ForeignKey(
            entity = GestureTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["gesture_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ActionGraphEntity::class,
            parentColumns = ["id"],
            childColumns = ["action_graph_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["gesture_id", "is_enabled"]),
        Index(value = ["action_graph_id"]),
        Index(value = ["scope_type", "target_package", "is_enabled"]),
    ],
)
data class AppTriggerRuleEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "gesture_id") val gestureId: String,
    @ColumnInfo(name = "action_graph_id") val actionGraphId: String,
    @ColumnInfo(name = "scope_type") val scopeType: String,
    @ColumnInfo(name = "target_package") val targetPackage: String?,
    @ColumnInfo(name = "target_activity") val targetActivity: String?,
    @ColumnInfo(name = "target_category") val targetCategory: String?,
    @ColumnInfo(name = "priority") val priority: Int,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean,
    @ColumnInfo(name = "cooldown_millis") val cooldownMillis: Long,
    @ColumnInfo(name = "haptic_override_id") val hapticOverrideId: String?,
)

/**
 * Privacy-safe execution telemetry entry. Never stores screen content, input text, or node labels.
 */
@Entity(
    tableName = "execution_logs",
    indices = [Index(value = ["timestamp_millis"])],
)
data class ExecutionLogEntity(
    @androidx.room.PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "log_id") val logId: Long = 0L,
    @ColumnInfo(name = "graph_id") val graphId: String,
    @ColumnInfo(name = "trigger_rule_id") val triggerRuleId: String?,
    @ColumnInfo(name = "overall_success") val overallSuccess: Boolean,
    @ColumnInfo(name = "node_count") val nodeCount: Int,
    @ColumnInfo(name = "duration_micros") val durationMicros: Long,
    @ColumnInfo(name = "timestamp_millis") val timestampMillis: Long,
)

/**
 * Local-only serialized weights of [com.vodiwalker.gesture.engine.prediction.ContextualNeuralModel].
 */
@Entity(tableName = "prediction_model_state")
data class PredictionModelEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "model_id") val modelId: String = SINGLETON_ID,
    @ColumnInfo(name = "weights_blob", typeAffinity = ColumnInfo.BLOB) val weightsBlob: ByteArray,
    @ColumnInfo(name = "bigram_json") val bigramJson: String,
    @ColumnInfo(name = "gesture_ids_json") val gestureIdsJson: String,
    @ColumnInfo(name = "total_updates") val totalUpdates: Long,
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long,
) {
    companion object {
        const val SINGLETON_ID: String = "contextual_mlp_v1"
    }

    override fun equals(other: Any?): Boolean =
        other is PredictionModelEntity &&
            modelId == other.modelId &&
            totalUpdates == other.totalUpdates &&
            weightsBlob.contentEquals(other.weightsBlob)

    override fun hashCode(): Int = 31 * modelId.hashCode() + weightsBlob.contentHashCode()
}

/**
 * CRUD + reactive observation for calibrated gesture templates.
 */
@Dao
interface GestureTemplateDao {
    /**
     * Reactive list of all templates ordered by usage then recency.
     */
    @Query("SELECT * FROM gesture_templates ORDER BY usage_count DESC, created_at_millis DESC")
    fun observeAllTemplates(): Flow<List<GestureTemplateEntity>>

    /**
     * Reactive list of enabled templates only.
     */
    @Query("SELECT * FROM gesture_templates WHERE is_enabled = 1")
    fun observeEnabledTemplates(): Flow<List<GestureTemplateEntity>>

    /**
     * One-shot snapshot of every template (recognition hot path).
     */
    @Query("SELECT * FROM gesture_templates ORDER BY created_at_millis DESC")
    suspend fun getAllTemplates(): List<GestureTemplateEntity>

    /**
     * One-shot snapshot of enabled templates.
     */
    @Query("SELECT * FROM gesture_templates WHERE is_enabled = 1")
    suspend fun getEnabledTemplates(): List<GestureTemplateEntity>

    /**
     * Single template by primary key.
     */
    @Query("SELECT * FROM gesture_templates WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): GestureTemplateEntity?

    /**
     * Insert-or-replace a template row.
     */
    @Upsert
    suspend fun upsert(entity: GestureTemplateEntity)

    /**
     * Deletes a template row.
     */
    @Query("DELETE FROM gesture_templates WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Atomically bumps the usage counter.
     */
    @Query("UPDATE gesture_templates SET usage_count = usage_count + 1 WHERE id = :id")
    suspend fun incrementUsage(id: String)
}

/**
 * Trigger-rule and Smart-Action graph persistence.
 */
@Dao
interface TriggerAndGraphDao {
    /**
     * Reactive enabled rules ordered by priority.
     */
    @Query("SELECT * FROM app_trigger_rules WHERE is_enabled = 1 ORDER BY priority DESC")
    fun observeEnabledRules(): Flow<List<AppTriggerRuleEntity>>

    /**
     * Reactive list of every rule.
     */
    @Query("SELECT * FROM app_trigger_rules ORDER BY priority DESC")
    fun observeAllRules(): Flow<List<AppTriggerRuleEntity>>

    /**
     * One-shot enabled rule snapshot.
     */
    @Query("SELECT * FROM app_trigger_rules WHERE is_enabled = 1")
    suspend fun getEnabledRules(): List<AppTriggerRuleEntity>

    /**
     * Rules bound to a single gesture.
     */
    @Query("SELECT * FROM app_trigger_rules WHERE gesture_id = :gestureId")
    suspend fun getRulesForGesture(gestureId: String): List<AppTriggerRuleEntity>

    /**
     * Insert-or-replace a trigger rule.
     */
    @Upsert
    suspend fun upsertRule(entity: AppTriggerRuleEntity)

    /**
     * Deletes a trigger rule.
     */
    @Query("DELETE FROM app_trigger_rules WHERE id = :id")
    suspend fun deleteRule(id: String)

    /**
     * Single graph by primary key.
     */
    @Query("SELECT * FROM action_graphs WHERE id = :id LIMIT 1")
    suspend fun getGraphById(id: String): ActionGraphEntity?

    /**
     * One-shot snapshot of all graphs.
     */
    @Query("SELECT * FROM action_graphs ORDER BY name ASC")
    suspend fun getAllGraphs(): List<ActionGraphEntity>

    /**
     * Reactive list of all graphs.
     */
    @Query("SELECT * FROM action_graphs ORDER BY name ASC")
    fun observeAllGraphs(): Flow<List<ActionGraphEntity>>

    /**
     * Insert-or-replace a graph.
     */
    @Upsert
    suspend fun upsertGraph(entity: ActionGraphEntity)

    /**
     * Deletes a graph; dependent rules cascade.
     */
    @Query("DELETE FROM action_graphs WHERE id = :id")
    suspend fun deleteGraph(id: String)
}

/**
 * Zero-PII execution journal plus local prediction-model checkpoints.
 */
@Dao
interface TelemetryAndModelDao {
    /**
     * Appends one execution record.
     */
    @Upsert
    suspend fun insertLog(entity: ExecutionLogEntity)

    /**
     * Reactive tail of the execution journal.
     */
    @Query("SELECT * FROM execution_logs ORDER BY timestamp_millis DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int = 50): Flow<List<ExecutionLogEntity>>

    /**
     * Deletes rows older than the cutoff timestamp.
     */
    @Query("DELETE FROM execution_logs WHERE timestamp_millis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    /**
     * Keeps only the newest [maxRows] rows.
     */
    @Query(
        """
        DELETE FROM execution_logs WHERE log_id NOT IN (
            SELECT log_id FROM execution_logs ORDER BY timestamp_millis DESC LIMIT :maxRows
        )
        """,
    )
    suspend fun trimToLatest(maxRows: Int)

    /**
     * Transactional age + size pruning used by the idle WorkManager job.
     */
    @Transaction
    suspend fun pruneLogs(cutoffMillis: Long, maxRows: Int) {
        deleteOlderThan(cutoffMillis)
        trimToLatest(maxRows)
    }

    /**
     * Loads the serialized prediction model (null when never trained).
     */
    @Query("SELECT * FROM prediction_model_state WHERE model_id = :modelId LIMIT 1")
    suspend fun loadModel(modelId: String = PredictionModelEntity.SINGLETON_ID): PredictionModelEntity?

    /**
     * Checkpoints the prediction model weights and Markov table.
     */
    @Upsert
    suspend fun saveModel(entity: PredictionModelEntity)
}

/**
 * Primary Room 2.7 database for VodiGesture. Stored strictly inside app-private credential-encrypted
 * storage and excluded from unencrypted cloud transfer via `data_extraction_rules.xml`.
 */
@Database(
    entities = [
        GestureTemplateEntity::class,
        ActionGraphEntity::class,
        AppTriggerRuleEntity::class,
        ExecutionLogEntity::class,
        PredictionModelEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class VodiGestureDatabase : RoomDatabase() {
    /**
     * Template DAO accessor.
     */
    abstract fun gestureTemplateDao(): GestureTemplateDao
    /**
     * Trigger/graph DAO accessor.
     */
    abstract fun triggerAndGraphDao(): TriggerAndGraphDao
    /**
     * Telemetry/model DAO accessor.
     */
    abstract fun telemetryAndModelDao(): TelemetryAndModelDao

    companion object {
        const val DATABASE_NAME: String = "vodigesture_vault.db"
    }
}
