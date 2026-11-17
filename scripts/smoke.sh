#!/usr/bin/env bash
# TYPFLOW emulator smoke test — launches the APK, walks through the screens with
# real taps and captures screenshots for visual verification.
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

tap_nav() {
  # $1 = dump name, $2 = index from the right (0 = home), $3 = label
  local xy x y
  xy=$(python3 scripts/ui_click.py "$OUT/$1.xml" --nav "$2" 2>/dev/null)
  x=${xy%% *}; y=${xy##* }
  if [ -z "${xy:-}" ]; then
    echo "NAV MISS: $3" >> "$OUT/notes.txt"
    return 1
  fi
  echo "NAV $3 -> $x,$y" >> "$OUT/notes.txt"
  adb shell input tap "$x" "$y" > /dev/null 2>&1
  sleep 3
  return 0
}

tap_text() {
  # $1 = dump name, $2 = needle, $3 = label
  local xy x y
  xy=$(python3 scripts/ui_click.py "$OUT/$1.xml" "$2" 2>/dev/null)
  x=${xy%% *}; y=${xy##* }
  if [ -z "${xy:-}" ]; then
    echo "TAP MISS: $2" >> "$OUT/notes.txt"
    return 1
  fi
  echo "TAP $3 -> $x,$y" >> "$OUT/notes.txt"
  adb shell input tap "$x" "$y" > /dev/null 2>&1
  sleep 3
  return 0
}

echo "== install ==" >> "$OUT/notes.txt"
adb install -r "$APK" >> "$OUT/notes.txt" 2>&1 || { echo "INSTALL FAILED"; exit 1; }
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

# settings: scroll the profile screen and open it
dump d07
tap_nav d07 4 profile
SIZE=$(python3 scripts/ui_click.py "$OUT/d07.xml" --size 2>/dev/null)
W=${SIZE%% *}; H=${SIZE##* }
adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 3)) 350 > /dev/null 2>&1 || true
sleep 1
adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 3)) 350 > /dev/null 2>&1 || true
sleep 2
dump d08
tap_text d08 "تنظیمات" settings
shot 08-settings
dump d09
tap_text d09 "NEBULA" theme-nebula
shot 09-theme-nebula

# language switch to English (LTR) and a real typing session
dump d10
tap_text d10 "English" lang-en
shot 10-english
dump d11
tap_nav d11 3 typing-en
shot 11-typing-en
dump d12
tap_text d12 "Type here" typing-field
adb shell input text "the%squick%sbrown%sfox" > /dev/null 2>&1 || true
sleep 2
shot 12-typing-input
adb shell input keyevent 111 > /dev/null 2>&1 || true
dump d13
tap_nav d13 1 analytics-en
shot 13-analytics-en

# back to Persian
dump d14
tap_nav d14 4 profile-en
adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 3)) 350 > /dev/null 2>&1 || true
sleep 1
dump d15
tap_text d15 "Settings" settings-en
dump d16
tap_text d16 "فارسی" lang-fa
shot 14-back-to-fa

# start a typing session from the home quick-access button
dump d06
tap_nav d06 0 home
dump d07
tap_text d07 "شروع تمرین تایپ" typing-start
shot 07-typing-session
adb shell input keyevent 4 > /dev/null 2>&1 || true

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
