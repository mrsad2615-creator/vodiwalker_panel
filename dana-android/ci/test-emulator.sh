#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

package=ai.dana.preview
apk="$RUNNER_TEMP/dana-verified/DANA-AI-preview.apk"
test_apk="$PWD/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
test_package="$package.test"
log="$RUNNER_TEMP/dana-instrumentation.log"

progress="$RUNNER_TEMP/dana-device-progress.txt"
: > "$progress"
: > "$log"

mark() { printf '%s\n' "$1" | tee -a "$progress"; }
device() { timeout --kill-after=3s "${DANA_ADB_TIMEOUT:-25s}" adb "$@"; }
trap 'rc=$?; printf "Script line %s returned %s.\n" "$LINENO" "$rc" >> "$progress"' ERR
report_failure() {
  mark "$1"
  device logcat -b crash -d -t 28 >> "$log" 2>&1 || true
  exit 1
}

printf '::notice title=Dana emulator booted::Android API %s is visible to adb.\n' "$(device shell getprop ro.build.version.sdk | tr -d '\r')"
mark 'Warming package manager and checking Android storage before installing the preview.'
if ! timeout --kill-after=8s 300s ./gradlew --no-daemon --console=plain :core:data:connectedDebugAndroidTest 2>&1 | tee "$log"; then
  report_failure 'Storage and Android Keystore instrumentation failed.'
fi
python3 - <<'PYTHON'
from pathlib import Path
from xml.etree import ElementTree as ET
path = Path('core/data/build/outputs/androidTest-results')
reports = list((path / 'connected/debug').rglob('TEST-*.xml')) or list(path.rglob('TEST-*.xml'))
cases = [case for report in reports for case in ET.parse(report).iter('testcase')]
if len(cases) < 8 or any(case.find('skipped') is not None or case.find('failure') is not None or case.find('error') is not None for case in cases):
    raise SystemExit('::error title=Dana device tests::core/data: expected at least eight passing tests with no failures/skips.')
print(f'::notice title=Dana device storage::{len(cases)} Android storage and Keystore tests passed.')
PYTHON

# The standalone storage test does not install the application package. Wait for
# the AOSP launcher and package/keystore services to settle before the UI run.
mark 'Storage passed; prepare emulator display.'
device shell svc power stayon true >> "$log" 2>&1 || report_failure 'Could not keep the emulator awake.'
# These requests are best-effort: AndroidJUnitRunner requires a resumed Activity
# below and does not pass on a locked/non-drawing device.
device shell input keyevent KEYCODE_WAKEUP >> "$log" 2>&1 || true
device shell wm dismiss-keyguard >> "$log" 2>&1 || true

# Assemble and signature-check once, install those exact bytes, and use a separate
# test APK. The app module is not installed through Gradle.
mark 'Install the signature-checked APK into the fresh emulator.'
if device shell pm path "$package" | grep -q '^package:'; then
  device shell pm clear "$package" 2>&1 | tee -a "$log" | grep -Fq Success || report_failure 'Could not reset existing preview data.'
fi
DANA_ADB_TIMEOUT=60s device install -r "$apk" >> "$log" 2>&1 || report_failure 'Could not install the verified APK.'
mark 'Install the instrumentation APK.'
DANA_ADB_TIMEOUT=60s device install -r -t "$test_apk" >> "$log" 2>&1 || report_failure 'Could not install the test APK.'
mark 'Run Studio and OCR tests on the exact installed APK.'
if ! DANA_ADB_TIMEOUT=235s device shell am instrument -w "$test_package/androidx.test.runner.AndroidJUnitRunner" 2>&1 | tee "$log"; then
  report_failure 'Could not run Android instrumentation on the verified APK.'
fi
if ! grep -Eq '^OK \(([3-9]|[1-9][0-9]+) tests\)' "$log" || grep -Eq 'FAILURES!!!|INSTRUMENTATION_(FAILED|ABORTED)' "$log"; then
  report_failure 'The installed APK did not pass Studio smoke and native OCR tests.'
fi

mark 'App instrumentation passed; copy four screenshots.'
screen_dir="$RUNNER_TEMP/dana-screens"
mkdir -p "$screen_dir"
for page in home account engines chat; do
  device exec-out run-as "$package" cat "files/ci-screens/$page.png" > "$screen_dir/$page.png"
done
python3 - "$screen_dir" <<'PYTHON'
from pathlib import Path
import struct
import sys
for p in Path(sys.argv[1]).glob('*.png'):
    with p.open('rb') as f:
        header = f.read(24)
    if len(header) != 24 or header[:8] != b'\x89PNG\r\n\x1a\n' or min(struct.unpack('>II', header[16:])) < 240:
        raise SystemExit(f'::error title=Dana screenshots::Missing or invalid screenshot: {p.name}')
PYTHON
printf '::notice title=Dana installed APK::Studio home, account setup, offline Free engine, Compare guard, Share review, approved note and native OCR passed.\n'
