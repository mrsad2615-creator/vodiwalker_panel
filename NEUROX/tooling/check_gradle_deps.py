#!/usr/bin/env python3
"""Fail if a module imports a NEUROX package it never declares as a dependency.

WHY: the first real Gradle build failed four separate times on this one class of
mistake - core:data used KSP without applying the plugin, core:designsystem used
com.neurox.presentation without depending on it, :app and :feature:* had a cycle,
and the app had no manifest at all. Each was invisible because nothing checked
that `import` and `build.gradle.kts` agreed.

The Kotlin compile cannot see it either: compiling a module against a big
classpath (as the offline gate does) resolves the import no matter which module
declares what. Only Gradle's per-module classpath - or this script - notices.

Exit code 0 means every import is backed by a declared dependency.
"""
from __future__ import annotations

import pathlib
import re
import sys

MODULE_PREFIX = {
    "com.neurox.common": ":core:common",
    "com.neurox.domain": ":core:domain",
    "com.neurox.games": ":core:games",
    "com.neurox.audio.platform": ":core:audio-android",
    "com.neurox.audio": ":core:audio",
    "com.neurox.security": ":core:security",
    "com.neurox.data": ":core:data",
    "com.neurox.designsystem": ":core:designsystem",
    "com.neurox.presentation": ":core:presentation",
}

IMPORT = re.compile(r"^import (com\.neurox\.[a-z0-9_.]+)", re.MULTILINE)


def owner(imported: str) -> str | None:
    """Longest matching package prefix wins, so platform/ beats its parent."""
    best, best_len = None, -1
    for prefix, module in MODULE_PREFIX.items():
        if (imported == prefix or imported.startswith(prefix + ".")) and len(prefix) > best_len:
            best, best_len = module, len(prefix)
    return best


def main() -> int:
    root = pathlib.Path(__file__).resolve().parent.parent
    features = {f.name for f in (root / "feature").iterdir() if f.is_dir()}
    for name in features:
        MODULE_PREFIX[f"com.neurox.feature.{name}"] = f":feature:{name}"

    build_files = sorted(
        list((root / "core").glob("*/build.gradle.kts"))
        + list((root / "feature").glob("*/build.gradle.kts"))
        + [root / "app/build.gradle.kts"]
    )

    problems: list[str] = []
    checked = 0
    for build_file in build_files:
        module_dir = build_file.parent
        sources = module_dir / "src" / "main" / "kotlin"
        if not sources.is_dir():
            continue
        checked += 1
        self_module = ":" + str(module_dir.relative_to(root)).replace("/", ":")
        declarations = build_file.read_text()

        used: set[str] = set()
        first_use: dict[str, str] = {}
        for kt in sources.rglob("*.kt"):
            text = kt.read_text()
            for imported in IMPORT.findall(text):
                module = owner(imported)
                if module and module != self_module:
                    used.add(module)
                    first_use.setdefault(module, imported)

        for module in sorted(used):
            if f'project("{module}")' not in declarations:
                problems.append(
                    f"{self_module}: uses {first_use[module]} but does not declare "
                    f'implementation(project("{module}"))'
                )

    print(f"checked {checked} modules against {len(MODULE_PREFIX)} packages")
    if problems:
        print()
        for p in problems:
            print(f"  DECLARE  {p}")
        print()
        print(f"RESULT: {len(problems)} undeclared module dependenc(ies).")
        return 1
    print("RESULT: every NEUROX import is backed by a declared dependency.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
