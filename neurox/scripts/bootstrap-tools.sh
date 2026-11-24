#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TOOLS="$ROOT/.local/tools"
mkdir -p "$TOOLS"
python -m pip install --target "$TOOLS/python" --upgrade jdk4py==17.0.9.2
npm install --prefix "$TOOLS/kotlin" kotlin-compiler@2.2.21 --no-audit --no-fund
JAVA_HOME="$TOOLS/python/jdk4py/java-runtime" "$TOOLS/kotlin/node_modules/kotlin-compiler/bin/kotlinc" -version
printf '\nInstalled JVM runtime and Kotlin compiler for core tests. Android SDK is NOT installed.\n'
