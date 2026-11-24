# Architecture — milestone 0.2

## Dependency graph

```text
app -> core:domain
app -> core:data -> core:domain
app -> core:designsystem -> core:domain
app -> backup:crypto
testing -> core:domain, backup:crypto
```

The domain module has no Android dependency. The encryption module has no Android dependency. The graph has no cycles. The existing Python application has no dependency on NEUROX.

## Responsibilities

* `core:domain`: validated configuration, original content, session state machine, scoring, typing measurements, adaptive suggestions, schedule conflict checks and color contrast calculations.
* `core:data`: Room entities, profile-scoped queries, transactions, versioned checkpoints and application preferences.
* `core:designsystem`: semantic theme tokens, local fonts, geometric vector-style icons and the emblem.
* `backup:crypto`: portable authenticated encryption. It does not restore databases.
* `app`: lifecycle integration, state collection, navigation, form validation and Compose screens.

## Session state

A new session starts in a paused state. An explicit start or resume action starts the current round. Reaction rounds first wait for a random interval. The engine then publishes an active stimulus. Reading rounds move from text to question. Completion requires all configured rounds.

Use `SystemClock.elapsedRealtime` for active time. Use epoch milliseconds only for calendar history. Freeze active time on pause. Restart an unfinished round after pause or process recreation. Never reconstruct a reaction latency from wall time. Do not store a partial typing string.

The ViewModel mutates the engine on the main dispatcher. It sends immutable checkpoints to a bounded persistence queue. The queue runs suspend Room operations. Input does not wait for disk I/O. A completed result appears only after its database transaction returns. Database failures pause training and show a retry action. A monotonic checkpoint guard rejects older queued writes. A completed row cannot receive another reward.

Persist each round and each pause. A sudden process kill can lose a pending write. Restore only the last durable checkpoint. Do not call the lost round complete. The current implementation does not promise zero-loss persistence for an in-flight frame.

## Scope boundaries

This milestone shares session orchestration across eight exercise paths. It does not yet provide the complete independent engine/plugin modules for all 34 exercise types. Split those paths behind explicit engine interfaces as the next exercise families are added. Split repository responsibilities before the vault, quest and custom-builder features are integrated. Add modules only when they own implemented behavior. Do not create empty module trees to imply completion.

## Documentation language

Use short instructions and consistent terms. ASD-STE100 Issue 9 is the intended controlled-language reference. Its official dictionary and all applicable rules have not been checked. These documents do not claim full compliance.

## Exercise expansion in 0.2

`Stimuli` owns seeded visual boards, balanced GO/NO-GO plans, phrase splitting and word batches. Save the seed in the configuration. Recreate an unfinished board or batch from that seed. Store only completed rounds. A paused phrase or word batch restarts at its round boundary.

Each published interactive state has an input revision. Compose callbacks supply the revision they displayed. The engine rejects a callback from an older phase, round or sprint word. The optional revision argument remains available to deterministic core drivers that do not render UI. Production ViewModel input paths always pass a revision.

Do not start GO from the tap handler. Only an explicit engine tick can publish GO. Keep correct NO-GO withholding separate from reaction latency. A slow phrase frame advances at most one phrase. It must not skip unseen words to meet an artificial target speed.

Passage and typing lists are cached in each engine instance. Do not rebuild original content on the high-frequency reaction ticker. Typing drafts use non-saveable Compose memory. Do not place them in the Activity SavedState bundle.
