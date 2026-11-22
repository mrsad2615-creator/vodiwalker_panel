#!/usr/bin/env python3
"""What fraction of the page actually changed between two app screenshots?

Standard library only, on purpose. An earlier version imported NumPy and failed
on the CI runner, where NumPy is not installed; the failure was swallowed and the
verdict silently became "no change", which is the same class of mistake this
project keeps having to fix. A verification tool must not have an optional
dependency.

The status bar and gesture area are masked: a clock that ticked is otherwise
enough to make "they differ" true.

Exit codes: 0 when the frames differ meaningfully, 1 when they do not (or either
frame cannot be read). A missing frame is a failure, never a silent pass.

Usage: compare_frames.py BEFORE.png AFTER.png [threshold]
"""

from __future__ import annotations

import struct
import sys
import zlib

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def read_png(path: str) -> tuple[int, int, list[bytearray]]:
    """Decode a non-interlaced 8-bit RGB/RGBA PNG into rows of RGB bytes."""
    with open(path, "rb") as handle:
        data = handle.read()
    if not data.startswith(PNG_SIGNATURE):
        raise ValueError("not a PNG")

    offset = len(PNG_SIGNATURE)
    width = height = 0
    channels = 3
    idat = bytearray()

    while offset < len(data):
        (length,) = struct.unpack(">I", data[offset:offset + 4])
        kind = data[offset + 4:offset + 8]
        body = data[offset + 8:offset + 8 + length]
        offset += 12 + length

        if kind == b"IHDR":
            width, height, depth, colour_type, _, _, interlace = struct.unpack(">IIBBBBB", body)
            if depth != 8 or interlace != 0:
                raise ValueError("only 8-bit non-interlaced PNGs are supported")
            channels = {0: 1, 2: 3, 4: 2, 6: 4}[colour_type]
        elif kind == b"IDAT":
            idat.extend(body)
        elif kind == b"IEND":
            break

    raw = zlib.decompress(bytes(idat))
    stride = width * channels
    rows: list[bytearray] = []
    previous = bytearray(stride)

    position = 0
    for _ in range(height):
        filter_type = raw[position]
        position += 1
        line = bytearray(raw[position:position + stride])
        position += stride

        if filter_type == 1:      # Sub
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif filter_type == 2:    # Up
            for i in range(stride):
                line[i] = (line[i] + previous[i]) & 0xFF
        elif filter_type == 3:    # Average
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((left + previous[i]) >> 1)) & 0xFF
        elif filter_type == 4:    # Paeth
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = previous[i]
                c = previous[i - channels] if i >= channels else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                predictor = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + predictor) & 0xFF

        rows.append(line)
        previous = line

    return width, height, rows


def rgb_at(row: bytearray, x: int, channels: int) -> tuple[int, int, int]:
    base = x * channels
    return row[base], row[base + 1], row[base + 2]


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    before_path, after_path = sys.argv[1], sys.argv[2]
    threshold = float(sys.argv[3]) if len(sys.argv) > 3 else 0.02

    try:
        w1, h1, rows1 = read_png(before_path)
        w2, h2, rows2 = read_png(after_path)
    except Exception as exc:  # any failure means "cannot verify"
        print(f"unreadable: {exc}")
        return 1

    width, height = min(w1, w2), min(h1, h2)
    top, bottom = int(height * 0.06), int(height * 0.96)

    changed = total = 0
    for y in range(top, bottom):
        row_a, row_b = rows1[y], rows2[y]
        for x in range(width):
            a = rgb_at(row_a, x, 4)
            b = rgb_at(row_b, x, 4)
            total += 1
            if abs(a[0] - b[0]) + abs(a[1] - b[1]) + abs(a[2] - b[2]) > 24:
                changed += 1

    fraction = changed / total if total else 0.0
    print(f"{fraction:.4f}")

    if fraction < threshold:
        print(
            f"::warning title=frame comparison::below {threshold:.0%} of the page "
            f"changed ({fraction:.1%})"
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
