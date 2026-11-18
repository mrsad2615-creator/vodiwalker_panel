package ir.aegis.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AegisMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE incidents ADD COLUMN duplicateOfId TEXT")
            db.execSQL("ALTER TABLE incidents ADD COLUMN aiSuggestedCategory TEXT")
            db.execSQL("ALTER TABLE map_markers ADD COLUMN elevationMeters REAL NOT NULL DEFAULT 0.0")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `family_plans` (
                    `id` TEXT NOT NULL,
                    `planTitle` TEXT NOT NULL,
                    `evacuationProtocol` TEXT NOT NULL,
                    `communicationProtocol` TEXT NOT NULL,
                    `outOfAreaContactName` TEXT NOT NULL,
                    `outOfAreaContactPhone` TEXT NOT NULL,
                    `rallyWindowMinutes` INTEGER NOT NULL,
                    `updatedAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_incidents_category` ON `incidents` (`category`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_incidents_status` ON `incidents` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_incidents_createdAtMillis` ON `incidents` (`createdAtMillis`)")
        }
    }
}

@Database(
    entities = [
        SecurityProfileEntity::class,
        EmergencyCardEntity::class,
        VaultItemEntity::class,
        TrustedContactEntity::class,
        SafetyPlanEntity::class,
        SecurityReminderEntity::class,
        JournalEntryEntity::class,
        IncidentEntity::class,
        IncidentTimelineEntity::class,
        IncidentAttachmentEntity::class,
        ChecklistEntity::class,
        ChecklistItemEntity::class,
        MapPackageEntity::class,
        MapMarkerEntity::class,
        SavedRouteEntity::class,
        FamilyMemberEntity::class,
        MeetingPointEntity::class,
        FamilyPlanEntity::class,
        AcademyProgressEntity::class,
        InventoryItemEntity::class,
        OfflineNoteEntity::class,
        VoiceNoteEntity::class,
        AuditLogEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AegisDatabase : RoomDatabase() {
    abstract fun dao(): AegisDao

    companion object {
        fun build(context: Context, dbName: String = "aegis_security.db"): AegisDatabase =
            Room.databaseBuilder(context.applicationContext, AegisDatabase::class.java, dbName)
                .addMigrations(AegisMigrations.MIGRATION_1_2)
                .build()
    }
}
