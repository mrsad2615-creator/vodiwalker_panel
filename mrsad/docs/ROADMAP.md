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
  published as `mrsad.apk` + `SHA256SUMS.txt`. The workflow reads the key
  from the `MRSAD_KEYSTORE_BASE64` repository secret whenever that secret
  exists and falls back to the cache otherwise, so the signing identity can
  survive a cache eviction. Every release publishes the SHA-256 fingerprint
  of its signing certificate.
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
* A published release cannot be changed. GitHub makes a release immutable
  once it is published, and an immutable release refuses new assets with
  `Cannot delete asset from an immutable release` — so the assets are
  uploaded into a **draft**, and the draft is published only after both
  files are there. The version number is the name of the release, which
  means a new build is a new version number, never a second attempt at an
  old one. The files go up over HTTP/1.1, because the upload host drops
  HTTP/2 streams often enough to end the transfer with an EOF.
* The release points at the commit that was built (`--target`), so the tag
  is proof of what is inside the APK rather than a name.

## Phase 9 — Interface quality ✅

The app was technically correct and visually dead: a flat vertical
gradient, one drifting particle sheet, and a "core" that was a drawing of
a core rather than a modelled one. This phase replaces the visual
vocabulary of the app and measures the result.

### The motion system

* `ui/theme/Motion.kt` holds the published Material 3 duration and easing
  tokens and the Material 3 Expressive springs, split into the **spatial**
  family (things that occupy space and may overshoot) and the **effects**
  family (things that only fade and must never overshoot). The two are
  never mixed.
* `MrsadTheme.motion` is the only place that decides how the interface
  moves: the user's reduce-motion setting, the device's graphics tier and
  the motion scheme all resolve there.
* Reduced motion switches the spatial family off and keeps a short, calm
  fade; the instrumentation hook takes the same path, so the tests can
  still reach idle.
* Every duration, easing and spring in the app now comes from this file —
  there is no second, ad-hoc vocabulary left.

### The 3D core

* `ui/components/Core3D.kt` is a hand-written renderer: a 20-facet
  icosahedral shell around a lit nucleus, two orbital rings split at the
  depth of the body, a key light, a fill light, a specular term, a rim
  term and a real perspective projection. No bitmap, no 3D library, no
  fake data.
* The geometry, the winding of the facets, the depth ordering and the
  projection are checked by `CoreGeometryTest` — on the JVM, without a
  device.
* The object reacts to the finger: it turns with a drag, keeps travelling
  after a fling, and answers a tap with a shockwave.
* What it shows is real: the nucleus is today's goal progress, the sparks
  are the streak, and the graduations of the outer ring are the personal
  record.

### The material

* `MrsadSurface` builds a card in five layers — shadow, gradient, sheen,
  bottom shade and a fading hairline — instead of a flat panel with a
  uniform border.
* The floating navigation bar has a lit top edge and a spring-driven
  selection; the screens have drifting light pools, a vignette and a
  particle field with real depth.
* State changes move: the press of a card, the entrance of a screen, the
  transition between two screens and the reveal of a chart when its data
  changes.

### The measurement

* `PerformanceBenchmarkTest` runs the *real, animated* application on a
  device, drives it through a scripted session and records the platform's
  own `FrameMetrics`, the CPU time of the process and its memory. It also
  takes the screenshots of the session.
* The `MRSAD Performance` workflow measures the revision before this
  phase and the revision after it on the same emulator, in the same job,
  with the same instrument, and writes the comparison into
  `PERF-COMPARISON.md`. Each side is measured five times: one run of a
  shared machine is not a measurement, and the comparison reports the
  middle of the runs next to the spread of the runs.
* The two sides are measured **in the same job, on one emulator, and the
  order of the two is swapped after every round**. While the two sides
  were measured in two jobs of a matrix, they ran on two machines of the
  cloud — and a machine is not the same machine twice. Two campaigns
  disagreed about the same code because of it. The single job removes
  that variable, and the swapped order removes the one that is left.
* A session whose instrumentation failed is not counted and not used,
  and the report of the session before it is taken away from the device
  before the session starts, so a number can never be counted twice.
* The frame budget is the refresh rate of the display the application is
  drawn on, not a constant, and the refresh rate is part of the report.

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
   APK can no longer be installed over an older one. The workflow now reads
   the key from the `MRSAD_KEYSTORE_BASE64` secret whenever it exists and
   falls back to the cache otherwise, but the secret still has to be
   created by a session with repository-admin access — see the open issue
   about moving the signing key out of the cache.
9. Every release now publishes the SHA-256 fingerprint of its signing
   certificate, so two releases can be compared without downloading
   anything. The fingerprint only helps if the key is not rotated: a
   rotation still breaks the upgrade path of every existing install.
10. The performance comparison is measured on an x86_64 emulator, not on a
    physical phone: the frame times are the platform's own numbers, but a
    real device with a different GPU will behave differently. The
    benchmark is reproducible and re-runnable, not a claim about every
    device.
11. The emulator is too slow to draw in 16.67 ms: *every* frame of
    *both* revisions is late against the budget of its display, so the
    jank column sits at its ceiling and cannot separate the two. The
    comparison is therefore about the middle and the spread of several
    runs, not about a pass or a fail — and it says so when a difference
    lies inside the spread. A verdict about smoothness needs a physical
    device, which this sandbox does not have.
