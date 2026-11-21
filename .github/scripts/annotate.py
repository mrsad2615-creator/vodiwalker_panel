#!/usr/bin/env python3
"""Publishes a file as workflow annotations.

Why: the artefacts of a run live on a storage host that some clients
cannot reach. An annotation is part of the check run, so it can be read
through the API alone, and it shows up in `gh run view` next to the job
that produced it.

The script never raises: a failing publisher must not hide the real
result.
"""
from __future__ import annotations

import pathlib
import sys

CHUNK = 16000


def publish(title: str, text: str) -> None:
    text = text.replace("\r", "")
    if not text:
        text = "(empty)"
    for start in range(0, len(text), CHUNK):
        chunk = text[start:start + CHUNK]
        escaped = chunk.replace("%", "%25").replace("\n", "%0A")
        print(f"::notice title={title}::{escaped}")


def main() -> int:
    try:
        if len(sys.argv) < 2:
            print("usage: annotate.py <file> [title]", file=sys.stderr)
            return 2
        path = pathlib.Path(sys.argv[1])
        title = sys.argv[2] if len(sys.argv) > 2 else "MRSAD measurement"
        if not path.exists():
            publish(title, f"{path} was not produced")
            return 0
        publish(title, path.read_text(errors="replace"))
    except Exception as exc:  # never let the publisher mask the result
        print(f"::notice title=MRSAD publisher::failed to publish: {exc!r}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
