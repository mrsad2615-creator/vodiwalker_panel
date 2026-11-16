#!/usr/bin/env python3
"""
MESAD — THE ART OF TIME
Precision Brand & Android Adaptive Icon Asset Pipeline.
Generates:
1. Main App Icon (1024px, 512px) from flagship selected concept + supersampled finish
2. Minimal Edition (512px PNG + crisp SVG)
3. Monochrome Edition (512px PNG + crisp SVG + Android 13-16 Monochrome stencil)
4. Dark Background & Light Background Editions (512px)
5. Android Adaptive Icon layers (108dp -> 432px xxxhdpi Foreground, Background, Monochrome + VectorDrawables + mipmap-mdpi..xxxhdpi)
6. Multi-size & OEM Mask Preview Sheet (mesad_icon_preview_sheet.png)
"""

import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance, ImageOps, ImageChops

ROOT = Path("/home/user/vodiwalker_panel")
CONCEPT_PATH = ROOT / "mesad-assets/concepts/mesad_icon_master.png"
OUT_ICONS = ROOT / "mesad-assets/icons"
ANDROID_RES = ROOT / "mesad-android/app/src/main/res"
STUDIO_PUBLIC_ICONS = ROOT / "mesad-studio/public/icons"


def ensure_dirs() -> None:
    OUT_ICONS.mkdir(parents=True, exist_ok=True)
    STUDIO_PUBLIC_ICONS.mkdir(parents=True, exist_ok=True)
    for d in [
        "drawable",
        "mipmap-anydpi-v26",
        "mipmap-mdpi",
        "mipmap-hdpi",
        "mipmap-xhdpi",
        "mipmap-xxhdpi",
        "mipmap-xxxhdpi",
    ]:
        (ANDROID_RES / d).mkdir(parents=True, exist_ok=True)


def make_rounded_mask(size: int, radius: int) -> Image.Image:
    scale = 4
    s = size * scale
    r = radius * scale
    mask = Image.new("L", (s, s), 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle((0, 0, s - 1, s - 1), radius=r, fill=255)
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def make_squircle_mask(size: int, exponent: float = 4.2) -> Image.Image:
    scale = 2
    s = size * scale
    mask = Image.new("L", (s, s), 0)
    px = mask.load()
    half = (s - 1) / 2.0
    for y in range(s):
        ny = abs((y - half) / half)
        for x in range(s):
            nx = abs((x - half) / half)
            val = (nx ** exponent + ny ** exponent) ** (1.0 / exponent)
            if val <= 1.0:
                edge = min(1.0, max(0.0, (1.0 - val) * half * 0.8))
                px[x, y] = int(255 * edge) if val > 0.97 else 255
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def make_circle_mask(size: int) -> Image.Image:
    scale = 4
    s = size * scale
    mask = Image.new("L", (s, s), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, s - 1, s - 1), fill=255)
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def make_teardrop_mask(size: int) -> Image.Image:
    scale = 4
    s = size * scale
    mask = Image.new("L", (s, s), 0)
    draw = ImageDraw.Draw(mask)
    r_big = s // 2
    r_small = int(s * 0.14)
    draw.ellipse((0, 0, s - 1, s - 1), fill=255)
    draw.rounded_rectangle((s // 2, s // 2, s - 1, s - 1), radius=r_small, fill=255)
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def render_obsidian_background(size: int, light_mode: bool = False) -> Image.Image:
    """Renders luxury radial brushed obsidian or titanium-alabaster icon background."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    px = img.load()
    cx, cy = size * 0.5, size * 0.44
    max_r = size * 0.75
    for y in range(size):
        for x in range(size):
            dx = x - cx
            dy = y - cy
            d = math.hypot(dx, dy) / max_r
            d = min(1.0, d)
            if not light_mode:
                # Deep Midnight Blue core -> Obsidian Black edge
                r = int(16 * (1 - d) + 7 * d)
                g = int(25 * (1 - d) + 8 * d)
                b = int(38 * (1 - d) + 12 * d)
            else:
                # Soft Alabaster Silver core -> Cool Titanium edge
                r = int(248 * (1 - d) + 222 * d)
                g = int(250 * (1 - d) + 227 * d)
                b = int(253 * (1 - d) + 236 * d)
            px[x, y] = (r, g, b, 255)
    return img


def extract_emblem_rgba(concept_img: Image.Image, target_size: int, scale_ratio: float = 0.76) -> Image.Image:
    """
    Extracts the central sculpted emblem from the dark obsidian studio background
    with clean luminance/radial alpha feathering so it works as a true transparent foreground!
    """
    w, h = concept_img.size
    # In mesad_icon_master.png, the emblem spans roughly x:[0.23..0.77], y:[0.22..0.77]
    # Let's crop tightly around the emblem center (0.50, 0.495) with radius 0.305 * w
    cx, cy = w * 0.50, h * 0.495
    half_box = w * 0.305
    cropped = concept_img.crop((
        int(cx - half_box),
        int(cy - half_box),
        int(cx + half_box),
        int(cy + half_box),
    )).convert("RGBA")

    emblem_px = int(target_size * scale_ratio)
    resized = cropped.resize((emblem_px, emblem_px), Image.Resampling.LANCZOS)
    px = resized.load()
    ec = emblem_px / 2.0
    outer_r = emblem_px * 0.488

    for y in range(emblem_px):
        for x in range(emblem_px):
            r, g, b, _ = px[x, y]
            dx = x - ec
            dy = y - ec
            dist = math.hypot(dx, dy)
            lum = max(r, g, b)
            # Inside the titanium outer ring (dist <= outer_r * 0.92), keep full solid opacity
            if dist <= outer_r * 0.89:
                alpha = 255
            elif dist <= outer_r * 1.04:
                # Top wedge protrudes slightly at 12 o'clock (dy < 0 and abs(dx) < emblem_px*0.09)
                is_zenith_wedge = (dy < -outer_r * 0.72 and abs(dx) < emblem_px * 0.10)
                if is_zenith_wedge and lum > 28:
                    alpha = min(255, int((lum - 18) * 6.5))
                else:
                    # Smooth anti-aliased edge + luminance key against #0B0C10 studio backdrop
                    radial_fade = max(0.0, min(1.0, (outer_r * 1.01 - dist) / (outer_r * 0.11)))
                    lum_fade = max(0.0, min(1.0, (lum - 20) / 36.0))
                    alpha = int(255 * max(radial_fade * lum_fade, lum_fade if dist < outer_r * 0.97 else 0.0))
            else:
                # Allow top tip of zenith wedge if bright
                if dy < -outer_r * 0.85 and abs(dx) < emblem_px * 0.085 and lum > 35:
                    alpha = min(255, int((lum - 22) * 5.5))
                else:
                    alpha = 0
            px[x, y] = (r, g, b, alpha)

    canvas = Image.new("RGBA", (target_size, target_size), (0, 0, 0, 0))
    offset = (target_size - emblem_px) // 2
    canvas.paste(resized, (offset, offset), resized)
    return canvas


def render_minimal_icon(size: int = 512, monochrome: bool = False, fg_only: bool = False) -> Image.Image:
    """
    Supersampled geometric horological vector render matching the exact DNA of MESAD:
    - Outer brushed titanium orbital ring + asymmetric lower-right crescent wrap
    - Inner sapphire/midnight core
    - 12-o'clock Champagne/Cyan zenith wedge with meridian channel
    """
    ss = 4
    S = size * ss
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    if not monochrome and not fg_only:
        bg = render_obsidian_background(S, light_mode=False)
        img.paste(bg, (0, 0))

    draw = ImageDraw.Draw(img)
    cx, cy = S // 2, S // 2

    # Safe zone geometry: when fg_only (108dp adaptive), safe zone is 66/108 = 0.611 of S
    emblem_r = int(S * (0.275 if fg_only else 0.355))
    ring_w = max(4, int(emblem_r * 0.21))
    inner_r = int(emblem_r * 0.64)

    if monochrome:
        white = (255, 255, 255, 255)
        clear = (0, 0, 0, 0)
        # Outer orbital ring
        draw.ellipse(
            (cx - emblem_r, cy - emblem_r, cx + emblem_r, cy + emblem_r),
            outline=white,
            width=ring_w,
        )
        # Asymmetric sculpted crescent thickening on lower-right (20 deg to 115 deg)
        cres_r = int(emblem_r * 1.03)
        draw.pieslice(
            (cx - cres_r, cy - cres_r, cx + cres_r, cy + cres_r),
            start=18,
            end=112,
            fill=white,
        )
        # Carve inner hole back out cleanly
        carve_r = emblem_r - ring_w
        draw.ellipse(
            (cx - carve_r, cy - carve_r, cx + carve_r, cy + carve_r),
            fill=clear,
        )
        # Core geometric faceted circle with negative-space meridian facet
        draw.ellipse(
            (cx - inner_r, cy - inner_r, cx + inner_r, cy + inner_r),
            outline=white,
            width=max(4, int(ring_w * 0.55)),
        )
        core_dot = int(inner_r * 0.52)
        draw.regular_polygon((cx, cy, core_dot), n_sides=8, rotation=22.5, fill=white)

        # Carve gap around 12 o'clock zenith wedge so stencil reads sharply
        wedge_top_y = cy - int(emblem_r * 1.08)
        wedge_bot_y = cy - int(inner_r * 0.58)
        wedge_half_w = int(emblem_r * 0.21)
        gap_pad = int(ring_w * 0.34)
        draw.polygon(
            [
                (cx - wedge_half_w - gap_pad, wedge_top_y - gap_pad),
                (cx + wedge_half_w + gap_pad, wedge_top_y - gap_pad),
                (cx, wedge_bot_y + gap_pad * 2),
            ],
            fill=clear,
        )
        # Draw crisp 12 o'clock zenith wedge
        draw.polygon(
            [
                (cx - wedge_half_w, wedge_top_y),
                (cx + wedge_half_w, wedge_top_y),
                (cx, wedge_bot_y),
            ],
            fill=white,
        )
        # Carve Y-meridian slit inside the wedge
        slit_w = max(2, int(ring_w * 0.18))
        draw.line([(cx, wedge_top_y + int(ring_w * 0.45)), (cx, wedge_bot_y - int(ring_w * 0.55))], fill=clear, width=slit_w)
    else:
        # Color Minimal Edition
        titanium = (200, 208, 220, 255)
        titanium_hi = (232, 237, 245, 255)
        graphite = (30, 36, 50, 255)
        sapphire_deep = (10, 30, 52, 255)
        sapphire_mid = (14, 68, 102, 255)
        cyan = (0, 229, 255, 255)
        champagne = (230, 199, 156, 255)

        # Ambient cyan halo behind ring
        halo = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        hdraw = ImageDraw.Draw(halo)
        hdraw.ellipse(
            (cx - inner_r - ring_w // 2, cy - inner_r - ring_w // 2, cx + inner_r + ring_w // 2, cy + inner_r + ring_w // 2),
            outline=(0, 229, 255, 110),
            width=max(4, ring_w // 2),
        )
        halo = halo.filter(ImageFilter.GaussianBlur(radius=max(4, S // 64)))
        img = Image.alpha_composite(img, halo)
        draw = ImageDraw.Draw(img)

        # Champagne outer micro-rim
        draw.ellipse(
            (cx - emblem_r - 4, cy - emblem_r - 4, cx + emblem_r + 4, cy + emblem_r + 4),
            outline=champagne,
            width=max(3, ring_w // 6),
        )
        # Brushed Titanium Orbital Ring
        draw.ellipse(
            (cx - emblem_r, cy - emblem_r, cx + emblem_r, cy + emblem_r),
            outline=titanium,
            width=ring_w,
        )
        # Lower-right sculpted titanium crescent accent
        draw.arc(
            (cx - emblem_r - ring_w // 5, cy - emblem_r - ring_w // 5, cx + emblem_r + ring_w // 5, cy + emblem_r + ring_w // 5),
            start=22,
            end=112,
            fill=titanium_hi,
            width=int(ring_w * 1.25),
        )

        # Champagne inner retaining bezel + cyan inner glow ring
        draw.ellipse(
            (cx - inner_r - int(ring_w * 0.22), cy - inner_r - int(ring_w * 0.22), cx + inner_r + int(ring_w * 0.22), cy + inner_r + int(ring_w * 0.22)),
            outline=cyan,
            width=max(3, ring_w // 6),
        )
        draw.ellipse(
            (cx - inner_r, cy - inner_r, cx + inner_r, cy + inner_r),
            fill=sapphire_deep,
            outline=champagne,
            width=max(3, ring_w // 5),
        )
        # Faceted octagonal sapphire core geometry
        draw.regular_polygon(
            (cx, cy, int(inner_r * 0.78)),
            n_sides=8,
            rotation=22.5,
            fill=sapphire_mid,
            outline=(0, 229, 255, 140),
        )
        draw.regular_polygon(
            (cx, cy, int(inner_r * 0.46)),
            n_sides=8,
            rotation=0,
            fill=graphite,
            outline=(0, 229, 255, 210),
        )

        # 12 o'clock Zenith Wedge (Warm Champagne + Electric Cyan Y-meridian)
        wedge_top_y = cy - int(emblem_r * 1.07)
        wedge_bot_y = cy - int(inner_r * 0.54)
        wedge_half_w = int(emblem_r * 0.19)
        draw.polygon(
            [
                (cx - wedge_half_w, wedge_top_y),
                (cx + wedge_half_w, wedge_top_y),
                (cx, wedge_bot_y),
            ],
            fill=champagne,
            outline=titanium_hi,
        )
        # Electric Cyan Y-meridian inlay
        y_mid = wedge_top_y + int((wedge_bot_y - wedge_top_y) * 0.34)
        lw = max(3, ring_w // 6)
        draw.line([(cx - int(wedge_half_w * 0.65), wedge_top_y + lw), (cx, y_mid)], fill=cyan, width=lw)
        draw.line([(cx + int(wedge_half_w * 0.65), wedge_top_y + lw), (cx, y_mid)], fill=cyan, width=lw)
        draw.line([(cx, y_mid), (cx, wedge_bot_y - lw * 2)], fill=cyan, width=lw)

    return img.resize((size, size), Image.Resampling.LANCZOS)


def write_svg_and_android_vectors() -> None:
    """Writes clean, exact SVG files and Android 108dp VectorDrawables (66dp safe zone: 21..87)."""
    # 1. Android ic_launcher_background.xml (108x108dp radial Obsidian -> Midnight)
    bg_xml = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:centerX="54"
                android:centerY="48"
                android:gradientRadius="68"
                android:type="radial">
                <item android:color="#FF121C2B" android:offset="0.0" />
                <item android:color="#FF0A0D14" android:offset="0.58" />
                <item android:color="#FF06070B" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
    <!-- Subtle horological meridian grid ring outside safe zone for parallax depth -->
    <path
        android:fillColor="#00000000"
        android:pathData="M54,14 A40,40 0 1,1 53.99,14 Z"
        android:strokeColor="#1400E5FF"
        android:strokeWidth="0.4" />
</vector>
"""
    (ANDROID_RES / "drawable/ic_launcher_background.xml").write_text(bg_xml, encoding="utf-8")

    # 2. Android ic_launcher_foreground.xml (108x108dp, emblem strictly within [24..84] inside 66dp safe zone [21..87]!)
    fg_xml = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Outer Champagne Micro-Chamfer Ring (r=28.5, within 66dp safe zone r=33) -->
    <path
        android:fillColor="#00000000"
        android:pathData="M54,25.5 A28.5,28.5 0 1,1 53.99,25.5 Z"
        android:strokeColor="#99E6C79C"
        android:strokeWidth="0.9" />

    <!-- Main Brushed Titanium Orbital Ring (outer r=28, inner r=22.2 -> stroke r=25.1, width=5.8) -->
    <path
        android:fillColor="#00000000"
        android:pathData="M54,28.9 A25.1,25.1 0 1,1 53.99,28.9 Z"
        android:strokeWidth="5.8">
        <aapt:attr name="android:strokeColor">
            <gradient
                android:endX="80"
                android:endY="80"
                android:startX="28"
                android:startY="28"
                android:type="linear">
                <item android:color="#FFE8EDF5" android:offset="0.0" />
                <item android:color="#FF9AA4B5" android:offset="0.5" />
                <item android:color="#FFD0D7E2" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>

    <!-- Asymmetric Sculpted Lower-Right Titanium Crescent Wrap -->
    <path
        android:fillColor="#00000000"
        android:pathData="M78.2,63.2 A26.0,26.0 0 0,1 44.5,78.2"
        android:strokeColor="#FFE2E8F2"
        android:strokeLineCap="round"
        android:strokeWidth="6.8" />

    <!-- Electric Cyan Inner Orbital Halo -->
    <path
        android:fillColor="#00000000"
        android:pathData="M54,34.2 A19.8,19.8 0 1,1 53.99,34.2 Z"
        android:strokeColor="#CC00E5FF"
        android:strokeWidth="0.9" />

    <!-- Deep Midnight Sapphire Core -->
    <path android:pathData="M54,35.6 A18.4,18.4 0 1,1 53.99,35.6 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:centerX="54"
                android:centerY="60"
                android:gradientRadius="19"
                android:type="radial">
                <item android:color="#FF00B8D4" android:offset="0.0" />
                <item android:color="#FF0B2D48" android:offset="0.48" />
                <item android:color="#FF071322" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>

    <!-- Sapphire Crystal Octagonal Facet Lines -->
    <path
        android:fillColor="#1F00E5FF"
        android:pathData="M54,39.5 L64.2,43.8 L68.5,54 L64.2,64.2 L54,68.5 L43.8,64.2 L39.5,54 L43.8,43.8 Z"
        android:strokeColor="#6600E5FF"
        android:strokeWidth="0.65" />
    <path
        android:fillColor="#3307090F"
        android:pathData="M54,44.5 L60.7,47.3 L63.5,54 L60.7,60.7 L54,63.5 L47.3,60.7 L44.5,54 L47.3,47.3 Z"
        android:strokeColor="#9900E5FF"
        android:strokeWidth="0.75" />

    <!-- Warm Champagne Core Retaining Bezel -->
    <path
        android:fillColor="#00000000"
        android:pathData="M54,35.6 A18.4,18.4 0 1,1 53.99,35.6 Z"
        android:strokeColor="#FFE6C79C"
        android:strokeWidth="1.0" />

    <!-- 12 O'Clock Zenith Time Index Wedge (Warm Champagne Gold + Cyan Y-Meridian) -->
    <path
        android:pathData="M48.4,23.5 L59.6,23.5 L54.0,42.5 Z"
        android:strokeColor="#FFF5E1C0"
        android:strokeLineJoin="round"
        android:strokeWidth="0.6">
        <aapt:attr name="android:fillColor">
            <gradient
                android:endX="54"
                android:endY="42.5"
                android:startX="54"
                android:startY="23.5"
                android:type="linear">
                <item android:color="#FFF3DEC0" android:offset="0.0" />
                <item android:color="#FFC9A574" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
    <!-- Electric Cyan Y-Meridian Inlay inside Zenith Index -->
    <path
        android:fillColor="#00000000"
        android:pathData="M50.2,24.8 L54.0,29.4 L57.8,24.8 M54.0,29.4 L54.0,40.4"
        android:strokeColor="#FF00E5FF"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:strokeWidth="1.1" />
</vector>
"""
    (ANDROID_RES / "drawable/ic_launcher_foreground.xml").write_text(fg_xml, encoding="utf-8")

    # 3. Android ic_launcher_monochrome.xml (Android 13-16 Themed Icon Stencil)
    mono_xml = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Orbital Ring Interrupted Cleanly at 12 O'Clock for Zenith Index -->
    <path
        android:fillColor="#00000000"
        android:pathData="M61.8,30.1 A25.1,25.1 0 1,1 46.2,30.1"
        android:strokeColor="#FFFFFFFF"
        android:strokeLineCap="butt"
        android:strokeWidth="5.8" />
    <!-- Sculpted Lower-Right Crescent -->
    <path
        android:fillColor="#00000000"
        android:pathData="M78.2,63.2 A26.0,26.0 0 0,1 44.5,78.2"
        android:strokeColor="#FFFFFFFF"
        android:strokeLineCap="round"
        android:strokeWidth="7.0" />
    <!-- Inner Horological Core Ring -->
    <path
        android:fillColor="#00000000"
        android:pathData="M58.5,36.2 A18.3,18.3 0 1,1 49.5,36.2"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="2.4" />
    <!-- Geometric Octagonal Center Core -->
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M54,45.2 L60.2,47.8 L62.8,54 L60.2,60.2 L54,62.8 L47.8,60.2 L45.2,54 L47.8,47.8 Z" />
    <!-- 12 O'Clock Zenith Chevron Index -->
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M48.6,23.5 L53.1,23.5 L53.1,40.8 Z M54.9,23.5 L59.4,23.5 L54.9,40.8 Z" />
</vector>
"""
    (ANDROID_RES / "drawable/ic_launcher_monochrome.xml").write_text(mono_xml, encoding="utf-8")

    # 4. Adaptive Icon XMLs (mipmap-anydpi-v26)
    adaptive_xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""
    (ANDROID_RES / "mipmap-anydpi-v26/ic_launcher.xml").write_text(adaptive_xml, encoding="utf-8")
    (ANDROID_RES / "mipmap-anydpi-v26/ic_launcher_round.xml").write_text(adaptive_xml, encoding="utf-8")

    # 5. Standalone Web/Brand SVGs
    main_svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">
  <defs>
    <radialGradient id="bgGrad" cx="50%" cy="44%" r="68%">
      <stop offset="0%" stop-color="#121C2B"/>
      <stop offset="58%" stop-color="#0A0D14"/>
      <stop offset="100%" stop-color="#06070B"/>
    </radialGradient>
    <linearGradient id="tiGrad" x1="25%" y1="20%" x2="80%" y2="80%">
      <stop offset="0%" stop-color="#E8EDF5"/>
      <stop offset="50%" stop-color="#8C96A8"/>
      <stop offset="100%" stop-color="#D4DCE8"/>
    </linearGradient>
    <radialGradient id="sapphireGrad" cx="50%" cy="58%" r="55%">
      <stop offset="0%" stop-color="#00D8F6"/>
      <stop offset="45%" stop-color="#0B2D48"/>
      <stop offset="100%" stop-color="#06101D"/>
    </radialGradient>
    <linearGradient id="goldGrad" x1="50%" y1="0%" x2="50%" y2="100%">
      <stop offset="0%" stop-color="#F5E2C4"/>
      <stop offset="100%" stop-color="#C6A06C"/>
    </linearGradient>
  </defs>
  <rect width="108" height="108" rx="24" fill="url(#bgGrad)"/>
  <circle cx="54" cy="54" r="32.5" fill="none" stroke="#E6C79C" stroke-opacity="0.65" stroke-width="0.9"/>
  <circle cx="54" cy="54" r="28.6" fill="none" stroke="url(#tiGrad)" stroke-width="6.6"/>
  <path d="M81.5 64.5 A29.6 29.6 0 0 1 43.2 81.6" fill="none" stroke="#E6ECF5" stroke-width="7.6" stroke-linecap="round"/>
  <circle cx="54" cy="54" r="22.4" fill="none" stroke="#00E5FF" stroke-opacity="0.85" stroke-width="1.0"/>
  <circle cx="54" cy="54" r="20.8" fill="url(#sapphireGrad)" stroke="#E6C79C" stroke-width="1.1"/>
  <polygon points="54,37.5 65.7,42.3 70.5,54 65.7,65.7 54,70.5 42.3,65.7 37.5,54 42.3,42.3" fill="#00E5FF" fill-opacity="0.12" stroke="#00E5FF" stroke-opacity="0.45" stroke-width="0.7"/>
  <polygon points="54,43.2 61.6,46.4 64.8,54 61.6,61.6 54,64.8 46.4,61.6 43.2,54 46.4,46.4" fill="#07090F" fill-opacity="0.35" stroke="#00E5FF" stroke-opacity="0.7" stroke-width="0.8"/>
  <polygon points="47.6,19.4 60.4,19.4 54.0,41.2" fill="url(#goldGrad)" stroke="#F7E6CA" stroke-width="0.6" stroke-linejoin="round"/>
  <path d="M49.6 20.9 L54.0 26.2 L58.4 20.9 M54.0 26.2 L54.0 38.8" fill="none" stroke="#00E5FF" stroke-width="1.25" stroke-linecap="round" stroke-linejoin="round"/>
</svg>
"""
    (OUT_ICONS / "ic_mesad_minimal.svg").write_text(main_svg, encoding="utf-8")
    (STUDIO_PUBLIC_ICONS / "ic_mesad_minimal.svg").write_text(main_svg, encoding="utf-8")

    mono_svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">
  <path d="M62.8 26.8 A28.6 28.6 0 1 1 45.2 26.8" fill="none" stroke="#FFFFFF" stroke-width="6.6"/>
  <path d="M81.5 64.5 A29.6 29.6 0 0 1 43.2 81.6" fill="none" stroke="#FFFFFF" stroke-width="7.8" stroke-linecap="round"/>
  <path d="M59.2 33.8 A20.8 20.8 0 1 1 48.8 33.8" fill="none" stroke="#FFFFFF" stroke-width="2.6"/>
  <polygon points="54,44.0 61.1,46.9 64.0,54 61.1,61.1 54,64.0 46.9,61.1 44.0,54 46.9,46.9" fill="#FFFFFF"/>
  <path d="M47.8,19.4 L53.0,19.4 L53.0,39.2 Z M55.0,19.4 L60.2,19.4 L55.0,39.2 Z" fill="#FFFFFF"/>
</svg>
"""
    (OUT_ICONS / "ic_mesad_monochrome.svg").write_text(mono_svg, encoding="utf-8")
    (STUDIO_PUBLIC_ICONS / "ic_mesad_monochrome.svg").write_text(mono_svg, encoding="utf-8")


def build_all() -> None:
    ensure_dirs()
    concept = Image.open(CONCEPT_PATH).convert("RGBA")

    # 1. Main Hero App Icon (1024x1024 & 512x512)
    # Crop slightly closer so the emblem commands 78% of the icon canvas with rich obsidian depth
    w, h = concept.size
    pad = int(w * 0.105)
    hero_crop = concept.crop((pad, pad - int(h * 0.01), w - pad, h - pad - int(h * 0.01)))
    hero_1024 = hero_crop.resize((1024, 1024), Image.Resampling.LANCZOS)
    hero_1024 = ImageEnhance.Contrast(hero_1024).enhance(1.06)
    hero_1024 = ImageEnhance.Sharpness(hero_1024).enhance(1.12)
    hero_1024.save(OUT_ICONS / "mesad_icon_main_1024.png")

    hero_512 = hero_1024.resize((512, 512), Image.Resampling.LANCZOS)
    hero_512.save(OUT_ICONS / "mesad_icon_main_512.png")
    hero_512.save(STUDIO_PUBLIC_ICONS / "mesad_icon_main_512.png")

    # 2. Adaptive Foreground (432x432 = 108dp xxxhdpi, emblem strictly inside 66dp=264px safe zone -> ratio 0.60)
    fg_432 = extract_emblem_rgba(concept, target_size=432, scale_ratio=0.61)
    fg_432.save(OUT_ICONS / "adaptive_foreground_432.png")
    fg_432.save(STUDIO_PUBLIC_ICONS / "adaptive_foreground_432.png")

    bg_432 = render_obsidian_background(432, light_mode=False)
    bg_432.save(OUT_ICONS / "adaptive_background_432.png")
    bg_432.save(STUDIO_PUBLIC_ICONS / "adaptive_background_432.png")

    mono_432 = render_minimal_icon(size=432, monochrome=True, fg_only=True)
    mono_432.save(OUT_ICONS / "adaptive_monochrome_432.png")
    mono_432.save(STUDIO_PUBLIC_ICONS / "adaptive_monochrome_432.png")

    # 3. Minimal Edition (512x512)
    minimal_512 = render_minimal_icon(size=512, monochrome=False, fg_only=False)
    minimal_512.save(OUT_ICONS / "mesad_icon_minimal_512.png")
    minimal_512.save(STUDIO_PUBLIC_ICONS / "mesad_icon_minimal_512.png")

    # 4. Monochrome Edition (512x512)
    mono_512 = render_minimal_icon(size=512, monochrome=True, fg_only=False)
    mono_512.save(OUT_ICONS / "mesad_icon_monochrome_512.png")
    mono_512.save(STUDIO_PUBLIC_ICONS / "mesad_icon_monochrome_512.png")

    # 5. Dark Background & Light Background Editions (512x512)
    emblem_512 = extract_emblem_rgba(concept, target_size=512, scale_ratio=0.78)
    dark_bg_512 = render_obsidian_background(512, light_mode=False)
    # Subtle cyan contact glow at bottom
    glow_dark = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    ImageDraw.Draw(glow_dark).ellipse((180, 395, 332, 455), fill=(0, 229, 255, 70))
    glow_dark = glow_dark.filter(ImageFilter.GaussianBlur(22))
    dark_bg_512 = Image.alpha_composite(dark_bg_512, glow_dark)
    dark_bg_512 = Image.alpha_composite(dark_bg_512, emblem_512)
    dark_bg_512.save(OUT_ICONS / "mesad_icon_on_dark_512.png")
    dark_bg_512.save(STUDIO_PUBLIC_ICONS / "mesad_icon_on_dark_512.png")

    light_bg_512 = render_obsidian_background(512, light_mode=True)
    # Soft natural drop shadow for light background legibility
    shadow = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    emblem_alpha = emblem_512.split()[3]
    shadow_layer = Image.new("RGBA", (512, 512), (8, 14, 26, 130))
    shadow.paste(shadow_layer, (0, 14), emblem_alpha)
    shadow = shadow.filter(ImageFilter.GaussianBlur(16))
    light_bg_512 = Image.alpha_composite(light_bg_512, shadow)
    light_bg_512 = Image.alpha_composite(light_bg_512, emblem_512)
    light_bg_512.save(OUT_ICONS / "mesad_icon_on_light_512.png")
    light_bg_512.save(STUDIO_PUBLIC_ICONS / "mesad_icon_on_light_512.png")

    # 6. Android Legacy/Fallback Mipmap Densities (mdpi=48, hdpi=72, xhdpi=96, xxhdpi=144, xxxhdpi=192)
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, px_size in densities.items():
        base = hero_1024.resize((px_size, px_size), Image.Resampling.LANCZOS)
        # Square/Rounded legacy icon
        sq = Image.new("RGBA", (px_size, px_size), (0, 0, 0, 0))
        sq.paste(base, (0, 0), make_rounded_mask(px_size, max(8, int(px_size * 0.22))))
        sq.save(ANDROID_RES / folder / "ic_launcher.png")
        # Round legacy icon
        rd = Image.new("RGBA", (px_size, px_size), (0, 0, 0, 0))
        rd.paste(base, (0, 0), make_circle_mask(px_size))
        rd.save(ANDROID_RES / folder / "ic_launcher_round.png")

    # 7. Write SVG & Android VectorDrawables
    write_svg_and_android_vectors()

    # 8. Build Comprehensive Multi-Size & OEM Mask Preview Sheet (1600 x 1060)
    sheet = Image.new("RGBA", (1600, 1060), (9, 11, 16, 255))
    sdraw = ImageDraw.Draw(sheet)
    # Subtle header accent line
    sdraw.line([(60, 88), (1540, 88)], fill=(38, 46, 62, 255), width=2)
    sdraw.line([(60, 88), (340, 88)], fill=(0, 229, 255, 255), width=2)
    sdraw.text((60, 42), "MESAD — BRAND ICON SYSTEM & ANDROID ADAPTIVE SPECIMEN", fill=(230, 236, 245, 255))
    sdraw.text((1260, 46), "108dp CONTAINER / 66dp SAFE ZONE", fill=(230, 199, 156, 255))

    def paste_card(icon_img: Image.Image, x: int, y: int, card_s: int, mask_fn, label: str):
        resized_ic = icon_img.resize((card_s, card_s), Image.Resampling.LANCZOS)
        masked = Image.new("RGBA", (card_s, card_s), (0, 0, 0, 0))
        m = mask_fn(card_s)
        masked.paste(resized_ic, (0, 0), m)
        sheet.paste(masked, (x, y), masked)
        sdraw.text((x + 6, y + card_s + 16), label, fill=(180, 190, 206, 255))

    # Row 1: Core Editions (220px each)
    mono_card = Image.new("RGBA", (512, 512), (18, 24, 34, 255))
    mono_card = Image.alpha_composite(mono_card, mono_512)

    row1_y = 130
    paste_card(hero_512, 60, row1_y, 220, lambda s: make_rounded_mask(s, 48), "01. MAIN HERO ICON")
    paste_card(minimal_512, 340, row1_y, 220, lambda s: make_rounded_mask(s, 48), "02. MINIMAL EDITION")
    paste_card(mono_card, 620, row1_y, 220, lambda s: make_rounded_mask(s, 48), "03. MONOCHROME STENCIL")
    paste_card(dark_bg_512, 900, row1_y, 220, lambda s: make_rounded_mask(s, 48), "04. DARK SURFACE")
    # Light surface backdrop plate so light icon stands out cleanly
    sdraw.rounded_rectangle((1166, row1_y - 14, 1414, row1_y + 234), radius=56, fill=(236, 240, 246, 255))
    paste_card(light_bg_512, 1180, row1_y, 220, lambda s: make_rounded_mask(s, 48), "05. LIGHT SURFACE")

    # Row 2: Android Adaptive Masks & Material You Dynamic Monochrome Tints
    sdraw.line([(60, 430), (1540, 430)], fill=(28, 34, 48, 255), width=1)
    sdraw.text((60, 452), "ANDROID OEM ADAPTIVE MASKS & ANDROID 13–16 THEMED MONOCHROME PREVIEWS", fill=(0, 229, 255, 230))

    # Compose Adaptive (bg_432 + fg_432) cropped to middle 72dp (288px of 432px) as Android Launcher does!
    adaptive_comp = Image.alpha_composite(bg_432, fg_432)
    inset = int(432 * (18.0 / 108.0))  # 18dp outer parallax margin
    viewport_288 = adaptive_comp.crop((inset, inset, 432 - inset, 432 - inset))

    row2_y = 495
    paste_card(viewport_288, 60, row2_y, 156, make_circle_mask, "PIXEL CIRCLE")
    paste_card(viewport_288, 256, row2_y, 156, make_squircle_mask, "ONE UI SQUIRCLE")
    paste_card(viewport_288, 452, row2_y, 156, lambda s: make_rounded_mask(s, 36), "ROUNDED RECT")
    paste_card(viewport_288, 648, row2_y, 156, make_teardrop_mask, "TEARDROP MASK")

    # 4 Material You Themed Monochrome previews
    mono_vp = mono_432.crop((inset, inset, 432 - inset, 432 - inset))
    tints = [
        ((14, 42, 54), (112, 236, 255), "THEMED CYAN"),
        ((46, 36, 22), (242, 208, 158), "THEMED CHAMPAGNE"),
        ((24, 32, 52), (168, 198, 255), "THEMED MIDNIGHT"),
        ((232, 238, 246), (22, 34, 52), "THEMED DAYLIGHT"),
    ]
    tx = 880
    for bg_col, fg_col, tlabel in tints:
        plate = Image.new("RGBA", (288, 288), (*bg_col, 255))
        tinted_fg = Image.new("RGBA", (288, 288), (*fg_col, 255))
        plate.paste(tinted_fg, (0, 0), mono_vp.split()[3])
        paste_card(plate, tx, row2_y, 140, make_circle_mask, tlabel)
        tx += 172

    # Row 3: Real-World Density & Small-Size Legibility Strip (192, 144, 96, 72, 48, 36, 24 px)
    sdraw.line([(60, 725), (1540, 725)], fill=(28, 34, 48, 255), width=1)
    sdraw.text((60, 748), "SMALL-SCALE LAUNCHER & STATUS-BAR LEGIBILITY TEST (192px → 24px)", fill=(230, 199, 156, 230))

    sx = 60
    base_y = 795
    for px_s, dname in [
        (192, "xxxhdpi (192px)"),
        (144, "xxhdpi (144px)"),
        (96, "xhdpi (96px)"),
        (72, "hdpi (72px)"),
        (48, "mdpi (48px)"),
        (36, "notif (36px)"),
        (24, "status (24px)"),
    ]:
        y_aligned = base_y + (192 - px_s) // 2
        icon_src = viewport_288 if px_s >= 48 else minimal_512
        scaled = icon_src.resize((px_s, px_s), Image.Resampling.LANCZOS)
        masked = Image.new("RGBA", (px_s, px_s), (0, 0, 0, 0))
        masked.paste(scaled, (0, 0), make_squircle_mask(px_s) if px_s >= 36 else make_circle_mask(px_s))
        sheet.paste(masked, (sx, y_aligned), masked)
        sdraw.text((sx, base_y + 205), dname, fill=(165, 175, 192, 255))
        sx += max(135, px_s + 54)

    # Safe Zone Wireframe Diagram on far right
    diag_s = 192
    dx_pos = 1320
    wf = adaptive_comp.resize((diag_s, diag_s), Image.Resampling.LANCZOS)
    sheet.paste(wf, (dx_pos, base_y), wf)
    # 72dp viewport box & 66dp safe-zone circle
    vp_margin = int(diag_s * (18 / 108))
    sz_margin = int(diag_s * (21 / 108))
    sdraw.rectangle((dx_pos, base_y, dx_pos + diag_s, base_y + diag_s), outline=(90, 102, 122, 255), width=1)
    sdraw.rectangle(
        (dx_pos + vp_margin, base_y + vp_margin, dx_pos + diag_s - vp_margin, base_y + diag_s - vp_margin),
        outline=(230, 199, 156, 200),
        width=1,
    )
    sdraw.ellipse(
        (dx_pos + sz_margin, base_y + sz_margin, dx_pos + diag_s - sz_margin, base_y + diag_s - sz_margin),
        outline=(0, 229, 255, 235),
        width=2,
    )
    sdraw.text((dx_pos, base_y + 205), "66dp SAFE ZONE", fill=(0, 229, 255, 255))

    sheet.save(OUT_ICONS / "mesad_icon_preview_sheet.png")
    sheet.save(STUDIO_PUBLIC_ICONS / "mesad_icon_preview_sheet.png")
    print("Icon suite generated successfully!")


if __name__ == "__main__":
    build_all()
