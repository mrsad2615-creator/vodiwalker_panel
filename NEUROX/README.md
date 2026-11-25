# NEUROX SENSE

An offline-first cognitive training app for Android, in Persian and English, built as a
Gradle multi-module Kotlin project.

**This README states what is verified and what is not. Read `docs/TEST_STATUS.md`
before trusting any number in here.**

## What it does

Four training modules, each with its own exercises, measurement and analytics:

| Module | Trains | Example exercises |
| --- | --- | --- |
| **VISION** | peripheral detection, visual memory, spatial memory | focus lock, peripheral sweep, flash recall |
| **REFLEX** | reaction speed, inhibitory control | simple reaction, go/no-go, sequence |
| **READ** | reading speed, comprehension | RSVP reader, focus guide, comprehension questions |
| **TYPE** | motor precision, rhythm | typing drills, rhythm alignment |

Around them: multi-profile isolation, an adaptive difficulty engine, weekly plans and
streaks, progress analytics, an exercise library with a builder, and encrypted
backup/restore.

## Honesty rules that shaped the code

These are not aspirations; each one is enforced somewhere in the code or the tests.

1. **The app never claims medical benefit.** It measures task performance. A session
   summary reports accuracy, response time and missed signals for *this session*, and
   the UI says so. There is no "IQ", no diagnosis, and no health claim to be found in
   the string catalogues.
2. **Timing is reported with its limits.** Touch latency and display latency are
   measured on the device and subtracted, and the recording states that the result is
   an estimate with a stated error band. A reaction time that cannot be trusted is not
   shown as if it could.
3. **Nothing is manufactured for a nicer chart.** A metric with too few samples shows
   "not enough data" rather than a number derived from two attempts.
4. **Persian is not a translation layer over an English UI.** The catalogues are in
   parity (asserted by a test, currently 564 keys each), the layout direction is
   derived from the language, and digits are localized except for measured values,
   which stay Western so a stopwatch is never ambiguous.
5. **A backup file reveals nothing about its owner.** The encrypted envelope contains
   no profile name or session count, so a lost file cannot identify anyone.

## Architecture

```
app                     single activity, navigation, DI container
 ├── feature/*          one module per screen area (11 modules)
 ├── core:presentation  pure JVM: string catalogues, screen state builders, session controller
 ├── core:designsystem  Compose theme, tokens, components, fonts
 ├── core:data          Room, preferences, repository implementations
 ├── core:domain        models, rules, repositories (pure JVM)
 ├── core:games         exercise content and engines (pure JVM)
 ├── core:audio         synthesis and the playback policy (pure JVM + one platform file)
 ├── core:security      vault crypto, PIN hashing (pure JVM + one platform file)
 └── core:common        result types, clock, JSON, math helpers
```

Three rules hold this shape together:

- **Features do not depend on `app`.** Screens take `com.neurox.presentation.di.AppServices`,
  an interface the application module implements. Before this was fixed, every route
  imported `com.neurox.app.di.AppContainer` - a dependency cycle that only "compiled"
  because the build check of the time ignored it.
- **`core:presentation` is pure JVM.** No Android, no Compose. That is what lets the
  string catalogues, the screen-state builders and the session controller be unit
  tested on a plain JVM, and it is enforced by the offline gate.
- **Platform code is quarantined.** Android-only files live in `platform/` (audio) or
  `keystore/` (security) packages so the offline verifier can exclude exactly those
  files and nothing else.

`docs/ARCHITECTURE.md` has the details, including the repository-stream contract.

## Building

```bash
./gradlew assembleDebug        # requires network: Google Maven, Maven Central
./gradlew test
```

The Gradle build is the authoritative build and **has not been run in this
environment** (see below).

## Verification in this environment

Two scripts, both runnable with the Kotlin compiler and a JDK and nothing else:

```bash
bash scripts/verify_domain.sh     # compiles the 8 pure modules, runs 323 tests
bash scripts/compile_android.sh   # compiles all 16 modules against API stubs
```

Latest results, with the evidence files in `docs/evidence/`:

| Gate | Result | Evidence |
| --- | --- | --- |
| `verify_domain.sh` | **323 / 323 tests pass** | `VERIFY_RUN_14.txt` |
| `compile_android.sh` | **16 / 16 modules, zero diagnostics** | `COMPILE_RUN_08.txt` |

### What `compile_android.sh` is, and is not

The sandbox cannot reach Google Maven, so `androidx.compose.*`, Room, Navigation and
the Android framework cannot be resolved. The script compiles every Android module
against `tooling/stubs/`: hand-written declarations of the API surface this app uses,
in their real packages, with the real names and parameter types.

It is a real compile - a missing import, a type error, a wrong parameter name, a
dependency cycle, or a reference to a resource that does not exist all fail it. It
already found 25 defects that the previous filtered "audit" reported as clean.

It does **not** prove:

- Compose runtime behaviour: recomposition, effect keys, stability, layout.
- Room's generated DAOs, or that the SQL in the DAOs is valid.
- That a stub matches the real library signature exactly. Where a stub is wrong, the
  gate can pass code Gradle would reject.
- Resource linking beyond `R.<type>.<name>` existence.

Only `./gradlew assembleDebug` proves those. **No APK was produced in this
environment**, and none of the numbers above should be read as "it runs on a device".

## Repository layout

```
NEUROX/
├── app/                     application module
├── core/                    the eight pure modules
├── feature/                 eleven feature modules
├── tooling/
│   ├── stubs/               Compose/Android API declarations for offline compilation
│   └── gen_r.py             generates R from res/ so resource references are checked
├── scripts/
│   ├── verify_domain.sh     pure layers: compile + 323 tests
│   └── compile_android.sh   all modules against the stubs
└── docs/
    ├── ARCHITECTURE.md
    ├── DECISIONS.md
    └── TEST_STATUS.md       exactly what has and has not been verified
```

## Licensing of bundled material

Vazirmatn fonts are bundled under the SIL Open Font License 1.1; the licence text is in
`app/src/main/assets/licenses/`. All reading passages are original NEUROX content. No
third-party copyrighted text is included.
