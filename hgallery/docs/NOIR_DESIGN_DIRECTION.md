# NOIR — Design Direction and Interaction Specification

Document: the approved visual direction, the gesture rules, and the motion rules.
Status: design, with the first sections implemented. The colour tokens, the
application icon, the home header, the filter chips, the viewer's swipe dismissal
and the three-phase launch are in the code and verified on an emulator capture.
Every preview in `docs/design/` is still a design reference, not a screenshot of
the application.
Read section 11 before you compare a preview with the build.

This document follows the sentence rules of ASD-STE100 Issue 9 (2025-01-15):
short sentences, active voice, American English. It is not a certified
compliance review. Read `docs/TERMINOLOGY.md` for the terms this project uses.

## 1. The problem this direction fixes

The application had a design system document and did not implement it. Measured
against commit `e1500b1`:

| The directive asks for | The code had |
|---|---|
| NOIR `#050505`, `#111111`, `#F28A36` | `#F7F7F9` and `#0068E8` — a light gray page with a blue accent |
| Orange for important actions only | blue in 12 UI sites |
| HGallery Ignition, three phases | implemented in `ui/brand/HgIgnition.kt`: 220 ms draw, 400 ms iris, 280 ms lift |
| A shared-element transition from grid to viewer | none |
| A staggered entrance for grid tiles | `staggerFraction` written, never called |
| Automatic filtering from the chips | implemented: `GalleryFilter` plus the chip row |
| Swipe down to close a photograph | implemented, with the zoom rule: a zoomed image pans |
| Brightness, volume and lock gestures in the player | absent |

The cause is not a bad design. The cause is that the design stopped at the
document. This direction starts at the code and the previews are the contract.

## 2. Visual direction

**One sentence:** a black room with the work lit in it.

1. The background is `#050505`. Not near-black, not gray. A photograph must be
   the brightest thing on the screen, and the product must never look like a
   paper application with a dark mode bolted on.
2. Surfaces are almost invisible. `#111111` and `#191919` differ from the
   background by less than 25 levels of luminance. Separation comes from a
   `#383838` border of 1 dp, not from a large shadow.
3. One accent. `#F28A36` marks exactly four things: the current tab, an active
   filter chip, the current filmstrip cell, and the play head. If an orange
   element is not one of those four, remove the orange.
4. Controls float. Bars are replaced by pills that sit above the content with a
   20 dp margin. A full-width bar touches the screen edge and reads as a system
   element; a floating pill reads as part of the product.
5. Corners are large and consistent: 10 dp for a tile, 18 dp for a panel, 30 dp
   for a dock, full round for a control. A 2 dp corner on a photograph reads as
   a spreadsheet cell.
6. Type does the work that color does not. Titles are large and tightly
   tracked. Counts and metadata are small, gray and quiet.

### 2.1 Why the light theme keeps the same identity

The light theme is not a second design. It inverts the ground and keeps the
rules:

- background `#F7F5F1` (warm paper, not white, so a photograph still reads as
  the brightest object)
- surface `#FFFFFF`, border `#E2DED8`
- text `#141210`, secondary `#6B665F`
- accent `#C66014` — the same hue as `#F28A36`, darkened until it passes 4.5:1
  on paper. The accent is not allowed to fail contrast in the light theme, and a
  lighter orange cannot do that on a light ground.

Both themes are shipped. The default follows the system, as approved.

## 3. Color tokens

| Token | Dark (default) | Light | Use |
|---|---|---|---|
| `background` | `#050505` | `#F7F5F1` | The page behind everything |
| `surface` | `#111111` | `#FFFFFF` | Cards, panels, docks |
| `surfaceHigh` | `#191919` | `#EFEBE5` | A pressed card, the selected segment |
| `surfaceElevated` | `#222222` | `#FFFFFF` | A sheet above a sheet |
| `border` | `#383838` | `#E2DED8` | 1 dp separation |
| `textPrimary` | `#F2F0EB` | `#141210` | Titles and values |
| `textSecondary` | `#929292` | `#6B665F` | Counts, metadata, inactive labels |
| `accent` | `#F28A36` | `#C66014` | The four marked states |
| `onAccent` | `#0C0804` | `#FFFFFF` | Text on an accent fill |
| `accentContainer` | `#2A1C10` | `#FBEDE0` | A tinted pill, such as the volume control |
| `danger` | `#E5484D` | `#C22B30` | Delete only |
| `scrim` | `#000000` at 55% | `#000000` at 40% | Behind a sheet |

No gradients. No purple, pink or neon. No color as the only signal: the current
filmstrip cell has a 2 dp outline **and** an accent underline, and the current
tab has a filled pill **and** an accent glyph.

## 4. Type scale

Families: Inter for Latin, Vazirmatn for Persian, in one `FontFamily` so mixed
text resolves per glyph. Both are already bundled; nothing is downloaded.

| Role | Size / line / tracking | Weight |
|---|---|---|
| Screen title | 34 / 40 / −0.6 | SemiBold |
| Sheet title | 21 / 27 / −0.2 | SemiBold |
| Section title | 15 / 20 / 0 | SemiBold |
| Body | 14 / 20 / 0.1 | Regular |
| Metadata | 12 / 16 / 0.15 | Regular |
| Overline (for example `TODAY`, `2 VIDEOS`) | 11 / 15 / +1.1 | SemiBold, uppercase |
| Timecode | 12 / 16 / 0.4 | Medium, `tnum` |

`tnum` on every number that can change while it is visible. A timecode that
changes width makes the whole row twitch.

## 5. Spacing, radius, touch

- Spacing steps: 4, 8, 12, 16, 20, 24, 32, 40.
- Screen margin: 20 dp. This is the one measured value from the reference, and
  it stays.
- Grid gap: 3 dp inside a day block, 12 dp between blocks.
- Radius: tile 10, cell 12, panel 18, sheet 28, dock 30, control full round.
- Minimum touch target: 48 dp, including a 24 dp glyph centred in it.
- Icon stroke: 1.8 dp at 24 dp. One weight everywhere. No mixed styles.

## 6. Home

- **Header.** A 34 sp title, then a count line. Two 34 dp round actions at the
  right: search, then filter. The filter opens a sheet; it is not a second row.
- **Filter chips.** One scrollable row under the header. The active chip is
  filled with the accent. Tapping a chip filters the grid in place, without
  leaving the screen. This is the behaviour the directive asks for and the
  current build does not have.
- **The hybrid grid.** A day block is not a uniform 4-column grid. Each block
  opens with one hero that spans 2 columns by 3 rows, then small cells fill the
  remaining columns. The rhythm changes at every block. A uniform grid is the
  single largest reason a gallery reads as generic.
- **Video cells.** A poster frame and a duration badge. No second icon system,
  no play glyph in the corner of every clip.
- **The dock.** A 60 dp pill, 20 dp from each edge, containing four
  destinations. The current one gets an `#191919` pill and an accent glyph and
  an accent label. Labels are 8.5 sp, visible, not a dot.
- **Loading.** A skeleton cell is a `#111111` block with a slow luminance sweep.
  Never a black hole and never a spinner per tile.

## 7. Photo viewer

- **Ground.** Pure black. The photograph keeps its aspect ratio and is never
  cropped in the default state.
- **Chrome.** A close control, a position counter and two actions float over the
  photograph on 34 dp circles of `#141414`. A top scrim keeps them legible on a
  bright sky. The scrim is a gradient of the photograph's own darkness, not a
  black bar.
- **Info panel, level one.** A pill under the photograph with the file name and
  one metadata line. Level two opens the full EXIF sheet. Two levels, as the
  directive asks; the current build has one and is disabled.
- **Filmstrip.** 54 dp cells, the current one outlined in the accent with an
  underline beneath it. It fades out at the right edge so it reads as
  scrollable.
- **Action row.** Share, favourite, rotate, delete on 42 dp circles. Favourite
  turns the accent. Delete is the only control that uses `danger`, and it opens
  a confirmation.
- **Dismissal.** A vertical drag on an unzoomed photograph moves it with the
  finger and fades the ground. Release above 18% of the screen height, or with
  enough velocity, and it closes. Release below that and it settles back with
  the interruptible spring. **When the photograph is zoomed, a vertical drag
  pans the image and never dismisses it.** This priority is the rule that makes
  the gesture feel correct instead of accidental.

## 8. Video player

- **Frame.** 16:9 inside the portrait screen, floated in black on a `#050505`
  ground. The frame is not edge to edge; the space above and below is what makes
  the floating controls readable.
- **Top dock.** A floating pill of secondary actions: close, aspect, rotate,
  share, gesture lock. The gesture lock is a control, and it is drawn as a
  padlock in a closed or open state. It is not a hint.
- **Volume pill.** Top right, `accentContainer` background, a track and a
  speaker. Its fill follows the volume. It is the reference's one tinted
  element and the reason the accent belongs in the player.
- **Centre transport.** Play and pause at 54 dp in white, with −10 s and +10 s at
  44 dp on either side. White for the primary action, because the primary action
  must be the brightest control.
- **Title and progress.** Title below the frame, then a 3 dp track. The play
  head is a 12 dp accent dot. Elapsed and total are at the two ends.
- **Filmstrip.** 56 dp cells with a duration badge; the current one gets an
  accent outline. It scrolls independently of playback and never blocks it.
- **The dock.** Four destinations: `Info`, `InSight`, `Speed`, `More`. The
  current one is a tinted pill with an accent label.
- **Auto-hide.** Three seconds after the last touch, and only while the clip is
  playing. A paused clip keeps its chrome. A clip that has ended keeps its
  chrome, because the user has a decision to make.

## 9. Gesture priority

The rules are ordered. A higher number never wins over a lower one. This table
is the arbitration contract, and each rule is testable.

### 9.1 Player

| Priority | Gesture | Region | Result |
|---|---|---|---|
| 1 | Tap on the gesture-lock control | Dock | Locks or unlocks input |
| 2 | Any touch while locked | Whole screen | Shows a HUD that says input is locked, and does nothing else |
| 3 | Drag on the progress track | Track | Seeks; the play head follows the finger |
| 4 | Drag on the filmstrip | Filmstrip | Scrolls the strip; does not seek |
| 5 | Horizontal drag elsewhere | Frame | Seeks by distance, with a HUD that shows the target time |
| 6 | Vertical drag, left half | Frame | Changes brightness |
| 7 | Vertical drag, right half | Frame | Changes volume |
| 8 | Double tap, left third | Frame | Seeks back 10 s |
| 9 | Double tap, right third | Frame | Seeks forward 10 s |
| 10 | Long press | Frame | Plays at 2× while held; returns to the previous speed on release |
| 11 | Vertical drag downward, unzoomed | Frame | Dismisses at the thresholds in section 7 |
| 12 | Single tap | Frame | Shows or hides the chrome |

Rules 5 to 7 share one `pointerInput`. The first movement axis that crosses
8 dp wins and the others are cancelled for that gesture, so a seek can never
turn into a brightness change halfway through.

### 9.2 Viewer

| Priority | Gesture | Result |
|---|---|---|
| 1 | Pinch | Zooms about the focal point between the two fingers |
| 2 | Drag while zoomed | Pans, clamped to the image edges |
| 3 | Drag while unzoomed, vertical | Dismisses |
| 4 | Drag while unzoomed, horizontal | Pages |
| 5 | Double tap | Zooms to 2.5× at the tap point, or returns to 1× |
| 6 | Single tap | Shows or hides the chrome |
| 7 | Swipe from either screen edge | The system back gesture wins. The viewer must not consume it |

Rule 7 is explicit because a viewer that eats the back gesture is a viewer a
user cannot leave with one hand.

## 10. Motion

Phase durations from the directive. Every duration is a token, and every token
has one purpose.

| Token | Duration | Curve | Use |
|---|---|---|---|
| `micro` | 100–160 ms | `emphasizedDecelerate` | Press feedback, chip fill, icon state |
| `standard` | 180–260 ms | `emphasizedDecelerate` | Chrome show and hide, panel expansion, filmstrip move |
| `signature` | 280–400 ms | spring, damping 0.9 | Grid to viewer, viewer to grid, dock morph |
| `large` | 250–350 ms | `emphasizedDecelerate` | A whole sheet |

Rules:

1. **Interruptible.** Spatial motion uses a spring, not a duration with an
   easing curve, so a gesture that reverses mid-flight keeps its velocity.
2. **No animation hides slow work.** A skeleton is not a substitute for a fast
   query. The splash never delays the start: the first frame is drawn as soon as
   the window exists, and the mark animates over content that is already there.
3. **Reduced motion.** If the platform animation scale is below 0.05, every
   token collapses to an instant change. The state change still happens; only
   the interpolation goes.
4. **One animated value per screen, not one per tile.** A staggered entrance
   drives twenty tiles from a single progress value. Twenty animations on a
   3 GB phone is a frame drop the user can see.
5. **Motion must state something.** A tile scales to 0.96 under a finger. A
   filmstrip cell gains an outline. A favourite fills. Nothing moves only to
   look busy.

## 11. What the previews are, and what they are not

The previews in this directory were rendered from real pixels: the photographs,
the clip frames and the filmstrip cells are cropped from this project's own
emulator screenshots under `hgallery/evidence/`. The interface was drawn at 390
× 844 dp, supersampled and downscaled, in Inter, which is the font the
application ships.

They are still **design references**. They are not screenshots of the
application, because none of this is implemented yet. Do not quote a preview as
evidence that a screen works. The only evidence of a working screen is an
emulator capture produced by `.ci/run-connected-tests.sh` and committed under
`hgallery/evidence/`.

Preview files:

| File | Screen |
|---|---|
| `00-board.png` | All four screens in order |
| `01-home.png` | Home with the hybrid grid and the floating dock |
| `02-viewer.png` | Photo viewer with the two-level info panel |
| `03-player.png` | Video player, chrome visible |
| `04-panel.png` | Player's `InSight` panel |
| `05-brand.png` | H Aperture Signature at four sizes and through four launcher masks |

The generator scripts are kept beside the images so a change to a token can be
rendered again without redrawing the screens. They need Pillow 13.

## 12. Logo direction

**H Aperture Signature.** A six-blade iris with an open housing.

- Two arcs of 164° form the housing, open at the top left and bottom right.
- Six blades are chord lines that rotate inward. The gap they leave is a
  hexagon, which is the pupil.
- The pupil is filled. It is the only solid shape in the mark, so it is what
  survives at the smallest size.
- At 24 dp the blades merge into a ring and the filled pupil still reads. That
  is deliberate: the mark is designed for 24 dp first and drawn larger second.
- It works in three treatments: accent on black, dark on accent, and one
  monochrome tint for themed icons.
- It survives circle, squircle and rounded-square masks without a second
  version, because the mark is centred and the blades already curve.

## 13. Implementation order

The directive lists nine stages. This direction changes none of them, and it
fixes what "stage 2" means, because stage 2 was marked done without shipping a
token.

1. Tokens: color, type, spacing, radius, motion. Replace `HgColorsLight` and
   `HgColorsDark` values. The `hgColors.accent` call sites already exist, so the
   accent moves for free.
2. The mark, as vectors: foreground, background, monochrome, notification,
   shortcut.
3. Home: chips filter, hybrid grid, floating dock, skeleton loading.
4. Viewer: vertical dismissal, two-level panel, shared-element entry.
5. Player: gestures, HUD, lock, the `InSight` panel.
6. Motion: stagger, press, shared element, auto-hide.
7. Evidence: an emulator capture for every screen above, so the previews can be
   replaced with photographs of the real application.

Stage 7 is the gate. A screen in this document is not complete until the
emulator has drawn it and the capture is committed.
