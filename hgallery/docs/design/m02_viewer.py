import lib
from lib import BG, SURFACE, ELEVATED, BORDER, TEXT, TEXT2, ACCENT, F, rrect, text, rounded
from PIL import Image, ImageDraw

S = 2
W, H = 390, 844
w, h = W * S, H * S
def px(v): return int(v * S)

canvas = Image.new("RGB", (w, h), (0, 0, 0))
d = ImageDraw.Draw(canvas)

# ── the photograph ───────────────────────────────────────────────────────────
# Its own evidence, cropped to the photo band, sitting between the chrome zones.
src = Image.open("/tmp/mock/06-viewer.png").convert("RGB")
photo = src.crop((0, 150, 320, 470)).resize((w, px(320)), Image.LANCZOS)
PHOTO_Y = 152
canvas.paste(photo, (0, px(PHOTO_Y)))

# top scrim over the photo so white chrome stays legible on a bright sky
top_scrim = Image.new("RGBA", (w, px(120)), (0, 0, 0, 0))
sd = ImageDraw.Draw(top_scrim)
for i in range(px(120)):
    sd.line((0, i, w, i), fill=(0, 0, 0, int(170 * (1 - i / px(120)))))
canvas.paste(Image.alpha_composite(canvas.crop((0, px(PHOTO_Y), w, px(PHOTO_Y) + px(120))).convert("RGBA"), top_scrim).convert("RGB"), (0, px(PHOTO_Y)))

# ── top chrome ───────────────────────────────────────────────────────────────
text(d, (px(24), px(26)), "9:41", F(px(13), 600), TEXT)

d.ellipse((px(14), px(48), px(62), px(96)), fill=(22, 22, 22))
d.line((px(31), px(65), px(45), px(79)), fill=TEXT, width=px(2))
d.line((px(45), px(65), px(31), px(79)), fill=TEXT, width=px(2))

text(d, (px(W/2), px(64)), "4 of 1,284", F(px(13), 500), (255, 255, 255, 215), anchor="ma")

for cx in (340, 376):
    d.ellipse((px(cx - 19), px(52), px(cx + 19), px(90)), fill=(22, 22, 22))
d.ellipse((px(334), px(66), px(346), px(78)), outline=TEXT, width=px(2))
d.line((px(340), px(66), px(340), px(60)), fill=TEXT, width=px(2))
for yy in (65, 71, 77):
    d.ellipse((px(374), px(yy - 2), px(378), px(yy + 2)), fill=TEXT)

# ── the info panel: directly under the photograph ────────────────────────────
PANEL_Y = PHOTO_Y + 320 + 20
rrect(d, (px(16), px(PANEL_Y), px(W - 16), px(PANEL_Y + 62)), px(18), fill=(15, 15, 15))
rrect(d, (px(16), px(PANEL_Y), px(W - 16), px(PANEL_Y + 62)), px(18), outline=(38, 38, 38), w=px(1))
text(d, (px(34), px(PANEL_Y + 11)), "IMG_2042.HEIC", F(px(14), 600), TEXT)
text(d, (px(34), px(PANEL_Y + 33)), "24 Nov 2024 · 12.4 MP · 4.2 MB", F(px(11.5), 400), TEXT2)
d.line((px(W - 46), px(PANEL_Y + 24), px(W - 38), px(PANEL_Y + 32)), fill=TEXT2, width=px(2))
d.line((px(W - 38), px(PANEL_Y + 32), px(W - 30), px(PANEL_Y + 24)), fill=TEXT2, width=px(2))

# ── filmstrip ────────────────────────────────────────────────────────────────
STRIP_Y = H - 132
g = Image.open("/tmp/mock/01-photos.png").convert("RGB")
CELL = 54
cells = [g.crop(b) for b in [(1,200,76,276),(82,200,157,276),(162,200,238,276),(242,200,316,276),(1,286,76,356),(162,286,238,356)]]
cx = 16
for i, c in enumerate(cells):
    im = c.resize((px(CELL), px(CELL)), Image.LANCZOS)
    canvas.paste(rounded(im, px(10)), (px(cx), px(STRIP_Y)), rounded(im, px(10)))
    if i == 3:
        rrect(d, (px(cx - 2), px(STRIP_Y - 2), px(cx + CELL + 2), px(STRIP_Y + CELL + 2)), px(12), outline=ACCENT, w=px(2))
        d.rounded_rectangle((px(cx + 16), px(STRIP_Y + CELL + 7), px(cx + CELL - 16), px(STRIP_Y + CELL + 9)), radius=px(1), fill=ACCENT)
    cx += CELL + 9

# the strip fades on the right so it reads as scrollable
fade = Image.new("RGBA", (px(64), px(CELL + 20)), (0, 0, 0, 0))
fd = ImageDraw.Draw(fade)
for i in range(px(64)):
    fd.line((i, 0, i, px(CELL + 20)), fill=(0, 0, 0, int(255 * i / px(64))))
canvas.paste(Image.alpha_composite(canvas.crop((px(W - 64), px(STRIP_Y - 6), w, px(STRIP_Y - 6) + px(CELL + 20))).convert("RGBA"), fade).convert("RGB"), (px(W - 64), px(STRIP_Y - 6)))

# ── action row ───────────────────────────────────────────────────────────────
ROW_Y = H - 44
acts = ["share", "heart", "rotate", "delete"]
start = W / 2 - 1.5 * 66
for i, a in enumerate(acts):
    cx0 = start + i * 66
    d.ellipse((px(cx0 - 21), px(ROW_Y - 21), px(cx0 + 21), px(ROW_Y + 21)), fill=(18, 18, 18))
    if a == "heart":
        d.polygon([(px(cx0), px(ROW_Y + 8)), (px(cx0 - 8), px(ROW_Y - 1)), (px(cx0 - 8), px(ROW_Y - 7)),
                   (px(cx0), px(ROW_Y - 1)), (px(cx0 + 8), px(ROW_Y - 7)), (px(cx0 + 8), px(ROW_Y - 1))], fill=ACCENT)
    elif a == "share":
        d.line((px(cx0), px(ROW_Y + 9), px(cx0), px(ROW_Y - 6)), fill=TEXT, width=px(2))
        d.line((px(cx0 - 6), px(ROW_Y), px(cx0), px(ROW_Y - 6)), fill=TEXT, width=px(2))
        d.line((px(cx0 + 6), px(ROW_Y), px(cx0), px(ROW_Y - 6)), fill=TEXT, width=px(2))
        d.line((px(cx0 - 8), px(ROW_Y + 3), px(cx0 - 8), px(ROW_Y + 9)), fill=TEXT, width=px(2))
        d.line((px(cx0 + 8), px(ROW_Y + 3), px(cx0 + 8), px(ROW_Y + 9)), fill=TEXT, width=px(2))
    elif a == "rotate":
        d.arc((px(cx0 - 8), px(ROW_Y - 8), px(cx0 + 8), px(ROW_Y + 8)), 45, 340, fill=TEXT, width=px(2))
        d.polygon([(px(cx0 + 5), px(ROW_Y - 10)), (px(cx0 + 10), px(ROW_Y - 4)), (px(cx0 + 3), px(ROW_Y - 3))], fill=TEXT)
    else:
        d.line((px(cx0 - 7), px(ROW_Y - 7), px(cx0 + 7), px(ROW_Y - 7)), fill=TEXT, width=px(2))
        rrect(d, (px(cx0 - 5), px(ROW_Y - 7), px(cx0 + 5), px(ROW_Y + 8)), px(2), outline=TEXT, w=px(2))
        d.line((px(cx0 - 2), px(ROW_Y - 10), px(cx0 + 2), px(ROW_Y - 10)), fill=TEXT, width=px(2))

canvas.resize((W, H), Image.LANCZOS).save("/tmp/mock/02-viewer.png")
print("saved 02-viewer.png")
