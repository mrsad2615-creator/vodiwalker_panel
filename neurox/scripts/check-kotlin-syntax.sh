#!/usr/bin/env bash
set -euo pipefail
PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$(cd "$PROJECT/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-$ROOT/.local/tools/python/jdk4py/java-runtime}"
KOTLIN_HOME="${KOTLIN_HOME:-$ROOT/.local/tools/kotlin/node_modules/kotlin-compiler}"
mkdir -p "$PROJECT/test-output"
"$KOTLIN_HOME/bin/kotlinc" "$PROJECT/scripts/tools/KotlinSyntax.kt" -cp "$KOTLIN_HOME/lib/kotlin-compiler.jar" -d "$PROJECT/test-output/syntax.jar"
"$JAVA_HOME/bin/java" -cp "$PROJECT/test-output/syntax.jar:$KOTLIN_HOME/lib/*" KotlinSyntaxKt "$PROJECT"
