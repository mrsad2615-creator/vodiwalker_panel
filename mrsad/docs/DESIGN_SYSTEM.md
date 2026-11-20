# MRSAD Design System

> One visual language: **Eclipse** (luxury metal) + **Quantum** (spatial depth) +
> **Nexus** (futuristic tech) + **Pure** (minimal clarity).
> Identity rule: *black & silver first; accent colour only where it means something.*

---

## 1. Colour tokens

Defined once in `ui/theme/Theme.kt` (`MrsadColors`), consumed everywhere through
`MrsadTheme.colors`. No feature file contains a raw colour literal.

| Token | Obsidian (brand) | Meaning |
|---|---|---|
| `background` | `#08090C` Obsidian | app canvas |
| `backgroundElevated` | `#0B0D11` | atmosphere gradient stop |
| `surface` / `surfaceHigh` | `#17191F` / `#1E2129` Graphite | cards, panels |
| `glass` | graphite @ 80 % | translucent panels |
| `border` / `borderStrong` | silver @ 20 % / 40 % | hairlines |
| `textPrimary` | `#F5F6F8` Pearl | headings, values |
| `textSecondary` | `#AEB4BE` Silver | labels, body |
| `textMuted` | `#6E7480` | captions, disabled text |
| `metalLight` / `metalMid` / `metalDark` | `#F5F6F8` / `#D8DDE5` / `#AEB4BE` Chrome | gradient metal, primary fills |
| `accent` | Chrome (Obsidian) · Violet `#8A75FF` (Quantum) · Ice `#5DC8E8` (Arctic) … | interactive highlight |
| `success` / `warning` / `error` | `#6FD3A8` / `#E8C06A` / `#F1788C` | states — **never brand** |
| `disabled` | `#3A3A47` | inactive |

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
* Both OFL 1.1; licences shipped with the app.

| Style | Size (× font scale) | Use |
|---|---|---|
| `display` | 34 / 46 | screen titles, big numbers in hero |
| `headline` | 24 / 34 | page headers |
| `title` | 18 / 26 | card titles |
| `bodyLarge` | 17 / 30 | reading short-form |
| `body` | 15 / 26 | default text |
| `label` | 13 / 20 | buttons, chips, tabs |
| `caption` | 11.5 / 18 | meta, hints |
| `metric` | 30 / 34, tabular figures | live metrics (no jitter) |
| `reading` | 21 / 38 | RSVP + reading surface |

Rules:
* Numbers always go through `formatInt / formatPercent / formatNumber`
  respecting the **Persian numerals** setting (e.g. `۱۲۳` vs `123`).
* Font scale (0.85–1.30) is a user setting and scales every style.
* Mixed Persian/Latin text is never manually reordered — the platform bidi
  algorithm handles it; we only pass correct `AnnotatedString` spans.

---

## 3. Layout & motion tokens

```
Spacing   xxs 4 · xs 8 · sm 12 · md 16 · lg 24 · xl 32 · xxl 48
Radius    sm 10 · md 16 · lg 22 · xl 28 · pill 100
Motion    fast 140 ms · normal 240 · slow 420 · cinematic 760
```

* Screen padding: 16 dp horizontal; content bottom padding ≥ 120 dp under
  the floating nav bar.
* Cards use 1 dp silver hairline + radial top sheen (the "material" signal).
* Press feedback: 0.985 scale, 140 ms — subtle, never bouncy.

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
| `MrsadCore` | living procedural core (energy = goal progress, sparks = streak) |
| `MrsadBottomBar` | 5 destinations, selected pill + label |
| `AmbientBackground` | per-page atmosphere with quality budget |

**No duplicated implementations**: a screen that needs a card uses
`MrsadSurface`; a new toggle uses the same `Switch` styling as Settings.

---

## 5. Page environments

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

## 6. Motion principles

1. **Purpose over flourish** — motion confirms actions or shows real change.
2. **One hero motion per screen** — the Core on Home, the RSVP word on Read.
3. **Nothing spins forever in the background** — animations pause with the
   process lifecycle.
4. **Cinematic but short** — splash → first frame, no artificial delay.
5. **Accessible** — reduce-motion path exists for every animation.
