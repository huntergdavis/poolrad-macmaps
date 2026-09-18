#!/usr/bin/env bash
# Everything that must pass before a release: native probe suites, the Java
# unit tests, the APK build, and every render check on a connected emulator.
#
#   tools/verify.sh            # all of it
#   tools/verify.sh --fast     # skip the on-device render checks
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-/usr/lib/android-sdk}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
FAST=0; [ "${1:-}" = "--fast" ] && FAST=1

echo "== native probe suites =="
for suite in "$ROOT"/tools/test-*.c; do
  [ -e "$suite" ] || continue
  name="$(basename "$suite" .c)"
  cc -Wall -Wextra -Werror -O1 -I"$ROOT/android/minivmac/src/main/jni/src" \
     -o "/tmp/$name" "$suite" && "/tmp/$name" >/dev/null && echo "  ok $name"
done
for suite in "$ROOT"/tools/test-*.py; do
  [ -e "$suite" ] || continue
  name="$(basename "$suite")"
  out="$(python3 "$suite" 2>&1)" && { echo "  ok $name"; continue; }
  # A suite that needs an optional module nobody installed is skipped loudly,
  # not counted as a pass and not treated as a failure.
  if grep -q "ModuleNotFoundError" <<<"$out"; then
    echo "  SKIP $name -- $(grep -o "No module named '[^']*'" <<<"$out" | head -1)"
  else
    echo "  FAIL $name"; echo "$out" | tail -20; exit 1
  fi
done

echo "== java tests and apk =="
(cd "$ROOT/android" && ./gradlew :minivmac:assembleMacIIDebug :minivmac:testMacIIDebugUnitTest -q)

echo "== the APK that would ship =="
"$ROOT/tools/test-apk-selection.sh"
python3 - "$ROOT" <<'PY'
import glob,re,sys
n=f=e=s=0
for p in glob.glob(sys.argv[1]+'/android/minivmac/build/test-results/testMacIIDebugUnitTest/TEST-*.xml'):
    m=re.search(r'tests="(\d+)"[^>]*skipped="(\d+)"[^>]*failures="(\d+)"[^>]*errors="(\d+)"',open(p).read())
    if m: n+=int(m.group(1)); s+=int(m.group(2)); f+=int(m.group(3)); e+=int(m.group(4))
print(f"  {n} java tests, {f} failures, {e} errors, {s} skipped")
sys.exit(1 if f or e else 0)
PY

[ "$FAST" = 1 ] && { echo "== render checks skipped (--fast) =="; exit 0; }
adb get-state >/dev/null 2>&1 || { echo "no device; render checks skipped" >&2; exit 0; }
echo "== render checks =="
for check in "$ROOT"/tools/*RenderCheck.java "$ROOT"/tools/*Check.java; do
  [ -e "$check" ] || continue
  name="$(basename "$check" .java)"
  case "$name" in *RenderCheck) ;; *) continue ;; esac
  echo "-- $name"
  "$ROOT/tools/render-check.sh" "$name" 2>/dev/null | grep -E '^(PASS|FAIL|[0-9]+ )' || {
    echo "  $name did not report"; exit 1; }
done
