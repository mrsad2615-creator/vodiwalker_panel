#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

api="$1"
abi="$2"
package=ai.dana.mrsad.compat
apk="$RUNNER_TEMP/dana-compat/mrsad-lite.apk"
test_apk="$PWD/app/build/outputs/apk/androidTest/compat/debug/app-compat-debug-androidTest.apk"
log="$RUNNER_TEMP/dana-compat-$api.log"

adb_run() { timeout --kill-after=3s "${ADB_LIMIT:-30s}" adb "$@"; }
: > "$log"
fail() {
  printf '%s\n' "$1" >> "$log"
  adb_run logcat -b crash -d -t 24 >> "$log" 2>&1 || true
  python3 - "$log" "$api" <<'PY'
import sys
from pathlib import Path
lines = Path(sys.argv[1]).read_text(errors="replace").splitlines()
important = [line for line in lines if any(s in line for s in ('Failure [', 'Error', 'Exception', 'ai.dana.', 'FAILURES', 'Tests run', 'OK ('))]
text = '\n'.join(dict.fromkeys(important[-36:] + lines[-8:]))[:5100]
text = text.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
print(f'::error title=Android API {sys.argv[2]} compatibility::{text}')
PY
  exit 1
}

[[ "$(adb_run shell getprop ro.build.version.sdk | tr -d '\r')" == "$api" ]] || fail 'Unexpected Android version'
[[ "$(adb_run shell getprop ro.product.cpu.abi | tr -d '\r')" == "$abi" ]] || fail 'Unexpected emulator ABI'
[[ -s "$apk" && -s "$test_apk" ]] || fail "Missing $apk or $test_apk"
if [[ "$abi" == x86_64 ]]; then expected64=true; else expected64=false; fi
adb_run shell svc power stayon true >> "$log" 2>&1 || fail 'Could not keep the screen awake'
adb_run shell input keyevent KEYCODE_WAKEUP || true
adb_run shell wm dismiss-keyguard || true
# Install the same signed APK for both API levels, before installing the separate test APK.
ADB_LIMIT=70s adb_run install -r "$apk" 2>&1 | tee -a "$log" | grep -q '^Success' || fail 'App could not be installed'
printf '::notice title=mrsad APK installed::Package Manager accepted the signed APK on API %s (%s).\n' "$api" "$abi"
# Launch the unmodified APK before loading Hilt's test application.
ADB_LIMIT=50s adb_run shell am start -W -n "$package/ai.dana.MainActivity" 2>&1 | tee -a "$log" | grep -q '^Status: ok' || fail 'MainActivity did not start'
adb_run shell am force-stop "$package" >> "$log" 2>&1 || fail 'Could not reset process before instrumentation'
ADB_LIMIT=70s adb_run install -r -t "$test_apk" 2>&1 | tee -a "$log" | grep -q '^Success' || fail 'Test APK could not be installed'
# Do not rely on shell privileges to change Wi-Fi or airplane mode on Android 8.
# The message test uses the app's offline mode and asserts zero requests with an isolated transport.
classes='ai.dana.ui.CompatibilityInstallTest,ai.dana.media.OfflinePhotoTest,ai.dana.ui.CommandWorkflowTest,ai.dana.ui.HostedWorkspaceTest'
if ! ADB_LIMIT=200s adb_run shell am instrument -w -e class "$classes" -e expectedApi "$api" -e expected64Bit "$expected64" \
    -e expectedHosted "${DANA_BUILD_MANAGED:-false}" \
    "$package.test/ai.dana.WorkspaceTestRunner" 2>&1 | tee -a "$log"; then
  fail 'Instrumentation command failed'
fi
if ! grep -Eq '^OK \(8 tests\)' "$log" || grep -Eq 'FAILURES!!!|INSTRUMENTATION_(FAILED|ABORTED)' "$log"; then
  fail 'Expected eight passing compatibility tests'
fi
printf '::notice title=mrsad compatible APK::Same signed APK installed and passed eight tests on Android API %s (%s), including Command reviews, the Android assistant request, native OCR and isolated email-to-model UI.\n' "$api" "$abi"
