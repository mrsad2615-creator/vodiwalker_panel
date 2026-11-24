# NEUROX Implementation Plan

This plan follows the supplied product specification while preserving the unrelated VodiWalker application already present in this repository. No NEUROX product feature is considered implemented by the Phase 0 audit.

## Architecture boundary

- Place the Android application in an isolated native project (recommended: `android/`) or a dedicated repository.
- Do not import VodiWalker server state, account data, runtime files, or network services into NEUROX.
- Keep NEUROX core training and essential data flows local and usable in airplane mode.
- Use Kotlin, Jetpack Compose, Material 3 foundations, Room, DataStore, Coroutines/Flow, and Android Keystore as required by the specification.
- Keep domain rules independent of Compose and Android infrastructure. Use explicit repository interfaces and dependency direction; avoid cyclic feature dependencies.

## Delivery gates

### Gate 0 — Product and toolchain boundary

- Agree the Android project location and supported SDK range.
- Confirm available Android build/signing environment.
- Preserve the existing VodiWalker application and `data/` files.
- Record product decisions that affect encryption, profiles, imported text formats, and backup recovery.

### Gate 1 — Foundation

- Establish Gradle wrapper and Kotlin/Compose build, app manifest, application identity, and modular package boundaries.
- Add design tokens, localization (fa/en), RTL/LTR direction support, navigation, Room schema/migrations, DataStore settings, profile-scoped data, error model, and unit-test setup.
- Acceptance: clean build; repository and migration tests; Persian/English layout smoke tests; no network dependency for core launch.

### Gate 2 — Visual identity and shell

- Create original vector emblem/wordmark/icon assets and an interruptible, skippable first-run intro.
- Implement distinct light/dark palettes, accessible contrast, accent preview/custom themes, and persistent theme selection.
- Implement six navigation destinations with useful empty/error states and actual stored values only.
- Acceptance: UI/navigation tests, accessibility labels/focus checks, reduced-motion behavior, theme persistence.

### Gate 3 — Four independent training systems

For each module deliver exercise state machines, validated settings, monotonic session timing when relevant, pause/resume/recovery, real scoring, persisted session records, history, and tests before calling it done.

- VISION: attention, search, tracking, matching, memory and spatial tasks.
- REFLEX: stimulus-response tasks, false-start/miss handling and latency caveat.
- READ: local content, reader controls, comprehension/recall where content supports it, and position persistence.
- TYPE: bilingual input, physical/virtual input distinctions, consistent documented WPM and weak-key summaries without retaining private text in analytics.

Acceptance: deterministic domain tests, interruption/recreation tests, accessibility review, airplane-mode tests, and UI tests for representative exercises in all four systems.

### Gate 4 — Adaptive training and planning

- Build deterministic, explainable recommendations from multiple sessions; cap changes and respect user overrides.
- Add manual/adaptive plans, schedule validation, editable plans, and adaptation history.
- Acceptance: rule boundary tests, no one-session overreaction, disabled-adaptation/manual-plan tests.

### Gate 5 — Analytics and progression

- Implement actual persisted aggregations, date/module filters, meaningful empty states, XP, achievement requirements, quests, and duplicate reward prevention.
- Keep gamification scores separate from measured performance.
- Acceptance: aggregation, timezone, duplicate-claim, and real-data UI tests.

### Gate 6 — Library, audio, profiles

- Add bundled original content, safe text import, validated custom exercise templates, local sound/haptics controls, and isolated guest/personal profiles.
- Acceptance: malformed import tests, config validation tests, profile isolation tests, audio lifecycle tests, and no arbitrary user code execution.

### Gate 7 — Backup and security

- Define a versioned backup format and threat model. Use authenticated encryption, unique nonces, separated keys, and reviewed Android Keystore/password derivation design.
- Validate and restore to temporary state; commit only after full validation; preserve active data on every failure path.
- Acceptance: correct/incorrect password, corruption, truncation, unsupported version, interrupted restore, and temporary-file cleanup tests.

### Gate 8 — Release readiness

- Run unit, integration, UI, accessibility, offline, performance, and security tests on available Android targets.
- Document exact tested SDK/device matrix and known limitations.
- Produce a release APK only from a verified reproducible release build. Do not claim absent device tests were run.

## Open decisions

- Android `minSdk` / target SDK and distribution channel.
- Whether more than one profile can have PIN protection and the recovery expectations.
- Supported imported reading formats and maximum import size.
- Backup password policy and whether scheduled backups are appropriate.
- Brand asset ownership/finalized wordmark rules.
