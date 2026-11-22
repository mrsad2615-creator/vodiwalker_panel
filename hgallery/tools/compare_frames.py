#!/usr/bin/env python3
"""What fraction of the page actually changed between two app screenshots?

The status bar and the gesture area are masked, because a clock that ticked is
otherwise enough to make "they differ" true — which is exactly how a view switch
that did nothing passed a naive comparison earlier in this project.

Exit codes: 0 when the frames differ meaningfully, 1 when they do not (or when
either frame is unreadable). A missing frame is a failure, never a silent pass.

Usage: compare_frames.py BEFORE.png AFTER.png [threshold]
"""

from __future__ import annotations

import sys

import numpy as np
from PIL import Image


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    before_path, after_path = sys.argv[1], sys.argv[2]
    threshold = float(sys.argv[3]) if len(sys.argv) > 3 else 0.02

    try:
        first = np.asarray(Image.open(before_path).convert("RGB")).astype(int)
        second = np.asarray(Image.open(after_path).convert("RGB")).astype(int)
    except Exception as exc:  # noqa: BLE001 - any failure means "cannot verify"
        print(f"unreadable: {exc}")
        return 1

    height = min(first.shape[0], second.shape[0])
    width = min(first.shape[1], second.shape[1])
    first, second = first[:height, :width], second[:height, :width]

    # Mask the status bar and the gesture area.
    top, bottom = int(height * 0.06), int(height * 0.96)
    first, second = first[top:bottom], second[top:bottom]

    changed = float((np.abs(first - second).sum(axis=2) > 24).mean())
    print(f"{changed:.4f}")

    if changed < threshold:
        print(
            f"::warning title=frame comparison::below {threshold:.0%} of the page "
            f"changed ({changed:.1%})"
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
