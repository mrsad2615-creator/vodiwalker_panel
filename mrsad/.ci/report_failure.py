"""Converts build/lint/instrumented-test failure output into GitHub annotations.

Why: artifact & log downloads can be unavailable to some clients, but
`::error::`-style workflow commands are always surfaced on the job summary and
in `gh run view`. This keeps every CI failure actionable without extra access.

Paths are resolved relative to the *working directory of the job*:
- the build job runs in `mrsad/`, so it passes `build.log` and
  `app/build/reports/lint-results-debug.txt`;
- the emulator job runs at the repository root, so it passes `mrsad/ui-test.log`.

The script never raises: a failing reporter must not hide the real failure.
"""
from __future__ import annotations

import glob
import pathlib
import re
import sys
import xml.etree.ElementTree as ET


def emit(level: str, title: str, body: str, limit: int = 3600) -> None:
    body = (body or "").strip()
    if not body:
        return
    body = body[:limit].replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    print(f"::{level} title={title}::{body}")


def annotate(title: str, body: str, limit: int = 3600) -> None:
    emit("error", title, body, limit)


def note(title: str, body: str, limit: int = 3600) -> None:
    emit("notice", title, body, limit)


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


def report_build_log(build_log: pathlib.Path) -> None:
    if not build_log.exists():
        return
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

    for line in text.splitlines():
        if re.search(r"Lint found \d+ error", line):
            annotate("MRSAD lint summary", line)


def report_lint(lint_report: pathlib.Path | None, requested: str | None) -> None:
    if lint_report is None or not lint_report.exists():
        note("MRSAD lint report not found",
             f"no lint report at {requested or '(not given)'}")
        return
    lint = lint_report.read_text(errors="replace")
    error_lines = [line.strip() for line in lint.splitlines() if ": Error:" in line]
    warning_lines = [line.strip() for line in lint.splitlines() if ": Warning:" in line]
    if error_lines:
        annotate("MRSAD lint errors", "\n".join(error_lines[:20]))
    if warning_lines:
        note("MRSAD lint warnings", "\n".join(warning_lines[:20]))
    if not error_lines and not warning_lines:
        annotate("MRSAD lint report", lint[:2000])


def _xml_files(base: pathlib.Path | None = None) -> list[str]:
    """Finds the connected-test XML reports.

    The emulator job runs the test script from `mrsad/` but the reporter
    itself runs from the repository root, so the reports have to be looked up
    relative to the log file as well as relative to the current directory.
    """
    roots: list[pathlib.Path] = []
    if base is not None:
        parent = base.parent
        roots += [parent / "ui-test-results", parent / "app/build/outputs/androidTest-results"]
    roots += [pathlib.Path("ui-test-results"),
              pathlib.Path("app/build/outputs/androidTest-results")]

    for root in roots:
        if root.is_dir():
            found = sorted(str(p) for p in root.rglob("*.xml"))
            if found:
                return found
    return []


def report_instrumented_tests(base: pathlib.Path | None = None) -> None:
    """Turns the connected-test XML reports into one annotation per test."""
    files = _xml_files(base)
    if not files:
        note("MRSAD instrumented tests", "no connected-test result XML was produced")
        return

    summary: list[str] = []
    failures: list[str] = []
    for path in files:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for case in root.iter("testcase"):
            name = case.get("name", "?")
            classname = case.get("classname", "")
            short = name.split(".")[-1]
            status = "PASS"
            detail = ""
            for child in case:
                tag = child.tag.lower()
                if tag in ("failure", "error"):
                    status = "FAIL"
                    message = (child.get("message") or child.text or "").strip()
                    detail = message.splitlines()[0] if message else tag
                    failures.append(f"{short}: {detail}")
                elif tag == "skipped":
                    status = "SKIP"
            summary.append(f"{status:4} {short}  ({classname})")

    if summary:
        annotate("MRSAD instrumented test results", "\n".join(summary[:40]))
    if failures:
        annotate("MRSAD instrumented test failures", "\n".join(failures[:20]))


def _thread_blocks(text: str) -> list[tuple[str, list[str]]]:
    """Splits a DALVIK thread dump into (header, stack frames) pairs."""
    blocks: list[tuple[str, list[str]]] = []
    header: str | None = None
    frames: list[str] = []
    for line in text.splitlines():
        if line.startswith('DALVIK THREADS ("'):
            if header is not None:
                blocks.append((header, frames))
            header = line
            frames = []
        elif header is not None:
            if line.startswith("  at "):
                frames.append(line.rstrip())
            elif line.strip() == "":
                blocks.append((header, frames))
                header = None
                frames = []
    if header is not None:
        blocks.append((header, frames))
    return blocks


def report_thread_dumps(text: str) -> None:
    """Shows where the hung process was actually spending its time."""
    blocks = _thread_blocks(text)
    if not blocks:
        return

    interesting = ("main", "Instr", "Test", "Espresso", "Compose")
    selected: list[str] = []
    for header, frames in blocks:
        if not any(tag in header for tag in interesting):
            continue
        # Keep the header plus the deepest part of the stack: the frames that
        # explain the hang are at the bottom.
        if len(frames) <= 10:
            keep = frames
        else:
            keep = frames[:2] + ["    ..."] + frames[-8:]
        selected.append(header + "\n" + "\n".join(keep))

    if selected:
        annotate("MRSAD hung thread stacks", "\n\n".join(selected[:12]), limit=6000)


def report_ui_test_log(log: pathlib.Path) -> None:
    """Extracts the per-test markers written by run-connected-tests.sh."""
    if not log.exists():
        note("MRSAD ui test log", f"{log} was not produced")
        return
    text = log.read_text(errors="replace")
    lines = text.splitlines()

    markers = [line for line in lines
               if line.startswith(("===== BEGIN ", "===== END ", "begin ", "end ",
                                   "passed=", "--- "))
               or re.search(r"(tests? (completed|failed)|BUILD (SUCCESSFUL|FAILED)"
                            r"|Execution failed for task|AppNotIdleException"
                            r"|ComposeTimeoutException|TimeoutException"
                            r"|INSTRUMENTATION_(RESULT|CODE)|Process crashed)", line)]
    if markers:
        annotate("MRSAD instrumented test log", "\n".join(markers[-60:]))

    # The app's own logcat tells us what the process was doing.
    app_lines = [line for line in lines
                 if "MrsadTest" in line or "FATAL EXCEPTION" in line
                 or "AndroidRuntime" in line or "ANR in" in line]
    if app_lines:
        annotate("MRSAD app logcat", "\n".join(app_lines[-40:]))

    # The thread dump is the decisive evidence for a hang.
    report_thread_dumps(text)

    # The tail of the log is where a hang shows its last words.
    tail = "\n".join(lines[-60:])
    if tail.strip():
        note("MRSAD ui test log tail", tail)


def main() -> int:
    try:
        argv = sys.argv[1:]
        build_log = pathlib.Path(argv[0]) if argv else pathlib.Path("build.log")
        requested_lint = argv[1] if len(argv) > 1 else None
        is_instrumented_log = build_log.name == "ui-test.log"

        report_build_log(build_log)

        if is_instrumented_log:
            # The emulator job does not run Lint; its log carries the test run.
            report_instrumented_tests(build_log)
            report_ui_test_log(build_log)
        else:
            report_lint(find_lint_report(requested_lint), requested_lint)
    except Exception as exc:  # never let the reporter mask the real failure
        note("MRSAD reporter", f"reporter failed: {exc!r}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
