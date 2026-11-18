# AegisGuard Architecture & Database Migrations

## 17-Module Dependency Graph
- `:core:model` (Pure Kotlin/JVM): Domain models, `AppPreferences`, `FullBackupSnapshot`, `AegisRepository`, `PreferencesRepository`, `ReminderScheduler`.
- `:core:crypto` (Pure Kotlin/JVM): `CryptoEngine` — PBKDF2WithHmacSHA256 (120,000 iterations), AES/GCM/NoPadding (256-bit, 128-bit auth tag, random 96-bit IV + 128-bit salt), SHA-256 integrity checksums, encrypted backup envelope (`AegisEncryptedBackupEnvelope`), and consent share packet signing.
- `:core:maps` (Pure Kotlin/JVM): `GeoMath` (Haversine distance, forward bearing, cardinal directions, DMS & UTM approximations, spatial grid binning), `Projection3D` (3D perspective projection + elevation extrusion), `OfflineMapSeeds`.
- `:core:ai` (Pure Kotlin/JVM): `LocalKnowledgeBase` (reviewed offline first-aid & disaster guides + EN/FA AI safety disclaimers), `LocalAiEngine` (EN/FA normalization, BM25 ranking, multi-signal classifier, trigram/token/geo duplicate detection, extractive summarizer, context-aware checklist synthesis, local statistics).
- `:core:database` (Android Library): Room v2 (`AegisDatabase`, 23 tables, `exportSchema = true`), `AegisMigrations.MIGRATION_1_2`, `RoomAegisRepository`, `DataStorePreferencesRepository`, `WorkManagerReminderScheduler` + `SecurityReminderWorker`.
- `:core:ui` (Android Library): `AegisTheme` (Obsidian Night / Obsidian Pearl, RTL/LTR EN/FA), `ObsidianGlassCard`, `Hero3DSecurityShield`, `AegisModuleIcon` custom Canvas icons, `LocalFileExporter` (`PdfDocument` + `FileProvider`).
- `:feature:*` (10 Android Library feature modules): `personal`, `crisis`, `maps`, `emergency`, `ai`, `family`, `academy`, `analytics`, `privacy`, `utilities`.
- `:app` (Android Application): `AppContainer` manual constructor DI, `MainActivity` (`FragmentActivity` host for `BiometricPrompt` + `FLAG_SECURE`), `CommandHomeScreen`, `LockGateOverlay`, `AegisBootReceiver`.

## Database Schema & Migration v1 -> v2
- **Schema Location**: `core/database/schemas/ir.aegis.core.database.AegisDatabase/2.json`.
- **`MIGRATION_1_2`** cleanly migrates authentic v1 SQLite databases to v2:
  1. Adds `duplicateOfId TEXT` and `aiSuggestedCategory TEXT` to `incidents` plus indices `index_incidents_category`, `index_incidents_status`, `index_incidents_createdAtMillis`.
  2. Adds `elevationMeters REAL NOT NULL DEFAULT 0.0` to `map_markers`.
  3. Creates the `family_plans` table.
- Verified automatically via `AegisDatabaseMigrationTest` using `FrameworkSQLiteOpenHelperFactory` to construct a legacy v1 SQLite file, insert v1 records, upgrade through Room's schema validator with `MIGRATION_1_2`, verify legacy rows survive, and test full AES-256-GCM snapshot export -> panic wipe -> restore.
