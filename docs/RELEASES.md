# 0.4.0 — Handwritten cartographer

Download `poolrad-macmaps-0.4.0.apk` from this release and install it on Android.
[Setup guide](https://github.com/huntergdavis/poolrad-macmaps/blob/main/docs/INSTALL.md).
Bring your own matching Mac ROM, bootable system disk, and Macintosh game disk.
They are not included. No browser hosting or account is required.

New in this prototype:

- Annotate a map tile with a monochrome flag and enlarged handwritten note.
- Pen, eraser, undo/redo, stroke autosave, and explicit linked-note deletion.
- Separate campaign notebooks; flags use verified area and tile identities,
  never a changing RAM address. Unknown geometry does not inherit another map's notes.
- Upper-half ink sheets preserve their proportions on resize and leave the
  lower game visible. Failed saves retain the sheet and offer retry.
- Versioned, checksummed local vector storage with ordered atomic saves.

The original game and its saves remain unchanged. Existing offline rune,
reference, map, and screenshot tools are retained. No new runtime network service.

Mac II universal APK: Android 5.0+, ARM64/ARMv7/x86/x86_64, versionCode 66.
Debug-signed with the existing personal-build key; treat this as an experimental
prerelease. Source for the build is in the matching `v0.4.0` tag. The adjacent
`.sha256` file provides its download checksum.

Validation: 90 Java tests, native reader sanitizer checks, APK asset inspection,
and local Android-emulator handwriting checks. See the [acceptance evidence](https://github.com/huntergdavis/poolrad-macmaps/blob/main/docs/NOTEBOOK.md).

**Install over the existing app to retain notes.** Uninstall/clear-data removes
them. Notebook export/import is not implemented yet; do not use this prototype
as the only copy of irreplaceable campaign information.

Direct map drawing, notebook backups, physical e-ink/stylus polish, Journal,
party sidebar, and companion tabs remain on the backlog. Full-game mutable-map
and combat/wilderness recognition are not established by this release.
