# Architecture

## Module graph

```
                        :app
                         │  (depends on everything below)
        ┌────────────────┼────────────────┬──────────────┐
   :feature/*        :core:designsystem  :core:data   :core:security
        │                 │                  │              │
        └────────┬────────┴──────┬───────────┘              │
           :core:presentation    │                          │
                 │               │                          │
            :core:games     :core:audio  ────────────────────┘
                 │               │
            :core:domain ────────┘
                 │
            :core:common
```

Dependency rules, enforced by the compile gate:

1. `:app` may depend on anything. **Nothing may depend on `:app`.**
2. `:core:common` depends on nothing project-local.
3. `:core:presentation` is pure JVM: no `android.*`, no `androidx.compose.*`.
4. Feature modules depend on core modules only, never on each other.
5. Android-only code sits in `platform/` or `keystore/` packages, so the offline
   verifier can exclude exactly those files.

## Layers

### `core:common`

Result types (`Outcome`, `DomainError`), a clock abstraction (`Clock`, `SystemClock`,
`ManualClock`), timing calibration, JSON parse/write, number and string helpers.
Everything here is deterministic and testable with no I/O.

**Why a JSON implementation at all:** the vault format and the session checkpoint both
serialise structured data, and adding a serialization library to the pure modules would
have pulled a compiler plugin into the offline gate. The parser is small, tested, and
its error type is part of the vault's validation path.

### `core:domain`

Models and rules, with **no I/O**. Exercises, sessions, profiles, plans, achievements,
the adaptive engine, the recommender, level/XP curves, streaks, analytics aggregation.
Repository *interfaces* live here; implementations live in `core:data`.

The rule that keeps this honest: if a function here needs a clock or a database, it
takes them as parameters. `Recommender.suggest(history, untrainedModules, nowMillis)`
takes the time rather than reading it.

### `core:games`

Exercise engines and content: stimulus generation, response windows, scoring per
module, the reading library. Depends on domain, never on Android.

**Determinism:** engines take a seed. Tests drive a fixed seed; the app seeds from the
session id. Without this, a failing trial could not be reproduced.

### `core:audio`

`Synth` (nine cues as PCM), `SoundCatalog` (gain quantisation), `AudioPolicy` (what may
play, at what gain, with what spacing). All pure and tested.

`platform/AudioRenderer` is the only Android file: it writes PCM to a short-lived
`AudioTrack` on a single worker thread, over a bounded queue, and **every request goes
through `AudioPolicy` first**. Ungated audio is audio that only looks muting in the UI.

### `core:security`

Vault crypto: AES-256-GCM, PBKDF2-HMAC-SHA256 at 210k iterations, envelope validation,
payload checksum, restore ordering. `keystore/` holds the Android Keystore wrapper and
is the only Android file.

Import order is fixed and tested: header → version → decrypt → payload → checksum →
restore. Nothing is applied to the database before the checksum matches.

### `core:data`

Room database (8 tables, foreign keys with `CASCADE`), DAOs, mappers, repository
implementations, preference keys.

**Repository streams** return `Stream<T>` (see DEC-0003). They are backed by Room Flows
and therefore never complete. `Stream.first(waitMillis)` in `core:domain` is the only
supported read-once; it is bounded and cancels its collector.

**Profile isolation** is enforced in the repository layer, not in screens: every query
is keyed by profile id, and `switchProfile` verifies the PIN and guarantees exactly one
active row in a single transaction.

### `core:designsystem`

Tokens (spacing, radius, type scale, motion), the theme engine (three built-in themes,
contrast validation), Compose components, Vazirmatn font family, and the bridge that
maps the presentation layer's enums onto Compose types.

**Why a bridge:** `core:presentation` cannot see Compose, so `LayoutDirection` and
`MotionModeName` exist twice. `PresentationBridge` translates in one file so the two
cannot drift.

### `core:presentation`

Pure JVM. String catalogues (Persian and English, in parity), number and date
formatting, screen-state models and builders, the session controller, result
presentation, the destination graph.

**Why screens get state objects rather than repositories:** a screen that computes its
own labels cannot be tested without Compose. `ScreenBuilders.home(...)` produces a
`HomeState` on a plain JVM, and the store-of-truth for "what the user sees" is a data
class with tests.

### `feature/*`

One module per screen area. Each exposes a stateful `*Route` (loads data, owns
`remember`ed state) and a stateless `*Screen` (renders a state object). Routes take
`AppServices` and callbacks; they never navigate by themselves, except through a
callback the app passes in.

### `app`

Single activity, `AppContainer` (manual DI), navigation host, bottom bar, `AppUiState`.

**Why manual DI:** the graph is small, has no scopes beyond the process, and is created
once. A framework would add a code generator and a build step for a graph that fits on
one screen.

## Data flow of a training session

```
HomeRoute                     loads history, achievements, checkpoint
  └─ onStartExercise ──> navigation to session/{exerciseId}
       SessionRoute           builds EngineSession + PolicySessionAudio + SessionController
         └─ SessionScreen     renders SessionUiState, calls controller.tick() per frame
              └─ answer       ResponseInput -> controller -> engine -> outcome
                   └─ FINISHED  SessionRoute saves the record exactly once
                        └─ ResultPresentation / AdaptationPresentation -> result screen
```

Three invariants in that path:

- **One save.** The record is persisted once, keyed by phase and record id, so a
  recomposition cannot double-count a session.
- **Pause on background.** `ON_STOP` pauses the engine, so a phone call does not become
  a 40-second response time.
- **Expiry is a miss.** A trial that times out is `MISSED`, not dropped. Dropping it
  would inflate accuracy.

## Error handling

`Outcome<T>` = `Ok(value) | Err(DomainError)`. Domain errors are a closed set with
stable string keys (`error.<code>`), so a new failure mode needs a new catalogue entry
and cannot surface as an empty message.

Screens show `ScreenState.Loading | Ready | Empty | Error(retryable)`. The error state
carries whether retrying can help, because "try again" on a corrupt backup is a lie.

## String catalogues

564 keys per language, asserted in parity. `Strings.raw(key)` returns the key itself
when missing - deliberately visible in QA rather than a blank - which is why a contract
test asserts that every key the pure layers *emit* exists in both catalogues.

Dynamic families (`module.vision`, `difficulty.3`, `skill.reaction_speed`,
`audio.music.volume`, `home.recommendedReason.*`) are generated from enums and
constants, and the contract test walks those enums rather than a hand-written list.
