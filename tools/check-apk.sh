#!/usr/bin/env bash
# Refuse an APK that the owner's tablet cannot install.
#
#   tools/check-apk.sh <apk>
#
# Exists because it happened: tools/release.sh picked the x86_64-only APK for
# nine releases running, and "app not installed as it isn't compatible with your
# phone" is all the tablet says about it. The chosen file must carry native code
# for arm64-v8a, which is what the tablet is, and it must be the universal build
# so that it also installs anywhere else.
set -euo pipefail
APK="${1:?usage: check-apk.sh <apk>}"
[ -f "$APK" ] || { echo "No such APK: $APK" >&2; exit 1; }

LIBS="$(unzip -Z1 "$APK" 'lib/*' 2>/dev/null | cut -d/ -f2 | sort -u || true)"
[ -n "$LIBS" ] || { echo "FAIL $APK carries no native libraries at all" >&2; exit 1; }

fail=0
for required in arm64-v8a armeabi-v7a; do
  grep -qx "$required" <<<"$LIBS" || { echo "FAIL $APK has no $required native code" >&2; fail=1; }
done
# An APK with only x86 flavours is exactly the mistake this guards against.
if ! grep -qx arm64-v8a <<<"$LIBS"; then
  echo "FAIL $APK would not install on an ARM tablet" >&2; fail=1
fi
case "$(basename "$APK")" in
  *universal*|poolrad-macmaps-*) ;;
  *) echo "FAIL $APK is a per-ABI build; ship the universal one" >&2; fail=1 ;;
esac
[ "$fail" = 0 ] || exit 1
echo "ok $(basename "$APK") carries: $(tr '\n' ' ' <<<"$LIBS")"
