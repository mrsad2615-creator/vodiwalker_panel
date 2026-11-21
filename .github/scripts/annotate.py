#!/usr/bin/env python3
"""Turn the relevant lines of a build log into GitHub Actions annotations.

Workflow logs are not readable from every environment; annotations are. This
script is therefore the *only* channel that reliably carries a failure reason
back out of a run, so it must never crash: a missing log is a warning, not an
error.
"""
import sys
from pathlib import Path

MAX_ANNOTATIONS = 20
KEYWORDS = (
    "error:",
    "e: ",
    "FAILURE:",
    "FAILED",
    "Caused by",
    "Exception",
    "Permission denied",
    "Could not",
    "cannot find symbol",
    "Unresolved reference",
)


def escape(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "").replace("\n", " ")


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: annotate.py <logfile> [title]")
        return 0
    log = Path(sys.argv[1])
    title = sys.argv[2] if len(sys.argv) > 2 else "build log"

    if not log.exists():
        print(f"::warning title={title}::{log} was never written")
        return 0

    lines = log.read_text(encoding="utf-8", errors="replace").splitlines()
    hits = [line for line in lines if any(k in line for k in KEYWORDS)]
    chosen = (hits or lines)[-MAX_ANNOTATIONS:]

    for line in chosen:
        print(f"::error title={title}::{escape(line.strip())[:900]}")
    print(f"::notice title={title}::{len(lines)} log lines, {len(hits)} matched a failure keyword")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
