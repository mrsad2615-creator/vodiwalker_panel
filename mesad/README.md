# MESAD — THE ART OF TIME

A dedicated, independent product workspace inside this repository; the existing Vodiwalker panel source remains untouched.

## Local Live Studio

```bash
cd mesad-studio
npm ci
npm test
npm run dev
```

Vite listens on `0.0.0.0:5173` and renders actual Three.js WebGL geometry (metallic orbit, faceted sapphire, time hands, and three distinct clock engines). The live studio includes local-first alarm, timezone city, monotonic stopwatch/countdown, calendar, reminder, theme, settings, and material controls.

## Android app

`mesad-android/` is a native Kotlin/Jetpack Compose Android Studio project, with a real OpenGL ES 3D clock surface and Compose Canvas Performance Mode fallback. On an Android SDK machine with JDK 17 and Gradle 8.11.1:

```bash
cd mesad-android
gradle testDebugUnitTest assembleDebug
```

GitHub Actions builds the Android debug APK and runs domain tests, and separately executes the web verification suite/build.

## Verification limits

The current agent sandbox has Node and Python but no JDK, Android SDK, or Gradle, and its network policy blocks the Google Android SDK download endpoints. Therefore the Android APK is **not claimed as locally compiled or device-tested here**. Run the repository workflow or the command above on an Android SDK-equipped host to perform that verification. The interactive WebGL build and tests are locally executable and verified.

Brand icon suite and export script are in `mesad-assets/icons/` and `scripts/generate_mesad_icon_suite.py`. Product requirements, design tokens, architecture, and platform decisions are documented in `mesad-docs/`.
