import lib
from lib import BG, SURFACE, ELEVATED, BORDER, TEXT, TEXT2, ACCENT, F, rrect, text, rounded
from PIL import Image, ImageDraw

S = 2
W, H = 390, 844
w, h = W * S, H * S
def px(v): return int(v * S)

canvas = Image.new("RGB", (w, h), (0, 0, 0))
d = ImageDraw.Draw(canvas)

# ── the frame ────────────────────────────────────────────────────────────────
# A real clip frame from the evidence, so the design is judged against real
# pixels rather than a colour block. 16:9 boxed inside the portrait screen.
src = Image.open("/tmp/mock/13-video-player.png").convert("RGB")
frame = src.crop((0, 236, 320, 416)).resize((w, px(292)), Image.LANCZOS)
FRAME_Y = 236
canvas.paste(frame, (0, px(FRAME_Y)))

# The player is immersive: the frame floats in black with generous room for the
# dock and the filmstrip, instead of being pinned edge to edge.
text(d, (px(24), px(26)), "9:41", F(px(13), 600), TEXT)

# ── top dock: a floating pill of secondary actions, as in the reference ──────
DOCK_Y = 60
rrect(d, (px(16), px(DOCK_Y), px(212), px(DOCK_Y + 46)), px(23), fill=(20, 20, 20))
rrect(d, (px(16), px(DOCK_Y), px(212), px(DOCK_Y + 46)), px(23), outline=(44, 44, 44), w=px(1))

# close
d.line((px(38), px(DOCK_Y + 15), px(50), px(DOCK_Y + 27)), fill=TEXT, width=px(2))
d.line((px(50), px(DOCK_Y + 15), px(38), px(DOCK_Y + 27)), fill=TEXT, width=px(2))
# aspect ratio
rrect(d, (px(72), px(DOCK_Y + 16), px(90), px(DOCK_Y + 30)), px(2), outline=TEXT, width=px(2))
# rotate
d.arc((px(104), px(DOCK_Y + 14), px(120), px(DOCK_Y + 30)), 45, 340, fill=TEXT, width=px(2))
d.polygon([(px(117), px(DOCK_Y + 12)), (px(122), px(DOCK_Y + 18)), (px(115), px(DOCK_Y + 19))], fill=TEXT)
# share
d.line((px(150), px(DOCK_Y + 29), px(150), px(DOCK_Y + 14)), fill=TEXT, width=px(2))
d.line((px(144), px(DOCK_Y + 20), px(150), px(DOCK_Y + 14)), fill=TEXT, width=px(2))
d.line((px(156), px(DOCK_Y + 20), px(150), px(DOCK_Y + 14)), fill=TEXT, width=px(2))
# lock, shown unlocked. This is the gesture lock, and it is a control, not a hint.
d.arc((px(176), px(DOCK_Y + 13), px(192), px(DOCK_Y + 25)), 180, 360, fill=TEXT, width=px(2))
rrect(d, (px(176), px(DOCK_Y + 22), px(192), px(DOCK_Y + 33)), px(2), fill=TEXT)

# ── volume pill, top right, the reference's one accent-tinted element ────────
rrect(d, (px(266), px(DOCK_Y), px(W - 16), px(DOCK_Y + 46)), px(23), fill=(28, 20, 14))
rrect(d, (px(266), px(DOCK_Y), px(W - 16), px(DOCK_Y + 46)), px(23), outline=(70, 46, 26), w=px(1))
rrect(d, (px(282), px(DOCK_Y + 21), px(344), px(DOCK_Y + 25)), px(2), fill=(64, 54, 46))
rrect(d, (px(282), px(DOCK_Y + 21), px(326), px(DOCK_Y + 25)), px(2), fill=ACCENT)
d.ellipse((px(322), px(DOCK_Y + 18), px(330), px(DOCK_Y + 28)), fill=ACCENT)
# speaker
d.polygon([(px(352), px(DOCK_Y + 19)), (px(356), px(DOCK_Y + 19)), (px(361), px(DOCK_Y + 15)),
           (px(361), px(DOCK_Y + 31)), (px(356), px(DOCK_Y + 27)), (px(352), px(DOCK_Y + 27))], fill=TEXT)
d.arc((px(358), px(DOCK_Y + 16), px(368), px(DOCK_Y + 30)), 300, 60, fill=TEXT, width=px(2))

# ── floating centre transport: −10, play/pause, +10 ─────────────────────────
CY = FRAME_Y + 146
for cx, kind in ((W/2 - 78, "back"), (W/2, "play"), (W/2 + 78, "fwd")):
    r = 27 if kind == "play" else 22
    fill = (255, 255, 255) if kind == "play" else (16, 16, 16)
    d.ellipse((px(cx - r), px(CY - r), px(cx + r), px(CY + r)), fill=fill,
              outline=None if kind == "play" else (70, 70, 70), width=px(1))
    if kind == "play":
        d.rounded_rectangle((px(cx - 6), px(CY - 11), px(cx - 1), px(CY + 11)), radius=px(2), fill=(10, 10, 10))
        d.rounded_rectangle((px(cx + 2), px(CY - 11), px(cx + 7), px(CY + 11)), radius=px(2), fill=(10, 10, 10))
    else:
        d.arc((px(cx - 8), px(CY - 8), px(cx + 8), px(CY + 8)), 0, 359, fill=TEXT, width=px(1))
        text(d, (px(cx), px(CY - 4)), "10", F(px(8.5), 700), TEXT, anchor="ma")
        if kind == "back":
            d.polygon([(px(cx - 11), px(CY)), (px(cx - 5), px(CY - 5)), (px(cx - 5), px(CY + 5))], fill=TEXT)
        else:
            d.polygon([(px(cx + 11), px(CY)), (px(cx + 5), px(CY - 5)), (px(cx + 5), px(CY + 5))], fill=TEXT)

# ── title block, lower left over the frame ──────────────────────────────────
TITLE_Y = FRAME_Y + 292 + 26
text(d, (px(24), px(TITLE_Y)), "CLIP · 5 SECONDS", F(px(10), 600), ACCENT)
text(d, (px(24), px(TITLE_Y + 16)), "clip-city.mp4", F(px(20), 600), TEXT)

# ── progress: thin, with the dragged position marked ────────────────────────
BAR_Y = TITLE_Y + 56
rrect(d, (px(24), px(BAR_Y), px(W - 24), px(BAR_Y + 3)), px(2), fill=(52, 52, 52))
rrect(d, (px(24), px(BAR_Y), px(248), px(BAR_Y + 3)), px(2), fill=ACCENT)
d.ellipse((px(242), px(BAR_Y - 4), px(254), px(BAR_Y + 7)), fill=ACCENT)
text(d, (px(24), px(BAR_Y + 12)), "0:02", F(px(10.5), 600), TEXT2)
text(d, (px(W - 24), px(BAR_Y + 12)), "0:05", F(px(10.5), 600), TEXT2, anchor="ra")

# ── the filmstrip: a real timeline, current cell accented ───────────────────
STRIP_Y = BAR_Y + 62
text(d, (px(24), px(STRIP_Y - 22)), "2 VIDEOS", F(px(10), 600), TEXT2)
g = Image.open("/tmp/mock/01-photos.png").convert("RGB")
cells = [g.crop((82, 200, 157, 276)), g.crop((1, 200, 76, 276)), g.crop((242, 200, 316, 276))]
cx = 24
for i, c in enumerate(cells):
    im = c.resize((px(56), px(56)), Image.LANCZOS)
    canvas.paste(rounded(im, px(10)), (px(cx), px(STRIP_Y)), rounded(im, px(10)))
    if i == 0:
        rrect(d, (px(cx - 2), px(STRIP_Y - 2), px(cx + 58), px(STRIP_Y + 58)), px(12), outline=ACCENT, w=px(2))
        rrect(d, (px(cx + 4), px(STRIP_Y + 4), px(cx + 30), px(STRIP_Y + 16)), px(4), fill=(0, 0, 0))
        text(d, (px(cx + 17), px(STRIP_Y + 6)), "0:05", F(px(7.5), 600), TEXT, anchor="ma")
    cx += 66

# ── the dock: four destinations, the current one accented ───────────────────
DOCK2 = H - 78
rrect(d, (px(20), px(DOCK2), px(W - 20), px(DOCK2 + 60)), px(30), fill=(18, 18, 18))
rrect(d, (px(20), px(DOCK2), px(W - 20), px(DOCK2 + 60)), px(30), outline=(42, 42, 42), w=px(1))
items = ["Info", "InSight", "Speed", "More"]
slot = (W - 40) / 4
for i, name in enumerate(items):
    cx0 = 20 + slot * i + slot / 2
    active = i == 0
    if active:
        rrect(d, (px(cx0 - 36), px(DOCK2 + 9), px(cx0 + 36), px(DOCK2 + 51)), px(21), fill=(36, 28, 20))
    text(d, (px(cx0), px(DOCK2 + 24)), name, F(px(11.5), 600 if active else 500),
         ACCENT if active else TEXT2, anchor="ma")

canvas.resize((W, H), Image.LANCZOS).save("/tmp/mock/03-player.png")
print("saved 03-player.png")
