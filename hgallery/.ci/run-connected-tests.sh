#!/bin/sh
# Instrumented tests on the CI emulator, plus real screenshots of the running app.
#
# A green test run proves the app did not crash. It does not prove the interface
# resembles Huawei Gallery. These PNGs are the evidence that gets measured
# against the reference captures, so the harness must never silently produce a
# picture of the launcher instead of the app.
set -u

ALBUM_NAME="hgallery-shots"
OUT_DIR="ui-shots"
DEVICE_DIR="/sdcard/Pictures/$ALBUM_NAME"
PKG="${HGPKG:-dev.hgallery.app.debug}"
ACTIVITY="dev.hgallery.app.MainActivity"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/diag"

mkdir -p ui-shots/diag
echo "== locating the application APK =="
APK=$(find apk -name '*.apk' 2>/dev/null | head -1)
echo "apk path: ${APK:-<none>}" | tee ui-shots/diag/apk-path.txt
find apk -type f 2>/dev/null | head -10 >> ui-shots/diag/apk-path.txt
if [ -n "$APK" ]; then
  adb install -r -g "$APK" > ui-shots/diag/adb-install.txt 2>&1
  echo "install status: $?"
  cat ui-shots/diag/adb-install.txt
else
  echo "::error title=e2e::no APK was downloaded into apk/"
  exit 1
fi

echo "== instrumented UI tests =="
./gradlew --console=plain --no-daemon :app:connectedDebugAndroidTest 2>&1 | tee ui-test.log
TEST_STATUS=$?
echo "connectedDebugAndroidTest exit status: $TEST_STATUS"

echo "== pushing fixture media so the timeline has real content =="
adb shell rm -rf "$DEVICE_DIR" >/dev/null 2>&1
adb shell mkdir -p "$DEVICE_DIR" >/dev/null 2>&1
for f in .ci/fixtures/*.jpg; do
  adb push "$f" "$DEVICE_DIR/" >/dev/null 2>&1
done
adb shell ls "$DEVICE_DIR" | wc -l
# MediaStore only notices new files once someone asks it to index them.
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$DEVICE_DIR" >/dev/null 2>&1
adb shell content call --uri content://media/external --method scan_volume --arg external_primary >/dev/null 2>&1
adb shell content query --uri content://media/external/images/media --projection _id 2>/dev/null | head -20 > ui-shots/diag/mediastore.txt

echo "== verifying the app is installed =="
if ! adb shell pm list packages | grep -q "$PKG"; then
  echo "::error title=e2e::$PKG is not installed on the emulator"
  adb shell pm list packages | grep -i gallery > ui-shots/diag/packages.txt
  exit 1
fi

echo "== granting media permissions =="
for p in READ_MEDIA_IMAGES READ_MEDIA_VIDEO READ_MEDIA_VISUAL_USER_SELECTED; do
  adb shell pm grant "$PKG" "android.permission.$p" 2>/dev/null
done
adb shell dumpsys package "$PKG" | grep -E "READ_MEDIA|granted=true" | head -10 > ui-shots/diag/permissions.txt

echo "== launching the app =="
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/$ACTIVITY" > ui-shots/diag/am-start.txt 2>&1
cat ui-shots/diag/am-start.txt
sleep 8

FOCUS=$(adb shell dumpsys window 2>/dev/null | grep -E "mCurrentFocus|mFocusedApp" | head -4)
echo "$FOCUS" > ui-shots/diag/focus.txt
echo "$FOCUS"

if ! echo "$FOCUS" | grep -q "$PKG"; then
  echo "::warning title=screenshots::the app never reached the foreground"
  adb shell dumpsys activity activities | head -40 > ui-shots/diag/activities.txt
  adb logcat -d -t 400 > ui-shots/diag/logcat.txt
  adb shell screencap -p "$DEVICE_DIR/99-failed.png"
  adb pull "$DEVICE_DIR" "$OUT_DIR" >/dev/null 2>&1
  exit $TEST_STATUS
fi

shot() {
  adb shell screencap -p "$DEVICE_DIR/$1"
  echo "captured $1"
}

shot 01-photos.png

# The bottom bar has four equal cells. Read the real geometry instead of
# guessing at a device size.
SIZE=$(adb shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p')
W=$(echo "$SIZE" | cut -d' ' -f1)
H=$(echo "$SIZE" | cut -d' ' -f2)
[ -z "$W" ] && W=1080 && H=1920
echo "screen ${W}x${H}"
Y=$((H - 110))

tap_and_shot() {
  adb shell input tap "$1" "$Y"
  sleep 4
  shot "$2"
}

tap_and_shot $((W / 8 * 3)) 02-albums.png
tap_and_shot $((W / 8 * 5)) 03-highlights.png
tap_and_shot $((W / 8 * 7)) 04-discover.png
tap_and_shot $((W / 8)) 05-photos-again.png

echo "== pulling screenshots =="
adb pull "$DEVICE_DIR" "$OUT_DIR" >/dev/null 2>&1
find "$OUT_DIR" -name '*.png' -exec ls -l {} \;

exit $TEST_STATUS
