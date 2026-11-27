# Test results — flagship programme, cycle 1

## Scope

Working-tree source in `kairos-app`, not the published 3.18.0 APK. Web tests run in Vitest with jsdom and fake-indexeddb. They do not prove Android lifecycle, native widgets, system backup, launcher or performance behavior.

## Executed

| Command | Result | Evidence and caveats |
|---|---|---|
| `npm test` | PASS, 63 files / 398 tests | Final run after the current source and test changes. React `act(...)` warnings still appear. |
| Targeted `npm test -- --run src/test/dataSafety.test.ts src/test/undo.test.ts src/test/eventHistory.test.ts src/test/evolutionSafety.test.tsx src/test/momentumNavigation.test.tsx` | PASS, 41 tests in 5 files | Executed before one additional concurrent-undo test and a success-message wording change; final full run includes those. |
| `npm run typecheck` | PASS | TypeScript project check. |
| `npm run lint` | PASS | ESLint. |
| `npm run build` | PASS | Vite production web build; chunk-size and mixed static/dynamic import warnings remain. Not an Android APK build. |
| `git diff --check` | PASS | Whitespace/conflict check for tracked modifications. |

## Failures encountered and addressed

- Initial targeted test: a manually constructed goal undo snapshot did not match its actual row's `updatedAt`; test now uses the real exact snapshot, consistent with conflict-safe semantics.
- Initial full suite: motivation favorites test observed the database commit before its live query updated the UI. Test now waits for the rendered favorite state. Later targeted and full runs passed; this does not prove the absence of other timing defects.

## New cases exercised

- Clearing IndexedDB primary data and history/snapshots, preserving settings, with a non-content clear event allowed.
- Simulated table-clear failure rolls back the IndexedDB transaction.
- Confirmation screen and active-focus clearing guard in jsdom.
- Undo rejects a subsequent edit or missing row and rejects a legacy recurring-completion rollback.
- Concurrent requests for the same undo apply at most once in fake-indexeddb.

- Completion and next recurring occurrence atomic rollback; independent series identity, legacy conservative fallback; conflicts and task form confirmation in jsdom.
- Backup snapshot consistency under a simulated concurrent transaction; merge preview collisions, stale estimates and malformed field rejection.
- Five-button dock, preference preservation, shortcut editing and directory access in jsdom.

- Focus recovery transaction and retry failure, catch-up across a break, and no duplicate manual accounting (`focusRecordingSafety.test.tsx`, `chronoPersistence.test.ts`).
- Concurrent settings, journal, goals, project stages and habit logs; destructive confirmation (`editorSafety.test.ts`, `habitSafety.test.ts`, `confirmDelete.test.tsx`, `chronoUi.test.tsx`).
- Optional preference restore excludes device secrets, permissions and active-focus conflict (`backupConsistency.test.ts`).

## Not executed / not verified

Android compile/APK, installation/upgrade, real POCO X3 Pro or other phone, screen/launcher, TalkBack, actual system-backup behavior, native notification and widget deletion, process death, multi-WebView concurrent changes, battery, RAM, CPU, thermal, frame performance, local model installation or inference. No claim of release readiness. The previous 3.18.0 Android CI applies to an earlier commit, not these changes.
