#!/bin/sh
# Instrumented tests on the CI emulator, plus real screenshots of the running app.
#
# A green test run proves the app did not crash. It does not prove the interface
# resembles Huawei Gallery. These PNGs are the evidence that gets measured
# against the reference captures, so the harness must never silently produce a
# picture of the launcher instead of the app.
#
# Order matters: AGP installs its own build of the app for the instrumented
# tests and may leave a differently-signed copy behind. Installing first gave
# INSTALL_FAILED_UPDATE_INCOMPATIBLE, so the tests run first and the screenshot
# build is installed cleanly afterwards.
set -u

ALBUM_NAME="hgallery-shots"
OUT_DIR="ui-shots"
DEVICE_DIR="/sdcard/Pictures/$ALBUM_NAME"
PKG="${HGPKG:-dev.hgallery.app.debug}"
ACTIVITY="dev.hgallery.app.MainActivity"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/diag"
# `adb pull` recreates the device folder name, so this is the only place the
# PNGs can end up. Creating it explicitly means they land at a known path.
mkdir -p "$OUT_DIR/$ALBUM_NAME"

echo "== instrumented UI tests =="
./gradlew --console=plain --no-daemon :app:connectedDebugAndroidTest 2>&1 | tee ui-test.log
TEST_STATUS=$?
echo "connectedDebugAndroidTest exit status: $TEST_STATUS"

echo "== installing the application build for screenshots =="
APK=$(find apk -name '*.apk' 2>/dev/null | head -1)
echo "apk path: ${APK:-<none>}" | tee "$OUT_DIR/diag/apk-path.txt"
if [ -z "$APK" ]; then
  echo "::error title=e2e::no APK was downloaded into apk/"
  exit 1
fi
adb uninstall "$PKG" > "$OUT_DIR/diag/adb-uninstall.txt" 2>&1 || true
adb install -r -g "$APK" > "$OUT_DIR/diag/adb-install.txt" 2>&1
INSTALL_STATUS=$?
cat "$OUT_DIR/diag/adb-install.txt"
echo "install status: $INSTALL_STATUS"
if [ "$INSTALL_STATUS" -ne 0 ]; then
  echo "::error title=e2e::the APK could not be installed"
  adb shell pm list packages | grep -i gallery > "$OUT_DIR/diag/packages.txt"
  exit 1
fi

# Fixtures are split across two device folders so the Albums tab has more than
# one bucket to draw, and their EXIF dates differ so the timeline must group
# them into separate day sections rather than collapsing everything into Today.
echo "== pushing fixture media so the timeline has real content =="
adb shell rm -rf "$DEVICE_DIR" >/dev/null 2>&1
adb shell mkdir -p "$DEVICE_DIR/Camera" >/dev/null 2>&1
adb shell mkdir -p "$DEVICE_DIR/Screenshots" >/dev/null 2>&1
n=0
for f in .ci/fixtures/*.jpg; do
  n=$((n + 1))
  if [ $((n % 4)) -eq 0 ]; then
    adb push "$f" "$DEVICE_DIR/Screenshots/" >/dev/null 2>&1
  else
    adb push "$f" "$DEVICE_DIR/Camera/" >/dev/null 2>&1
  fi
done
# MediaStore only notices new files once someone asks it to index them.
adb shell content call --uri content://media/external --method scan_volume --arg external_primary >/dev/null 2>&1
sleep 6
adb shell content query --uri content://media/external/images/media --projection _id 2>/dev/null | wc -l | tee "$OUT_DIR/diag/mediastore-count.txt"

echo "== spreading date_taken across three days so grouping is exercised =="
NOW_MS=$(($(date +%s) * 1000))
adb shell content query --uri content://media/external/images/media --projection _id 2>/dev/null \
  | sed -n 's/.*_id=\([0-9]*\).*/\1/p' > "$OUT_DIR/diag/media-ids.txt"
IDX=0
TOTAL=$(wc -l < "$OUT_DIR/diag/media-ids.txt")
GROUP=$(( (TOTAL + 2) / 3 ))
[ "$GROUP" -lt 1 ] && GROUP=1
while IFS= read -r MID; do
  [ -z "$MID" ] && continue
  DAY=$((IDX / GROUP))
  NEWMS=$((NOW_MS - DAY * 86400000))
  adb shell content update --uri "content://media/external/images/media/$MID" \
    --bind date_taken:l:"$NEWMS" --bind date_added:s:$((NEWMS / 1000)) >/dev/null 2>&1
  IDX=$((IDX + 1))
done < "$OUT_DIR/diag/media-ids.txt"
echo "retimed $IDX rows into $(( (IDX + GROUP - 1) / GROUP )) day groups" | tee "$OUT_DIR/diag/retime.txt"

echo "== granting media permissions =="
for p in READ_MEDIA_IMAGES READ_MEDIA_VIDEO READ_MEDIA_VISUAL_USER_SELECTED; do
  adb shell pm grant "$PKG" "android.permission.$p" 2>/dev/null
done
adb shell dumpsys package "$PKG" > "$OUT_DIR/diag/package-dump.txt" 2>&1

echo "== launching the app =="
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/$ACTIVITY" > "$OUT_DIR/diag/am-start.txt" 2>&1
cat "$OUT_DIR/diag/am-start.txt"
sleep 10

FOCUS=$(adb shell dumpsys window 2>/dev/null | grep -E "mCurrentFocus|mFocusedApp" | head -4)
echo "$FOCUS" > "$OUT_DIR/diag/focus.txt"
echo "$FOCUS"

if ! echo "$FOCUS" | grep -q "$PKG"; then
  echo "::warning title=screenshots::the app never reached the foreground"
  adb logcat -d -t 600 > "$OUT_DIR/diag/logcat.txt"
  adb shell screencap -p "$DEVICE_DIR/99-failed.png"
  adb pull "$DEVICE_DIR" "$OUT_DIR" >/dev/null 2>&1
  exit $TEST_STATUS
fi

shot() {
  adb shell screencap -p "$DEVICE_DIR/$1"
  echo "captured $1"
}

shot 01-photos.png

# The bottom bar has four equal cells; read the real geometry, never a guess.
SIZE=$(adb shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p')
W=$(echo "$SIZE" | cut -d' ' -f1)
H=$(echo "$SIZE" | cut -d' ' -f2)
[ -z "$W" ] && W=1080 && H=1920
echo "screen ${W}x${H}"
Y=$((H - 55))

tap_and_shot() {
  adb shell input tap "$1" "$Y"
  sleep 4
  shot "$2"
}

tap_and_shot $((W / 8 * 3)) 02-albums.png
tap_and_shot $((W / 8 * 5)) 03-highlights.png
tap_and_shot $((W / 8 * 7)) 04-discover.png
tap_and_shot $((W / 8)) 05-photos-again.png

# Open the first thumbnail to photograph the full-screen viewer. The first cell
# sits just under the date header; tap its centre.
adb shell input tap $((W / 4)) $((H / 2 - 40))
sleep 5
shot 06-viewer.png
# A single tap toggles the chrome off; this shot proves the photo can be seen
# without any bars in the way.
adb shell input tap $((W / 2)) $((H / 2))
sleep 3
shot 07-viewer-chrome-hidden.png

# Back out of the viewer, then pinch out to reach month view. This is the
# behaviour Huawei's manual describes, so it has to be photographed, not assumed.
#
# `input swipe` cannot express two fingers; a pinch needs a real multi-touch
# event stream. `input motionevent` sends a single pointer, so for two fingers
# `sendevent` is the only option — but device node numbers differ between images.
# A pinch that silently does nothing would produce a "day view" screenshot that
# looks like a pass, so the result is checked rather than assumed.
adb shell input keyevent KEYCODE_BACK
sleep 3

PINCH_DEV=$(adb shell getevent -pl 2>/dev/null | sed -n 's/.*add device [0-9]*: \/dev\/input\/event\([0-9]*\).*/\1/p' | head -1)
echo "touch device: event${PINCH_DEV:-<none>}" | tee "$OUT_DIR/diag/pinch.txt"
if [ -n "$PINCH_DEV" ]; then
  DEV=/dev/input/event$PINCH_DEV
  Y1=$((H / 2 - 120)); Y2=$((H / 2 + 120))
  adb shell "sendevent $DEV 3 57 1; sendevent $DEV 3 53 $((W/2-80)); sendevent $DEV 3 54 $Y1; sendevent $DEV 3 58 50; sendevent $DEV 3 57 2; sendevent $DEV 3 53 $((W/2+80)); sendevent $DEV 3 54 $Y2; sendevent $DEV 3 58 50; sendevent $DEV 1 330 1; sendevent $DEV 0 0 0"
  for step in 1 2 3 4 5 6; do
    adb shell "sendevent $DEV 3 53 $((W/2-80-step*25)); sendevent $DEV 3 54 $((Y1-step*25)); sendevent $DEV 3 57 2; sendevent $DEV 3 53 $((W/2+80+step*25)); sendevent $DEV 3 54 $((Y2+step*25)); sendevent $DEV 0 0 0"
  done
  adb shell "sendevent $DEV 3 57 1; sendevent $DEV 3 57 2; sendevent $DEV 1 330 0; sendevent $DEV 0 0 0"
fi
sleep 4
shot 08-month-view.png

# Compare against the day view: if the pinch worked, the grid shows more columns
# and month headers, so the two images must differ.
# Compare the two views with the status bar masked out: a clock that ticked is
# otherwise enough to make "they differ" true and hide a pinch that did nothing.
adb pull "$DEVICE_DIR/01-photos.png" "$OUT_DIR/diag/day.png" >/dev/null 2>&1
adb pull "$DEVICE_DIR/08-month-view.png" "$OUT_DIR/diag/month.png" >/dev/null 2>&1
VERDICT=$(python3 - <<'PYEOF'
from PIL import Image
import numpy as np, sys
try:
    a = np.asarray(Image.open("ui-shots/diag/day.png").convert("RGB")).astype(int)
    b = np.asarray(Image.open("ui-shots/diag/month.png").convert("RGB")).astype(int)
except Exception as exc:
    print("unreadable: %s" % exc); raise SystemExit
h = min(a.shape[0], b.shape[0]); w = min(a.shape[1], b.shape[1])
a, b = a[:h, :w], b[:h, :w]
# mask the status bar (top 6%) and the gesture area (bottom 4%)
top, bottom = int(h * 0.06), int(h * 0.96)
a, b = a[top:bottom], b[top:bottom]
diff = (np.abs(a - b).sum(axis=2) > 24).mean()
print("%.4f %.0f" % (diff, diff * 100))
PYEOF
)
CHANGED=$(echo "$VERDICT" | cut -d' ' -f1)
echo "changed-pixel fraction below the status bar: $CHANGED" >> "$OUT_DIR/diag/pinch.txt"
python3 - "$CHANGED" <<'PYEOF'
import sys
fraction = float(sys.argv[1]) if sys.argv[1] else 0.0
if fraction < 0.02:
    print("::warning title=month view::the pinch did not change the grid (%.1f%% of pixels changed)" % (fraction * 100))
PYEOF

echo "== pulling screenshots =="
adb pull "$DEVICE_DIR" "$OUT_DIR" >/dev/null 2>&1
find "$OUT_DIR" -name '*.png' -exec ls -l {} \;

exit $TEST_STATUS
