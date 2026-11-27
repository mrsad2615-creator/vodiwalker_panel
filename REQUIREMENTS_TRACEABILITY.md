# Requirements traceability — starting registry

The user’s 32-part flagship directive is the source of truth. This initial registry indexes *sections*, not yet every individual sentence; decomposition into independently testable requirements is outstanding. A section being listed does not imply implementation or verification. Status vocabulary: NOT_STARTED, RESEARCHING, DESIGNED, IMPLEMENTING, IMPLEMENTED, TESTING, VERIFIED, BLOCKED, DEFERRED. No item in this initial registry is marked VERIFIED. Priorities: P0 integrity/security, P1 core, P2 expansion.

| ID | Directive / module | Priority | Dependencies | Status | Evidence / remaining work |
|---|---|---|---|---|---|
| DIR-01 | Product principles | P0 | All modules | RESEARCHING | Principles set; requirements and proof per flow incomplete. |
| DIR-02 | Competitive research by module | P1 | Sources | RESEARCHING | `COMPETITIVE_BENCHMARK.md` initial entries only; most products/modules not studied. |
| DIR-03 | Technical research and decisions | P1 | Compatibility and licences | RESEARCHING | `TECHNOLOGY_DECISIONS.md`; runtime and device evaluation pending. |
| DIR-04 | Project audit and mandatory P0 | P0 | Code/test inspection | IMPLEMENTING | `PROJECT_AUDIT.md`; DATA-01/03 mitigations incomplete; full audit outstanding. |
| DIR-05 | Living dashboard and five configurable destinations | P1 | P0, design, settings migration | IMPLEMENTING | Five-button dock with two configurable shortcuts; old four preferences retained. Living dashboard, visual and device validation remain open. |
| DIR-06 | Complete task system | P1 | DATA-03 | IMPLEMENTING | Transactional task edit/completion, conflict checks, recurrence identity and confirmation added; full requirement remains open. |
| DIR-07 | In-app and native widgets | P1 | DATA-01, TIMER-01, device | NOT_STARTED | Existing snapshot widgets are not full ecosystem. |
| DIR-08 | Habits | P1 | Recurrence, analytics | NOT_STARTED | Existing partial habits; full directive not assessed. |
| DIR-09 | Projects and goals | P1 | Data models | NOT_STARTED | Existing partial models; linked progress needs verification. |
| DIR-10 | Calendar and time | P1 | Date model, scheduling | NOT_STARTED | Existing Jalali views and ICS; full requirements open. |
| DIR-11 | Focus and authoritative native timer | P0 | TIMER-01, Android tests | NOT_STARTED | Native/WebView divergence unresolved; web focus recording and recovery are more atomic. |
| DIR-12 | Text-only local AI | P1 | runtime research, model/device | IMPLEMENTING | Current assistant remains rule-based; no local LLM. Local conversation snapshots are persisted with confirmed deletion and backup. Runtime, model compatibility, Persian quality, privacy boundaries and target-device evaluation remain open. |
| DIR-13 | Inner Drive motivation | P2 | content evidence/privacy | NOT_STARTED | Existing quote interface differs from requested module. |
| DIR-14 | Analytics | P1 | trustworthy data models | NOT_STARTED | Existing charts do not demonstrate full requirement. |
| DIR-15 | Journal and knowledge | P2 | storage, backup | NOT_STARTED | Existing journal partial. |
| DIR-16 | Global search and commands | P2 | indexing, data contracts | NOT_STARTED | Existing search partial. |
| DIR-17 | Offline automation | P2 | permissions, audit, undo | NOT_STARTED | Rule engine not implemented. |
| DIR-18 | Settings and personalization | P1 | module contracts | IMPLEMENTING | DATA-01 disclosure implemented; full settings remain open. |
| DIR-19 | Backup and restore | P0 | schema, integrity, migration | IMPLEMENTING | Consistent export snapshot, validation and collision preview; opt-in safe preferences, still merge-only and incomplete. |
| DIR-20 | Security and privacy | P0 | signing, backup, data inventory | RESEARCHING | Public signing key and Android backup remain open. |
| DIR-21 | Original module-specific design | P1 | research, usability | NOT_STARTED | No screenshots or approved screen specifications this cycle. |
| DIR-22 | Granular requirements and module execution | P1 | registry decomposition | IMPLEMENTING | This section-level registry is a start, not full traceability. |
| DIR-23 | Visual QA | P1 | device/screenshots | BLOCKED | No browser/Android device visual inspection available this cycle. |
| DIR-24 | Functional, E2E and device testing | P0 | native device/toolchain | TESTING | `TEST_RESULTS.md`; jsdom is not Android E2E. |
| DIR-25 | Performance measurements | P1 | device profiler | BLOCKED | No device benchmarks. |
| DIR-26 | Accessibility and internationalization | P1 | design, device testing | NOT_STARTED | Existing RTL/Jalali partial; accessibility not verified. |
| DIR-27 | APK and dependency policy | P1 | release builds | NOT_STARTED | New APK target approximately 300 MB, optional models separate. No new release size measured. |
| DIR-28 | Required project docs | P1 | evidence per module | IMPLEMENTING | Only some real evidence documents exist; full list incomplete. |
| DIR-29 | Quality scoring and release gates | P0 | tested modules | NOT_STARTED | No unsupported numerical scores. Critical defects prevent release readiness. |
| DIR-30 | Improvement loop | P1 | observations and tests | IMPLEMENTING | `QUALITY_IMPROVEMENT_LOG.md` starts this cycle. |
| DIR-31 | Autonomy and approved decisions | P0 | user direction | IMPLEMENTING | Five customizable destinations selected explicitly; privacy boundaries still apply. |
| DIR-32 | Final acceptance | P0 | all release gates | NOT_STARTED | No claim of flagship completion or release readiness. |

## Atomic requirements begun this cycle

| ID | Requirement | Priority | Dependencies | Status | Design / code / data / tests | Limitations |
|---|---|---|---|---|---|---|
| DATA-01A | Remove undo and migration snapshots and old ledger from local DB on confirmed data clearing. | P0 | IndexedDB transaction | TESTING | Design/scope: `PROJECT_AUDIT.md`; `src/lib/db.ts`; relevant Dexie tables; `src/test/dataSafety.test.ts`, `eventHistory.test.ts`. | Settings retained; external stores and concurrent processes not covered. |
| DATA-01B | Disclose retained data; block clearing during an active/paused focus run. | P0 | focus state | TESTING | `SettingsPage.tsx`, AppContext focus state; `evolutionSafety.test.tsx`. | No Android UI or native-state guarantee. |
| DATA-03A | Refuse undo if target changed or entry already replayed, transactionally. | P0 | snapshots and Dexie | TESTING | `src/lib/undo.ts`; undo and target tables; `dataSafety.test.ts`, `undo.test.ts`. | Conservative JSON snapshot comparison; fail-closed; writer/history atomicity remains open. |
| DATA-03B | Avoid partial undo of legacy recurring-task completion. | P0 | recurrence model | TESTING | `src/lib/undo.ts`; `dataSafety.test.ts`. | Refuses rather than implements full recurrence undo; direct task actions still need work. |

| DATA-03C | Commit task changes, status, generated occurrence and undo metadata atomically. | P0 | Dexie, occurrence identity | TESTING | `db.ts`, `undo.ts`, `recurrenceSafety.test.ts`, `taskSafetyUi.test.tsx`. | Other entities still have best-effort history; old series retain title-based duplicate heuristic. |
| DATA-04A | Export a consistent snapshot and show an honest merge preview. | P0 | Backup schema, IndexedDB | TESTING | `db.ts`, `backupValidation.ts`, `SettingsPage.tsx`, `backupConsistency.test.ts`, `evolutionSafety.test.tsx`. Optional authenticated encryption: `backupCrypto.ts`, `backupCrypto.test.ts`, `encryptedBackupUi.test.tsx`. | Not a complete backup; preview is an estimate and does not lock subsequent imports; encrypted restore is not tested on Android. |
| TIMER-02 | Atomically record completed focus and recovery state; avoid duplicate recorded durations. | P0 | IndexedDB, clock | TESTING | `chrono/persistence.ts`, `AppContext.tsx`, `focusRecordingSafety.test.tsx`. | Native notification actions can still diverge when WebView is absent; physical Android testing outstanding. |
| NAV-01 | Five dock buttons, customize two shortcuts, retain old preferences and provide directory. | P1 | settings | TESTING | `navigation.ts`, `AppShell.tsx`, `momentumNavigation.test.tsx`. | Device, viewport and accessibility validation remain open. |

| AI-HISTORY-01 | Persist local assistant exchanges; guard deletion, backup and sample visibility. | P0 | IndexedDB v6 and backup v4 | TESTING | `CommandCenter.tsx`, `assistantHistory.ts`, `assistantHistory.test.tsx`, `backupValidation.ts`. | Conversation snapshots are historical, rule-based and not an LLM. Sample-derived entries are conservatively hidden, which can hide unrelated messages too. Not tested on physical Android. |

Test command results and limitations: `TEST_RESULTS.md`. All other granular requirements, design artifacts, data maps and test cases still need decomposition; this file must be expanded rather than declaring whole sections complete.
