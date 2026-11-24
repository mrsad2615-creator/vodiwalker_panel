import lib
from lib import BG, SURFACE, ELEVATED, BORDER, TEXT, TEXT2, ACCENT, F, rrect, text, rounded
from PIL import Image, ImageDraw

S = 2
W, H = 390, 844
w, h = W * S, H * S
def px(v): return int(v * S)

canvas = Image.new("RGB", (w, h), (0, 0, 0))
d = ImageDraw.Draw(canvas)

# ── the frame, dimmed: the panel is open over a playing clip ────────────────
src = Image.open("/tmp/mock/13-video-player.png").convert("RGB")
frame = src.crop((0, 236, 320, 416)).resize((w, px(200)), Image.LANCZOS)
canvas.paste(frame, (0, px(120)))
dim = Image.new("RGBA", (w, px(200)), (0, 0, 0, 130))
canvas.paste(Image.alpha_composite(canvas.crop((0, px(120), w, px(320))).convert("RGBA"), dim).convert("RGB"), (0, px(120)))

text(d, (px(24), px(26)), "9:41", F(px(13), 600), TEXT)

# ── the sheet ───────────────────────────────────────────────────────────────
SHEET_Y = 356
rrect(d, (0, px(SHEET_Y), w, h), px(28), fill=(14, 14, 14))
# the grab handle
rrect(d, (px(W/2 - 18), px(SHEET_Y + 10), px(W/2 + 18), px(SHEET_Y + 14)), px(2), fill=(58, 58, 58))

# header: the clip, then a segmented control for the four sections
text(d, (px(24), px(SHEET_Y + 30)), "clip-city.mp4", F(px(17), 600), TEXT)
text(d, (px(24), px(SHEET_Y + 54)), "1280×720 · H.264 · 30 fps · 0:05", F(px(11), 400), TEXT2)

SEG_Y = SHEET_Y + 80
segs = ["Playback", "Picture", "Audio", "More"]
rrect(d, (px(16), px(SEG_Y), px(W - 16), px(SEG_Y + 38)), px(19), fill=(22, 22, 22))
segw = (W - 32) / 4
for i, sname in enumerate(segs):
    x0 = 16 + segw * i
    if i == 0:
        rrect(d, (px(x0 + 3), px(SEG_Y + 3), px(x0 + segw - 3), px(SEG_Y + 35)), px(17), fill=(42, 42, 42))
    text(d, (px(x0 + segw/2), px(SEG_Y + 14)), sname, F(px(11.5), 600 if i == 0 else 500),
         TEXT if i == 0 else TEXT2, anchor="ma")

# ── speed: a slider with the live value, not a row of pills ─────────────────
ROW = SEG_Y + 62
text(d, (px(24), px(ROW)), "Speed", F(px(13), 500), TEXT2)
text(d, (px(W - 24), px(ROW - 2)), "1.0×", F(px(15), 700), ACCENT, anchor="ra")
SL_Y = ROW + 30
rrect(d, (px(24), px(SL_Y), px(W - 24), px(SL_Y + 4)), px(2), fill=(48, 48, 48))
knob_x = 24 + (W - 48) * 0.333
rrect(d, (px(24), px(SL_Y), px(knob_x), px(SL_Y + 4)), px(2), fill=ACCENT)
d.ellipse((px(knob_x - 6), px(SL_Y - 7), px(knob_x + 6), px(SL_Y + 11)), fill=ACCENT)
# Ticks sit under the track's own positions, not at guessed x values: 0.25x is the
# left end, 1x a third along, 2x two thirds, 3x the right end.
track_x0, track_x1 = 24, W - 24
for frac, lbl in ((0.0, "0.25×"), (0.333, "1×"), (0.667, "2×"), (1.0, "3×")):
    tx = track_x0 + (track_x1 - track_x0) * frac
    text(d, (px(tx), px(SL_Y + 18)), lbl, F(px(9.5), 500), TEXT2, anchor="ma")

# ── repeat: three states, the chosen one accented ───────────────────────────
R2 = SL_Y + 54
text(d, (px(24), px(R2)), "Repeat", F(px(13), 500), TEXT2)
states = ["Off", "One", "All"]
sx = W - 24 - len(states) * 62
for i, sname in enumerate(states):
    x0 = sx + i * 62
    on = i == 2
    rrect(d, (px(x0), px(R2 - 8), px(x0 + 56), px(R2 + 18)), px(13),
          fill=(46, 32, 20) if on else (24, 24, 24))
    text(d, (px(x0 + 28), px(R2 - 3)), sname, F(px(11), 600 if on else 500),
         ACCENT if on else TEXT2, anchor="ma")

# ── picture: aspect ratio, a visual choice ──────────────────────────────────
R3 = R2 + 52
text(d, (px(24), px(R3)), "Aspect", F(px(13), 500), TEXT2)
modes = ["Fit", "Fill", "16:9", "1:1"]
sx = W - 24 - len(modes) * 54
for i, m in enumerate(modes):
    x0 = sx + i * 54
    on = i == 0
    rrect(d, (px(x0), px(R3 - 8), px(x0 + 48), px(R3 + 18)), px(13),
          fill=(46, 32, 20) if on else (24, 24, 24))
    text(d, (px(x0 + 24), px(R3 - 3)), m, F(px(11), 600 if on else 500),
         ACCENT if on else TEXT2, anchor="ma")

# ── audio and subtitles: rows that say what is actually available ───────────
R4 = R3 + 46
def row(y, label, value, chevron=True, available=True):
    text(d, (px(24), px(y)), label, F(px(13), 500), TEXT)
    text(d, (px(W - 44), px(y)), value, F(px(12), 500), TEXT2, anchor="ra")
    if chevron:
        d.line((px(W - 34), px(y + 4), px(W - 29), px(y + 9)), fill=TEXT2, width=px(2))
        d.line((px(W - 29), px(y + 9), px(W - 24), px(y + 4)), fill=TEXT2, width=px(2))

row(R4, "Audio track", "Stereo")
d.line((px(24), px(R4 + 20), px(W - 24), px(R4 + 20)), fill=(30, 30, 30), width=px(1))
row(R4 + 30, "Subtitles", "Off")
d.line((px(24), px(R4 + 50), px(W - 24), px(R4 + 50)), fill=(30, 30, 30), width=px(1))
row(R4 + 60, "Brightness", "Auto")

# ── the honest state: what this clip does not carry ─────────────────────────
NOTE_Y = R4 + 92
text(d, (px(24), px(NOTE_Y)), "One audio track in this clip.",
     F(px(10.5), 400), (108, 108, 108))
text(d, (px(24), px(NOTE_Y + 14)), "Track and subtitle pickers appear only when a file carries them.",
     F(px(10.5), 400), (108, 108, 108))

canvas.resize((W, H), Image.LANCZOS).save("/tmp/mock/04-panel.png")
print("saved 04-panel.png")
