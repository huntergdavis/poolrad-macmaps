#!/usr/bin/env bash
# Compile a tools/*RenderCheck.java against the built app classes, dex it, and
# run it on a connected emulator through app_process.
#
#   tools/render-check.sh CombatMapRenderCheck [args...]
#
# Requires the app to have been built at least once (assembleMacIIDebug), and
# exactly one device, or ANDROID_SERIAL set.
set -euo pipefail

CLASS="${1:?usage: render-check.sh <ClassName> [args...]}"; shift || true
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_HOME:-/usr/lib/android-sdk}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export PATH="$SDK/platform-tools:$SDK/build-tools/34.0.0:$JAVA_HOME/bin:$PATH"

APP_CLASSES="$ROOT/android/minivmac/build/intermediates/javac/macIIDebug/compileMacIIDebugJavaWithJavac/classes"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
[ -d "$APP_CLASSES" ] || { echo "Build the app first: (cd android && ./gradlew :minivmac:assembleMacIIDebug)" >&2; exit 1; }

OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
javac -nowarn -source 17 -target 17 -cp "$ANDROID_JAR:$APP_CLASSES" \
      -d "$OUT/classes" "$ROOT/tools/$CLASS.java"
d8 --lib "$ANDROID_JAR" --classpath "$APP_CLASSES" --output "$OUT" \
   $(find "$OUT/classes" -name '*.class') $(find "$APP_CLASSES" -name '*.class')

REMOTE="/data/local/tmp/$CLASS.zip"
adb push -q "$OUT/classes.dex" /data/local/tmp/classes.dex >/dev/null 2>&1 || adb push "$OUT/classes.dex" /data/local/tmp/classes.dex >/dev/null
adb shell "cd /data/local/tmp && rm -f $CLASS.zip"
(cd "$OUT" && zip -q classes.zip classes.dex) && adb push "$OUT/classes.zip" "$REMOTE" >/dev/null
adb shell "CLASSPATH=$REMOTE app_process /system/bin $CLASS $*"
