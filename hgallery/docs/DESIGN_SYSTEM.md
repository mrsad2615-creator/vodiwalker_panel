# DESIGN_SYSTEM.md

Derived from **measured pixels** of official Huawei reference captures, not from
memory. The measurement script is `tools/measure_reference.py`; its output is in
`docs/REFERENCE_ANALYSIS.md`.

## Reference corpus

| ID | Screen | Source | Status |
|---|---|---|---|
| R1 | Photos timeline (EMUI 11) | techadvisor EMUI 11 hands-on | verified screenshot |
| R2 | Albums (official simulator) | consumer-tkbdownload.huawei.com `albums.png` | verified, official |
| R3 | Recently deleted + selection | consumer-tkbdownload.huawei.com `restore.png` | verified, official |
| R4 | Full-screen viewer | — | **not yet secured** — behaviour documented as uncertain |
| R5 | Photo editor | — | **not yet secured** — see KNOWN_LIMITATIONS |

## Colour tokens (light)

| Token | Value | How it was obtained |
|---|---|---|
| `page` | `#FFFFFF` | dominant page pixel |
| `pageAlt` | `#FCFCFC` | second dominant pixel of R2 content area |
| `textPrimary` | `#1A1A1A` | darkest stable glyph cluster in R2 (measured `#242020` at anti-aliased edges) |
| `textSecondary` | `#8C8C8C` | album count labels in R2 |
| `accent` | `#0080FF` | solid interior of the `ADD ALBUM` link in R2, measured `#0080FC` |
| `divider` | `#EDEDED` | 1 px separators |
| `selectionCheck` | `#0080FF` | checked badge fill in R3 |
| `scrim` | `#000000` @ 40 % | viewer chrome behind controls |

## Colour tokens (dark)

| Token | Value |
|---|---|
| `page` | `#000000` |
| `pageAlt` | `#1C1C1E` |
| `textPrimary` | `#FFFFFF` |
| `textSecondary` | `#8C8C8C` |
| `accent` | `#0A84FF` |
| `divider` | `#2C2C2E` |

The dark palette follows the same structure; it is *not* yet pixel-verified
against a Huawei dark-mode capture, and `KNOWN_LIMITATIONS.md` says so.

## Type scale

| Role | Size | Weight | Evidence |
|---|---|---|---|
| Large screen title ("Photos", "Albums") | 30 sp | 600 | R1/R2 title block is roughly 2.4× the body line height |
| Section date header | 15 sp | 500 | R1 "Today" / "29 August 2020" |
| Section header (MORE ALBUMS) | 12 sp | 500, 0.06 em tracking | R2 upper-case header |
| Album name | 14 sp | 400 | R2 |
| Album count | 12 sp | 400, secondary | R2 ("22", "1", "3") |
| Selection title ("3 items selected") | 17 sp | 500 | R3 |
| Bottom-bar label | 10 sp | 400 | R2 Gallery tab labels |
| Body / dialog | 14 sp | 400 | R3 explanatory paragraph |

Persian uses Vazirmatn at the same sizes; digits follow the locale.

## Spacing scale

`4 8 12 16 20 24 32` dp. Screen gutter is **20 dp** (measured from R1: the title
and the first thumbnail column share the same left edge). Grid gutter is **2 dp**,
matching the near-seamless Huawei masonry.

## Grid

| Screen | Columns | Cell | Gutter |
|---|---|---|---|
| Photos timeline | 4 | square, `(width − 2×20 − 3×2) / 4` | 2 dp |
| Albums | 3 | 4:3 crop, rounded 6 dp | 12 dp |
| Viewer thumbnails (selection) | 4 | square | 2 dp |

## Shape, elevation, motion

| Token | Value |
|---|---|
| Album thumbnail radius | 6 dp |
| Bottom sheet radius | 20 dp |
| Dialog radius | 16 dp |
| Elevation | none on page content; 8 dp on floating bars only |
| `motionFast` | 150 ms, `FastOutLinearInEasing` |
| `motionBase` | 250 ms, `FastOutSlowInEasing` |
| `motionEmphasised` | 400 ms, `FastOutSlowInEasing` |
| Hero (viewer open/close) | 300 ms, container transform |

## Iconography

Original vector geometry in `HgIcons.kt`, drawn to Huawei's optical weight:
24 dp box, 1.8 dp stroke, round caps and joins. No Huawei asset file is copied.

## Touch targets

Every control reserves **48 dp** minimum, even when the drawn glyph is 24 dp.
