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
    h, w, _ = img.shape
    out: dict[str, float | int | None] = {"width": w, "height": h}

    # Vertical profile: which rows contain anything at all.
    content_rows = [y for y in range(h) if row_is_content(img, y)]

    # --- Left gutter -----------------------------------------------------
    # Find the leftmost content column in the widest content band. The reference
    # aligns the title and the first grid column on the same gutter.
    #
    # Coloured (non-grey) pixels are photographs; grey/black pixels are text.
    rgb = img
    max_c = rgb.max(axis=2)
    min_c = rgb.min(axis=2)
    saturated = (max_c - min_c) > 28          # a photograph, not text
    text_like = (~saturated) & (max_c < 200)  # dark, low-saturation

    # The status bar and the toolbar sit above the page content and would win
    # any "leftmost pixel" contest, so the grid gutter is measured only inside
    # the densest photographic band, and the title gutter only inside the title's
    # own row range.
    content_top = int(h * 0.06)
    sat_below = saturated[content_top:]
    densities = sat_below.sum(axis=1)
    if densities.max() > w * 0.25:
        grid_start = content_top + int(np.argmax(densities > w * 0.25))
        grid_rows = saturated[grid_start:]
        photo_cols = np.where(grid_rows.any(axis=0))[0]
    else:
        photo_cols = np.where(saturated.any(axis=0))[0]

    # Text bands, ignoring everything above the status bar.
    text_band = text_like[content_top:]
    band_rows = np.where(text_band.any(axis=1))[0]
    title_cols = np.array([], dtype=int)
    if len(band_rows):
        # The title is the first tall text block; take its first 40 rows.
        first = band_rows[0]
        block = text_band[first:first + min(40, len(text_band) - first)]
        title_cols = np.where(block.any(axis=0))[0]

    out["first_photo_col"] = int(photo_cols.min()) if len(photo_cols) else None
    out["first_text_col"] = int(title_cols.min()) if len(title_cols) else None
    if out["first_photo_col"] is not None:
        out["photo_gutter_ratio"] = round(out["first_photo_col"] / w, 4)
    if out["first_text_col"] is not None:
        out["title_gutter_ratio"] = round(out["first_text_col"] / w, 4)

    # --- Grid seams ------------------------------------------------------
    # The reference grid is near-seamless, so what matters is how wide the seams
    # are, not how many coloured blobs appear: photographs that happen to be
    # similar in tone would otherwise be counted as one cell. A column counts as
    # a seam when it is background for the whole grid band.
    if len(photo_cols):
        band = saturated[grid_start:]
        seam_cols = int((~band).all(axis=0).sum())
        out["grid_seam_cols"] = seam_cols
        out["grid_seam_ratio"] = round(seam_cols / w, 4)

    # --- Title band ------------------------------------------------------
    # The largest vertical run of text-only rows near the top is the title.
    bands: list[tuple[int, int]] = []
    start = None
    for y in range(h):
        has_text = bool(text_like[y].any())
        has_photo = bool(saturated[y].any())
        if has_text and not has_photo:
            if start is None:
                start = y
        else:
            if start is not None:
                bands.append((start, y))
                start = None
    if start is not None:
        bands.append((start, h))
    if bands:
        # tallest band in the top third is the screen title
        top_bands = [b for b in bands if b[1] < h * 0.45] or bands
        biggest = max(top_bands, key=lambda b: b[1] - b[0])
        out["title_band_px"] = biggest[1] - biggest[0]
        out["title_height_ratio"] = round((biggest[1] - biggest[0]) / h, 4)
        out["title_top_ratio"] = round(biggest[0] / h, 4)

    # --- Bottom bar ------------------------------------------------------
    # The bar is the lowest content band that spans nearly the full width.
    if content_rows:
        out["last_content_row_ratio"] = round(content_rows[-1] / h, 4)

    # --- Accent colour ---------------------------------------------------
    # Sample the most saturated blue, which is the accent in both images.
    blue = rgb[(rgb[:, :, 2] > 180) & (rgb[:, :, 0] < 120) & (rgb[:, :, 1] > 90)]
    if len(blue):
        med = np.median(blue, axis=0).astype(int)
        out["accent_rgb"] = "#%02X%02X%02X" % (med[0], med[1], med[2])

    # --- Page background --------------------------------------------------
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
        ("photo_gutter_ratio", "photo grid gutter / width"),
        ("title_gutter_ratio", "title gutter / width"),
        ("grid_seam_ratio", "grid seam width / width"),
        ("title_height_ratio", "title band / height"),
        ("page_rgb", "page colour"),
        ("accent_rgb", "accent colour"),
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
