#!/usr/bin/env bash
#
# Runs the performance benchmark on a connected device and collects what it
# produced: the report the benchmark printed and the screenshots it took.
#
# The benchmark prints a single line that starts with `MRSAD_PERF_JSON:`;
# the instrumentation runner captures it in the JUnit XML, so the report
# can be recovered without any device tooling. The screenshots are written
# to the application's own external storage and are pulled with `adb`.
#
# Usage: run-perf-benchmark.sh <gradle-project-dir> <output-file> [screenshot-dir]

set -uo pipefail

GRADLE_DIR="${1:?usage: run-perf-benchmark.sh <gradle-project-dir> <output-file> [screenshot-dir]}"
OUTPUT="${2:?usage: run-perf-benchmark.sh <gradle-project-dir> <output-file> [screenshot-dir]}"
SCREENSHOT_DIR="${3:-}"

cd "$GRADLE_DIR"

# Everything the run prints is kept in a log next to the project, so a
# failing run can be reported without downloading anything.
LOG="$GRADLE_DIR/perf-benchmark.log"

STATUS=0
./gradlew --console=plain --no-daemon :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.mrsad.app.PerformanceBenchmarkTest \
  2>&1 | tee "$LOG"
STATUS=${PIPESTATUS[0]}

echo "the benchmark run finished with status $STATUS" | tee -a "$LOG"

REPORT=""
for directory in app/build/outputs/androidTest-results app/build/reports/androidTests; do
  if [ -d "$directory" ]; then
    REPORT=$(grep -rho 'MRSAD_PERF_JSON:{[^<]*}' "$directory" 2>/dev/null | tail -1 | sed 's/^MRSAD_PERF_JSON://')
    if [ -n "$REPORT" ]; then
      break
    fi
  fi
done

if [ -z "$REPORT" ]; then
  echo "no performance report was produced by the benchmark" >&2
  exit 1
fi

printf '%s\n' "$REPORT" >> "$LOG"
printf '%s\n' "$REPORT" > "$OUTPUT"
echo "the performance report was written to $OUTPUT"
cat "$OUTPUT"

# The screenshots are the visual evidence of the measurement.
if [ -n "$SCREENSHOT_DIR" ]; then
  mkdir -p "$SCREENSHOT_DIR"
  for package in dev.mrsad.app.debug dev.mrsad.app; do
    if adb shell "ls /sdcard/Android/data/$package/files/" >/dev/null 2>&1; then
      adb pull "/sdcard/Android/data/$package/files/." "$SCREENSHOT_DIR" || true
      break
    fi
  done
  echo "the screenshots were collected into $SCREENSHOT_DIR"
  ls -la "$SCREENSHOT_DIR" || true
fi
