# REFERENCE_ANALYSIS.md

What the Huawei Gallery reference actually shows, and how each observation was
obtained. Measurements were made with Pillow on the reference PNGs
(`tools/measure_reference.py`), not estimated from memory.

## Corpus

| ID | Screen | Source | Confidence |
|---|---|---|---|
| R1 | Photos timeline, EMUI 11 | techadvisor EMUI 11 hands-on | high — real device capture |
| R2 | Albums tab | consumer-tkbdownload.huawei.com `albums.png` (official support simulator) | **highest — Huawei's own** |
| R3 | Recently deleted, multi-select | consumer-tkbdownload.huawei.com `restore.png` | **highest — Huawei's own** |
| R4 | Full-screen viewer | not secured | **none — see below** |
| R5 | Photo editor | not secured | **none** |
| R6 | Discover / Highlights *content* | not secured | **none** |

## R1 — Photos timeline

Observed structure, top to bottom:

1. Status bar, then a toolbar with a search glyph and an overflow glyph at the
   right edge.
2. A large left-aligned **"Photos"** title.
3. A smaller, centred line: `372 photos and 19 videos`. The noun is pluralised
   (`19 videos`), so the count is not a fixed string.
4. Day sections: `Today`, then absolute dates (`29 August 2020`).
5. A seamless four-column grid, roughly 2 dp between cells.

The title is part of the scrolling page, not a pinned bar — it is drawn above the
first date header and shares the grid's left gutter.

## R2 — Albums

1. Toolbar: search, overflow.
2. Title **"Albums"**.
3. A row of three "smart" tiles: **All photos**, **Videos**, **Camera**. Their
   covers are 2×2 mosaics of up to four items. The Videos cover carries a large
   play badge.
4. Under each tile: the name, then a count on its own line (`22`, `1`, `3`) in a
   lighter, smaller weight.
5. A section header **MORE ALBUMS** in upper case with letter-spacing, and an
   **ADD ALBUM** action in accent blue on the same line.
6. Device folders as tiles: `Screenshots`, `Others`, `Recently del...` (truncated
   in the reference itself).
7. A four-cell bottom bar: **Photos · Albums · Highlights · Discover**, the active
   item in accent blue with its label, the others in grey.

## R3 — Recently deleted

Multi-select toolbar shows a close glyph, then `3 items selected` as the title, and
an explanatory paragraph: *"Photos and videos will be permanently deleted after
the number of days shown (up to 30 days)."* Below the grid, a row of actions:
**Restore · Delete · Select all**. Selected tiles carry a circular blue check at
the top-right and are dimmed.

**This is not encryption.** The screen is an ordinary album with a delayed delete.
Nothing in this project will describe it as secure or encrypted storage.

## Measured colours

Sampled from R2 by counting the most frequent quantised pixel values:

| Surface | Measured | Adopted |
|---|---|---|
| Page background | `#FCFCFC` (75 % of a content block) | `#FFFFFF` page, `#FCFCFC` alt |
| Accent (ADD ALBUM interior) | `#0080FC` | `#0080FF` |
| Primary text (glyph core) | `#242020` at anti-aliased edges | `#1A1A1A` |
| Secondary (album counts) | `#8C8C8C` | `#8C8C8C` |

Anti-aliased glyph interiors never reach pure black in a screenshot, so the text
token is set slightly darker than the measured edge value rather than equal to it.

## What is *not* verified — and will not be claimed

* **R4 viewer icon order.** The bottom action row is implemented as
  Share / Favourite / Edit / Delete / Details on the strength of how EMUI's viewer
  behaves, not on a capture. Documented in `KNOWN_LIMITATIONS.md`.
* **Editor with no reference.** The spec lists crop, rotate, flip, exposure and
  filters. An editor that fakes the result of a slider would be dishonest, and a
  real one (tile-based non-destructive rendering, undo stack, export through
  MediaStore) is a project in itself. It is **not implemented**, and the Edit
  button is therefore wired to nothing yet rather than pretending.
* **Dark theme parity.** A Huawei dark-mode capture was not secured. The dark
  palette follows the light palette's structure and the tokens are in place, but
  it is not claimed to be pixel-verified.
* **120 Hz / thermal behaviour.** Cannot be measured: there is no POCO X3 Pro and
  no emulator that reports 120 Hz. See `PERFORMANCE_REPORT.md`.
