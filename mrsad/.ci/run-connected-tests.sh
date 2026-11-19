#!/bin/sh
# Instrumented test runner used by CI. Kept as a file (not an inline YAML
# block) so shell quoting inside the emulator action stays predictable.
set -u
LOG=ui-test.log
if ./gradlew --console=plain --no-daemon :app:connectedDebugAndroidTest > "$LOG" 2>&1
then
  echo "===== INSTRUMENTED TESTS PASSED ====="
  grep -E "Tests on|BUILD SUCCESSFUL|tests completed" "$LOG" | tail -5
  exit 0
fi
echo "===== INSTRUMENTED TEST FAILURE ====="
tail -n 120 "$LOG"
echo "===== TEST RESULT SUMMARY ====="
grep -r -h -E "(failure text|AssertionError|tests completed|FAILED)" app/build/outputs/androidTest-results . 2>/dev/null | head -40
echo "===== XML FAILURES ====="
for f in $(find app/build/outputs/androidTest-results -name "*.xml" 2>/dev/null); do
  grep -h -A3 "<failure" "$f" | head -20
done
exit 1
