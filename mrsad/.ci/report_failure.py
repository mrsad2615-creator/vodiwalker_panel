"""Converts build/lint failure output into GitHub workflow annotations.

Why: artifact & log downloads can be unavailable to some clients, but
`::error::`-style workflow commands are always surfaced on the job summary and
in `gh run view`. This keeps every CI failure actionable without extra access.

Paths are resolved relative to the *working directory of the job*, which for
the MRSAD build job is the `mrsad/` project directory (not the repository
root). The script therefore accepts project-relative paths such as
`build.log` and `app/build/reports/lint-results-debug.txt`.
"""
from __future__ import annotations

import glob
import pathlib
import re
import sys


def annotate(title: str, body: str, limit: int = 3600) -> None:
    body = body.strip()
    if not body:
        return
    body = body[:limit].replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    print(f"::error title={title}::{body}")


def note(title: str, body: str, limit: int = 3600) -> None:
    body = body.strip()
    if not body:
        return
    body = body[:limit].replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    print(f"::notice title={title}::{body}")


def find_lint_report(given: str | None) -> pathlib.Path | None:
    """Resolves the lint report, falling back to a search of the build dir."""
    if given:
        candidate = pathlib.Path(given)
        if candidate.exists():
            return candidate
    for pattern in (
        "app/build/reports/lint-results-*.txt",
        "app/build/reports/lint-results*.txt",
        "**/lint-results-*.txt",
    ):
        matches = sorted(glob.glob(pattern, recursive=True))
        if matches:
            return pathlib.Path(matches[0])
    return None


def main() -> int:
    argv = sys.argv[1:]
    build_log = pathlib.Path(argv[0]) if argv else pathlib.Path("build.log")
    lint_report = find_lint_report(argv[1] if len(argv) > 1 else None)

    if build_log.exists():
        text = build_log.read_text(errors="replace")

        errors = [line for line in text.splitlines() if line.startswith("e: file://")]
        if errors:
            annotate("MRSAD compile errors", "\n".join(errors[:25]))

        if "What went wrong" in text:
            idx = text.find("What went wrong")
            annotate("MRSAD Gradle failure", text[idx:idx + 1500])

        markers = [line for line in text.splitlines()
                   if re.search(r"(FAILED|Execution failed|Lint found .* error)", line)]
        if markers:
            annotate("MRSAD failing tasks", "\n".join(markers[:15]))

        # The lint summary is printed by the Gradle task itself; surface it even
        # when the XML/text report could not be located.
        for line in text.splitlines():
            if re.search(r"Lint found \d+ error", line):
                annotate("MRSAD lint summary", line)

    if lint_report is not None and lint_report.exists():
        lint = lint_report.read_text(errors="replace")
        error_lines = [line.strip() for line in lint.splitlines() if ": Error:" in line]
        warning_lines = [line.strip() for line in lint.splitlines() if ": Warning:" in line]
        if error_lines:
            annotate("MRSAD lint errors", "\n".join(error_lines[:20]))
        if warning_lines:
            note("MRSAD lint warnings", "\n".join(warning_lines[:20]))
        if not error_lines and not warning_lines:
            annotate("MRSAD lint report", lint[:2000])
    else:
        note(
            "MRSAD lint report not found",
            f"no lint report at {argv[1] if len(argv) > 1 else '(not given)'}",
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
