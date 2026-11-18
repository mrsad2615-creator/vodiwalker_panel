#!/usr/bin/env bash
# ---------------------------------------------------------------------------------------------
# VodiGesture — full local verification gate.
#
# Runs every check that is reproducible without an Android SDK / Google Maven access:
#   1. M3 HCT token + WCAG generation        (tools/generate_m3_tokens.mjs)
#   2. Android 14/15 standards audit         (tools/audit_android_standards.py)
#   3. Kotlin K2 unit/concurrency/chaos suite (tools/run_kotlin_tests.sh)
#   4. Workbench TypeScript strict typecheck  (preview-workbench)
#   5. Workbench Vitest suite                 (preview-workbench)
#
# Exits non-zero on the first failing gate so CI can consume it directly.
# ---------------------------------------------------------------------------------------------
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

step() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }

step "1/5  M3 HCT tokens + WCAG contrast audit"
/opt/android-toolchain/node_modules/.bin/tsx tools/generate_m3_tokens.mjs

step "2/5  Android 14/15 standards audit (ARCH · API · MANIFEST · M3 · DOC)"
python3 tools/audit_android_standards.py --json android/docs/standards_audit.json

step "3/5  Kotlin K2 engine suite (-Werror, JVM 21)"
./tools/run_kotlin_tests.sh

step "4/5  Workbench TypeScript strict typecheck"
(cd preview-workbench && npx tsc --noEmit)

step "5/5  Workbench Vitest suite"
(cd preview-workbench && npx vitest run)

printf '\n\033[1;32mALL GATES PASSED\033[0m\n'
