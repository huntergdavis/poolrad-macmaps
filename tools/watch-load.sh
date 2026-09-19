#!/usr/bin/env bash
# Drive the companion's own Load, photographing the guest every few seconds so
# the step it stalls at can be seen rather than guessed.
#
#   tools/watch-load.sh emulator-5584 /tmp/shots
set -euo pipefail
SERIAL="${1:?usage: watch-load.sh <serial> <output dir>}"
OUT="${2:?usage: watch-load.sh <serial> <output dir>}"
export PATH="${ANDROID_HOME:-/usr/lib/android-sdk}/platform-tools:$PATH"
export ANDROID_SERIAL="$SERIAL"
mkdir -p "$OUT"

tap() { adb shell input tap "$1" "$2"; sleep "${3:-3}"; }
say() { echo "== $* =="; }

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
find_and_tap() { "$HERE/tap-text.sh" "$SERIAL" "$1" "${2:-8}"; sleep "${3:-4}"; }

say "PoolRad menu -> Saved games -> Load a saved game"
find_and_tap "POOLRAD" 8 3
find_and_tap "Saved games" 8 8
find_and_tap "Load a saved game" 8 7
say "choosing the save"
find_and_tap "${SAVE:-SampleParty}" 8 5
say "confirming"
find_and_tap "LOAD" 8 2

for i in $(seq 1 ${WATCH:-60}); do
  sleep 5
  adb exec-out screencap -p > "$OUT/step-$(printf %02d "$i").png"
  # The overlay says which part is happening; read it rather than guess.
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  # The overlay line if it is up, otherwise what the map is saying.
  LINE="$(adb shell cat /sdcard/ui.xml 2>/dev/null \
      | grep -oE 'text="(Restarting[^"]*|Starting[^"]*|Loading[^"]*|Waiting[^"]*|Loaded[^"]*)"|content-desc="[^"]{20,}"' \
      | head -1 || true)"
  echo "  $(printf %3ds $((i*5)))  ${LINE:0:150}"
done
echo "shots in $OUT"
