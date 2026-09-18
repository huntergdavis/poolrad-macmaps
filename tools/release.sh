#!/usr/bin/env bash
# Cut a release: bump the version, build the universal APK, tag, push, and
# publish to GitHub with the APK attached.
#
#   tools/release.sh 0.38.0 "Two marks finished"
#
# Expects docs/releases/<version>.md to exist already; it becomes the release
# notes. Refuses to run on a dirty tree or without that file.
set -euo pipefail
VERSION="${1:?usage: release.sh <version> <title>}"
TITLE="${2:?usage: release.sh <version> <title>}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-/usr/lib/android-sdk}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
cd "$ROOT"

[ -n "$(git status --porcelain)" ] && { echo "Working tree is dirty; commit first." >&2; exit 1; }
[ -f "docs/releases/$VERSION.md" ] || { echo "Write docs/releases/$VERSION.md first." >&2; exit 1; }
git rev-parse "v$VERSION" >/dev/null 2>&1 && { echo "v$VERSION already exists." >&2; exit 1; }

GRADLE=android/minivmac/build.gradle
OLD_CODE="$(grep -oP 'versionCode \K[0-9]+' "$GRADLE" | head -1)"
NEW_CODE=$((OLD_CODE + 1))
OLD_NAME="$(grep -oP "versionName '\K[^']+" "$GRADLE" | head -1)"
sed -i "s/versionCode $OLD_CODE/versionCode $NEW_CODE/" "$GRADLE"
sed -i "s/versionName '$OLD_NAME'/versionName '$VERSION'/g" "$GRADLE"
echo "== $OLD_NAME ($OLD_CODE) -> $VERSION ($NEW_CODE) =="

(cd android && ./gradlew :minivmac:assembleMacIIDebug -q)
APK="$(find android/minivmac/build/outputs/apk/macII/debug -name '*universal*.apk' -o -name '*.apk' | grep -v arm64 | head -1)"
[ -f "$APK" ] || { echo "No universal APK produced" >&2; exit 1; }
cp "$APK" "scratch/poolrad-macmaps-$VERSION.apk"
echo "APK: scratch/poolrad-macmaps-$VERSION.apk  $(sha256sum "scratch/poolrad-macmaps-$VERSION.apk" | cut -d' ' -f1)"

git add "$GRADLE"
git commit -q -m "$VERSION — $TITLE

Version $VERSION, versionCode $NEW_CODE.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
git tag -a "v$VERSION" -m "$VERSION — $TITLE"
git push -q origin main
git push -q origin "v$VERSION"
gh release create "v$VERSION" "scratch/poolrad-macmaps-$VERSION.apk" \
   --title "$VERSION — $TITLE" --notes-file "docs/releases/$VERSION.md" --prerelease
echo "released v$VERSION"
