# UI design specification

## Identity

Use an angular interlocking N emblem. Use a champagne surface treatment for the in-app mark. Use a simple vector silhouette at small sizes. Keep the notification icon monochrome. The supplied SVG files are editable originals, not generated screenshots. No complex raster asset is required.

## Tokens

Dark background: `#0C0E11`. Dark card: `#181B20`. Warm text: `#F2EDE4`. Default requested accent: `#D4B982`.

Light background: `#F6F2E9`. Light card: `#EDE6DA`. Graphite text: `#202329`. Default requested accent: `#765A28`.

Use Material color roles in each screen. Keep error colors independent of the accent. The contrast function can adjust a requested accent before use. Keep the requested dark and light values in storage.

Use Vazirmatn Regular and Bold from bundled TTF files. Its OFL licence is included. The font includes Persian and Latin glyphs. Keep platform shaping and bidirectional layout. Set RTL for Persian and LTR for English. Never reverse strings. Apply the system font scale and the profile font scale together.

## Structure and behavior

Home uses an editorial heading, a measured progression panel, a responsive four-module grid, interruption recovery and real recent activity. With no valid sessions, show an empty state. Level 1 with zero XP is the initial rule, not invented progress.

Training setup shows the real instructions before start. The run screen uses visible pause and resume controls. The VISION grid uses labelled cells. The REFLEX surface uses both text and color. READ separates text and questions. TYPE accepts IME text through a text field, not only physical key events.

Use 48 dp minimum touch targets where the layout permits. Test small screens and large font sizes before acceptance. The current app has no cinematic logo introduction or page choreography. A reduced-motion preference is stored, but decorative motion is not yet enabled in either mode.

The theme preview contains an explicitly labelled sample progress bar. It is not user performance data. No dashboard progress value is sampled or randomized.

## Acceptance still required

Review Persian mixed-direction punctuation, English labels, large text, focus order, TalkBack descriptions, tablet layouts and landscape keyboards on Android. Do not equate source labels or syntax validation with accessibility acceptance.

## Additional exercise surfaces

Keep exactly four module cards on Home. The training selector and library expose twelve individual exercises. Use exercise names, not duplicate module labels, in the selector.

Visual Search shows an upright T sample and 16 equal-size cells. Use the same color, stroke and card treatment for targets and distractors. Describe each rotated shape in the accessibility label. Do not distinguish the target only by a highlight.

GO/NO-GO uses words and a circle/cross, not color alone. Phrase Reading shows the current phrase and measured phrase progress, then comprehension options. Word Sprint shows the current word index and accepts each word separately. Typing input is held in non-saveable memory and clears on word change or pause.

## Builder and additional surfaces

The library has Original, My Exercises and Favorites areas. Favorites can contain both original identifiers and profile-owned custom UUID references. Keep visible edit, duplicate, favorite and confirmed-delete actions. Show an invalid-template error instead of running an unreadable saved definition.

The builder supports name, module, one of twelve templates, rounds, difficulty, response window for timed modules and success criteria. Display the effective window after difficulty adjustment. For reading/typing, say that duration depends on completion; do not label an unused timer as a fixed session duration. Offer a real practice session without rewards. Persist a definition before navigating back on Save.

Pattern Match uses equal-color nine-cell samples and four equal-density choices. Shape Reaction uses both geometric cues and named choices. RSVP uses one word per frame. Code Typing uses an LTR code surface, preserves newlines, and does not execute target or input content.
