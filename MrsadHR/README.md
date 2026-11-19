# MrsadHR · Temporal Instruments

Standalone native Android project, independent of the Python panel in the repository root. Requires Android 8.0 or newer. No network permission, accounts, tracking or remote data stores.

## Install and build

Open **MrsadHR/** (not the parent directory) in Android Studio Ladybug or newer. Use JDK 17 and install SDK Platform 35. From this directory:

```sh
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew connectedDebugAndroidTest  # start an API 33 emulator with an unlocked screen first
```

Windows: use `gradlew.bat`. On GitHub, **Actions → MrsadHR Android → run → Artifacts → MrsadHR-debug** contains the installable APK. Unzip before installing. `./gradlew assembleRelease` builds the minified unsigned release. Use Android Studio’s Generate Signed Bundle / APK with your own private signing key for distribution. No release key is stored in source.

## Operation

Choose Countdown or Stopwatch. Tap a quick duration or **Edit time** for hours, minutes and seconds. **Save preset** stores a named interval. **Edit** beside a saved interval allows changing its duration/name or deleting it. Start enters focus mode by default; **Exit focus** reveals the studio without pausing the clock. Both instruments keep independent state. The three header shortcuts open materials, sounds and settings. Preferences include English / فارسی and reduced motion.

## Timing and power contract

`Clock` is an immutable model: elapsed = banked duration + monotonic time since start. Pausing banks this difference once. Rendering never advances time. Transport changes are persisted with the Android boot counter; process recreation within the same boot restores the anchor. A reboot resets running instruments rather than reusing an unrelated timebase. Stopwatch laps store cumulative elapsed milliseconds. Only the visible running readout requests samples.

A user-started `specialUse` foreground service owns countdown expiry and the system chronometer notification. A single elapsed-realtime wake-up alarm handles device sleep; it is cancelled on reset, pause and completion. The app's core timer functionality needs exact delivery in idle mode: API 33+ uses the dedicated timer-app `USE_EXACT_ALARM` permission. API 31–32 uses an inexact fallback when exact access is unavailable; the alert **can arrive late** then, but remaining time stays correct. Android can also delay or suppress alerts after force-stop, with restricted battery policies, notification denial or audio focus denial. Force-stop is not a supported background execution mode. No busy wake lock or polling notification loop.

## Code and assets

- `Clock.kt`: pure monotonic transport and lap arithmetic.
- `Store.kt`: Room preset DAO, DataStore preferences, transport snapshots and ViewModel.
- `TimerService.kt`: foreground lifetime, alarms, notifications, haptics and audio focus.
- `Material.kt`, `Atmosphere.kt`: finish tokens, static light fields, engraved dial and Motion Engine M1.
- `MainActivity.kt`, `Centers.kt`, `Language.kt`: lifecycle-aware instrument, editors, libraries and bilingual controls.
- The four WAV cues and Lottie resonance animation were authored for this app. Fonts: Space Grotesk and Vazirmatn, SIL Open Font License; see `licenses/`.

## Verification beyond CI

Before release, compare a long interval against an external clock across screen lock, Doze, process recreation, and wall-clock changes. Exercise notification denial, audio interruption, font scaling, TalkBack, Persian RTL, and each finish on physical devices. Emulator tests are not proof of OEM-specific power behavior or sustained 60 FPS.
