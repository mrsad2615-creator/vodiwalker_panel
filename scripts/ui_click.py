#!/usr/bin/env python3
"""Find the tap target for a UI text/emoji from a uiautomator dump.

Usage: ui_click.py <dump.xml> <needle> [--index N]
Prints "x y" (center of the smallest clickable ancestor around the text).
"""
import re
import sys


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: ui_click.py dump.xml needle", file=sys.stderr)
        return 1
    path, needle = sys.argv[1], sys.argv[2]
    xml = open(path, encoding="utf-8", errors="ignore").read()

    nodes = []
    for m in re.finditer(r"<node\b[^>]*>", xml):
        tag = m.group(0)
        attrs = dict(re.findall(r'(\w[\w-]*)="([^"]*)"', tag))
        bounds = attrs.get("bounds", "")
        bm = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", bounds)
        if not bm:
            continue
        x1, y1, x2, y2 = (int(v) for v in bm.groups())
        nodes.append(
            {
                "text": attrs.get("text", ""),
                "desc": attrs.get("content-desc", ""),
                "clickable": attrs.get("clickable") == "true",
                "box": (x1, y1, x2, y2),
            }
        )

    hits = [
        n
        for n in nodes
        if needle in n["text"] or needle in n["desc"] or n["text"] in needle
    ]
    if not hits:
        print(f"NOTFOUND {needle}", file=sys.stderr)
        return 2

    target = hits[0]
    tx1, ty1, tx2, ty2 = target["box"]

    # smallest clickable node that contains the text node
    best = None
    for n in nodes:
        x1, y1, x2, y2 = n["box"]
        if not n["clickable"]:
            continue
        if x1 <= tx1 and y1 <= ty1 and x2 >= tx2 and y2 >= ty2:
            area = (x2 - x1) * (y2 - y1)
            if best is None or area < best[0]:
                best = (area, (x1, y1, x2, y2))

    box = best[1] if best else target["box"]
    x1, y1, x2, y2 = box
    print(f"{(x1 + x2) // 2} {(y1 + y2) // 2}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
