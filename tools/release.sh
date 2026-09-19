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
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Expected a numeric release version" >&2; exit 1; }
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-/usr/lib/android-sdk}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
cd "$ROOT"
[ "$(git branch --show-current)" = main ] || { echo "Release from main only" >&2; exit 1; }
mkdir -p scratch
exec 9>scratch/release.lock
flock -n 9 || { echo "Another release is running" >&2; exit 1; }

[ -n "$(git status --porcelain)" ] && { echo "Working tree is dirty; commit first." >&2; exit 1; }
[ -f "docs/releases/$VERSION.md" ] || { echo "Write docs/releases/$VERSION.md first." >&2; exit 1; }
git rev-parse "v$VERSION" >/dev/null 2>&1 && { echo "v$VERSION already exists." >&2; exit 1; }
python3 tools/readme-screenshots.py check "$VERSION"
bash tools/test-snapshot-native.sh

GRADLE=android/minivmac/build.gradle
OLD_CODE="$(grep -oP 'versionCode \K[0-9]+' "$GRADLE" | head -1)"
NEW_CODE=$((OLD_CODE + 1))
OLD_NAME="$(grep -oP "versionName '\K[^']+" "$GRADLE" | head -1)"
sed -i "s/versionCode $OLD_CODE/versionCode $NEW_CODE/" "$GRADLE"
sed -i "s/versionName '$OLD_NAME'/versionName '$VERSION'/g" "$GRADLE"
python3 - "$OLD_NAME" "$VERSION" <<'PY'
from pathlib import Path
import re
import sys
for name in ('README.md', 'docs/INSTALL.md'):
    path = Path(name)
    path.write_text(path.read_text().replace(sys.argv[1], sys.argv[2]))
backlog = Path('docs/BACKLOG.md')
backlog.write_text(re.sub(r'Current published release: \*\*[0-9.]+\*\*',
                         'Current published release: **' + sys.argv[2] + '**',
                         backlog.read_text()))
PY
echo "== $OLD_NAME ($OLD_CODE) -> $VERSION ($NEW_CODE) =="

(cd android && ./gradlew :minivmac:assembleMacIIDebug :minivmac:testMacIIDebugUnitTest --max-workers=2 -q)
# The universal build, by name, and nothing else. A `find` with an ungrouped
# -o once picked the x86_64-only APK here, and nine releases went out that the
# owner's ARM tablet answered with "app not installed as it isn't compatible
# with your phone". Name the file, then prove it carries ARM code.
APK="android/minivmac/build/outputs/apk/macII/debug/minivmac-macII-universal-debug.apk"
[ -f "$APK" ] || { echo "No universal APK at $APK" >&2; exit 1; }
cp "$APK" "scratch/poolrad-macmaps-$VERSION.apk"
"$ROOT/tools/check-apk.sh" "scratch/poolrad-macmaps-$VERSION.apk"
echo "APK: scratch/poolrad-macmaps-$VERSION.apk  $(sha256sum "scratch/poolrad-macmaps-$VERSION.apk" | cut -d' ' -f1)"

git diff --check
git add "$GRADLE" README.md docs/INSTALL.md docs/BACKLOG.md
git commit -q -m "$VERSION — $TITLE

Version $VERSION, versionCode $NEW_CODE."
git tag -a "v$VERSION" -m "$VERSION — $TITLE"
git push -q origin main
git push -q origin "v$VERSION"
gh release create "v$VERSION" "scratch/poolrad-macmaps-$VERSION.apk" \
   --title "$VERSION — $TITLE" --notes-file "docs/releases/$VERSION.md" --prerelease
echo "released v$VERSION"
