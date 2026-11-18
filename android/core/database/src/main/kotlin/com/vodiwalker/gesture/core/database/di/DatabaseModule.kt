package com.vodiwalker.gesture.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.vodiwalker.gesture.core.database.ExecutionLogEntity
import com.vodiwalker.gesture.core.database.GestureTemplateDao
import com.vodiwalker.gesture.core.database.RoomActionGraphRepository
import com.vodiwalker.gesture.core.database.RoomGestureTemplateRepository
import com.vodiwalker.gesture.core.database.RoomTriggerRuleRepository
import com.vodiwalker.gesture.core.database.TelemetryAndModelDao
import com.vodiwalker.gesture.core.database.TriggerAndGraphDao
import com.vodiwalker.gesture.core.database.VodiGestureDatabase
import com.vodiwalker.gesture.core.model.ActionGraphRepository
import com.vodiwalker.gesture.core.model.ExecutionReport
import com.vodiwalker.gesture.core.model.ExecutionTelemetryRepository
import com.vodiwalker.gesture.core.model.GestureTemplateRepository
import com.vodiwalker.gesture.core.model.TriggerRuleRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [ExecutionTelemetryRepository] enforcing zero-PII bounded storage.
 */
@Singleton
class RoomExecutionTelemetryRepository @Inject constructor(
    private val dao: TelemetryAndModelDao,
) : ExecutionTelemetryRepository {
    override suspend fun recordExecution(report: ExecutionReport, timestampMillis: Long) {
        dao.insertLog(
            ExecutionLogEntity(
                graphId = report.graphId,
                triggerRuleId = report.triggerRuleId,
                overallSuccess = report.overallSuccess,
                nodeCount = report.traces.size,
                durationMicros = report.totalDurationMicros,
                timestampMillis = timestampMillis,
            ),
        )
    }

    override suspend fun pruneOlderThan(cutoffMillis: Long, maxRetainedRows: Int) {
        dao.pruneLogs(cutoffMillis = cutoffMillis, maxRows = maxRetainedRows)
    }
}

/**
 * Hilt bindings: repository ports → Room adapters, plus the singleton WAL database.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {

    /**
     * Binds the Room template repository to the domain port.
     */
    @Binds
    @Singleton
    abstract fun bindGestureTemplateRepository(
        impl: RoomGestureTemplateRepository,
    ): GestureTemplateRepository

    /**
     * Binds the Room trigger-rule repository to the domain port.
     */
    @Binds
    @Singleton
    abstract fun bindTriggerRuleRepository(
        impl: RoomTriggerRuleRepository,
    ): TriggerRuleRepository

    /**
     * Binds the Room action-graph repository to the domain port.
     */
    @Binds
    @Singleton
    abstract fun bindActionGraphRepository(
        impl: RoomActionGraphRepository,
    ): ActionGraphRepository

    /**
     * Binds the Room telemetry repository to the domain port.
     */
    @Binds
    @Singleton
    abstract fun bindExecutionTelemetryRepository(
        impl: RoomExecutionTelemetryRepository,
    ): ExecutionTelemetryRepository

    companion object {

        /**
         * Builds the singleton database with WAL journaling for concurrent reads.
         */
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): VodiGestureDatabase =
            Room.databaseBuilder(
                context,
                VodiGestureDatabase::class.java,
                VodiGestureDatabase.DATABASE_NAME,
            )
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()

        /**
         * Provides the template DAO.
         */
        @Provides
        fun provideGestureTemplateDao(db: VodiGestureDatabase): GestureTemplateDao =
            db.gestureTemplateDao()

        /**
         * Provides the trigger/graph DAO.
         */
        @Provides
        fun provideTriggerAndGraphDao(db: VodiGestureDatabase): TriggerAndGraphDao =
            db.triggerAndGraphDao()

        /**
         * Provides the telemetry/model DAO.
         */
        @Provides
        fun provideTelemetryAndModelDao(db: VodiGestureDatabase): TelemetryAndModelDao =
            db.telemetryAndModelDao()
    }
}
