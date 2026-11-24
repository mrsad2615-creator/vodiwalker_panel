#!/usr/bin/env bash
# Install browser review tools outside application source. Node 22.17+ is required.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TOOLS="$ROOT/.local/tools/design"
mkdir -p "$TOOLS/sysroot"
npm install --prefix "$TOOLS" --no-save --package-lock=false \
  playwright-core@1.65.1 @sparticuz/chromium@158.0.0 axe-core@4.14.1
# The bundled Amazon Linux compatibility libraries also permit this Chromium
# build to run on the sandbox. Do not install them into system directories.
node - "$TOOLS" <<'JS'
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const tools = process.argv[2];
fs.writeFileSync(path.join(tools, 'sysroot/al2023.tar'), zlib.brotliDecompressSync(
  fs.readFileSync(path.join(tools, 'node_modules/@sparticuz/chromium/bin/al2023.tar.br'))));
JS
tar -xf "$TOOLS/sysroot/al2023.tar" -C "$TOOLS/sysroot"
printf '%s\n' 'Browser tools ready. Start a static server for neurox/ on port 4173.' 
printf '%s\n' 'Run from repository root: LD_LIBRARY_PATH="$PWD/.local/tools/design/sysroot/lib" node neurox/scripts/test-design.mjs'
