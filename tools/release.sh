#!/usr/bin/env bash
# Cut a release: bump the version, build the universal APK, tag, push, and
# publish to GitHub with the APK attached.
#
#   tools/release.sh 0.38.0 "Two marks finished"
#   tools/release.sh 0.100.0 "Quiet while you read" --existing-ref d4e1b79
#
# An already-bumped current version keeps its versionCode. --existing-ref
# backfills a historical release from an exact, published main commit without
# changing main or its current download links.
#
# Expects docs/releases/<version>.md to exist already; it becomes the release
# notes. Refuses to run on a dirty tree or without that file.
set -euo pipefail
VERSION="${1:?usage: release.sh <version> <title>}"
TITLE="${2:?usage: release.sh <version> <title>}"
EXISTING_REF=""
if [ "$#" -ne 2 ]; then
    [ "$#" -eq 4 ] && [ "$3" = --existing-ref ] || { echo "Expected optional --existing-ref <commit>" >&2; exit 1; }
    EXISTING_REF="$4"
fi
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
git rev-parse "v$VERSION" >/dev/null 2>&1 && { echo "v$VERSION already exists." >&2; exit 1; }
JOURNAL=android/minivmac/src/main/assets/journal/adventurers-journal.prjr
[ -f "$JOURNAL" ] || { echo "Prepare the bundled journal first; see docs/JOURNAL.md." >&2; exit 1; }

if [ -n "$EXISTING_REF" ]; then
    SOURCE_COMMIT="$(git rev-parse --verify "$EXISTING_REF^{commit}")"
    git fetch -q origin main
    git merge-base --is-ancestor "$SOURCE_COMMIT" origin/main || { echo "Source must already be on origin/main" >&2; exit 1; }
    git show "$SOURCE_COMMIT:android/minivmac/build.gradle" | python3 -c '
import re, sys
names = re.findall(r"versionName '\''([^'\'']+)'\''", sys.stdin.read())
if not names or set(names) != {sys.argv[1]}:
    sys.exit("Source version names do not match requested release")
' "$VERSION"
    git cat-file -e "$SOURCE_COMMIT:docs/releases/$VERSION.md"
    BUILD_ROOT="$ROOT/scratch/release-source-$VERSION"
    [ ! -e "$BUILD_ROOT" ] || { echo "Build directory already exists: $BUILD_ROOT" >&2; exit 1; }
    git worktree add --detach "$BUILD_ROOT" "$SOURCE_COMMIT"
    # The generated journal is intentionally ignored by Git. Supply the
    # documented release asset, then check the packaged bytes before upload.
    mkdir -p "$BUILD_ROOT/$(dirname "$JOURNAL")"
    cp "$JOURNAL" "$BUILD_ROOT/$JOURNAL"
    # Retain the checkout on failure for diagnosis. Never change the active
    # checkout or borrow an APK from a different source revision.
    (
        cd "$BUILD_ROOT"
        python3 tools/readme-screenshots.py check "$VERSION"
        bash tools/test-snapshot-native.sh
        bash tools/test-audio-native.sh
        bash tools/test-emulation-wait.sh
        (cd android && ./gradlew :minivmac:assembleMacIIDebug :minivmac:testMacIIDebugUnitTest --max-workers=2 -q)
        APK=android/minivmac/build/outputs/apk/macII/debug/minivmac-macII-universal-debug.apk
        tools/check-apk.sh "$APK"
        node tools/check-wheel-apk.mjs "$APK"
        cp "$APK" "$ROOT/scratch/poolrad-macmaps-$VERSION.apk"
    )
    echo "APK: scratch/poolrad-macmaps-$VERSION.apk  $(sha256sum "scratch/poolrad-macmaps-$VERSION.apk" | cut -d' ' -f1)"
    git tag -a "v$VERSION" "$SOURCE_COMMIT" -m "$VERSION — $TITLE"
    git push -q origin "v$VERSION"
    gh release create "v$VERSION" "scratch/poolrad-macmaps-$VERSION.apk" \
        --verify-tag --title "$VERSION — $TITLE" \
        --notes-file "$BUILD_ROOT/docs/releases/$VERSION.md" --prerelease
    echo "released v$VERSION from $SOURCE_COMMIT (version unchanged)"
    exit 0
fi

[ -f "docs/releases/$VERSION.md" ] || { echo "Write docs/releases/$VERSION.md first." >&2; exit 1; }
python3 tools/readme-screenshots.py check "$VERSION"
bash tools/test-snapshot-native.sh
bash tools/test-audio-native.sh
bash tools/test-emulation-wait.sh

GRADLE=android/minivmac/build.gradle
OLD_CODE="$(grep -oP 'versionCode \K[0-9]+' "$GRADLE" | head -1)"
NEW_CODE=$((OLD_CODE + 1))
OLD_NAME="$(grep -oP "versionName '\K[^']+" "$GRADLE" | head -1)"
[ "$OLD_NAME" != "$VERSION" ] || NEW_CODE="$OLD_CODE"
sed -i "s/versionCode $OLD_CODE/versionCode $NEW_CODE/" "$GRADLE"
sed -i "s/versionName '$OLD_NAME'/versionName '$VERSION'/g" "$GRADLE"
python3 - "$OLD_NAME" "$VERSION" <<'PY'
from pathlib import Path
import re
import sys
for name in ('README.md', 'docs/INSTALL.md'):
    path = Path(name)
    # The source may already be bumped while download links lag several
    # releases behind. Update linked release versions, not only OLD_NAME.
    text = path.read_text().replace(sys.argv[1], sys.argv[2])
    text = re.sub(r'\[[^\]\n]*\]\(https://github\.com/huntergdavis/poolrad-macmaps/releases/(?:download|tag)/v[^)]+\)',
                  lambda match: re.sub(r'\d+\.\d+\.\d+', sys.argv[2], match.group()), text)
    path.write_text(text)
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
node "$ROOT/tools/check-wheel-apk.mjs" "scratch/poolrad-macmaps-$VERSION.apk"
echo "APK: scratch/poolrad-macmaps-$VERSION.apk  $(sha256sum "scratch/poolrad-macmaps-$VERSION.apk" | cut -d' ' -f1)"

git diff --check
git add "$GRADLE" README.md docs/INSTALL.md docs/BACKLOG.md
git commit -q -m "$VERSION — $TITLE

Version $VERSION, versionCode $NEW_CODE."
git tag -a "v$VERSION" -m "$VERSION — $TITLE"
git push -q origin main
git push -q origin "v$VERSION"
gh release create "v$VERSION" "scratch/poolrad-macmaps-$VERSION.apk" \
   --verify-tag --title "$VERSION — $TITLE" --notes-file "docs/releases/$VERSION.md" --prerelease
echo "released v$VERSION"
