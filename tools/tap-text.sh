#!/usr/bin/env bash
# Tap the on-screen element whose text or description contains a phrase.
#
#   tools/tap-text.sh emulator-5584 "Load a saved game" [attempts]
#
# Exists because tapping remembered coordinates is how three test runs drew
# conclusions about a feature that may never have been exercised: the
# coordinates were measured with three saved games listed, and with any other
# number the taps landed somewhere else and nothing noticed. uiautomator already
# reports every element's text and bounds, so this reads them and taps the
# middle of the match. It fails loudly when there is no match, which is the
# whole point.
set -euo pipefail
SERIAL="${1:?usage: tap-text.sh <serial> <phrase> [attempts]}"
PHRASE="${2:?usage: tap-text.sh <serial> <phrase> [attempts]}"
ATTEMPTS="${3:-8}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PATH="${ANDROID_HOME:-/usr/lib/android-sdk}/platform-tools:$PATH"
export ANDROID_SERIAL="$SERIAL"

DUMP="$(mktemp)"
trap 'rm -f "$DUMP"' EXIT
for _ in $(seq 1 "$ATTEMPTS"); do
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/ui.xml > "$DUMP" 2>/dev/null || true
  if BOUNDS="$(python3 "$HERE/find-element.py" "$DUMP" "$PHRASE")"; then
    # shellcheck disable=SC2086
    adb shell input tap $BOUNDS
    echo "tapped \"$PHRASE\" at $BOUNDS"
    exit 0
  fi
  sleep 2
done
echo "No element on screen says \"$PHRASE\"" >&2
exit 1
