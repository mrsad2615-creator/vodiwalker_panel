#!/usr/bin/env bash
# ---------------------------------------------------------------------------------------------
# VodiGesture — CI Gradle wrapper with actionable failure reporting
#
# Runs Gradle, tees the full log, and on failure republishes the interesting part of the output as
# GitHub workflow annotations (`::error::…`). Workflow-log and artifact download endpoints are not
# reachable from every environment, whereas annotations are always returned by the Checks API, so
# a red build stays diagnosable. The full log is copied to `android/ci-logs/<slug>.log` too.
#
# Usage: bash tools/ci_gradle.sh <slug> <gradle args...>   (invoke from anywhere)
# ---------------------------------------------------------------------------------------------
set -uo pipefail

slug="${1:?usage: ci_gradle.sh <slug> <gradle args...>}"
shift

# Always operate on the Gradle project, wherever the caller invoked us from.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR/android"

log="/tmp/vodigesture-gradle-${slug}.log"
mkdir -p ci-logs

# Log the exact argv: a stray word-split argument is otherwise very hard to see in CI.
printf 'gradle args (%s):' "$#"
printf ' [%s]' "$@"
printf '\n'

./gradlew "$@" 2>&1 | tee "$log"
status="${PIPESTATUS[0]}"

annotate() {
  # Annotations are single-line and must not start with a space.
  local line="${1//$'\r'/}"
  line="${line#"${line%%[![:space:]]*}"}"
  [ -n "$line" ] && printf '::error::%s\n' "$line"
}

if [ "$status" -ne 0 ]; then
  echo "::group::Diagnostics (${slug})"

  # 1) Compiler diagnostics (Kotlin `e:` / javac `error:`) — the highest-signal lines.
  grep -E '(^e: |error:|Unresolved reference|Type mismatch|Cannot access|cannot be provided)' "$log" \
    | head -n 25 \
    | while IFS= read -r line; do annotate "$line"; done

  # 2) Gradle's own explanation block, including every continuation line.
  awk '
    /^\* What went wrong:/ { capture = 1 }
    capture { print }
    capture && /^\* Try:/ { capture = 0 }
  ' "$log" | head -n 40 | while IFS= read -r line; do annotate "$line"; done

  # 3) Failed tasks and the exception chain.
  grep -E '(^> Task .*FAILED|^Caused by:|^Execution failed for task)' "$log" \
    | head -n 20 \
    | while IFS= read -r line; do annotate "$line"; done

  # 4) The tail, which usually carries the final exception message.
  tail -n 25 "$log" | while IFS= read -r line; do annotate "$line"; done

  echo "::endgroup::"
  cp "$log" "ci-logs/${slug}.log"
  exit "$status"
fi

exit 0
