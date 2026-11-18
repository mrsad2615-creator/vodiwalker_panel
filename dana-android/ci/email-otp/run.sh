#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
export DANA_TEST_DB_PASSWORD DANA_TEST_JWT_SECRET
DANA_TEST_DB_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_hex(24))')"
DANA_TEST_JWT_SECRET="$(python3 -c 'import secrets; print(secrets.token_hex(40))')"
compose=(docker compose --project-name dana-email-otp --file compose.yaml)
cleanup() {
  local result=$?
  trap - EXIT
  if ((result != 0)); then
    # Isolated test services receive synthetic data only. Avoid printing their environment variables.
    "${compose[@]}" ps || true
    "${compose[@]}" logs --tail 40 auth || true
    printf '::error title=Dana email integration::A GoTrue/SMTP verification step failed (exit %s).\n' "$result"
  fi
  "${compose[@]}" down -v --remove-orphans || true
  exit "$result"
}
trap cleanup EXIT
"${compose[@]}" up -d --wait --wait-timeout 100
python3 test-flow.py
