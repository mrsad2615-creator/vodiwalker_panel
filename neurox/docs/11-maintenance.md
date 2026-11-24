# Maintenance guide

## Before a change

1. Read the scope ledger and known limitations.
2. Run the core tests.
3. Run the Kotlin syntax scan.
4. Keep the Python panel unchanged unless a separate task requires a change.

## After a change

1. Add regression tests for the changed behavior.
2. Run `scripts/test-core.sh`.
3. Run `scripts/check-kotlin-syntax.sh`.
4. Build and lint the Android app in an SDK-capable environment.
5. Run Room and Compose instrumented tests.
6. Update the test report with actual results.
7. Update the scope ledger and release notes.

## Data changes

Keep the generated Room schema in source. Version 2 was recovered from successful hosted native CI; no generated historical version-1 schema exists. The legacy-v1 SQL fixture is explicitly hand-audited. Before a later schema change, increment the database version and add a non-destructive migration. Test migration using exported historical schemas. Keep checkpoint versions separate from database versions and vault-envelope versions. Reject unknown payload versions until a migration exists.

Do not log passwords, entered typing content, decrypted backups or private profile data. Keep runtime files, tool installations, SDKs, generated JARs and APK build outputs outside Git. Keep release signing material outside source control.

## Dependency baseline

The project pins Kotlin/Compose plugin 2.2.21, AGP 8.13.1, Gradle 8.13, KSP 2.2.21-2.0.4, Compose BOM 2025.10.01, Activity 1.11.0, Lifecycle 2.9.4, Room 2.8.3, DataStore 1.1.7 and Coroutines 1.10.2. These are pinned intended-compatible versions, not a claim that they are the newest versions. Hosted native CI has resolved and compiled this baseline. The sandbox itself cannot download the full Android toolchain. Check official release notes, repository resolution and security advisories before release. Update a compatible group, then run the full regression matrix.

The sandbox Java runtime package uses Java 17.0.9 for isolated core tests. It is not an Android release toolchain recommendation. Use a maintained full JDK 17 in CI and local Android development.

## Build integrity

The Gradle wrapper scripts and JAR came from the Gradle v8.13.0 repository through the GitHub contents API. The wrapper JAR SHA-256 is `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`. Keep TLS validation enabled. Add official distribution checksum and dependency verification after successful resolution. Do not treat an unrun workflow as a passing check.

The standalone syntax utility uses a compiler API scheduled for change after Kotlin 2.2. Keep its compiler pinned until the utility is updated. It reports grammar errors only.

## Hosted evidence and timer tests

Run `python3 scripts/read-ci-evidence.py RUN_ID` from `neurox/` to retrieve check results, JUnit summaries, artifact links and APK hashes using the existing GitHub CLI connection. GitHub blob downloads may fail in restricted environments even when metadata works. The CI scripts therefore publish bounded diagnostics and numbered Room-schema chunks. Do not accept incomplete chunks. `--save-schema` requires successful native compilation on the current branch.

Compose/Espresso idle synchronization can drain an 8 ms reflex ticker until all unanswered rounds finish. Layout tests use an isolated database and disable automatic publication in their ViewModel fixture. This is test wiring only; production Activity factories use the default real ticker. `LiveTickerTest` covers real `SystemClock`, main-thread publication, waiting/active transitions, missed-target persistence and zero rewards without Compose idling. Keep both types of test. Never disable the production timer merely to obtain green UI tests.

Use explicit `: Unit` return types for coroutine expression-body JUnit 4 tests. An expression ending in a repository method can otherwise infer a non-void JVM return type and prevent the whole class from running. The static guard catches this known error pattern; native instrumentation remains required.

Use the APK SHA-256 for the exact successful run. Debug signing keys can differ between hosted runs. Do not recommend uninstalling an older build to bypass signature mismatch: that deletes local data, and the integrated export workflow is not ready. Test development builds on disposable devices or profiles. A signed release needs separately managed signing material and full acceptance evidence.
