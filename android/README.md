# NEUROX Android — foundation increment

This is a new native Android application isolated from the existing VodiWalker Python service in the repository root. It uses Kotlin, Jetpack Compose, Room, and offline local storage. It has no internet permission and does not call a remote service.

## Current implementation status

Implemented in this increment:

- Native Android/Compose project configuration (single `app` module, layered packages).
- Persisted English/Persian language switch, matched app string resources, and RTL/LTR layout direction.
- Persian and English offline prompt word sets for READ and TYPE.
- Functional appearance studio with distinct dark/light palettes, eleven accent presets, custom HEX accent input, and restore-default action; choices persist locally.
- Theme tokens keep accent selection separate from screen layout and exercise scoring.
- Home, training-module selection, analytics, and clearly marked early Profile/Library/Settings sections.
- Four small functioning ten-round practice flows (shape choice, color-word choice, word recognition, exact text entry).
- Local Room persistence for completed session summaries, with analytics based on those records.
- Pure-domain scoring, exercise prompt generation, and a bounded ten-round training state machine with unit tests.
- Monotonic elapsed and per-response timing; the REFLEX slice is a color-word interference task, and conservative difficulty adjustment requires a five-response window.
- Session response-time summaries persisted with an explicit Room migration path.
- Explicit save-in-progress and save-failure/retry states; results are not labeled saved before the database insert succeeds.
- No fabricated progress; empty analytics and no-history states are explicit.

Not implemented yet: Persian translations and RTL switching, light theme/Color Studio, proper exercise-specific engines and settings, baseline assessment, full analytics filters/charts, adaptive history and plan management, reading library/import, keyboard statistics and WPM sessions, profiles, gamification, audio/haptics, encrypted backup/restore, full interruption/process-death recovery, icon/brand asset suite, and release/test-device validation. The visible sections with incomplete capabilities state this rather than pretending they work.

This is a first foundation slice, not the completed product and not a release APK.

## Build

Requirements: JDK 17, Android SDK Platform 35, Android Build Tools compatible with Android Gradle Plugin 8.7.3, and Gradle 8.9 or a compatible 8.x version.

From this directory:

```sh
gradle testDebugUnitTest
gradle assembleDebug
```

The current execution environment does not have Java, Gradle, Android SDK, or `adb`, so Android compilation, JUnit execution, and device tests could not run here. As an alternative static check, I installed a Kotlin Tree-sitter grammar into a temporary environment and parsed every Kotlin source and test file; all parsed without syntax errors. This is only a syntax check. It does not type-check Android/Compose APIs or execute tests. The manifest XML also parses successfully. Do not treat the source as a verified release until Gradle tests and an Android build run in an Android-capable environment.
