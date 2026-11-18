# 02 · Feature Implementation Roadmap

Status legend: **DONE** = implemented + verified in this repository · **NEXT** = designed,
not yet built · **LATER** = backlog.

---

## Phase 0 — Multi-engine foundation ✅ DONE

| # | Deliverable | Evidence |
| --- | --- | --- |
| 0.1 | Toolchain verification (JDK 21, Kotlin 2.1, Node 22, Python 3.11) | `tools/run_kotlin_tests.sh`, `tools/audit_android_standards.py` |
| 0.2 | 17-module Gradle project + `build-logic` convention plugins | `android/settings.gradle.kts`, `build-logic/convention/` |
| 0.3 | HCT-generated M3 color schemes + WCAG audit | `android/docs/m3_contrast_audit.json` (4 schemes × 15 pairs) |
| 0.4 | Five pure-Kotlin engines with no Android coupling | `android/engine/**` |

**Engine contract (why multi-engine):** each engine owns exactly one decision domain and is
independently testable, replaceable, and power-gated.

| Engine | Single responsibility | Public entry point |
| --- | --- | --- |
| Gesture | "What did the user draw?" | `HybridGestureEngine.recognize` |
| Prediction | "What is the user about to draw?" | `AiGesturePredictionEngine.predict` |
| Smart-Action | "What should happen, safely?" | `SmartActionEngine.executeGraph` |
| Haptics | "How should it feel?" | `HapticChoreographer.compilePreset` |
| Power | "What may we afford right now?" | `AdaptivePowerGovernor.evaluate` |

---

## Phase 1 — Core gesture recording ✅ DONE

- **Multi-algorithm recognition** — Protractor (angular), $P+ point-cloud (multi-stroke),
  Sakoe–Chiba banded DTW (temporal), directional/Rubine guard. Weighted fusion differs for
  single- vs multi-stroke and orientation-sensitive templates.
- **3-sample calibration** — barycenter averaging, per-sample quality rejection, and a collision
  gate (`≥ 0.89` similarity against an existing template is refused with the conflicting name).
- **Signal conditioning** — velocity-adaptive 1€ filter with a 45 px/ms teleport clamp; NaN/Inf
  and reversed timestamps are sanitized (verified: ≥38% tremor reduction, <8 px ballistic lag).
- **Anti-recursion** — `SyntheticGestureGuard` suppression window + token, `SYNTHETIC_LOOP_BLOCKED`.
- UI: `:feature:recorder` — `GestureRecorderScreen` (stateless) + `GestureRecorderViewModel` (UDF),
  live canvas with testTag `cinematic_gesture_canvas`.

## Phase 2 — App-specific triggers ✅ DONE

- Sealed `TriggerScope`: `Global` (100) < `Category` (200) < `Package` (300) < `Activity` (400),
  tie-broken by user `priority`, per-rule cooldown enforced by `AppTriggerResolver`.
- Room schema denormalizes scope into indexed columns
  (`scope_type, target_package, is_enabled`) with cascading FKs.
- UI: `:feature:triggers` — trigger matrix with per-rule toggles and graph names.

## Phase 3 — Global overlay controls ✅ DONE

- `TYPE_ACCESSIBILITY_OVERLAY` (no `SYSTEM_ALERT_WINDOW`), edge handle, expandable glass canvas,
  API 31+ `FLAG_BLUR_BEHIND`, `currentWindowMetrics` (API 30+) instead of deprecated display APIs.
- `OverlayComposeLifecycleOwner` lets Compose run inside a non-Activity window without leaks.
- Prediction chips are actionable: tapping a chip dispatches that template's graph.

## Phase 4 — Smart-Action engine ✅ DONE

- Validated DAG: cycle detection (3-colour DFS), node (64) and depth (24) ceilings, referential
  integrity, per-node timeouts/retries/fallback edges, per-node `CircuitBreaker`.
- 9 command types covering global actions, synthetic gestures, app launch, deep links, media keys,
  hardware toggles, semantic node actions, haptics, and delays.
- 8 guard types incl. battery floor, foreground package allow/deny, time window, keyguard, headset.
- UI: execution results surface as `ExecutionReport` traces (zero-PII telemetry rows).

## Phase 5 — Haptic choreography ✅ DONE

- Three-tier negotiation: `VibrationEffect.Composition` (API 30/31+, primitives gated per level)
  → amplitude waveform → predefined effect → suppressed.
- 8 curated presets + continuous stroke micro-ticks modulated by velocity and curvature.
- All output scaled by `userIntensity × powerBudget.hapticScale`; `VibratorManager` only
  (no legacy `VIBRATOR_SERVICE` lookup).

## Phase 6 — AI gesture prediction ✅ DONE

- On-device 16→24→1 MLP with manual backprop + bigram Markov chain; no network, no remote model.
- Cold-start contextual priors by app category/hour; ghost-trail completion projection.
- Sensitive-context suppression returns nothing rather than a hint.
- Checkpointing into Room `prediction_model_state` (weights blob + bigram JSON).

## Phase 7 — Battery-optimized background processing ✅ DONE

- Six-mode deterministic governor; override priority
  **privacy → Doze → thermal → system saver → battery floor → user preference**.
- Budget fans out to four consumers: accessibility event throttle, overlay rendering, ensemble
  size / prefix prediction, haptic + animation scale.
- `TelemetryCompactionWorker`: 24h periodic, `requiresDeviceIdle` + `requiresBatteryNotLow`,
  7-day cutoff, 500-row cap, unique work `KEEP`.

## Phase 8 — UI/UX: Obsidian Cinema ✅ DONE

- 15-style M3 type scale with tabular numerals, 4dp spacing grid, 48dp touch targets.
- Glassmorphic surfaces (`GlassmorphicCard`), spring press physics (`SpringPressableCard`),
  `ConfidenceArcGauge`, 120Hz historical-pointer canvas.
- Dynamic color (API 31+) + `UiModeManager` high-contrast (API 34+) with static fallbacks.
- Single-activity `NavigationSuiteScaffold` + `PredictiveBackHandler` scale choreography.

## Phase 9 — Permissions & compliance ✅ DONE

- Google Play Prominent Disclosure modal → service enablement → optional notifications.
- Android 13–15 Restricted Settings detection (`PackageInstaller` install source) with an
  "App info → Allow restricted settings" guidance card.
- Lifecycle-aware refresh (re-inspect on `ON_RESUME`), live `AccessibilityManager` query — no
  `Settings.Secure` string parsing.

---

## Phase 10 — NEXT (designed, not yet implemented)

| Item | Design note | Effort |
| --- | --- | --- |
| Gesture library import/export | Versioned JSON envelope, checksum, no auto-execution on import | M |
| Per-app profiles | `AppTriggerRule` already supports it; needs a picker with `queries`-scoped package labels | M |
| Multi-stroke recorder UX | Engine already supports N strokes; recorder needs stroke affordances | S |
| LiteRT model swap | `:app` already depends on LiteRT; replace the Kotlin MLP behind the same port | L |
| Widget/Quick Settings tile | `TileService` toggling the overlay without opening the app | S |
| Wear OS companion | Recognizer is pure Kotlin; needs a circular canvas + haptic tier for wear | L |
| Baseline profile + macrobenchmark | `profileinstaller` is wired; add `benchmark` module runs | M |

## Phase 11 — LATER

Cloud sync (explicitly out of scope for the privacy posture), on-device speech triggers,
tablet/foldable two-pane trigger editor, community template marketplace (would require a network
permission and a re-review of the threat model).
