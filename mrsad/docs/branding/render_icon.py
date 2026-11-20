"""Renders the MRSAD adaptive-icon vectors so the geometry can be checked.

Parses the drawable XML directly and rasterises it, including the
`aapt:attr` gradients that the brand artwork uses, so what is reviewed here
is the shipped artwork rather than a re-drawing of it.
"""
import math
import re
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw

ANDROID = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"


def hexcolor(value, default=(255, 255, 255)):
    if not value or not value.startswith("#"):
        return default
    v = value.lstrip("#")
    if len(v) == 6:
        return tuple(int(v[i:i + 2], 16) for i in (0, 2, 4))
    if len(v) == 8:  # AARRGGBB
        return tuple(int(v[i:i + 2], 16) for i in (2, 4, 6))
    return default


def parse_path(data):
    """Returns a list of closed polygons for the M/L/H/V/Z subset we use."""
    tokens = re.findall(r"[MLHVZmlhvz]|-?\d+(?:\.\d+)?", data)
    polys, cur = [], []
    pos = (0.0, 0.0)
    i = 0
    while i < len(tokens):
        t = tokens[i]
        if t in "Mm":
            if cur:
                polys.append(cur)
            cur = []
            i += 1
            x, y = float(tokens[i]), float(tokens[i + 1])
            if t == "m":
                x, y = pos[0] + x, pos[1] + y
            pos = (x, y)
            cur.append(pos)
            i += 2
        elif t in "Ll":
            i += 1
            x, y = float(tokens[i]), float(tokens[i + 1])
            if t == "l":
                x, y = pos[0] + x, pos[1] + y
            pos = (x, y)
            cur.append(pos)
            i += 2
        elif t in "Hh":
            i += 1
            x = float(tokens[i])
            if t == "h":
                x = pos[0] + x
            pos = (x, pos[1])
            cur.append(pos)
            i += 1
        elif t in "Vv":
            i += 1
            y = float(tokens[i])
            if t == "v":
                y = pos[1] + y
            pos = (pos[0], y)
            cur.append(pos)
            i += 1
        elif t in "Zz":
            if cur:
                polys.append(cur)
                cur = []
            i += 1
        else:
            i += 1
    if cur:
        polys.append(cur)
    return polys


def _gradient_spec(node):
    """Reads an <aapt:attr name="android:fillColor"><gradient/></aapt:attr>."""
    for child in node:
        if child.tag == AAPT + "attr" and child.get("name") == "android:fillColor":
            # The nested <gradient> element carries no namespace prefix.
            grad = child.find("gradient")
            if grad is None:
                grad = child.find(AAPT + "gradient")
            if grad is None:
                return None
            spec = {"type": grad.get(ANDROID + "type", "linear")}
            for key in ("startX", "startY", "endX", "endY", "centerX", "centerY",
                        "gradientRadius"):
                val = grad.get(ANDROID + key)
                if val is not None:
                    spec[key] = float(val)
            spec["startColor"] = hexcolor(grad.get(ANDROID + "startColor"))
            spec["endColor"] = hexcolor(grad.get(ANDROID + "endColor"))
            centre = grad.get(ANDROID + "centerColor")
            if centre:
                spec["centerColor"] = hexcolor(centre)
            return spec
    return None


def _linear_image(spec, size):
    """Renders a linear gradient across the 108-unit viewport."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    sx, sy = spec.get("startX", 0.0), spec.get("startY", 0.0)
    ex, ey = spec.get("endX", 108.0), spec.get("endY", 108.0)
    dx, dy = ex - sx, ey - sy
    length2 = dx * dx + dy * dy or 1.0
    start, end = spec["startColor"], spec["endColor"]
    centre = spec.get("centerColor")
    for y in range(size):
        for x in range(size):
            ux, uy = x / size * 108.0, y / size * 108.0
            t = ((ux - sx) * dx + (uy - sy) * dy) / length2
            t = max(0.0, min(1.0, t))
            if centre is not None:
                if t <= 0.5:
                    k = t * 2
                    px[x, y] = tuple(int(start[i] + (centre[i] - start[i]) * k)
                                     for i in range(3))
                else:
                    k = (t - 0.5) * 2
                    px[x, y] = tuple(int(centre[i] + (end[i] - centre[i]) * k)
                                     for i in range(3))
            else:
                px[x, y] = tuple(int(start[i] + (end[i] - start[i]) * t)
                                 for i in range(3))
    return img


def _radial_image(spec, size):
    img = Image.new("RGB", (size, size))
    px = img.load()
    cx = spec.get("centerX", 54.0) / 108.0 * size
    cy = spec.get("centerY", 54.0) / 108.0 * size
    radius = spec.get("gradientRadius", 108.0) / 108.0 * size
    start, end = spec["startColor"], spec["endColor"]
    centre = spec.get("centerColor")
    for y in range(size):
        for x in range(size):
            t = min(1.0, math.hypot(x - cx, y - cy) / radius)
            if centre is not None:
                if t <= 0.5:
                    k = t * 2
                    px[x, y] = tuple(int(start[i] + (centre[i] - start[i]) * k)
                                     for i in range(3))
                else:
                    k = (t - 0.5) * 2
                    px[x, y] = tuple(int(centre[i] + (end[i] - centre[i]) * k)
                                     for i in range(3))
            else:
                px[x, y] = tuple(int(start[i] + (end[i] - start[i]) * t)
                                 for i in range(3))
    return img


def render(drawable, size, background=None):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    tree = ET.parse(drawable)
    scale = size / 108.0
    for node in tree.getroot().iter():
        if not node.tag.endswith("path"):
            continue
        data = node.get(ANDROID + "pathData")
        if not data:
            continue
        polygons = parse_path(data)
        if not polygons:
            continue
        pts = [[(x * scale, y * scale) for x, y in poly] for poly in polygons]

        spec = _gradient_spec(node)
        if spec is not None:
            if spec["type"] == "radial":
                fill_img = _radial_image(spec, size)
            else:
                fill_img = _linear_image(spec, size)
            mask = Image.new("L", (size, size), 0)
            md = ImageDraw.Draw(mask)
            for poly in pts:
                md.polygon(poly, fill=255)
            img.paste(fill_img, (0, 0), mask)
            continue

        fill = node.get(ANDROID + "fillColor")
        stroke = node.get(ANDROID + "strokeColor")
        alpha = node.get(ANDROID + "fillAlpha")
        if fill:
            color = hexcolor(fill)
            a = int(round(float(alpha) * 255)) if alpha else 255
            for poly in pts:
                ImageDraw.Draw(img).polygon(poly, fill=color + (a,))
        if stroke:
            color = hexcolor(stroke)
            w = max(1, int(round(float(node.get(ANDROID + "strokeWidth", "1")) * scale)))
            for poly in pts:
                ImageDraw.Draw(img).line(poly + [poly[0]], fill=color, width=w,
                                        joint="curve")
    return img


def compose(bg, fg, mono, size=512):
    base = render(bg, size)
    base.alpha_composite(render(fg, size))
    return base


def compose_mono(mono, size=512, tint=(255, 255, 255)):
    """Renders the monochrome layer the way the platform tints it."""
    layer = render(mono, size)
    flat = Image.new("RGBA", (size, size), tint + (0,))
    flat.putalpha(layer.getchannel("A"))
    return flat


if __name__ == "__main__":
    import sys
    res = sys.argv[1]
    out = sys.argv[2]
    img = compose(f"{res}/drawable/ic_launcher_background.xml",
                  f"{res}/drawable/ic_launcher_foreground.xml",
                  f"{res}/drawable/ic_launcher_monochrome.xml")
    img.save(out)
    print("wrote", out, img.size)

    # A small-size proof: the mark has to survive being shrunk to 48px.
    small = img.resize((48, 48), Image.LANCZOS)
    small.save(out.replace(".png", "-48.png"))
    print("wrote", out.replace(".png", "-48.png"))
