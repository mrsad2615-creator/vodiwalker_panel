#!/usr/bin/env bash
# TYPFLOW emulator smoke test — installs the APK, walks every screen with real
# taps, exercises theme + language switching and a typing session, then captures
# screenshots for visual verification.
set -u

PKG="com.typflow.app"
APK="${1:-apk/TYPFLOW-1.1.0.apk}"
OUT="telemetry"

mkdir -p "$OUT"
rm -f "$OUT"/*.png "$OUT"/*.xml "$OUT"/*.txt 2>/dev/null || true

shot() { adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null || true; }

dump() {
  adb shell uiautomator dump "/sdcard/$1.xml" > /dev/null 2>&1 || true
  adb pull "/sdcard/$1.xml" "$OUT/$1.xml" > /dev/null 2>&1 || true
}

swipe_up() {
  local n=${1:-1} i=0
  while [ "$i" -lt "$n" ]; do
    adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 3)) 320 \
      > /dev/null 2>&1 || true
    sleep 1
    i=$((i + 1))
  done
  sleep 1
}

tap_text() {
  local xy x y
  xy=$(python3 scripts/ui_click.py "$OUT/$1.xml" "$2" 2>/dev/null)
  if [ -z "${xy:-}" ]; then
    echo "TAP MISS: $2" >> "$OUT/notes.txt"
    return 1
  fi
  x=${xy%% *}; y=${xy##* }
  echo "TAP $3 -> $x,$y" >> "$OUT/notes.txt"
  adb shell input tap "$x" "$y" > /dev/null 2>&1
  sleep 3
  return 0
}

tap_text_scroll() {
  # $1 = dump base name, $2 = needle, $3 = label
  local i=0
  while [ "$i" -lt 4 ]; do
    dump "$1-$i"
    if tap_text "$1-$i" "$2" "$3"; then return 0; fi
    swipe_up 1
    i=$((i + 1))
  done
  echo "SCROLL TAP MISS: $2" >> "$OUT/notes.txt"
  return 1
}

nav_wait() {
  # $1 = dump base name, $2 = tab index, $3 = label
  local i=0
  while [ "$i" -lt 5 ]; do
    dump "$1-$i"
    if tap_nav "$1-$i" "$2" "$3"; then return 0; fi
    sleep 2
    i=$((i + 1))
  done
  return 1
}

tap_nav() {
  local xy x y
  xy=$(python3 scripts/ui_click.py "$OUT/$1.xml" --nav "$2" 2>/dev/null)
  if [ -z "${xy:-}" ]; then
    echo "NAV MISS: $3" >> "$OUT/notes.txt"
    return 1
  fi
  x=${xy%% *}; y=${xy##* }
  echo "NAV $3 -> $x,$y" >> "$OUT/notes.txt"
  adb shell input tap "$x" "$y" > /dev/null 2>&1
  sleep 3
  return 0
}

echo "== install ==" >> "$OUT/notes.txt"
adb install -r "$APK" >> "$OUT/notes.txt" 2>&1 || { echo "INSTALL FAILED"; exit 1; }
adb shell pm clear "$PKG" > /dev/null 2>&1 || true
adb logcat -c > /dev/null 2>&1

echo "== launch ==" >> "$OUT/notes.txt"
adb shell am start -n "$PKG/.MainActivity" >> "$OUT/notes.txt" 2>&1
sleep 4
shot 01-splash
sleep 2
shot 01b-splash
sleep 4
shot 02-home
dump d02
SIZE=$(python3 scripts/ui_click.py "$OUT/d02.xml" --size 2>/dev/null)
W=${SIZE%% *}; H=${SIZE##* }
echo "size=$SIZE" >> "$OUT/notes.txt"

# ---- main navigation (RTL) ----
tap_nav d02 1 typing
shot 03-typing

dump d03
tap_nav d03 2 reading
shot 04-reading

dump d04
tap_nav d04 3 analytics
shot 05-analytics

dump d05
tap_nav d05 4 profile
shot 06-profile

# ---- settings: theme switch ----
swipe_up 2
tap_text_scroll d06 "تنظیمات" settings
shot 07-settings
tap_text_scroll d07 "SIGNAL" theme-signal
shot 08-theme-signal

# ---- settings scrolled: language switch ----
swipe_up 1
tap_text_scroll d08 "ENGLISH" lang-en
sleep 3
shot 09-english
adb shell input keyevent 4 > /dev/null 2>&1 || true
sleep 2
nav_wait d09 1 home-en
shot 09b-home-en

# ---- English (LTR) typing session with real key input ----
nav_wait d09b 3 typing-en
shot 10-typing-en
dump d10
tap_text d10 "TYPE HERE" typing-field
adb shell input text "the%squick%sbrown%sfox" > /dev/null 2>&1 || true
sleep 2
shot 11-typing-input
adb shell input keyevent 111 > /dev/null 2>&1 || true
sleep 1
adb shell input keyevent 4 > /dev/null 2>&1 || true
sleep 1

nav_wait d11 4 analytics-en
shot 12-analytics-en

nav_wait d12 5 profile-en
swipe_up 2
tap_text_scroll d13 "SETTINGS" settings-en
shot 13-settings-en

# ---- back to Persian and finish on the dashboard ----
swipe_up 2
tap_text_scroll d14 "فارسی" lang-fa
sleep 3
adb shell input keyevent 4 > /dev/null 2>&1 || true
sleep 2
nav_wait d15 1 home-fa
shot 14-back-to-fa

echo "== crash scan ==" >> "$OUT/notes.txt"
if adb logcat -d | grep -q "FATAL EXCEPTION"; then
  echo "CRASH FOUND" >> "$OUT/notes.txt"
  adb logcat -d > "$OUT/logcat.txt" || true
else
  echo "no crash" >> "$OUT/notes.txt"
fi
echo "== done ==" >> "$OUT/notes.txt"
