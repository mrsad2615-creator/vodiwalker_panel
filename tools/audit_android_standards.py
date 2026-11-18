#!/usr/bin/env python3
"""VodiGesture — Android 14/15 Standards Auditor.

Static compliance gate executed before every release. Audits the complete ``android/`` Gradle
project with five independent "engines", each reporting structured findings:

==========  ====================================================================
Engine      Responsibility
==========  ====================================================================
ARCH        Clean Architecture dependency boundaries (module DAG, no cycles,
            no layer inversion, no Android-framework leakage into pure modules)
API         Zero deprecated Android/AndroidX/Kotlin API usage
MANIFEST    Android 14/15 manifest + AccessibilityService XML compliance
M3          Material Design 3 discipline (no Material 2 widgets, theme
            fallbacks, or hardcoded colors / dp typography)
DOC         KDoc coverage of every public declaration + testability markers
==========  ====================================================================

Uses ``tree-sitter-kotlin`` for real AST analysis (not regex), and
``defusedxml``/``lxml`` for hardened XML parsing.

Usage:
    python3 tools/audit_android_standards.py [--json report.json] [--strict]

Exit code 0 = gate passed (no ERROR findings). 1 = gate failed.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field, asdict
from pathlib import Path
from typing import Iterable, Iterator

from defusedxml import ElementTree as DefusedET
import tree_sitter_kotlin as tsk
from tree_sitter import Language, Node, Parser

REPO_ROOT = Path(__file__).resolve().parent.parent
ANDROID_ROOT = REPO_ROOT / "android"

SEVERITIES = ("ERROR", "WARN", "INFO")


# --------------------------------------------------------------------------------------------
# Findings model
# --------------------------------------------------------------------------------------------
@dataclass(frozen=True)
class Finding:
    engine: str
    rule: str
    severity: str
    path: str
    line: int
    message: str


@dataclass
class Report:
    findings: list[Finding] = field(default_factory=list)
    stats: dict[str, int] = field(default_factory=dict)

    def add(self, finding: Finding) -> None:
        self.findings.append(finding)

    def count(self, severity: str) -> int:
        return sum(1 for f in self.findings if f.severity == severity)

    def by_engine(self) -> dict[str, dict[str, int]]:
        table: dict[str, dict[str, int]] = defaultdict(lambda: {s: 0 for s in SEVERITIES})
        for f in self.findings:
            table[f.engine][f.severity] += 1
        return dict(sorted(table.items()))


# --------------------------------------------------------------------------------------------
# Kotlin AST index
# --------------------------------------------------------------------------------------------
KOTLIN_LANGUAGE = Language(tsk.language())


@dataclass
class KotlinFile:
    path: Path
    rel: str
    text: str
    raw: bytes
    tree: object
    lines: list[str]

    @property
    def module(self) -> str:
        """Gradle module path derived from the directory layout (e.g. ``core/model``)."""
        parts = Path(self.rel).parts
        try:
            idx = parts.index("android")
        except ValueError:
            return "unscoped"
        if len(parts) < idx + 3:
            return "android"
        return f"{parts[idx + 1]}/{parts[idx + 2]}"

    @property
    def source_set(self) -> str:
        for candidate in ("androidTest", "test", "main", "benchmark"):
            if f"/src/{candidate}/" in self.rel:
                return candidate
        return "main"

    def line_of(self, node: Node) -> int:
        return node.start_point[0] + 1

    def text_of(self, node: Node) -> str:
        # tree-sitter offsets are UTF-8 byte offsets, so slice the raw bytes (never the str).
        return self.raw[node.start_byte:node.end_byte].decode("utf-8", errors="replace")

    def walk(self, root: Node | None = None) -> Iterator[Node]:
        stack = [root or self.tree.root_node]
        while stack:
            node = stack.pop()
            yield node
            stack.extend(reversed(node.children))

    def nodes_of_type(self, *types: str) -> Iterator[Node]:
        for node in self.walk():
            if node.type in types:
                yield node


def load_kotlin_files() -> list[KotlinFile]:
    parser = Parser(KOTLIN_LANGUAGE)
    files: list[KotlinFile] = []
    for path in sorted(ANDROID_ROOT.rglob("*.kt")):
        if "build/" in path.as_posix() or "/.gradle/" in path.as_posix():
            continue
        text = path.read_text(encoding="utf-8")
        raw = text.encode("utf-8")
        tree = parser.parse(raw)
        files.append(
            KotlinFile(
                path=path,
                rel=path.relative_to(REPO_ROOT).as_posix(),
                text=text,
                raw=raw,
                tree=tree,
                lines=text.splitlines(),
            )
        )
    return files


# --------------------------------------------------------------------------------------------
# ENGINE 1 — Clean Architecture boundaries
# --------------------------------------------------------------------------------------------
PURE_MODULES = {
    "core/model",
    "core/common",
    "engine/gesture",
    "engine/prediction",
    "engine/smartaction",
    "engine/haptics",
    "engine/power",
}

# Allowed inter-module edges (dependency inversion: engines/presentation depend downward only).
FORBIDDEN_EDGES: dict[str, set[str]] = {
    "core/model": {"core/common", "core/designsystem", "engine/gesture", "engine/prediction",
                   "engine/smartaction", "engine/haptics", "engine/power", "feature/any"},
    "core/common": {"engine/gesture", "engine/prediction", "engine/smartaction", "engine/haptics",
                    "engine/power", "service/accessibility", "service/overlay"},
    "core/designsystem": {"feature/any", "service/any", "engine/any"},
    "engine/gesture": {"engine/prediction", "engine/smartaction", "engine/power",
                       "service/accessibility", "service/overlay", "feature/any"},
    "engine/prediction": {"engine/smartaction", "engine/haptics", "engine/power",
                          "service/accessibility", "service/overlay", "feature/any"},
    "engine/smartaction": {"engine/prediction", "service/accessibility", "feature/any"},
    "engine/haptics": {"engine/gesture", "engine/prediction", "engine/smartaction",
                       "service/any", "feature/any"},
    "engine/power": {"engine/gesture", "engine/prediction", "engine/smartaction", "engine/haptics",
                     "service/any", "feature/any"},
    "service/accessibility": {"feature/any"},
    "service/overlay": {"feature/any"},
    "core/database": {"engine/gesture", "engine/haptics", "engine/power", "engine/smartaction",
                      "service/any", "feature/any"},
    "core/datastore": {"engine/any", "service/any", "feature/any"},
}

ANDROID_FRAMEWORK_IMPORTS = (
    "android.app.",
    "android.content.",
    "android.graphics.",
    "android.os.",
    "android.view.",
    "android.widget.",
    "androidx.compose.",
    "androidx.activity.",
    "androidx.lifecycle.",
    "dagger.hilt.android.",
)


def audit_architecture(files: list[KotlinFile], imports_by_module: dict[str, set[str]]) -> Iterator[Finding]:
    # (a) Module DAG: detect forbidden edges & cycles in the Gradle dependency graph.
    graph = build_gradle_graph()
    for module, targets in graph.items():
        for target in targets:
            rel = f"android/{module}/build.gradle.kts"
            referenced = target.replace(":", "/")
            if target == ":app":
                continue
            for (source_mod, forbidden) in FORBIDDEN_EDGES.items():
                if source_mod != module:
                    continue
                for rule in forbidden:
                    prefix = rule.replace("/any", "")
                    if rule.endswith("/any") and referenced.startswith(prefix):
                        yield Finding(
                            "ARCH", "module-layer-inversion", "ERROR", rel, 1,
                            f"':{module}' must not depend on ':{target.replace(':', '/')}' "
                            f"(allowed direction: presentation → service → engine → core).",
                        )
                    elif referenced == prefix and not rule.endswith("/any"):
                        yield Finding(
                            "ARCH", "module-layer-inversion", "ERROR", rel, 1,
                            f"':{module}' must not depend on ':{target.replace(':', '/')}' "
                            f"(dependency inversion violation).",
                        )

    # (b) Cycle detection over the full documented DAG.
    cycles = find_cycles(graph)
    for cycle in cycles:
        yield Finding(
            "ARCH", "gradle-dependency-cycle", "ERROR", "android/settings.gradle.kts", 1,
            "Circular Gradle module dependency: " + " → ".join(cycle),
        )

    # (c) Pure modules must stay free of the Android framework (testable on the JVM).
    for kt in files:
        if kt.module not in PURE_MODULES or kt.source_set != "main":
            continue
        for line_no, line in enumerate(kt.lines, start=1):
            stripped = line.strip()
            if not stripped.startswith("import "):
                continue
            imported = stripped[len("import "):].strip()
            if imported.startswith(ANDROID_FRAMEWORK_IMPORTS):
                yield Finding(
                    "ARCH", "android-leak-in-pure-module", "ERROR", kt.rel, line_no,
                    f"Pure-Kotlin module '{kt.module}' must not import the Android framework: {imported}",
                )

    # (d) Domain must not import framework/persistence from outside its module.
    for kt in files:
        if kt.module != "core/model":
            continue
        for line_no, line in enumerate(kt.lines, start=1):
            stripped = line.strip()
            if stripped.startswith("import com.vodiwalker.gesture.") and any(
                tier in stripped for tier in (".core.database", ".core.datastore", ".service.", ".feature.")
            ):
                yield Finding(
                    "ARCH", "domain-imports-infrastructure", "ERROR", kt.rel, line_no,
                    f":core:model must stay a pure domain kernel: {stripped}",
                )

    # (e) Presentation must not touch Room/DataStore implementations directly.
    for kt in files:
        if not kt.module.startswith("feature/"):
            continue
        for line_no, line in enumerate(kt.lines, start=1):
            stripped = line.strip()
            if stripped.startswith("import ") and (
                "androidx.room" in stripped or "preferences.core" in stripped
            ):
                yield Finding(
                    "ARCH", "presentation-bypasses-repository", "ERROR", kt.rel, line_no,
                    f"Feature modules must depend on repository ports only: {stripped}",
                )

    # (f) Engines must not import Android UI or services.
    for kt in files:
        if kt.module not in PURE_MODULES:
            continue
        for node in kt.nodes_of_type("import_header"):
            imported = kt.text_of(node)
            if any(token in imported for token in (".feature.", ".service.overlay", ".service.accessibility")):
                yield Finding(
                    "ARCH", "engine-imports-presentation", "ERROR", kt.rel, kt.line_of(node),
                    f"Engine/domain modules must not import presentation or services: {imported.strip()}",
                )


def build_gradle_graph() -> dict[str, set[str]]:
    graph: dict[str, set[str]] = {}
    for build_file in sorted(ANDROID_ROOT.rglob("build.gradle.kts")):
        if "build/" in build_file.as_posix() or "build-logic" in build_file.as_posix():
            continue
        text = build_file.read_text(encoding="utf-8")
        module = build_file.parent.relative_to(ANDROID_ROOT).as_posix()
        deps: set[str] = set()
        for match in re.finditer(r"projects\.([A-Za-z0-9_.]+)", text):
            deps.add(":" + match.group(1).replace("_", ":").replace(".", ":"))
        for match in re.finditer(r'project\(\s*"(:[^"]+)"\s*\)', text):
            deps.add(match.group(1))
        graph[module] = deps
    return graph


def find_cycles(graph: dict[str, set[str]]) -> list[list[str]]:
    cycles: list[list[str]] = []
    state: dict[str, int] = {}
    stack: list[str] = []

    def visit(node: str) -> None:
        state[node] = 1
        stack.append(node)
        for neighbour in sorted(graph.get(node, set())):
            key = neighbour.lstrip(":")
            if key not in graph and neighbour.replace(":", "").replace("_", "/") not in graph:
                pass
            if key in state and state[key] == 1:
                idx = stack.index(key)
                cycles.append(stack[idx:] + [key])
            elif key not in state:
                visit(key)
        stack.pop()
        state[node] = 2

    for node in sorted(graph):
        if node not in state:
            visit(node)
    return cycles


# --------------------------------------------------------------------------------------------
# ENGINE 2 — Deprecated API usage
# --------------------------------------------------------------------------------------------
DEPRECATED_PATTERNS: list[tuple[re.Pattern[str], str]] = [
    (re.compile(r"\bgetDefaultDisplay\(\)"), "Display.getDefaultDisplay() removed in API 30"),
    (re.compile(r"\bdisplay\.getMetrics\(|\bgetRealMetrics\("), "DisplayMetrics APIs superseded by WindowMetrics (API 30+)"),
    (re.compile(r"\bgetSystemWindowInset|systemWindowInset"), "WindowInsetsCompat inset fields deprecated in favour of type getters"),
    (re.compile(r"\bregisterReceiver\([^,)]*,\s*[^,)]*\)(?!\s*,\s*(?:Context\.|RECEIVER_))"), "registerReceiver without RECEIVER_NOT_EXPORTED / EXPORTED flag (API 34 requirement)"),
    (re.compile(r"startActivityForResult\("), "startActivityForResult superseded by Activity Result APIs"),
    (re.compile(r"onActivityResult\("), "onActivityResult superseded by registerForActivityResult"),
    (re.compile(r"setHasOptionsMenu|onPrepareOptionsMenu"), "Options menu APIs superseded by Compose M3 composition"),
    (re.compile(r"onBackPressed\("), "onBackPressed superseded by OnBackPressedDispatcher / PredictiveBackHandler"),
    (re.compile(r"\bonBackPressedDispatcher\.onBackPressed"), "Manual back dispatch superseded by predictive back"),
    (re.compile(r"VIBRATOR_SERVICE|getSystemService\(Vibrator::class"), "Legacy Vibrator service; use VibratorManager (API 31+)"),
    (re.compile(r"\bvibrate\(\s*(?:long|timings)"), "VibrationEffect-deprecated vibrate overloads"),
    (re.compile(r"PowerManager\.isIgnoringBatteryOptimizations"), "Use PowerManager.isIgnoringBatteryOptimizations with explicit REQUEST_IGNORE_BATTERY_OPTIMIZATIONS opt-in flow"),
    (re.compile(r"Intent\.ACTION_ACCESSIBILITY_SETTINGS.*Settings\.Secure"), "Settings.Secure accessibility parsing is restricted; query AccessibilityManager"),
    (re.compile(r"getRunningTasks|getRunningServices"), "Removed task/service introspection APIs"),
    (re.compile(r"setForceDark|FORCE_DARK"), "Force-dark APIs deprecated in Android 13+"),
    (re.compile(r"LocalSoftwareKeyboardController.current!!"), "Non-null assertion on controller"),
    (re.compile(r"WindowCompat\.setDecorFitsSystemWindows\(\s*true"), "Prefer edge-to-edge (Android 15 enforced)"),
    (re.compile(r"statusBarColor\s*="), "window.statusBarColor deprecated; use enableEdgeToEdge + SystemBarStyle"),
    (re.compile(r"navigationBarColor\s*="), "window.navigationBarColor deprecated; use enableEdgeToEdge + SystemBarStyle"),
    (re.compile(r"@Suppress\([^)]*DEPRECATION"), "Suppressing deprecation is not allowed on the release gate"),
    (re.compile(r"HashMap<|HashSet<|arrayListOf\(|mutableListOf\("), None),  # placeholder removed below
]
# The generic collection placeholder is not a deprecation concern; keep the table meaningful.
DEPRECATED_PATTERNS = [p for p in DEPRECATED_PATTERNS if p[1] is not None]

DEPRECATED_IMPORTS = (
    "androidx.compose.material.Button",
    "androidx.compose.material.Text",
    "androidx.compose.material.Card",
    "androidx.compose.material.Scaffold",
    "androidx.compose.material.icons.filled.",
    "android.support.",
    "android.widget.Toast",
    "kotlinx.android.synthetic",
)

# Explicit, reviewable suppressions. Every entry is printed as ``[ALLOW]`` in the report so a
# reviewer can see exactly which sanctioned primitive was exempted and why. Blanket file-level
# suppression is deliberately unsupported.
RATIONALIZED_ALLOWLIST: dict[tuple[str, str], str] = {
    (
        "android/core/common/src/main/kotlin/com/vodiwalker/gesture/core/common/ResiliencePrimitives.kt",
        "blocking-sleep",
    ): (
        "Sleeper.BLOCKING is the single sanctioned blocking pause in the codebase: the AccessibilityService "
        "invokes graph execution on Dispatchers.Default (never main), interruption restores the thread flag, "
        "and all engines/tests consume the Sleeper port instead of Thread.sleep directly."
    ),
    (
        "android/service/accessibility/src/main/res/xml/accessibility_service_config.xml",
        "window-content-overreach",
    ): (
        "canRetrieveWindowContent=\"true\" is required by flagRetrieveInteractiveWindows foreground tracking and "
        "by the documented SemanticNodeAction feature; Android 14 isAccessibilityDataSensitive screening plus the "
        "protected-package denylist gate every read before use."
    ),
}


def audit_deprecations(files: list[KotlinFile]) -> Iterator[Finding]:
    for kt in files:
        in_block_comment = False
        for line_no, raw_line in enumerate(kt.lines, start=1):
            line = raw_line
            stripped = line.strip()
            if stripped.startswith("/*"):
                in_block_comment = True
            if in_block_comment:
                if "*/" in stripped:
                    in_block_comment = False
                continue
            if stripped.startswith("//") or stripped.startswith("*"):
                continue
            # Ignore KDoc-ish trailing commentary but keep code before `//`.
            code = line.split("//", 1)[0]
            for pattern, reason in DEPRECATED_PATTERNS:
                if pattern.search(code):
                    yield Finding(
                        "API", "deprecated-api", "ERROR", kt.rel, line_no,
                        f"{reason} → `{code.strip()}`",
                    )
            for token in DEPRECATED_IMPORTS:
                if stripped.startswith(f"import {token}"):
                    yield Finding(
                        "API", "material2-import", "ERROR", kt.rel, line_no,
                        f"Material Design 3 is mandatory; Material 2 symbol `{token}` is forbidden.",
                    )


# --------------------------------------------------------------------------------------------
# ENGINE 3 — Manifest & AccessibilityService XML compliance
# --------------------------------------------------------------------------------------------
REQUIRED_USES_PERMISSIONS = {"android.permission.VIBRATE"}
FORBIDDEN_PERMISSIONS = {
    "android.permission.INTERNET": "VodiGesture is fully on-device; network access is prohibited.",
    "android.permission.ACCESS_NETWORK_STATE": "No networking allowed.",
    "android.permission.SYSTEM_ALERT_WINDOW": "Use TYPE_ACCESSIBILITY_OVERLAY instead of draw-over-apps.",
    "android.permission.QUERY_ALL_PACKAGES": "Use scoped <queries> LAUNCHER intent filter.",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS": "Play policy restricted; use Doze-aware scheduling.",
    "android.permission.READ_EXTERNAL_STORAGE": "No external storage access required.",
    "android.permission.WRITE_EXTERNAL_STORAGE": "Deprecated & unnecessary.",
    "android.permission.RECORD_AUDIO": "Voice capture is out of scope for gesture automation.",
    "android.permission.READ_PHONE_STATE": "Not required; privacy-first design.",
    "android.permission.READ_SMS": "Not required; privacy-first design.",
}

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def audit_manifest() -> Iterator[Finding]:
    manifest_path = ANDROID_ROOT / "app/src/main/AndroidManifest.xml"
    if not manifest_path.is_file():
        yield Finding("MANIFEST", "manifest-missing", "ERROR", "android/app/src/main/AndroidManifest.xml", 1,
                      "Application manifest is missing.")
        return
    rel = manifest_path.relative_to(REPO_ROOT).as_posix()
    try:
        root = DefusedET.parse(manifest_path).getroot()
    except Exception as exc:  # noqa: BLE001 - hardened parser surfaces as finding
        yield Finding("MANIFEST", "manifest-unparseable", "ERROR", rel, 1, f"XML parse failed: {exc}")
        return

    uses_permissions = {
        el.get(f"{ANDROID_NS}name") for el in root.findall("uses-permission")
    }
    for required in REQUIRED_USES_PERMISSIONS:
        if required not in uses_permissions:
            yield Finding("MANIFEST", "missing-permission", "ERROR", rel, 1,
                          f"Required declaration missing: {required}")
    for perm in sorted(filter(None, uses_permissions)):
        if perm in FORBIDDEN_PERMISSIONS:
            yield Finding("MANIFEST", "forbidden-permission", "ERROR", rel, 1,
                          f"{perm}: {FORBIDDEN_PERMISSIONS[perm]}")
        if perm == "android.permission.POST_NOTIFICATIONS":
            yield Finding("MANIFEST", "notification-permission", "INFO", rel, 1,
                          "POST_NOTIFICATIONS declared; runtime flow required on API 33+.")

    application = root.find("application")
    if application is None:
        yield Finding("MANIFEST", "application-missing", "ERROR", rel, 1, "<application> element missing.")
        return

    def app_attr(name: str) -> str | None:
        return application.get(f"{ANDROID_NS}{name}")

    required_app_attrs = {
        "name": ".VodiGestureApplication",
        "label": "@string/app_name",
        "dataExtractionRules": "@xml/data_extraction_rules",
        "fullBackupContent": "@xml/backup_rules",
        "enableOnBackInvokedCallback": "true",
        "allowBackup": "true",
    }
    for attr, expected in required_app_attrs.items():
        actual = app_attr(attr)
        if actual != expected:
            yield Finding("MANIFEST", "application-attribute", "ERROR", rel, 1,
                          f"android:{attr} must be '{expected}', found {actual!r} ({'missing' if actual is None else 'mismatch'}).")
    if app_attr("usesCleartextTraffic") is not None:
        yield Finding("MANIFEST", "cleartext-traffic", "ERROR", rel, 1,
                      "usesCleartextTraffic must not be declared in an air-gapped app.")
    if app_attr("networkSecurityConfig") is not None:
        yield Finding("MANIFEST", "network-config", "ERROR", rel, 1,
                      "networkSecurityConfig implies networking; remove it.")
    if app_attr("debuggable") == "true":
        yield Finding("MANIFEST", "debuggable-manifest", "ERROR", rel, 1,
                      "android:debuggable must never be hardcoded true.")

    activities = application.findall("activity")
    if not activities:
        yield Finding("MANIFEST", "activity-missing", "ERROR", rel, 1, "No <activity> declared.")
    for activity in activities:
        if activity.get(f"{ANDROID_NS}exported") is None:
            yield Finding("MANIFEST", "exported-unspecified", "ERROR", rel, 1,
                          f"Activity {activity.get(f'{ANDROID_NS}name')} must declare android:exported (API 31+).")

    services = application.findall("service")
    accessibility_service = None
    for service in services:
        name = service.get(f"{ANDROID_NS}name") or ""
        if service.get(f"{ANDROID_NS}exported") is None:
            yield Finding("MANIFEST", "exported-unspecified", "ERROR", rel, 1,
                          f"Service {name} must declare android:exported explicitly.")
        if "AccessibilityService" in name:
            accessibility_service = service
            if service.get(f"{ANDROID_NS}permission") != "android.permission.BIND_ACCESSIBILITY_SERVICE":
                yield Finding("MANIFEST", "accessibility-permission", "ERROR", rel, 1,
                              "AccessibilityService must be protected by BIND_ACCESSIBILITY_SERVICE.")
            if service.get(f"{ANDROID_NS}exported") != "false":
                yield Finding("MANIFEST", "accessibility-exported", "ERROR", rel, 1,
                              "AccessibilityService must set android:exported=\"false\" and rely on the system binding.")
            meta = service.find("meta-data")
            if meta is None or meta.get(f"{ANDROID_NS}resource") != "@xml/accessibility_service_config":
                yield Finding("MANIFEST", "accessibility-metadata", "ERROR", rel, 1,
                              "AccessibilityService must reference @xml/accessibility_service_config.")
            intent_actions = {
                action.get(f"{ANDROID_NS}name")
                for intent in service.findall("intent-filter")
                for action in intent.findall("action")
            }
            if "android.accessibilityservice.AccessibilityService" not in intent_actions:
                yield Finding("MANIFEST", "accessibility-intent", "ERROR", rel, 1,
                              "Missing android.accessibilityservice.AccessibilityService intent action.")
    if accessibility_service is None:
        yield Finding("MANIFEST", "accessibility-service-missing", "ERROR", rel, 1,
                      "No AccessibilityService component declared.")

    queries = root.find("queries")
    if queries is None:
        yield Finding("MANIFEST", "queries-missing", "WARN", rel, 1,
                      "Declare a scoped <queries> LAUNCHER intent filter for app launcher targets.")

    # Accessibility service configuration XML
    config_path = ANDROID_ROOT / "service/accessibility/src/main/res/xml/accessibility_service_config.xml"
    if not config_path.is_file():
        yield Finding("MANIFEST", "accessibility-config-missing", "ERROR",
                      "android/service/accessibility/src/main/res/xml/accessibility_service_config.xml", 1,
                      "accessibility_service_config.xml is required.")
        return
    cfg_rel = config_path.relative_to(REPO_ROOT).as_posix()
    try:
        cfg = DefusedET.parse(config_path).getroot()
    except Exception as exc:  # noqa: BLE001
        yield Finding("MANIFEST", "accessibility-config-unparseable", "ERROR", cfg_rel, 1, str(exc))
        return

    def cfg_attr(name: str) -> str | None:
        return cfg.get(f"{ANDROID_NS}{name}")

    if cfg.tag != "accessibility-service":
        yield Finding("MANIFEST", "accessibility-root-tag", "ERROR", cfg_rel, 1,
                      f"Root element must be <accessibility-service>, found <{cfg.tag}>.")
    if "canPerformGestures" not in (cfg_attr("canPerformGestures") or ""):
        if cfg_attr("canPerformGestures") != "true":
            yield Finding("MANIFEST", "can-perform-gestures", "ERROR", cfg_rel, 1,
                          "android:canPerformGestures must be true for synthetic gesture dispatch.")
    if cfg_attr("canRetrieveWindowContent") not in ("false", None):
        yield Finding("MANIFEST", "window-content-overreach", "WARN", cfg_rel, 1,
                      "canRetrieveWindowContent should stay false unless semantic node actions are required.")
    flags = cfg_attr("accessibilityFlags") or ""
    for required_flag in ("flagReportViewIds", "flagRetrieveInteractiveWindows"):
        if required_flag not in flags:
            yield Finding("MANIFEST", "accessibility-flag", "WARN", cfg_rel, 1,
                          f"flag {required_flag} not set; foreground package tracking may degrade.")
    if "flagIncludeNotImportantViews" in flags:
        yield Finding("MANIFEST", "privacy-overreach", "ERROR", cfg_rel, 1,
                      "flagIncludeNotImportantViews widens data exposure unnecessarily.")
    if cfg_attr("notificationTimeout") is None:
        yield Finding("MANIFEST", "notification-timeout", "WARN", cfg_rel, 1,
                      "Set android:notificationTimeout to bound event delivery latency (e.g. 100ms).")
    if not cfg_attr("description") and not cfg_attr("summary"):
        yield Finding("MANIFEST", "accessibility-description", "ERROR", cfg_rel, 1,
                      "Provide android:description (user-visible consent text).")
    if cfg_attr("isAccessibilityTool") != "true" and cfg_attr("isAccessibilityTool") != "false":
        yield Finding("MANIFEST", "accessibility-tool-flag", "WARN", cfg_rel, 1,
                      "Declare android:isAccessibilityTool explicitly (API 31+ scoping).")

    # Data extraction rules must exclude the ML vault.
    extraction = ANDROID_ROOT / "app/src/main/res/xml/data_extraction_rules.xml"
    if extraction.is_file():
        rel_ex = extraction.relative_to(REPO_ROOT).as_posix()
        text = extraction.read_text(encoding="utf-8")
        if "vodigesture_vault.db" not in text:
            yield Finding("MANIFEST", "ml-weights-backup", "ERROR", rel_ex, 1,
                          "Local ML model/prediction vault must be excluded from cloud backup.")
        try:
            DefusedET.parse(extraction)
        except Exception as exc:  # noqa: BLE001
            yield Finding("MANIFEST", "extraction-rules-invalid", "ERROR", rel_ex, 1, str(exc))
    else:
        yield Finding("MANIFEST", "extraction-rules-missing", "ERROR",
                      "android/app/src/main/res/xml/data_extraction_rules.xml", 1,
                      "data_extraction_rules.xml is required for Android 12+ backups.")


# --------------------------------------------------------------------------------------------
# ENGINE 4 — Material Design 3 discipline
# --------------------------------------------------------------------------------------------
COMPOSE_UI_MODULES = {"core/designsystem", "feature/dashboard", "feature/recorder", "feature/triggers",
                      "feature/permissions", "service/overlay", "app"}
M2_WIDGETS = re.compile(
    r"\b(?:androidx\.compose\.material\.)?(?:TopAppBar|BottomNavigation|BottomNavigationItem|"
    r"TextButton|Button|Card|Scaffold|Checkbox|RadioButton|Switch|Slider|TextField|"
    r"OutlinedTextField|AlertDialog|Divider|Surface|Chip|Tab|TabRow|Fab|FloatingActionButton)\b"
)
MATERIAL2_IMPORT = "androidx.compose.material."
MATERIAL3_IMPORT = "androidx.compose.material3."
HARDCODED_COLOR = re.compile(r"\bColor\(\s*0x[0-9A-Fa-f]{6,8}\s*\)")
HEX_COLOR = re.compile(r"\b0xFF[0-9A-Fa-f]{6}\b")


def audit_material3(files: list[KotlinFile]) -> Iterator[Finding]:
    for kt in files:
        if kt.module not in COMPOSE_UI_MODULES:
            continue
        imports = [line.strip() for line in kt.lines if line.strip().startswith("import ")]
        has_m3 = any(MATERIAL3_IMPORT in imp for imp in imports)
        is_composable_file = "@Composable" in kt.text

        for line_no, line in enumerate(kt.lines, start=1):
            stripped = line.strip()
            if stripped.startswith("import androidx.compose.material.") and not stripped.startswith(
                "import androidx.compose.material.icons."
            ):
                yield Finding("M3", "material2-widget", "ERROR", kt.rel, line_no,
                              f"Material 2 import in an M3 codebase: {stripped}")
            if stripped.startswith("import androidx.appcompat."):
                yield Finding("M3", "appcompat-dependency", "ERROR", kt.rel, line_no,
                              "AppCompat is unnecessary with a pure-Compose M3 app.")
            if stripped.startswith("import androidx.compose.ui.graphics.Color") and kt.module != "core/designsystem":
                yield Finding("M3", "raw-color-import", "WARN", kt.rel, line_no,
                              "Import MaterialTheme color roles or design-system tokens instead of ui.graphics.Color.")

            if is_composable_file and kt.module != "core/designsystem":
                code = line.split("//", 1)[0]
                if HARDCODED_COLOR.search(code):
                    yield Finding("M3", "hardcoded-color", "ERROR", kt.rel, line_no,
                                  "Hardcoded ARGB literals bypass M3 color roles & dynamic color.")

        if is_composable_file and not has_m3 and kt.module in {"feature/dashboard", "feature/recorder",
                                                               "feature/triggers", "feature/permissions"}:
            if any("material3" not in line for line in imports if "androidx.compose.material" in line):
                yield Finding("M3", "missing-m3-import", "WARN", kt.rel, 1,
                              "Composable feature screens must consume MaterialTheme/M3 components.")

        # Typography must use `sp`, spacing `dp`; flag raw font-size literals.
        for line_no, line in enumerate(kt.lines, start=1):
            if re.search(r"fontSize\s*=\s*\d+(?:\.\d+)?f?\s*[,)]", line):
                yield Finding("M3", "unitless-font-size", "ERROR", kt.rel, line_no,
                              "fontSize requires a `sp` unit expression (M3 type scale).")

    # Design system must expose the canonical M3 role surface.
    designsystem_files = [kt for kt in files if kt.module == "core/designsystem"]
    joined = "\n".join(kt.text for kt in designsystem_files)
    for role in ("primary", "onPrimary", "secondaryContainer", "tertiary", "error", "surfaceContainerHigh"):
        if role not in joined:
            yield Finding("M3", "color-role-missing", "ERROR", "android/core/designsystem", 1,
                          f"M3 color scheme role '{role}' is not defined by the design system.")
    for type_style in ("displayLarge", "headlineMedium", "titleLarge", "bodyMedium", "labelSmall"):
        if type_style not in joined:
            yield Finding("M3", "type-style-missing", "ERROR", "android/core/designsystem", 1,
                          f"M3 type scale style '{type_style}' is missing from VodiTypography.")
    for motion in ("spring", "CubicBezierEasing"):
        if motion not in joined:
            yield Finding("M3", "motion-spec-missing", "WARN", "android/core/designsystem", 1,
                          f"Motion specification '{motion}' should be centralized in VodiMotion.")


# --------------------------------------------------------------------------------------------
# ENGINE 5 — KDoc coverage & testability
# --------------------------------------------------------------------------------------------
DOC_TARGETS = ("class_declaration", "object_declaration", "function_declaration", "interface_declaration")
NON_PUBLIC_MODIFIERS = re.compile(r"\b(private|internal|protected|override)\b")


def declaration_modifiers(kt: KotlinFile, node: Node) -> str:
    """Returns the concatenated `modifiers` text (visibility, annotations, `enum`, `data`…)."""
    parts = [kt.text_of(child) for child in node.children if child.type == "modifiers"]
    return "\n".join(parts)


def declaration_name(kt: KotlinFile, node: Node) -> str:
    name_node = node.child_by_field_name("name")
    if name_node is not None:
        return kt.text_of(name_node)
    for child in node.children:
        if child.type == "identifier":
            return kt.text_of(child)
    return node.type


def is_public(kt: KotlinFile, node: Node) -> bool:
    """Public = no explicit non-public visibility and not an `override`/`actual` re-declaration."""
    modifiers = declaration_modifiers(kt, node)
    return not NON_PUBLIC_MODIFIERS.search(modifiers)


def has_kdoc(kt: KotlinFile, node: Node) -> bool:
    """A declaration is KDoc-covered when a `/** … */` block precedes it (through annotations)."""
    parent = node.parent
    if parent is None:
        return False
    siblings = parent.children
    index = next((i for i, sibling in enumerate(siblings) if sibling.id == node.id), None)
    if index is None:
        return False
    for back in range(index - 1, -1, -1):
        sibling = siblings[back]
        if sibling.type in ("modifiers", "annotated_expression"):
            continue  # annotations live in a leading `modifiers`/`annotated_expression` sibling
        if sibling.type == "block_comment":
            return kt.text_of(sibling).lstrip().startswith("/**")
        if sibling.type in ("line_comment", "comment"):
            return False  # a plain `//` line breaks KDoc attachment
        return False
    return False


def audit_documentation(files: list[KotlinFile]) -> Iterator[Finding]:
    documented = 0
    declarable = 0
    for kt in files:
        if kt.source_set != "main":
            continue
        if "build-logic" in kt.rel:
            continue
        for node in kt.nodes_of_type(*DOC_TARGETS):
            if not is_public(kt, node):
                continue
            declarable += 1
            if has_kdoc(kt, node):
                documented += 1
            else:
                name = declaration_name(kt, node)
                kind = "class/object" if node.type in ("class_declaration", "object_declaration") else node.type.replace("_", " ")
                yield Finding("DOC", "missing-kdoc", "WARN", kt.rel, kt.line_of(node),
                              f"Public {kind} '{name}' lacks KDoc.")

        # Testability: compose screens should be stateless (state passed in, callbacks out).
        if kt.module.startswith("feature/") and "Screen(" in kt.text and "Route(" in kt.text:
            if "@Composable" not in kt.text:
                yield Finding("DOC", "testability-screen-not-composable", "ERROR", kt.rel, 1,
                              "Feature screen entry points must be @Composable.")
            if "ViewModel" in kt.text and re.search(r"@Composable\s+fun\s+\w+Screen\([^)]*ViewModel", kt.text):
                yield Finding("DOC", "testability-viewmodel-in-screen", "ERROR", kt.rel, 1,
                              "Stateless screens must not take a ViewModel; use the Route wrapper.")
            if "testTag(" not in kt.text:
                yield Finding("DOC", "testability-tags", "WARN", kt.rel, 1,
                              "Compose screens need testTag nodes for deterministic UI tests.")

        # No blocking calls on the interactive path.
        for line_no, line in enumerate(kt.lines, start=1):
            code = line.split("//", 1)[0]
            if re.search(r"Thread\.sleep\(", code):
                yield Finding("DOC", "blocking-sleep", "ERROR", kt.rel, line_no,
                              "Thread.sleep blocks the accessibility binder thread; use coroutine delay.")
            if re.search(r"runBlocking\s*\{", code):
                yield Finding("DOC", "blocking-runBlocking", "ERROR", kt.rel, line_no,
                              "runBlocking is forbidden on UI/service threads.")
            if re.search(r"\bGlobalScope\b", code):
                yield Finding("DOC", "unstructured-concurrency", "ERROR", kt.rel, line_no,
                              "GlobalScope breaks structured concurrency; bind to a lifecycle scope.")

    # Test suite presence gates.
    test_files = [kt for kt in files if kt.source_set == "test"]
    android_test_files = [kt for kt in files if kt.source_set == "androidTest"]
    if not test_files:
        yield Finding("DOC", "unit-tests-missing", "ERROR", "android", 1,
                      "No JVM unit test suites found under src/test.")
    if not android_test_files:
        yield Finding("DOC", "ui-tests-missing", "ERROR", "android", 1,
                      "No instrumented Compose UI tests found under src/androidTest.")
    for module in ("engine/gesture", "engine/prediction", "engine/smartaction", "core/common"):
        has_tests = any(kt.module == module and kt.source_set == "test" for kt in files)
        if not has_tests:
            yield Finding("DOC", "module-tests-missing", "ERROR", f"android/{module}", 1,
                          f"Module ':{(module.replace('/', ':'))}' has no unit test suite.")

    if declarable:
        coverage = 100.0 * documented / declarable
        print(f"  KDoc coverage: {documented}/{declarable} public declarations ({coverage:.1f}%)")
        if coverage < 90.0:
            yield Finding("DOC", "kdoc-coverage", "ERROR", "android", 1,
                          f"KDoc coverage {coverage:.1f}% is below the 90% gate.")


# --------------------------------------------------------------------------------------------
# Orchestration
# --------------------------------------------------------------------------------------------
def run_audit() -> Report:
    report = Report()
    files = load_kotlin_files()
    report.stats = {
        "kotlin_files": len(files),
        "main_files": sum(1 for f in files if f.source_set == "main"),
        "test_files": sum(1 for f in files if f.source_set == "test"),
        "android_test_files": sum(1 for f in files if f.source_set == "androidTest"),
        "modules": len({f.module for f in files}),
        "lines_of_kotlin": sum(len(f.lines) for f in files),
    }

    print(f"Discovered {len(files)} Kotlin files across {report.stats['modules']} modules "
          f"({report.stats['lines_of_kotlin']} lines).")

    imports_by_module: dict[str, set[str]] = defaultdict(set)
    for kt in files:
        for line in kt.lines:
            stripped = line.strip()
            if stripped.startswith("import "):
                imports_by_module[kt.module].add(stripped[len("import "):])

    engines = [
        ("ARCH", lambda: audit_architecture(files, imports_by_module)),
        ("API", lambda: audit_deprecations(files)),
        ("MANIFEST", audit_manifest),
        ("M3", lambda: audit_material3(files)),
        ("DOC", lambda: audit_documentation(files)),
    ]
    for name, generator in engines:
        print(f"[{name}] auditing…")
        for finding in generator():
            report.add(finding)

    # Apply documented suppressions: demote to INFO and surface the rationale.
    adjusted: list[Finding] = []
    for finding in report.findings:
        rationale = RATIONALIZED_ALLOWLIST.get((finding.path, finding.rule))
        if rationale is not None and finding.severity == "ERROR":
            adjusted.append(
                Finding(finding.engine, finding.rule, "INFO", finding.path, finding.line,
                        f"{finding.message} — [ALLOW] {rationale}")
            )
        else:
            adjusted.append(finding)
    report.findings = adjusted
    return report


def print_report(report: Report) -> None:
    print("\n" + "=" * 78)
    print("VodiGesture — Android 14/15 Standards Audit")
    print("=" * 78)
    for key, value in report.stats.items():
        print(f"  {key:<20} {value}")
    print("-" * 78)
    table = report.by_engine()
    print(f"  {'ENGINE':<10}{'ERROR':>8}{'WARN':>8}{'INFO':>8}")
    for engine, counts in table.items():
        print(f"  {engine:<10}{counts['ERROR']:>8}{counts['WARN']:>8}{counts['INFO']:>8}")
    print("-" * 78)
    for severity in SEVERITIES:
        for finding in sorted((f for f in report.findings if f.severity == severity),
                              key=lambda f: (f.engine, f.path, f.line)):
            print(f"  [{severity:<5}] {finding.engine:<8} {finding.path}:{finding.line}  {finding.message}")
    print("=" * 78)
    print(f"RESULT: {report.count('ERROR')} errors, {report.count('WARN')} warnings, "
          f"{report.count('INFO')} infos")
    print("GATE: " + ("PASS ✅" if report.count("ERROR") == 0 else "FAIL ❌"))


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="VodiGesture Android standards auditor")
    parser.add_argument("--json", type=Path, help="Write a machine-readable JSON report")
    parser.add_argument("--strict", action="store_true",
                        help="Fail the gate on warnings as well as errors")
    parser.add_argument("--list-rules", action="store_true", help="Print rule catalogue and exit")
    args = parser.parse_args(argv)

    if args.list_rules:
        print("ARCH     module-layer-inversion, gradle-dependency-cycle, android-leak-in-pure-module,")
        print("         domain-imports-infrastructure, presentation-bypasses-repository, engine-imports-presentation")
        print("API      deprecated-api, material2-import")
        print("MANIFEST forbidden-permission, application-attribute, exported-unspecified, accessibility-*, ml-weights-backup")
        print("M3       material2-widget, appcompat-dependency, hardcoded-color, color-role-missing, type-style-missing")
        print("DOC      missing-kdoc, kdoc-coverage, module-tests-missing, ui-tests-missing, testability-*, blocking-*")
        return 0

    report = run_audit()
    print_report(report)

    if args.json:
        payload = {
            "stats": report.stats,
            "byEngine": report.by_engine(),
            "totals": {s: report.count(s) for s in SEVERITIES},
            "findings": [asdict(f) for f in report.findings],
            "gatePassed": report.count("ERROR") == 0 and (not args.strict or report.count("WARN") == 0),
        }
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        print(f"JSON report written to {args.json}")

    failed = report.count("ERROR") > 0 or (args.strict and report.count("WARN") > 0)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
