# Test plan

## Available local suite

Run `scripts/test-core.sh`. Compile production domain and cipher sources with Kotlin 2.2.21 and JVM target 17. Treat compiler warnings as failures. Use a fake monotonic clock and seeded random source for deterministic engine tests. Test valid and invalid inputs, all eight exercise paths, pause/resume, recovery, scoring, WPM, Unicode, adaptive conservatism, schedule conflicts, progression and accent contrast. Test encryption round trips and rejection of wrong passwords, tampered headers, corrupted tags, truncation, unknown versions, invalid costs and unsafe sizes.

Run `scripts/check-kotlin-syntax.sh`. Parse Kotlin and Gradle Kotlin files. This is syntax-only verification. It does not resolve Android APIs.

Run `scripts/check-project.py`. Parse XML and the pre-existing Python sources. Check that original Python files match the base commit. Check offline manifest and selected data isolation invariants. Static string checks are not integration or security proofs.

## Android integration tests supplied, not run here

`core/data/src/androidTest/.../RepositoryTest.kt` covers idempotent completion, profile histories, cross-profile mutation rejection, aborted sessions, duplicate active sessions, cascade deletion, stale checkpoints checkpoint codec versions, immutable configurations and cross-profile theme ownership. It uses an in-memory Room database.

`app/src/androidTest/.../NavigationTest.kt` covers home-to-exercise execution, pause and an empty analytics state. Run it on a clean install. Reset app data between tests when adding independent fixtures.

## Required release matrix

* API 26, current mid-range API and API 36.
* Low-end, mid-range and flagship physical Android devices.
* Persian RTL and English LTR; mixed text and numbers.
* Default and maximum system font scale; profile scale up to 150%.
* TalkBack, Switch Access, physical keyboard and at least two IMEs.
* Airplane mode from cold start through all core workflows.
* Rotation, process kill, incoming call, screen lock and audio focus interruption.
* Disk-full errors, interrupted writes, malformed imports and failed restore commit.
* Theme save/rename/delete, separate variants and contrast of all states.
* Account/profile boundary and PIN bypass tests after PIN implementation.
* Database migration from every supported schema version.
* Frame timing, first-pixel stimulus latency, input timestamp jitter and refresh rates.
* Startup, navigation, memory, battery and database-query latency under large histories.

A successful build does not satisfy this matrix. Record device model, OS, refresh rate, IME, accessibility state, seed and build version with measurement reports. Do not claim 60 FPS or high-refresh support before measurement.

## Expansion regression suite

`ExerciseExpansionTests.kt` adds seeded board generation checks, GO/NO-GO balance and false alarms, non-skipping phrase timing, real display-time WPM, word-by-word batch completion, per-language inputs, bounded draft sizes and old input revision rejection. A cross-product driver completes all eight exercises in both languages and all supported levels. A second driver recreates each exercise from its completed-round checkpoint.

Additional Android codec/repository tests cover legacy version-1 input, version-2 round trips, new exercise identifiers, extreme valid seeds and saved correct inhibition. `ExerciseUiTest` supplies in-memory database fixtures for Visual Search, Phrase Reading and Word Sprint. These are test sources, not executed test evidence in this environment.
