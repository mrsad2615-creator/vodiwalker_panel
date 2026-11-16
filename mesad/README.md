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

GitHub Actions builds the Android debug APK and runs domain tests, and separately executes the web verification suite/build. Latest verified build: commit `497b412` (workflow run [43939238578](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/43939238578)). Both Android unit tests and debug APK assembly passed, as did the WebGL studio tests and production build. The release workflow can publish the APK as a public GitHub Release asset for direct download.

## Download and install

Use the direct `app-debug.apk` download link in the latest MESAD release. Transfer the APK to an Android device and open it. If Android prompts you, allow your file/browser app to install unknown apps, then confirm installation. This is a debug-signed build; Android may show a standard warning for apps installed outside Google Play.

To build again locally, use an Android SDK-equipped host with JDK 17 and Gradle 8.11.1:

```bash
cd mesad-android
gradle testDebugUnitTest assembleDebug
```

## Verification limits

The sandbox itself has no JDK, Android SDK, or Gradle, so it did not compile the APK locally. The GitHub Actions runner did compile it and pass the JVM unit tests. No emulator or physical-device QA is claimed; exact-alarm permission handling, notification behavior, boot rescheduling, adaptive launcher icon appearance, and frame-time/battery targets still need device verification. The interactive WebGL build and tests also pass in CI.

Brand icon suite and export script are in `mesad-assets/icons/` and `scripts/generate_mesad_icon_suite.py`. Product requirements, design tokens, architecture, and platform decisions are documented in `mesad-docs/`.
