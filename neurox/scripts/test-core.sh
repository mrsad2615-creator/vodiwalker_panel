#!/usr/bin/env bash
set -euo pipefail
PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$(cd "$PROJECT/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-$ROOT/.local/tools/python/jdk4py/java-runtime}"
COMPILER="${KOTLINC:-$ROOT/.local/tools/kotlin/node_modules/kotlin-compiler/bin/kotlinc}"
if [[ ! -x "$JAVA_HOME/bin/java" || ! -x "$COMPILER" ]]; then
    echo "Missing test tools. Run neurox/scripts/bootstrap-tools.sh, or set JAVA_HOME and KOTLINC." >&2
    exit 2
fi
mkdir -p "$PROJECT/test-output"
mapfile -t SOURCES < <(find "$PROJECT/core/domain/src/main" "$PROJECT/backup/crypto/src/main" "$PROJECT/testing/src" -name '*.kt' | sort)
"$COMPILER" "${SOURCES[@]}" -jvm-target 17 -Werror -include-runtime -d "$PROJECT/test-output/core-tests.jar"
"$JAVA_HOME/bin/java" -jar "$PROJECT/test-output/core-tests.jar" | tee "$PROJECT/test-output/core-tests.txt"
python "$PROJECT/scripts/check-project.py"

python "$PROJECT/scripts/test-migration-sql.py"

python -m unittest discover -s "$PROJECT/testing" -p 'test_*.py'
