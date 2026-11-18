# 01 · System Analysis — Android Accessibility Gesture Interception & Automation

**Project:** VodiGesture (`com.vodiwalker.gesture`)
**Targets:** minSdk 29 (Android 10) · targetSdk 35 (Android 15) · compileSdk 35 · JVM 21 · Kotlin 2.1.20

---

## 1. Why AccessibilityService (and not an IME, overlay, or Shizuku)

| Capability needed | Interface candidate | Verdict |
| --- | --- | --- |
| Observe foreground app / window changes | `AccessibilityService` | ✅ `TYPE_WINDOW_STATE_CHANGED`, `flagRetrieveInteractiveWindows` |
| Perform navigation/system commands | `AccessibilityService.performGlobalAction` | ✅ 12 `GLOBAL_ACTION_*` constants (API 29–34) |
| **Synthesize** pointer gestures | `AccessibilityService.dispatchGesture` | ✅ Requires `canPerformGestures="true"` |
| Draw a floating control | `WindowManager` overlay | ⚠️ `SYSTEM_ALERT_WINDOW` is a restricted, Play-policy-sensitive permission |
| Draw a floating control (better) | `TYPE_ACCESSIBILITY_OVERLAY` | ✅ Granted implicitly by the accessibility service; no extra runtime permission |
| Capture *global* raw touch (including other apps) | — | ❌ Not available to third-party apps at all |

### Consequence: the recognition surface is the overlay, not the system

Android does not expose global raw touch input to non-system apps. VodiGesture therefore splits
responsibility:

- **Interaction surface** — the `TYPE_ACCESSIBILITY_OVERLAY` edge handle owned by
  `:service:overlay`. All user-drawn strokes are captured here at full pointer fidelity
  (`ACTION_DOWN/MOVE/UP` with historical samples), which is exactly what the recognizer needs.
- **Context & dispatch** — `:service:accessibility` observes `TYPE_WINDOW_STATE_CHANGED` for
  app/activity scoping, evaluates privacy gates, and executes actions via `performGlobalAction`,
  `dispatchGesture`, `CameraManager`, `AudioManager` and scoped package launch intents.

This is the only architecture that is simultaneously Play-policy-compliant, permission-minimal,
and capable of the required feature set.

---

## 2. Android version capability matrix

| Capability | API 29 | 30 | 31 | 33 | 34 | 35 |
| --- | --- | --- | --- | --- | --- | --- |
| `dispatchGesture` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `TYPE_ACCESSIBILITY_OVERLAY` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Edge-to-edge enforced | — | `setDecorFitsSystemWindows(false)` | | | | ✅ mandatory |
| `VibrationEffect.Composition` primitives | — | `CLICK/TICK/QUICK_*` | `LOW_TICK/THUD/SPIN` | | | |
| `FLAG_BLUR_BEHIND` (glassmorphism) | — | — | ✅ | | | |
| `isAccessibilityDataSensitive` | — | — | — | — | ✅ | ✅ |
| Restricted Settings (sideload) | — | — | — | ✅ | ✅ | ✅ |
| Predictive back | — | — | — | opt-in | ✅ | ✅ default |
| `UiModeManager.contrast` (HC toggle) | — | — | — | — | ✅ | ✅ |
| `AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT` | — | — | ✅ | | | |

Each row is implemented behind an explicit `Build.VERSION.SDK_INT` branch; no deprecated fallback
path exists (verified by the `API` audit engine, see `05_VERIFICATION.md`).

---

## 3. Threat model & privacy posture

**Assets:** on-device gesture templates, Smart-Action graphs, calibrated ML weights, execution
counters.

**Adversaries / risks**

1. *Screen-content leakage* — the service can see node trees. Mitigation: `canRetrieveWindowContent`
   is used **only** for `SemanticNodeAction`; every read is preceded by an
   `isAccessibilityDataSensitive` (API 34+) check plus a protected-package denylist
   (`biometrics`, `permissioncontroller`, `packageinstaller`, password managers, wallets).
2. *Keystroke / password capture* — never performed. `flagIncludeNotImportantViews` is
   deliberately **absent**; password fields are skipped by the same sensitivity gate.
3. *Exfiltration* — the manifest declares **no `INTERNET` permission**. There is no network stack,
   no analytics SDK, and no remote model. Weights live in app-private Room storage excluded from
   cloud backup.
4. *Malicious trigger injection* — Smart-Action graphs are validated (`ActionGraphValidator`:
   cycle detection, depth/node bounds, referential integrity) before persistence; every command
   is a sealed type, so no arbitrary intent/URI is reachable without passing `openDeepLink`'s
   scheme allowlist.
5. *Recursion / self-triggering* — `SyntheticGestureGuard` opens a suppression window before each
   `dispatchGesture` and rejects any capture inside it (`SYNTHETIC_LOOP_BLOCKED`).
6. *Denial of service via crafted vault* — the JSON codec is depth/length bounded
   (`MAX_DEPTH = 32`, `MAX_INPUT_LENGTH = 1 MiB`) and fails soft; corrupt rows degrade to defaults
   instead of throwing on the binder thread.

**Data lifecycle:** execution telemetry is zero-PII (graph id, node count, duration, success flag),
pruned by an idle+battery-constrained `WorkManager` job (7-day cutoff, 500-row ceiling).

---

## 4. Modular Clean Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│ :app              VodiGestureApplication · MainActivity · DI · WorkMgr   │
├──────────────────────────────────────────────────────────────────────────┤
│ :feature:dashboard  :feature:recorder  :feature:triggers  :feature:perm. │  MVVM + UDF
├──────────────────────────────────────────────────────────────────────────┤
│ :service:accessibility                       :service:overlay            │  platform edge
├──────────────────────────────────────────────────────────────────────────┤
│ :engine:gesture  :engine:prediction  :engine:smartaction                 │  100% pure Kotlin
│ :engine:haptics  :engine:power                                          │  (JVM-testable)
├──────────────────────────────────────────────────────────────────────────┤
│ :core:model  :core:common  :core:designsystem                           │
│ :core:database (Room)  :core:datastore (Preferences DataStore)          │
└──────────────────────────────────────────────────────────────────────────┘
        dependencies point downward only — enforced by the ARCH audit engine
```

**Dependency rule.** `:core:model` is a pure domain kernel (no framework, no sibling imports).
Engines depend only on `:core:model` + `:core:common`. Android framework imports are **forbidden**
inside the seven pure modules and verified by AST analysis on every audit run.

**Ports & adapters.** The domain owns interfaces (`GestureTemplateRepository`,
`TriggerRuleRepository`, `ActionGraphRepository`, `ExecutionTelemetryRepository`) and platforms are
injected as functions/ports (`PlatformActionExecutor`, `Sleeper`, `MonotonicClock`,
`VibrateDriver`). Room/DataStore/`AccessibilityService` supply the implementations through Hilt
`@Binds`/`@Provides`; nothing in the engine layer knows Android exists.

### Module responsibilities

| Module | Type | Responsibility | Key types |
| --- | --- | --- | --- |
| `:core:model` | JVM | Immutable domain model | `GestureTemplate`, `AppTriggerRule`, `SmartActionGraph`, `PowerExecutionBudget` |
| `:core:common` | JVM | Result/error taxonomy, resilience primitives, persistence codec | `AppResult`, `GestureError`, `RingBuffer`, `CircuitBreaker`, `SyntheticGestureGuard`, `JsonCodec`, `Sleeper` |
| `:core:designsystem` | Android | M3 theme, motion, cinematic components | `VodiGestureTheme`, `VodiTypography`, `VodiMotion`, `GlassmorphicCard`, `CinematicGestureCanvas` |
| `:core:database` | Android | Room 2.7 entities, DAOs, codecs, repositories, preset seeding | `VodiGestureDatabase`, `EntityMappers`, `PresetSeeder` |
| `:core:datastore` | Android | User preferences (DataStore) | `UserSettingsDataSource`, `UserSettings` |
| `:engine:gesture` | JVM | Recognition + calibration | `HybridGestureEngine`, `AdaptiveOneEuroFilter`, `GestureGeometry`, 4 recognizers |
| `:engine:prediction` | JVM | Online MLP + Markov prediction, ghost trails | `AiGesturePredictionEngine` |
| `:engine:smartaction` | JVM | DAG validation/execution, trigger resolution | `SmartActionEngine`, `ActionGraphValidator`, `AppTriggerResolver` |
| `:engine:haptics` | JVM | Three-tier haptic planning | `HapticChoreographer` |
| `:engine:power` | JVM | Deterministic battery/thermal governor | `AdaptivePowerGovernor` |
| `:service:accessibility` | Android | The service, platform adapters, event throttling | `VodiGestureAccessibilityService`, `AndroidHapticDriver`, `AndroidPlatformActionExecutor` |
| `:service:overlay` | Android | `TYPE_ACCESSIBILITY_OVERLAY` window + Compose lifecycle | `GlobalOverlayController`, `OverlayComposeLifecycleOwner` |
| `:feature:*` | Android | Stateless Compose screens + `@HiltViewModel` routes | `*Screen` / `*Route` / `*ViewModel` |
| `:app` | Android | Application, activity, DI graph, WorkManager | `VodiGestureApplication`, `MainActivity`, `MultiEngineModule` |

---

## 5. Runtime data flow

```
Touch (overlay)                Accessibility events
      │                                │
      ▼                                ▼
AdaptiveOneEuroFilter ──► HybridGestureEngine ──► RecognitionOutcome
      │                          ▲                      │
      │                          │                      ├─ Matched ──► AppTriggerResolver
      │                   AiGesturePredictionEngine      │                    │
      │                     (prefix + context)          │                    ▼
      └──► ghost trail ────────┘                        │            SmartActionEngine (DAG)
                                                        │                    │
                                     Rejected ──────────┘                    ├─► PlatformActionExecutor
                                     (haptic + collapse)                     ├─► HapticChoreographer
                                                                             └─► ExecutionTelemetryRepository
```

Everything on that path is either pure Kotlin (JVM-verified) or a thin platform adapter.

---

## 6. Concurrency model

| Thread | Owns | Rule |
| --- | --- | --- |
| Main (service) | Overlay composition, window manager | Never blocks: all IO is dispatched |
| `Dispatchers.Default` | Recognition, prediction, DAG execution | CPU-bound, bounded by timeouts |
| `Dispatchers.IO` | Room, DataStore, package queries | Never touches the UI tree |
| `VibratorManager` binder | Haptics | Fire-and-forget, exceptions swallowed |
| WorkManager | Telemetry pruning | Idle + battery-not-low constrained |

Shared mutable state is confined to atomics (`AtomicLong`, `AtomicReference` in
`CircuitBreaker`/`SyntheticGestureGuard`), `ConcurrentHashMap` (per-node breakers, cooldowns), and
`synchronized` sections in `AdaptiveOneEuroFilter`/`RingBuffer` that are verified under 8-thread
contention in the test suite.
