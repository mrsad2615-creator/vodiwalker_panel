# UI design specification

See [design research](13-design-research.md) and the [shared design system](14-design-system.md) for the 20-route prototype, critique and current Quiet Precision implementation rules.

## Identity

Use an angular interlocking N emblem. Use a champagne surface treatment for the in-app mark. Use a simple vector silhouette at small sizes. Keep the notification icon monochrome. The supplied SVG files are editable originals, not generated screenshots. No complex raster asset is required.

## Tokens

Dark background: `#0C0E11`. Dark card: `#181B20`. Warm text: `#F2EDE4`. Default requested accent: `#D4B982`.

Light background: `#F6F2E9`. Light card: `#FFFAF2`. Graphite text: `#202329`. Default requested accent: `#765A28`.

Use Material color roles in each screen. Keep error colors independent of the accent. The contrast function can adjust a requested accent before use. Keep the requested dark and light values in storage.

Use Vazirmatn Regular and Bold from bundled TTF files. Its OFL licence is included. The font includes Persian and Latin glyphs. Keep platform shaping and bidirectional layout. Set RTL for Persian and LTR for English. Never reverse strings. Apply the system font scale and the profile font scale together.

## Structure and behavior

Home uses an editorial heading, a measured progression panel, a responsive four-module grid, interruption recovery and real recent activity. With no valid sessions, show an empty state. Level 1 with zero XP is the initial rule, not invented progress.

Training setup shows the real instructions before start. The run screen uses visible pause and resume controls. The VISION grid uses labelled cells. The REFLEX surface uses both text and color. READ separates text and questions. TYPE accepts IME text through a text field, not only physical key events.

Use 48 dp minimum interactive targets. Test small screens and large font sizes before acceptance. The shared presentation now has an optional 160 ms content entrance outside RUN and RESULT. Reduced motion removes it. Android 12+ uses the original mark for the system splash without a forced delay. Adaptive and monochrome launcher icons are included. These changes still require device animation and accessibility acceptance.

The theme preview contains an explicitly labelled sample progress bar. It is not user performance data. No dashboard progress value is sampled or randomized.

## Acceptance still required

Review Persian mixed-direction punctuation, English labels, large text, focus order, TalkBack descriptions, tablet layouts and landscape keyboards on Android. Do not equate source labels or syntax validation with accessibility acceptance.

## Additional exercise surfaces

Keep exactly four module cards on Home. The training selector and library expose fourteen individual exercises. Use exercise names, not duplicate module labels, in the selector.

Visual Search shows an upright T sample and 16 equal-size cells. Use the same color, stroke and card treatment for targets and distractors. Describe each rotated shape in the accessibility label. Do not distinguish the target only by a highlight.

GO/NO-GO uses words and a circle/cross, not color alone. Phrase Reading shows the current phrase and measured phrase progress, then comprehension options. Word Sprint shows the current word index and accepts each word separately. Typing input is held in non-saveable memory and clears on word change or pause.

## Builder and additional surfaces

The library has Original, My Exercises and Favorites areas. Favorites can contain both original identifiers and profile-owned custom UUID references. Keep visible edit, duplicate, favorite and confirmed-delete actions. Show an invalid-template error instead of running an unreadable saved definition.

The builder supports name, module, one of fourteen templates, rounds, difficulty, response window for timed modules and success criteria. Display the effective window after difficulty adjustment. For reading/typing, say that duration depends on completion; do not label an unused timer as a fixed session duration. Offer a real practice session without rewards. Persist a definition before navigating back on Save.

Pattern Match uses equal-color nine-cell samples and four equal-density choices. Shape Reaction uses both geometric cues and named choices. RSVP uses one word per frame. Code Typing uses an LTR code surface, preserves newlines, and does not execute target or input content.

## Journeys added in 0.5

Home and Train open equal studio overviews for all four modules. Each overview has actual seven-day activity, a least-practiced-task suggestion and the full implemented exercise list. Setup opens only after a deliberate choice. A new setup revision resets stale configuration when an exercise is explicitly chosen; leaving for settings and returning retains the current setup. Keep the optional onboarding baseline flag correct even after a previous manual toggle.

First launch opens a skippable three-step local introduction. Profile settings include an optional 5–60 minute goal. Actual validated session time drives the goal. Profile opens six computed milestones. Daily chart dates are localized and chart chronology follows reading direction; physical reaction arrows never do. Route/profile saveable state is separate from transient typing drafts and engine timing.
