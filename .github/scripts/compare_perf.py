#!/usr/bin/env python3
"""Compares two sets of performance reports and writes a markdown summary.

Every set comes from the same benchmark, on the same emulator, driven by
the same script, several times over: the machine shares its cores with
everything else that runs on it, so a single run says more about the
machine than about the application. The middle of the runs is reported,
and so is the spread, because a difference that lies inside the spread is
not a difference.

The script never invents a number: a missing report is reported as
missing.
"""

from __future__ import annotations

import json
import os
import statistics
import sys
from typing import Iterable, Optional


def load(path: str) -> Optional[dict]:
    if not os.path.exists(path):
        return None
    try:
        with open(path, "r", encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, ValueError) as error:
        print(f"could not read {path}: {error}", file=sys.stderr)
        return None


def load_many(given: str) -> list[dict]:
    """Loads every report of a side: a file, or every json in a folder."""
    if os.path.isdir(given):
        paths = sorted(
            os.path.join(given, name)
            for name in os.listdir(given)
            if name.endswith(".json")
        )
    elif given:
        paths = [given]
    else:
        paths = []

    reports = []
    for path in paths:
        report = load(path)
        if report is not None:
            reports.append(report)
    return reports


def values_of(reports: Iterable[dict], phase: str, key: str) -> list[float]:
    """Every value of one metric over every run of one side."""
    found = []
    for report in reports:
        section = report.get(phase)
        if not section:
            continue
        value = section.get(key)
        if value is None:
            continue
        try:
            found.append(float(value))
        except (TypeError, ValueError):
            continue
    return found


def top_values(reports: Iterable[dict], key: str) -> list[float]:
    found = []
    for report in reports:
        value = report.get(key)
        if value is None:
            continue
        try:
            found.append(float(value))
        except (TypeError, ValueError):
            continue
    return found


def middle(values: list[float]) -> Optional[float]:
    if not values:
        return None
    return statistics.median(values)


def show(value: Optional[float]) -> str:
    if value is None:
        return "—"
    if abs(value - round(value)) < 0.05:
        return str(int(round(value)))
    return f"{value:.1f}"


def span(values: list[float]) -> str:
    if not values:
        return "—"
    if len(values) == 1:
        return show(values[0])
    return f"{show(min(values))}–{show(max(values))}"


def row(label: str, baseline: list[float], redesign: list[float], better: str = "lower") -> str:
    """One line of the comparison, with the verdict the data supports."""
    b = middle(baseline)
    r = middle(redesign)
    if b is None or r is None or b == 0:
        return f"| {label} | {show(b)} | {show(r)} | — |"

    change = (r - b) / b * 100.0
    if better == "lower":
        verdict = "better" if change < -1.0 else ("worse" if change > 1.0 else "same")
    else:
        verdict = "better" if change > 1.0 else ("worse" if change < -1.0 else "same")

    # A difference that lies inside the spread of the runs is not a
    # difference: the machine, not the application, decided it.
    if baseline and redesign:
        lowest = max(min(baseline), min(redesign))
        highest = min(max(baseline), max(redesign))
        if lowest <= highest:
            verdict = "inside the spread"
    return f"| {label} | {show(b)} | {show(r)} | {change:+.1f}% {verdict} |"


def main() -> int:
    if len(sys.argv) < 4:
        print(
            "usage: compare_perf.py <baseline.json|dir> <redesign.json|dir> <output.md>",
            file=sys.stderr,
        )
        return 2

    baseline = load_many(sys.argv[1])
    redesign = load_many(sys.argv[2])
    output = sys.argv[3]

    lines = [
        "# MRSAD — performance comparison",
        "",
        "Both sides were measured by the same benchmark, on the same emulator,",
        "driven by the same script, several times each. The frame numbers come",
        "from the platform's own `FrameMetrics`, and the CPU and memory numbers",
        "from the process itself.",
        "",
        f"- baseline: {len(baseline)} runs",
        f"- redesign: {len(redesign)} runs",
        "",
        "The number of each column is the middle of the runs. The verdict says",
        "whether the difference is larger than the spread of the runs; a",
        "difference *inside the spread* is the machine talking, not the",
        "application.",
        "",
    ]

    if not baseline or not redesign:
        missing = []
        if not baseline:
            missing.append("baseline")
        if not redesign:
            missing.append("redesign")
        lines.append(
            f"> The {', '.join(missing)} side produced no report; the comparison "
            f"is incomplete."
        )
        lines.append("")
    else:
        versions = sorted({report.get("version", "?") for report in baseline + redesign})
        lines.append(f"- versions: {', '.join(versions)}")
        lines.append("")

    lines += [
        "## Frames, whole session",
        "",
        "| metric | baseline | redesign | change |",
        "| --- | --- | --- | --- |",
    ]
    for key, label, better in (
        ("frames", "frames", "higher"),
        ("meanMs", "mean frame (ms)", "lower"),
        ("p50Ms", "p50 frame (ms)", "lower"),
        ("p90Ms", "p90 frame (ms)", "lower"),
        ("p95Ms", "p95 frame (ms)", "lower"),
        ("p99Ms", "p99 frame (ms)", "lower"),
        ("maxMs", "worst frame (ms)", "lower"),
        ("jankPercent", "jank (%)", "lower"),
    ):
        lines.append(
            row(label, values_of(baseline, "overall", key), values_of(redesign, "overall", key), better)
        )

    lines += [
        "",
        "## Frames, per phase",
        "",
        "| phase | metric | baseline | redesign | change |",
        "| --- | --- | --- | --- | --- |",
    ]
    for phase, title in (("idle", "idle (the object turns on its own)"),
                         ("scroll", "scroll"),
                         ("touch", "touch (the object under the finger)"),
                         ("navigate", "navigate")):
        for key, label in (("p50Ms", "p50 (ms)"), ("p95Ms", "p95 (ms)"), ("jankPercent", "jank (%)")):
            lines.append(
                row(f"{title} · {label}",
                    values_of(baseline, phase, key),
                    values_of(redesign, phase, key))
                .replace("| ", f"| {phase} | ", 1)
            )

    lines += [
        "",
        "## The process",
        "",
        "| metric | baseline | redesign | change |",
        "| --- | --- | --- | --- |",
    ]
    for key, label in (
        ("cpuMillis", "CPU time (ms)"),
        ("cpuPercent", "CPU load (%)"),
        ("pssEndKb", "PSS at the end (kB)"),
        ("pssDeltaKb", "PSS growth (kB)"),
        ("javaHeapUsedKb", "Java heap in use (kB)"),
        ("nativeHeapKb", "native heap (kB)"),
    ):
        lines.append(
            row(label, top_values(baseline, key), top_values(redesign, key))
        )

    lines += [
        "",
        "## The spread of the runs",
        "",
        "The lowest and the highest value of every run of each side. A",
        "difference that is smaller than this is not a difference.",
        "",
        "| metric | baseline | redesign |",
        "| --- | --- | --- |",
    ]
    for phase, key, label in (
        ("overall", "p50Ms", "p50 frame (ms)"),
        ("overall", "p95Ms", "p95 frame (ms)"),
        ("overall", "maxMs", "worst frame (ms)"),
    ):
        lines.append(
            f"| {label} | {span(values_of(baseline, phase, key))} "
            f"| {span(values_of(redesign, phase, key))} |"
        )
    for key, label in (("cpuPercent", "CPU load (%)"), ("pssEndKb", "PSS at the end (kB)")):
        lines.append(
            f"| {label} | {span(top_values(baseline, key))} "
            f"| {span(top_values(redesign, key))} |"
        )

    budgets = []
    for reports in (baseline, redesign):
        for report in reports:
            section = report.get("overall") or {}
            budget = section.get("budgetMs")
            if budget is not None:
                budgets.append(int(budget))
    lines += ["", "## The frame budget", ""]
    if not budgets:
        lines.append("The frame budget was not recorded, so the jank numbers cannot be compared.")
    elif len(set(budgets)) > 1:
        lines.append(
            f"The two sides were measured against different frame budgets "
            f"({', '.join(str(b) for b in sorted(set(budgets)))} ms); the jank "
            f"numbers are not comparable."
        )
    else:
        budget = budgets[0]
        lines.append(
            f"A frame longer than {budget} ms missed its deadline on the "
            f"{round(1000.0 / budget)} Hz display of the emulator and is counted "
            f"as jank."
        )
    lines.append("")

    with open(output, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines))

    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
