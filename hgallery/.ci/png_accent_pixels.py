#!/usr/bin/env python3
"""Count the accent-coloured pixels in a PNG, with no third-party modules.

## Why this exists

The screenshot harness needs one yes/no answer: does this still show the launch
mark, or does it show an empty cover (or the finished grid)? It tried two cheap
answers and both were wrong.

- File size. The run that caught the mark produced 14,449 bytes; the run that caught
  a blank cover produced 22,165. A blank screenshot has a flat background and should
  compress smallest, but the status bar's clock, the notification icons and the
  emulator's own noise dominate the size at this scale. The metric is not merely
  noisy, it points the wrong way.
- Anything else on the host. `ffmpeg` is absent. Pillow is not installed, and the
  script must not install packages to take a picture.

So this decodes the PNG directly. It reads the IHDR, concatenates the IDAT chunks,
inflates them with `zlib`, reverses the per-scanline filters, then counts pixels that
are close to the accent colour. Python's standard library has everything needed.

## The two signals, and why one is not enough

Hue alone counts 2,827 accent pixels in a real launch frame and 14,045 in a photo
grid, because sunset photographs are orange. The grid is not the launch. So the
second signal is flatness: the share of the screen taken by a single colour. The
cover is an empty plate (96 per cent), and a grid of photographs is not (57 per cent).
A blank cover is flat but has no accent, so it fails the first test.

| frame | accent pixels | flatness | verdict |
|---|---|---|---|
| launch cover, mark drawn | 2,827 | 96.0 % | the launch |
| blank cover, app not drawn yet | 0 | 89.2 % | not the launch |
| the finished grid | 14,045 | 56.5 % | not the launch |

## Usage

    png_accent_pixels.py <file.png>                  # print the accent pixel count
    png_accent_pixels.py <file.png> --launch         # is this a launch cover?
    png_accent_pixels.py <file.png> --min <count>    # at least this many

`--launch` prints both numbers and exits 0 only when the frame is flat and carries
the mark. The harness uses that form.

A file it cannot parse exits 2 with a message on stderr, and the caller treats that as
"no evidence" rather than as success.
"""

import struct
import sys
import zlib
from collections import Counter

# The accent, in both themes. The launch still is taken in whichever theme the
# emulator happens to be in.
ACCENTS = [
    (242, 138, 54),   # #F28A36, dark theme
    (198, 96, 20),    # #C66014, light theme
]

# Matching by distance in RGB does not work, and the measurement says so: the pale
# blade at 30 per cent progress is (246, 215, 188), which is 55 levels away from the
# light accent in red alone but visibly the same colour. Hue is the property that
# survives the fade. Saturation and value are floors, not windows, because the fade
# only moves them down.
HUE_WINDOW = 14.0     # degrees
MIN_SATURATION = 0.10
MIN_VALUE = 0.55


def _paeth(a, b, c):
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    return b if pb <= pc else c


def decode(path):
    """Return (width, height, list of (r, g, b)) for an 8-bit RGB or RGBA PNG."""
    with open(path, "rb") as handle:
        data = handle.read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")

    pos = 8
    width = height = colour_type = bit_depth = None
    idat = bytearray()
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length  # length, type, body, CRC
        if kind == b"IHDR":
            width, height, bit_depth, colour_type = struct.unpack(">IIBB", body[:10])
        elif kind == b"IDAT":
            idat += body
        elif kind == b"IEND":
            break

    if bit_depth != 8:
        raise ValueError("only 8-bit PNGs are handled, got %r" % (bit_depth,))
    channels = {2: 3, 6: 4, 0: 1}.get(colour_type)
    if channels is None:
        raise ValueError("colour type %r is not handled" % (colour_type,))

    raw = zlib.decompress(bytes(idat))
    stride = width * channels
    out = bytearray(stride * height)
    previous = bytearray(stride)

    for row in range(height):
        start = row * (stride + 1)
        method = raw[start]
        line = bytearray(raw[start + 1:start + 1 + stride])
        if method == 1:  # Sub
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif method == 2:  # Up
            for i in range(stride):
                line[i] = (line[i] + previous[i]) & 0xFF
        elif method == 3:  # Average
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((left + previous[i]) >> 1)) & 0xFF
        elif method == 4:  # Paeth
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                up_left = previous[i - channels] if i >= channels else 0
                line[i] = (line[i] + _paeth(left, previous[i], up_left)) & 0xFF
        elif method != 0:
            raise ValueError("filter %r is not defined" % (method,))
        out[row * stride:(row + 1) * stride] = line
        previous = line

    pixels = []
    for i in range(0, len(out), channels):
        if channels == 1:
            value = out[i]
            pixels.append((value, value, value))
        else:
            pixels.append((out[i], out[i + 1], out[i + 2]))
    return width, height, pixels


def _hue(r, g, b):
    """Hue in degrees, and the saturation and value that go with it."""
    high = max(r, g, b)
    low = min(r, g, b)
    if high == low:
        return 0.0, 0.0, high / 255.0
    span = high - low
    if high == r:
        hue = (60.0 * ((g - b) / span)) % 360.0
    elif high == g:
        hue = 60.0 * ((b - r) / span) + 120.0
    else:
        hue = 60.0 * ((r - g) / span) + 240.0
    return hue, span / high, high / 255.0


def accent_count(path):
    _, _, pixels = decode(path)
    target_hues = [_hue(*accent)[0] for accent in ACCENTS]
    total = 0
    for r, g, b in pixels:
        hue, saturation, value = _hue(r, g, b)
        if saturation < MIN_SATURATION or value < MIN_VALUE:
            continue
        for target in target_hues:
            difference = abs(hue - target)
            # Hue wraps at 360, so 350 and 5 degrees are 15 apart, not 345.
            if min(difference, 360.0 - difference) <= HUE_WINDOW:
                total += 1
                break
    return total


def flatness(path):
    """Percentage of the screen taken by its single most common colour."""
    _, _, pixels = decode(path)
    common = Counter(pixels).most_common(1)
    if not common:
        return 0.0
    return 100.0 * common[0][1] / len(pixels)


# A cover is an empty plate. 85 per cent is well above the grid's 57 and well below
# the two covers measured at 96 and 89, so it sits in the gap rather than on an edge.
LAUNCH_MIN_FLATNESS = 85.0
# The mark is a few thousand pixels at 220 dp on a 320 dp screen. 500 is an order of
# magnitude below the observed 2,827 and far above the blank frame's zero.
LAUNCH_MIN_ACCENT = 500


def main(argv):
    if len(argv) < 2:
        print("usage: png_accent_pixels.py <file.png> [--launch | --min N]", file=sys.stderr)
        return 2
    path = argv[1]
    try:
        if len(argv) >= 3 and argv[2] == "--launch":
            count = accent_count(path)
            flat = flatness(path)
            verdict = count >= LAUNCH_MIN_ACCENT and flat >= LAUNCH_MIN_FLATNESS
            print(
                "accent=%d (need %d) flatness=%.1f%% (need %.0f%%) -> %s"
                % (count, LAUNCH_MIN_ACCENT, flat, LAUNCH_MIN_FLATNESS,
                   "launch" if verdict else "not the launch")
            )
            return 0 if verdict else 1
        count = accent_count(path)
    except Exception as error:  # noqa: BLE001 - any failure means "no evidence"
        print("cannot read %s: %s" % (path, error), file=sys.stderr)
        return 2
    if len(argv) >= 4 and argv[2] == "--min":
        return 0 if count >= int(argv[3]) else 1
    print(count)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
