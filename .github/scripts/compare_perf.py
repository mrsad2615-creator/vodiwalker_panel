#!/usr/bin/env python3
"""Compares two performance reports and writes a markdown summary.

Both reports come from the same benchmark, on the same emulator, driven
by the same script, so the numbers are directly comparable. The script
never invents a number: a missing report is reported as missing.
"""

from __future__ import annotations

import json
import os
import sys


def load(path: str):
    if not os.path.exists(path):
        return None
    try:
        with open(path, "r", encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, ValueError) as error:
        print(f"could not read {path}: {error}", file=sys.stderr)
        return None


def cell(report, phase: str, key: str):
    if not report:
        return "—"
    section = report.get(phase)
    if not section:
        return "—"
    value = section.get(key)
    if value is None:
        return "—"
    return str(value)


def row(label: str, baseline, redesign, better: str = "lower") -> str:
    if baseline == "—" or redesign == "—":
        return f"| {label} | {baseline} | {redesign} | — |"
    try:
        b = float(baseline)
        r = float(redesign)
    except ValueError:
        return f"| {label} | {baseline} | {redesign} | — |"
    if b == 0:
        return f"| {label} | {baseline} | {redesign} | — |"
    change = (r - b) / b * 100.0
    if better == "lower":
        verdict = "better" if change < -1.0 else ("worse" if change > 1.0 else "same")
    else:
        verdict = "better" if change > 1.0 else ("worse" if change < -1.0 else "same")
    return f"| {label} | {baseline} | {redesign} | {change:+.1f}% {verdict} |"


def main() -> int:
    if len(sys.argv) < 4:
        print("usage: compare_perf.py <baseline.json> <redesign.json> <output.md>", file=sys.stderr)
        return 2

    baseline = load(sys.argv[1])
    redesign = load(sys.argv[2])
    output = sys.argv[3]

    lines = [
        "# MRSAD — performance comparison",
        "",
        "Both rows were measured by the same benchmark, on the same emulator,",
        "driven by the same script. The frame numbers come from the platform's",
        "own `FrameMetrics`, and the CPU and memory numbers from the process",
        "itself.",
        "",
    ]

    if baseline is None or redesign is None:
        lines.append("> One of the two runs did not produce a report; the comparison is incomplete.")
        lines.append("")
    else:
        lines.append(f"- baseline: `{baseline.get('version', '?')}` · cpu {baseline.get('cpuPercent', '?')}% · pss {baseline.get('pssEndKb', '?')} kB")
        lines.append(f"- redesign: `{redesign.get('version', '?')}` · cpu {redesign.get('cpuPercent', '?')}% · pss {redesign.get('pssEndKb', '?')} kB")
        lines.append("")

    lines += [
        "## Frames, whole session",
        "",
        "| metric | baseline | redesign | change |",
        "| --- | --- | --- | --- |",
        row("frames", cell(baseline, "overall", "frames"), cell(redesign, "overall", "frames"), "higher"),
        row("mean frame (ms)", cell(baseline, "overall", "meanMs"), cell(redesign, "overall", "meanMs")),
        row("p50 frame (ms)", cell(baseline, "overall", "p50Ms"), cell(redesign, "overall", "p50Ms")),
        row("p90 frame (ms)", cell(baseline, "overall", "p90Ms"), cell(redesign, "overall", "p90Ms")),
        row("p95 frame (ms)", cell(baseline, "overall", "p95Ms"), cell(redesign, "overall", "p95Ms")),
        row("p99 frame (ms)", cell(baseline, "overall", "p99Ms"), cell(redesign, "overall", "p99Ms")),
        row("worst frame (ms)", cell(baseline, "overall", "maxMs"), cell(redesign, "overall", "maxMs")),
        row("jank (%)", cell(baseline, "overall", "jankPercent"), cell(redesign, "overall", "jankPercent")),
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
                    cell(baseline, phase, key),
                    cell(redesign, phase, key))
                .replace("| ", f"| {phase} | ", 1)
            )

    lines += [
        "",
        "## The process",
        "",
        "| metric | baseline | redesign | change |",
        "| --- | --- | --- | --- |",
        row("CPU time (ms)", str(baseline.get("cpuMillis", "—")) if baseline else "—",
            str(redesign.get("cpuMillis", "—")) if redesign else "—"),
        row("CPU load (%)", str(baseline.get("cpuPercent", "—")) if baseline else "—",
            str(redesign.get("cpuPercent", "—")) if redesign else "—"),
        row("PSS at the end (kB)", str(baseline.get("pssEndKb", "—")) if baseline else "—",
            str(redesign.get("pssEndKb", "—")) if redesign else "—"),
        row("native heap (kB)", str(baseline.get("javaHeapUsedKb", "—")) if baseline else "—",
            str(redesign.get("javaHeapUsedKb", "—")) if redesign else "—"),
        "",
        "A frame longer than 16.67 ms missed its deadline on a 60 Hz display and",
        "is counted as jank.",
        "",
    ]

    with open(output, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines))

    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
