#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

fail() {
  printf '::error title=Dana Android emulator::%s\n' "$1"
  exit 1
}

trap 'code=$?; printf "::error title=Dana emulator script::Failed at line %s (exit %s).\n" "$LINENO" "$code"' ERR
printf '::notice title=Dana emulator booted::Android API %s is visible to adb.\n' "$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
if ! ./gradlew --no-daemon --console=plain :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest 2>&1 | tee "$RUNNER_TEMP/dana-device.log"; then
  python3 - "$RUNNER_TEMP/dana-device.log" <<'PYTHON'
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

messages = []
for report in sorted(Path('.').glob('**/build/outputs/androidTest-results/**/TEST-*.xml')):
    for case in ET.parse(report).iter('testcase'):
        for node in case.findall('failure') + case.findall('error'):
            messages.append(f"{case.get('name')}: {node.get('message', '')}\n{(node.text or '')[:1000]}")
lines = Path(sys.argv[1]).read_text(errors='replace').splitlines()
important = [line for line in lines if line.startswith(('e:', '> ', 'Caused by:', 'Error:', '* What went wrong')) and not line.startswith('> Task')]
message = '\n'.join(messages + (important or lines[-25:]))[:3800]
print('::error title=Dana device test failed::' + message.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))
PYTHON
  exit 1
fi
printf '::notice title=Dana device test execution::Storage and native OCR instrumentation finished successfully.\n'
python3 - <<'PYTHON'
from pathlib import Path
from xml.etree import ElementTree as ET

for module, minimum in [('core/data', 7), ('app', 2)]:
    path = Path(module) / 'build/outputs/androidTest-results'
    reports = list((path / 'connected/debug').rglob('TEST-*.xml')) or list(path.rglob('TEST-*.xml'))
    cases = [case for report in reports for case in ET.parse(report).iter('testcase')]
    if len(cases) < minimum or any(case.find('skipped') is not None or case.find('failure') is not None or case.find('error') is not None for case in cases):
        raise SystemExit(f'::error title=Dana device tests::{module}: expected at least {minimum} passing tests with no failures/skips.')
    print(f'{len(cases)} Android tests in {module} executed successfully')
PYTHON

adb install -r app/build/outputs/apk/debug/app-debug.apk
package=ai.dana.preview
activity="$package/ai.dana.MainActivity"
adb shell am force-stop "$package"
start=$(adb shell am start -W -n "$activity" | tr -d '\r')
printf '%s\n' "$start"
grep -q '^Status: ok$' <<< "$start" || fail 'The APK did not launch successfully.'

xml_path=/sdcard/dana-smoke.xml
snapshot="$RUNNER_TEMP/dana-smoke.xml"
dump_ui() {
  for attempt in 1 2 3; do
    adb shell rm -f "$xml_path"
    if timeout 15s adb shell uiautomator dump "$xml_path" >/dev/null 2>&1 &&
      adb exec-out cat "$xml_path" > "$snapshot" && [[ -s "$snapshot" ]]; then
      return 0
    fi
  done
  fail 'Cannot inspect the running Dana activity.'
}

tap_text() {
  local needle="$1" xy x y
  xy=$(python3 - "$snapshot" "$needle" <<'PYTHON'
import re
import sys
from xml.etree import ElementTree as ET

nodes = [n for n in ET.parse(sys.argv[1]).iter('node') if n.get('text') == sys.argv[2]]
for node in nodes:
    match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
    if match:
        left, top, right, bottom = map(int, match.groups())
        if right > left and bottom > top:
            print((left + right) // 2, (top + bottom) // 2)
            break
PYTHON
)
  [[ -n "$xy" ]] || fail "Cannot find the control: $needle"
  read -r x y <<< "$xy"
  adb shell input tap "$x" "$y"
}

dump_ui
grep -Fq 'میز کار دانا' "$snapshot" || fail 'The Studio home screen is missing.'
tap_text 'مقایسه'
dump_ui
grep -Fq 'یک سؤال، چند پاسخ' "$snapshot" || fail 'The comparison screen did not open.'
grep -Fq 'حالت کاملاً آفلاین روشن است' "$snapshot" || fail 'A clean install did not display the offline guard.'
tap_text 'تنظیمات'
dump_ui
grep -Fq 'مرکز اتصال موتورها' "$snapshot" || fail 'The Engine Hub did not open.'
tap_text 'راه‌اندازی Gemini'
dump_ui
grep -Fq 'نشانی رسمی Gemini آماده است' "$snapshot" || fail 'The Gemini preset did not open.'
adb shell input keyevent KEYCODE_BACK

# Use an incoming user share to fill the draft exactly as Android does. It must not send or save automatically.
opened=$(adb shell "am start -W -n $activity -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT 'note DANA_SMOKE_LOCAL'" | tr -d '\r')
grep -q '^Status: ok$' <<< "$opened" || fail 'Shared text did not open Dana.'
dump_ui
grep -Fq 'DANA_SMOKE_LOCAL' "$snapshot" || fail 'The shared text did not appear in the editable composer.'
tap_text 'بازبینی'
dump_ui
grep -Fq 'این کار را انجام دهیم؟' "$snapshot" || fail 'Local command bypassed or failed to open the review.'
tap_text 'تأیید و اجرا'
dump_ui
grep -Fq 'یادداشت روی گوشی ذخیره شد' "$snapshot" || fail 'The approved offline note was not saved.'
tap_text 'یادداشت‌ها'
dump_ui
grep -Fq 'DANA_SMOKE_LOCAL' "$snapshot" || fail 'The approved offline note is absent from the notes repository.'
adb shell pidof "$package" >/dev/null || fail 'Dana terminated during the smoke test.'
printf '\nDana APK installed, opened the Studio, showed the default offline guard, opened the Gemini preset, and saved an approved note while offline.\n'
