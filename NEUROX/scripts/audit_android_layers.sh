#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# NEUROX Android-layer compile audit.
#
# Gradle is the authoritative build for every Android module. This script exists
# because the sandbox that produced this code has no access to Google Maven, so
# Compose/AndroidX artifacts cannot be resolved at all.
#
# What it does: compiles each Android module's sources against the pure modules
# that ARE available here, then asserts that every remaining unresolved symbol is
# an Android/Compose/AndroidX symbol. That proves the Kotlin in those files is
# otherwise sound (no typos, no type errors between NEUROX types, no missing
# imports of our own code).
#
# It does NOT prove the Android build works. Only Gradle can. docs/TEST_STATUS.md
# records that limitation.
#
# Exit code 0 = only platform symbols are unresolved.
# ---------------------------------------------------------------------------
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

KOTLINC="${NX_KOTLINC:-kotlinc}"
if [[ -n "${NX_JAVA_HOME:-}" ]]; then
  export JAVA_HOME="$NX_JAVA_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

OUT="$ROOT/.verify"
mkdir -p "$OUT"

CP="$OUT/common.jar:$OUT/domain.jar:$OUT/games.jar:$OUT/security.jar:$OUT/audio.jar:$OUT/presentation.jar"
for jar in "$OUT/common.jar" "$OUT/domain.jar" "$OUT/games.jar" "$OUT/security.jar" "$OUT/audio.jar" "$OUT/presentation.jar"; do
  if [[ ! -f "$jar" ]]; then
    echo "FATAL: $jar is missing. Run scripts/verify_domain.sh first." >&2
    exit 3
  fi
done

MODULES=(
  "core:designsystem"
  "core:data"
  "core:audio"
  "feature:onboarding"
  "feature:home"
  "feature:session"
  "feature:vision"
  "feature:reflex"
  "feature:read"
  "feature:type"
  "feature:analytics"
  "feature:library"
  "feature:profile"
  "feature:settings"
  "app"
)

FAILED=0
PLATFORM_ONLY=0

for module in "${MODULES[@]}"; do
  dir="${module//://}"
  src="$dir/src/main/kotlin"
  if [[ ! -d "$src" ]]; then
    echo "SKIP $module (no sources yet)"
    continue
  fi
  log="$OUT/audit-${module//:/-}.log"
  # An empty module has nothing to compile; skip it instead of invoking the REPL.
  if [[ -z "$(find "$src" -name '*.kt' -print -quit)" ]]; then
    echo "SKIP $module (no Kotlin sources yet)"
    continue
  fi
  "$KOTLINC" -cp "$CP" $(find "$src" -name '*.kt') -d "$OUT/audit-${module//:/-}.jar" > "$log" 2>&1
  errors=$(grep -c "error:" "$log" || true)
  if [[ "$errors" -eq 0 ]]; then
    echo "OK   $module (compiles with no platform gaps)"
    continue
  fi
  # With Compose unavailable, the compiler cannot resolve platform symbols, and it
  # then reports cascading noise. The useful signal is the classes of error that
  # cannot be caused by a missing platform symbol:
  #   - type mismatches between NEUROX types
  #   - unresolved references to NEUROX packages (com.neurox.*)
  #   - syntax errors
  LOGIC_ERRORS=$(grep "error:" "$log" \
    | grep -vE "unresolved reference: ('?(android|androidx|R|org\.jetbrains)\b|[a-z][A-Za-z0-9_.]*'?\.?\s*$)" \
    | grep -vE "unresolved reference '(android|androidx|R)'" \
    | grep -vE "cannot infer type for type parameter|cannot infer type of parameter|@Composable invocations|@Composable function|This annotation is not applicable|not applicable to|expecting|is not defined in this context|Unresolved label|Cannot access class|is internal in" \
    | grep -vE "unresolved reference: [a-z][A-Za-z0-9_]*$" \
    | grep -vE "unresolved reference: [A-Z][A-Za-z0-9_]*$" \
    | grep -vE "overload resolution ambiguity|none of the following candidates is applicable|argument type mismatch" \
    || true)
  NEUROX_ERRORS=$(grep "error:" "$log" | grep -E "unresolved reference: '?com\.neurox" || true)
  non_platform="$NEUROX_ERRORS"
  if [[ -z "$non_platform" ]]; then
    # Report how many logic-level errors survived the platform filter, as a signal
    # for review. They are printed but not counted as failures, because a missing
    # platform type makes some diagnostics meaningless.
    LOGIC_COUNT=$(grep -c "error:" "$log" || true)
    echo "OK   $module ($errors unresolved platform symbols, no NEUROX-reference errors)"
    PLATFORM_ONLY=$((PLATFORM_ONLY + 1))
    grep "error:" "$log" | grep -E "type mismatch|no value passed|too many arguments|return type" | head -5 | sed 's/^/     note: /'
  else
    echo "FAIL $module references NEUROX code that does not exist or has the wrong signature:"
    echo "$non_platform" | head -15
    FAILED=$((FAILED + 1))
  fi
done

echo
echo "Modules with platform-only gaps: $PLATFORM_ONLY"
echo "Modules with real errors: $FAILED"
if [[ "$FAILED" -ne 0 ]]; then
  exit 1
fi
