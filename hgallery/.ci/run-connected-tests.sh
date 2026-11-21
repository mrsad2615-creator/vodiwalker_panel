#!/bin/sh
# Instrumented tests on the CI emulator, plus real screenshots of the app.
#
# The screenshots are the point: a green test run proves the app did not crash,
# it does not prove the interface resembles Huawei Gallery. These PNGs are the
# evidence that gets measured against the reference captures.
set -u

ALBUM_NAME="hgallery-shots"
OUT_DIR="ui-shots"
DEVICE_DIR="/sdcard/Pictures/$ALBUM_NAME"
PKG="dev.hgallery.app.debug"

mkdir -p "$OUT_DIR"

echo "== clearing any previous shots on the device =="
adb shell rm -rf "$DEVICE_DIR" >/dev/null 2>&1
adb shell mkdir -p "$DEVICE_DIR" >/dev/null 2>&1

echo "== instrumented UI tests =="
./gradlew --console=plain --no-daemon :app:connectedDebugAndroidTest 2>&1 | tee ui-test.log
TEST_STATUS=$?
echo "connectedDebugAndroidTest exit status: $TEST_STATUS"

echo "== launching the app for screenshots =="
adb shell am force-stop "$PKG"
adb shell pm grant "$PKG" android.permission.READ_MEDIA_IMAGES 2>/dev/null
adb shell pm grant "$PKG" android.permission.READ_MEDIA_VIDEO 2>/dev/null
adb shell am start -n "$PKG/dev.hgallery.app.MainActivity" >/dev/null 2>&1
sleep 7

shot() {
  adb shell screencap -p "$DEVICE_DIR/$1"
  echo "captured $1"
}

shot 01-photos.png

# The bottom bar is four equal cells. On a 1080-wide screen the centres fall at
# 135 / 405 / 675 / 945 px; the bar sits just above the gesture area.
W=$(adb shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1/p')
H=$(adb shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\2/p')
echo "screen ${W}x${H}"
Y=$((H - 120))

adb shell input tap 405 "$Y"
sleep 3
shot 02-albums.png

adb shell input tap 675 "$Y"
sleep 3
shot 03-highlights.png

adb shell input tap 135 "$Y"
sleep 3
shot 04-photos-again.png

echo "== pulling screenshots =="
adb pull "$DEVICE_DIR" "$OUT_DIR" 2>&1 | tail -2
find "$OUT_DIR" -name '*.png' -exec ls -l {} \;

# Report the test result, not the screenshot step, as this script's outcome.
exit $TEST_STATUS
