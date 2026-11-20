#!/usr/bin/env bash
#
# Measures the two builds of the app, on one emulator, in one job.
#
# Why both builds are measured here and not in two jobs: a job of the
# matrix runs on its own machine, and a machine of the cloud is not the
# same machine twice. When the two builds are measured on two machines,
# a slow afternoon on one of them looks exactly like a slow
# application. So both builds are measured here, on the same emulator,
# in the same job, and the order of the two is swapped after every
# round, so that neither of them can profit from being measured first.
#
# Every build is measured several times, because one run of an emulator
# is not a measurement: the machine shares its cores with everything
# else that runs on it. The comparison reports the middle of the runs
# and the spread of the runs.
#
# Usage: run-perf-benchmark.sh <work-dir> [runs]
#
# The work directory must hold the two builds:
#   <work-dir>/apks/baseline/{app.apk,test.apk}
#   <work-dir>/apks/redesign/{app.apk,test.apk}
#
# And it receives:
#   <work-dir>/out/perf-baseline/perf-session-<n>.json
#   <work-dir>/out/perf-redesign/perf-session-<n>.json
#   <work-dir>/out/screenshots-baseline/
#   <work-dir>/out/screenshots-redesign/

if [ -z "${BASH_VERSION:-}" ]; then
  exec bash "$0" "$@"
fi

set -uo pipefail

WORK="${1:?usage: run-perf-benchmark.sh <work-dir> [runs]}"
RUNS="${2:-${MRSAD_PERF_RUNS:-5}}"

HERE="$(cd "$(dirname "$0")" && pwd)"
SESSION_SCRIPT="$HERE/perf-session.sh"

LOG="$WORK/perf-benchmark.log"
OUT="$WORK/out"
mkdir -p "$OUT/perf-baseline" "$OUT/perf-redesign" \
         "$OUT/screenshots-baseline" "$OUT/screenshots-redesign"
: > "$LOG"

FAILED=""
BASE_COUNT=0
REDESIGN_COUNT=0

for round in $(seq 1 "$RUNS"); do
  echo "===== round $round of $RUNS =====" | tee -a "$LOG"

  # The order is swapped every round, so that the build that is
  # measured first does not always pay for the start of the round.
  if [ $((round % 2)) -eq 1 ]; then
    ORDER="baseline redesign"
  else
    ORDER="redesign baseline"
  fi

  for side in $ORDER; do
    APP_APK="$WORK/apks/$side/app.apk"
    TEST_APK="$WORK/apks/$side/test.apk"
    REPORT="$OUT/perf-$side/perf-session-$round.json"

    if [ ! -f "$APP_APK" ] || [ ! -f "$TEST_APK" ]; then
      echo "the build of $side is missing" >&2
      FAILED="$FAILED $side:$round"
      continue
    fi

    echo "----- $side, round $round -----" | tee -a "$LOG"
    if bash "$SESSION_SCRIPT" "$APP_APK" "$TEST_APK" "$LOG" "$REPORT" \
         "$OUT/screenshots-$side" >> "$LOG" 2>&1; then
      echo "the report of $side, round $round, was written"
      if [ "$side" = "baseline" ]; then
        BASE_COUNT=$((BASE_COUNT + 1))
      else
        REDESIGN_COUNT=$((REDESIGN_COUNT + 1))
      fi
    else
      echo "the session of $side, round $round, failed; its numbers are not used" >&2
      FAILED="$FAILED $side:$round"
    fi
  done
done

echo "the baseline produced $BASE_COUNT reports, the redesign produced $REDESIGN_COUNT"

{
  echo "rounds: $RUNS"
  echo "baseline reports: $BASE_COUNT"
  echo "redesign reports: $REDESIGN_COUNT"
  echo "failed sessions:${FAILED:- none}"
} > "$OUT/summary.txt"

# A session that failed is not used, but the campaign is only a
# measurement when both sides have enough sessions to say something.
MIN_SESSIONS="${MRSAD_PERF_MIN_SESSIONS:-3}"
if [ "$BASE_COUNT" -lt "$MIN_SESSIONS" ] || [ "$REDESIGN_COUNT" -lt "$MIN_SESSIONS" ]; then
  echo "at least $MIN_SESSIONS sessions per side are needed; there are $BASE_COUNT and $REDESIGN_COUNT" >&2
  exit 1
fi

if [ -n "$FAILED" ]; then
  echo "the sessions$FAILED did not produce a usable report"
fi

echo "the measurement is in $OUT"
exit 0
