#!/usr/bin/env bash
# Run one explicitly supported local debugger helper against a disposable emulator.
set -euo pipefail
SERIAL="${1:?usage: debug-ui.sh emulator-NNNN GuestCommand|ReplayMessage|CoreStatus|AudioActivity|PauseActivity [args...]}"
CLASS="${2:?missing helper}"; shift 2
[[ "$SERIAL" =~ ^emulator-[0-9]+$ ]] || { echo "Emulator only" >&2; exit 1; }
case "$CLASS" in GuestCommand|ReplayMessage|CoreStatus|AudioActivity|PauseActivity) ;; *) echo "Unsupported helper" >&2; exit 1;; esac
SDK="${ANDROID_HOME:-/usr/lib/android-sdk}"
PKG=com.hunterdavis.poolradmacmaps.ii
ADB="$SDK/platform-tools/adb"
PID="$("$ADB" -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')"
[[ "$PID" =~ ^[0-9]+$ ]] || { echo "Expected one running app process" >&2; exit 1; }
PORT="$("$ADB" -s "$SERIAL" forward tcp:0 "jdwp:$PID")"
trap '"$ADB" -s "$SERIAL" forward --remove "tcp:$PORT" >/dev/null 2>&1 || true' EXIT
"${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}/bin/java" --add-modules jdk.jdi \
    "$(dirname "$0")/$CLASS.java" "$PORT" "$@"
