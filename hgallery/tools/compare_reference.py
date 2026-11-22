#!/usr/bin/env python3
"""Measure layout metrics on a reference capture and an implementation shot.

This does not compute a single "similarity score". A percentage would be
meaningless here: the two images show different photographs, so any pixel
distance is dominated by content, not by design. What is comparable is
*structure* — where the title sits, how wide the gutter is, how many columns the
grid has, how tall the bottom bar is. Those are measured in each image and scaled
to the image width, so captures at different resolutions can be compared.

Usage:
    python3 tools/compare_reference.py REFERENCE.png IMPLEMENTATION.png

KNOWN LIMITATION — read before trusting the numbers
---------------------------------------------------
This tool assumes both images are *full-screen device captures*. None of the
available Huawei references are:

  * R1 is a 341x750 press image. Its photographs run to x=0 while the title
    starts at x=24, which is only possible if the capture was cropped.
  * R2 and R3 are marketing compositions: the phone is drawn at an angle,
    surrounded by callout circles, on a coloured background.

Feeding those images in produces figures that look precise and are not. Only the
measurements that survive this — flat-region colours, and the title cap height
relative to image height — should be read as findings. Absolute gutters cannot be
recovered from the available corpus, and this file will not pretend otherwise.
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image


def load(path: Path) -> np.ndarray:
    return np.asarray(Image.open(path).convert("RGB")).astype(np.int16)


def is_background(px: np.ndarray) -> bool:
    """White or near-white, the page colour of the light theme."""
    return bool(px[0] > 244 and px[1] > 244 and px[2] > 244)


def row_is_content(img: np.ndarray, y: int, threshold: int = 6) -> bool:
    """True if the row has at least `threshold` non-background columns."""
    row = img[y]
    non_bg = sum(1 for px in row if not is_background(px))
    return non_bg >= threshold


def measure(img: np.ndarray) -> dict:
    """Measure the layout structure that is comparable between two captures.

    Every measurement is confined to a row band that provably contains the
    feature being measured. An earlier version scanned whole images and happily
    reported the status bar as the "leftmost text", which produced numbers that
    looked precise and were nonsense.
    """
    h, w, _ = img.shape
    out: dict[str, float | int | None] = {"width": w, "height": h}

    max_c = img.max(axis=2)
    min_c = img.min(axis=2)
    saturated = (max_c - min_c) > 30          # photographic
    dark = max_c < 170                        # text or glyph

    # ---- Grid band: rows whose saturated coverage exceeds 40 % of the width.
    coverage = saturated.sum(axis=1) / w
    grid_rows = np.where(coverage > 0.40)[0]
    if len(grid_rows) >= 3:
        grid_top, grid_bottom = int(grid_rows.min()), int(grid_rows.max())
        out["grid_top_ratio"] = round(grid_top / h, 4)
        out["grid_bottom_ratio"] = round(grid_bottom / h, 4)

        band = saturated[grid_top:grid_bottom + 1]
        cols = np.where(band.any(axis=0))[0]
        if len(cols):
            out["grid_left_ratio"] = round(int(cols.min()) / w, 4)
            out["grid_right_margin_ratio"] = round((w - 1 - int(cols.max())) / w, 4)
        # A seam is a column that is *never* photographic inside the band, i.e. a
        # real gap between cells rather than a dark patch in one photograph.
        seams = int((~band).all(axis=0).sum())
        out["seam_ratio"] = round(seams / w, 4)

    # ---- Title: the first tall dark band below the status bar and above the grid.
    limit = int(out.get("grid_top_ratio", 1.0) * h)
    top_of_page = int(h * 0.05)
    search_band = dark[top_of_page:max(limit, top_of_page + 1)]
    if search_band.size:
        rows_with_text = np.where(search_band.any(axis=1))[0]
        if len(rows_with_text):
            # Group consecutive rows into blocks, then take the block that is at
            # least 8 rows tall: that is a title, not an icon.
            blocks, start = [], rows_with_text[0]
            for previous, row in zip(rows_with_text, rows_with_text[1:]):
                if row - previous > 3:
                    blocks.append((start, previous))
                    start = row
            blocks.append((start, rows_with_text[-1]))
            tall = [b for b in blocks if b[1] - b[0] >= 8] or blocks
            first = tall[0]
            block = search_band[first[0]:first[1] + 1]
            cols = np.where(block.any(axis=0))[0]
            if len(cols):
                out["title_left_ratio"] = round(int(cols.min()) / w, 4)
            out["title_height_ratio"] = round((first[1] - first[0] + 1) / h, 4)
            out["title_top_ratio"] = round((top_of_page + first[0]) / h, 4)

    # ---- Colours.
    blue = img[(img[:, :, 2] > 170) & (img[:, :, 0] < 130) & (img[:, :, 1] > 80)]
    if len(blue):
        med = np.median(blue, axis=0).astype(int)
        out["accent_rgb"] = "#%02X%02X%02X" % (med[0], med[1], med[2])

    corners = np.concatenate([
        img[2:12, 2:12].reshape(-1, 3),
        img[2:12, -12:-2].reshape(-1, 3),
    ])
    med_bg = np.median(corners, axis=0).astype(int)
    out["page_rgb"] = "#%02X%02X%02X" % (med_bg[0], med_bg[1], med_bg[2])
    return out


def compare(ref: dict, impl: dict) -> list[tuple[str, object, object, str]]:
    rows = []
    for key, label in [
        ("grid_left_ratio", "grid left gutter / width"),
        ("grid_right_margin_ratio", "grid right margin / width"),
        ("title_left_ratio", "title left gutter / width"),
        ("seam_ratio", "grid seam width / width"),
        ("title_height_ratio", "title cap height / height"),
        ("grid_top_ratio", "grid top / height"),
        ("page_rgb", "page colour"),
    ]:
        a, b = ref.get(key), impl.get(key)
        if a is None or b is None:
            rows.append((label, a, b, "not measurable"))
            continue
        if isinstance(a, float) or isinstance(b, float):
            delta = abs(float(a) - float(b))
            verdict = "close" if delta <= 0.02 else "differs"
            rows.append((label, a, b, f"Δ {delta:.4f} — {verdict}"))
        else:
            rows.append((label, a, b, "same" if a == b else "differs"))
    return rows


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    ref_path, impl_path = Path(sys.argv[1]), Path(sys.argv[2])
    ref, impl = measure(load(ref_path)), measure(load(impl_path))

    print(f"reference : {ref_path}  {ref['width']}x{ref['height']}")
    print(f"implement.: {impl_path}  {impl['width']}x{impl['height']}")
    print()
    print(f"{'metric':34} {'reference':>16} {'ours':>16}  verdict")
    print("-" * 96)
    for label, a, b, verdict in compare(ref, impl):
        print(f"{label:34} {str(a):>16} {str(b):>16}  {verdict}")
    print()
    print("Measured structure only. No combined similarity score is reported: the two")
    print("images contain different photographs, so pixel distance would measure the")
    print("content, not the design.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
