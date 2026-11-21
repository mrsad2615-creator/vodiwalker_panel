#!/bin/sh
# Instrumented tests on the CI emulator, plus real screenshots of the app.
#
# The screenshots are the point: a green test run proves the app did not crash,
# it does not prove the interface looks like Huawei Gallery. These PNGs are the
# evidence that gets compared against the reference captures.
set -u

ALBUM_NAME="hgallery-shots"
OUT_DIR="ui-shots"
DEVICE_DIR="/sdcard/Pictures/$ALBUM_NAME"

mkdir -p "$OUT_DIR"

echo "== clearing any previous shots on the device =="
adb shell rm -rf "$DEVICE_DIR" >/dev/null 2>&1
adb shell mkdir -p "$DEVICE_DIR" >/dev/null 2>&1

echo "== instrumented UI tests =="
./gradlew --console=plain --no-daemon :app:connectedDebugAndroidTest 2>&1 | tee ui-test.log
TEST_STATUS=$?

echo "== launching the app for screenshots =="
adb shell am force-stop dev.hgallery.app.debug
adb shell pm grant dev.hgallery.app.debug android.permission.READ_MEDIA_IMAGES 2>/dev/null
adb shell pm grant dev.hgallery.app.debug android.permission.READ_MEDIA_VIDEO 2>/dev/null
adb shell am start -n dev.hgallery.app.debug/dev.hgallery.app.MainActivity >/dev/null 2>&1
sleep 6

# Name the shots so the comparison script can pair them with a reference.
adb shell screencap -p "$DEVICE_DIR/01-photos.png"
sleep 2

echo "== pulling screenshots =="
adb pull "$DEVICE_DIR" "$OUT_DIR" 2>&1 | tail -2
ls -l "$OUT_DIR/$ALBUM_NAME" 2>/dev/null

# The tests already ran; this script's exit code reflects them, not the shots.
exit $TEST_STATUS
