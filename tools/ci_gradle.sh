#!/usr/bin/env bash
# ---------------------------------------------------------------------------------------------
# VodiGesture — CI Gradle wrapper with actionable failure reporting
#
# Runs Gradle, tees the full log, and on failure republishes the compiler errors as GitHub
# workflow annotations (`::error::…`). Artifact/workflow-log retrieval is not always possible in
# restricted networks, whereas annotations are always returned by the Checks API, so a red build
# stays diagnosable. The full log is also copied to `android/ci-logs/<slug>.log` for upload.
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

set -o pipefail
./gradlew "$@" 2>&1 | tee "$log"
status="${PIPESTATUS[0]}"

if [ "$status" -ne 0 ]; then
  echo "::group::Diagnostics (${slug})"
  # Kotlin/Java compiler diagnostics, Gradle's own explanation block, and failed tasks.
  grep -nE '(^e: |^w: |error:|FAILURE:|> Task .*FAILED|\* What went wrong|Caused by:|Could not (resolve|find|compile)|Unresolved reference)' "$log" \
    | grep -vE '^w: .*(deprecated|is never used|unused)' \
    | head -n 60 \
    | while IFS= read -r line; do
        # `::error::` messages must be single-line.
        printf '::error::%s\n' "${line//$'\r'/}"
      done
  echo "::endgroup::"

  tail -n 120 "$log"
  cp "$log" "ci-logs/${slug}.log"
  exit "$status"
fi

exit 0
