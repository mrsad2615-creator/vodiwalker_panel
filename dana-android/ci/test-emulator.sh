#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

trap 'code=$?; printf "::error title=Dana emulator script::Failed at line %s (exit %s).\n" "$LINENO" "$code"' ERR
package=ai.dana.preview
apk="$RUNNER_TEMP/dana-verified/DANA-AI-preview.apk"
test_apk="$PWD/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
test_package="$package.test"
log="$RUNNER_TEMP/dana-instrumentation.log"

device() { timeout --kill-after=3s "${DANA_ADB_TIMEOUT:-25s}" adb "$@"; }
report_failure() {
  device logcat -b crash -d -t 28 >> "$log" 2>&1 || true
  python3 - "$log" "$1" <<'PYTHON'
from pathlib import Path
import sys

lines = Path(sys.argv[1]).read_text(errors='replace').splitlines()
important = [line for line in lines if line.startswith(('Error:', 'FAILURES', 'Caused by:', 'FATAL EXCEPTION')) or 'Exception:' in line or 'AssertionError' in line]
message = sys.argv[2] + '\n' + '\n'.join(important[:12] + lines[-24:])[:3350]
print('::error title=Dana device test::' + message.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))
PYTHON
  exit 1
}

printf '::notice title=Dana emulator booted::Android API %s is visible to adb.\n' "$(device shell getprop ro.build.version.sdk | tr -d '\r')"
device shell svc power stayon true
device shell input keyevent KEYCODE_WAKEUP
device shell wm dismiss-keyguard

# Assemble and signature-check once, install those exact bytes, and use a separate
# test APK. Do not reinstall the app through Gradle before the UI test executes.
DANA_ADB_TIMEOUT=60s device install -r "$apk" > "$log" 2>&1 || report_failure 'Could not install the verified APK.'
device shell pm clear "$package" | grep -Fq Success
DANA_ADB_TIMEOUT=60s device install -r -t "$test_apk" >> "$log" 2>&1 || report_failure 'Could not install the test APK.'
if ! DANA_ADB_TIMEOUT=235s device shell am instrument -w "$test_package/androidx.test.runner.AndroidJUnitRunner" 2>&1 | tee "$log"; then
  report_failure 'Could not run Android instrumentation on the verified APK.'
fi
if ! grep -Eq '^OK \(([3-9]|[1-9][0-9]+) tests\)' "$log" || grep -Eq 'FAILURES!!!|INSTRUMENTATION_(FAILED|ABORTED)' "$log"; then
  report_failure 'The installed APK did not pass Studio smoke and native OCR tests.'
fi

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
