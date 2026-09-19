#!/usr/bin/env bash
# Compile and run a tools/*Check.java UI Automator test on a disposable emulator.
# Simple output avoids the legacy WatcherResultPrinter's android.test annotation.
# AOSP: https://android.googlesource.com/platform/prebuilts/fullsdk/sources/android-31/+/refs/heads/androidx-savedstate-release/com/android/uiautomator/testrunner/UiAutomatorTestRunner.java
set -euo pipefail
SERIAL="${1:?usage: ui-test.sh emulator-SERIAL ClassName}"
CLASS="${2:?usage: ui-test.sh emulator-SERIAL ClassName}"
[[ "$SERIAL" =~ ^emulator-[0-9]+$ && "$CLASS" =~ ^[A-Za-z][A-Za-z0-9]*$ ]] || exit 2
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_HOME:-/usr/lib/android-sdk}"
cd "$ROOT"
mkdir -p scratch
OUT="$(mktemp -d scratch/ui-test.XXXXXX)"
echo "UI test build/evidence: $OUT"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
UI_JAR="$SDK/platforms/android-34/uiautomator.jar"
JUNIT_JAR=/usr/share/java/junit-3.8.2.jar
mkdir "$OUT/classes" "$OUT/dex"
javac -nowarn -source 17 -target 17 -cp "$ANDROID_JAR:$UI_JAR:$JUNIT_JAR" -d "$OUT/classes" "tools/$CLASS.java"
"$SDK/build-tools/34.0.0/d8" --lib "$ANDROID_JAR" --classpath "$UI_JAR" --classpath "$JUNIT_JAR" --output "$OUT/dex" "$OUT/classes/$CLASS.class"
(cd "$OUT/dex" && zip -q ../test.jar classes.dex)
REMOTE="/data/local/tmp/$CLASS-test.jar"
"$SDK/platform-tools/adb" -s "$SERIAL" push "$OUT/test.jar" "$REMOTE"
"$SDK/platform-tools/adb" -s "$SERIAL" shell uiautomator runtest "$REMOTE" -c "$CLASS" -s | tee "$OUT/result.log"
if rg -q 'Test run aborted|INSTRUMENTATION_STATUS: shortMsg=|FAILURES!!!' "$OUT/result.log"; then
  echo "UI runner did not complete successfully" >&2
  exit 1
fi
rg -q 'OK \([1-9][0-9]* tests?\)' "$OUT/result.log"
