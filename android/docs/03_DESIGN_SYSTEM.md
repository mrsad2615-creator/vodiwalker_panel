# 03 · UI/UX Design System Specification — "Obsidian Cinema"

**Philosophy.** VodiGesture interacts with the user for a *fraction of a second at a time* — an
84 ms haptic confirmation, a 200 ms ghost trail, a 1.2 s gesture. The design system is therefore
optimized for **glanceability, tactile certainty, and cinematic depth**, never for dense
information. Everything below is implemented in `:core:designsystem` and verified against the
generated token artifacts.

---

## 1. Color System (Material Design 3 / HCT)

Seed color **`#5865F2` — Electric Iris**, processed through Google's CAM16/HCT engine
(`@material/material-color-utilities` 0.4.0, Tonal Spot scheme) at four fidelity levels.

| Scheme | Kotlin token | Source file | WCAG 2.1 result |
| --- | --- | --- | --- |
| Light standard | `VodiLightColorScheme` | `Color.kt` | 15/15 pairs · min **4.26:1** |
| Dark (Obsidian Cinema) | `VodiDarkColorScheme` | `Color.kt` | 15/15 pairs · min **5.87:1** |
| Dark high-contrast (AAA) | `VodiDarkHighContrastColorScheme` | `Color.kt` | 15/15 pairs · min **11.07:1** |
| Light high-contrast (AAA) | `VodiLightHighContrastColorScheme` | `Color.kt` | 15/15 pairs · min **8.97:1** |

Artifacts: `android/docs/m3_contrast_audit.json` (machine-readable, includes every pair, ratio, and
threshold), `preview-workbench/src/theme/m3Tokens.ts` (identical values for the web workbench).

**Rules enforced by tooling**

- Composables consume **only** `MaterialTheme.colorScheme.*` or `LocalExtendedColors.*`.
  Hardcoded `Color(0x…)` literals outside `:core:designsystem` are an audit **error**.
- Outlines are exempted at 3.0:1 (non-text UI component contrast); all text/background pairs use
  the 4.5:1 AA threshold, and the AAA schemes use 7.0:1.
- Dynamic color (`dynamicDarkColorScheme`/`dynamicLightColorScheme`) is opt-in on API 31+ and
  falls back to the static schemes above; high contrast is forced when
  `UiModeManager.contrast == CONTRAST_HIGH` (API 34+).

**Extended (non-M3) semantic roles** — `ExtendedCinematicColors`: `statusActive`, `statusWarning`,
`statusError`, `glassTint`, `glassBorder`, `glassHighlight`, `ghostTrail`, `inkPrimary`, `inkGlow`.
These are intentionally *outside* the M3 role set because they encode engine state, not brand.

---

## 2. Typography

15-style M3 type scale (`VodiTypography`), all roles defined — no `default` leakage.

| Role | Size / line | Weight | Tracking | Use |
| --- | --- | --- | --- | --- |
| `displayLarge` | 57 / 64 | SemiBold | −0.25 | Splash wordmark |
| `headlineLarge` | 32 / 40 | SemiBold | 0 | Screen hero ("Multi-Engine Online") |
| `headlineSmall` | 24 / 32 | SemiBold | 0 | Onboarding section headings |
| `titleLarge` | 22 / 28 | Medium | 0 | Card titles |
| `titleMedium` | 16 / 24 | Medium | 0.15 | List rows |
| `bodyLarge` | 16 / 24 | Regular | 0.5 | Prose |
| `bodyMedium` | 14 / 20 | Regular | 0.25 | Descriptions |
| `bodySmall` | 12 / 16 | Regular | 0.4 | Helper text |
| `labelLarge` | 14 / 20 | Medium | 0.1 | Buttons, chips |
| `labelSmall` | 11 / 16 | Medium | 0.5 | Overline / telemetry captions |

`displayLarge … labelSmall` all set `fontFeatureSettings = "tnum"` on numeric slots so live
confidence/latency values **never reflow** during 120 Hz updates.

---

## 3. Motion

`VodiMotion` centralizes every duration, easing, and spring — no ad-hoc `tween(300)` in screens.

| Token | Value | Applied to |
| --- | --- | --- |
| `spatialFast` | `spring(dampingRatio = 0.78, stiffness = medium)` | Presses, chips, selection |
| `spatialExpressive` | `spring(dampingRatio = 0.68, stiffness = mediumLow)` | Sheet/panel transitions |
| `effects` | `spring(dampingRatio = noBouncy, stiffness = medium)` | Fades, scrims, glass opacity |
| `EmphasizedDecelerate` | `CubicBezier(0.05, 0.7, 0.1, 1.0)` | Enter transitions |
| `EmphasizedAccelerate` | `CubicBezier(0.3, 0.0, 0.8, 0.15)` | Exit transitions |
| `Standard` | `CubicBezier(0.2, 0.0, 0.0, 1.0)` | Utility changes |
| Durations | 180 / 320 / 500 ms | Short / medium / long |
| Predictive back | `1 − 0.08 × progress` scale | `PredictiveBackHandler` |

Reduced-motion compliance: the workbench honours `prefers-reduced-motion`, and Compose animations
read `MotionDurationScale` from the system animator scale so users who disable animations get
instant state changes rather than queued springs.

---

## 4. Glassmorphism specification

Implemented by `GlassmorphicCard` (Compose) and `.glass` recipes (workbench):

| Property | Value | Rationale |
| --- | --- | --- |
| Surface tint | `onSurface @ 5.5%` (dark) / `surface @ 62%` (light) | Legible without washing out content |
| Blur | 22–26 px backdrop, 160% saturation | Depth cue; API 31+ also requests `FLAG_BLUR_BEHIND` on the window |
| Border | 1 dp `outlineVariant @ 60%` | Defines the edge on low-contrast wallpapers |
| Radius | 28 dp (cards) · 32 dp (hero) | M3 large/extra-large shape tokens |
| Elevation shadow | `0 24 60 -24` rgba(0,0,0,.55) | Cinematic lift without a hard drop shadow |
| Inner highlight | `inset 0 1px 0 onSurface @ 12%` | Fakes a glass bevel |

**Accessibility guardrails:** glass is never placed behind text alone — every glass surface has an
opaque `surfaceContainer` fallback when `forceHighContrast` is true, and the AAA schemes disable
tint (it would otherwise erode measured contrast).

---

## 5. Component inventory

| Component | Location | Behaviour |
| --- | --- | --- |
| `GlassmorphicCard` | `CinematicComponents.kt` | Blurred surface + optional `Surface` fallback in HC mode |
| `SpringPressableCard` | `CinematicComponents.kt` | `interactionSource`-driven scale/glow press, respects disabled state |
| `ConfidenceArcGauge` | `CinematicComponents.kt` | 270° arc, animated sweep, tabular value, semantic `progressBarRangeInfo` |
| `CinematicGestureCanvas` | `CinematicComponents.kt` | 120 Hz historical-pointer ink, pressure-width, ghost overlay, `testTag("cinematic_gesture_canvas")` |
| `GlobalOverlayRoot` | `:service:overlay` | Edge handle (`overlay_edge_handle`), expandable canvas, prediction chips, dismiss (`overlay_dismiss_button`) |
| `PermissionStepCard` | `:feature:permissions` | Three-state step (blocked/pending/granted) with inline action |
| `DashboardScreen` hero | `:feature:dashboard` | Engine health + power mode + haptic gain + template list |

Every interactive element carries a `testTag` and meets the **48 dp** minimum touch target
(`VodiSpacing.minTouchTarget`).

---

## 6. Layout, density & adaptive behaviour

- Spacing scale: 4 / 8 / 12 / 16 / 24 / 32 dp (`VodiSpacing`), 4 dp grid, no arbitrary values.
- `NavigationSuiteScaffold` switches between bottom bar (compact), rail (medium), and drawer
  (expanded) automatically — the app never hardcodes a phone layout.
- Edge-to-edge is mandatory (`enableEdgeToEdge`); all screens consume
  `Scaffold`/`WindowInsets` padding, so Android 15's enforced edge-to-edge cannot clip content.
- Overlay respects gesture insets (`respectSystemGestureInsets = true`) so it never steals the
  system back-swipe region.

---

## 7. Iconography

`material-icons-extended` Rounded family only (one family, consistent stroke weight). Icons are
always paired with a label or `contentDescription`; purely decorative icons pass
`contentDescription = null`.

---

## 8. UX flow specification

**First run** → Prominent Disclosure modal (explicit consent, decline path) → Accessibility step
(locked until consent) → Restricted Settings helper (only if the install source implies sideload and
the service is still off) → optional notifications → "Enter VodiGesture Studio".

**Recording** → name → draw sample 1/3 → lock → draw 2/3 → draw 3/3 → collision check → save.
Failure states are non-destructive: the canvas keeps prior samples and surfaces the reason
("Stroke too short", "Conflicts with existing gesture (92% similar)").

**Triggering** → overlay handle → draw → success haptic (`CONFIRM_CINEMATIC`) → action graph runs →
telemetry row. Rejection → `REJECT_DOUBLE_THUD` + collapse, never a modal interruption.

**Privacy** → if the foreground surface is sensitive, the overlay visibly desaturates and shows the
shield glyph; strokes are still drawable but never recognized, and no haptic confirmation fires.

---

## 7. Persian-first localisation contract

The interface is Persian (fa) with an English mirror. The rules below are enforced, not aspirational.

### 7.1 Type and script
| Rule | Implementation |
| --- | --- |
| Persian UI typeface | Vazirmatn (SIL OFL 1.1), bundled in `core:designsystem/res/font`, licence in `res/raw` |
| Line boxes | 1.55–1.7× font size; headings 1.35× — Persian ascenders, dots and marks need the room |
| Tracking | `letterSpacing = 0.sp` on every style: spacing tears cursive joins apart |
| Digits | `PersianText` converts to ۰۱۲۳۴۵۶۷۸۹ with the Persian thousands separator (U+066C) |
| Quotation | Persian guillemets «…» via `PersianText.quote` |
| Mixed scripts | LTR fragments (package names, versions) wrapped in Unicode isolates (U+2066…U+2069) |

### 7.2 Direction
- `android:supportsRtl="true"`, `localeConfig` lists `fa` first, `en` second.
- No screen uses `padding(left/right=)`; all spacing is `start/end`.
- Trailing controls (switches) sit in a `SpaceBetween` row, so they land on the end edge in both
  directions without any explicit alignment.
- Directional icons come from `Icons.AutoMirrored.*`, per Material's RTL mirroring guidance.

### 7.3 Copy
- 137 strings per locale, key-for-key identical (`values/` ↔ `values-fa/`).
- No user-facing literal in Kotlin: ViewModels emit semantic state (`RecorderFeedback`,
  `StatusTone`) and the composable resolves the string.
- Optional permissions are labelled `اختیاری` and never gate the primary action.
