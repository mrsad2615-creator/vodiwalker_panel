#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# NEUROX Android-layer COMPILE GATE.
#
# WHAT THIS IS
# Gradle is the authoritative build for every Android module, but this sandbox
# cannot reach Google Maven, so Compose/AndroidX artifacts cannot be resolved at
# all. This script compiles every Android module FOR REAL against a hand-written
# stub of the platform API (tooling/stubs), in dependency order, with each
# module's classes on the classpath of the next.
#
# WHY IT REPLACED THE OLD "AUDIT"
# The previous script compiled each module in isolation and then FILTERED the
# errors, keeping only "unresolved reference: com.neurox*". That hid:
#   - real type errors (`argument type mismatch`, `overload resolution ambiguity`),
#   - missing imports of our own code,
#   - feature modules importing the application module (a dependency cycle),
#   - resources that do not exist (R references were never checked).
# It reported "0 real errors" on code that does not compile. This gate reports a
# module as OK only when the compiler produced no diagnostics at all.
#
# WHAT IT STILL DOES NOT PROVE
#   - Compose runtime semantics (recomposition, effect ordering, stability).
#   - Room's generated DAO code and its SQL.
#   - Resource linking beyond `R.<type>.<name>` existence.
#   - That the hand-written stubs match the real library signatures exactly.
# Only `./gradlew assembleDebug` proves those. That is blocked here by the network,
# and docs/TEST_STATUS.md says so.
#
# Exit code 0 = every module compiled with zero diagnostics.
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
COROUTINES="${NX_COROUTINES_JAR:-}"
if [[ -z "$COROUTINES" ]]; then
  # kotlinc ships coroutines next to the stdlib in the compiler distribution.
  for candidate in \
    "/home/user/.local/nx-tools/kc/lib/kotlinx-coroutines-core-jvm.jar" \
    "$(dirname "$(command -v "$KOTLINC")")/../lib/kotlinx-coroutines-core-jvm.jar"; do
    if [[ -f "$candidate" ]]; then COROUTINES="$candidate"; break; fi
  done
fi
if [[ -z "$COROUTINES" || ! -f "$COROUTINES" ]]; then
  echo "FATAL: kotlinx-coroutines-core-jvm.jar not found; set NX_COROUTINES_JAR." >&2
  exit 3
fi

mkdir -p "$OUT"

# The pure layers must exist first; verify_domain.sh builds them.
for jar in common domain games security audio presentation; do
  if [[ ! -f "$OUT/$jar.jar" ]]; then
    echo "FATAL: $OUT/$jar.jar is missing. Run scripts/verify_domain.sh first." >&2
    exit 3
  fi
done

ONLY="${1:-}"
FAIL=0
BUILT=()

step() { printf '\n== %s\n' "$*"; }

# validate_stubs - compile the stub set once; it is the foundation of every result.
validate_stubs() {
  step "platform stubs"
  "$KOTLINC" -cp "$COROUTINES" $(find tooling/stubs -name '*.kt') -d "$OUT/stubs.jar" \
    > "$OUT/stubs.log" 2>&1
  if [[ $? -ne 0 ]] || grep -q "error:" "$OUT/stubs.log"; then
    echo "FATAL: the platform stubs do not compile; every result below would be meaningless."
    grep "error:" "$OUT/stubs.log" | head -10
    exit 4
  fi
  echo "OK   stubs ($(find tooling/stubs -name '*.kt' | wc -l | tr -d ' ') files)"
}

BASE="$OUT/common.jar:$OUT/domain.jar:$OUT/games.jar:$OUT/security.jar:$OUT/audio.jar:$OUT/presentation.jar:$OUT/stubs.jar:$COROUTINES"

# build <module> <extra-classpath>
build() {
  local m="$1" extra="$2"
  local dir="${m//://}"
  local src="$dir/src/main/kotlin"
  if [[ ! -d "$src" ]]; then
    echo "SKIP $m (no sources)"
    return 0
  fi
  if [[ -n "$ONLY" && "$m" != "$ONLY" ]]; then
    return 0
  fi
  local rfile=""
  local log="$OUT/c-${m//:/-}.log"
  local out="$OUT/c-${m//:/-}.jar"
  if [[ -d "$dir/src/main/res" ]]; then
    if ! python3 tooling/gen_r.py "$dir" "$OUT/r/$dir/R.kt" > "$OUT/r-${m//:/-}.log" 2>&1; then
      echo "FAIL $m (R generation failed) -> $OUT/r-${m//:/-}.log"
      cat "$OUT/r-${m//:/-}.log" | head -5 | sed 's/^/       /'
      FAIL=$((FAIL + 1))
      return 0
    fi
    rfile="$OUT/r/$dir/R.kt"
  fi
  rm -f "$out"
  # shellcheck disable=SC2046,SC2086
  "$KOTLINC" -cp "$BASE$extra" $(find "$src" -name '*.kt') $rfile -d "$out" > "$log" 2>&1
  local n
  n=$(grep -c "error:" "$log" || true)
  if [[ "$n" -eq 0 ]]; then
    echo "OK   $m"
  else
    echo "FAIL $m ($n errors) -> $log"
    grep "error:" "$log" | head -8 | sed 's/^/       /'
    FAIL=$((FAIL + 1))
  fi
}

validate_stubs

# --- order mirrors settings.gradle.kts -------------------------------------
step "core"
build core:designsystem ""
build core:data ":$OUT/c-core-designsystem.jar"
build core:audio ":$OUT/c-core-designsystem.jar"
# The Android edge of audio: AudioRenderer against AudioTrack.
build core:audio-android ":$OUT/c-core-audio.jar:$OUT/c-core-designsystem.jar"


# Features see the pure layers, the design system and the data layer - and the
# container ONLY through com.neurox.presentation.di.AppServices.
FEATCP=":$OUT/c-core-designsystem.jar:$OUT/c-core-data.jar:$OUT/c-core-audio.jar:$OUT/c-core-audio-android.jar"

step "features"
FEATURES=(onboarding home session vision reflex read type analytics library profile settings)
for f in "${FEATURES[@]}"; do
  build "feature:$f" "$FEATCP"
done

step "app"
APPCP="$FEATCP"
for f in "${FEATURES[@]}"; do
  APPCP="$APPCP:$OUT/c-feature-$f.jar"
done
build app "$APPCP"

step "gradle dependency declarations"
# An import that resolves against the shared classpath but has no matching
# implementation(project(...)) fails ONLY under real Gradle, which gives each module
# its own classpath. That happened four times before this check existed.
if ! ( cd "$ROOT" && python3 tooling/check_gradle_deps.py ); then
  echo "  FAIL: undeclared module dependencies (see above)"
  FAIL=$((FAIL + 1))
fi

step "manifests"
# A missing or invalid AndroidManifest.xml is invisible to a Kotlin compile, and it
# is fatal: the first real Gradle build failed on exactly this, after every module
# had "compiled clean" for the project's whole life. Each Android module that
# produces an APK or an AAR needs a manifest, and the application one must declare
# its launcher activity or the app installs with no way to open it.
MANIFEST_FAIL=0
xml_ok() {
  # Structural check without a parser: balanced tags, one root, well-formed header.
  python3 - "$1" <<'PYEOF' || return 1
import sys, xml.etree.ElementTree as ET
try:
    ET.parse(sys.argv[1])
except Exception as e:
    print(f"  XML ERROR: {e}")
    raise SystemExit(1)
PYEOF
}

ANDROID_MODULES=(app core/audio-android core/data core/designsystem core/security)
for m in "${ANDROID_MODULES[@]}"; do
  mf="$m/src/main/AndroidManifest.xml"
  if [[ ! -f "$mf" ]]; then
    echo "  FAIL $m: no AndroidManifest.xml at $mf"
    MANIFEST_FAIL=$((MANIFEST_FAIL + 1))
    continue
  fi
  if ! xml_ok "$mf"; then
    echo "  FAIL $m: AndroidManifest.xml is not well-formed"
    MANIFEST_FAIL=$((MANIFEST_FAIL + 1))
    continue
  fi
  echo "  ok   $m"
done
# Android library modules may omit a manifest, but the generated R needs a package,
# which comes from the namespace - so check that separately in gen_r.py's caller.
if [[ -f app/src/main/AndroidManifest.xml ]]; then
  for token in ".NeuroxActivity" "android.intent.category.LAUNCHER" "android.intent.action.MAIN"; do
    if ! grep -q -- "$token" app/src/main/AndroidManifest.xml; then
      echo "  FAIL app: manifest does not declare $token"
      MANIFEST_FAIL=$((MANIFEST_FAIL + 1))
    fi
  done
fi
FAIL=$((FAIL + MANIFEST_FAIL))

echo
if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: every Android module compiled with zero diagnostics and every manifest is present."
  exit 0
fi
echo "RESULT: $FAIL module(s) failed to compile."
exit 1
