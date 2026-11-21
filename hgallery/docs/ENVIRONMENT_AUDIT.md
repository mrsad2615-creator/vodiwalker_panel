# ENVIRONMENT_AUDIT.md

Verified on 2026-11-21 inside the Arena sandbox, before any code was written.
Every line below was tested, not assumed.

## 1. What exists here

| Capability | Status | Evidence |
|---|---|---|
| Bash / git / gh (authenticated) | available | `gh auth status` → logged in as `mrsad2615-creator` |
| JDK (local) | **only via pip** `jdk4py` → Temurin 25 | `java -version` → `openjdk 25.0.5.1` |
| Local Kotlin compiler | downloadable from GitHub releases | `kotlin-compiler-2.0.21.zip` fetched |
| Local Android SDK (sdkmanager, AAPT2, D8) | **absent**, cannot be installed | `dl.google.com` unreachable |
| Gradle distribution | **absent**, cannot be installed | `services.gradle.org` unreachable |
| Maven Central / Google Maven | **blocked** | HTTP 000 on `repo1.maven.org`, `dl.google.com/dl/android/maven2` |
| Python Pillow + NumPy | available | PIL 13.0.0, NumPy 2.4.6 |
| Emulator / physical device | **absent** | no `/dev/kvm`, no ADB server, no USB |
| Outbound hosts | github.com, codeload.github.com, api.github.com, registry.npmjs.org, pypi.org (only) | curl probes |

## 2. Consequence

Android dependencies (AndroidX, Compose, AGP) can only be resolved where Maven
access exists. In this sandbox it does not. **Therefore the APK cannot be
compiled locally.**

The only reachable environment with both a JDK and an Android SDK is
**GitHub Actions**. It was verified empirically:

* `Domain core (pure Kotlin tests)` → **success**
* `Build debug APK` (assembleDebug + testDebugUnitTest + lintDebug) → **success**
* debug APK uploaded as workflow artifact `hgallery-debug-apk`

Run id: 44954974228 (branch `arena/09657e4c-vodiwalker-panel`).

## 3. Feasible local loop — **none for Kotlin**

An attempt was made to obtain the Kotlin compiler from the GitHub release
(`kotlin-compiler-2.0.21.zip`). The request redirects to
`release-assets.githubusercontent.com`, which is **not** in the allowlist:

```
curl: (35) OpenSSL SSL_connect: SSL_ERROR_SYSCALL in connection to
release-assets.githubusercontent.com:443
```

So neither `kotlinc` nor an Android SDK can be obtained locally. **Every
compilation and every test in this project runs in GitHub Actions.** The
feedback loop is therefore ~5–7 minutes per iteration, which is why changes are
batched and verified together rather than guessed at.

What *is* local and trustworthy: Python (Pillow/NumPy) for reference-image
measurement, and `git`/`gh` for driving the CI runs.

## 4. What is genuinely missing

| Missing | Impact | Honest alternative |
|---|---|---|
| Physical POCO X3 Pro | **cannot measure 120 Hz, thermal or battery behaviour** | run on a CI emulator and report frame metrics from `FrameMetrics` there, clearly labelled as emulator data |
| Android Studio / Layout Inspector | no interactive inspection | Compose previews + on-device screenshot dumps |
| Multi-model orchestration | this agent is a single model; it cannot invoke other models | perform *separate* research, implementation, audit and verification passes, as the brief permits when only one engine exists |
| Installing the APK | cannot install on the user's phone | deliver a signed APK artifact the user installs |

No claim of a physical-device measurement will be made anywhere in this project.
