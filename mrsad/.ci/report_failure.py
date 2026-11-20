"""Converts build/lint failure output into GitHub workflow annotations.

Why: artifact & log downloads can be unavailable to some clients, but
`::error::`-style workflow commands are always surfaced on the job summary and
in `gh run view`. This keeps every CI failure actionable without extra access.
"""
import pathlib
import re
import sys


def annotate(title: str, body: str, limit: int = 3600) -> None:
    body = body.strip()
    if not body:
        return
    body = body[:limit].replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    print(f"::error title={title}::{body}")


def main() -> int:
    build_log = pathlib.Path(sys.argv[1])
    lint_report = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else None

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

    if lint_report is not None and lint_report.exists():
        lint = lint_report.read_text(errors="replace")
        error_lines = []
        for line in lint.splitlines():
            if ": Error:" in line:
                error_lines.append(line.strip())
        if error_lines:
            annotate("MRSAD lint errors", "\n".join(error_lines[:20]))
        else:
            annotate("MRSAD lint report", lint[:2000])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
