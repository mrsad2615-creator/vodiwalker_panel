#!/usr/bin/env bash
set -uo pipefail
mkdir -p test-output
./gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest --continue --stacktrace > test-output/android-device.log 2>&1
status=$?
cat test-output/android-device.log
python3 - "$status" <<'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

def emit(level, title, text):
    text = text[-12000:]
    for index in range(0, len(text), 2800):
        escaped = text[index:index + 2800].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
        print(f'::{level} title={title}::{escaped}')

tests = failures = skipped = reports = 0
module_cases = {}
for module in ['app', 'core/data']:
    module_cases[module] = 0
    for path in Path(module, 'build/outputs/androidTest-results').rglob('*.xml'):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        reports += 1
        for case in root.iter('testcase'):
            tests += 1
            module_cases[module] += 1
            if case.find('skipped') is not None:
                skipped += 1
            for kind in ['failure', 'error']:
                error = case.find(kind)
                if error is not None:
                    failures += 1
                    emit('error', 'Android instrumented test', f'{module}: {case.get("classname")}.{case.get("name")}\n' + ''.join(error.itertext()))
emit('notice', 'Android test counts', f'{reports} XML reports; {tests} cases; {failures} failures; {skipped} skipped')
if sys.argv[1] != '0' and failures == 0:
    lines = Path('test-output/android-device.log').read_text(errors='replace').splitlines()
    relevant = [line for line in lines if line.startswith(('e: ', 'FAILURE:', '* What went wrong:', 'ERROR:')) or (line.startswith('> ') and not line.startswith('> Task ')) or 'failed' in line.lower() or 'error:' in line.lower()]
    emit('error', 'Android device runner diagnostics', '\n'.join(relevant)[-12000:] or '\n'.join(lines[-40:]))
if sys.argv[1] == '0' and (failures or tests == skipped or not all(module_cases.values())):
    emit('error', 'Android test evidence incomplete', f'Cannot accept this run: module cases={module_cases}, failures={failures}, skipped={skipped}. Both modules must execute real passing cases.')
    sys.exit(2)
PY
report_status=$?
if [[ "$status" == 0 && "$report_status" != 0 ]]; then exit "$report_status"; fi
exit "$status"
