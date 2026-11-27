# Quality improvement log

## Cycle 1 — data clearing and undo safety (in progress)

### QI-01: retained private snapshots

- **Observed weakness / evidence:** prior `clearAllUserData` omitted undo and migration snapshots, ledger and active session (`src/lib/db.ts`); UI claimed all data were removed.
- **User impact:** old private content could remain locally after an apparent complete wipe.
- **Root cause:** wipe covered only primary records; snapshots and separate storage domains were not in its scope.
- **Improvement:** clear DB history/snapshots and active run atomically with primary records; make scope explicit; refuse UI clearing while focus is running or paused with progress.
- **Change:** `src/lib/db.ts`, `src/features/settings/SettingsPage.tsx`, test coverage in `src/test/dataSafety.test.ts` and `src/test/evolutionSafety.test.tsx`.
- **Test results:** see `TEST_RESULTS.md`; no Android or launcher test.
- **Limitations:** not a full device reset; settings, localStorage, external backup, native caches/notifications and possible system backups may remain. In-flight work and process races need further investigation. New non-content clear event may be recorded.

### QI-02: unsafe stale undo

- **Observed weakness / evidence:** general `undoEntry` restored old rows without verifying that their current state still matched the saved result.
- **User impact:** a later edit or deletion could be overwritten or resurrected.
- **Root cause:** missing atomic compare-and-restore.
- **Improvement:** re-read entry and target within the transaction; refuse a stale snapshot; refuse recurring completion rollback when its generated occurrence cannot be safely tracked.
- **Change:** `src/lib/undo.ts` and `src/test/dataSafety.test.ts`.
- **Test results:** see `TEST_RESULTS.md`.
- **Limitations:** snapshot equality is conservative; safe new recurrence entries can be rolled back, but legacy entries without metadata fail closed and write/history creation are not universally atomic.

### QI-03: asynchronous test assumption

- **Observed weakness / evidence:** full suite saw motivation favorite database write before Dexie live query rendered the updated state; the test immediately expected the favorites view to contain a quote.
- **User impact:** flaky automation can hide real failures or misrepresent release readiness.
- **Root cause:** the test conflated DB commit with UI subscription delivery.
- **Change:** wait for the rendered favorite state before continuing in `src/test/momentumNavigation.test.tsx`.
- **Test results:** see `TEST_RESULTS.md`.
- **Limitations:** this does not establish that every UI timing issue or React `act(...)` warning is fixed.


## Cycle 2 — task/backup safety and navigation (in progress)

### QI-04: recurring task side effects and conflicting edits

- Task status and editor operations now commit task, occurrence and undo/event entries together; the editor refuses stale snapshots and keeps unsaved text. New recurring series have identity; legacy rows keep the conservative old heuristic. Reverting a completion removes its generated occurrence only if unchanged and unreferenced.
- Completed recurring task creation creates its next occurrence transactionally. Failure injection and concurrent writes are tested with fake-indexeddb; other entities still have best-effort history. Ledger failure now aborts task operations in the atomic paths.
- Deletion requires confirmation in the task editor and archive and checks the confirmed snapshot. Associated task undo rows and migration task snapshots are removed, but external backup files, focus references and other data may remain; this is not secure erasure.

### QI-05: backup consistency and honest scope

- Exported collections are read in one readonly transaction; AI key is omitted. Structural/enum/date validation is stricter; review screen estimates new/existing IDs, explicitly discloses merge-only behavior and privacy limitations, and guards file-selection races. Export failures surface to the user.
- Existing settings, active runs and history are not restored; backup is neither complete nor encrypted. Counts can change between preview and confirmation. Older, malformed backups that happened to import previously may now be rejected rather than corrupting the database; compatibility testing on actual historic backups remains open.

### QI-06: five-button dock

- Home, assistant, two user-selectable shortcuts and a directory are shown; four stored preference slots remain intact and swaps preserve them. All existing destinations remain in the directory. Tests cover settings preservation and directory access, but not real screen dimensions, TalkBack or device behavior.

See `TEST_RESULTS.md` for the current test run. The wider directive, native timer authority, private signing, widget privacy and device release gates remain open.

### QI-07: focus duration durability and concurrent editors

- Record a completed focus phase and its advanced recovery row together; retain the prior recoverable state and retry after a storage error. Keep a persisted recorded-seconds counter so manually ending an interval session does not record completed blocks twice. Catch-up also records focus when the last crossed boundary was a break. Resets, navigation within a running plan, and deletion of a focus-history row require confirmation. These are web/IndexedDB safeguards, not a resolution of native notification control reconciliation.
- Serialize settings and habit/goal/project/stage/journal edits and their undo rows, reject stale editor snapshots and orphaned habit logs/stages, and require confirmation for irreversible deletion. Explicit opt-in restores a validated subset of ordinary preferences, not device credentials, permissions or an active run. Tests and remaining device limitations are in `TEST_RESULTS.md`.
