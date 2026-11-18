package ir.aegis.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.aegis.core.crypto.CryptoEngine
import ir.aegis.core.model.AppPreferences
import ir.aegis.core.model.FamilyPlan
import ir.aegis.core.model.VaultCategory
import ir.aegis.core.model.VaultItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AegisDatabaseMigrationTest {

    @Test
    fun migrateVersion1ToVersion2PreservesRowsAndValidatesSchema() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_v1_v2.db"
        context.deleteDatabase(dbName)

        // Create authentic v1 SQLite database before opening with Room v2
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `security_profiles` (`id` TEXT NOT NULL, `fullName` TEXT NOT NULL, `aliasOrCallsign` TEXT NOT NULL, `homeRegionLabel` TEXT NOT NULL, `homeLatitude` REAL NOT NULL, `homeLongitude` REAL NOT NULL, `safeCheckInCode` TEXT NOT NULL, `duressCode` TEXT NOT NULL, `emergencyNote` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `emergency_cards` (`id` TEXT NOT NULL, `fullName` TEXT NOT NULL, `dateOfBirth` TEXT NOT NULL, `bloodType` TEXT NOT NULL, `allergies` TEXT NOT NULL, `medications` TEXT NOT NULL, `medicalConditions` TEXT NOT NULL, `primaryIceContactName` TEXT NOT NULL, `primaryIceContactPhone` TEXT NOT NULL, `organDonor` INTEGER NOT NULL, `languagesSpoken` TEXT NOT NULL, `insuranceOrIdNumber` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `vault_items` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `encryptedPayloadBase64` TEXT NOT NULL, `ivBase64` TEXT NOT NULL, `saltBase64` TEXT NOT NULL, `sha256Checksum` TEXT NOT NULL, `attachmentPath` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `trusted_contacts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `relation` TEXT NOT NULL, `phone` TEXT NOT NULL, `alternateChannel` TEXT NOT NULL, `addressOrZone` TEXT NOT NULL, `priorityRank` INTEGER NOT NULL, `canReceiveSosMessage` INTEGER NOT NULL, `notes` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `safety_plans` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `scenarioType` TEXT NOT NULL, `triggerConditions` TEXT NOT NULL, `actionStepsJson` TEXT NOT NULL, `primarySafeLocation` TEXT NOT NULL, `backupSafeLocation` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `security_reminders` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `notes` TEXT NOT NULL, `dueAtMillis` INTEGER NOT NULL, `recurrenceDays` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `journal_entries` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `isEncrypted` INTEGER NOT NULL, `ivBase64` TEXT NOT NULL, `saltBase64` TEXT NOT NULL, `threatLevel` TEXT NOT NULL, `tagsJson` TEXT NOT NULL, `locationLabel` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `incidents` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `category` TEXT NOT NULL, `priority` TEXT NOT NULL, `status` TEXT NOT NULL, `latitude` REAL, `longitude` REAL, `locationLabel` TEXT NOT NULL, `isArchived` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `incident_timeline` (`id` TEXT NOT NULL, `incidentId` TEXT NOT NULL, `eventType` TEXT NOT NULL, `summary` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`incidentId`) REFERENCES `incidents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_incident_timeline_incidentId` ON `incident_timeline` (`incidentId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `incident_attachments` (`id` TEXT NOT NULL, `incidentId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `localFilePath` TEXT NOT NULL, `sha256Checksum` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`incidentId`) REFERENCES `incidents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_incident_attachments_incidentId` ON `incident_attachments` (`incidentId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `checklists` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `linkedIncidentId` TEXT, `isAiGenerated` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `checklist_items` (`id` TEXT NOT NULL, `checklistId` TEXT NOT NULL, `label` TEXT NOT NULL, `isChecked` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`checklistId`) REFERENCES `checklists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklist_items_checklistId` ON `checklist_items` (`checklistId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `map_packages` (`id` TEXT NOT NULL, `regionName` TEXT NOT NULL, `regionCode` TEXT NOT NULL, `description` TEXT NOT NULL, `minLat` REAL NOT NULL, `maxLat` REAL NOT NULL, `minLon` REAL NOT NULL, `maxLon` REAL NOT NULL, `sizeBytes` INTEGER NOT NULL, `tileCount` INTEGER NOT NULL, `versionTag` TEXT NOT NULL, `sha256Checksum` TEXT NOT NULL, `isBundled` INTEGER NOT NULL, `isInstalled` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `map_markers` (`id` TEXT NOT NULL, `packageId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `category` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `isEmergencyResource` INTEGER NOT NULL, `isCustom` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_map_markers_packageId` ON `map_markers` (`packageId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `saved_routes` (`id` TEXT NOT NULL, `packageId` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `waypointsJson` TEXT NOT NULL, `totalDistanceMeters` REAL NOT NULL, `estimatedWalkMinutes` INTEGER NOT NULL, `hazardLevel` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `family_members` (`id` TEXT NOT NULL, `fullName` TEXT NOT NULL, `relation` TEXT NOT NULL, `ageGroup` TEXT NOT NULL, `bloodType` TEXT NOT NULL, `allergies` TEXT NOT NULL, `medications` TEXT NOT NULL, `specialNeeds` TEXT NOT NULL, `phone` TEXT NOT NULL, `emergencyNote` TEXT NOT NULL, `checkInStatus` TEXT NOT NULL, `lastCheckInMillis` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `meeting_points` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `isPrimary` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `addressNote` TEXT NOT NULL, `landmarkNote` TEXT NOT NULL, `instructions` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `academy_progress` (`courseId` TEXT NOT NULL, `completedLessonsJson` TEXT NOT NULL, `bestQuizScore` INTEGER NOT NULL, `totalQuizQuestions` INTEGER NOT NULL, `scenarioBestScore` INTEGER NOT NULL, `badgeEarned` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`courseId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `inventory_items` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `minimumThreshold` REAL NOT NULL, `expiryAtMillis` INTEGER, `storageLocation` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `offline_notes` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `category` TEXT NOT NULL, `isPinned` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `voice_notes` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `localFilePath` TEXT NOT NULL, `durationMillis` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` TEXT NOT NULL, `actionType` TEXT NOT NULL, `moduleName` TEXT NOT NULL, `summary` TEXT NOT NULL, `timestampMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_timestampMillis` ON `audit_logs` (`timestampMillis`)")

                db.execSQL("INSERT INTO incidents VALUES ('v1-inc', 'Legacy v1 Fire Drill', 'Created in schema v1', 'FIRE', 'HIGH', 'OPEN', 35.7, 51.4, 'HQ', 0, 1000, 1000)")
                db.execSQL("INSERT INTO map_markers VALUES ('v1-mk', 'pack-tehran-grid', 'Legacy Shelter', 'v1 point', 'SHELTER', 35.71, 51.39, 1, 0, 1000)")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build(),
        )
        helper.writableDatabase.close()

        // Open with Room v2 + MIGRATION_1_2 (Room validates exact schema integrity automatically)
        val roomDb = Room.databaseBuilder(context, AegisDatabase::class.java, dbName)
            .addMigrations(AegisMigrations.MIGRATION_1_2)
            .build()

        val repo = RoomAegisRepository(roomDb)
        val migratedIncident = repo.getIncidentById("v1-inc")
        assertNotNull(migratedIncident)
        assertEquals("Legacy v1 Fire Drill", migratedIncident?.title)

        val migratedMarkers = repo.observeMapMarkers().first()
        assertEquals(1, migratedMarkers.size)
        assertEquals(0.0, migratedMarkers.first().elevationMeters, 0.01)

        // Write into newly migrated family_plans table
        repo.upsertFamilyPlan(
            FamilyPlan("fp-test", "Migrated Plan", "Evacuate North", "SMS Code", "Relay", "112", 30, 2000L),
        )
        assertEquals("Migrated Plan", repo.observeFamilyPlan().first()?.planTitle)

        // Test encrypted vault + backup + panic wipe + restore
        val enc = CryptoEngine.encryptText("Passport #AB1234", "pin1234", iterations = 2_000)
        repo.upsertVaultItem(
            VaultItem("v-1", "Passport", VaultCategory.IDENTITY, enc.ciphertextBase64, enc.ivBase64, enc.saltBase64, enc.plaintextSha256, "", 1000L, 1000L),
        )
        val snapshot = repo.exportFullSnapshot(AppPreferences())
        val sealed = CryptoEngine.sealEncryptedBackup(snapshot, "RestoreKey!2026")
        repo.panicWipeSensitiveData()
        assertTrue(repo.observeVaultItems().first().isEmpty())
        val reopened = CryptoEngine.openEncryptedBackup(sealed, "RestoreKey!2026")
        repo.restoreFullSnapshot(reopened)
        assertEquals(1, repo.observeVaultItems().first().size)

        roomDb.close()
        context.deleteDatabase(dbName)
    }
}
