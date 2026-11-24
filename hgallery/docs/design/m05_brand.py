import lib
from lib import BG, SURFACE, ELEVATED, BORDER, TEXT, TEXT2, ACCENT, F, rrect, text
from PIL import Image, ImageDraw
import math

def aperture(cx, cy, R, blades=6, inner=0.30, rot=-90):
    """The H Aperture Signature: a hexagonal iris whose blades leave an H."""
    # blade wedge points: the opening is a hexagon, so the H reads as the gap
    pts = []
    for i in range(blades * 2):
        a = math.radians(rot + i * 360 / (blades * 2))
        pts.append((cx + R * math.cos(a), cy + R * math.sin(a)))
    return pts

def draw_mark(d, cx, cy, size, accent=ACCENT, fg=TEXT, stroke=True):
    """Draws the mark at a given centre and diameter."""
    R = size / 2
    # outer ring, open at two arcs so it reads as an aperture housing
    box = (cx - R, cy - R, cx + R, cy + R)
    if stroke:
        d.arc(box, 8, 172, fill=accent, width=max(2, size // 26))
        d.arc(box, 188, 352, fill=accent, width=max(2, size // 26))
    # six blades: chords that rotate inward, leaving a hexagonal pupil
    innerR = R * 0.46
    for i in range(6):
        a0 = math.radians(-90 + i * 60)
        a1 = math.radians(-90 + i * 60 + 60)
        x0, y0 = cx + innerR * math.cos(a0), cy + innerR * math.sin(a0)
        x1, y1 = cx + R * 0.96 * math.cos(a1), cy + R * 0.96 * math.sin(a1)
        d.line((x0, y0, x1, y1), fill=accent, width=max(2, size // 34))
    # pupil: a small hexagon, filled, which is the light the lens lets through
    pup = []
    for i in range(6):
        a = math.radians(-90 + i * 60)
        pup.append((cx + R * 0.22 * math.cos(a), cy + R * 0.22 * math.sin(a)))
    d.polygon(pup, fill=fg)

# ── sheet ───────────────────────────────────────────────────────────────────
S = 3
W, H = 1000, 620
w, h = W * S, H * S
canvas = Image.new("RGB", (w, h), (10, 10, 10))
d = ImageDraw.Draw(canvas)
def px(v): return int(v * S)

text(d, (px(48), px(40)), "H Aperture Signature", F(px(26), 600), TEXT)
text(d, (px(48), px(74)), "One mark, six blades, one light. Read at 24 dp before it is read at 512.", F(px(13), 400), TEXT2)

# 1. the primary mark, large, on NOIR black
CARD = 268
rrect(d, (px(48), px(120), px(48 + CARD), px(120 + CARD)), px(40), fill=(15, 15, 15))
rrect(d, (px(48), px(120), px(48 + CARD), px(120 + CARD)), px(40), outline=(40, 40, 40), width=px(1))
draw_mark(d, px(48 + CARD/2), px(120 + CARD/2), px(150))
text(d, (px(48), px(120 + CARD + 14)), "Primary · black", F(px(12), 500), TEXT2)

# 2. the accent tile, as the launcher draws it
X2 = 48 + CARD + 28
rrect(d, (px(X2), px(120), px(X2 + CARD), px(120 + CARD)), px(40), fill=(242, 138, 54))
draw_mark(d, px(X2 + CARD/2), px(120 + CARD/2), px(150), accent=(20, 12, 6), fg=(255, 255, 255))
text(d, (px(X2), px(120 + CARD + 14)), "Launcher · accent", F(px(12), 500), TEXT2)

# 3. light theme, to prove the identity survives inversion
X3 = X2 + CARD + 28
rrect(d, (px(X3), px(120), px(X3 + CARD), px(120 + CARD)), px(40), fill=(247, 245, 241))
draw_mark(d, px(X3 + CARD/2), px(120 + CARD/2), px(150), accent=(198, 96, 20), fg=(20, 18, 16))
text(d, (px(X3), px(120 + CARD + 14)), "Light theme", F(px(12), 500), TEXT2)

# ── the small-size proof: the mark has to survive 24 dp ─────────────────────
ROW_Y = 120 + CARD + 60
text(d, (px(48), px(ROW_Y)), "AT ACTUAL SIZE", F(px(11), 600), TEXT2)
sizes = [48, 36, 24]
xx = 48
for sdp in sizes:
    rrect(d, (px(xx), px(ROW_Y + 26), px(xx + sdp), px(ROW_Y + 26 + sdp)), px(int(sdp * 0.24)), fill=(242, 138, 54))
    draw_mark(d, px(xx + sdp/2), px(ROW_Y + 26 + sdp/2), px(sdp * 0.62), accent=(20, 12, 6), fg=(255, 255, 255))
    text(d, (px(xx), px(ROW_Y + 26 + sdp + 8)), f"{sdp} dp", F(px(10), 500), TEXT2)
    xx += sdp + 26

# ── the mask test: circle, squircle, rounded square all keep the mark ───────
MX = 320
text(d, (px(MX), px(ROW_Y)), "LAUNCHER MASKS", F(px(11), 600), TEXT2)
mask_col = (242, 138, 54)
# circle
d.ellipse((px(MX), px(ROW_Y + 26), px(MX + 92), px(ROW_Y + 118)), fill=mask_col)
draw_mark(d, px(MX + 46), px(ROW_Y + 72), px(54), accent=(20, 12, 6), fg=(255, 255, 255))
# squircle
rrect(d, (px(MX + 112), px(ROW_Y + 26), px(MX + 204), px(ROW_Y + 118)), px(34), fill=mask_col)
draw_mark(d, px(MX + 158), px(ROW_Y + 72), px(54), accent=(20, 12, 6), fg=(255, 255, 255))
# rounded square
rrect(d, (px(MX + 224), px(ROW_Y + 26), px(MX + 316), px(ROW_Y + 118)), px(18), fill=mask_col)
draw_mark(d, px(MX + 270), px(ROW_Y + 72), px(54), accent=(20, 12, 6), fg=(255, 255, 255))
# monochrome, themed-icon form
rrect(d, (px(MX + 336), px(ROW_Y + 26), px(MX + 428), px(ROW_Y + 118)), px(34), fill=(54, 54, 54))
draw_mark(d, px(MX + 382), px(ROW_Y + 72), px(54), accent=(210, 210, 210), fg=(210, 210, 210))
text(d, (px(MX + 336), px(ROW_Y + 126)), "monochrome", F(px(10), 500), TEXT2)

canvas.resize((W, H), Image.LANCZOS).save("/tmp/mock/05-brand.png")
print("saved 05-brand.png")
