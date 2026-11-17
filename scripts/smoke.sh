#!/usr/bin/env bash
# TYPFLOW emulator smoke test — installs the APK, walks every screen with real
# taps, exercises theme + language switching and a typing session, then captures
# screenshots for visual verification.
set -u

PKG="com.typflow.app"
APK="${1:-apk/TYPFLOW-1.0.0.apk}"
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
dump d06
tap_text d06 "تنظیمات" settings
shot 07-settings
dump d07
tap_text d07 "NEBULA" theme-nebula
shot 08-theme-nebula

# ---- settings scrolled: language switch ----
swipe_up 2
dump d08
tap_text d08 "English" lang-en
shot 09-english

# ---- English (LTR) typing session with real key input ----
dump d09
tap_nav d09 3 typing-en
shot 10-typing-en
dump d10
tap_text d10 "Type here" typing-field
adb shell input text "the%squick%sbrown%sfox" > /dev/null 2>&1 || true
sleep 2
shot 11-typing-input

dump d11
tap_nav d11 1 analytics-en
shot 12-analytics-en

dump d12
tap_nav d12 4 profile-en
swipe_up 2
dump d13
tap_text d13 "Settings" settings-en
swipe_up 2
dump d14
tap_text d14 "فارسی" lang-fa
shot 13-back-to-fa

# ---- restore the default theme and finish on the dashboard ----
dump d15
for _ in 1 2 3; do
  adb shell input swipe $((W / 2)) $((H / 3)) $((W / 2)) $((H * 3 / 4)) 300 \
    > /dev/null 2>&1 || true
  sleep 1
done
dump d16
tap_text d16 "CYBER FUSION" theme-cyber
shot 14-final-settings

echo "== crash scan ==" >> "$OUT/notes.txt"
adb logcat -d > emu-logcat.txt 2>/dev/null || true
tail -c 40000 emu-logcat.txt > "$OUT/logcat.txt" 2>/dev/null || true
if grep -q "FATAL EXCEPTION" emu-logcat.txt; then
  grep -A 25 "FATAL EXCEPTION" emu-logcat.txt | head -50
  echo "CRASH DETECTED"
  exit 1
fi
echo "NO CRASH"
exit 0
