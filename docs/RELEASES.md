# 0.5.2 — Party health through combat

[Release notes](releases/0.5.2.md): the health reader separates heroes from
enemies in the original Mac combat list. Current/max HP and party order remain
visible; 152 Java tests, three native suites and seven Android View checks pass.

## 0.5.1 — Hands-free code-wheel acceptance

[Release notes](releases/0.5.1.md): distinct automatic answers and normal
game quit/relaunch verified end to end, with 152 Java tests passing.

## 0.5.0 — A more useful companion

Download `poolrad-macmaps-0.5.0.apk` from this release and install it on Android.
[Setup guide](https://github.com/huntergdavis/poolrad-macmaps/blob/main/docs/INSTALL.md).
Bring your own matching Mac ROM, bootable system disk, and Macintosh game disk.
They are not included. No browser hosting or account is required.

New in this prototype:

- Tap any map tile or symbol: each flag owns a map-left, writing-right page.
- Draw across both halves; erase only ink, with one undo/redo history.
- Choose distinct symbols for smithies, temples, inns, shops, monsters, hidden
  walls, districts and treasure. These are your labels, not discovered spoilers.
- Legacy handwriting moves into the writing half with an original-byte backup;
  completed ink and symbol changes save together, privately and atomically.
- No Annotate checkbox, separate map-editor action, spell search or RAM debug
  button in the everyday menu. The finite spell list remains browsable.
- Offline weapons, armor and ammunition: 59 entries, details and comparison,
  with original printed/Macintosh disagreements visible. No search keyboard.
- Verified code-wheel prompts receive normal keystrokes and Return automatically;
  the manual illustrated helper remains available. No game-memory writes.
- Party names, current/max HP and monochrome bars beside the map. Values come
  from the original Mac records; unavailable health is cleared, not guessed.

The original game and its saves remain unchanged. Existing offline rune,
reference, map, and screenshot tools are retained. No new runtime network service.

Mac II universal APK: Android 5.0+, ARM64/ARMv7/x86/x86_64, versionCode 67.
Debug-signed with the existing personal-build key; treat this as an experimental
prerelease. Source for the build is in the matching `v0.5.0` tag. The adjacent
`.sha256` file provides its download checksum.

149 Java tests, three native sanitizer suites and six Android ink-render checks
pass. Actual emulator checks cover old-note migration, independent pages,
symbols, pen/eraser/undo/redo, equipment comparison and automatic wheel entry.
[Validation scope and remaining checks](https://github.com/huntergdavis/poolrad-macmaps/blob/main/docs/LOCAL_TESTING.md).

**Install over the existing app to retain notes.** Uninstall/clear-data removes
them. Notebook export/import is not implemented yet; do not use this prototype
as the only copy of irreplaceable campaign information.

Notebook backups, physical e-ink/stylus polish, Journal and companion tabs remain
on the backlog. Full-game mutable-map
and combat/wilderness recognition are not established by this release.
