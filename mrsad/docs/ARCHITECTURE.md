# MRSAD — Architecture

> Persian-first, offline-first cognitive training ecosystem for Android.
> Kotlin · Jetpack Compose · Room · DataStore · WorkManager · zero network permission.

---

## 1. Module graph

```
mrsad/
├─ core/            pure Kotlin (no Android, no third-party deps) — the source of truth
└─ app/             Android application (Compose UI, Room, DataStore, services)
```

`core` is a **JVM library** on purpose:

* every metric, generator and rule is unit-testable on a plain JVM,
* CI runs `:core:test` in ~90 s before spending emulator minutes,
* it cannot accidentally depend on Android APIs, so it stays portable
  (the same code can later power a desktop or web companion).

### Why not the 15-module layout from the brief?

The brief suggested ~18 Gradle modules. For a single-team product this
multiplies build time and configuration surface without buying isolation
(the feature modules would all depend on the same core + data layer anyway).
MRSAD uses **two modules with strict package boundaries**:

```
app/src/main/kotlin/dev/mrsad/app/
├─ data/          Room entities/DAOs, repositories, DataStore settings, DI container
├─ ui/            design system (theme, tokens, components, charts, environments)
├─ navigation/    one NavHost, typed routes
├─ feature/       home, type, read, mind, analytics, assistant, settings, studio, placeholder
└─ util/          MrsadClock (single time source)
```

Feature code may depend on `data` and `ui`; `data` never depends on `ui`.
This is checked by review convention today and by package-visibility rules
(`internal`) where it matters.

---

## 2. Data layer

### Room (schema v1, exported)

| Table | Purpose |
|---|---|
| `typing_sessions` | one row per finished typing session: cpm, wpm, wordWpm, accuracy, keystrokes, errors, elapsed ms, date ISO, compact mistake heatmap, completion flag, timestamps |
| `activity_events` | **append-only log of every completed activity** across modules: module, date, minutes, primary/secondary metric, payload |

The activity log is the canonical source for streaks, daily summaries, charts
and coaching. Adding a module therefore never requires rewriting analytics —
it only adds rows with a new `module` value.

Indexes: `dateIso`, `finishedAtMillis`, `module`, `(dateIso, module)` — exactly
the access patterns used by Home and Analytics.

Migrations: `exportSchema = true`, schemas committed under `app/schemas/`.
`fallbackToDestructiveMigration` is deliberately **not** used: a future schema
change must ship a real migration.

### DataStore

`mrsad_settings` (Preferences): theme, graphics quality, Persian numerals,
haptics, sound, reduce-motion, font scale, defaults flag. Read as a single
`Flow<MrsadSettings>` that drives the entire theme.

### Time

`MrsadClock` is the only source of "now"/"today". It returns ISO local dates and
epoch millis; foreground transitions re-emit the day key so dashboards roll over
correctly after midnight.

---

## 3. Domain core (`:core`)

| File | Responsibility |
|---|---|
| `text/PersianText.kt` | Arabic↔Persian normalisation (ی/ک/ه), ZWNJ handling, digit conversion, Persian-aware word count |
| `typing/TypingSession.kt` | clock-injected typing state machine + metric definitions |
| `reading/ReadingEngine.kt` | RSVP chunking, ORP pivot, durations, effective-WPM formula, comprehension scoring |
| `analytics/AnalyticsMath.kt` | linear trend, streaks, ISO date arithmetic, percent change |
| `coach/Coach.kt` | offline rule/statistics coaching, daily plan generation |
| `mind/MindGenerators.kt` | deterministic generators for 6 cognitive exercises |
| `session/FocusSession.kt` | monotonic Pomodoro state machine (ready for Focus Space) |
| `random/SplitMix64.kt` | deterministic PRNG (reproducible daily content) |

All of it is covered by `CoreChecks` — an executable specification of 38
assertions that runs both as plain Kotlin (`java -jar`) and as JUnit on CI.

---

## 4. UI architecture

* **Single activity**, Compose Navigation, five-destination bottom bar
  (خانه / تایپ / ذهن / تحلیل / دستیار) + routed secondary screens.
* **Design system** (`ui/theme`, `ui/components`): semantic colour tokens for
  6 themes, Vazirmatn/Estedad type scale, spacing/radius/motion tokens, and one
  implementation per component (surface, buttons, chips, charts, states).
* **Page environments** (`AmbientBackground`): each page has its own atmosphere
  (gradient lighting + optional particle layer). The animation clock is
  suspended when the process is not resumed; `GraphicsQuality.PERFORMANCE` and
  "reduce motion" disable the particle layer entirely.
* **The Core object** (`MrsadCore`) is procedural Canvas art whose energy is
  real daily-goal progress and whose orbiting sparks are the real streak.
* All screens handle **loading / empty / error / disabled** states; no screen
  fabricates numbers. Placeholders (Arena, Focus, Planner, Library) state
  clearly that they are scheduled for the next phase.

### Persian correctness

* Default locale is Persian (`values/`), English is `values-en/`.
* `supportsRtl=true`, `localeConfig` for per-app language.
* All numbers pass through `formatInt/formatPercent` honouring the
  Persian-numeral setting.
* Comparison of typed text goes through `PersianText.normalizeForComparison`
  (a typist pressing Space instead of ZWNJ is never penalised).
* Typing surface renders an `AnnotatedString` over the *normalised* target and
  advances only on correct input, which keeps bidi shaping stable.

---

## 5. Concurrency & performance rules

* All Room access is observable `Flow` or `suspend` — never on the main thread.
* Aggregate/coaching computation runs on `Dispatchers.Default`.
* Session timers use `SystemClock.elapsedRealtime()` (monotonic) and pause
  bookkeeping, never frame counters.
* Particle rendering is a single Canvas with a deterministic field; frames are
  only requested while the process is resumed.
* Recomposition discipline: `remember`ed particles, stable keys, `derivedStateOf`
  where a value is reused.

---

## 6. Security & privacy

* **No `INTERNET` permission** — the app physically cannot upload data.
* All data lives in the app's private storage; OS backup rules include only the
  database and settings.
* No analytics SDKs, no ad IDs, no external services, no API keys.
* The AI module is local: rules + statistics over stored data.

---

## 7. Build & CI

* AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, JDK 17, compileSdk 35, minSdk 26.
* Plain Kotlin test runner in `:core` (also bridged into JUnit).
* `.github/workflows/mrsad-android.yml`:
  1. `:core:test` (fast gate),
  2. `assembleDebug` + `testDebugUnitTest` + `lintDebug` with actionable error
     extraction,
  3. artifact upload (`mrsad-debug-apk`),
  4. emulator smoke tests (`connectedDebugAndroidTest`).
