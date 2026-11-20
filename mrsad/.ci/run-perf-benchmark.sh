#!/usr/bin/env bash
#
# Runs the performance benchmark on a connected device and collects what it
# produced: the report the benchmark printed and the screenshots it took.
#
# The application is installed and driven by hand instead of through the
# Gradle task for connected tests: that task removes the application when
# it is done, and with it the pictures the benchmark took. Here the
# application stays on the device, so the evidence can be read afterwards.
#
# The benchmark writes its report into the application's own storage and
# prints it with a marker, so the report can be recovered without any
# device tooling; the pictures are pulled from the same place.
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

APP_ID="dev.mrsad.app.debug"
TEST_ID="dev.mrsad.app.debug.test"
INSTRUMENTATION="androidx.test.runner.AndroidJUnitRunner"

# ---- 1. Build ---------------------------------------------------------------
: > "$LOG"
./gradlew --console=plain --no-daemon \
  :app:assembleDebug :app:assembleDebugAndroidTest 2>&1 | tee -a "$LOG"
STATUS=${PIPESTATUS[0]}
echo "the build finished with status $STATUS" | tee -a "$LOG"
if [ "$STATUS" -ne 0 ]; then
  exit "$STATUS"
fi

# ---- 2. The shell becomes root ----------------------------------------------
# The emulator is a userdebug image, so the shell can become root: that is
# the way into the private storage of the application, where the benchmark
# wrote its report and its pictures.
adb root >/dev/null 2>&1 || true
adb wait-for-device >/dev/null 2>&1 || true

# ---- 3. Install and run -----------------------------------------------------
adb install -r -t app/build/outputs/apk/debug/app-debug.apk 2>&1 | tee -a "$LOG"
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk 2>&1 | tee -a "$LOG"

adb shell am instrument -w \
  -e class dev.mrsad.app.PerformanceBenchmarkTest \
  "$TEST_ID/$INSTRUMENTATION" 2>&1 | tee -a "$LOG"
STATUS=${PIPESTATUS[0]}
echo "the benchmark run finished with status $STATUS" | tee -a "$LOG"

# ---- 4. The report ----------------------------------------------------------
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
  # The report is printed on a line of its own, so the rest of the line
  # is the report.
  REPORT=$(grep -o 'MRSAD_PERF_JSON:.*' "$LOG" 2>/dev/null | tail -1 | sed 's/^MRSAD_PERF_JSON://')
fi

# The application writes its report into its own storage as well. If the
# output of the run was lost, that file is the second way to the number.
if [ -z "$REPORT" ]; then
  echo "the report was not found in the output, looking on the device"
  for directory in "/data/user/0/$APP_ID/files" "/data/data/$APP_ID/files"; do
    if adb shell "cat $directory/mrsad-perf.json" > "$GRADLE_DIR/device-report.json" 2>/dev/null; then
      if [ -s "$GRADLE_DIR/device-report.json" ]; then
        REPORT=$(cat "$GRADLE_DIR/device-report.json")
        break
      fi
    fi
  done
  if [ -z "$REPORT" ]; then
    rm -f "$GRADLE_DIR/device-report.json"
    echo "no performance report was produced by the benchmark" >&2
    exit 1
  fi
fi

printf '%s\n' "$REPORT" >> "$LOG"
printf '%s\n' "$REPORT" > "$OUTPUT"
echo "the performance report was written to $OUTPUT"
cat "$OUTPUT"

# ---- 5. The screenshots -----------------------------------------------------
# The pictures are the visual evidence of the measurement.
if [ -n "$SCREENSHOT_DIR" ]; then
  mkdir -p "$SCREENSHOT_DIR"
  DEVICE_LISTING="$GRADLE_DIR/device-screenshots.txt"
  : > "$DEVICE_LISTING"
  echo "== who am I ==" >> "$DEVICE_LISTING"
  adb shell id >> "$DEVICE_LISTING" 2>&1

  for package in "$APP_ID" dev.mrsad.app; do
    for directory in "/data/user/0/$package/files" "/data/data/$package/files"; do
      if adb shell "ls $directory" >/dev/null 2>&1; then
        echo "== $directory ==" >> "$DEVICE_LISTING"
        adb shell "ls -la $directory" >> "$DEVICE_LISTING" 2>&1
        # The whole directory at once, and then every picture by name:
        # some versions of adb refuse a path that ends in "/.".
        adb pull "$directory/." "$SCREENSHOT_DIR" >/dev/null 2>&1 || true
        adb shell "ls $directory" 2>/dev/null \
          | tr -d '\r' \
          | while read -r name; do
              case "$name" in
                *.png)
                  adb pull "$directory/$name" "$SCREENSHOT_DIR" >/dev/null 2>&1 || true
                  ;;
              esac
            done
        break
      else
        echo "== $directory is not there ==" >> "$DEVICE_LISTING"
      fi
    done
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
