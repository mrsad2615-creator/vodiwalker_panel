"""Generates the MRSAD launcher/splash vector artwork from the brand source.

The source of truth is `concept-2-geometric.png`: a cinematic black-and-white
"M" built from six parallel metal plates. This script re-derives that
construction as clean, perfectly symmetric geometry, maps it into the
Android adaptive-icon viewport, and writes the drawable XML.

Keeping the geometry in a script (rather than hand-editing path data) is
what makes the mark reviewable: the plate definitions can be compared 1:1
with the concept, and the Android output is regenerated rather than
transcribed.

Usage:
    python docs/branding/build_icon.py
    python docs/branding/build_icon.py --preview   # also writes PNG proofs
"""
from __future__ import annotations

import argparse
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")

# --------------------------------------------------------------------------
# Brand geometry
# --------------------------------------------------------------------------
# Mark space is the concept's own pixel grid, normalised so the outer left
# edge of the mark sits at x = 0 and the top edge at y = 0. The mark is
# 703 x 653 units and perfectly symmetric about x = 351.5.
#
# The construction is one chevron band plus two stems:
#
#   upper edge   (0,0) -> (351.5,365) -> (703,0)
#   lower edge   (0,182) -> (351.5,540) -> (703,182)
#   stems        hanging off the lower edge at x 0..121 and x 582..703
#
# The chevron band is cut into four plates by vertical slices, and the two
# stems are cut off on a steeper diagonal at the bottom. The thin grooves
# between the plates are what make the concept read as machined sheet metal
# rather than a flat glyph.
#
#   A  outer-left band,  bright   \
#   B  inner-left band,  mid        >  the chevron that forms the M's middle
#   C  inner-right band, mid       /
#   D  outer-right band, bright
#   E  left stem, dark
#   F  right stem, dark

MARK_W = 703.0
CENTRE = MARK_W / 2.0            # 351.5

# Chevron band edges, sampled from the concept's own plate boundaries.
UPPER_SLOPE = 365.0 / CENTRE      # 1.0384
UPPER_VERTEX = 365.0              # upper edge at x = CENTRE
LOWER_AT_0 = 182.0                # lower edge at x = 0
LOWER_AT_CENTRE = 540.0           # lower edge at x = CENTRE
LOWER_SLOPE = (LOWER_AT_CENTRE - LOWER_AT_0) / CENTRE   # 1.0185

# Vertical slices that cut the chevron band into plates.
SLICE_AD = 198.0                  # outer-left | inner-left
SLICE_BC = 351.5                  # inner-left | inner-right
SLICE_CD = 505.0                  # inner-right | outer-right

# Grooves. The vertical slices are cut across the band so they need to be
# wider than the stem groove to read the same at launcher size.
GROOVE_SPLIT = 6.0
GROOVE_STEM = 13.0

# Stems.
STEM_W = 121.0
STEM_BOTTOM = 653.0
STEM_CUT = 480.0


def upper(x):
    """Upper edge of the chevron band at x."""
    return UPPER_SLOPE * min(x, MARK_W - x)


def lower(x):
    """Lower edge of the chevron band at x."""
    return LOWER_AT_0 + LOWER_SLOPE * min(x, MARK_W - x)


def band(x0, x1):
    """A slice of the chevron band between x0 and x1."""
    return [(x0, upper(x0)), (x1, upper(x1)), (x1, lower(x1)), (x0, lower(x0))]


def plates(groove_split, groove_stem):
    """The six plates in mark space.

    `groove_split` is the clearance at the vertical slices and
    `groove_stem` the clearance between the chevron and the stems. Zero for
    both yields a single connected silhouette.
    """
    g = groove_split / 2.0
    gs = groove_stem

    a = band(0.0, SLICE_AD - g)
    b = band(SLICE_AD + g, SLICE_BC - g)
    c = band(SLICE_BC + g, SLICE_CD - g)
    d = band(SLICE_CD + g, MARK_W)

    def stem(x_outer, x_inner):
        return [(x_outer, lower(x_outer) + gs),
                (x_inner, lower(x_inner) + gs),
                (x_inner, STEM_BOTTOM),
                (x_outer, STEM_CUT)]

    e = stem(0.0, STEM_W)
    f = stem(MARK_W, MARK_W - STEM_W)

    return {
        "A": (a, "outer"),
        "B": (b, "inner"),
        "C": (c, "inner"),
        "D": (d, "outer"),
        "E": (e, "leg"),
        "F": (f, "leg"),
    }


def silhouette():
    """The mark as one connected outline, for the monochrome layer.

    With the grooves closed the chevron and the stems meet exactly, so the
    outer boundary of the mark is a ten-point polygon.
    """
    return {
        "mark": ([(0.0, 0.0),
                  (CENTRE, UPPER_VERTEX),
                  (MARK_W, 0.0),
                  (MARK_W, STEM_CUT),
                  (MARK_W - STEM_W, STEM_BOTTOM),
                  (MARK_W - STEM_W, lower(MARK_W - STEM_W)),
                  (CENTRE, LOWER_AT_CENTRE),
                  (STEM_W, lower(STEM_W)),
                  (STEM_W, STEM_BOTTOM),
                  (0.0, STEM_CUT)], "outer"),
    }


# --------------------------------------------------------------------------
# Tones, sampled from the concept's own greyscale.
# --------------------------------------------------------------------------
# The concept is lit from the upper left: the outer bands are bright silver,
# the inner bands a step darker, and the stems darkest. The same hierarchy
# carries into the vector as flat facets plus a short gradient, which keeps
# the mark legible at 48px where a full 3D render turns to mush.
TONES = {
    "outer": ("#FFFFFF", "#CFD5DD"),
    "inner": ("#C6CCD5", "#959CA6"),
    "leg": ("#868D97", "#5F666F"),
}


def minimal_enclosing_circle(points):
    """Welzl's algorithm, small-n version."""
    import itertools

    def ok(c, r):
        return all(math.hypot(p[0] - c[0], p[1] - c[1]) <= r + 1e-9 for p in points)

    best = None
    for a, b in itertools.combinations(points, 2):
        c = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        r = math.dist(a, b) / 2
        if best is None or r < best[1]:
            if ok(c, r):
                best = (c, r)
    for a, b, c3 in itertools.combinations(points, 3):
        ax, ay = a
        bx, by = b
        cx, cy = c3
        d = 2 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by))
        if abs(d) < 1e-9:
            continue
        ux = ((ax ** 2 + ay ** 2) * (by - cy) + (bx ** 2 + by ** 2) * (cy - ay)
              + (cx ** 2 + cy ** 2) * (ay - by)) / d
        uy = ((ax ** 2 + ay ** 2) * (cx - bx) + (bx ** 2 + by ** 2) * (ax - cx)
              + (cx ** 2 + cy ** 2) * (bx - ax)) / d
        c = (ux, uy)
        r = math.dist(a, c)
        if best is None or r < best[1]:
            if ok(c, r):
                best = (c, r)
    return best


def fmt(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return s if s else "0"


def path_data(poly):
    head = f"M{fmt(poly[0][0])},{fmt(poly[0][1])}"
    rest = " ".join(f"L{fmt(x)},{fmt(y)}" for x, y in poly[1:])
    return f"{head} {rest} Z"


# --------------------------------------------------------------------------
# XML emitters
# --------------------------------------------------------------------------
VECTOR_HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!--
{doc}

Generated by docs/branding/build_icon.py from the brand source
docs/branding/concept-2-geometric.png. Do not hand-edit: change the plate
definitions in that script and re-run it so the artwork and the concept
stay in step.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="{width}"
    android:height="{height}"
    android:viewportWidth="108"
    android:viewportHeight="108">
"""


def _gradient_axis(poly):
    """Gradient axis for a plate: across the band, from the lit edge inward.

    Each plate is shaded along its own short axis, so the fold reads the
    same way on every plate whichever way the band runs.
    """
    cx = sum(p[0] for p in poly) / len(poly)
    cy = sum(p[1] for p in poly) / len(poly)
    # Short axis of a band: perpendicular to its long (45-degree) axis.
    nx, ny = -UPPER_SLOPE, 1.0
    norm = math.hypot(nx, ny)
    nx, ny = nx / norm, ny / norm
    if nx * cx + ny * cy > 0:
        nx, ny = -nx, -ny
    half = max(abs((x - cx) * nx + (y - cy) * ny) for x, y in poly)
    return (cx - nx * half, cy - ny * half), (cx + nx * half, cy + ny * half)


def plate_path(poly, tone, mono=False):
    start, end = TONES[tone]
    if mono:
        start = end = "#FFFFFF"
    (sx, sy), (ex, ey) = _gradient_axis(poly)
    return f"""    <path
        android:pathData="{path_data(poly)}">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="{fmt(sx)}"
                android:startY="{fmt(sy)}"
                android:endX="{fmt(ex)}"
                android:endY="{fmt(ey)}"
                android:startColor="{start}"
                android:endColor="{end}" />
        </aapt:attr>
    </path>"""


def write_vector(path, doc, shapes, mono=False):
    body = [VECTOR_HEADER.format(doc=doc, width="108dp", height="108dp")]
    for name, (poly, tone) in shapes.items():
        body.append(plate_path(poly, tone, mono))
    body.append("</vector>\n")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(body))
    print("wrote", os.path.relpath(path, ROOT))


def write_background(path):
    content = """<?xml version="1.0" encoding="utf-8"?>
<!--
  MRSAD launcher background.

  Adaptive icons are masked by the launcher, so the background has to fill
  the whole 108dp canvas edge to edge. The previous version painted a
  circle on top of a darker square, which left the square's corners visible
  as a black frame around the launcher shape.

  Palette: Obsidian #08090C -> Graphite #17191F, the same two values the
  in-app surfaces use, so the icon and the app share one material. A soft
  off-centre radial lift gives the mark a cinematic key light.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="44"
                android:centerY="36"
                android:gradientRadius="82"
                android:startColor="#22262E"
                android:centerColor="#121419"
                android:endColor="#08090C" />
        </aapt:attr>
    </path>
</vector>
"""
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(content)
    print("wrote", os.path.relpath(path, ROOT))


def layout(shapes):
    """Maps mark-space polygons into the 108-unit adaptive-icon viewport.

    The mark is fitted to the 66dp safe zone: whatever mask the launcher
    uses, this circle of the canvas is always visible, so nothing important
    can ever be cropped.
    """
    pts = [p for poly, _ in shapes.values() for p in poly]
    centre, radius = minimal_enclosing_circle(pts)
    scale = 33.0 / radius
    out = {}
    for name, (poly, tone) in shapes.items():
        out[name] = ([(54.0 + (x - centre[0]) * scale,
                       54.0 + (y - centre[1]) * scale) for x, y in poly], tone)
    return out, centre, radius, scale


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true",
                    help="also render PNG proofs of the generated artwork")
    args = ap.parse_args()

    colour_view, centre, radius, scale = layout(plates(GROOVE_SPLIT, GROOVE_STEM))
    mono_view, _, _, _ = layout(silhouette())
    print(f"mark centre=({centre[0]:.1f},{centre[1]:.1f}) r={radius:.1f} "
          f"scale={scale:.5f}")

    draw = os.path.join(RES, "drawable")
    write_background(os.path.join(draw, "ic_launcher_background.xml"))

    write_vector(
        os.path.join(draw, "ic_launcher_foreground.xml"),
        "  MRSAD launcher foreground - the \"Geometric M\" mark.\n\n"
        "  Six parallel metal plates: the bright outer bands form the\n"
        "  shoulders of the M, the mid bands the inner chevron, and the dark\n"
        "  plates the outer stems. The thin grooves between the plates are\n"
        "  what make it read as folded sheet metal rather than a flat glyph.",
        colour_view)

    write_vector(
        os.path.join(draw, "ic_launcher_monochrome.xml"),
        "  MRSAD launcher monochrome layer (Android 13+ themed icons).\n\n"
        "  The platform tints this layer with the user's wallpaper colours,\n"
        "  so it has to be a flat silhouette of the mark. The full-colour\n"
        "  foreground cannot be reused here: it would tint as an image with\n"
        "  baked-in greys and a visible seam.\n\n"
        "  This is the same mark with the grooves closed, so it stays legible\n"
        "  as a single shape once the platform flattens it to one colour.",
        mono_view, mono=True)

    write_vector(
        os.path.join(draw, "ic_splash_mark.xml"),
        "  MRSAD splash mark - the same \"Geometric M\" as the launcher\n"
        "  foreground, so the first frame the user sees is already the brand.",
        colour_view)

    if args.preview:
        sys.path.insert(0, HERE)
        import render_icon  # noqa: E402
        from PIL import Image  # noqa: E402

        img = render_icon.compose(
            f"{draw}/ic_launcher_background.xml",
            f"{draw}/ic_launcher_foreground.xml",
            f"{draw}/ic_launcher_monochrome.xml")
        out = os.path.join(HERE, "icon-preview.png")
        img.save(out)
        print("wrote", os.path.relpath(out, ROOT))
        img.resize((48, 48), Image.LANCZOS).save(out.replace(".png", "-48.png"))
        print("wrote", os.path.relpath(out.replace(".png", "-48.png"), ROOT))

        tinted = render_icon.compose_mono(
            f"{draw}/ic_launcher_monochrome.xml", 256, (255, 255, 255))
        plate = Image.new("RGBA", (256, 256), (24, 26, 32, 255))
        plate.alpha_composite(tinted)
        plate.save(os.path.join(HERE, "icon-monochrome-proof.png"))
        print("wrote", os.path.relpath(
            os.path.join(HERE, "icon-monochrome-proof.png"), ROOT))


if __name__ == "__main__":
    import sys
    main()
