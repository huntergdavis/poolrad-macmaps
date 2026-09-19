# Keeping the README current

Every image embedded in README.md must come from a current running release
and must be at most three releases older than the release being published.
Refresh screenshots whenever the pictured interface changes, even within that
window. Remove stale illustrations from the README when a useful replacement
is not ready; historical evidence can stay in the feature's research document.

Use the disposable emulator and normal shutdown/update/load workflow described
in MARCHING_ORDER.md. Navigate to each real app view, then capture it:

    python3 tools/readme-screenshots.py capture emulator-5586 readme-map
    python3 tools/readme-screenshots.py capture emulator-5586 readme-options
    python3 tools/readme-screenshots.py capture emulator-5586 readme-marching-order
    python3 tools/readme-screenshots.py capture emulator-5586 readme-message-log
    python3 tools/readme-screenshots.py capture emulator-5586 readme-money
    python3 tools/readme-screenshots.py capture emulator-5586 readme-save-preview

Capture reads the installed application's version, requires a matching local
release tag and foreground PoolRad activity, writes a PNG, and records its
SHA-256, capture time and release in docs/images/readme-screenshots.json.
Visually inspect every image and correct its README caption before committing.
A version label alone does not prove a modified development APK matches a tag:
install the published APK with update-test-app.py before these captures.

    python3 tools/readme-screenshots.py check 0.84.0

The check reads every HTML or Markdown image in the README, verifies its file
and recorded hash, and counts intervening numeric release tags. release.sh runs
it for the proposed release before changing versions, building or publishing.
Untracked, missing, modified, future-version and overly old images stop release.
Do not edit capture metadata to pass the check: recapture the installed release
or remove that image from the README.
