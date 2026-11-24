# NEUROX Android — current foundation build

The native Android application is isolated under `android/`; it does not connect to the existing VodiWalker Python service. The app has no internet permission and stores session/preference data locally.

## Implemented

- Kotlin, Jetpack Compose, Material 3 foundations, Room, and DataStore.
- Persisted English/Persian language switch and RTL/LTR layout direction. Bundled prompt words are available in both languages.
- Functional appearance studio: distinct dark/light palettes, 11 accent presets, custom six-digit HEX accent, persisted selection, and restore-default action.
- Home dashboard and real local session history with explicit empty states; no fabricated level or performance values.
- Four distinct ten-round practice flows: visual shape search, color-word interference with monotonic response timing, word recognition, and exact-text typing.
- Unit-tested domain scoring, WPM formula, difficulty bounds, response counting, and session completion.
- Room persistence, a versioned migration for response-time metrics, and save progress/error/retry UI.
- CI workflow builds and tests the Android app on a GitHub-hosted Android environment, then publishes a debug APK artifact.

## Not complete

This foundation is not the full NEUROX specification. Remaining major work includes the complete exercise catalog and configurable engines, baseline assessment, saved/interruption-safe sessions, full analytics filters/charts, adaptive multi-session planning, reading library/import, typing WPM and weak-key analysis, profiles and isolation, gamification, audio/haptics, encrypted backup/restore, local font/icon suite, accessibility/device testing, and a signed release APK.

Do not treat a successful debug build as proof that all features are complete or that the app is production-ready.

## Build locally

Requirements: JDK 17, Android SDK Platform 35, Android Build Tools 35.0.0, and Gradle 8.9.

```sh
gradle --no-daemon testDebugUnitTest assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. The GitHub Actions run publishes `neurox-debug-apk` as a downloadable artifact for 90 days. The artifact is debug-signed and is for installation/testing, not production distribution.
