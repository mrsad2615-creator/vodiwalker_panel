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

# The emulator is a userdebug image, so the shell can become root: that
# is the way into the private storage of the application, where the
# benchmark wrote its report and its pictures.
adb root >/dev/null 2>&1 || true
adb wait-for-device >/dev/null 2>&1 || true

REPORT=""
for directory in app/build/outputs/androidTest-results app/build/reports/androidTests; do
  if [ -d "$directory" ]; then
    REPORT=$(grep -rho 'MRSAD_PERF_JSON:{[^<]*}' "$directory" 2>/dev/null | tail -1 | sed 's/^MRSAD_PERF_JSON://')
    if [ -n "$REPORT" ]; then
      break
    fi
  fi
done

# The application writes its report into its own storage as well. If the
# test XML was lost, that file is the second way to the same number.
pull_report() {
  for package in dev.mrsad.app.debug dev.mrsad.app; do
    for directory in "/data/user/0/$package/files" "/data/data/$package/files"; do
      if adb shell "cat $directory/mrsad-perf.json" > "$GRADLE_DIR/device-report.json" 2>/dev/null; then
        if [ -s "$GRADLE_DIR/device-report.json" ]; then
          return 0
        fi
      fi
    done
    if adb shell "run-as $package cat files/mrsad-perf.json" > "$GRADLE_DIR/device-report.json" 2>/dev/null; then
      if [ -s "$GRADLE_DIR/device-report.json" ]; then
        return 0
      fi
    fi
  done
  rm -f "$GRADLE_DIR/device-report.json"
  return 1
}

if [ -z "$REPORT" ]; then
  echo "the report was not found in the test results, looking on the device"
  if pull_report; then
    REPORT=$(cat "$GRADLE_DIR/device-report.json")
  fi
fi

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
  echo "== who am I ==" >> "$DEVICE_LISTING"
  adb shell id >> "$DEVICE_LISTING" 2>&1

  for package in dev.mrsad.app.debug dev.mrsad.app; do
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
    # `run-as` is the second way in, for a device where the shell cannot
    # become root.
    if adb shell "run-as $package ls files" >/dev/null 2>&1; then
      echo "== run-as $package ls files ==" >> "$DEVICE_LISTING"
      adb shell "run-as $package ls -la files" >> "$DEVICE_LISTING" 2>&1
      adb shell "run-as $package ls files" 2>/dev/null \
        | tr -d '\r' \
        | while read -r name; do
            case "$name" in
              *.png)
                adb exec-out "run-as $package cat files/$name" > "$SCREENSHOT_DIR/$name" || true
                ;;
            esac
          done
    else
      echo "== run-as $package ls files failed ==" >> "$DEVICE_LISTING"
      adb shell "run-as $package ls files" >> "$DEVICE_LISTING" 2>&1
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
