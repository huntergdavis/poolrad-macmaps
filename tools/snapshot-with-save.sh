#!/usr/bin/env bash
# Boot the guest, load a saved game, and take a RAM snapshot of it -- checking
# at every step that the thing just asked for actually happened.
#
#   tools/snapshot-with-save.sh emulator-5584 F7Injured /tmp/out.ram
#
# Written after an afternoon spent believing the snapshot path was broken. It
# was not: the captures were of a machine that had not booted, because the steps
# were run by hand minutes apart and the emulated Mac had restarted in between.
# Every step here verifies itself, so an empty snapshot means something.
set -euo pipefail
SERIAL="${1:?usage: snapshot-with-save.sh <serial> <save> <output>}"
SAVE="${2:?usage: snapshot-with-save.sh <serial> <save> <output>}"
OUT="${3:?usage: snapshot-with-save.sh <serial> <save> <output>}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PATH="${ANDROID_HOME:-/usr/lib/android-sdk}/platform-tools:$PATH"

say() { echo "== $* =="; }

say "booting"
python3 "$HERE/play.py" --serial "$SERIAL" boot

say "loading $SAVE"
python3 "$HERE/play.py" --serial "$SERIAL" load "$SAVE"

say "clicking through the intro until the party is out in the world"
python3 "$HERE/play.py" --serial "$SERIAL" tour --limit 80 >/dev/null 2>&1 || true

say "confirming the party is actually somewhere"
# Not "is the game running" -- the companion says "Position unavailable" at a
# Continue prompt and at the title too, and both are correct. The only reading
# worth a snapshot is one where the companion can name where the party is.
for _ in $(seq 1 10); do
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  WHERE="$(adb shell cat /sdcard/ui.xml 2>/dev/null | tr '>' '\n' | grep -o 'content-desc="[^"]*"' | head -1)"
  case "$WHERE" in
    *"Party at"*) break ;;
  esac
  sleep 3
done
case "$WHERE" in
  *"Party at"*) echo "   ${WHERE:0:90}" ;;
  *) echo "The companion cannot say where the party is; a snapshot would prove nothing." >&2
     echo "   saw: ${WHERE:0:120}" >&2; exit 1 ;;
esac

say "snapshotting"
"$HERE/capture-ram.sh" "$SERIAL" "$OUT"

say "confirming the game survived it"
python3 "$HERE/play.py" --serial "$SERIAL" state 2>&1 | tail -1
