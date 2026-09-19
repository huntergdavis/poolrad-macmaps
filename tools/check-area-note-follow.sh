#!/usr/bin/env bash
# Visible synthetic acceptance. Use a disposable emulator, installed candidate APK,
# selected ROM and NO game disks. Never run against an active game/campaign.
set -euo pipefail
SERIAL="${1:?usage: check-area-note-follow.sh emulator-SERIAL}"
[[ "$SERIAL" =~ ^emulator-[0-9]+$ ]] || { echo 'Emulator required' >&2; exit 1; }
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-/usr/lib/android-sdk}"
ADB="$SDK/platform-tools/adb"
PACKAGE=com.hunterdavis.poolradmacmaps.ii
DISKS="$($ADB -s "$SERIAL" shell run-as "$PACKAGE" ls files/disks)"
[ -z "$DISKS" ] || { echo 'Use a disposable instance with no guest disks; instrumentation restarts the process.' >&2; exit 1; }
if "$ADB" -s "$SERIAL" shell run-as "$PACKAGE" test -d files/notebooks; then
    echo 'Use a fresh disposable app with no notebook collection; preserve earlier test output separately.' >&2
    exit 1
fi
OUT="$(mktemp -d "$ROOT/scratch/area-note-follow-check.XXXXXX")"
mkdir -p "$OUT/classes"
cat > "$OUT/AndroidManifest.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.hunterdavis.poolradmacmaps.f57check">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="30"/>
<application android:label="F57 controlled check"/>
<instrumentation android:name="name.osher.gil.minivmac.check.AreaNoteFollowCheck" android:targetPackage="com.hunterdavis.poolradmacmaps.ii"/>
</manifest>
XML
javac -cp "$SDK/platforms/android-34/android.jar" -d "$OUT/classes" "$ROOT/tools/AreaNoteFollowCheck.java"
"$SDK/build-tools/34.0.0/d8" --lib "$SDK/platforms/android-34/android.jar" --output "$OUT" "$OUT"/classes/name/osher/gil/minivmac/check/*.class
"$SDK/build-tools/34.0.0/aapt" package -f -M "$OUT/AndroidManifest.xml" -I "$SDK/platforms/android-34/android.jar" -F "$OUT/check.apk"
(cd "$OUT" && zip -q check.apk classes.dex)
"$SDK/build-tools/34.0.0/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android "$OUT/check.apk"
"$ADB" -s "$SERIAL" install -r "$OUT/check.apk"
"$ADB" -s "$SERIAL" shell am instrument -w com.hunterdavis.poolradmacmaps.f57check/name.osher.gil.minivmac.check.AreaNoteFollowCheck | tee "$OUT/result.txt"
mkdir -p "$OUT/screenshots"
"$ADB" -s "$SERIAL" exec-out run-as "$PACKAGE" tar -C files -cf - f57-check | tar -C "$OUT/screenshots" -xf -
grep -q '^PASS arrival fallback' "$OUT/result.txt"
echo "Evidence: $OUT"
