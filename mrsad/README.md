# MRSAD — مرصاد

**زیست‌بوم رشد شناختی برای اندروید** · Persian-first · Offline-first · Kotlin + Jetpack Compose

---

## What this is

MRSAD is a single premium Android application that combines:

| Module | Status | What it really does |
|---|---|---|
| **MRSAD HOME** | ✅ working | Real dashboard: today's minutes/sessions, real streak, real records, data-driven daily plan, live "Core" object fed by measured activity |
| **MRSAD TYPE** | ✅ working | Persian & English typing trainer, 4 durations, 11 texts, live WPM/CPM/accuracy, wrong-letter analysis, persistent results |
| **MRSAD READ** | ✅ working | RSVP speed reading (1–3 words, 120–900 wpm), pivot guides, comprehension quiz, effective-WPM metric |
| **MRSAD MIND** | ✅ working | 6 real cognitive exercises with procedural generation, real scoring, reaction times, adaptive difficulty |
| **MRSAD AI** | ✅ working | Offline coaching: rule/statistics engine over your real data; says "not enough data" honestly |
| **MRSAD ANALYTICS** | ✅ working | 7-day trends, module distribution, daily table, previous-week comparison — all from your data |
| **MRSAD STUDIO** | ✅ working | 6 themes, live preview, font scale, graphics quality, reduce motion — applied app-wide instantly |
| **MRSAD ARENA** | ⬜ next phase | missions, achievements, levels (routed placeholder) |
| **FOCUS SPACE** | 🟠 engine ready | monotonic session state machine implemented & unit-tested; service+screen next |
| **Planner / Habits / Library** | ⬜ next phase | routed placeholders |

> The app declares **no INTERNET permission**. Everything is local.

---

## Visual identity

* **4 original concepts** in `docs/branding/` (Metallic M, Geometric M, Orbital M, Fusion M).
* Production mark: a vector **Fusion-M** (extruded metal + orbital ring) used for the
  adaptive icon, monochrome icon, splash and in-app brand.
* **6 themes**: Obsidian, Quantum, Crystal, Arctic, Phantom, Spectrum — semantic
  colour tokens, not hard-coded colours.
* Typography: **Vazirmatn** (UI) + **Estedad** (display/reading) — both SIL OFL 1.1,
  licences included in `app/src/main/assets/licenses/`.
* Every page has its own generated environment (lighting + optional particles)
  with quality/reduce-motion budgets.

---

## Architecture in one screen

```
core/   pure Kotlin — typing engine, Persian text normalization, reading engine,
        analytics math, coach rules, exercise generators, focus state machine.
        Fully unit-tested (38 executable checks), zero dependencies.

app/    Android — Compose UI, Room (typing_sessions + activity_events),
        DataStore settings, manual DI container, single NavHost.
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for details and rationale.

---

## Build

Requirements: **JDK 17**, Android SDK 35, Gradle wrapper included.

```bash
cd mrsad
./gradlew :core:test          # fast domain gate (~1 min)
./gradlew :app:assembleDebug  # debug APK
./gradlew :app:lintDebug      # Android lint
```

CI (`.github/workflows/mrsad-android.yml`) runs core tests → debug build +
unit tests + lint → uploads `mrsad-debug-apk` → runs instrumented UI tests on
an API 33 emulator.

---

## Install

1. Download the `mrsad-debug-apk` artifact from the latest green
   **MRSAD Android** workflow run (Actions → run → Artifacts).
2. `adb install -r mrsad-debug.apk` or copy to the device and open it
   (allow "install unknown apps" for your file manager).

---

## Privacy

* No network permission, no analytics SDK, no ads, no accounts.
* Training data lives in the app database; OS backup includes only the DB
  and settings so reinstalls keep your history.
* Deleting the app deletes all data.

---

## Honest status

`docs/ROADMAP.md` is the authoritative checklist of what is done, what is
implemented but unverified, and what is not built yet. Nothing in this README
is marked ✅ unless the CI pipeline in this repository proves it.
