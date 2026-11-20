#!/usr/bin/env bash
#
# Runs ONE measurement session of a build that is already made.
#
# The application is installed and driven by hand instead of through the
# Gradle task for connected tests: that task removes the application
# when it is done, and with it the pictures the benchmark took. Here the
# application stays on the device, so the evidence can be read
# afterwards.
#
# The benchmark writes its report into the application's own storage and
# prints it with a marker, so the report can be recovered without any
# device tooling; the pictures are pulled from the same place.
#
# Usage:
#   perf-session.sh <app-apk> <test-apk> <log-file> <report-file> [screenshot-dir]
#
# The session returns 0 only when the instrumentation succeeded and a
# report was produced. A session that failed says nothing about the
# application, so its numbers are not used.

if [ -z "${BASH_VERSION:-}" ]; then
  exec bash "$0" "$@"
fi

set -uo pipefail

APP_APK="${1:?usage: perf-session.sh <app-apk> <test-apk> <log-file> <report-file> [screenshot-dir]}"
TEST_APK="${2:?}"
LOG="${3:?}"
REPORT="${4:?}"
SCREENSHOT_DIR="${5:-}"

APP_ID="dev.mrsad.app.debug"
TEST_ID="dev.mrsad.app.debug.test"
INSTRUMENTATION="androidx.test.runner.AndroidJUnitRunner"

GRADLE_DIR="$(dirname "$REPORT")"
mkdir -p "$(dirname "$REPORT")"

# ---- 1. The shell becomes root ----------------------------------------------
# The emulator is a userdebug image, so the shell can become root: that
# is the way into the private storage of the application, where the
# benchmark wrote its report and its pictures.
adb root >/dev/null 2>&1 || true
adb wait-for-device >/dev/null 2>&1 || true

# ---- 2. Install -------------------------------------------------------------
# The application is removed first: the two builds of the campaign are
# signed with different keys, and a session that starts from the state
# of the session before it is not the same session twice.
adb uninstall "$APP_ID" >/dev/null 2>&1 || true
adb uninstall "$TEST_ID" >/dev/null 2>&1 || true
adb install -r -t "$APP_APK" 2>&1 | tee -a "$LOG"
adb install -r -t "$TEST_APK" 2>&1 | tee -a "$LOG"

# ---- 3. The report of the session -------------------------------------------
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

# ---- 4. The screenshots of the session --------------------------------------
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

# ---- 5. The session ---------------------------------------------------------
# Where the log ends before this session: only what comes after it
# belongs to this session.
LOG_OFFSET=0
[ -f "$LOG" ] && LOG_OFFSET=$(wc -c < "$LOG")
SESSION_START=$(date +%s)

# The report of the session before must not be able to pass as the
# report of this one, so it is taken away first.
rm -f "$GRADLE_DIR/device-report.json"
adb shell "rm -f /data/user/0/$APP_ID/files/mrsad-perf.json /data/data/$APP_ID/files/mrsad-perf.json" \
  >/dev/null 2>&1 || true

adb shell am instrument -w \
  -e class dev.mrsad.app.PerformanceBenchmarkTest \
  "$TEST_ID/$INSTRUMENTATION" 2>&1 | tee -a "$LOG"
STATUS=${PIPESTATUS[0]}
echo "the session finished with status $STATUS" | tee -a "$LOG"

if [ "$STATUS" -ne 0 ]; then
  # The instrumentation failed. Its numbers, if there are any, are not
  # used: a session that did not finish cannot describe the interface.
  echo "the session failed with status $STATUS; its numbers are not used" >&2
  exit 1
fi

if ! read_report "$REPORT"; then
  echo "the session produced no report" >&2
  exit 1
fi

if [ -n "$SCREENSHOT_DIR" ]; then
  mkdir -p "$SCREENSHOT_DIR"
  collect_screenshots
fi

exit 0
