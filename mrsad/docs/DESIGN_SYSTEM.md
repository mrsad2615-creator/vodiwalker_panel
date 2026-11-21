# MRSAD Design System

> One visual language: **COSMIC LUXURY** — cinematic technology. Black is the
> foundation, chrome and titanium build the structure, and carefully selected
> accent colours create energy and distinction.
> Identity rule: *black & silver first; accent colour only where it means something.*

---

## 1. Colour tokens

Defined once in `ui/theme/Theme.kt` (`MrsadColors`), consumed everywhere through
`MrsadTheme.colors`. No feature file contains a raw colour literal.

| Token | Obsidian (brand) | Meaning |
|---|---|---|
| `background` | `#08090C` Obsidian Black | app canvas |
| `backgroundElevated` | `#101B2D` Deep Navy | atmosphere gradient stop |
| `surface` / `surfaceHigh` | `#171A20` / `#1F242E` Graphite | cards, panels |
| `glass` | graphite @ 80 % | translucent panels |
| `border` / `borderStrong` | chrome silver @ 20 % / 40 % | hairlines |
| `textPrimary` | `#F4F6FA` White | headings, values |
| `textSecondary` | `#C7CDD5` Chrome Silver | labels, body |
| `textMuted` | `#777F8A` Titanium | captions, disabled text |
| `metalLight` / `metalMid` / `metalDark` | `#F4F6FA` / `#C7CDD5` / `#777F8A` | gradient metal, primary fills |
| `navy` / `iceBlue` / `electricBlue` / `violet` | `#101B2D` / `#8DCBFF` / `#3478F6` / `#8B70D6` | the cosmic accents, by name |
| `accent` | Ice Blue (Obsidian) · Violet (Quantum/Phantom) · Electric Blue (Crystal) · Ice Blue (Arctic) … | interactive highlight |
| `success` / `warning` / `error` | `#6FD3A8` / `#E8C06A` / `#F1788C` | states — **never brand** |
| `disabled` | `#3A3A47` | inactive |

Distribution guideline: ~70 % dark surfaces, ~25 % silver / titanium / white,
~5 % accent. Accents appear only for interactive states, energy effects, data
visualisation and important highlights — never as decoration.

Green is a semantic success colour only; it is never a brand colour.

### Six themes

| Theme | Canvas | Metal | Accent | Mood |
|---|---|---|---|---|
| Obsidian | `#08090C` | Pearl→Chrome→Silver | Chrome | signature luxury |
| Quantum | `#0A0812` | lilac metals | Violet | spatial / holographic |
| Crystal | `#F2F3F6` (light!) | dark graphite | Royal `#3D6BFF` | pearl glass |
| Arctic | `#060B10` | ice metals | Ice Blue | cold precision |
| Phantom | `#07060A` | muted violet metals | `#B08CFF` | deep black / violet |
| Spectrum | `#07080B` | iridescent silver | `#9BB6FF` | controlled spectrum |

Light theme (Crystal) flips system-bar icon appearance automatically.

---

## 2. Typography

* **Vazirmatn** — UI: excellent Persian shaping, 4 weights (400/500/600/700).
* **Estedad** — display + reading: geometric, high legibility at large sizes.
* **Space Grotesk** — the Latin voice: the wordmark, Latin numerals and every
  Latin string. The UI and display families are *combined* families (Space
  Grotesk first, then the Persian face), so Compose falls back per glyph and
  a mixed line reads as one design.
* All OFL 1.1; licences shipped with the app.

| Style | Size (× font scale) | Use |
|---|---|---|
| `display` | 34 / 46 | screen titles, big numbers in hero |
| `headline` | 24 / 34 | page headers |
| `title` | 18 / 26 | card titles |
| `bodyLarge` | 17 / 30 | reading short-form |
| `body` | 15 / 26 | default text |
| `label` | 13 / 22 | buttons, chips, tabs |
| `caption` | 12 / 19 | meta, hints |
| `metric` | 30 / 40, tabular figures | live metrics (no jitter); the tall line keeps Persian descenders clear |
| `reading` | 21 / 38 | RSVP + reading surface |

Rules:
* Numbers always go through `formatInt / formatPercent / formatNumber`
  respecting the **Persian numerals** setting (e.g. `۱۲۳` vs `123`).
* Font scale (0.85–1.30) is a user setting and scales every style.
* Mixed Persian/Latin text is never manually reordered — the platform bidi
  algorithm handles it; we only pass correct `AnnotatedString` spans.

---

## 3. Layout tokens

```
Spacing   xxs 4 · xs 8 · sm 12 · md 16 · lg 24 · xl 32 · xxl 48
Radius    sm 10 · md 16 · lg 22 · xl 28 · pill 100
Touch     48 dp minimum in both directions
```

* Screen padding: 16 dp horizontal; content bottom padding ≥ 120 dp under
  the floating nav bar.
* Motion is **not** a layout token: durations, easings and springs live in
  `ui/theme/Motion.kt` and are resolved through `MrsadTheme.motion`.

---

## 3a. The motion system

Every animation in the app is built from the tokens in `ui/theme/Motion.kt`
and from nothing else. The durations and the easings are the published
Material 3 tokens; the springs are the Material 3 Expressive springs.

### Two families, never mixed

| Family | Moves | Spring (Expressive) | Overshoot |
|---|---|---|---|
| **spatial** | position, size, scale, rotation — anything that occupies space | fast `0.6 / 800` · default `0.8 / 380` · slow `0.8 / 200` | yes, slightly — that is what makes a movement physical |
| **effects** | opacity, colour — anything that does not occupy space | fast `1.0 / 3800` · default `1.0 / 1600` · slow `1.0 / 800` | never — a colour that overshoots would travel through a colour that is not in the palette |

A spatial spring on a colour is what makes an interface feel cheap; an
effects spring on a position is what makes it feel sluggish. Every
animated property is classified once, in the token file.

### The other tokens

* **Durations** — the Material 3 scale: `short1 50 · short2 100 · short3 150 ·
  short4 200 · medium1 250 … long4 600 · extraLong1 700 … extraLong4 1000`.
* **Easings** — the Material 3 curves: `standard (0.2, 0, 0, 1)`,
  `standardAccelerate (0.3, 0, 1, 1)`, `standardDecelerate (0, 0, 0, 1)`,
  `emphasized (0.2, 0, 0, 1)`, `emphasizedAccelerate (0.3, 0, 0.8, 0.15)`,
  `emphasizedDecelerate (0.05, 0.7, 0.1, 1)`, `legacy (0.4, 0, 0.2, 1)`.
* **Scheme** — `EXPRESSIVE` (the default: springs that give the interface
  its character) or `STANDARD` (utilitarian, almost imperceptible).

### Reduced motion

The setting *کاهش حرکت* (Settings → toggles) switches the spatial family
off completely: the object still has to appear and disappear, but it must
not travel. The effects family keeps a short, calm 200 ms fade, because an
interface that changes without any transition is disorienting. The same
path is used by the instrumentation hook, where every animation is static
so that the tests can reach idle.

### Where each token is used

| Moment | Family | Token |
|---|---|---|
| press of a card or a button | spatial | fast |
| the pill of a navigation destination | spatial | default |
| the colour of an icon | effects | default |
| the entrance of a screen | spatial | default |
| the transition between two screens | spatial + effects | default + fast |
| the reveal of a chart | spatial | default |
| the growth of a progress bar | spatial | default |

---

## 4. Components (`ui/components`)

| Component | Purpose / rule |
|---|---|
| `MrsadSurface` | the only card/panel material (glass + hairline + sheen) |
| `MrsadPrimaryButton` | metallic gradient fill, obsidian text — commit actions |
| `MrsadSecondaryButton` | outlined silver — secondary paths |
| `MrsadChip` | status/metric pills |
| `MetricValue` | tabular numeric readout |
| `MrsadProgressBar` | metallic progress with real values only |
| `SectionHeader` | title + optional trailing action |
| `EmptyState / LoadingState / ErrorState` | every screen must handle all three |
| `SparkLine`, `BarRow` | real-data charts; empty input → "needs more data" |
| `MrsadCore` (ORRERY PRIME) | the 3D centrepiece: a faceted shell around a luminous energy core, three orbital rings, orbiting metallic bodies, a drag and a fling, a shockwave on a tap |
| `MetalIcon` / `MrsadIcons` | the custom icon family: hybrid 3D geometry, machined facets shaded with the metal ramp |
| `chamferedShape` | the hybrid geometric corner: rounded, with the tip cut at 45° |
| `MrsadReveal` | the entrance of a piece of content: a short travel and a fade, staggered |
| `MrsadBottomBar` | 5 destinations, selected pill + label |
| `AmbientBackground` | per-page atmosphere with quality budget |

**No duplicated implementations**: a screen that needs a card uses
`MrsadSurface`; a new toggle uses the same `Switch` styling as Settings.

---

## 4a. The material of a surface

A card is not a rectangle with a border, it is a lit object. `MrsadSurface`
builds it in five layers, from the back to the front:

1. a soft shadow that lifts the card off the page,
2. a vertical gradient, so the card is brighter where the light falls on it,
3. a sheen along the top edge,
4. a shade along the bottom edge, where the card rests on something,
5. a hairline that fades out towards the bottom instead of ringing the card
   like a fence.

The same material is used by the floating navigation bar, which adds a lit
top edge of its own because it hovers over the content.

---

## 5. ORRERY PRIME

The centrepiece of the home screen is a real three-dimensional object,
rendered by hand. There is no bitmap, no 3D library and no fake data in
the pipeline. It is a mechanical cosmic universe: a faceted icosahedral
shell around a luminous energy core, three orbital rings at different
angles, and small metallic bodies riding them at their own speeds.

### The pipeline

| Stage | What happens |
|---|---|
| geometry | a regular icosahedron: 12 vertices, 20 facets, 30 edges (`CoreGeometry`) |
| rotation | `Ry(yaw) · Rx(pitch) · Rz(roll)` |
| projection | a pinhole camera with a real perspective divide (focal 5.6, distance 6.5) |
| shading | a key light, a dim fill, a specular term and a rim term per facet |
| drawing | the facets are sorted back to front and painted; the edges are drawn as light |

The facets are translucent, so the light of the nucleus shines through the
shell instead of being hidden behind it. The orbital rings are split at the
depth of the body: the half behind it is drawn first and the half in front
of it last, so a ring passes *through* the object instead of floating over
it. A breathing energy halo rings the nucleus, and its radius follows the
real goal progress.

### The interaction

| Gesture | Result |
|---|---|
| drag | the object turns with the finger, and tilts |
| fling | it keeps travelling and slows down by friction |
| tap | a shockwave leaves the nucleus |

The idle turntable is very slow (0.16 rad/s) and waits 1.6 s after the last
touch before it takes over again. It is a material, not an animation: it
is what makes the object read as a solid body.

### The data

Nothing on the object is invented:

| Visual | Real value |
|---|---|
| the brightness and the size of the nucleus | `energy` — the progress towards today's goal |
| the sparks in the orbit | `streakDays` — the real streak, up to 24 |
| the graduations of the outer ring | `bestWpm` — the real personal record |

### The budget

The renderer allocates nothing per frame after the first one: every buffer
is reused, and the object is drawn by a single Canvas. The number of
segments in a ring follows the graphics tier (56 / 40 / 24), and so does
the number of orbiting bodies (6 / 4 / 0): on a weak device the orrery
loses its moons before it loses its shape.

---

## 6. Page environments

| Page | Environment | Intensity |
|---|---|---|
| Home | Obsidian Depth — deep black, central core, particles | accent 30 % |
| Type | Titanium Surface — flat, distraction-free, max contrast | 14 % |
| Read | Flow Space — calm, soft light | 12 % |
| Mind | Neural Dimension — spatial, dynamic | 26 % |
| Analytics | Data Universe — structured, subtle tech | 20 % |
| AI | AI Atmosphere — central glow, responsive | 34 % |
| Studio | — live preview of the *current* theme | 26 % |
| Focus | near-still, low light | 10 % |

Rules:
* Backgrounds never overlap text; particle layers sit under content and are
  suspended when the process is not resumed.
* `GraphicsQuality.PERFORMANCE` and "reduce motion" fully disable motion while
  preserving the static atmosphere.

---

## 7. Motion principles

1. **Purpose over flourish** — motion confirms actions or shows real change.
2. **One hero motion per screen** — the Core on Home, the RSVP word on Read.
3. **Nothing spins forever in the background** — the ambient clock and the
   core's turntable both stop when the process is not resumed, and both are
   switched off by the reduce-motion setting.
4. **Cinematic but short** — splash → first frame, no artificial delay.
5. **Accessible** — the reduce-motion path exists for every animation, and
   the spatial family is the thing that is switched off.
6. **Measured, not asserted** — the quality of the interface is measured by
   `PerformanceBenchmarkTest` on a real device: frame times from the
   platform's own `FrameMetrics`, the CPU time of the process and its
   memory. The comparison against the previous revision is produced by the
   `MRSAD Performance` workflow.

---

## 8. Measuring the interface

| Question | Answered by |
|---|---|
| does a frame miss its deadline? | `FrameMetrics.TOTAL_DURATION` per frame, jank over the frame budget of the display |
| how long are the slowest frames? | p50 / p90 / p95 / p99 / max per phase |
| what does the animation cost? | `Process.getElapsedCpuTime()` over the measured window |
| what does it hold? | `Debug.getPss()` and the native heap |
| what does it look like? | the screenshots the same benchmark takes |

Three rules make the number mean something:

1. **The budget is the display's, not a constant.** The frame budget is
   `1000 / refreshRate` of the display the application is drawn on, and
   the refresh rate is written into the report. A fixed 16.67 ms would
   have called every frame of a 30 Hz panel a jank that never happened.
2. **Both revisions are measured on the same emulator, in the same job,
   and the order of the two is swapped after every round.** This is the
   rule that was learned the hard way: while the two sides were measured
   in two jobs of a matrix, they ran on two machines of the cloud, and a
   machine is not the same machine twice. Two campaigns disagreed about
   the same code — one said the redesign was 66 % slower on p95, the
   next said it was 2 % slower — because the second campaign happened to
   run the redesign on a busier machine. One machine, both sides, and
   the order swapped, takes that variable away.
3. **One run is not a measurement.** An emulator shares its cores with
   everything else that runs on it; two runs of the *same* build have
   differed by twenty points of CPU on this machine. Every build is
   therefore measured five times, and the comparison reports the middle
   of the runs together with the spread of the runs.
4. **A difference inside the spread is not a difference.** The verdict of
   the comparison says so in words rather than in a colour.
5. **A session that failed is not a session.** A session whose
   instrumentation failed, or that produced no report, is not counted
   and not used: a session that did not finish cannot describe the
   interface. The report of the session before it is taken away from the
   device before the session starts, so it can never be used twice.

The result is written to `PERF-COMPARISON.md` and published with the
screenshots of both revisions in `benchmarks/screenshots/`.
