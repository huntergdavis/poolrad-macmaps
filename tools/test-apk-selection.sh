#!/usr/bin/env bash
# The regression that shipped nine uninstallable releases, as a test.
#
# tools/release.sh chose its APK with a find whose -o was never grouped, so
# -name '*.apk' matched every file and the grep -v arm64 -- meant to prefer the
# universal build -- threw the ARM one away. It picked the x86_64-only APK. The
# tablet's answer was "app not installed as it isn't compatible with your phone".
#
# Three things are checked: that the checker rejects a per-ABI APK, that it
# accepts the universal one, and that the release script still asks it.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/android/minivmac/build/outputs/apk/macII/debug"
failures=0

expect() {   # expect <what> <expected-exit> <cmd...>
  local what="$1" want="$2"; shift 2
  local got=0
  "$@" >/dev/null 2>&1 || got=$?
  if [ "$got" = "$want" ]; then echo "  ok $what"
  else echo "  FAIL $what (exit $got, wanted $want)"; failures=$((failures+1)); fi
}

echo "== the checker =="
if [ -f "$BUILD/minivmac-macII-x86_64-debug.apk" ]; then
  expect "an x86_64-only APK is refused" 1 "$ROOT/tools/check-apk.sh" "$BUILD/minivmac-macII-x86_64-debug.apk"
  expect "an armeabi-v7a-only APK is refused" 1 "$ROOT/tools/check-apk.sh" "$BUILD/minivmac-macII-armeabi-v7a-debug.apk"
  expect "an arm64-only APK is refused, universal or nothing" 1 \
      "$ROOT/tools/check-apk.sh" "$BUILD/minivmac-macII-arm64-v8a-debug.apk"
  expect "the universal APK is accepted" 0 "$ROOT/tools/check-apk.sh" "$BUILD/minivmac-macII-universal-debug.apk"
else
  echo "  SKIP no built APKs to check; run assembleMacIIDebug first"
fi
expect "something that is not an APK is refused" 1 "$ROOT/tools/check-apk.sh" "$ROOT/README.md"
expect "a missing file is refused" 1 "$ROOT/tools/check-apk.sh" "$ROOT/nothing-here.apk"

echo "== the release script =="
if grep -q "minivmac-macII-universal-debug.apk" "$ROOT/tools/release.sh"; then
  echo "  ok release.sh names the universal build"
else
  echo "  FAIL release.sh no longer names the universal build"; failures=$((failures+1))
fi
if grep -q "check-apk.sh" "$ROOT/tools/release.sh"; then
  echo "  ok release.sh checks the APK before publishing"
else
  echo "  FAIL release.sh publishes without checking the APK"; failures=$((failures+1))
fi
if grep -qE "find .*-name .*-o -name" "$ROOT/tools/release.sh"; then
  echo "  FAIL release.sh is picking its APK with an ungrouped find again"; failures=$((failures+1))
else
  echo "  ok release.sh is not guessing at which APK to ship"
fi

[ "$failures" = 0 ] || { echo "$failures APK-selection checks failed"; exit 1; }
echo "APK selection holds"
