# Test status

**Last updated with evidence in `docs/evidence/`.**

This file exists to stop anyone (including the author) from overstating what has been
verified. Every claim below has a command you can re-run and an evidence file you can
open.

## Summary

| Check | Command | Result | Evidence |
| --- | --- | --- | --- |
| Pure-layer tests | `bash scripts/verify_domain.sh` | **323 / 323 pass** | `evidence/VERIFY_RUN_14.txt` |
| Android-module compile | `bash scripts/compile_android.sh` | **16 / 16 modules, 0 diagnostics** | `evidence/COMPILE_RUN_08.txt` |
| Gradle build (`assembleDebug`) | `./gradlew assembleDebug` | **NOT RUN** | blocked, see below |
| Instrumented / UI tests | `./gradlew connectedAndroidTest` | **NOT RUN, NOT WRITTEN** | requires a device |
| APK on a device | - | **NOT PRODUCED** | - |

## What the 323 tests cover

The pure modules (`core:common`, `domain`, `games`, `security`, `audio`,
`presentation`) are compiled and tested on a plain JVM.

- **Domain rules**: level curve and XP, streak counting, adaptive difficulty
  adjustment with its minimum-sample guard, weak-skill detection, recommendation
  determinism, session record derivation, baseline exclusion from analytics.
- **Session engine**: phases, idempotent start, expiry → miss, answer only while
  running, pause extends deadlines and is excluded from active duration, early answer
  counted as premature, summary stability, safety at any frame rate, wall-clock
  changes not affecting elapsed measurement.
- **Games/content**: exercise catalogues, stimulus generation, reading content
  integrity, response-window honouring, no-go trials carrying no grid.
- **Security**: AES-256-GCM round trip, PBKDF2 derivation, wrong password rejected
  without yielding data, tampering detected (header, payload, checksum), truncation,
  version handling, PIN hashing.
- **Vault**: export/restore round trip, header readable without a password, fingerprint
  comparison, corrupt and truncated files rejected, content validation.
- **Audio policy**: per-channel independence, silent mode, focus mode, spacing rules,
  background suppression, gain quantisation.
- **Presentation**: string catalogue parity and key contract, screen-state builders,
  number/digit localisation, civil-date arithmetic, result presentation.
- **Navigation**: destination uniqueness, bottom-bar membership, immersion rules,
  route resolution, label-key existence in both languages.

## What the compile gate covers

`scripts/compile_android.sh` compiles all sixteen modules in dependency order against
`tooling/stubs/` - hand-written declarations of the Compose/Android API surface the app
uses - with each module visible to the next.

A module passes only when the compiler emits **zero diagnostics**. This catches:

- Missing imports and references between NEUROX types.
- Type errors, wrong parameter names, wrong argument counts.
- Dependency cycles (a feature module importing the application module).
- References to resources that do not exist (`R.<type>.<name>` generated from `res/`).
- Ambiguous or shadowed declarations.

It already found 25 real defects in code that the previous filtered audit reported as
clean, including a dependency cycle across every feature module, ~150 imports pointing
at packages that do not exist, a repository constructed without a required DAO, and a
startup path that could never complete.

## What is NOT verified - read this before shipping

1. **No Gradle build has run.** The environment cannot reach Google Maven
   (`dl.google.com`), Maven Central or `services.gradle.org`, so AndroidX, Compose,
   Room and the Android Gradle Plugin cannot be resolved. `./gradlew assembleDebug` is
   expected to work on a normal machine, but that is an expectation, not a result.
2. **No APK exists.** Nothing has been installed on a device or emulator. Startup,
   navigation, rendering, fonts and audio have never been observed running.
3. **The stubs are hand-written.** They mirror the real signatures for the calls this
   app makes, but a stub that is wrong in the permissive direction lets a call through
   that Gradle would reject. The stub set is the largest single risk in this repo.
4. **Room SQL is not checked.** Room's annotation processor generates and validates
   DAO code; without it, the `@Query` strings are unverified text. A typo there compiles
   here and fails at runtime.
5. **Compose semantics are unchecked.** Recomposition counts, effect keys, stability,
   `remember` scoping and layout behaviour are invisible to a compiler.
6. **No Compose UI tests exist.** Screens have never been rendered.
7. **Accessibility was designed for, not measured.** Semantic descriptions are present
   in the code, contrast ratios are computed in `ThemeEngine`, and text scaling is
   threaded through the theme - but no TalkBack session or contrast audit has been run
   on a device.
8. **Audio has never been played.** The renderer's threading was rewritten after review
   (bounded queue instead of wait/notify), but nothing has confirmed that a cue is
   audible.
9. **Timing calibration is unvalidated on hardware.** The subtraction logic is tested;
   the measured input/display latencies are estimates from settings, not measurements
   taken against a reference device.
10. **Localisation is asserted, not reviewed.** Catalogue parity is tested; whether the
    Persian reads naturally to a native speaker has not been checked.

## How the numbers were produced

```
Total: 323   Passed: 323   Failed: 0   Skipped: 0      # VERIFY_RUN_14.txt
RESULT: every Android module compiled with zero diagnostics.   # COMPILE_RUN_08.txt
```

Both scripts fail loudly: a compile error exits non-zero, a failed test is listed and
counted, and the compile gate refuses to report anything at all if the stubs
themselves fail to compile.

## Known gaps to close before a release

- Run `./gradlew assembleDebug` and `./gradlew test` on a machine with network access.
- Write Compose UI tests for the session surface and the four onboarding steps.
- Run a TalkBack pass and a contrast audit on a device.
- Validate the DAO SQL with Room's processor (it runs as part of the Gradle build).
- Measure real input and display latency on at least two devices and record the error
  band in the app's own timing note.
