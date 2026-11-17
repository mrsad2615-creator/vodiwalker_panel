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

tap_text() {
  # $1 = dump name, $2 = needle, $3 = label
  local x y
  x=$(python3 scripts/ui_click.py "$OUT/$1.xml" "$2" 2>"$OUT/click-$3.err" | cut -d' ' -f1)
  y=$(python3 scripts/ui_click.py "$OUT/$1.xml" "$2" 2>/dev/null | cut -d' ' -f2)
  if [ -z "${x:-}" ] || [ -z "${y:-}" ]; then
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
sleep 2
shot 01-splash
sleep 6
shot 02-home
dump d02
tap_text d02 "تایپ" typing
shot 03-typing

dump d03
tap_text d03 "خواندن" reading
shot 04-reading

dump d04
tap_text d04 "آمار" analytics
shot 05-analytics

dump d05
tap_text d05 "پروفایل" profile
shot 06-profile

# settings is reachable from the profile header button
dump d06
tap_text d06 "تنظیمات" settings
shot 07-settings

# back home and start a typing session (typing area interaction)
dump d07
tap_text d07 "خانه" home
shot 08-home-again

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
