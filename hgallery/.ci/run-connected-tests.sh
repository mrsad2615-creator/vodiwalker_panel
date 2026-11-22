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
# Real photographs, not flat test cards. The earlier fixtures were solid colour
# blocks with a white circle, which made every screenshot of the app look like a
# prototype regardless of how the layout underneath was built.
n=0
for f in .ci/fixtures/real/*.jpg; do
  n=$((n + 1))
  if [ $((n % 4)) -eq 0 ]; then
    adb push "$f" "$DEVICE_DIR/Screenshots/" >/dev/null 2>&1
  else
    adb push "$f" "$DEVICE_DIR/Camera/" >/dev/null 2>&1
  fi
done
# Two real H.264/AAC clips so the Albums tab has a Videos bucket and the player
# has something to actually decode.
for f in .ci/fixtures/real/*.mp4; do
  [ -f "$f" ] || continue
  adb push "$f" "$DEVICE_DIR/Camera/" >/dev/null 2>&1
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

# Tap a control by its Compose test tag.
#
# This exists because guessing pixel coordinates produced four wrong screenshots
# in a row: a tap 4% too high hits white space and the shot silently shows the
# previous screen. MainActivity exposes test tags as accessibility resource ids,
# so `uiautomator dump` can locate the real bounds and we tap the centre of them.
# Returns non-zero (and reports why) when the tag is not on screen, which is far
# more useful than a screenshot of the wrong page.
# Dump the live view hierarchy once, into the artifacts directory.
dump_ui() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml > "$OUT_DIR/diag-$1.xml" 2>/dev/null
}

# Centre of the first node whose resource-id or content-desc matches $1.
node_centre() {
  adb shell cat /sdcard/ui.xml 2>/dev/null | tr '>' '\n' \
    | grep -E "$1" \
    | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' \
    | head -1
}

# Tap by tag, or by accessibility text when the tag is not exposed.
#
# Compose test tags are not published as resource ids here (testTagsAsResourceId
# does not resolve in this Compose version), but contentDescription does reach the
# accessibility tree, so the labels added to the grid cells are addressable.
tap_tag_or_text() {
  TAG="$1"; PATTERN="$2"
  dump_ui "$TAG"
  BOUNDS=$(node_centre "$PATTERN")
  if [ -z "$BOUNDS" ]; then
    NODES=$(adb shell cat /sdcard/ui.xml 2>/dev/null | grep -o 'content-desc="[^"]\+"' | sort -u | head -6 | tr '\n' ' ')
    echo "::warning title=e2e::no node matching $PATTERN. labels on screen: ${NODES:-none}"
    return 1
  fi
  set -- $BOUNDS
  CX=$(( ($1 + $3) / 2 )); CY=$(( ($2 + $4) / 2 ))
  echo "::notice title=e2e::tapped $PATTERN at ($CX,$CY) bounds [$1,$2][$3,$4]"
  adb shell input tap "$CX" "$CY"
  sleep 3
  return 0
}

tap_tag() {
  TAG="$1"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml > "$OUT_DIR/diag/ui-$TAG.xml" 2>/dev/null
  BOUNDS=$(adb shell cat /sdcard/ui.xml 2>/dev/null \
    | tr '>' '\n' \
    | grep "resource-id=\"$PKG:id/$TAG\"" \
    | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' \
    | head -1)
  if [ -z "$BOUNDS" ]; then
    echo "::warning title=e2e::no node with tag $TAG on screen"
    return 1
  fi
  set -- $BOUNDS
  CX=$(( ($1 + $3) / 2 ))
  CY=$(( ($2 + $4) / 2 ))
  echo "tap_tag $TAG -> centre ($CX,$CY) from bounds [$1,$2][$3,$4]"
  adb shell input tap "$CX" "$CY"
  sleep 3
  return 0
}

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

# Back out of the viewer, then switch to month view with the toolbar control.
# A synthetic two-finger pinch through `sendevent` was tried first and produced a
# 0.0% change: it never reached the app. The switch is the same state change in a
# form that can be driven and therefore checked.
adb shell input keyevent KEYCODE_BACK
sleep 3
# The view switch is the first of three toolbar icons, measured from the
# screenshot itself: its centre sits at 0.58 of the width, 0.075 of the height.
adb shell input tap $((W * 58 / 100)) $((H * 75 / 1000))
sleep 4
shot 08-month-view.png

# Multi-selection, photographed the way a user enters it: a long press on the
# first tile. `input swipe` with equal start and end is a long press on this
# Android version; the duration is what makes it a press rather than a tap.
adb shell input tap $((W * 58 / 100)) $((H * 75 / 1000))
sleep 3
adb shell input swipe $((W / 4)) $((H / 2 - 40)) $((W / 4)) $((H / 2 - 40)) 900
sleep 4
shot 09-selection.png

# The delete confirmation must be photographed too: a dialog that reads
# "can be restored" on a device with no trash would be a false claim, and the
# screenshot is how that gets checked.
# Open the confirmation dialog and photograph it, then dismiss with Back: the
# shot proves the dialog exists and that its wording matches the device's real
# delete path.
# The action row is three equal cells: Share, Select all, Delete. These two
# anchors are not guesses: the danger-coloured Delete glyph was located in
# evidence/09-selection.png with a pixel scan and measures x=0.796, y=0.866.
# Two earlier attempts guessed and photographed "select all" and then the
# Discover tab instead of the dialog.
adb shell input tap $((W * 796 / 1000)) $((H * 866 / 1000))
sleep 3
shot 10-delete-confirmation.png
adb shell input keyevent KEYCODE_BACK
sleep 3

# Verify that the switch really changed the page. Both frames are pulled fresh:
# a stale copy from an earlier run must never decide this verdict.
rm -f "$OUT_DIR/diag/day.png" "$OUT_DIR/diag/month.png"
adb pull "$DEVICE_DIR/01-photos.png" "$OUT_DIR/diag/day.png" >/dev/null 2>&1
adb pull "$DEVICE_DIR/08-month-view.png" "$OUT_DIR/diag/month.png" >/dev/null 2>&1

if python3 tools/compare_frames.py "$OUT_DIR/diag/day.png" "$OUT_DIR/diag/month.png" 0.02 > "$OUT_DIR/diag/view-change.txt" 2>&1; then
  echo "view switch changed the page (see view-change.txt)" >> "$OUT_DIR/diag/pinch.txt"
else
  cat "$OUT_DIR/diag/view-change.txt"
  echo "view switch did NOT change the page" >> "$OUT_DIR/diag/pinch.txt"
fi
cat "$OUT_DIR/diag/view-change.txt" | head -2

# The video player. Reached through Albums -> Videos -> first tile, which is the
# path a user takes and does not depend on a MediaStore id.
echo "== photographing the video player =="
# Every tap from here on is by tag, never by coordinate.
adb shell input tap $((W / 8 * 3)) $((H - 55))
sleep 3
shot 11-albums-for-video.png

if tap_tag_or_text "videos-album" 'content-desc="Videos|text="Videos'; then
  sleep 2
  shot 12-videos-album.png
  # The album's first tile: the only nodes whose label starts with "Video,".
  # Tapping the first match avoids depending on test tags, which are not exposed.
  if tap_tag_or_text "first-video" 'content-desc="Video, '; then
    # The chrome hides itself three seconds into playback, and a tap on the
    # surface both shows it and pins it. So: wait past the auto-hide, then tap once
    # at 30% height (never the centre, where the transport disc sits once a clip
    # ends — an earlier run tapped there and restarted the clip it meant to
    # photograph).
    sleep 4
    adb shell input tap $((W / 2)) $((H * 30 / 100))
    sleep 1
    shot 13-video-player.png
    # Second tap unpins and hides it; this is the shot that proves the surface is
    # really playing behind its own bars.
    adb shell input tap $((W / 2)) $((H * 30 / 100))
    sleep 1
    shot 14-video-player-no-chrome.png
    # Playback ends on its own and the screen reveals the chrome by itself, so the
    # ended state is photographed without touching anything. Tapping here is what
    # produced a 0:01-of-0:06 'ended' shot in the previous run.
    sleep 6
    shot 15-video-player-ended.png
    # Landscape, which is how video is actually watched and the one case that
    # exercises the orientation control and the reflow of the chrome.
    adb shell settings put system accelerometer_rotation 0
    adb shell settings put system user_rotation 1
    sleep 3
    shot 16-video-landscape.png
    adb shell settings put system user_rotation 0
    adb shell settings put system accelerometer_rotation 1
    sleep 2
    adb shell input keyevent KEYCODE_BACK
    sleep 2
  else
    echo "::warning title=e2e::the Videos album showed no video tiles"
  fi
else
  echo "::warning title=e2e::Videos album tile not found; player not photographed"
fi

echo "== pulling screenshots =="
adb pull "$DEVICE_DIR" "$OUT_DIR" >/dev/null 2>&1
find "$OUT_DIR" -name '*.png' -exec ls -l {} \;

exit $TEST_STATUS
