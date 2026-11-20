#!/usr/bin/env bash
#
# Builds the app sources plus the desktop harness and runs the whole test suite.
# Exits non-zero when a test fails.
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

JDK="${JAVA_HOME:-/tmp/jdk/jdk4py/java-runtime}"
KOTLINC="${KOTLINC:-/tmp/kotlinc/bin/kotlinc}"
STDLIB="${KOTLIN_STDLIB:-/tmp/kotlinc/lib/kotlin-stdlib.jar}"
OUT="${TEST_OUT:-$ROOT/build/tests/classes}"

export JAVA_HOME="$JDK"
export PATH="$JAVA_HOME/bin:$PATH"

rm -rf "$OUT"
mkdir -p "$OUT"

# The Android-only sources need android.jar and are covered by the interaction tests through the
# platform-independent core, so they are excluded here.
SOURCES=$(find app/src/main/java -name "*.kt" | grep -v "/android/" || true)

"$KOTLINC" $SOURCES \
  tools/harness/src/com/mrsadhr/preview/*.kt \
  tools/harness/src/com/mrsadhr/test/*.kt \
  -d "$OUT" \
  -nowarn

java -Djava.awt.headless=true -cp "$OUT:$STDLIB" com.mrsadhr.test.RunTests

# Device-shaped stress pass: every screen, theme, language and mode at extreme viewports, font
# scales and inset combinations, plus a long run of frames. A layout fault that only shows up on a
# phone (a zero-sized viewport, a split-screen window, a huge font) fails here instead of shipping.
echo "==> device-shaped stress pass"
java -Djava.awt.headless=true -cp "$OUT:$STDLIB" com.mrsadhr.preview.StageStress app/src/main/assets/fonts
