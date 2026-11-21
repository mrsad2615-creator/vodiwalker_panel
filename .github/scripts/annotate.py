#!/usr/bin/env python3
"""Print the tail of a build log as GitHub Actions error annotations.

A build that fails should say *why* in the run summary instead of forcing the
reader to download a multi-megabyte log.
"""
import sys
from pathlib import Path

MAX_LINES = 60


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: annotate.py <logfile> [title]", file=sys.stderr)
        return 2
    log = Path(sys.argv[1])
    title = sys.argv[2] if len(sys.argv) > 2 else "build log"
    if not log.exists():
        print(f"::warning::{title}: {log} was not produced")
        return 0
    lines = log.read_text(encoding="utf-8", errors="replace").splitlines()
    interesting = [
        line for line in lines
        if any(k in line for k in ("error:", "FAILURE", "e: ", "Caused by", "Exception", "> Task"))
    ]
    tail = (interesting or lines)[-MAX_LINES:]
    print(f"::group::{title} ({len(lines)} lines, showing {len(tail)})")
    for line in tail:
        print(line.replace("%", "%25").replace("\r", ""))
    print("::endgroup::")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
