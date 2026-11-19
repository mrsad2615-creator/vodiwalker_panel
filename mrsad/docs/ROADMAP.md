# MRSAD — Roadmap & Delivery Status

This file is the single source of truth for "what actually works".
It is updated at every phase; nothing is marked ✅ before CI proves it.

Legend: ✅ done & verified · 🟡 implemented, awaiting verification · 🟠 partial · ⬜ not started

---

## Phase 1 — Product analysis & architecture ✅

* Environment audit: Maven Central / Google Maven / services.gradle.org are
  unreachable from the sandbox, so the authoritative build runs on GitHub
  Actions (Gradle 8.11.1 + JDK 17 + AGP 8.7.3 + Kotlin 2.0.21).
* Portable toolchain bootstrapped locally (JDK 17 via PyPI `jdk4py`,
  Kotlin 2.0.21 compiler via npm) → domain core compiled and executed locally.
* Architecture: `core` (pure Kotlin) + `app` (Compose/Room/DataStore), see
  `ARCHITECTURE.md`.

## Phase 2 — Brand & visual system ✅

* 4 original logo concepts (AI-rendered, `docs/branding/concept-*.png`).
* Production vector mark (Fusion-M with orbital ring): adaptive icon,
  monochrome layer, splash drawable.
* Semantic colour system for 6 themes (Obsidian, Quantum, Crystal, Arctic,
  Phantom, Spectrum) with light/dark aware system bars.
* Typography: Vazirmatn (UI) + Estedad (display/reading), both SIL OFL 1.1,
  licences shipped in `assets/licenses/`.
* Tokens for spacing, radii, motion; one component implementation each.
* Persistent page environments with quality/reduce-motion budgets.

## Phase 3 — Core application ✅

* Splash → main shell; Persian default locale, RTL, per-app language config.
* Five-destination navigation (Home / Type / Mind / Analytics / Assistant)
  + routed Studio, Settings, Read, Arena, Focus, Planner, Library.
* Real Home dashboard: today's minutes/sessions, real streak, real personal
  records, data-driven daily plan, live Core object (energy = goal progress,
  sparks = streak). Honest empty state with a working CTA.
* Settings: 6 themes, quality levels, Persian numerals, haptics, reduce motion —
  every toggle persists in DataStore and applies instantly.
* Studio: live preview using the same core object + theme/tint/font-scale/quality
  controls.

## Phase 4 — Training modules

### MRSAD TYPE ✅ (implemented + unit-tested engine)
* Persian & English, timed modes (30/60/120/180 s), 11 curated texts.
* Engine rules tested in `:core`: no cursor advance on error, backspace never
  erases error history, ZWNJ accepts Space, honest accuracy formula.
* Monotonic timing; pause freezes the clock; zero-keystroke runs are never saved.
* IME bridge: diffing captures real Persian soft-keyboard input and hardware keys.
* Results: word-WPM, CPM, accuracy, errors, hardest letters — persisted to Room.

### MRSAD READ ✅ (implemented + unit-tested engine)
* RSVP with 1–3 words/display, 120–900 wpm, pivot guides, 3 original texts.
* Measured speed from monotonic elapsed time with pauses removed.
* Mandatory comprehension quiz; **effective WPM = raw × comprehension** so speed
  alone is never rewarded. Persisted as a READ activity.

### MRSAD MIND ✅ (implemented + unit-tested generators)
* 6 exercises: digit span, pattern memory, selective attention, Stroop,
  timed arithmetic, number-sequence logic — all procedurally generated with
  provable single correct answers.
* Real scoring, reaction times, adaptive level (≥85 % up, <50 % down), history,
  persistence to the activity log.

## Phase 5 — Intelligence & analytics

* Assistant ✅ — offline intent routing + rule/statistical coaching over real
  data; refuses to analyse with <3 sessions (says so explicitly).
* Analytics 🟡 — 7-day real aggregates, trends, module distribution, daily
  table, previous-week comparison. (One aggregate path still needs CI/runtime
  verification after the current fix cycle.)
* Daily plan engine ✅ — deterministic per date, weakness-first.

## Phase 6 — Additional features ⬜

* ARENA (missions/achievements/levels) — routed placeholder.
* FOCUS SPACE — the monotonic session engine exists and is tested; the
  foreground service + screen are next.
* Planner, Habit tracker, Personal library — routed placeholders.
* Smart reminders (WorkManager) — dependency included, not wired.

## Phase 7 — Integration 🟠

* Everything already writes through one activity log, so integration is
  architectural rather than a later migration. Two features remain stubbed.

## Phase 8 — QA & release 🟡

* CI pipeline: core JUnit gate → debug build + lint + unit tests → APK artifact
  → emulator smoke tests (Home renders, nav to Type, Settings opens).
* Release APK: `assembleRelease` (unsigned) is planned once debug is green.

---

## Known limitations (honest list)

1. **No on-device verification yet from this sandbox** — the sandbox cannot run
   Gradle; results come from the GitHub Actions pipeline in this repository.
2. `assembleRelease` currently has minification disabled; R8 rules must be
   validated on-device before enabling.
3. Analytics module distribution aggregates only the visible 7-day window.
4. Assistant intent routing is keyword-based; ambiguous questions fall back to
   the general analysis (by design, documented in the answer copy).
5. Persian text justification relies on platform shaping; long-word lines may
   stretch on very narrow screens.
6. Sound effects are disabled in the UI until a real audio engine is added
   (the setting exists but is greyed out — no fake toggle).
7. The Arena/Focus/Planner/Library screens are explicit placeholders.
8. Release signing must be configured with the owner's keystore.
