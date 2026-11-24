# Flagship design research and decision record

Date: 2026-11-24. Baseline: native 0.4.0-dev, commit `4d96388`; 14 implemented exercises and 34 passing instrumented cases. This is a new product-wide design cycle, not acceptance of the unfinished product.

## Method and limits

Read the official product pages, Android documentation and the published Duolingo design case study listed below. Inspect published descriptions and available page imagery. Do not claim a hands-on audit of installed commercial apps, a usability study, a scientific efficacy review or a measured competitor-performance comparison. Website imagery is not evidence of screen-reader behavior. Do not copy screenshots, proprietary fonts, mascots, illustrations or components into NEUROX.

The Nothing official OS URLs failed to load. Use its attributed iF Design submission as a secondary visual reference. An attempted Eyeleo domain returned unrelated gambling content. Reject that source entirely; use the SafeEyes project for the visual-comfort category instead. Promotional speed/cognitive-benefit claims are not NEUROX requirements.

## Evidence ledger

| Reference | Evidence read | Extracted principle | Do not import |
|---|---|---|---|
| Apple Fitness+ | Official support describes For You, Explore, Plans, Library, Search, preview/start and optional live metrics [1](https://support.apple.com/en-us/108761) | Separate discovery, preparation, active execution and summary. Keep one primary start action. Surface only relevant live metrics | Subscription/account dependency, comparative Burn Bar, exercise imagery or medical authority |
| Peak | Official page describes short workouts, Coach, progress and a game catalogue [2](https://www.peak.net/) | Organize small sessions into understandable skill families. Explain recommendations and show the exercise before launch | Marketing efficacy statements, pressure language or an opaque intelligence score |
| Elevate | Official product page separates practical skill groups and performance feedback [3](https://themindcompany.com/apps/elevate) | Explain what a task practices; keep comprehension separate from pace. Use concise editorial titles and localized body text | Percentile claims without a reference population, paywalls, cloud accounts or survey claims as causal evidence |
| Duolingo | Designers document inconsistent headers/spacing, four explored directions, tiered headers and the trade-off between consistency and purpose [4](https://blog.duolingo.com/core-tabs-redesign/) | Use shared header tiers across every module. Diverge internally before selecting. Do not give every page an oversized decorative hero | Mascot, bright game palette, guilt-based streaks, competitive social pressure |
| Nothing OS | Attributed iF submission emphasizes intentional minimalism, monochrome aesthetics and user control [5](https://ifdesign.com/en/winner-ranking/project/nothingos/714658) | Use quiet surfaces, disciplined symbols and limited signal color. Avoid competing accents | Dot-matrix body copy, proprietary visual assets, brand mimicry or an unverified accessibility claim |
| Material 3 / Expressive | Official site describes adaptive components, flexible type, contrast in shapes and token-driven motion [6](https://m3.material.io/) | Preserve native component semantics. Use expression at navigation and completion, not while measuring reaction latency | Blind adoption of bounce, heavy morphing during training or an unnecessary dependency migration |
| Android accessibility | Compose documentation specifies appropriate API semantics and minimum 48 dp interactive targets [7](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) | Native switches/buttons, labels for custom graphics, adequate touch targets and alternatives to gestures | Assuming default semantics eliminate the need for TalkBack and keyboard testing |
| Android performance | Official startup guide distinguishes TTID/TTFD and cold/warm/hot starts [8](https://developer.android.com/topic/performance/issues/launch-time) | No forced splash delay. Observe initialization, remove animation from measurement surfaces, measure real startup later | Invented startup or frame-time numbers |
| TypingClub | Official page describes posture guidance, lessons, optional profile, feedback and replay [9](https://www.typingclub.com/) | Pair accuracy with speed. Explain keyboard-specific limitations and weak keys. Keep instructions close to input | Universal finger guidance for Android virtual keyboards or replay that silently records entered text |
| Spreeder | Official page exposes reading modes, adjustable presentation, library, bookmarks and import [10](https://www.spreeder.com/) | Separate reading material management from the player. Make pace, position and comprehension legible | Cloud/AI dependency, DRM bypass or marketing multipliers |
| SafeEyes | Open-source project describes visual-comfort break reminders [11](https://github.com/slgobinath/SafeEyes) | Provide an easy exit, comfort information and motion control in VISION | Medical treatment claims or coercive break locks |
| Loop Habit Tracker | Official site emphasizes reminders, charts and long-term habits [12](https://loophabits.org/) | Prefer legible trends and optional goals to punitive streaks | Copying GPL implementation or product art; research is not code reuse |

## Cross-reference critique

**Hierarchy:** Use a small brand header, a clear editorial page title, one dominant action and supporting measurements. Do not show an empty ring as earned progress. A new profile gets an honest first-session state.

**Navigation:** Four engines need equal prominence. Home opens a dedicated overview for each engine; the overview opens exercise setup. Library, insights, profile and settings remain accessible without a nine-item bottom bar. The engine switcher and back button preserve context.

**Typography:** Use the existing licensed local Vazirmatn family for both languages. Avoid artificial tracking in Persian connected text. Use large numerals only for meaningful measured values. Keep code LTR. Physical arrow directions must not mirror with the locale.

**Color and spacing:** Champagne is the action color, not a performance score. Use muted module accents as redundant identifiers beside icon/text. Increase panel corners and spacing consistently; do not restyle only Home. Use warm paper in light mode rather than simply invert black.

**Behavior and motion:** Native interaction feedback remains. Content entrance can use short opacity changes outside training. Reduced motion removes optional transitions. Never animate stimulus publication or add a countdown delay solely for appearance.

**Accessibility:** Use text alongside symbols. Provide textual chart summaries. Keep touch targets at least 48 dp. Support system font scale in addition to a profile multiplier. Make reading order deliberate in both languages. Device tests remain mandatory.

**Information architecture:** Keep settings, identity and data management distinct. Adaptation explains history requirements and reasons. A backup screen must distinguish selection, validation, preview, confirmation and commit; do not show a success state for an unimplemented restore.

## Three internal directions

Scores below are professional design judgments on a 1–5 scale, not user-study measurements. Equal weights are used to expose trade-offs, not to fabricate scientific precision.

| Direction | Clarity | Brand | Four-module consistency | Comfort | Engineering restraint | Sum |
|---|---:|---:|---:|---:|---:|---:|
| A. Premium Dark Minimalism | 5 | 4 | 5 | 5 | 5 | 24 |
| B. Futuristic Neuroscience | 3 | 4 | 3 | 2 | 2 | 14 |
| C. Refined Editorial Technology | 5 | 5 | 5 | 5 | 4 | 24 |

A uses deep charcoal, champagne, compact metrics and quiet panels. It is clear but can feel generic if every section is the same card. B uses technical grids, spectral accents and frequent motion. It is distinctive but increases visual fatigue and competes with stimuli. C uses strong editorial headings, structured data, generous spacing and restrained instrument-like diagrams. It is useful across reading, typing and reaction tasks.

**Selected: A + C, named “Quiet Precision.”** Use A's low-noise surfaces and C's typography and information hierarchy. Keep B only as precise geometric line art where it explains a module. No neon glow, continuous decorative motion or pseudo-neuroscience imagery.

The browser prototype exposes A/B/C appearance modes for internal comparison, plus light/dark and FA/EN controls. The selected default is Quiet Precision. These are review controls, not a request for the user to choose.

## Prototype review and acceptance

Create 20 named screen routes before broad presentation changes: splash, onboarding, home, training selection, VISION overview/exercise, REFLEX overview/exercise, READ overview/player, TYPE overview/exercise, result, insights, achievements, library, profile, settings, vault and error/empty states.

All prototype measurements must be explicitly labeled sample design content. Prototype actions navigate or change their local presentation. They must not pretend to persist Android sessions, encrypt files, restore data, grant real rewards or measure reaction latency. The Android application must continue to use only real local data.

Review each route at 360 and 430 px widths, FA/EN, dark/light and large text. Check primary action, labels, contrast, focus, overflow, empty states and navigation. Record automated checks separately from manual visual review. Do not call browser views Android screenshots. Do not call prototype coverage product completion.

## Implementation sequence

1. Preserve the 0.4 engine, storage and security behavior.
2. Add tokenized shared components and apply them to all existing screens.
3. Add equal-quality engine overviews, honest local insights and computed achievements.
4. Add persistent onboarding/preferences without a destructive Room change.
5. Add launcher/splash integration without a forced startup delay.
6. Execute regression tests and recover actual native build evidence.
7. Continue the remaining exercises, Reading Studio, planner, audio and integrated vault. The new master specification expands required capabilities; the prior 34-exercise ledger remains a minimum, not the complete new scope.

## Review evidence, 2026-11-24

All 20 browser routes now exist. The local test harness completed 320 layout combinations with no overflow failures or page errors and 80 axe route audits with no failures. The first contrast failure was fixed by removing theme-color interpolation. Larger 48 px controls and stronger section-label contrast were applied across the prototype. Manual screenshot review covered all 20 selected-direction English dark routes, the direction comparison and Persian light VISION. This is not a usability study. Catalogue icon fill, TYPE destination and sample-content inconsistencies found during critique were corrected and regression-checked. See `14-design-system.md` for route-level critique and explicit limits; do not describe automated coverage as manual review of every state.

The first native integration adds shared tokens, all four studio overviews, real-history charts and six computed milestones, persistent onboarding and optional daily goals. Existing 14 exercise paths remain. Local checks pass: 230 domain cases, 44 static checks, five SQLite checks and six Python tests; 53 Kotlin files parse. These checks do not resolve Android types or prove runtime behavior. Hosted native run `45497991213` passed build/lint and 44 emulator cases, including preference/navigation/onboarding and repeated-baseline regressions. The full expanded product remains unfinished.
