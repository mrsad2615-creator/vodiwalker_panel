#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

fail() {
  printf '::error title=Dana Android emulator::%s\n' "$1"
  exit 1
}

trap 'code=$?; printf "::error title=Dana emulator script::Failed at line %s (exit %s).\n" "$LINENO" "$code"' ERR
printf '::notice title=Dana emulator booted::Android API %s is visible to adb.\n' "$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
if ! ./gradlew --no-daemon --console=plain :core:data:connectedDebugAndroidTest 2>&1 | tee "$RUNNER_TEMP/dana-device.log"; then
  python3 - "$RUNNER_TEMP/dana-device.log" <<'PYTHON'
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

messages = []
for report in Path('core/data/build/outputs/androidTest-results').rglob('TEST-*.xml'):
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
printf '::notice title=Dana device test execution::Storage instrumentation finished successfully.\n'
python3 - <<'PYTHON'
from pathlib import Path
from xml.etree import ElementTree as ET

reports = list(Path('core/data/build/outputs/androidTest-results/connected/debug').rglob('TEST-*.xml'))
if not reports:
    reports = list(Path('core/data/build/outputs/androidTest-results').rglob('TEST-*.xml'))
cases = [case for report in reports for case in ET.parse(report).iter('testcase')]
if len(cases) < 6 or any(case.find('skipped') is not None or case.find('failure') is not None or case.find('error') is not None for case in cases):
    raise SystemExit('::error title=Dana device tests::Expected at least six executed Android storage tests with no failures/skips.')
print(f'{len(cases)} Android storage tests executed successfully')
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

dump_ui
coords=$(python3 - "$snapshot" <<'PYTHON'
import re
import sys
from xml.etree import ElementTree as ET

nodes = [node for node in ET.parse(sys.argv[1]).iter('node') if node.get('text') == 'مقایسه']
for node in nodes:
    m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
    if m:
        left, top, right, bottom = map(int, m.groups())
        if right > left and bottom > top:
            print((left + right) // 2, (top + bottom) // 2)
            break
PYTHON
)
[[ -n "$coords" ]] || fail 'The comparison control is missing from the home screen.'
read -r x y <<< "$coords"
adb shell input tap "$x" "$y"
dump_ui
grep -Fq 'یک سؤال، چند پاسخ' "$snapshot" || fail 'The comparison screen did not open.'
grep -Fq 'حالت کاملاً آفلاین روشن است' "$snapshot" || fail 'A clean install did not display the offline guard.'
adb shell pidof "$package" >/dev/null || fail 'Dana terminated during the smoke test.'
printf '\nDana APK installed, launched, opened comparison, and showed the default offline guard.\n'
