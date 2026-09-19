#!/usr/bin/env bash
# Send a documented game Command-key through the app's existing timed input.
set -euo pipefail
SERIAL="${1:?usage: guest-command.sh emulator-NNNN load|quit|begin|view|save}"
ACTION="${2:?missing command}"
[[ "$SERIAL" =~ ^emulator-[0-9]+$ ]] || { echo "Emulator only" >&2; exit 1; }
SDK="${ANDROID_HOME:-/usr/lib/android-sdk}"
PKG=com.hunterdavis.poolradmacmaps.ii
ADB="$SDK/platform-tools/adb"
PID="$("$ADB" -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"
[[ "$PID" =~ ^[0-9]+$ ]] || { echo "Expected one running app process" >&2; exit 1; }
PORT="$("$ADB" -s "$SERIAL" forward tcp:0 "jdwp:$PID")"
trap '"$ADB" -s "$SERIAL" forward --remove "tcp:$PORT" >/dev/null 2>&1 || true' EXIT
"${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}/bin/java" --add-modules jdk.jdi \
    "$(dirname "$0")/GuestCommand.java" "$PORT" "$ACTION" "${@:3}"
