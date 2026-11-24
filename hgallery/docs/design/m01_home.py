import lib
from lib import BG, BG2, SURFACE, ELEVATED, BORDER, TEXT, TEXT2, ACCENT, F, FA, rrect, text, rounded
from PIL import Image, ImageDraw
import os

W, H = 390, 844           # a 390 dp phone at 1x; renders at 2x below
S = 2                     # supersample factor
w, h = W * S, H * S

canvas = Image.new("RGB", (w, h), BG)
d = ImageDraw.Draw(canvas)

def px(v): return int(v * S)

# ── status bar ────────────────────────────────────────────────────────────────
text(d, (px(24), px(18)), "9:41", F(px(13), 600), TEXT)
# signal / battery as simple marks, drawn not typed
for i in range(4):
    bh = px(3 + i * 2)
    d.rounded_rectangle((px(300 + i * 5), px(22 - i * 2), px(302 + i * 5), px(24)), radius=px(1), fill=TEXT)
rrect(d, (px(334), px(16), px(364), px(26)), px(4), outline=TEXT2, w=px(1))
d.rounded_rectangle((px(336), px(18), px(357), px(24)), radius=px(2), fill=TEXT)

# ── header: a big editorial title on the left, two round actions on the right ──
text(d, (px(24), px(50)), "Photos", F(px(34), 600), TEXT)          # headlineLarge, -0.6 tracking in app
text(d, (px(24), px(96)), "1,284 photos · 96 videos", F(px(14), 400), TEXT2)

for cx in (322, 366):
    d.ellipse((px(cx - 17), px(54), px(cx + 17), px(88)), fill=SURFACE)
# search glyph
d.ellipse((px(313), px(64), px(325), px(76)), outline=TEXT, width=px(2))
d.line((px(324), px(75), px(330), px(81)), fill=TEXT, width=px(2))
# filter glyph
for i, yy in enumerate((64, 70, 76)):
    d.line((px(357), px(yy), px(375), px(yy)), fill=TEXT, width=px(2))
    d.ellipse((px(363), px(yy - 2), px(367), px(yy + 2)), fill=BG, outline=TEXT, width=px(2))

# ── filter chips: a row that scrolls, the active one carries the accent ───────
chips = [("All", True), ("Favourites", False), ("Videos", False), ("Screenshots", False)]
cx = 24
for label, active in chips:
    fw = d.textlength(label, font=F(px(13), 500))
    cw = int(fw) + px(30)
    box = (px(cx), px(126), px(cx) + cw, px(160))
    if active:
        rrect(d, box, px(17), fill=ACCENT)
        text(d, (px(cx) + px(15), px(136)), label, F(px(13), 600), (12, 8, 4))
    else:
        rrect(d, box, px(17), fill=SURFACE)
        text(d, (px(cx) + px(15), px(136)), label, F(px(13), 500), TEXT2)
    cx += cw // S + 8 // 1
    cx += 8

# ── the grid: day header, then an editorial hybrid ───────────────────────────
top = 186
text(d, (px(24), px(top)), "TODAY", F(px(11), 600), TEXT2)
text(d, (px(300), px(top)), "1 of 9", F(px(11), 500), TEXT2)

# Real pixels from the emulator capture, cut into tiles.
src = Image.open("/tmp/mock/01-photos.png").convert("RGB")
# the 4-column strip in the capture runs roughly y=100..410 across x=0..320
# Bounds read off a 20 dp ruler overlaid on the capture. Every rectangle below was
# checked to contain photograph and no text: no header, no "0:06" badge, no gap.
BOXES = [
    (1, 200, 76, 258),    # lake and mountain, top left
    (82, 200, 157, 258),  # neon city at night, top second
    (162, 200, 238, 276), # snow town
    (242, 200, 316, 276), # dune at dusk
    (1, 286, 76, 356),    # breakfast table
    (82, 286, 157, 356),  # autumn trees
    (162, 286, 238, 356), # portrait
    (242, 286, 316, 356), # neon reflection
    (1, 366, 76, 436),    # sunset palms
    (82, 366, 157, 436),  # lake, second
]
tiles = [src.crop(b) for b in BOXES]

GRID_TOP = 208
gx, gap = 24, 6
col_w = (W - gx * 2 - gap * 3) // 4        # 4 columns

# An editorial rhythm: one tall hero per block, then small tiles. This is what
# makes the grid stop reading as a spreadsheet.
def tile(img, x, y, tw, th, r=10):
    im = img.resize((px(tw), px(th)), Image.LANCZOS)
    canvas.paste(rounded(im, px(r)), (px(x), px(y)), rounded(im, px(r)))

# hero spans two columns and three rows
hero = tiles[0].resize((px(col_w * 2 + gap), px(col_w * 3 + gap * 2)), Image.LANCZOS)
canvas.paste(rounded(hero, px(12)), (px(gx), px(GRID_TOP)), rounded(hero, px(12)))

tile(tiles[1], gx + col_w * 2 + gap * 2, GRID_TOP, col_w, col_w)
tile(tiles[2], gx + col_w * 3 + gap * 3, GRID_TOP, col_w, col_w)
tile(tiles[3], gx + col_w * 2 + gap * 2, GRID_TOP + col_w + gap, col_w, col_w)
tile(tiles[4], gx + col_w * 3 + gap * 3, GRID_TOP + col_w + gap, col_w, col_w)
tile(tiles[5], gx + col_w * 2 + gap * 2, GRID_TOP + (col_w + gap) * 2, col_w, col_w)
tile(tiles[6], gx + col_w * 3 + gap * 3, GRID_TOP + (col_w + gap) * 2, col_w, col_w)

row2 = GRID_TOP + col_w * 3 + gap * 2 + 8
ROW2 = [tiles[7], tiles[8], tiles[9]]  # spare-white crop removed
for i, t in enumerate(ROW2):
    tile(t, gx + i * (col_w + gap), row2, col_w, col_w)

# The fourth cell is a clip: poster plus duration, which is how a video reads in a
# grid of photographs without a second icon system.
vid_src = Image.open("/tmp/mock/13-video-player.png").convert("RGB")
vid = vid_src.crop((20, 250, 300, 410)).resize((px(col_w), px(col_w)), Image.LANCZOS)
vx = gx + 3 * (col_w + gap)
canvas.paste(rounded(vid, px(10)), (px(vx), px(row2)), rounded(vid, px(10)))
rrect(d, (px(vx + col_w - 36), px(row2 + col_w - 18), px(vx + col_w - 4), px(row2 + col_w - 4)),
      px(6), fill=(0, 0, 0))
text(d, (px(vx + col_w - 31), px(row2 + col_w - 16)), "0:05", F(px(8.5), 600), TEXT)

# video badge on the one that is a clip
bx, by = gx + col_w + gap, row2
rrect(d, (px(bx + col_w - 34), px(by + col_w - 16), px(bx + col_w - 4), px(by + col_w - 3)), px(6), fill=(0, 0, 0))
rrect(d, (px(bx + col_w - 34), px(by + col_w - 16), px(bx + col_w - 4), px(by + col_w - 3)), px(6), fill=None, outline=(255,255,255,30))
text(d, (px(bx + col_w - 30), px(by + col_w - 14)), "0:06", F(px(8), 600), TEXT)

# ── the floating dock: a pill that sits above the content, not a bar ─────────
dock_y = H - 96
rrect(d, (px(20), px(dock_y), px(W - 20), px(dock_y + 60)), px(30), fill=(20, 20, 20))
rrect(d, (px(20), px(dock_y), px(W - 20), px(dock_y + 60)), px(30), outline=(44, 44, 44), w=px(1))

tabs = ["Photos", "Albums", "Highlights", "Discover"]
active_i = 0
slot = (W - 40) / 4
for i, name in enumerate(tabs):
    cx0 = 20 + slot * i + slot / 2
    if i == active_i:
        rrect(d, (px(cx0 - 26), px(dock_y + 8), px(cx0 + 26), px(dock_y + 52)), px(22), fill=ELEVATED)
        # house glyph, accented
        hx, hy = cx0, dock_y + 22
        d.line((px(hx - 8), px(hy + 2), px(hx), px(hy - 5)), fill=ACCENT, width=px(2))
        d.line((px(hx), px(hy - 5), px(hx + 8), px(hy + 2)), fill=ACCENT, width=px(2))
        d.line((px(hx - 6), px(hy + 1), px(hx - 6), px(hy + 8)), fill=ACCENT, width=px(2))
        d.line((px(hx + 6), px(hy + 1), px(hx + 6), px(hy + 8)), fill=ACCENT, width=px(2))
        d.line((px(hx - 6), px(hy + 8), px(hx + 6), px(hy + 8)), fill=ACCENT, width=px(2))
        text(d, (px(cx0), px(dock_y + 38)), "Photos", F(px(8), 600), ACCENT, anchor="ma")
    else:
        hx, hy = cx0, dock_y + 22
        # three different silhouettes: albums (stack), highlights (spark), discover (compass)
        if i == 1:
            rrect(d, (px(hx - 8), px(hy - 6), px(hx + 4), px(hy + 6)), px(3), outline=TEXT2, w=px(2))
            rrect(d, (px(hx - 4), px(hy - 2), px(hx + 8), px(hy + 8)), px(3), outline=TEXT2, w=px(2))
        elif i == 2:
            d.line((px(hx), px(hy - 7), px(hx), px(hy + 6)), fill=TEXT2, width=px(2))
            d.line((px(hx - 7), px(hy), px(hx + 7), px(hy)), fill=TEXT2, width=px(2))
            d.line((px(hx - 4), px(hy - 4), px(hx + 4), px(hy + 4)), fill=TEXT2, width=px(2))
        else:
            d.ellipse((px(hx - 7), px(hy - 7), px(hx + 7), px(hy + 7)), outline=TEXT2, width=px(2))
            d.polygon([(px(hx + 3), px(hy - 3)), (px(hx - 1), px(hy + 1)), (px(hx + 1), px(hy - 1))], fill=TEXT2)

# ── menu glyph at the very top right of the dock row is not needed; keep it clean
canvas = canvas.resize((W, H), Image.LANCZOS)
canvas.save("/tmp/mock/01-home.png")
print("saved 01-home.png", canvas.size)
