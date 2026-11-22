# PROJECT_MASTER_SPEC.md

## Mission

A personal, local-only recreation of the Huawei Gallery experience for one device:
a Xiaomi POCO X3 Pro. The priorities, in order, are reference fidelity, real
functionality, honest reporting, and only then breadth of feature set. A feature
that is missing and admitted is worth more here than a feature that is painted on.

## Non-goals

* Not a product, not for distribution, not for other devices.
* No cloud, no account, no network permission, no analytics.
* No reproduction of Huawei proprietary code, fonts or image assets. The design is
  recreated from measured references; the icons are original geometry.
* No AI features. They would require sending photographs off the device, which
  contradicts the privacy requirement, and cannot be verified here anyway.

## Ground truth

The reference corpus lives in `tools/refs/`, and every claim about the reference is
traced to one of those files or to a public document, in `REFERENCE_ANALYSIS.md`.

| ID | Screen | Confidence |
|---|---|---|
| R1 | Photos timeline (EMUI 11) | real device capture |
| R2 | Albums (Huawei's own support simulator) | highest |
| R3 | Recently deleted (Huawei's own support simulator) | highest |
| R4 | Viewer | **absent** — behaviour inferred |
| R5 | Editor | **absent** |

## Delivery standard

A release is only described as ready when all of the following are true, and each
one is checkable from the repository:

1. `:core:test` passes — 25 unit tests over grouping, sorting, formatting, search
   and similarity.
2. `:app:assembleDebug`, `:app:testDebugUnitTest` and `:app:lintDebug` pass, with
   lint fatal (`lint.xml` silences exactly two checks, each with a written reason).
3. Instrumented Compose tests pass on an emulator: shell navigation, album
   drill-down, localisation, month/day switching.
4. The app launches on that emulator and `am start` reports `Status: ok`.
5. Screenshots of every implemented screen are captured from the running app and
   committed to `evidence/`, so a reviewer sees the artefact and not a description.
6. A signed release APK is built, verified with `apksigner`, and published with its
   SHA-256.

Where a standard cannot be met for a stated reason, `KNOWN_LIMITATIONS.md` says so
plainly and no claim is made.

## Document index

| Document | Contents |
|---|---|
| `ENVIRONMENT_AUDIT.md` | what the build environment can and cannot do, with evidence |
| `REFERENCE_ANALYSIS.md` | what the reference shows, measured |
| `FEATURE_PARITY_MATRIX.md` | honest per-feature status |
| `DESIGN_SYSTEM.md` | the measured palette, type ramp, spacing and grid |
| `ARCHITECTURE.md` | module split, data flow, and why each omission exists |
| `MEASURED_COMPARISON.txt` | structural metrics: reference versus implementation |
| `PERFORMANCE_REPORT.md` | what was measured, what was not |
| `BUG_TRACKER.md` | every defect found, with its cause and fix |
| `KNOWN_LIMITATIONS.md` | everything unfinished, stated plainly |
