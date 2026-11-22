#!/usr/bin/env python3
"""Turn the relevant lines of a build log into GitHub Actions annotations.

Workflow logs are not readable from every environment; annotations are. This
script is therefore the *only* channel that reliably carries a failure reason
back out of a run, so it must never crash: a missing log is a warning, not an
error.
"""
import sys
from pathlib import Path

MAX_ANNOTATIONS = 40
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
    "Error:",
    "lint",
    "Lint found",
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
    hits = []
    for index, line in enumerate(lines):
        if any(k in line for k in KEYWORDS):
            hits.append(line)
        elif "What went wrong" in line:
            # The lines after this header carry the actual cause, and they rarely
            # match any keyword on their own.
            hits.extend(lines[index + 1:index + 5])
    chosen = (hits or lines)[-MAX_ANNOTATIONS:]

    for line in chosen:
        print(f"::error title={title}::{escape(line.strip())[:900]}")

    print(f"::notice title={title}::{len(lines)} log lines, {len(hits)} matched a failure keyword")

    # Keyword matching is a heuristic and silently returns nothing useful when it
    # misses. That cost several CI cycles: a run failed with an empty annotation
    # set because the real cause ("Could not resolve all files for configuration")
    # was one line above the 40-line window. So when nothing matched, always emit
    # the tail verbatim. The tail is where Gradle puts a configuration failure.
    if not hits:
        for line in lines[-25:]:
            print(f"::error title={title} tail::{escape(line.strip())[:900]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
