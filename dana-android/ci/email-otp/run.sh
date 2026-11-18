#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
export DANA_TEST_DB_PASSWORD DANA_TEST_JWT_SECRET
DANA_TEST_DB_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_hex(24))')"
DANA_TEST_JWT_SECRET="$(python3 -c 'import secrets; print(secrets.token_hex(40))')"
compose=(docker compose --project-name dana-email-otp --file compose.yaml)
log=$(mktemp)
cleanup() {
  local result=$?
  trap - EXIT
  "${compose[@]}" down -v --remove-orphans || true
  rm -f "$log"
  exit "$result"
}
trap cleanup EXIT

failure() {
  local stage="$1"
  # Retain only synthetic service diagnostics, with the generated test passwords removed.
  "${compose[@]}" ps >> "$log" 2>&1 || true
  "${compose[@]}" logs --tail 14 auth >> "$log" 2>&1 || true
  python3 - "$log" "$stage" <<'PY'
import os
import sys
from pathlib import Path

lines = Path(sys.argv[1]).read_text(errors="replace").splitlines()
for name in ("DANA_TEST_DB_PASSWORD", "DANA_TEST_JWT_SECRET"):
    lines = [line.replace(os.environ[name], "[redacted]") for line in lines]
keyword = ("error", "fatal", "failed", "unhealthy", "manifest", "unexpected HTTP", "no six-digit otp", "invalid")
filtered = [line for line in lines if any(word.lower() in line.lower() for word in keyword)]
message = sys.argv[2] + "\n" + "\n".join(dict.fromkeys(filtered or lines[-16:]))[:3300]
print("::error title=Dana email integration::" + message.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A"))
PY
  exit 1
}
if ! "${compose[@]}" up -d --wait --wait-timeout 100 > "$log" 2>&1; then failure 'Could not start the isolated services.'; fi
printf '\n' >> "$log"
if ! python3 test-flow.py >> "$log" 2>&1; then failure 'The email fixture did not complete.'; fi
tail -1 "$log"
