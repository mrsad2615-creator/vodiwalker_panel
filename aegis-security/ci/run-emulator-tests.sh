#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

progress="$RUNNER_TEMP/aegis-device-progress.txt"
e2e_log="$RUNNER_TEMP/aegis-e2e.log"
guest_log="$RUNNER_TEMP/aegis-emulator-guest.log"
: > "$progress"
: > "$e2e_log"
: > "$guest_log"

mark() { printf '%s\n' "$1" | tee -a "$progress"; }
trap 'rc=$?; printf "Script failed at line %s with exit code %s\n" "$LINENO" "$rc" >> "$progress"' ERR

mark "Waiting for emulator adb connection"
adb wait-for-device
adb shell svc power stayon true || true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true

mark "Enforcing Airplane Mode and disabling WiFi/Mobile radios"
adb shell cmd connectivity airplane-mode enable || true
adb shell settings put global airplane_mode_on 1 || true
adb shell svc wifi disable || true
adb shell svc data disable || true
echo "Android API: $(adb shell getprop ro.build.version.sdk | tr -d '\r') | Airplane mode: $(adb shell settings get global airplane_mode_on | tr -d '\r')" | tee -a "$progress"

adb logcat -T 1 -v brief 'ActivityTaskManager:I' 'WindowManager:E' 'AndroidRuntime:E' 'Watchdog:E' 'libc:F' '*:S' > "$guest_log" 2>&1 &
logcat_pid=$!
trap 'kill "$logcat_pid" 2>/dev/null || true' EXIT

mark "Running Room v1->v2 migration test & Compose E2E suite via Gradle"
if ! timeout --kill-after=10s 540s gradle --no-daemon --console=plain --stacktrace \
  :core:database:connectedDebugAndroidTest \
  :app:connectedDebugAndroidTest 2>&1 | tee "$e2e_log"; then
  mark "connectedDebugAndroidTest failed"
  adb logcat -b crash -d -t 40 >> "$e2e_log" 2>&1 || true
  exit 1
fi

mark "Verifying Android instrumentation XML test counts"
python3 - <<'PYTHON'
from pathlib import Path
from xml.etree import ElementTree as ET
for mod in ['core/database', 'app']:
    reports = list((Path(mod) / 'build/outputs/androidTest-results').rglob('TEST-*.xml'))
    cases = [c for r in reports for c in ET.parse(r).iter('testcase')]
    if not cases or any(c.find('failure') is not None or c.find('error') is not None for c in cases):
        raise SystemExit(f"::error title=AndroidTest::{mod} instrumentation had 0 or failed tests")
    print(f"Verified {len(cases)} passing Android instrumentation test(s) in {mod}")
PYTHON

mark "Installing signed app-release.apk and launching MainActivity in Airplane Mode for screenshot & logcat check"
mkdir -p screenshots
adb uninstall ir.aegis.security >/dev/null 2>&1 || true
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -W -n ir.aegis.security/ir.aegis.app.MainActivity
sleep 3
adb exec-out screencap -p > screenshots/home_airplane_mode.png || true
adb shell input tap 108 1820 || true
sleep 2
adb exec-out screencap -p > screenshots/map_airplane_mode.png || true
adb logcat -d -s AndroidRuntime:E | tee screenshots/crash_log.txt || true
if grep -q "FATAL EXCEPTION" screenshots/crash_log.txt; then
  mark "FATAL EXCEPTION in logcat"
  exit 1
fi
mark "Emulator verification completed successfully"
