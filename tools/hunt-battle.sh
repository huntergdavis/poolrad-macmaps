#!/usr/bin/env bash
# Walk the party about until a fight starts, then stop and leave it on screen.
#
#   tools/hunt-battle.sh emulator-5584 [steps]
#
# Written because finding a battle for a screenshot is the same fiddly loop
# every time: step, look at the companion's own reading of the machine, and
# stop the moment it says COMBAT rather than guessing from the picture.
set -euo pipefail
SERIAL="${1:?usage: hunt-battle.sh <serial> [steps]}"
STEPS="${2:-160}"
SDK="${ANDROID_HOME:-/usr/lib/android-sdk}"
export PATH="$SDK/platform-tools:$PATH"
export ANDROID_SERIAL="$SERIAL"

# A wander that turns often; the slums run out of corridor quickly otherwise.
PATTERN=(8 8 6 8 8 4 8 8 8 2 8 6 8 8 4 8)
for ((i = 0; i < STEPS; i++)); do
  key="${PATTERN[$((i % ${#PATTERN[@]}))]}"
  adb shell input text "$key" >/dev/null 2>&1 || true
  sleep 0.9
  if (( i % 4 == 3 )); then
    # The companion's own accessible text is the cheapest true reading of
    # what the game is doing; no pixels, no guessing.
    if adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 &&
       adb shell cat /sdcard/ui.xml 2>/dev/null | grep -q "Battle overview"; then
      echo "battle after $((i + 1)) steps"
      exit 0
    fi
  fi
done
echo "no battle in $STEPS steps" >&2
exit 1
