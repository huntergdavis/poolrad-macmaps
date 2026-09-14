# Releases

## 0.13.1 — Walls where walls belong

[Release notes](releases/0.13.1.md): match the original Mac wall-first rule,
remove phantom door outlines from open edges, and keep doorway symbols neutral
across live, fog and handwritten-note maps. User-confirmed e-ink stylus and
two-finger zoom/scroll acceptance is now recorded separately from emulator tests.

## 0.13.0 — Follow the tour, spread out your notes

[Release notes](releases/0.13.0.md): restored tutorial tracking, guided-step
sampling, a compact flag editor with more than twice the tested sketch height,
and explicit reference-map modes. Notes, exploration and writable disks survive
the in-place update; public downloads remain bring-your-own-files.

## 0.12.0 — Follow your own footsteps

[Release notes](releases/0.12.0.md): remembered walked squares, optional
visited-only fog, directional feet and a bounded route with return directions.
Exploration belongs to each campaign notebook, survives area changes and is
included in complete backups. The original game remains unchanged.

## 0.11.0 — A place for every note

[Release notes](releases/0.11.0.md): 29 named Macintosh areas, verified live
record identities that survive door-state changes, unchanged notebook keys,
and bounded map headers. Public APK remains bring-your-own-files.

## 0.10.0 — Know your party

[Release notes](releases/0.10.0.md): map-left party HP/AC/class rows,
tap-for-details and small-window collapse. Fresh-save health is corrected by
validating the Mac allocator's real logical record size. 277 Java tests,
focused Android View checks and existing helper/native suites pass; actual
fresh load, details and keyboard/tab transitions are verified locally.

## 0.9.0 — A place for the companion

[Release notes](releases/0.9.0.md): retained Map/Info tabs, five working offline
tools, a smaller menu, and reference/picker windows bounded above the guest.
Visibility migration and fresh-sample polling retain the original game session.
262 Java tests plus focused lifecycle, Android View and existing helper/native
checks pass. Public downloads remain bring-your-own-files.

## 0.8.0 — A quieter Mac desktop

[Release notes](releases/0.8.0.md): real guest White/Mist/Stonework previews,
clean-shutdown Apply, and preference-only restoration that keeps later saves.
Supported System 7.5.5 disks only. 251 Java tests, 54 Python helper tests and
three native sanitizer suites pass. Public downloads remain bring-your-own-files.

### Build tools — pinned personal sources (2026-09-14)

Available on `main` after 0.7.0: an [explicit source-manifest build](PERSONAL_ASSET_FETCH.md)
fetches checksum-pinned private inputs and reuses verified caches offline. No
default assets, checkout hooks, runtime downloads or public personal-APK uploads.
25 fetch tests and the local HTTPS → APK route pass. This changes build tooling
only; it is also included in the 0.8.0 source release.

## 0.7.0 — Your personal starting point

[Release notes](releases/0.7.0.md): opt-in private APK packaging, verified
first-use import and automatic boot, with save-preserving updates and manual
recovery. The public APK remains bring-your-own-files. 213 Java tests, 16
synthetic builder tests and three native suites pass; physical acceptance stays open.

## 0.6.0 — Keep your cartography

[Release notes](releases/0.6.0.md): complete local notebook backups and restore,
fitted map-and-handwriting PNGs, and deliberate notebook removal. Existing
campaigns are never overwritten by import. 198 Java tests and real emulator
backup → remove → restore acceptance pass; physical tablet acceptance stays open.

## 0.5.3 — Pen-friendly map notes

[Release notes](releases/0.5.3.md): remembered pen-only input, finger zoom/pan,
Fit page, canceled/palm-pointer protection and larger nearby-flag choices.
Software checks pass; physical pen/e-ink acceptance remains open.

## 0.5.2 — Party health through combat

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
