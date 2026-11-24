# Data model — database version 2

| Table | Key | Stored values |
|---|---|---|
| profiles | stable string ID | name, guest flag, language, theme mode, dark/light accent, adaptive preference, reduced motion, text scale |
| sessions | UUID | profile FK, exercise/module, start/end epoch ms, status, checkpoint JSON, difficulty, baseline flag, validity, active duration, accuracy, latency/deviation, WPM, XP |
| favorites | profile ID + exercise | profile-scoped saved exercise |
| saved_themes | UUID | profile FK, name, independent dark/light colors |
| adaptations | UUID | profile FK, exercise, timestamp, prior/suggested level, reason, acceptance flag |

All child tables delete on profile deletion. Guest ID is `guest`. A guest row cannot be deleted through the repository. Session lookup requires the session ID and the profile ID. Profile switching is a logical data partition, not PIN authentication.

DataStore keeps only the selected profile ID. If this ID is absent or no longer exists, select the guest row. Settings that belong to a profile stay in that profile's Room row.

A checkpoint has a format version, validated configuration, attempt list, elapsed active time and completion flag. Attempts contain numerical measurements and aggregate weak-key counts. They do not contain the user's typed string. Decode rejects unknown versions, invalid enum values, unsafe bounds and contradictory completion state.

Use one transaction for session completion. Ignore repeated completion callbacks. Ignore checkpoints older than durable progress. Derive total XP from completed session rows. Do not create a second mutable reward total.

Room schema export is configured. Successful native CI generated the version-2 schema JSON. The first API export was truncated and was rejected. The complete chunked export from successful native run 45482317590 is now checked in at `core/data/schemas/com.neurox.data.NeuroxDatabase/2.json` (identity hash `cfc53d208dc748bbea7b91f8748a7636`). The local evidence reader validated the export before saving it. The instrumented 1-to-2 migration case passed in run 45481036708. Add explicit migrations and tests for future database versions. Never enable destructive migration as an upgrade strategy.

Epoch timestamps use UTC instants. Calendar groups use the current device timezone. Moving between timezones can change a displayed training date. Active duration never uses calendar time.

## Checkpoint format 2

Version 2 adds a signed 64-bit `stimulusSeed` to configuration. Version-1 data remains readable with seed 0. That historical checkpoint change required no Room table change. The current database is version 2 and the current encoder writes checkpoint version 3. Do not rewrite old completed session summaries merely to change checkpoint version.

`WITHHELD` is a correct GO/NO-GO inhibition, not a measured reaction. It has a null response latency. Validation rejects this outcome for other exercises and for scheduled GO trials. Include it in accuracy; exclude it from latency mean and deviation. Do not award a completed inactive session XP without any timed response.

A sprint writes only completed word batches as attempts. Intermediate word submissions can save cumulative session duration but never entered words or a partial batch score. Phrase index and typing drafts are not durable. After interruption, restart the unfinished round; retain the completed attempts and active time already saved.

## Database version 2 and checkpoint format 3

`custom_exercises` uses a UUID primary key and a profile foreign key with cascading deletion. Store name, template/module, difficulty, rounds, base response window, target accuracy, unsuccessful-round allowance, schema version and creation/update times. Index the profile identifier.

Migration 1 to 2 creates only this table and index. `schema-v1.sql` is a hand-audited legacy fixture, not a substitute for exported historical Room schemas. The local SQL suite executes the exact migration statements against SQLite and checks preservation, references and cascade behavior. The instrumented migration test opens the result with Room for complete schema validation.

Checkpoint format 3 stores goal percentage, error allowance, practice flag and paired custom UUID/name fields. Versions 1 and 2 decode with goal 80, allowance 30, practice false and no custom source. Keep the independent database, checkpoint and builder-schema version numbers distinct. Old completed rows need no rewrite.

Custom favorites use `custom:<UUID>` in the existing favorite key. Remove this favorite when deleting its definition. Do not cascade that deletion to sessions; their snapshots are independent historical records.
