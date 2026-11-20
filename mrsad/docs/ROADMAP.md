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
* Production vector mark — the **Geometric M** (six parallel metal plates):
  adaptive icon, monochrome layer, splash drawable, and the in-app mark, all
  generated from one geometry in `docs/branding/build_icon.py` and mirrored by
  the pure-Kotlin `dev.mrsad.core.brand.MrsadMark` so the app and the icon
  cannot drift apart.
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
* RSVP with 1–3 words/display, 120–900 wpm, pivot guides.
* 9 original texts: three subjects (concentration, cognition, growth) at three
  difficulty tiers, each with four questions answered by the text itself.
* Measured speed from monotonic elapsed time with pauses removed.
* Mandatory comprehension quiz; **effective WPM = raw × comprehension** so speed
  alone is never rewarded. Persisted as a READ activity.

### MRSAD MIND ✅ (implemented + unit-tested generators)
* 6 exercises: digit span, pattern memory, selective attention, Stroop,
  timed arithmetic, number-sequence logic — all procedurally generated with
  provable single correct answers.
* Real scoring, reaction times and history, persistence to the activity log.
* **Per-exercise difficulty ladder**: each exercise moves on its own evidence
  (two consecutive rounds ≥85 % up, two <50 % down, capped at 8), and the level
  is a pure function of the recorded scores, so it survives a restart.

## Phase 5 — Intelligence & analytics

* Assistant ✅ — offline intent routing + rule/statistical coaching over real
  data; refuses to analyse with <3 sessions (says so explicitly).
* Analytics ✅ — 7-day real aggregates, trends, module distribution, daily
  table, previous-week comparison. Every chart keeps one slot per day of the
  window, and a day without a measurement breaks the line instead of drawing
  a dip, so the chart never shows a session that never happened.
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

## Phase 8 — QA & release ✅

* CI pipeline: core JUnit gate → debug build + lint + unit tests → APK artifact
  → emulator smoke tests (Home renders, brand mark drawn, nav to Type, typing
  session, every control reachable by name, right-to-left layout, Settings
  applies a persisted theme). The runner executes one method per Gradle
  invocation under a timeout, so a hanging test cannot eat the whole workflow
  budget; a unit test compares that method list with the test class, so a
  method that is never executed fails the build instead of passing silently.
* Release APK: signed with a key held in the Actions cache (never in git),
  published as `mrsad.apk` + `SHA256SUMS.txt`.
* The accessibility floor is enforced by tests, not by eye: the colour system
  is checked against the WCAG contrast ratios, the corpora are checked for
  answer ranges and tier coverage, and every control reserves a 48dp touch
  target.
* Screen-reader semantics are enforced the same way. A source audit fails the
  build when an icon-only button loses its name, and an emulator test walks
  the real semantics tree and fails when a tappable control cannot be
  reached by name. The selected bottom-bar destination does not announce its
  label twice.
* The layout is right-to-left on every device. The direction of the root view
  follows the *system* locale, so the composition root forces it explicitly —
  the app is Persian only and must not depend on the phone's language. The
  directional icons (the back arrows and the send arrow) use the
  auto-mirrored variants, and an emulator test reads the real bounds of the
  bottom bar to prove the destinations run from right to left.
* The version shown on the Settings screen is read from the build, so it can
  never drift away from the released artifact.

---

## Known limitations (honest list)

1. **No on-device verification yet from this sandbox** — the sandbox cannot run
   Gradle; results come from the GitHub Actions pipeline in this repository.
2. `assembleRelease` currently has minification disabled; R8 rules must be
   validated on-device before enabling.
3. Analytics module distribution aggregates only the visible 7-day window (the
   streak and the personal records deliberately look further back).
4. Assistant intent routing is keyword-based; ambiguous questions fall back to
   the general analysis (by design, documented in the answer copy).
5. Persian text justification relies on platform shaping; long-word lines may
   stretch on very narrow screens.
6. Sound effects are disabled in the UI until a real audio engine is added
   (the setting exists but is greyed out — no fake toggle).
7. The Arena/Planner/Library screens are explicit placeholders.
8. The release key lives in the GitHub Actions cache, not in a repository
   secret: if that cache is evicted the signing identity changes and a new
   APK can no longer be installed over an older one.
