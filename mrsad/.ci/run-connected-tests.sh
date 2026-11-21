#!/bin/sh
# Instrumented test runner used by CI. Kept as a file (not an inline YAML
# block) so shell quoting inside the emulator action stays predictable.
#
# Why each test runs in its own bounded Gradle invocation:
#
# A Compose UI test that cannot reach idle blocks in `waitForIdle()`, which
# has no timeout of its own. Left unbounded, one such test would consume the
# whole workflow budget and hide the result of every other test. Running the
# methods one at a time under `timeout` turns a hang into a bounded failure
# and tells us exactly which test hung.
#
# While a test is running, a watcher dumps the *thread stacks* of the app
# process shortly before the timeout fires. That dump is the single most
# informative artefact available for a hang: it shows whether the main
# thread is busy (a real animation/loop) or parked in the message loop
# (the hang is in the test framework's idle wait), and it shows exactly which
# test thread is stuck.
#
# Every phase is echoed into ui-test.log so the CI reporter can turn it into
# workflow annotations.
set -u

LOG=ui-test.log
RESULTS=ui-test-results
DUMPS=ui-test-dumps
CLASS=dev.mrsad.app.MrsadSmokeTest
APP_PKG=dev.mrsad.app.debug
PER_TEST_TIMEOUT=${MRSAD_TEST_TIMEOUT_SECONDS:-180}
# Dump the stacks a little before the kill, while the test is definitely
# still hung, so the dump reflects the hang and not the teardown.
DUMP_LEAD=${MRSAD_TEST_DUMP_LEAD_SECONDS:-20}

# Every method of the smoke class has to run here. A test that is not
# listed is a test that never runs, which looks exactly like a test that
# passes — so the list is kept in sync with the class by hand and reviewed
# whenever a method is added.
METHODS="homeShowsBrandAndLivingCore
theBrandMarkIsDrawnWhereTheProductIntroducesItself
bottomNavigationReachesTypeSetup
typingSessionStartsAndAbandonsWithoutFabricatingARecord
everyInteractiveControlIsNamed
theLayoutIsRightToLeftOnEveryDevice
settingsOpensAndAppliesTheChosenTheme"

: > "$LOG"
rm -rf "$RESULTS" "$DUMPS"
mkdir -p "$RESULTS" "$DUMPS"

# A bigger logcat buffer keeps a full thread dump from being truncated.
adb logcat -G 16M >/dev/null 2>&1
# Start from an empty log buffer so the first capture contains only what the
# first test produced.
adb logcat -c >/dev/null 2>&1

app_pid() {
    pid=$(timeout 5 adb shell pidof "$APP_PKG" 2>/dev/null | tr -d '\r' | awk '{print $1}')
    if [ -z "$pid" ]; then
        pid=$(timeout 5 adb shell ps -A 2>/dev/null | tr -d '\r' | awk '$NF ~ /'"$APP_PKG"'$/ {print $2; exit}')
    fi
    echo "$pid"
}

# Runs in the background while a test executes: if the test is still alive
# when the timeout approaches, dump the app's thread stacks.
watchdog() {
    method="$1"
    sleep $((PER_TEST_TIMEOUT - DUMP_LEAD)) &
    sleep_pid=$!
    trap 'kill "$sleep_pid" >/dev/null 2>&1; exit 0' TERM INT
    wait "$sleep_pid" || return 0
    pid=$(app_pid)
    [ -n "$pid" ] || return 0
    # SIGQUIT makes the runtime print every thread's stack into logcat.
    timeout 5 adb shell kill -3 "$pid" >/dev/null 2>&1
    sleep 3
    timeout 10 adb logcat -d -v threadtime 2>/dev/null \
        | sed -n '/DALVIK THREADS/,/^$/p' \
        > "$DUMPS/$method.txt" 2>/dev/null
    {
        echo "----- THREAD DUMP $method (pid $pid) -----"
        if [ -s "$DUMPS/$method.txt" ]; then
            head -n 250 "$DUMPS/$method.txt"
        else
            echo "(no thread dump captured)"
        fi
    } >> "$LOG"
}

run_one() {
    method="$1"
    echo "===== BEGIN $method =====" >> "$LOG"
    date -u +"begin %Y-%m-%dT%H:%M:%SZ" >> "$LOG"

    watchdog "$method" &
    watcher=$!

    timeout --signal=TERM --kill-after=30 "$PER_TEST_TIMEOUT" \
        ./gradlew --console=plain --no-daemon :app:connectedDebugAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.class="$CLASS#$method" >> "$LOG" 2>&1
    rc=$?

    kill "$watcher" >/dev/null 2>&1
    wait "$watcher" >/dev/null 2>&1

    echo "===== END $method exit=$rc =====" >> "$LOG"
    date -u +"end %Y-%m-%dT%H:%M:%SZ" >> "$LOG"

    # Keep a per-test copy of the machine readable results.
    mkdir -p "$RESULTS/$method"
    for f in app/build/outputs/androidTest-results/connected/*/*.xml; do
        [ -f "$f" ] && cp -f "$f" "$RESULTS/$method/" 2>/dev/null
    done

    # The app's own logcat is the only place that shows what the process was
    # actually doing (for example whether the deterministic motion budget was
    # in effect), so capture it with every test.
    {
        echo "----- LOGCAT $method -----"
        timeout 10 adb logcat -d -v brief 2>/dev/null | tail -n 400
    } >> "$LOG"
    timeout 5 adb logcat -c >/dev/null 2>&1

    # A killed run can leave the app process behind; stop it so the next test
    # starts from a clean device.
    timeout 5 adb shell am force-stop "$APP_PKG" >/dev/null 2>&1
    timeout 5 adb shell am force-stop dev.mrsad.app >/dev/null 2>&1

    return $rc
}

FAILED=0
PASSED=0

# Build the app and the test APK once, up front.
#
# The first connected-test invocation has to compile the app, run KSP for
# Room and build the test APK. That easily takes longer than the per-test
# timeout, so without this warm-up the *first* test would be killed by the
# timeout while it was still compiling — which looks exactly like a hang.
echo "===== WARM-UP BUILD =====" >> "$LOG"
date -u +"begin %Y-%m-%dT%H:%M:%SZ" >> "$LOG"
timeout --signal=TERM --kill-after=30 "${MRSAD_BUILD_TIMEOUT_SECONDS:-900}" \
    ./gradlew --console=plain --no-daemon \
    :app:assembleDebug :app:assembleDebugAndroidTest >> "$LOG" 2>&1
build_rc=$?
echo "===== END WARM-UP BUILD exit=$build_rc =====" >> "$LOG"
date -u +"end %Y-%m-%dT%H:%M:%SZ" >> "$LOG"

if [ "$build_rc" -ne 0 ]; then
    echo "===== BUILD FAILED ====="
    tail -n 120 "$LOG"
    exit 1
fi

for m in $METHODS; do
    if ! timeout 5 adb get-state >/dev/null 2>&1; then
        echo "===== EMULATOR DISCONNECTED BEFORE $m =====" >> "$LOG"
        FAILED=$((FAILED + 1))
        break
    fi
    if run_one "$m"; then
        PASSED=$((PASSED + 1))
    else
        FAILED=$((FAILED + 1))
    fi
done

{
    echo "===== TEST RESULT SUMMARY ====="
    echo "passed=$PASSED failed=$FAILED"
    for m in $METHODS; do
        echo "--- $m ---"
        for f in "$RESULTS/$m"/*.xml; do
            [ -f "$f" ] || continue
            grep -o 'tests="[0-9]*" *failures="[0-9]*" *errors="[0-9]*"' "$f" 2>/dev/null | head -1
        done
    done
} >> "$LOG"

echo "===== INSTRUMENTED TESTS COMPLETE ====="
echo "passed=$PASSED failed=$FAILED"

if [ "$FAILED" -ne 0 ]; then
    echo "===== INSTRUMENTED TEST FAILURE ====="
    tail -n 120 "$LOG"
    echo "===== XML FAILURES ====="
    grep -r -h -A4 "<failure" "$RESULTS" 2>/dev/null | head -60
    exit 1
fi

echo "===== INSTRUMENTED TESTS PASSED ====="
grep -E "Tests on|BUILD SUCCESSFUL" "$LOG" | tail -5
exit 0
