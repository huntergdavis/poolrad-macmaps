# PoolRad Mac Maps

A personal, offline, black-and-white automap for **Macintosh Pool of Radiance**
running in Mini vMac on an Android e-ink tablet.

The goal: the current area, walls, doors, and party arrow above the original
Mac display, with the keyboard below. No character editor, cloud service, or
game remake. A **PoolRad** toolbar menu groups the game-specific helpers.

**Status: live-map Android prototype for the supplied Macintosh v1.1 game.**

- Boots the supplied Mac II ROM and copied disks in a local Android emulator;
  launches Pool of Radiance v1.1 and loads its sample party.
- **Map above the Mac:** monochrome walls, door outlines, and a facing arrow,
  read directly from the running game's RAM. No separate map import. The
  optional keyboard stays underneath; **PoolRad → Show live map** remembers
  whether you want the pane visible.
- Samples a small read-only packet four times a second while visible; redraws
  only when map/position changes. No blinking arrow or network connection.
- **PoolRad → Code wheel lookup:** select both runes and a path, then **Enter
  code** types the answer and presses Return. All 72 rune illustrations are
  bundled in the personal APK: no download or cache setup. The lookup and
  pickers stay in the top half without dimming the game below.
- Read-only guest RAM capture works in debug builds, under the same menu.
- The map reader validates all 29 supplied maps, with bounds-checked decoding.
- First-area prototype, not complete game coverage: area names, transitions,
  combat/wilderness detection, and physical e-ink behavior still need validation.

- [Full prioritized backlog — handwriting is next](docs/BACKLOG.md)
- [Feature research and the new tablet layout](docs/FEATURE_RESEARCH.md)
- [Implementation plan and scope](docs/PLAN.md)
- [Research, sources, and local test findings](docs/RESEARCH.md)
- [Build, local testing, and current limitations](docs/LOCAL_TESTING.md)
- [Pinned emulator source and license](android/UPSTREAM.md)

The fork installs separately from the Play Store app. Local testing currently
uses the Mac II flavor to match the supplied 256 KiB ROM; the tablet's selected
machine/ROM configuration still needs confirmation.

Keep ROMs, disk images, game files, saves, and RAM captures under `scratch/`.
They are local test inputs, ignored by Git, and must not be bundled in the app.

The emulator source retains its GPLv2 license. The DAX/GEO reader credits
[Gold Box Explorer](https://github.com/bsimser/Gold-Box-Explorer), with its MIT
notice in `licenses/`. Code-wheel arithmetic/table reference:
[Dave Kennedy / Andrew Schultz](https://dkennedy.io/por-code-wheel/wwm.html).
The reference's images are private build inputs, bundled only in the personal
APK, not redistributed in this source tree. See the build notes before sharing
an APK publicly.
