#!/usr/bin/env bash
# Surface compiler diagnostics through annotations as well as downloadable logs.
# This is useful when a client cannot reach GitHub's artifact/log storage hosts.
set -uo pipefail
mkdir -p test-output
./gradlew :testing:run :app:assembleDebug :app:lintDebug :core:data:assembleDebugAndroidTest :app:assembleDebugAndroidTest --stacktrace > test-output/android-build.log 2>&1
status=$?
cat test-output/android-build.log
if [[ "$status" != 0 ]]; then
    python3 - <<'PY'
from pathlib import Path
lines = Path('test-output/android-build.log').read_text(errors='replace').splitlines()
important = [line for line in lines if line.startswith(('e: ', 'FAILURE:', '* What went wrong:', '> ', 'ERROR:')) or ' error:' in line or 'error: ' in line]
text = '\n'.join(important)[-12000:] or '\n'.join(lines[-45:])[-12000:]
print('::error title=Android compiler diagnostics::' + text.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))
PY
fi
if [[ "$status" == 0 ]]; then
    python3 - <<'PYREPORT'
from pathlib import Path
import hashlib
import json
for path in Path('core/data/schemas').rglob('*.json'):
    # This is a generated Room schema, not private user data.
    text = json.dumps(json.loads(path.read_text()), separators=(',', ':'))
    chunks = [text[i:i + 2800] for i in range(0, len(text), 2800)]
    for index, chunk in enumerate(chunks, 1):
        message = f'{path}|{index}|{len(chunks)}|' + chunk
        print('::notice title=Room schema chunk::' + message.replace('%', '%25').replace('\n', '%0A'))
for path in Path('app/build/outputs/apk/debug').glob('*.apk'):
    print(f'::notice title=Debug APK evidence::{path.name}; bytes={path.stat().st_size}; sha256={hashlib.sha256(path.read_bytes()).hexdigest()}')
PYREPORT
fi
exit "$status"
