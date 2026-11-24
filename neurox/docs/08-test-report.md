# Test report — 2026-11-24

## Verified results

| Check | Result | Evidence and scope |
|---|---|---|
| Core and cipher cases | 199 PASS, 0 FAIL | Production Kotlin domain/cipher; Kotlin 2.2.21, JVM 17 |
| Static invariants and XML | 34 PASS | Includes offline manifest, assets and preserved Python sources |
| Production migration SQL | 4 PASS | Local SQLite preservation, index, foreign key and cascade checks |
| Kotlin syntax | 43 files, 0 parse errors | Local scan after color/direction implementation; not Android type checking |
| CI evidence parser | 6 PASS | Complete/reordered chunks, partial/duplicate/unsafe/malformed exports |
| Android compile, Room/KSP, lint | PASS | Native job in runs `45479907006` and `45481036708` |
| Android instrumentation APK compilation | PASS | Both data and app test APKs compiled in run `45481036708` |
| Emulator suite | 25 cases, 2 FAIL, 0 skipped | Run `45481036708`; two XML reports. This is NOT an acceptance pass |
| Original Python source | Preserved | No panel implementation changes |

## Native build and APK

Run `45481036708`, source commit `5dc5ff409967e13e6a0515557a84d6462d000687`, built version `0.3.0-dev` successfully with:

```text
:testing:run :app:assembleDebug :app:lintDebug
:core:data:assembleDebugAndroidTest :app:assembleDebugAndroidTest
```

[Debug APK artifact](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/45481036708/artifacts/14961643788). GitHub access may be required. The archive contains `app-debug.apk`, **12,715,928 bytes**. SHA-256 of the APK (not its ZIP archive):

```text
6f0c7963d7b930cdf5f804cb9e503106d1b721a940aa686c41c5574d1e40a898
```

This is a debug-signed development build, not a signed production release. The artifact is remote; downloading GitHub blob storage from this sandbox failed with EOF. No local download, install or physical-device verification is claimed.

Room generated database-v2 schema during the native build. The first API annotation export was truncated at 4096 characters, so it was correctly rejected as incomplete. Run `45482317590` published complete numbered chunks; its generated schema is now committed under `core/data/schemas/`. The evidence reader accepts only complete, valid exports from a successful native job on the current branch.

## Emulator failures and corrective work

Run `45481036708` actually executed 25 instrumented cases: 23 passed, two failed, none skipped. The reported failures were:

1. `NavigationTest.homeOpensAnActualExercise`: the start-session node was absent immediately after the library navigation click.
2. `NavigationTest.emptyHistoryDoesNotInventStatistics`: the expected empty-state text was not displayed.

These were test failures, not an unverified infrastructure diagnosis. Existing navigation tests used the installed application's persistent database, whereas other UI tests used isolated fixtures. Correction commit `139a2dd` uses a fresh in-memory database and a managed ViewModel for each navigation test, waits for the destination node, and scrolls to the empty state. It adds a real Activity smoke test separately so manifest/Activity wiring remains covered, plus a builder save-and-display UI scenario. Run `45482317590` executed 27 cases with one failure: the home-to-training navigation case still timed out. Empty history, builder save-and-display, Activity smoke, and all other cases passed. Inspection then found that the VISION text could exist below the viewport: the test clicked without scrolling the module card into view. The next correction explicitly scrolls and asserts visibility before clicking. Do not count that correction as passing until CI completes.

Custom CRUD, ownership, deletion/history snapshots, practice reward exclusion, invalid input, checkpoint compatibility and Room migration cases were included in the 25-case run; the two reported failures were only navigation cases. Migration starts from a hand-audited v1 SQL fixture, not a historical generated Room schema.

## Limits and reproducibility

The local Gradle distribution download and Google SDK download remain blocked by sandbox network policy. Hosted GitHub Actions bypassed that local limitation without disabling TLS or using an unverified mirror. The initial run `45479693104` failed in SDK setup; switching to the runner's installed SDK enabled successful native builds.

Physical-device, TalkBack, accessibility audit, frame timing, memory, battery, reduced-motion acceptance and airplane-mode runtime checks have not been completed. The integrated vault/restore workflow does not exist yet. A successful compile and 23 passing device cases do not establish production readiness.

Local raw output is ignored under `test-output/`. Reproduce with `scripts/test-core.sh`, `scripts/check-kotlin-syntax.sh` and `python3 -m unittest discover -s testing -p 'test_*.py'`. Use `python3 scripts/read-ci-evidence.py RUN_ID` to retrieve workflow conclusions, test failures, artifact links and APK metadata via the authenticated GitHub CLI without depending on downloadable log blobs. Add `--save-schema` only to recover complete generated schema evidence.

## Source continuation 0.4 (native verification pending)

Color Reaction adds four named fixed-color signals and four real choices. Direction Match presents physical arrows; levels above one randomize their position independently of the correct direction. Both use unpublished-stimulus protection, monotonic response timing, early/wrong/missed outcomes, input revision checks and checkpoint recovery. They are selectable in the built-in library and finite builder. Local tests now total 199 cases, including a full fourteen-exercise cross-product matrix and 22 new reaction checks. The first local run correctly failed the old three-per-module catalogue invariant; the invariant was updated to the implemented 3/5/3/3 counts, and the complete suite passed. No behavior test was removed.

The available 0.3 APK does not include these new paths. Their native compile and new UI scenario require the next CI run. Test helper syntax remains a separate result from native compilation.
