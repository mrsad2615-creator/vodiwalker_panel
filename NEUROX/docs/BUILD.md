# Building NEUROX SENSE

There are two ways to build this project, and they prove different things.

## 1. On GitHub Actions (how to get an APK)

Nothing to install. Push to `NEUROX/`, or press **Run workflow** on
[Build APK](../../actions/workflows/build-apk.yml). The run:

1. installs JDK 17 and Android SDK platform 35,
2. runs `./gradlew assembleDebug` in `NEUROX/`,
3. checks the APK's package id, `minSdk` and signature with `aapt2` and `apksigner`,
4. uploads it as a workflow artifact and publishes it to
   [`releases/latest`](../../releases/latest).

Download link: **https://github.com/mrsad2615-creator/vodiwalker_panel/releases/latest/download/NEUROX-sense-debug.apk**

The APK is a debug build with a throwaway key, so it installs but does not upgrade
over an existing copy. It is a way to try the app, not a distribution channel.

## 2. Locally

```bash
cd NEUROX
./gradlew assembleDebug            # APK in app/build/outputs/apk/debug/
./gradlew test                     # JVM unit tests, includes the 323 pure tests
./gradlew connectedAndroidTest     # needs a device or emulator
```

Requirements, matching `gradle/libs.versions.toml`:

| Component | Version | Note |
| --- | --- | --- |
| JDK | 17–21 | `jvmToolchain(17)` in every Android module |
| Android Gradle Plugin | 8.7.3 | |
| Gradle | 8.11.1 | via the wrapper, `gradle/wrapper/gradle-wrapper.properties` |
| compileSdk / targetSdk | 35 | |
| minSdk | 26 | Android 8.0 |
| Kotlin | 2.1.0 | with KSP `2.1.0-1.0.29` for Room |

The first build downloads Gradle, AGP, Compose, Room and Navigation; roughly 1–2 GB
into `~/.gradle`. It needs network access to `dl.google.com`, `repo.maven.apache.org`
and `services.gradle.org`. **This is exactly what the development sandbox cannot do**,
which is why `scripts/*.sh` exist as an offline substitute and why no APK existed
before the workflow was added.

## 3. A signed release build

The release variant is already configured for minification (`isMinifyEnabled = true`,
`isShrinkResources = true`) but **not for signing** — deliberately, because signing
keys do not belong in a repository.

To produce an installable release:

1. Create a keystore, kept outside the repo:

   ```bash
   keytool -genkeypair -v -keystore neurox-release.jks \
     -alias neurox -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Add these as repository secrets and pass them to the workflow as environment
   variables:

   | Secret | Contents |
   | --- | --- |
   | `NEUROX_KEYSTORE_BASE64` | `base64 -w0 neurox-release.jks` |
   | `NEUROX_KEYSTORE_PASSWORD` | keystore password |
   | `NEUROX_KEY_ALIAS` | `neurox` |
   | `NEUROX_KEY_PASSWORD` | key password |

3. Configure signing in `app/build.gradle.kts` from those variables, and switch the
   workflow's `assembleDebug` to `bundleRelease` (an `.aab` for Play) or
   `assembleRelease`.

**Back up the keystore and its passwords.** Losing them means the installed app can
never be updated again, only replaced.

`*.jks` and `*.keystore` are already in `NEUROX/.gitignore` so the key cannot be
committed by accident.

## 4. What the offline gates do

Without network, two scripts still verify the code:

```bash
bash NEUROX/scripts/verify_domain.sh    # compiles 8 pure modules, runs 323 tests
bash NEUROX/scripts/compile_android.sh  # compiles all 16 modules against API stubs
```

Run `verify_domain.sh` first: it deletes and rebuilds its work directory, so a
compile-gate run before it is wasted. These prove the code is consistent with the
API surface the stubs describe. They do **not** prove it builds under Gradle, that
Room's generated DAOs are valid, or that Compose behaves correctly at runtime. See
[`TEST_STATUS.md`](TEST_STATUS.md) for the full list of what is unverified.

## 5. If the Gradle build fails

It has never been run, so the first failure is expected and informative. Common
causes and where to look:

| Symptom | Likely cause |
| --- | --- |
| `Could not find androidx...` | a coordinate in `libs.versions.toml` was guessed, not published |
| `Unresolved reference` in a feature module | the stubs were more permissive than the real API (see DEC-0009) |
| KSP / Room errors | a `@Query` is invalid SQL, or an entity lacks a no-arg path |
| `Compilation failed` on an `R.` symbol | `tooling/gen_r.py` and the real resource tree disagree |
| `Duplicate class` | two BOM-managed Compose artifacts pulled at different versions |

Paste the first error, not the last: Gradle's last line is a summary and the first
error is the cause.
