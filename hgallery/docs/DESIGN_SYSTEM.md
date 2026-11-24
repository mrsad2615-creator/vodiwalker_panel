# DESIGN_SYSTEM.md

The design system of HGallery.

The colour and motion tokens are in `HgTheme.kt` and `HgMotion.kt`. That code is
the source of truth. This page describes the intent and the reasons.

`NOIR_DESIGN_DIRECTION.md` holds the current design direction.

The reference corpus below is historical. It started from measured pixels of
Huawei reference captures. The measurement script is
`tools/measure_reference.py`. Its output is in `docs/REFERENCE_ANALYSIS.md`.

## Reference corpus

| ID | Screen | Source | Status |
|---|---|---|---|
| R1 | Photos timeline (EMUI 11) | techadvisor EMUI 11 hands-on | verified screenshot |
| R2 | Albums (official simulator) | consumer-tkbdownload.huawei.com `albums.png` | verified, official |
| R3 | Recently deleted + selection | consumer-tkbdownload.huawei.com `restore.png` | verified, official |
| R4 | Full-screen viewer | — | **not yet secured** — behaviour documented as uncertain |
| R5 | Photo editor | — | **not yet secured** — see KNOWN_LIMITATIONS |

## Colour tokens

> **Status: superseded.** `NOIR_DESIGN_DIRECTION.md` replaces the colour and type
> tables below. The code uses the NOIR tokens. The tables in this section show the
> measured values of the reference captures only. Do not use them for new work.

The NOIR tokens are the implemented values. `HgTheme.kt` holds them.

### Light

| Token | Value |
|---|---|
| `background` | `#F7F5F1` |
| `surface` | `#FFFFFF` |
| `surfaceHigh` | `#EFEBE5` |
| `textPrimary` | `#141210` |
| `textSecondary` | `#6B665F` |
| `accent` | `#C66014` |
| `divider` | `#E2DED8` |

### Dark

| Token | Value |
|---|---|
| `background` | `#050505` |
| `surface` | `#111111` |
| `surfaceHigh` | `#191919` |
| `surfaceSunken` | `#0C0C0C` |
| `textPrimary` | `#F2F0EB` |
| `textSecondary` | `#929292` |
| `accent` | `#F28A36` |
| `divider` | `#383838` |
| `danger` | `#E5484D` |

To verify a token, read the value from `HgTheme.kt`. Do not copy a value from
this page.

## Measured values of the reference captures

The capture measurement script is `tools/measure_reference.py`. Its output is in
`docs/REFERENCE_ANALYSIS.md`. These values are historical data.

| Token | Light | Dark |
|---|---|---|
| `page` | `#FFFFFF` | `#000000` |
| `pageAlt` | `#FCFCFC` | `#1C1C1E` |
| `textPrimary` | `#1A1A1A` | `#FFFFFF` |
| `textSecondary` | `#8C8C8C` | `#8C8C8C` |
| `accent` | `#0080FF` | `#0A84FF` |
| `divider` | `#EDEDED` | `#2C2C2E` |

R2 gave the light accent from the solid interior of the `ADD ALBUM` link. The
measured value was `#0080FC`. R3 gave the selection check fill.

The dark values are not pixel-verified against a dark-mode capture.
`KNOWN_LIMITATIONS.md` records this limit.

## Type scale

Read the sizes from `HgTypography.kt`. The scale is 34 / 28 / 22 / 17 / 15 / 13 /
11 sp. Line heights are fixed values for each style, not a multiplier. Large
titles use negative tracking.

Inter is the Latin face. Vazirmatn is the Persian face. Both are in one family,
so a mixed string keeps one line height.

Timecodes use tabular figures (`TimecodeLabel`). Player indicators use
`PlayerIndicatorLabel`.

The table below is the historical reference measurement.

| Role | Size | Weight |
|---|---|---|
| Large screen title ("Photos", "Albums") | 30 sp | 600 |
| Section date header | 15 sp | 500 |
| Section header (MORE ALBUMS) | 12 sp | 500, 0.06 em tracking |
| Album name | 14 sp | 400 |
| Album count | 12 sp | 400, secondary |
| Selection title ("3 items selected") | 17 sp | 500 |
| Bottom-bar label | 10 sp | 400 |
| Body / dialog | 14 sp | 400 |

Persian uses Vazirmatn at the same sizes. Digits follow the locale.

## Spacing scale

`4 8 12 16 20 24 32` dp. Screen gutter is **20 dp**. Grid gap is **3 dp**
(`HgMetrics.GridGap`). Read the values from `HgSpacing` and `HgMetrics`.

## Grid

| Screen | Columns | Cell | Gutter |
|---|---|---|---|
| Photos timeline | 4 | square, `(width − 2×20 − 3×3) / 4` | 3 dp |
| Albums | 3 | 4:3 crop, rounded 6 dp | 12 dp |

## Shape, elevation, motion

Read the shape and motion values from `HgShapes` and `HgMotion`. The motion
durations are in `HgDuration`:

| Token | Value |
|---|---|
| `Micro` | 120 ms |
| `Standard` | 220 ms |
| `Signature` | 340 ms |
| `Large` | 300 ms |
| `ChromeAutoHide` | 3000 ms |

Elevation is none on page content. Floating bars use 8 dp.

## Iconography

Original vector geometry in `HgIcons.kt`. Box is 24 dp. Stroke is 1.8 dp with
round caps and joins. No Huawei asset file is copied.

The application icon is one aperture mark. `ic_launcher_foreground.xml`,
`ic_launcher_monochrome.xml` and `ic_splash_logo.xml` use the same geometry.

## Touch targets

Every control reserves **48 dp** minimum, even when the drawn glyph is 24 dp.

## Persian

Persian uses Vazirmatn at the type-scale sizes. Digits follow the locale. The
UI strings are in `values` and `values-fa`.
