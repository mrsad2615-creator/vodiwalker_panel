#!/usr/bin/env bash
#
# Runs the performance benchmark on a connected device and collects what it
# produced: the reports of the sessions and the screenshots of the last one.
#
# The application is installed and driven by hand instead of through the
# Gradle task for connected tests: that task removes the application when
# it is done, and with it the pictures the benchmark took. Here the
# application stays on the device, so the evidence can be read afterwards.
#
# The benchmark is run more than once, because one run of an emulator is
# not a measurement: the machine shares its cores with everything else
# that runs on it. Several runs, and the middle of them, say something a
# single run cannot.
#
# The benchmark writes its report into the application's own storage and
# prints it with a marker, so the report can be recovered without any
# device tooling; the pictures are pulled from the same place.
#
# Usage: run-perf-benchmark.sh <gradle-project-dir> <output-dir> [screenshot-dir]

# The emulator action runs its script with a POSIX shell, so the script
# starts itself again under bash, which is what the rest of it expects.
if [ -z "${BASH_VERSION:-}" ]; then
  exec bash "$0" "$@"
fi

set -uo pipefail

GRADLE_DIR="${1:?usage: run-perf-benchmark.sh <gradle-project-dir> <output-dir> [screenshot-dir]}"
OUTPUT_DIR="${2:?usage: run-perf-benchmark.sh <gradle-project-dir> <output-dir> [screenshot-dir]}"
SCREENSHOT_DIR="${3:-}"

RUNS="${MRSAD_PERF_RUNS:-5}"

cd "$GRADLE_DIR" || exit 1
# The absolute path, so the log can be found from anywhere.
GRADLE_DIR="$(pwd)"
mkdir -p "$OUTPUT_DIR"

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

# ---- 3. Install -------------------------------------------------------------
adb install -r -t app/build/outputs/apk/debug/app-debug.apk 2>&1 | tee -a "$LOG"
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk 2>&1 | tee -a "$LOG"

# ---- 4. The report of one session -------------------------------------------
# The report is looked for in three places: the output of the run, the
# test results of Gradle, and the application's own storage.
read_report() {
  local file="$1"
  local report=""

  # The report is printed on a line of its own, so the rest of the line
  # is the report. Only the part of the log that belongs to this session
  # is read, so a session never reports the numbers of the one before it.
  if [ -f "$LOG" ]; then
    report=$(tail -c "+$((LOG_OFFSET + 1))" "$LOG" 2>/dev/null \
      | grep -o 'MRSAD_PERF_JSON:.*' | tail -1 | sed 's/^MRSAD_PERF_JSON://')
  fi

  # The application writes its report into its own storage as well. If
  # the output of the run was lost, that file is the second way to the
  # number. It must be a file that this session wrote: the file is
  # removed before the session starts, and its time is compared with the
  # start of the session as well.
  if [ -z "$report" ]; then
    for directory in "/data/user/0/$APP_ID/files" "/data/data/$APP_ID/files"; do
      if adb shell "cat $directory/mrsad-perf.json" > "$GRADLE_DIR/device-report.json" 2>/dev/null; then
        if [ -s "$GRADLE_DIR/device-report.json" ]; then
          STAMP=$(adb shell "stat -c %Y $directory/mrsad-perf.json" 2>/dev/null | tr -d '\r' || true)
          if [ -n "$STAMP" ] && [ "$STAMP" -lt "${SESSION_START:-0}" ]; then
            echo "the report on the device is older than this session and is not used"
            continue
          fi
          report=$(cat "$GRADLE_DIR/device-report.json")
          break
        fi
      fi
    done
  fi

  # The test results of Gradle are the last way to the report.
  if [ -z "$report" ]; then
    for directory in app/build/outputs/androidTest-results app/build/reports/androidTests; do
      if [ -d "$directory" ]; then
        report=$(grep -rho 'MRSAD_PERF_JSON:{[^<]*}' "$directory" 2>/dev/null | tail -1 | sed 's/^MRSAD_PERF_JSON://')
        if [ -n "$report" ]; then
          break
        fi
      fi
    done
  fi

  if [ -z "$report" ]; then
    rm -f "$GRADLE_DIR/device-report.json"
    return 1
  fi
  printf '%s\n' "$report" > "$file"
  return 0
}

# ---- 5. The screenshots of one session --------------------------------------
collect_screenshots() {
  local directory="$GRADLE_DIR/device-screenshots.txt"
  : > "$directory"
  echo "== who am I ==" >> "$directory"
  adb shell id >> "$directory" 2>&1

  for package in "$APP_ID" dev.mrsad.app; do
    for place in "/data/user/0/$package/files" "/data/data/$package/files"; do
      if adb shell "ls $place" >/dev/null 2>&1; then
        echo "== $place ==" >> "$directory"
        adb shell "ls -la $place" >> "$directory" 2>&1
        # Every picture by name: some versions of adb refuse a path that
        # ends in "/.".
        adb shell "ls $place" 2>/dev/null \
          | tr -d '\r' \
          | while read -r name; do
              case "$name" in
                *.png)
                  adb pull "$place/$name" "$SCREENSHOT_DIR" >/dev/null 2>&1 || true
                  ;;
              esac
            done
        break
      else
        echo "== $place is not there ==" >> "$directory"
      fi
    done
  done

  {
    echo "the device holds:"
    cat "$directory"
    echo
    echo "the collected screenshots:"
    ls -la "$SCREENSHOT_DIR" || true
  } > "$GRADLE_DIR/screenshot-collection.txt"
}

# ---- 6. The sessions --------------------------------------------------------
mkdir -p "$OUTPUT_DIR"

# A session that failed says nothing about the application, and a session
# that failed without a report must not be counted. At least this many
# sessions have to succeed for the measurement to mean something: one
# run of a shared emulator says more about the machine than about the
# application.
MIN_SESSIONS="${MRSAD_PERF_MIN_SESSIONS:-3}"
if [ "$MIN_SESSIONS" -gt "$RUNS" ]; then
  MIN_SESSIONS="$RUNS"
fi

FAILED=""
for run in $(seq 1 "$RUNS"); do
  echo "===== session $run of $RUNS =====" | tee -a "$LOG"
  # Where the log ends before this session: only what comes after it
  # belongs to this session.
  LOG_OFFSET=$(wc -c < "$LOG")
  SESSION_START=$(date +%s)

  # The report of the session before must not be able to pass as the
  # report of this one, so it is taken away first.
  rm -f "$GRADLE_DIR/device-report.json"
  adb shell "rm -f /data/user/0/$APP_ID/files/mrsad-perf.json /data/data/$APP_ID/files/mrsad-perf.json" \
    >/dev/null 2>&1 || true
  rm -rf app/build/outputs/androidTest-results app/build/reports/androidTests

  adb shell am instrument -w \
    -e class dev.mrsad.app.PerformanceBenchmarkTest \
    "$TEST_ID/$INSTRUMENTATION" 2>&1 | tee -a "$LOG"
  STATUS=${PIPESTATUS[0]}
  echo "session $run finished with status $STATUS" | tee -a "$LOG"

  if [ "$STATUS" -ne 0 ]; then
    # The instrumentation failed. Its numbers, if there are any, are not
    # used: a session that did not finish cannot describe the interface.
    echo "session $run failed with status $STATUS; its numbers are not used" >&2
    FAILED="$FAILED $run"
    continue
  fi

  if read_report "$OUTPUT_DIR/perf-session-$run.json"; then
    echo "the report of session $run was written"
  else
    echo "session $run produced no report" >&2
    FAILED="$FAILED $run"
    continue
  fi

  if [ -n "$SCREENSHOT_DIR" ]; then
    mkdir -p "$SCREENSHOT_DIR"
    collect_screenshots
  fi
done

# The pictures of the last session, and nothing else.
if [ -n "$SCREENSHOT_DIR" ]; then
  rm -f "$SCREENSHOT_DIR/mrsad-perf.json" "$SCREENSHOT_DIR/profileInstalled"
  rm -rf "$SCREENSHOT_DIR/datastore"
  echo "the screenshots were collected into $SCREENSHOT_DIR"
  ls -la "$SCREENSHOT_DIR" || true
fi

REPORTS=$(ls "$OUTPUT_DIR"/perf-session-*.json 2>/dev/null | wc -l)
echo "$REPORTS of $RUNS sessions produced a report"
if [ -n "$FAILED" ]; then
  echo "the sessions$FAILED did not produce a usable report"
fi

# The summary is written next to the reports, so that the number of
# usable sessions travels with the numbers themselves.
{
  echo "sessions: $RUNS"
  echo "usable reports: $REPORTS"
  echo "failed sessions:${FAILED:- none}"
} > "$OUTPUT_DIR/summary.txt"

if [ "$REPORTS" -eq 0 ]; then
  echo "no performance report was produced by the benchmark" >&2
  exit 1
fi
if [ "$REPORTS" -lt "$MIN_SESSIONS" ]; then
  echo "only $REPORTS of $RUNS sessions produced a report; at least $MIN_SESSIONS are needed" >&2
  exit 1
fi
