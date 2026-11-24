# Quiet Precision design system

Status: implementation in progress, 2026-11-24. These rules cover all four systems. Browser evidence is not Android acceptance.

## Identity and foundations

Use the original folded N mark. Do not use competitor art. Use charcoal `#0C0E11`, champagne `#D4B982` and warm paper surfaces. Keep action color separate from score or stimulus meaning. Preserve the existing profile accent presets and contrast guard. Use semantic Material colors in native components. Module identifiers pair a muted accent with a name and symbol; color alone must not carry information. Physical reaction colors and arrows retain their tested meaning in both directions.

Use bundled OFL Vazirmatn. Use editorial display text sparingly, title text for sections, body text for instructions and label text for metadata. Never add letter spacing to connected Persian text. Keep code in LTR. Respect system font scaling and the profile scale. Do not rasterize text.

Native spacing tokens are in `core/designsystem/.../Tokens.kt`: 4, 8, 12, 16, 20, 24 and 32 dp. Page padding is 22 dp. Maximum reading width is 840 dp. Control/card/hero radii are 14/20/24 dp. Interactive targets must be at least 48 dp. Use wrapping rows for chips and metrics, not fixed-width translations.

## Shared architecture

`Theme.kt` owns palettes, typography and shapes. `Tokens.kt` owns spacing, radii, motion and module accents. `Page`, `Panel`, `SectionTitle`, `Metric` and shared navigation own screen structure. `JourneyUi.kt` owns studio overviews and accessible history charts. Measurement screens keep engine timing outside presentation animation. Do not duplicate engine or repository state in decorative widgets.

Main navigation: Home, Studios, Library, Insights, Profile. Settings opens from the toolbar. Each of four studios has the same overview hierarchy: system switcher, actual recent activity, explained exercise suggestion, complete available exercise list and comfort/measurement notes. Setup precedes a real session. Back from setup returns to its studio. Achievements opens from Profile. Retain page state per profile/route and per studio. Profile changes must not expose another profile's sessions.

## Component and behavior rules

- Headers: compact brand toolbar; editorial page title; smaller section labels. One primary action in each decision group.
- Panels: consistent shape and spacing, no ornamental elevation on measurement surfaces.
- Buttons: verb labels, full-width primary action when space is narrow. Disable commands during a write. No nonfunctional decorative controls.
- Inputs: native semantics, localized guidance, bounded values and explicit failures. Preserve the original data after a rejected operation.
- Charts: show only validated stored sessions. Give an equivalent textual summary to accessibility services. Zero history is an empty state, not sample success.
- Goals: optional 5–60 active minutes, default 15, stored per profile. No punishment for rest. Goals do not alter scores.
- Milestones: derive six rules from valid completed non-baseline history. Never award extra XP for viewing the page.
- Suggestions: name the least-practiced available task from valid history; deterministic tie-break. Explain the reason. Do not call this a learned cognitive model or silently change difficulty.
- Onboarding: three steps, skip at any time, local language/theme/motion/goal preferences. Optional visual baseline opens actual setup and earns no XP. Full four-system assessment is not implemented.
- Loading/error/empty: distinguish initialization, storage failure, no results and unavailable features. Keep retry/back paths. No simulated restore success.
- Motion: optional 160 ms entrance outside RUN and RESULT. Reduced motion removes it. No animated stimulus publication. Android 12+ system splash uses a short original mark reveal; no artificial startup wait. Pre-Android 12 uses a static window background. Validate platform animation-scale behavior on devices before acceptance.

## Locale and accessibility

Support FA/EN and RTL/LTR through the app locale context. Do not mirror physical stimulus directions. Use text with icons, native switches and accessible names. Preserve keyboard/back paths alongside touch. Charts announce daily values. Browser axe checks are supplementary; TalkBack, Switch Access, large font, high contrast and Android touch-target checks remain required.

## Prototype route critique

All 20 routes were rendered at 360/430 px, FA/EN, dark/light and normal/large text: 320 layout checks. Axe audits cover the 80 route/language/theme combinations at normal text. Latest result: zero overflow failures, zero page errors and zero axe route failures. Test command from repository root:

```sh
LD_LIBRARY_PATH="$PWD/.local/tools/design/sysroot/lib" node neurox/scripts/test-design.mjs
```

The local Chromium harness and libraries are tools, not application dependencies. Generated reports/screenshots stay in ignored `test-output/design/`. Manual screenshot inspection covered all 20 routes in the selected English dark direction, plus Persian light VISION and the Home direction comparison. This is an agent visual critique, not user testing or manual review of every locale/theme/text-size combination.

| Route | Critique and response |
|---|---|
| Splash | Keep the mark clear in light mode; remove theme interpolation that caused a transient contrast failure. No forced wait. |
| Onboarding | Keep skip visible, privacy explicit and baseline optional. Native onboarding stores actual preferences. |
| Home | Prioritize next action and four equal systems. Prototype sample metrics remain labeled; native uses stored history. |
| Selection | Four consistent cards, no hidden secondary engine. Native Studios implements this hierarchy. |
| VISION overview | Separate comfort notes from performance. Use text beside sage module accent. |
| VISION exercise | Quiet target surface, explicit controls. Browser target is a preview, not a validated eye task. |
| REFLEX overview | Explain latency limits and precision before speed. No population ranking. |
| REFLEX exercise | Stable high-signal stimulus, no decorative motion. Native timer remains monotonic. |
| READ overview | Place comprehension next to pace; distinguish text management from play. |
| READ player | Readable text and pause controls; full personal document import remains outstanding. |
| TYPE overview | Distinguish keyboard context and accuracy. No unsupported universal finger guidance. |
| TYPE exercise | LTR code/input isolation and clear target. Do not persist entered typing text in history. |
| Completion | Summary hierarchy separates validity, accuracy and time. Do not turn invalid practice into rewards. |
| Insights | Label aggregation, date range and baseline filter. Actual daily chart now uses stored records. |
| Achievements | Explain each threshold, locked states and real progress. Native derives unlocks without XP mutation. |
| Library | Search has a real browser filtering action. Preserve native catalogue, favorites and custom builder. |
| Profile | Keep local identity distinct from cloud accounts; expose measured milestones. |
| Settings | Group persistent controls and explain reduced motion. Retain existing profile options. |
| Backup/restore | Mark prototype vault disabled; never represent a mock dialog as authenticated restore. Native integration remains a gate. |
| Error/empty | Supply recovery navigation and calm factual messages, not invented progress. |

Refinements applied across routes: 48 px icon/chip/toggle controls, gold section labels with higher contrast, immediate theme switching and wrapping narrow-screen layouts. Subsequent screenshot critique found black-filled catalogue glyphs, an unknown TYPE preview destination, an inconsistent sample level and an overstated comprehension level range. The glyph stroke, route mapping, sample level and fixed-level label were corrected. The harness now checks every screen link against the 20-route index. All checks pass after these corrections. Native migration is partial. These decisions do not certify engine completeness, device accessibility, performance, signing or release readiness.
