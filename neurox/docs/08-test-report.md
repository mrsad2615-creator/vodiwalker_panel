# Test report — 2026-11-24

## Executed

| Check | Result | Scope |
|---|---|---|
| Kotlin core/cipher compilation | PASS | Kotlin 2.2.21, JVM 17, warnings treated as errors |
| Core and security unit cases | 177 PASS, 0 FAIL | Actual production domain and cipher sources |
| Static invariants and XML checks | 34 PASS | No internet permission, profile query patterns, assets, preserved Python sources |
| Kotlin syntax scan | 39 files, 0 parse errors | Includes Gradle and Android test sources; no Android API type checking |
| Production SQL migration | 4 PASS | Local SQLite; not Android Room validation |
| Original Python AST parsing | PASS | No panel source modifications |
| `git diff --check` | PASS at review | Whitespace only |

The first aggregate test command found a missing static-check script after the 101 core cases passed. The missing script was added. The complete command then exited successfully. The syntax scan found shell text appended to an icon source file. That text was removed. The syntax scan then passed. Tests were not removed to hide failures.

## Blocked or not executed

* Gradle 8.13 wrapper startup: **FAILED** during distribution download with `SSLHandshakeException: Remote host terminated the handshake`.
* Android SDK download: **BLOCKED** by network restrictions to `dl.google.com`.
* Android app compilation, lint, Room/KSP generation: **NOT VERIFIED**.
* Room integration tests and Compose UI tests: **NOT RUN**.
* GitHub Actions workflow: **EXECUTED** on this branch. Initial Android SDK installer step failed. The hosted SDK alternative passed setup; native compilation and emulator results are tracked below.
* Emulator and physical device tests: **NOT RUN**.
* Accessibility, frame timing, memory, battery and airplane-mode tests: **NOT RUN**.
* Full vault restore tests: **NOT AVAILABLE**; the restore workflow is not implemented.
* Signed release and installation APK: **NOT PRODUCED**.

The available results validate the tested core behavior only. They do not establish production readiness. Raw local output is regenerated in ignored `neurox/test-output/`. Use `scripts/test-core.sh` and `scripts/check-kotlin-syntax.sh` to reproduce these results.

## Milestone 0.2 continuation

The sandbox tool directory did not persist into this turn. The tool bootstrap script reinstalled the pinned Java runtime and Kotlin compiler. The first test invocation correctly stopped with a missing-tools error; it did not claim a pass.

The expanded suite initially had 142 passing cases. Revision-safety tests increased coverage. A later all-exercise matrix found that Quick Tap exposed a NO-GO plan value in its view model even though its scoring and UI used GO. The view now reports GO for every Quick Tap active trial. The matrix then passed. The final suite has 150 passing cases and no failures. No failing tests were removed. The original catalogue invariant was updated from four entries to all eight implemented identifiers.

The Gradle wrapper download was attempted again in this turn. It failed with the same SSL handshake error. Native app compilation, lint and instrumented tests remain unverified. The new Compose and codec tests are not included in the 150 executed core cases. The syntax utility still emits a warning about its pinned compiler API; it is not an Android type checker.

## Milestone 0.3 continuation

The local suite now includes 177 passing core/security cases, 34 static checks and four executed SQLite integration checks. The catalogue test now requires all twelve implemented identifiers. The all-exercise matrix includes Pattern Match, Shape Reaction, RSVP and Code Typing. Builder tests cover finite schemas, control-character rejection, source identity, practice reward exclusion and success criteria without changing measured results.

Alternative build paths tested:

* Official Gradle GitHub release: release metadata is reachable, but binary download redirects to a blocked asset host. No unverified mirror or disabled TLS validation was used.
* GitHub Actions run `45479693104`: failed in `android-actions/setup-android` before native compilation. This is not evidence that the app compiles.
* The workflow was changed to use the Android SDK already installed on the hosted runner. Run `45479907006` passed checkout, JDK/SDK setup, local core tests and Kotlin syntax parsing. Native build and emulator outcomes must be checked before release claims.

Source additions not included in the 177 executed cases: Android custom-exercise CRUD, cross-profile rejection, history snapshot, practice session, checkpoint-version and migration validation tests. These require the native instrumented suite. The version-1 SQL fixture is hand-audited; do not describe it as a generated Room schema.
