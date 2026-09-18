#!/usr/bin/env bash
# Rebuild one tagged release's universal APK and replace its published asset.
#
#   tools/rebuild-release-apk.sh 0.41.0
#
# For repairing the nine releases that went out carrying an x86_64-only APK.
set -euo pipefail
VERSION="${1:?usage: rebuild-release-apk.sh <version>}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-/usr/lib/android-sdk}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
cd "$ROOT"

[ -n "$(git status --porcelain)" ] && { echo "Working tree is dirty; commit first." >&2; exit 1; }
HERE="$(git rev-parse --abbrev-ref HEAD)"
trap 'git checkout -q "$HERE"' EXIT

git checkout -q "v$VERSION"
(cd android && ./gradlew :minivmac:assembleMacIIDebug -q)
APK=android/minivmac/build/outputs/apk/macII/debug/minivmac-macII-universal-debug.apk
"$ROOT/tools/check-apk.sh" "$APK"
cp "$APK" "scratch/poolrad-macmaps-$VERSION.apk"
gh release upload "v$VERSION" "scratch/poolrad-macmaps-$VERSION.apk" --clobber
echo "replaced v$VERSION  $(sha256sum "scratch/poolrad-macmaps-$VERSION.apk" | cut -d' ' -f1)"
