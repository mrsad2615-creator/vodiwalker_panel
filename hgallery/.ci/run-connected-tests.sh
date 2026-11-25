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
# Screenshots are written to Pictures, which MediaStore indexes, so every capture
# became a gallery item: the app photographed itself, the grid filled with shots of
# its own toolbar, and the Photos count read "24 photos and 2 videos" when the
# fixtures are 8 and 2. They stay in Pictures because the *fixtures* must stay in a
# scanned volume — moving them to /data/local/tmp made the app show "No photos yet",
# since that path is outside every volume MediaStore scans. The fix is a .nomedia
# marker in this one directory, which is what it is for: files remain readable by
# `adb pull`, indexed by nothing.
DEVICE_DIR="/sdcard/Pictures/$ALBUM_NAME"
# Fixtures are media the app must find, so they live outside the marked directory:
# .nomedia hides every descendant, not just its own level, and when the fixtures
# were pushed inside $DEVICE_DIR the library came up empty.
FIXTURE_DIR="/sdcard/Pictures/hgallery-fixtures"
PKG="${HGPKG:-dev.hgallery.app.debug}"
ACTIVITY="dev.hgallery.app.MainActivity"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/diag"
# `adb pull` recreates the device folder name, so this is the only place the
# PNGs can end up. Creating it explicitly means they land at a known path.
mkdir -p "$OUT_DIR/$ALBUM_NAME"

# Fixture media, as a function, because its position in the file matters.
#
# This block used to sit after the instrumented test run, so the tests ran against an
# empty MediaStore: every test that needed a photograph returned early and reported
# success, and the player tests could not reach a video. The seeding and the `.nomedia`
# marker are therefore two functions called in an explicit order below -- media first,
# tests second, screenshots third -- rather than a long script whose order is implied by
# where the lines happen to be.
seed_fixture_media() {
  # Fixtures are split across two device folders so the Albums tab has more than
  # one bucket to draw, and their EXIF dates differ so the timeline must group
  # them into separate day sections rather than collapsing everything into Today.
  echo "== pushing fixture media so the timeline has real content =="
  # Remove the folder earlier revisions of this script left in Pictures. On a reused
  # emulator those captures stay in MediaStore and would keep appearing as library
  # items, which is the artefact this change exists to remove.
  adb shell rm -rf "/sdcard/Pictures/hgallery-shots" >/dev/null 2>&1
  adb shell rm -rf "$DEVICE_DIR" >/dev/null 2>&1
  adb shell rm -rf "$FIXTURE_DIR" >/dev/null 2>&1
  # Created here, after the purge above and before the first screencap: an earlier
  # revision created it before this same rm -rf and the marker was deleted a few lines
  # later, which is why a run with the fix in place still photographed itself.
  adb shell mkdir -p "$DEVICE_DIR" >/dev/null 2>&1
  adb shell touch "$DEVICE_DIR/.nomedia" >/dev/null 2>&1
  if adb shell ls -a "$DEVICE_DIR" 2>/dev/null | grep -q nomedia; then
    echo "::notice title=e2e::.nomedia is in place, so captures stay out of the library"
  else
    echo "::error title=e2e::.nomedia could not be created; every screenshot will be indexed"
  fi
  adb shell mkdir -p "$FIXTURE_DIR/Camera" >/dev/null 2>&1
  adb shell mkdir -p "$FIXTURE_DIR/Screenshots" >/dev/null 2>&1
  # Real photographs, not flat test cards. The earlier fixtures were solid colour
  # blocks with a white circle, which made every screenshot of the app look like a
  # prototype regardless of how the layout underneath was built.
  n=0
  for f in .ci/fixtures/real/*.jpg; do
    n=$((n + 1))
    if [ $((n % 4)) -eq 0 ]; then
      adb push "$f" "$FIXTURE_DIR/Screenshots/" >/dev/null 2>&1
    else
      adb push "$f" "$FIXTURE_DIR/Camera/" >/dev/null 2>&1
    fi
  done
  # Two real H.264/AAC clips so the Albums tab has a Videos bucket and the player
  # has something to actually decode.
  for f in .ci/fixtures/real/*.mp4; do
    [ -f "$f" ] || continue
    adb push "$f" "$FIXTURE_DIR/Camera/" >/dev/null 2>&1
  done
  # MediaStore only notices new files once someone asks it to index them.
  adb shell content call --uri content://media/external --method scan_volume --arg external_primary >/dev/null 2>&1
  sleep 6
  adb shell content query --uri content://media/external/images/media --projection _id 2>/dev/null | wc -l | tee "$OUT_DIR/diag/mediastore-count.txt"

  echo "== spreading date_taken across three days so grouping is exercised =="
  # `date +%s` through `adb shell` runs in the *device's* toybox, which cannot do
  # 64-bit shell arithmetic: the multiplication wrapped and every fixture landed on
  # 1970-01-01, which then rendered as the wrong year in date headers. Compute the
  # millisecond clock in the host shell and pass the number through.
  NOW_MS=$(( $(date +%s) * 1000 ))
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
}

seed_fixture_media

echo "== instrumented UI tests =="
# The exit status must be Gradle's, not tee's.
#
# This was `./gradlew ... | tee ui-test.log` followed by `TEST_STATUS=$?`, which reports
# tee's status and is therefore always 0. In POSIX sh there is no `pipefail`, so the
# value could not have been anything else. `TEST_STATUS` is the exit status of this whole
# script, so every instrumented failure -- a crashing screen, an assertion that did not
# hold, a test that never compiled -- was reported as a green run. The log was captured;
# the failure was not.
#
# `set -o pipefail` is not available in sh, and the workflow runs this file with `sh`, so
# the status is taken from the log instead: AGP writes a failing test into ui-test.log as
# "FAILED" and exits non-zero, and that line is what this checks for.
./gradlew --console=plain --no-daemon :app:connectedDebugAndroidTest 2>&1 | tee ui-test.log
TEST_STATUS=0
if grep -qE "^FAILED |FAILURE: Build failed|Tests on .* failed" ui-test.log; then
  TEST_STATUS=1
fi
# Report the outcome per class, not only the aggregate. An aggregate line says "5 tests
# failed" without saying which, and an absent line is ambiguous between "this class
# passed" and "this class never ran" -- both of which happened in the run that first made
# this visible. `--tests` results appear as one line per executed test.
echo "connectedDebugAndroidTest: $TEST_STATUS (0 = passed)"
grep -E "^[A-Za-z0-9_.]+ > [A-Za-z0-9_]+.*(FAILED|PASSED)" ui-test.log | while IFS= read -r line; do
  echo "::notice title=e2e::$line"
done
# One line per class stating how many of its tests were seen, so a class that ran zero
# tests is visibly different from a class whose tests passed.
for CLASS in $(grep -oE "dev\.hgallery\.app\.[A-Za-z]+Test" ui-test.log | sort -u); do
  RAN=$(grep -cE "^${CLASS} > " ui-test.log || true)
  FAILED=$(grep -E "^${CLASS} > " ui-test.log | grep -c "FAILED" || true)
  echo "::notice title=e2e::$CLASS: $RAN reported, $FAILED failed"
done

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
  # Check the foreground before the capture, not after. A screenshot of another app is
  # worse than a missing screenshot: it gets reviewed as if it proved something. This
  # is how the launcher's home screen ended up in five evidence slots for several runs.
  #
  # `Pop-Up Window` is allowed because it is ours. A `DropdownMenu` or a dialog gets its
  # own window, and that window becomes mCurrentFocus with a name that carries no
  # package. Rejecting it made every shot after the overflow menu opened fail, which
  # called the restart in assert_app_foreground, which then made the month, selection
  # and delete sections report "app not in front". The bot deleted three evidence files
  # as a result -- a gate that rejects the app's own dialog is not a strict gate.
  FOCUS_NOW=$(adb shell dumpsys window 2>/dev/null | grep -E "mCurrentFocus" | head -1)
  case "$FOCUS_NOW" in
    *"$PKG"*|*"Pop-Up Window"*) ;;
    *)
      echo "::error title=e2e::refusing to save $1: the app is not in front"
      echo "::error title=e2e::focus is: ${FOCUS_NOW:-unknown}"
      # Return, do not fall through. The old branch printed the error and then took
      # the screenshot anyway; with a caller that tests the exit status, silently
      # proceeding produced a numbered slot in the evidence with no file behind it,
      # which is how 40 and 41 were once cited and never existed.
      return 1
      ;;
  esac
  adb shell screencap -p "$DEVICE_DIR/$1" || return 1
  # Verify the capture landed. `screencap` can exit 0 and write nothing when the
  # surface it is reading is mid-destruction, and a caller that believes it
  # succeeded leaves a numbered gap in the evidence with no explanation.
  if ! adb shell test -s "$DEVICE_DIR/$1"; then
    echo "::error title=e2e::$1 was requested but the file is empty or absent"
    return 1
  fi
  echo "captured $1"
}

# Tap a row of the overflow menu.
#
# `uiautomator dump` returns an empty hierarchy while a pop-up window holds focus,
# which is what a `DropdownMenu` is: the run that added the menu produced "labels on
# screen: none" and then refused every following screenshot. So this tries the dump
# first, and falls back to geometry taken from the trigger's own bounds.
#
# The fallback is not a guess at the screen: the menu is anchored to the trigger, its
# rows are `hgSpacing.minTouch` tall in the app's own layout, and the first row starts
# one `hgSpacing.floatGap` below the trigger. It taps the horizontal centre of the
# trigger, which is inside the menu when it opens below and left of it. If the app's
# row height or padding changes, this offset must change with it -- and the menu
# screenshots in the run are what reveal that.
ANCHOR_CX=""
ANCHOR_BOTTOM=""

tap_menu_row() {
  TAG="$1"; PATTERN="$2"; ROW="$3"
  BOUNDS=$(node_centre "$PATTERN")
  if [ -z "$BOUNDS" ]; then
    echo "::notice title=e2e::the menu dump is empty, using the trigger geometry for $TAG"
    if [ -z "$ANCHOR_BOTTOM" ]; then
      echo "::error title=e2e::no trigger geometry recorded for the menu"
      return 1
    fi
    DENS=$(adb shell wm density | tr -dc '0-9')
    : "${DENS:=320}"
    STEP=$(( 48 * DENS / 160 ))
    GAP=$(( 8 * DENS / 160 ))
    CX="$ANCHOR_CX"
    CY=$(( ANCHOR_BOTTOM + GAP + ROW * STEP + STEP / 2 ))
    echo "::notice title=e2e::tapping menu row $ROW at ($CX,$CY) below trigger bottom $ANCHOR_BOTTOM"
    adb shell input tap "$CX" "$CY"
    sleep 3
    return 0
  fi
  set -- $BOUNDS
  CX=$(( ($1 + $3) / 2 )); CY=$(( ($2 + $4) / 2 ))
  echo "::notice title=e2e::tapped menu row $PATTERN at ($CX,$CY)"
  adb shell input tap "$CX" "$CY"
  sleep 3
  return 0
}

# Long-press the first node whose label matches, using its own bounds.
long_press_tag_or_text() {
  TAG="$1"; PATTERN="$2"
  dump_ui "$TAG"
  BOUNDS=$(node_centre "$PATTERN")
  if [ -z "$BOUNDS" ]; then
    echo "::warning title=e2e::no node matching $PATTERN to long-press"
    return 1
  fi
  set -- $BOUNDS
  CX=$(( ($1 + $3) / 2 )); CY=$(( ($2 + $4) / 2 ))
  echo "::notice title=e2e::long-pressed $PATTERN at ($CX,$CY)"
  adb shell input swipe "$CX" "$CY" "$CX" "$CY" 900
  sleep 2
  return 0
}

# The launch sequence, recorded rather than captured.
#
# A still cannot photograph this. `screencap` takes about a second to start on this
# emulator, which is longer than the 900 ms sequence, so two runs in a row produced
# either the window background before Compose drew or the finished grid after the
# cover had lifted. The animation was real in both runs; the camera was simply slower
# than the subject.
#
# So: record the screen, launch the app inside the recording, pull the video and take
# three frames out of it. The frames are the sequence, not a re-enactment of it.
#
# If either `screenrecord` or `ffmpeg` is unavailable the step warns and moves on. It
# must not fail the run: the launch animation is verified by `IgnitionTest` on every
# run, and this is the picture to go with it.
echo "== recording the launch sequence =="
# Motion has to be switched on for this step, and this is the reason none of the
# recordings worked before: the workflow starts the emulator with
# `disable-animations: true`, which sets all three animation scales to 0. The
# application reads the platform scale, as it must, so `rememberReduceMotion()`
# returns true and the launch sequence collapses to an instant state change. That is
# correct behaviour on a device with animations off, and it means the CI emulator has
# never run a single frame of animation in any run so far.
#
# Turned on here, for this step only, and put back afterwards: leaving them on would
# change the timing of every test that follows and make the run flaky in a way that
# has nothing to do with the product.
for scale in animator_duration_scale transition_animation_scale window_animation_scale; do
  adb shell settings put global "$scale" 1 >/dev/null 2>&1
done
sleep 1

LAUNCH_MP4="$DEVICE_DIR/launch.mp4"
adb shell rm -f "$LAUNCH_MP4" >/dev/null 2>&1
adb shell am force-stop "$PKG"
# Background the recorder, then launch into it. `screenrecord` caps at 3 minutes and
# stops on its own; 6 seconds is enough for the launch plus the settle.
adb shell "screenrecord --time-limit 6 --size 320x640 --bit-rate 2000000 $LAUNCH_MP4" \
  >/dev/null 2>&1 &
RECORDER=$!
sleep 1
adb shell am start -n "$PKG/$ACTIVITY" > /dev/null 2>&1
wait $RECORDER 2>/dev/null || true
sleep 1
adb pull "$LAUNCH_MP4" "$OUT_DIR/launch.mp4" >/dev/null 2>&1

# `screenrecord` on the Android emulator writes raw H.264 in an MP4 container, and
# this container is readable enough that the recording is kept as the evidence of
# motion once it exists. It is pulled to `ui-shots` and the workflow copies only
# `*.png` into `evidence/`, so a run that records and cuts no frames would leave no
# trace. There is a fallback for that below.
if [ -s "$OUT_DIR/launch.mp4" ]; then
  echo "::notice title=e2e::recorded the launch to launch.mp4 ($(wc -c < "$OUT_DIR/launch.mp4") bytes)"
else
  echo "::warning title=e2e::screenrecord produced no file"
fi

# The frames are cut here only if ffmpeg exists. It does not exist on the Ubuntu
# runner image this workflow uses, and installing it from the network is not
# something a build step should do to take a picture. So the fallback is a still
# taken with `screencap` *while* the sequence is running: launch, then capture
# immediately, at the moment the animation scale is 1 so the cover lasts 900 ms
# rather than one frame. That is what makes the still possible at all -- the earlier
# attempts failed because animations were off, not because the camera was slow.
FRAMES=0
if [ -s "$OUT_DIR/launch.mp4" ] && command -v ffmpeg >/dev/null 2>&1; then
  for pair in "0.30 44-launch-ignition" "0.55 45-launch-aperture" "0.80 46-launch-lift"; do
    set -- $pair
    ffmpeg -y -loglevel error -ss "$1" -i "$OUT_DIR/launch.mp4" -frames:v 1 \
      "$OUT_DIR/$2.png" 2>/dev/null && FRAMES=$((FRAMES + 1))
  done
  [ "$FRAMES" -gt 0 ] && echo "::notice title=e2e::cut $FRAMES launch frames from the recording"
fi
if [ "$FRAMES" -eq 0 ]; then
  # Two attempts. `screencap` takes about a second to start on this emulator, which is
  # longer than the 900 ms sequence, so a single attempt is a coin flip even with
  # animations on. Two relaunches halve the chance of a blank evidence folder; they do
  # not remove it.
  #
  # A missing still is a warning, not a failure, and that is a deliberate limit: the
  # animation is verified on every run by IgnitionTest, which asserts the cover exists,
  # leaves the tree and does not replay on rotation. This step is the picture to go
  # with that, and a picture that a slow camera misses is not a product defect.
  echo "::notice title=e2e::ffmpeg is absent, so the launch is captured as a still"
  # The sequence is launched at four times its product length, because `screencap`
  # takes about a second to start and the product sequence is 0.9 s. The extra is read
  # by `HGalleryApp` and passed to `HgIgnition`; the product never sets it and no
  # release build has it in its launch path.
  #
  # The file name carries the factor. A 3.6-second launch must not be read as the
  # product's own timing, and a comment inside a commit message is not where someone
  # looking at a screenshot will find that out.
  for attempt in 1 2; do
    adb shell am force-stop "$PKG"
    adb shell am start -n "$PKG/$ACTIVITY" --ef hgallery.capture.ignitionScale 4 > /dev/null 2>&1
    sleep 1
    # The scale the application actually read, before judging the picture. Without this
    # line a failed capture is ambiguous between "the extra did not arrive" and "the
    # camera is too slow", and the two have different fixes.
    adb logcat -d -t 200 2>/dev/null | grep "HGalleryIgnition" | tail -1 \
      | while IFS= read -r line; do echo "::notice title=e2e::$line"; done
    adb shell screencap -p "$DEVICE_DIR/44-launch-sequence-4x.png" >/dev/null 2>&1
    if adb shell test -s "$DEVICE_DIR/44-launch-sequence-4x.png"; then
      # Decide from the pixels, not from the file size.
      #
      # File size was tried first and pointed the wrong way: the frame that caught the
      # mark was 14,449 bytes and the frame that caught an empty cover was 22,165. The
      # status bar's clock and the emulator's own noise dominate the size at this scale.
      #
      # `png_accent_pixels.py` decodes the PNG with the standard library -- no Pillow,
      # no ffmpeg -- and answers one question: is this a flat plate that carries the
      # mark? Both halves are needed. Hue alone counts 14,045 accent pixels in the photo
      # grid, because sunsets are orange. Flatness alone accepts a blank cover. Together
      # they separated all three measured frames correctly.
      adb pull "$DEVICE_DIR/44-launch-sequence-4x.png" "$OUT_DIR/44-launch-sequence-4x.png" >/dev/null 2>&1
      if VERDICT=$(python3 .ci/png_accent_pixels.py "$OUT_DIR/44-launch-sequence-4x.png" --launch); then
        echo "::notice title=e2e::$VERDICT"
        echo "captured 44-launch-sequence-4x.png"
        break
      fi
      echo "::notice title=e2e::attempt $attempt: ${VERDICT:-could not read the frame}, retrying"
      adb shell rm -f "$DEVICE_DIR/44-launch-sequence-4x.png"
      rm -f "$OUT_DIR/44-launch-sequence-4x.png"
    fi
    [ "$attempt" -eq 2 ] && echo "::warning title=e2e::the launch cover was not captured"
  done
fi

# Motion off again, before the tests and the rest of the captures.
for scale in animator_duration_scale transition_animation_scale window_animation_scale; do
  adb shell settings put global "$scale" 0 >/dev/null 2>&1
done
# Let the sequence finish before anything taps.
sleep 2

shot 01-photos.png

# The screen size, for the taps below that must land anywhere but a control.
SIZE=$(adb shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p')
W=$(echo "$SIZE" | cut -d' ' -f1)
H=$(echo "$SIZE" | cut -d' ' -f2)
[ -z "$W" ] && W=1080 && H=1920
echo "screen ${W}x${H}"

# Tap a bottom-bar destination by the name it announces, never by a coordinate.
#
# Every tap on the bar used to be `$((H - 55))`, which is inside the system
# navigation area on a gesture-navigation device. It only ever worked because the
# bar had no navigationBarsPadding() of its own and its content was drawn under the
# inset. Fixing the bar moved its labels up, and this is the same fix on the harness
# side: ask the hierarchy where the destination is.
tap_tab() {
  TAB="$1"
  if tap_tag_or_text "tab-$TAB" "content-desc=\"$TAB\""; then
    return 0
  fi
  echo "::error title=e2e::the $TAB destination was not addressable in the bottom bar"
  return 1
}

# Dump the live view hierarchy once, into the artifacts directory.
# Write the current view hierarchy to /sdcard/ui.xml, and keep a copy for the run.
#
# `uiautomator dump` waits for the window to be idle and gives up silently when it
# is not: a playing video surface never settles, and the command then writes nothing.
# One attempt is what the harness used to make, so a single unlucky moment looked
# exactly like "the control is not there". The evidence for 40 and
# 41-video-second-clip disappeared for that reason on one run and came back on the
# next with no code change in between.
#
# Three attempts, and an explicit error when the tree is empty, so the next failure
# names itself instead of turning into a silent "labels on screen: none".
dump_ui() {
  attempt=0
  while [ "$attempt" -lt 3 ]; do
    attempt=$((attempt + 1))
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb shell cat /sdcard/ui.xml > "$OUT_DIR/diag-$1.xml" 2>/dev/null
    if [ -s "$OUT_DIR/diag-$1.xml" ] && grep -q 'content-desc\|bounds' "$OUT_DIR/diag-$1.xml"; then
      [ "$attempt" -gt 1 ] && echo "::notice title=e2e::the UI tree took $attempt attempts for $1"
      return 0
    fi
    sleep 2
  done
  echo "::error title=e2e::the UI tree was empty after $attempt attempts for $1"
  return 1
}

# Centre of the first node whose resource-id or content-desc matches $1.
node_centre() {
  adb shell cat /sdcard/ui.xml 2>/dev/null | tr '>' '\n' \
    | grep -E "$1" \
    | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' \
    | head -1
}

# Fail loudly if the app is no longer in front.
#
# This exists because it was not here. One wrong tap in the delete section pressed
# BACK with no dialog open, which exited the app; the next tap landed on the launcher's
# Calendar icon. From that point every screenshot showed Google Calendar, and the run
# reported success with a folder of pictures of the wrong app -- twice, which is why
# the video and favourite sections had no evidence for several builds.
#
# The check costs one dumpsys and turns silent garbage into a named failure.
assert_app_foreground() {
  WHERE="$1"
  FOCUS_NOW=$(adb shell dumpsys window 2>/dev/null | grep -E "mCurrentFocus" | head -1)
  case "$FOCUS_NOW" in
    # A dialogs and pop-up menus are our windows too: see the note in shot().
    *"$PKG"*|*"Pop-Up Window"*) return 0 ;;
  esac
  echo "::error title=e2e::the app left the foreground before $WHERE"
  echo "::error title=e2e::focus is: ${FOCUS_NOW:-unknown}"
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/$ACTIVITY" >/dev/null 2>&1
  sleep 5
  return 1
}

# Scroll a horizontal row until a node appears, then leave it where it can be tapped.
#
# The chip row is a real horizontal scroll: "Large" sits off the right edge until it
# is swiped. A single fixed swipe was a guess and it guessed wrong — the row moved
# but Large was still clipped, so the run reported "not on screen after swiping"
# while the label list plainly showed the row had scrolled. This dumps the hierarchy
# between attempts and stops as soon as the node exists anywhere on screen.
scroll_to() {
  PATTERN="$1"; ROWY="$2"; ATTEMPT=1
  while [ "$ATTEMPT" -le 3 ]; do
    dump_ui "scroll-$ATTEMPT"
    if node_centre "$PATTERN" | grep -q .; then
      echo "::notice title=e2e::$PATTERN visible after $ATTEMPT swipe(s)"
      return 0
    fi
    adb shell input swipe $((W * 80 / 100)) "$ROWY" $((W * 15 / 100)) "$ROWY" 300
    sleep 2
    ATTEMPT=$((ATTEMPT + 1))
  done
  dump_ui "scroll-final"
  node_centre "$PATTERN" | grep -q . && return 0
  return 1
}

# The vertical centre of the kind-chip row, read from the hierarchy rather than
# guessed as a fraction of the screen height: adding the row labels moved every row
# down, and the old fraction landed on the label above.
kind_chip_row_y() {
  dump_ui "kind-row"
  Y1=$(node_centre 'content-desc="Photos"' | cut -d' ' -f2)
  Y2=$(node_centre 'content-desc="Photos"' | cut -d' ' -f4)
  if [ -n "$Y1" ] && [ -n "$Y2" ]; then
    echo $(( (Y1 + Y2) / 2 ))
  else
    echo $((H * 20 / 100))
  fi
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
  # Remember the target. A menu opened from this control is anchored to it, and the
  # dump of a pop-up window is empty, so the geometry is the only handle left.
  ANCHOR_CX="$CX"; ANCHOR_BOTTOM="$4"
  echo "::notice title=e2e::tapped $PATTERN at ($CX,$CY) bounds [$1,$2][$3,$4]"
  adb shell input tap "$CX" "$CY"
  sleep 3
  return 0
}

# Type into whatever has focus.
type_text() {
  adb shell input text "$1"
  sleep 2
}

# Tap a destination by name, then photograph it. The four coordinate taps this
# replaces were the same `H - 55` guess as the Albums one above.
tap_tab_and_shot() {
  tap_tab "$1" || true
  sleep 1
  shot "$2"
}

tap_tab_and_shot "Albums" 02-albums.png
tap_tab_and_shot "Highlights" 03-highlights.png
tap_tab_and_shot "Discover" 04-discover.png
tap_tab_and_shot "Photos" 05-photos-again.png

# Open the first thumbnail by its accessibility label. The old coordinate assumed a
# cell under the date header; when the grid's gutter changed the tap landed on empty
# space, the viewer never opened, and the "chrome hidden" shot was the grid.
if tap_tag_or_text "first-photo-viewer" 'content-desc="Photo, '; then
  sleep 4
else
  echo "::error title=e2e::no photo tile was addressable to open the viewer"
fi
shot 06-viewer.png
# A single tap toggles the chrome off; this shot proves the photo can be seen
# without any bars in the way.
adb shell input tap $((W / 2)) $((H / 2))
sleep 3
shot 07-viewer-chrome-hidden.png

# Back out of the viewer, then switch to month view.
# A synthetic two-finger pinch through `sendevent` was tried first and produced a
# 0.0% change: it never reached the app. The switch is the same state change in a
# form that can be driven and therefore checked.
adb shell input keyevent KEYCODE_BACK
sleep 3

# The month switch moved into the overflow menu when four controls on the title
# row squeezed the title to one letter per line. The menu rows announce their own
# labels, so the old pattern would not match the trigger. This opens the menu,
# photographs it, and then taps the row by its label.
#
# The row names the destination, not the current state: when the screen is in day
# view the row reads "Switch to month view", and after the tap it reads the
# opposite. That is why the trigger is matched by the overflow's own label and the
# menu row by the destination.
if tap_tag_or_text "overflow" "content-desc=\"More options\""; then
  sleep 2
  # Row 1 of the menu is the month/day switch: select-all is row 0.
  if tap_menu_row "view-switch" "content-desc=\"Switch to month view\"" 1; then
    sleep 3
  else
    echo "::error title=e2e::the month-view row was not addressable in the menu"
  fi
else
  echo "::error title=e2e::the overflow menu was not addressable"
fi
shot 08-month-view.png

# Back to day view through the same menu, so the selection shots that follow are
# taken in the view the rest of the run assumes.
if tap_tag_or_text "overflow-back" "content-desc=\"More options\""; then
  sleep 2
  if tap_menu_row "view-switch-back" "content-desc=\"Switch to day view\"" 1; then
    sleep 3
  else
    echo "::warning title=e2e::could not return to day view from the menu"
  fi
fi

# Multi-selection, photographed the way a user enters it: a long press on the
# first tile. `input swipe` with equal start and end is a long press on this
# Android version; the duration is what makes it a press rather than a tap.
# Long press the first tile by its label rather than by coordinate.
long_press_tag_or_text "first-photo-select" 'content-desc="Photo, '
sleep 4
shot 09-selection.png

# The delete confirmation must be photographed too: a dialog that reads
# "can be restored" on a device with no trash would be a false claim, and the
# screenshot is how that gets checked.
# Open the confirmation dialog and photograph it, then dismiss with Back: the
# shot proves the dialog exists and that its wording matches the device's real
# delete path.
# By label, not by fraction. The fractions below were measured from an older layout
# and stopped being true when the action bar's height changed; they then selected
# "select all" and finally pressed BACK with no dialog open, which exited the app.
if ! tap_tag_or_text "delete-action" 'content-desc="Delete"'; then
  echo "::error title=e2e::the Delete action was not addressable in selection mode"
fi
sleep 3
shot 10-delete-confirmation.png
adb shell input keyevent KEYCODE_BACK
sleep 3
# The delete section ends with a dialog that may or may not have opened. If it did not,
# that BACK closed the app instead of the dialog.
assert_app_foreground "the view-switch comparison" || true

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
assert_app_foreground "the video section"
# Every tap from here on is by tag, never by coordinate.
tap_tab "Albums" || true
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

    # == the media replacement, which is the path defect A lived on ==
    #
    # Every shot above is the same clip. The player used to build a new ExoPlayer
    # per clip and leave the surface bound to the released one, so the failure
    # only appeared when the item changed: sound continued, picture did not. A
    # screenshot of the first clip cannot show that, however green the run is.
    #
    # The filmstrip under the clip carries both cells with their own labels, so
    # the second one is reachable by name rather than by a guessed coordinate.
    echo "::notice title=e2e::selecting the second clip to exercise the media replacement"
    # The label comes from FilmstripCell, which builds it as "<name>, <seconds>s":
    # "clip-city.mp4, 5s" and "clip-lake.mp4, 6s". It is NOT the grid label, which
    # is "Video, 5 seconds, clip-city.mp4"; the first version of this step copied
    # the grid form, matched nothing, and the run warned honestly instead of
    # silently photographing the clip it already had.
    #
    # Matching the file name is deliberate. The clip on screen is clip-lake, so a
    # pattern that matched any cell would tap the cell already playing.
    if tap_tag_or_text "second-video" 'content-desc="clip-city'; then
      sleep 3
      # Past the auto-hide, then one tap to pin the chrome, because the surface
      # must be photographed with its bars to show which clip is on screen.
      adb shell input tap $((W / 2)) $((H * 30 / 100))
      sleep 1
      if ! shot 40-video-second-clip.png; then
        echo "::warning title=e2e::the second clip was selected but its first shot failed"
      fi
      # A second tap hides the bars again: this is the shot that shows the picture
      # of the NEW clip alone, which is what a released player could not draw.
      adb shell input tap $((W / 2)) $((H * 30 / 100))
      sleep 1
      if ! shot 41-video-second-clip-no-chrome.png; then
        echo "::warning title=e2e::the second clip was selected but its clean shot failed"
      fi
    else
      echo "::warning title=e2e::the filmstrip showed no second clip; the media replacement was not exercised"
    fi

    adb shell input keyevent KEYCODE_BACK
    sleep 2
  else
    echo "::warning title=e2e::the Videos album showed no video tiles"
  fi
else
  echo "::warning title=e2e::Videos album tile not found; player not photographed"
fi

# Search: the magnifier used to be wired to an empty lambda, so this is the first
# run in which the screen it should have opened can be photographed at all.
# Favourites: mark one grid item, then photograph the heart it should draw. The heart
# is decor, so the only honest evidence that it works is to see one on a tile, and the
# only user path to it is the viewer's favourite control.
echo "== photographing favourite overlay =="
assert_app_foreground "the favourites section" || true
# The app must be in front, and on a known screen. force-stop rather than am start:
# `am start` on a running task resumes whatever screen it was left on, and the video
# section leaves an album open. Every tap after that then addressed an album listing
# two clips, which is how this step kept reporting "no photo tile" while the app was
# plainly working. A previous version also pressed BACK unconditionally here, which
# popped the empty search and then the app itself, so the next tap landed on the
# launcher and opened a browser.
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/$ACTIVITY" > /dev/null 2>&1
sleep 4
# Tap the tile by its description rather than by a coordinate: every cell announces
# "Photo, <name>", so the first one is addressable no matter where the grid puts it.
if tap_tag_or_text "first-photo" 'content-desc="Photo, '; then
  sleep 2
  shot 23-viewer-before-favourite.png
  if tap_tag_or_text "viewer-favourite" 'content-desc="Add to favourites"'; then
    sleep 3
    # Android shows its own "Allow this app to modify this file?" dialog, because the
    # fixture photos belong to MediaStore rather than to this app. A user must answer
    # it, so the harness answers it too; without this the write is refused and the
    # heart stays hollow, which is exactly what the first attempt photographed.
    # Android's own dialog for a favourite write: "Allow HGallery to change this
    # photo's favourite status?" with Allow / Don't allow. The wording varies by
    # version, so several spellings are accepted.
    if tap_tag_or_text "allow-modify" 'text="Allow"|text="ALLOW"|text="allow"'; then
      sleep 3
    fi
    # Whatever the screen shows, the reason is in the app's own log lines. The XML
    # dump says what was drawn; it cannot say why the write was refused.
    adb logcat -d -t 400 | grep -E "HGallery(Viewer|Actions)" > "$OUT_DIR/diag/favourite-log.txt" 2>/dev/null
    grep -c . "$OUT_DIR/diag/favourite-log.txt" > /dev/null 2>&1 || true
    while IFS= read -r line; do
      echo "::warning title=favourite::$line"
    done < "$OUT_DIR/diag/favourite-log.txt"
    shot 24-viewer-favourited.png
    # Details reads the row the grid already loaded, so it needs no new permission
    # and the sheet must appear at once. Photographed because a dialog that renders
    # nothing is indistinguishable from a dialog that works, in code.
    if tap_tag_or_text "viewer-details" 'content-desc="Details"'; then
      sleep 2
      shot 26-viewer-details.png
      # The sheet covers the bottom controls; a tap above it dismisses.
      adb shell input keyevent KEYCODE_BACK
      sleep 2
    else
      echo "::warning title=e2e::the Details control was not on screen"
    fi
    # The viewer's dismiss control is "Close"; "Back" was a guess that cost a run.
    if tap_tag_or_text "viewer-close" 'content-desc="Close"'; then
      sleep 2
      # The heart is drawn on the tile; this is the shot that proves it.
      shot 25-photos-favourite-heart.png
    else
      echo "::warning title=e2e::no Back control in the viewer"
    fi
  else
    echo "::warning title=e2e::the favourite control was not on screen in the viewer"
  fi
else
  echo "::warning title=e2e::no photo tile was addressable on the Photos tab"
fi
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/$ACTIVITY" > /dev/null 2>&1
sleep 4

echo "== photographing search =="
assert_app_foreground "the search section" || true
# The app was just force-stopped and relaunched, so it is on the Photos tab with no
# viewer and no album open. Both tabs open search, but the Photos corner holds
# nothing else, so the tap is unambiguous. Do not tap anywhere before this: the
# grid's first cell is a photo tile, and tapping it opens the viewer.
if tap_tag_or_text "search-button" 'content-desc="Search"'; then
  sleep 2
  shot 30-search-empty.png
  type_text "clip"
  shot 31-search-text.png
  # Photos first: "clip" matches two videos, so choosing Photos must drop to one
  # still result. That is the shot that proves the kind axis filters rather than
  # merely highlights.
  if tap_tag_or_text "chip-photos" 'content-desc="Photos"'; then
    sleep 2
    shot 32-search-photos.png
  fi
  if tap_tag_or_text "chip-videos" 'content-desc="Videos"'; then
    sleep 2
    shot 33-search-videos.png
  fi
  # Size and recency live past the first four chips, so the row must be scrolled.
  # The row's height, not a guessed fraction of the screen: the labels above moved
  # everything down, and the previous fraction landed on the row above.
  KIND_Y=$(kind_chip_row_y)
  if scroll_to 'content-desc="Large"' "$KIND_Y" && \
     tap_tag_or_text "chip-large" 'content-desc="Large"'; then
    sleep 2
    # "clip" AND Videos AND Large: the fixtures are 1.5-1.9 MB, so this must find
    # nothing. Photographing the empty state is the proof that the axes AND, not OR.
    shot 34-search-no-results.png
  else
    echo "::warning title=e2e::the Large chip could not be reached"
  fi
  # Same axis, opposite value: Large must be able to become Small. Photographing
  # the swap proves the chips replace rather than accumulate.
  if scroll_to 'content-desc="Small"' "$KIND_Y"; then
    if tap_tag_or_text "chip-small" 'content-desc="Small"'; then
      sleep 2
      shot 36-search-size-swapped.png
    fi
  fi
  adb shell input keyevent KEYCODE_BACK
  sleep 2
  shot 35-after-search.png
else
  echo "::warning title=e2e::search button not found"
fi

# == dark theme ==
#
# The redesign is built from a dark palette, and until this step every screenshot in
# the folder was light mode: the emulator boots light, and nothing ever changed it.
# A palette that has only ever been rendered in one theme is half-tested, whatever
# the tokens say.
#
# The path is a user path, not a test hook: Settings, then the Dark row, then back to
# the library. The app was just relaunched and is on the Photos tab.
echo "== photographing the dark theme =="
# Relaunch before this step. The search section ends with BACK, which closes search
# but can leave the shell where it was, and the first attempt at this step found
# itself on the search screen: "More options" was not there because the header was
# the search field. A force-stop is the only way to be sure which screen the app is
# on, and the step needs the home header.
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/$ACTIVITY" > /dev/null 2>&1
sleep 4
assert_app_foreground "the dark theme section" || true
if tap_tag_or_text "settings-open" "content-desc=\"More options\""; then
  # The overflow rows are a pop-up window, and the dump of a pop-up is empty, so the
  # third row is taken from the trigger's own geometry, as in the month-view step.
  if tap_menu_row "settings-row" "content-desc=\"Settings\"" 2; then
    sleep 2
    if tap_tag_or_text "theme-dark" "content-desc=\"Dark\"|text=\"Dark\""; then
      sleep 2
      shot 42-settings-dark.png
      adb shell input keyevent KEYCODE_BACK
      sleep 3
      # The home screen in dark, which is the shot the design was drawn against.
      shot 43-photos-dark.png
    else
      echo "::warning title=e2e::the Dark theme row was not addressable"
    fi
  else
    echo "::warning title=e2e::the Settings row was not addressable"
  fi
  # Put the theme back. The rest of the harness and the next run assume the light
  # theme, and a device that stays dark makes every later screenshot look wrong.
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/$ACTIVITY" > /dev/null 2>&1
  sleep 3
  if tap_tag_or_text "settings-open-2" "content-desc=\"More options\""; then
    if tap_menu_row "settings-row-2" "content-desc=\"Settings\"" 2; then
      sleep 2
      tap_tag_or_text "theme-light" "content-desc=\"Light\"|text=\"Light\"" || \
        echo "::warning title=e2e::the theme was left on Dark"
      sleep 2
      adb shell input keyevent KEYCODE_BACK
      sleep 2
    fi
  fi
else
  echo "::warning title=e2e::the overflow menu was not addressable for the dark theme"
fi

echo "== pulling screenshots =="
adb pull "$DEVICE_DIR" "$OUT_DIR" >/dev/null 2>&1
find "$OUT_DIR" -name '*.png' -exec ls -l {} \;

exit $TEST_STATUS
