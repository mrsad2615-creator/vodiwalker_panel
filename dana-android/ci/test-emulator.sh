#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

device() { timeout --kill-after=2s 18s adb "$@"; }

fail() {
  # This emulator is clean and receives only synthetic fixture text. Include the
  # last visible nodes and crash buffer so a failure is actionable without job logs.
  local summary
  summary=$(python3 - "$snapshot" <<'PYTHON'
import sys
from xml.etree import ElementTree as ET

try:
    nodes = list(ET.parse(sys.argv[1]).iter('node'))
    print('Packages: ' + ', '.join(sorted({node.get('package', '?') for node in nodes})))
    labels = dict.fromkeys(text for node in nodes for attr in ('text', 'content-desc') if (text := node.get(attr)))
    print('Visible: ' + ' | '.join(labels))
except (OSError, ET.ParseError):
    print('No current UI hierarchy.')
PYTHON
)
  summary="$1"$'\n'"$summary"$'\n'
  summary+="$(device shell dumpsys -t 5 window | grep -m 2 -E 'mCurrentFocus|mFocusedApp' || true)"$'\n'
  summary+="$(device logcat -b crash -d -t 28 | tail -c 2100 || true)"
  summary=${summary:0:3900}
  summary=${summary//%/%25}
  summary=${summary//$'\r'/%0D}
  summary=${summary//$'\n'/%0A}
  printf '::error title=Dana Android emulator::%s\n' "$summary"
  exit 1
}

trap 'code=$?; printf "::error title=Dana emulator script::Failed at line %s (exit %s).\n" "$LINENO" "$code"' ERR
printf '::notice title=Dana emulator booted::Android API %s is visible to adb.\n' "$(device shell getprop ro.build.version.sdk | tr -d '\r')"
device shell svc power stayon true
package=ai.dana.preview
activity="$package/ai.dana.MainActivity"
xml_path=/sdcard/dana-smoke.xml
snapshot="$RUNNER_TEMP/dana-smoke.xml"
# Use the precise bytes whose signature, native alignment and hash were checked.
timeout --kill-after=2s 45s adb install -r "$RUNNER_TEMP/dana-verified/DANA-AI-preview.apk" || fail 'Could not install the verified APK.'
device shell input keyevent KEYCODE_WAKEUP
device shell wm dismiss-keyguard
device shell pm clear "$package" | grep -Fq Success

# Don't wait on ActivityManager's first-frame callback: the actual Compose
# semantics below are a stronger readiness check than `am start -W`.
launch() {
  local output=''
  if ! output=$(device shell am start "$@" 2>&1 | tr -d '\r') || grep -Eq '^Error( type|:)|^Exception' <<< "$output"; then
    fail "Could not start Dana. adb: ${output:0:950}"
  fi
  printf '%s\n' "$output"
}
launch -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$activity"

await_ui() {
  local expected="$1" message="$2" attempt
  # Wait for the required semantic text, not merely the splash window.
  for attempt in 1 2 3 4 5; do
    if device shell "rm -f $xml_path && timeout -k 1 10 uiautomator dump $xml_path" >/dev/null 2>&1 &&
      device exec-out cat "$xml_path" > "$snapshot" && grep -Fq "$expected" "$snapshot"; then
      return 0
    fi
    sleep 1
  done
  fail "$message"
}

tap_text() {
  local needle="$1" xy x y
  xy=$(python3 - "$snapshot" "$needle" <<'PYTHON'
import re
import sys
from xml.etree import ElementTree as ET

needle = sys.argv[2]
visible = list(ET.parse(sys.argv[1]).iter('node'))
nodes = [n for n in visible if n.get('text') == needle] + [n for n in visible if n.get('text', '').startswith((needle + ', ', needle + '\n'))]
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
  device shell input tap "$x" "$y"
}

screen_dir="$RUNNER_TEMP/dana-screens"
mkdir -p "$screen_dir"
screenshot() {
  device exec-out screencap -p > "$screen_dir/$1.png"
  test -s "$screen_dir/$1.png"
}

await_ui 'سلام، من دانام.' 'The redesigned Studio home did not render.'
await_ui 'میز کار دانا' 'The home shortcuts are missing.'
screenshot home
tap_text 'حساب'
await_ui 'ثبت‌نام با ایمیل' 'The email account screen is missing.'
await_ui 'پیکربندی ثبت‌نام' 'A clean build did not expose the email service setup.'
screenshot account
tap_text 'پیکربندی ثبت‌نام'
await_ui 'نشانی پروژهٔ Supabase' 'The email service setup did not open.'
device shell input keyevent KEYCODE_BACK
await_ui 'حساب دانا' 'The account page did not resume.'
tap_text 'موتورها'
await_ui 'مرکز اتصال موتورها' 'The Engine Hub did not open.'
await_ui 'راه‌اندازی موتور رایگان' 'The free provider setup is missing.'
screenshot engines
tap_text 'راه‌اندازی موتور رایگان'
await_ui 'کلید شخصی OpenRouter' 'The OpenRouter Free preset did not open.'
await_ui 'openrouter/free' 'The free-only model is missing.'
# A fixture key is stored without authorizing the network or contacting OpenRouter.
tap_text 'کلید شخصی OpenRouter'
device shell input text 'sk-or-v1-dana-ci-offline-fixture'
device shell input keyevent KEYCODE_BACK
await_ui 'ذخیرهٔ موتور' 'The preset save control is missing.'
tap_text 'ذخیرهٔ موتور'
await_ui 'موتور ذخیره شد' 'The offline engine setup could not store the profile.'
tap_text 'خانه'
await_ui 'مقایسه' 'The home comparison shortcut is missing.'
tap_text 'مقایسه'
await_ui 'یک سؤال، چند پاسخ' 'The comparison screen did not open.'
await_ui 'حالت کاملاً آفلاین روشن است' 'Engine setup disturbed the offline guard.'

# Use an incoming user share to fill the draft exactly as Android does. It must not send or save automatically.
launch "-n $activity -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT 'note DANA_SMOKE_LOCAL'"
await_ui 'DANA_SMOKE_LOCAL' 'The shared text did not appear in the editable composer.'
tap_text 'بازبینی'
await_ui 'این کار را انجام دهیم؟' 'Local command bypassed or failed to open the review.'
tap_text 'تأیید و اجرا'
await_ui 'یادداشت روی گوشی ذخیره شد' 'The approved offline note was not saved.'
tap_text 'خانه'
await_ui 'یادداشت‌ها' 'The local notes shortcut is missing.'
tap_text 'یادداشت‌ها'
await_ui 'DANA_SMOKE_LOCAL' 'The approved offline note is absent from the notes repository.'
device shell pidof "$package" >/dev/null || fail 'Dana terminated during the smoke test.'
printf '::notice title=Dana installed APK::New home, account setup, free-engine setup, offline guard and approved note passed. No real email or AI credentials used.\n'

# Exercise the APK's launch on a fresh install before the instrumentation runner can alter the UI/task state.
device shell am force-stop "$package"
if ! timeout --kill-after=8s 360s ./gradlew --no-daemon --console=plain :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest 2>&1 | tee "$RUNNER_TEMP/dana-device.log"; then
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

for module, minimum in [('core/data', 8), ('app', 2)]:
    path = Path(module) / 'build/outputs/androidTest-results'
    reports = list((path / 'connected/debug').rglob('TEST-*.xml')) or list(path.rglob('TEST-*.xml'))
    cases = [case for report in reports for case in ET.parse(report).iter('testcase')]
    if len(cases) < minimum or any(case.find('skipped') is not None or case.find('failure') is not None or case.find('error') is not None for case in cases):
        raise SystemExit(f'::error title=Dana device tests::{module}: expected at least {minimum} passing tests with no failures/skips.')
    print(f'{len(cases)} Android tests in {module} executed successfully')
PYTHON
