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

# The emulator action runs its script with a POSIX shell, so the script
# starts itself again under bash, which is what the rest of it expects.
if [ -z "${BASH_VERSION:-}" ]; then
  exec bash "$0" "$@"
fi

set -uo pipefail

GRADLE_DIR="${1:?usage: run-perf-benchmark.sh <gradle-project-dir> <output-file> [screenshot-dir]}"
OUTPUT="${2:?usage: run-perf-benchmark.sh <gradle-project-dir> <output-file> [screenshot-dir]}"
SCREENSHOT_DIR="${3:-}"

cd "$GRADLE_DIR" || exit 1
# The absolute path, so the log can be found from anywhere.
GRADLE_DIR="$(pwd)"

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
  # What the device holds, so a missing screenshot can be told apart from
  # a screenshot that was never taken.
  DEVICE_LISTING="$GRADLE_DIR/device-screenshots.txt"
  : > "$DEVICE_LISTING"
  for package in dev.mrsad.app.debug dev.mrsad.app; do
    if adb shell "ls /sdcard/Android/data/$package/files/" >/dev/null 2>&1; then
      echo "== /sdcard/Android/data/$package/files/ ==" >> "$DEVICE_LISTING"
      adb shell "ls -la /sdcard/Android/data/$package/files/" >> "$DEVICE_LISTING" 2>&1
      # The whole directory at once, and then every picture by name: some
      # versions of adb refuse a path that ends in "/.".
      adb pull "/sdcard/Android/data/$package/files/." "$SCREENSHOT_DIR" || true
      adb shell "ls /sdcard/Android/data/$package/files/" 2>/dev/null \
        | tr -d '\r' \
        | while read -r name; do
            case "$name" in
              *.png)
                adb pull "/sdcard/Android/data/$package/files/$name" "$SCREENSHOT_DIR" || true
                ;;
            esac
          done
      break
    else
      echo "== /sdcard/Android/data/$package/files/ is not there ==" >> "$DEVICE_LISTING"
    fi
  done

  {
    echo "the device holds:"
    cat "$DEVICE_LISTING"
    echo
    echo "the collected screenshots:"
    ls -la "$SCREENSHOT_DIR" || true
  } > "$GRADLE_DIR/screenshot-collection.txt"

  echo "the screenshots were collected into $SCREENSHOT_DIR"
  ls -la "$SCREENSHOT_DIR" || true
fi
