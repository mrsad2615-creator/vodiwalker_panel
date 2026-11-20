#!/usr/bin/env bash
#
# Runs the performance benchmark on a connected device and extracts the
# report the benchmark itself produced.
#
# The benchmark prints a single line that starts with `MRSAD_PERF_JSON:`;
# the instrumentation runner captures it in the JUnit XML, so the report
# can be recovered without any device tooling.
#
# Usage: run-perf-benchmark.sh <gradle-project-dir> <output-file>

set -uo pipefail

GRADLE_DIR="${1:?usage: run-perf-benchmark.sh <gradle-project-dir> <output-file>}"
OUTPUT="${2:?usage: run-perf-benchmark.sh <gradle-project-dir> <output-file>}"

cd "$GRADLE_DIR"

STATUS=0
./gradlew --console=plain --no-daemon :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.mrsad.app.PerformanceBenchmarkTest \
  || STATUS=$?

echo "the benchmark run finished with status $STATUS"

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

printf '%s\n' "$REPORT" > "$OUTPUT"
echo "the performance report was written to $OUTPUT"
cat "$OUTPUT"
