# 0.3.0 — Offline companion tools

Download `poolrad-macmaps-0.3.0.apk` from this release and install it on Android.
[Setup guide](https://github.com/huntergdavis/poolrad-macmaps/blob/main/docs/INSTALL.md).
Bring your own matching Mac ROM, bootable system disk, and Macintosh game disk.
They are not included. No browser hosting or account is required.

New in this prototype:

- Android app name: **Pool of Radiance**; project name: PoolRad Mac Maps.
- All 72 rune illustrations included in the repository and APK, fully offline.
- Screenshot → Android Save/Share for the map, guest display, and keyboard.
- Offline levels/skills, spell browser with touch-only filters, and exact
  mixed-coin conversion. Original sources and platform uncertainties are visible.
- Public screenshot-led README with prominent Gold Box Companion inspiration.

Mac II universal APK: Android 5.0+, ARM64/ARMv7/x86/x86_64, versionCode 65.
Debug-signed with the existing personal-build key; treat this as an experimental
prerelease. Source for the build is in the matching `v0.3.0` tag. The adjacent
`.sha256` file provides its download checksum.

Validation: 50 Java tests, native reader sanitizer checks, APK asset inspection,
and local Android-emulator UI/save/share tests pass. Physical e-ink/stylus
acceptance and complete-game area tracking remain unverified. Handwritten
notes, Journal, party sidebar, and companion tabs remain on the backlog.
