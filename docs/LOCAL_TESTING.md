# Local Android prototype

## Build

Use JDK 17, Android SDK 34, build-tools 34.0.0, and NDK 27.0.12077973.
The personal build also needs the 72 reference GIFs in
`android/minivmac/private-assets/codewheel/`: `esp01.gif`–`esp36.gif` and
`det01.gif`–`det36.gif`, retaining the numbering from
`https://dkennedy.io/por-code-wheel/img/`. This ignored folder is an Android asset
source, not the app's cache. The local files were recovered unchanged from our
previously downloaded emulator reference images. A fresh checkout needs these
private build inputs supplied separately. `verifyRuneArtwork` fails a build
with missing, oversized, or invalid-header/dimension images.

From `android/`:

```sh
./gradlew :minivmac:assembleMacIIDebug :minivmac:testMacIIDebugUnitTest
```

Set `JAVA_HOME` and `ANDROID_HOME` to the local installations if necessary.
Normal builds produce debug-signed APKs in
`android/minivmac/build/outputs/apk/macII/debug/`, including ARM64 and universal
variants. Debug signing is suitable for this prototype; keep the same personal
key for future updates. Release signing is not configured.

For faster **local x86_64 emulator testing**, add
`-Pandroid.injected.build.abi=x86_64`. That IDE-style build instead writes to
`build/intermediates/apk/macII/debug/`, is marked test-only, and requires
`adb install -t`. Do not hand that APK to an ARM tablet.

The `macPlus` flavor builds separately and requires a matching Plus ROM. The
supplied 256 KiB Mac II ROM is not a substitute. The tested local app ID is
`com.hunterdavis.poolradmacmaps.ii`; neither flavor replaces the stock app.

## Test inputs

Never mount the original supplied disks for testing. ROMs, images, saves,
extracted resource forks, screenshots, and RAM stay in ignored `scratch/`.
The app uses private internal storage for disks/ROMs/cache and disables cloud
backup. Regular imports use the upstream Android file picker.

On this workstation, the AOSP API 30 emulator `poolrad-map-test` runs on
`emulator-5580`, at 1200×1600 and density 200. It has booted copies of
`MacII.ROM`, `MinivMacBootv2.dsk`, and an HFS disk made from the supplied game
archive. This does not verify the physical tablet's e-ink refresh behavior.

### Resource-fork-safe test disk

Install `unar` and `hfsutils`. Extract privately:

```sh
unar -q -s -k visible -o scratch/extracted scratch/input/Pool_Of_Radiance.SIT
node tools/prepare-test-game.mjs 'scratch/extracted/Pool Of Radiance' scratch/test-disks/NEW-game.dsk
```

The helper refuses an existing destination, wraps the extracted data/resource
forks in MacBinary II, then delegates HFS creation/copying to `hfsutils`. It
does not ship game data or implement HFS. The game application has an empty
data fork and a 327,595-byte resource fork; losing that fork makes it unlaunchable.

**Known input issue:** `unar` reported an extraction error for
`PoolRad2/ITEM2.DAX`. All eight GEO files decode; the game boots and its sample
party loads, but this is not proof that every encounter/item file is healthy.

## Code wheel

Use **PoolRad → Code wheel lookup**. Select an outer Espruar rune, an inner
Dethek rune, and the dotted/dash-dot/dashed path. **Enter code** sends normal
guest key events, including Return. It does not bypass or patch game code.
Use the action at the game's code-word prompt; it does not yet detect the
prompt or identify its runes automatically. Closing the lookup sends nothing.
App pause cancels a pending entry and releases any held helper key.

Rune numbering matches the reading order of the linked reference. Its 72
illustrations are now bundled in the personal APK and read directly from assets.
No download button, network call, or existing app cache is needed, including on
first launch. Previous private caches can remain unused; they are not consulted.
The lookup, both rune grids, and path selector occupy only the upper half of
the usable screen. The game remains undimmed below. Rune grids and lookup
content scroll on smaller windows; Close/Cancel/Enter stay in the action row.
Rotation resizes open panels, and closing or cancelling sends no game input.

After building, run `node tools/check-wheel-apk.mjs` from the repository root
to check that all 72 images in the universal APK match the private originals,
and that no ROMs, disks, or game data were packaged with them.

The linked table spells one entry `80ASIS` (with a zero); we preserve it instead
of silently changing the requested reference. That particular answer remains
unverified in-game. A leading wheel alignment digit is omitted when typing.

### Code-wheel 0.2.1 acceptance (2026-09-13)

- Built all four Mac II ABI APKs and universal APK; 24 Java tests pass.
- `node tools/check-wheel-apk.mjs`: all 72 GIFs match the local originals
  byte-for-byte (9,574 bytes total). No ROM/disk/game archive was bundled.
- Upgraded the test emulator to versionCode 64 / versionName 0.2.1, with Wi-Fi
  and mobile data disabled. Moved the old `files/codewheel` aside to
  `files/codewheel-legacy-backup`, preserving it; no active cache exists.
- Both 36-rune grids render, and 21/14/dashes still gives `WYVERN`.
- At 1200×1600, lookup/rune/path windows stop at y=780, above midpoint y=800;
  the guest is not dimmed. Verified again at 1600×1200 after rotation, including
  scrolling to rune 36 and accessible Cancel. Selections survive rotation.
- Against the actual Mac prompt, both runes and the path remain visible beneath
  the picker: `scratch/wheel-real-prompt-grid.png`. Selected 12/6/dashes →
  `FRIEND` and used Enter code, which sends the word and Return. The prompt
  closed back to the game's menus, without a rejection prompt.
- Screenshots: `wheel-top-half-offline.png`, `wheel-outer-bundled.png`,
  `wheel-inner-bundled.png`, `wheel-path-top-half.png`,
  `wheel-landscape-grid-scrolled.png`, and `wheel-real-prompt-answer.png`.

## Live area-map prototype

The map appears above the Mac display by default. **PoolRad → Show live map**
toggles it and stores the choice locally. Opening the virtual keyboard reserves
its space below the guest. The map uses spare portrait height where possible;
on smaller windows the guest scales down without overlapping either pane.

This is the **area position**, not a tactical combat map. Area transitions,
combat/wilderness recognition, and area names are not yet validated. Do not
interpret a retained local-area position as the combatant's tile or world-map
location. This is not yet a complete-game mapper.

The game supplies the loaded map, so there is no map-file import step and no
game map bundled in the APK. The full area is shown, not an explored-only map.
All door bit types use the same outline; secret/locked semantics are not claimed.

If a read becomes unavailable after a valid map, retain its geometry but hide
the arrow and label it **Last area · position unavailable**. This avoids a whole
map disappearing on a transient read while never showing an old arrow as live.

### Native profile and update path

`POOLRAD.h` implements one observed game-version profile:

- Low-memory `CurApName` at `0x910` must exactly match the Pascal string
  `Pool of Radiance v1.1` (21 characters).
- Read big-endian `CurrentA5` at `0x904`. The geometry **handle** is stored at
  `A5 - 15168`; dereference the handle to obtain the movable map block.
- The adjacent X/Y bytes are at `A5 - 15086`, followed by facing. Observed
  west/south values are 6/4; the profile interprets 0/2/4/6 as N/E/S/W.
- Validate alignment, every address/range, the 1,024-byte heap payload size,
  16×16 coordinate bounds, and the even facing value. Reject empty geometry:
  the code-wheel screen allocates a zeroed map with default 15,1 W globals.
- The four 256-byte map planes come directly from that handle; they have no
  two-byte DAX record prefix. `PoolRadState` adapts them to the existing `GeoMap`.

These are **Mac v1.1 observations**, not DOS addresses. Only A5-relative offsets
are fixed; A5, the master pointer, and the map allocation are resolved each time.
`tools/test-map-probe.c` exercises relocated synthetic addresses and invalid
pointers with the exact same native reader used by the APK.

The UI requests at most one sample per 250 ms while resumed and visible. An
atomic request is serviced between emulated CPU ticks. JNI returns a 1,200-byte
`PRM1` packet (identity/pointers, 128 nearby global bytes, and 1,024 geometry bytes)
or null. No full RAM scan, file write, or network call is part of live polling.
Java compares position/facing/geometry before invalidating the view; unrelated
global changes don't cause map redraws. Pause/hide removes the polling callback;
core identity and generation checks discard obsolete queued UI updates.

Native checks, using synthetic data only:

```sh
cc -std=c11 -Wall -Wextra -Werror -fsanitize=address,undefined tools/test-map-probe.c -o scratch/test-map-probe
scratch/test-map-probe
```

To inspect an existing private RAM capture with the production native reader:

```sh
cc -std=c11 -Wall -Wextra -Werror tools/probe-ram.c -o scratch/probe-ram
scratch/probe-ram scratch/EXAMPLE.ram > scratch/EXAMPLE.probe
```

The CLI prints coordinates/pointers to stderr and the small binary packet to
stdout. Unavailable state exits nonzero with no packet. Keep both RAM and probe
files private; the latter still contains game geometry.

### Completed smoke checks (2026-09-13)

- All four configured Mac II ABIs built, with a normal universal APK.
- 24 JUnit tests passed (13 DAX/GEO tests, 5 wheel tests, 6 live-state tests).
- Native probe tests passed with AddressSanitizer/UndefinedBehaviorSanitizer:
  identity, null/short buffers, relocation, invalid pointers, heap bounds,
  coordinates/facing, empty startup geometry, and geometry payload copying.
- All 29 local maps parsed; every cell/direction lookup exercised.
- Actual game boot, code-wheel answer, and sample-party load succeeded.
- Private RAM snapshots captured while the game remained running.
- The native rune download cached all 72 illustrations; glyphs and lookup worked
  with the test emulator's Wi-Fi and mobile data disabled.
- Native **Enter code** selected 21/14/dashes → `WYVERN`, typed it, pressed
  Return, and passed the actual Mac game's startup check. No external key event
  supplied the Return in that test.

The checked screenshots live only in `scratch/`. `tools/android-ui.mjs` selects
host controls by fresh accessibility labels, avoiding timing-sensitive taps.
It requires an explicit emulator serial and refuses physical-device targets.

### Discovery captures and remaining coverage

Debug **PoolRad → Capture RAM** copies RAM between CPU ticks, then writes the
copy on a worker thread to private `files/snapshots/`. No native RAM pointer is
read from the UI thread. On the tested 8 MiB Mac II, captures are 8,388,608 bytes.
Pull snapshots only from the local test app using `adb exec-out run-as`; never
commit them or put them in an APK.

`tools/InspectMaps.java` and `tools/InspectRam.java` use the same Java DAX/GEO
decoder as the app. Compile with the mapper classes to a directory in scratch.
The latter finds geometry candidates, **not** confirmed active-map pointers.

Two labeled captures show the game reporting `15,1 W`, then `11,2 S` during its
opening tour. Map 0 wall data matched at `0x71d9e8`; the X/Y bytes changed
correctly at `0x7677ba`, with facing `6 → 4`. CurrentA5 was `0x76b2a8`, the map
handle `0x668538`. The A5-relative reader now reproduces both labeled states,
and rejects `scratch/startup.ram` (no party, zeroed map). None of those absolute
heap addresses are embedded in the tracker.

After a later cold boot and sample-party load, the Android pane automatically
drew New Phlan and its `15,1 W` arrow. The screenshot is
`scratch/live-map-party.png`. A subsequent boot with version 0.2.0 confirmed
the startup guard in the Android pane, then the sample-party load and tour:

| Stop | Map pane | Independent guest readout | Evidence in scratch |
| --- | --- | --- | --- |
| Rolf introduction | 15,1 W | 15,1 W | `map-final-party-ready.png` |
| Temple of Tyr | 11,2 S | 11,2 S | `map-tour-stop-1.png` |
| Training school | 5,2 E | 5,2 E | `map-tour-stop-3.png` |

The UI probe also observed 5,3 N while the tour was moving. One transient sample
was unavailable; this prompted the explicit last-area/no-arrow presentation.
The map follows game state during scripted movement, not an estimate from keys.
After the tour, Android number keys exercised ordinary movement: `6` turned
`0,4 W → 0,4 N`, `8` stepped to `0,3 N`, `6` faced `0,3 E`, and another `8`
hit a solid east wall without changing coordinates or moving the map marker.
Both the game and pane matched in `map-manual-turn.png`, `map-manual-step.png`,
`map-facing-wall.png`, and `map-blocked-step.png`.
Area transitions and combat/wilderness still require separate checks.
Keyboard-open layout was checked in
`map-keyboard-open.png`; panes remain separate at 1200×1600.
The PoolRad toggle removed/restored the map, and Android Home → resume restored
the live `5,2 E` position without rebooting the guest (`map-resumed.png`).
The last-area/no-arrow presentation and non-focusable map adjustment were built
after that Android route; they were code-reviewed, not rerun through a full
guest boot. The 24 Java tests and all four ABI builds pass on the final source.

One switch to 8× during Mac OS startup was followed by a guest divide-by-zero
error. A subsequent cold boot at the default 1× succeeded; use 1× until Finder
for this test disk. No Android app crash appeared in the checked crash log.

Local runner note: two long-lived terminal emulator processes exited gracefully
during testing (exit 0, no app-crash evidence in their exit logs). The current
AVD runs under `systemd-run --user --unit=poolrad-map-test --collect ...` so it is
not tied to a tool terminal session. This is test infrastructure, not an APK
dependency. Inspect it with `systemctl --user status poolrad-map-test`.
