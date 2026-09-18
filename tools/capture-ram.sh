#!/usr/bin/env bash
# Take a full RAM snapshot of the running guest and copy it out.
#
#   tools/capture-ram.sh emulator-5584 /where/to/put/it.ram
#
# Wraps the three steps that were being retyped: find the app's process, put a
# local JDWP forward on it, and ask the app for its own bounded snapshot. The
# app writes it to its private storage; this brings it back.
set -euo pipefail
SERIAL="${1:?usage: capture-ram.sh <serial> <output>}"
OUT="${2:?usage: capture-ram.sh <serial> <output>}"
PKG="${3:-com.hunterdavis.poolradmacmaps.ii}"
SDK="${ANDROID_HOME:-/usr/lib/android-sdk}"
export PATH="$SDK/platform-tools:$PATH"
export ANDROID_SERIAL="$SERIAL"
JAVA="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}/bin/java"

PID="$(adb shell pidof "$PKG" | tr -d '\r' | awk '{print $1}')"
[ -n "$PID" ] || { echo "$PKG is not running" >&2; exit 1; }
BEFORE="$(adb shell "run-as $PKG ls files/snapshots/ 2>/dev/null | wc -l" | tr -d '\r')"

adb forward --remove tcp:8700 >/dev/null 2>&1 || true
adb forward tcp:8700 "jdwp:$PID" >/dev/null
"$JAVA" --add-modules jdk.jdi "$(dirname "${BASH_SOURCE[0]}")/CapturePartyRam.java" 8700

# The snapshot is written asynchronously; wait for a new file rather than guess.
for _ in $(seq 1 60); do
  NOW="$(adb shell "run-as $PKG ls files/snapshots/ 2>/dev/null | wc -l" | tr -d '\r')"
  [ "$NOW" -gt "$BEFORE" ] && break
  sleep 2
done
[ "${NOW:-0}" -gt "$BEFORE" ] || { echo "No new snapshot appeared" >&2; exit 1; }

LATEST="$(adb shell "run-as $PKG ls -t files/snapshots/" | head -1 | tr -d '\r')"
adb exec-out "run-as $PKG cat files/snapshots/$LATEST" > "$OUT"

# A snapshot taken while the core is restarting comes back almost empty, and
# an empty one looks exactly like a real one until something tries to read it.
# The Macintosh keeps the application globals pointer in low memory at 0x904;
# if that is not a plausible address, this is not a snapshot worth keeping.
"$(dirname "${BASH_SOURCE[0]}")/check-snapshot.py" "$OUT"
echo "$OUT  $(stat -c%s "$OUT") bytes  (from $LATEST)"
