#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# NEUROX offline verification (pure-JVM layers).
#
# Purpose: compile and execute the real NEUROX unit tests in an environment
# without Maven access. Gradle is the primary build system; this script is the
# secondary, dependency-free path used to prove logic inside the sandbox.
#
# Requirements:
#   NX_KOTLINC  path to kotlinc  (default: kotlinc on PATH)
#   NX_JAVA_HOME  JDK 17+        (default: java on PATH)
#
# Exit code 0 means every test passed.
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

KOTLINC="${NX_KOTLINC:-kotlinc}"
JAVA_HOME_DIR="${NX_JAVA_HOME:-}"
if [[ -n "$JAVA_HOME_DIR" ]]; then
  export JAVA_HOME="$JAVA_HOME_DIR"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
JAVA_BIN="${JAVA_HOME_DIR:+$JAVA_HOME_DIR/bin/}java"

OUT="$ROOT/.verify"
RUNNER="$OUT/runner-classes"
rm -rf "$OUT"
mkdir -p "$OUT" "$RUNNER"

if [[ -z "${NX_KOTLIN_STDLIB:-}" ]]; then
  KOTLINC_REAL="$(command -v "$KOTLINC")"
  EXEC_TARGET="$(sed -n "s/^exec \([^ ]*\).*/\1/p" "$(readlink -f "$KOTLINC_REAL")" | head -1)"
  if [[ -n "$EXEC_TARGET" && -f "$EXEC_TARGET" ]]; then
    export NX_KOTLIN_STDLIB="$(dirname "$EXEC_TARGET")/../lib/kotlin-stdlib.jar"
  else
    export NX_KOTLIN_STDLIB="$(dirname "$(readlink -f "$KOTLINC_REAL")")/../lib/kotlin-stdlib.jar"
  fi
fi
if [[ ! -f "$NX_KOTLIN_STDLIB" ]]; then
  echo "FATAL: kotlin-stdlib.jar not found. Set NX_KOTLIN_STDLIB." >&2
  exit 3
fi

kc() {
  # shellcheck disable=SC2046
  "$KOTLINC" "$@" 2>&1 | grep -v '^warning: unable to find kotlin-stdlib' || true
}

compile() {
  local name="$1"; shift
  local out="$1"; shift
  echo "== compile $name =="
  kc -cp "$CP" $(find "$@" -name '*.kt' 2>/dev/null) -d "$out"
  if [[ ! -f "$out" ]]; then
    echo "FATAL: compilation of $name failed" >&2
    exit 4
  fi
}

NX_COROUTINES_JAR="${NX_COROUTINES_JAR:-}"
if [[ -z "$NX_COROUTINES_JAR" ]]; then
  for candidate in \
    "/home/user/.local/nx-tools/kc/lib/kotlinx-coroutines-core-jvm.jar" \
    "$(dirname "$(command -v "$KOTLINC")")/../lib/kotlinx-coroutines-core-jvm.jar"; do
    if [[ -f "$candidate" ]]; then NX_COROUTINES_JAR="$candidate"; break; fi
  done
fi

CP=""
compile "core:common" "$OUT/common.jar" core/common/src/main/kotlin
CP="$OUT/common.jar${NX_COROUTINES_JAR:+:$NX_COROUTINES_JAR}"
compile "core:domain" "$OUT/domain.jar" core/domain/src/main/kotlin
CP="$OUT/common.jar:$OUT/domain.jar${NX_COROUTINES_JAR:+:$NX_COROUTINES_JAR}"
compile "core:games" "$OUT/games.jar" core/games/src/main/kotlin
CP="$OUT/common.jar:$OUT/domain.jar:$OUT/games.jar${NX_COROUTINES_JAR:+:$NX_COROUTINES_JAR}"
# core:audio is pure Kotlin. The Android AudioTrack renderer lives in the separate
# :core:audio-android Gradle module, so nothing has to be filtered out here.
find core/audio/src/main/kotlin -name '*.kt' > "$OUT/audio-sources.txt"
echo "== compile core:audio =="
kc -cp "$CP" $(cat "$OUT/audio-sources.txt") -d "$OUT/audio.jar"
if [[ ! -f "$OUT/audio.jar" ]]; then
  echo "FATAL: compilation of core:audio failed" >&2
  exit 4
fi
CP="$OUT/common.jar:$OUT/domain.jar:$OUT/games.jar:$OUT/audio.jar${NX_COROUTINES_JAR:+:$NX_COROUTINES_JAR}"
compile "core:presentation" "$OUT/presentation.jar" core/presentation/src/main/kotlin
CP="$OUT/common.jar:$OUT/domain.jar:$OUT/games.jar:$OUT/audio.jar:$OUT/presentation.jar${NX_COROUTINES_JAR:+:$NX_COROUTINES_JAR}"
# core:security has one Android-only file (Android Keystore). The pure crypto and
# vault logic is compiled for the JVM; the Keystore wrapper is verified by Gradle.
echo "== prepare core:security (pure part) =="
SEC_SRC="$OUT/security-src"
mkdir -p "$SEC_SRC"
cp -r core/security/src/main/kotlin/* "$SEC_SRC"/
rm -rf "$SEC_SRC/com/neurox/security/keystore"
compile "core:security" "$OUT/security.jar" "$SEC_SRC"
CP="$CP:$OUT/security.jar"
CP="$CP${NX_COROUTINES_JAR:+:$NX_COROUTINES_JAR}"
compile "tests" "$OUT/tests.jar" core/common/src/test/kotlin core/domain/src/test/kotlin core/games/src/test/kotlin core/security/src/test/kotlin core/audio/src/test/kotlin core/presentation/src/test/kotlin
CP="$CP:$OUT/tests.jar:$NX_KOTLIN_STDLIB${NX_COROUTINES_JAR:+:$NX_COROUTINES_JAR}"

echo "== compile test runner =="
kc -cp "$CP" scripts/verify/TestRunner.kt -d "$RUNNER"

echo "== run tests =="
set +e
"$JAVA_BIN" -cp "$RUNNER:$CP" com.neurox.verify.TestRunnerKt \
  "$OUT/tests.jar" "$OUT/presentation.jar" "$OUT/audio.jar" "$OUT/security.jar" "$OUT/games.jar" "$OUT/domain.jar" "$OUT/common.jar" > "$OUT/test-output.txt" 2>&1
STATUS=$?
set -e
tail -30 "$OUT/test-output.txt"
echo
echo "Full output: $OUT/test-output.txt"
exit $STATUS
