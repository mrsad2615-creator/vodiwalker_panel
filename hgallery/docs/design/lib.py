from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math

# NOIR tokens, exactly as specified in the directive.
BG        = (5,5,5)
BG2       = (17,17,17)
SURFACE   = (25,25,25)
ELEVATED  = (34,34,34)
BORDER    = (56,56,56)
TEXT      = (242,240,235)
TEXT2     = (146,146,146)
ACCENT    = (242,138,54)

FONT = "/home/user/vodiwalker_panel/hgallery/app/src/main/res/font/inter_variable.ttf"
FONT_FA = "/home/user/vodiwalker_panel/gallery/app/src/main/res/font/vazirmatn_medium.ttf"

def F(size, weight=None):
    # Inter variable: PIL picks the default instance; set_variation_by_axes gives weight.
    f = ImageFont.truetype(FONT, size)
    if weight is not None:
        try:
            f.set_variation_by_axes([weight])
        except Exception:
            pass
    return f

def FA(size, weight=None):
    f = ImageFont.truetype("/home/user/vodiwalker_panel/hgallery/app/src/main/res/font/vazirmatn_medium.ttf", size)
    return f

def rrect(d, box, r, fill=None, outline=None, w=1, width=None):
    if width is not None: w = width
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=w)

def text(d, xy, s, font, fill, anchor="la"):
    d.text(xy, s, font=font, fill=fill, anchor=anchor)

def vignette(img, strength=0.55):
    # A soft top/bottom falloff so a photograph sits in the dark. Done in place.
    w, h = img.size
    grad = Image.new("L", (1, h))
    for y in range(h):
        t = y / max(1, h - 1)
        v = int(255 * min(1.0, (1 - t) ** 2 * strength + t * 0.0))
        grad.putpixel((0, y), v)
    grad = grad.resize((w, h))
    dark = Image.new("RGB", (w, h), BG)
    return Image.composite(img, dark, grad.point(lambda v: 255 - int(v * 0.0)))

def rounded(img, r):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, img.size[0] - 1, img.size[1] - 1), radius=r, fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out

def phone_frame(canvas, xy, size, radius=44):
    d = ImageDraw.Draw(canvas)
    x, y = xy
    w, h = size
    rrect(d, (x - 10, y - 10, x + w + 10, y + h + 10), radius + 10, fill=(38, 38, 40))
    rrect(d, (x - 6, y - 6, x + w + 6, y + h + 6), radius + 6, fill=(12, 12, 13))
