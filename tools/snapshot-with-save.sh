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

say "confirming the game is actually running"
STATE="$(python3 "$HERE/play.py" --serial "$SERIAL" state 2>&1 | tail -1)"
echo "   state: $STATE"
case "$STATE" in
  *"not running"*) echo "The game is not running; nothing to snapshot." >&2; exit 1 ;;
esac

say "snapshotting"
"$HERE/capture-ram.sh" "$SERIAL" "$OUT"

say "confirming the game survived it"
python3 "$HERE/play.py" --serial "$SERIAL" state 2>&1 | tail -1
