#!/usr/bin/env bash
#
# Reproducible command line build of MrsadHR without Gradle.
#
# The normal build path is Android Studio / `./gradlew assembleDebug`. This script exists because it
# is the exact toolchain that produced the signed APK shipped with the project, and because it makes
# the build verifiable on a machine with nothing but a JDK, kotlinc, the Android SDK build-tools
# binaries and this repository.
#
#   tools/apk/build_apk.sh [debug|release] [output.apk]
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BUILD="${BUILD_DIR:-$ROOT/build/apk}"
VARIANT="${1:-debug}"
OUT="${2:-$ROOT/dist/mrsadhr-$VARIANT.apk}"

JDK="${JAVA_HOME:-/tmp/jdk/jdk4py/java-runtime}"
ANDROID_JAR="${ANDROID_JAR:-/tmp/sdk/platforms/android-35/android.jar}"
KOTLINC="${KOTLINC:-/tmp/kotlinc/bin/kotlinc}"
KOTLIN_STDLIB="${KOTLIN_STDLIB:-/tmp/kotlinc/lib/kotlin-stdlib.jar}"
AAPT2="${AAPT2:-/tmp/tools/bin/x64/linux/aapt2}"
ZIPALIGN="${ZIPALIGN:-$ROOT/tools/zipalign.py}"
MINAPK="${MINAPK:-/tmp/minapk/tools}"

export JAVA_HOME="$JDK"
export PATH="$JAVA_HOME/bin:$PATH"

APP="$ROOT/app/src/main"
rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$(dirname "$OUT")"

echo "==> 1/7 compile resources"
"$AAPT2" compile --dir "$APP/res" -o "$BUILD/resources.zip"

# AGP 8 takes the package from `namespace`; the command line toolchain still needs it in the
# manifest, so the packaged copy gets it injected here instead of in the source file.
python3 - "$APP/AndroidManifest.xml" "$BUILD/AndroidManifest.xml" <<'PYEOF'
import sys
src, dst = sys.argv[1], sys.argv[2]
text = open(src).read().replace(
    '<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
    '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.mrsadhr">',
    1,
)
open(dst, "w").write(text)
PYEOF

echo "==> 2/7 link resources"
"$AAPT2" link \
  -o "$BUILD/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$BUILD/AndroidManifest.xml" \
  --min-sdk-version 23 \
  --target-sdk-version 35 \
  --version-code 2 \
  --version-name "1.0.1" \
  --java "$BUILD/gen" \
  "$BUILD/resources.zip"

echo "==> 3/7 generate R"
python3 "$ROOT/tools/apk/gen_r.py" "$BUILD/gen/com/mrsadhr/R.java" "$BUILD/gen/R.kt"

echo "==> 4/7 compile kotlin"
"$KOTLINC" \
  $(find "$APP/java" -name "*.kt") "$BUILD/gen/R.kt" \
  -classpath "$ANDROID_JAR" \
  -d "$BUILD/classes.jar" \
  -jvm-target 1.8 \
  -nowarn

echo "==> 5/7 dex"
java -cp "$MINAPK/d8.jar" com.android.tools.r8.D8 \
  --min-api 23 --release \
  --lib "$ANDROID_JAR" \
  --output "$BUILD/dex" \
  "$BUILD/classes.jar" "$KOTLIN_STDLIB"

echo "==> 6/7 package assets, fonts and audio"
ASSETS=()
while IFS= read -r f; do
  rel="${f#$APP/assets/}"
  ASSETS+=("$f:assets/$rel")
done < <(find "$APP/assets" -type f | sort)

rm -f "$BUILD/unaligned.apk" "$BUILD/aligned.apk"
DEX=()
while IFS= read -r f; do
  DEX+=("$f:$(basename "$f")")
done < <(find "$BUILD/dex" -name "classes*.dex" | sort)

python3 "$ZIPALIGN" add "$BUILD/base.apk" "$BUILD/unaligned.apk" \
  "${DEX[@]}" \
  "${ASSETS[@]}"
python3 "$ZIPALIGN" align "$BUILD/unaligned.apk" "$BUILD/aligned.apk"

echo "==> 7/7 sign"
java -jar "$MINAPK/apksigner.jar" sign \
  --ks "$MINAPK/debug.keystore" \
  --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey \
  --min-sdk-version 23 \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT" "$BUILD/aligned.apk"

java -jar "$MINAPK/apksigner.jar" verify --print-certs "$OUT"
"$AAPT2" dump badging "$OUT" | head -5
echo "built $OUT"
