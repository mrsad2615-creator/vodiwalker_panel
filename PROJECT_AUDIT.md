# KAIROS project audit — flagship programme baseline

Status: **in progress**. This is an initial risk-led audit, not a claim that every source file was reviewed or every defect reproduced. Prior detailed evidence: `KAIROS_COMPREHENSIVE_AUDIT_FA.md` (if available in the workspace), `KAIROS_BASELINE_AUDIT.md`, `KAIROS_3.18.0_FA.md`. Baseline release 3.18.0; subsequent working-tree changes are not included in that APK.

## Architecture and inventory

React/TypeScript/Vite/Capacitor app in `kairos-app`; Dexie IndexedDB v5 (14 tables), Jalali dates, Java Android service/plugins/widgets. Main pages include home, tasks, habits, projects, calendar, focus, analytics, text assistant, motivation and settings. Android min API 24 and target API 36. An existing six-slot shell remains in code; the user has now selected a **personalizable five-destination** direction, not yet implemented.

## Priority findings and current state

| ID | Priority | Evidence and status | Remaining risk |
|---|---|---|---|
| DATA-01 | P0 | `clearAllUserData` originally retained undo snapshots and migration snapshots; this cycle clears them, ledger, grants and active session in the DB transaction. Settings are intentionally retained and a content-free clear event may be written afterward. UI discloses scope and blocks while a focus session is in progress. | Not a full device reset. Web storage, native service/widget/notification state and exported/system backups remain; multi-tab/concurrent activity and Android deletion are not verified. |
| DATA-02 | P0 | `exportBackup` v3 exports primary data; import merges missing rows, does not restore settings and lacks all-table coverage. | Full/selected restore, semantics, old-file migrations and Android interruption remain open. Do not call existing backup complete. |
| DATA-03 | P0 | General `undoEntry` previously overwrote current records without checking them. This cycle checks snapshot and entry inside the transaction; legacy recurring completions are refused rather than partially undone. | Write + history creation are not consistently atomic; recurrence creation is still not transactional, and full safe recurrence undo is not implemented. |
| TIMER-01 | P0 | `AppContext`, `chrono/persistence.ts`, `ChronoService.java` maintain separate representations. | Process death, notification control and restoration need a single authoritative state and device tests. |
| SEC-01 | P0 | Existing repository signing material is public; do not treat signed APKs as verified publisher identity. | Key rotation and compatible user migration remain unresolved; do not delete existing keys or invalidate user installs without a migration strategy. |
| SEC-02 | P0 | Android manifest `allowBackup=true`; system backup may include app data under device-dependent conditions. | Policy/backup rules and transfer behavior require explicit design and tests; no assertion of actual leakage. |
| WIDGET-01 | P1 | Native providers use snapshot data and deep links; daily/annual views are read-only. | Launcher, freshness, resizing, privacy and safe direct interaction not verified on devices. |
| AI-01 | P1 | Existing assistant is local rule-based, not an LLM; current history is not durable. | Offline runtime, model support, Persian evaluation and controlled tools are not implemented or tested. |
| UX-01 | P1 | Low-contrast text token and small text sizes found in source; modal focus management lacks an explicit trap/restore. | Needs real-screen contrast, TalkBack, font-scale and keyboard tests. |
| PERF-01 | P1 | Full-table `toArray` live queries and large Vite chunk warrant profiling. | No startup, frame, RAM, power or thermal benchmark performed. |

## Testing and constraints

Web tests use Vitest/jsdom/fake-indexeddb; Android CI success for the older 3.18.0 release is not evidence for this unbuilt working tree. No physical phone, launcher, TalkBack, visual screenshot review, or native release build has been run for this cycle. `TEST_RESULTS.md` records commands and outcomes. Audio files and their licences need independent verification.

## Next gate

Keep DATA-01 through SEC-02 open until cross-store behavior, device cases, recurrence and backup are demonstrated; do not expand to a new major module while a critical data integrity defect remains. Full directive decomposing, source-file audit, competitor research, visual specifications and device evidence are ongoing, not complete.
