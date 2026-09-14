# Local Android prototype

## Build

Use JDK 17, Android SDK 34, build-tools 34.0.0, and NDK 27.0.12077973.
The 72 rune GIFs are checked in under
`android/minivmac/src/main/assets/codewheel/`: `esp01.gif`–`esp36.gif` and
`det01.gif`–`det36.gif`. No external artwork preparation or runtime download is
needed. The original numbering and unchanged reference images are credited in
[CODE_WHEEL_ARTWORK.md](../licenses/CODE_WHEEL_ARTWORK.md).
`verifyRuneArtwork` rejects missing, oversized, or invalid-header/dimension images.

From `android/`:

```sh
./gradlew :minivmac:assembleMacIIDebug :minivmac:testMacIIDebugUnitTest
```

Set `JAVA_HOME` and `ANDROID_HOME` to the local installations if necessary.
Normal builds produce debug-signed APKs in
`android/minivmac/build/outputs/apk/macII/debug/`, including ARM64 and universal
variants. Debug signing is suitable for this prototype; keep the same personal
key for future updates. The public prototype APK uses that same debug key;
dedicated production signing is not configured.

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

## W1 desktop appearance acceptance (0.8.0, 2026-09-14)

Both public and personal universal Mac II APKs build with JDK 17. The full
unit task passes **251 tests, zero failures/errors/skips**: 13 new bounded-HFS
desktop tests, 17 setting-store tests and eight disk-access tests, plus the
213 existing tests. The 16 personal-package, 25 source-fetch and 13 personal
boot-helper tests pass, as do all three native reader sanitizer suites.
The public artifact checker verifies all 72 unchanged rune pictures and no
ROM, disk, game archive or private personal-package assets.

The new tests use synthetic disks, including a fragmented System resource
fork and an ordinary saved-game region. They check allocated/overlapping
extents, overflow chains, malformed resources, dirty/locked disks, unsupported
profiles, exact appearance-only edits, and restoration while retaining a newer
save byte. Store checks cover original-backup corruption, path confinement,
failed publication, same-size/mtime source mutation, cleanup and an existing
reader keeping its original inode. Maintenance refuses running emulation and
disk import/mutation; only native exit plus handle cleanup releases that gate.
Pausing or merely observing a non-ready screen is insufficient.

Actual acceptance uses the separate AOSP API 30 `poolrad-package-test` AVD on
`emulator-5584` at 1200×1600, density 200. The original campaign emulator on
5580 is untouched. The test uses its existing writable 32 MiB combined disk,
not an original input or replacement campaign image.

- A normal game Quit → Finder Special → Shut Down reached Restart Emulator
  before updating 0.7.0 to 0.8.0. The original game then booted again normally.
- During emulation, White/Mist/Stonework previews are available but Check/Apply
  are disabled, with explicit clean-shutdown instructions. The guest remains
  visible and undimmed below the existing upper-half dialog.
- Normal guest shutdown under the new gate enables the disk check. The actual
  System 7.5.5 resources validate, and Apply White completes through the menu.
  A 380-byte original-setting record is saved in private app storage.
- Comparing **every byte of the 32 MiB disk** immediately before/after Apply
  finds exactly 57 changed bytes, all inside the permitted `PAT ` 16 / `ppat`
  16 appearance fields. Catalogs, executable resources, saves and every other
  byte are unchanged. Both mono and color forms are updated; no RAM patch or
  Android paint-over is involved.
- Restart Emulator boots to a genuinely white guest desktop with the original
  disk, utility and Trash icons intact. Early Welcome-to-Mac boot frames still
  use the ROM's checker pattern; the selected System desktop appears afterward.
- The startup alias still launches the original game and automatic wheel entry
  still succeeds. Loading the existing SampleParty reaches Rolf's introduction;
  guest and live map both show New Phlan 15,1 W. Original game windows remain
  intact on the white background. Fresh-save companion HP is still unavailable,
  as recorded for B1; this appearance change does not claim to fix that reader.
- After another clean shutdown, the final APK is installed in place. Its
  installed SHA-256 exactly matches the public release artifact. It boots with
  the White setting intact; normal Quit/Shut Down makes the original setting
  available again, so the backup survives the update and process restart.
- That final artifact applies Mist and Stonework from their previews. Relative
  to the stopped white disk they change 16 and 30 appearance bytes respectively,
  with zero other changes. Their binary patterns, fallback bits and color
  pixels agree. Controls, Close and Back are locked during saving. These two
  styles are checked through real menu/disk operations, not separate guest boots.
- Original restores all **190 original resource bytes exactly**, while every
  other byte still matches the current disk. **1,008 bytes changed by the
  intervening normal guest sessions are retained**, not rolled back to the first
  before-image. These are filesystem/session changes, not a claimed newly saved
  campaign; later campaign-byte preservation is also covered synthetically.
- Restarting the final artifact after restoration shows the original gray
  checker desktop and the original game launching again (`w1-restored-desktop.png`).

Evidence remains private in `scratch/w1-running-preview.png`,
`w1-white-saved.png`, `w1-white-desktop.png`, `w1-party-file-menu.png`,
`w1-stone-preview.png`, `w1-original-restored-ui.png`, and stopped before/after
disk copies.
The supplied original ROM and source disks are not modified or published.
An Android SystemUI ANR occurred during the old 0.7.0 cold-start setup, before
installing this change; selecting Wait allowed that session to continue. This
is not claimed fixed by W1. The checked 0.8.0 Android crash buffer is empty.
Physical tablet, stylus, e-ink, broader System versions and arbitrary wallpaper
utilities remain untested. No GitHub Actions or physical-device CI is claimed.

## B2 source-fetch build acceptance (2026-09-14)

This is build tooling on `main` after 0.7.0, not a new Android runtime version.
`python3 tools/test-fetch-personal-assets.py -v` passes **25 focused tests**:
strict metadata/source pins, no-follow redirects, streaming lengths/hashes,
ROM integrity, finite read timeouts, safe input-labeled errors, stage cleanup,
private output confinement, and immutable/offline cache checks. The tests use
synthetic bytes; the redirect test exercises the real handler against loopback.
The existing 16 personal-package tests and three native sanitizer suites pass.
The Android unit task was rerun, not merely accepted as up to date: **213 tests,
zero failures/errors/skips**.

A separate local HTTPS server serves a checksum-valid but nonbootable synthetic
ROM and a 512-byte fake disk. Its short-lived test certificate is trusted only
through `SSL_CERT_FILE` in the test build environment; production TLS validation
is not disabled. The actual Gradle source-manifest route makes exactly two
requests, verifies and packages those exact bytes in the Mac II universal
personal APK. It also builds the public APK, whose artifact checker confirms
all 72 unchanged rune GIFs and no `assets/personal/` metadata or private payload.

Missing acknowledgement, conflicting bundle/source properties and an offline
cache miss all fail **before any request**. With the HTTPS server stopped,
offline preparation succeeds from its verified cache. Changing that fixture's
cached disk then makes preparation fail even though old generated assets exist;
it neither repairs the input nor falls back to the old assets. Restoring only
the synthetic fixture permits the next offline preparation. A separate public
build also succeeds with deliberately nonexistent/conflicting private paths,
confirming it does not resolve private configuration at all.

The ignored local route and logs are `scratch/check-b2-build.py` and
`android/minivmac/private-assets/b2-acceptance-2n8mkpiu/`. No real game or firmware
was served over the network, and no fetched input or personal APK was uploaded.
The earlier `poolradPersonalBundle` route is also rebuilt using the existing
private B1 bundle, leaving local personal APK output with the supplied starting
media rather than synthetic test bytes. No guest is started for these checks.
Runtime, current campaigns, signing configuration and public APK version stay
unchanged. Physical tablet/stylus/e-ink acceptance remains pending.

## 0.7.0 personal-package acceptance (2026-09-14)

Both Mac II universal/all-four-ABI variants build, versionCode 72, with
**213 Java tests, zero failures/errors/skips**. The 15 new installer tests use
synthetic ROM/disks and cover verified atomic publication, exact lengths/hashes,
strict metadata, legacy-media refusal, mutable/deleted media preservation,
failure cleanup, publication races and root lookup during a blocked copy.
`python3 tools/test-personal-package.py -v` passes all 16 synthetic builder
tests. All three existing native probe suites also pass under address/undefined
sanitizers. These are focused local checks, not new CI or a long soak matrix.

The public APK was built in the same invocation as the personal APK, with the
private-bundle property present and generated private assets retained. The
public artifact checker passes: all 72 rune GIFs are unchanged, no ROM/disk/game
archives, and no `assets/personal/` payload or metadata. The personal assets are
present only in the opt-in variant. A subsequent `preparePersonalAssets`
invocation **without** the property fails with the explicit input-path error,
despite the cached prior private build. Both APKs use the existing prototype
signing certificate.

The separate API 30 `poolrad-package-test` AVD on `emulator-5584` is configured
at 1200×1600 / density 200 before boot. The existing campaign on `emulator-5580`
is not used or reset for these checks. Personal input is the previously verified
F9 combined System 7/game disk plus the supplied matching Mac II ROM, copied
through the new helper into ignored `scratch/b1-private-bundle`. Original ROM
and combined-disk hashes remain unchanged.

A read-only app-files directory on an otherwise empty disposable installation
provokes a real setup failure before publication. **Use my own files** shows
the ordinary ROM browser and remembers the choice after reopening; no bundled
media appears. A temporary setup View initially remained behind the welcome
fragment; removing it before the handoff fixed that observed defect, and the
repeated UI check shows only the normal welcome controls. After resetting only
this still-empty fixture, the same failure followed by restoring permission and
**Retry personal setup** publishes the ROM/disk and starts the guest without
ROM-selection or disk-import clicks. Its normal startup alias reaches the
original game's title screen without a Finder launch click.

Re-opening during copying does not hold the UI on the installer's copy lock;
that backend interleaving has a bounded concurrency test. The brief FileManager
root-selection step is separately synchronized to prevent stale legacy paths
from replacing the newly published personal paths. Error/recovery content is
scrollable. Neither measure claims physical rotation/stylus/e-ink acceptance.
See [personal packaging and recovery](PERSONAL_PACKAGE.md).

The shell-managed AVD processes exited during this route; a separate-session
launch (`setsid -f`) was used to continue without resetting the device. Restarting
the same AVD retained its installed data. An Android System UI startup timeout
was dismissed with Wait; its event identifies `SystemUIAuxiliaryDumpService`
at 03:47:14, before PoolRad was launched at 03:47:35. With PoolRad stopped,
SHA-256 comparison across both a personal APK
update and an update back to the public APK proves the disk, ROM, manifest and
receipt all remain byte-identical. The disk already differs from the bundled
starting image because of ordinary guest writes; updates do not restore that
starting image. On relaunch the public APK uses the retained personal disk,
including the original Mac's expected improper-shutdown warning after the AVD
interruption. A normal OK click continues its startup alias into the game.
This is not claimed as an uninterrupted boot or physical-device acceptance.
On the separate-session emulator, the public APK's retained SampleParty loads
through the original File → Load picker to New Phlan 15,1 W, with the live map
and Rolf's opening tutorial visible. No party statistics, movement or save
contents are edited to obtain this result.
Companion HP bars were not visible on that fresh SampleParty; this does not
repeat the earlier postcombat health acceptance. Party polling is enabled and
unchanged by B1. Fresh-save slot/heap coverage is recorded under P1, with the
cause still unverified; no RAM capture or guessed health was substituted.
After normal game Quit and Finder Shut Down, Manage Disks lists the retained
32 MiB `disk1.dsk`. Export resolves its new personal-storage FileProvider URI
and opens Android's chooser without an invalid-root error. This bare AVD has
no app accepting that disk type; no recipient is selected and actual external
disk-file delivery is untested. The existing Import control remains available.

Final public universal APK SHA-256:
`837e5f313c95f13b6830b08ace58df6748fb5df5743b0243221b6919d7629834`.
The personal APK is separate, kept locally in ignored scratch storage, and is
not a public release asset.

## 0.6.0 notebook protection acceptance (2026-09-14)

The universal/all-four-ABI build succeeds, versionCode 71. **198 Java tests,
zero failures/errors/skips**, three native sanitizer suites, six actual Android
page-image checks (`tools/NotePageImageCheck.java`) and eight stopped-core input
checks (`java tools/StoppedCoreInputCheck.java`) pass. The latter extracts the
actual listener/key-release methods against a recording Core; it is not physical
input acceptance. It covers a separately observed 0.5.3 null-Core mouse crash
after normal guest shutdown. The fix uses captured, ready Core references.

On the API 30 emulator, a disposable Notebook 3 receives a Smithy flag at New
Phlan 2,2 and a stroke crossing the map/writing boundary. Its PNG saved through
Android's document picker matches the immutable prepared PNG byte-for-byte
(SHA-256 `1d94dd3ac4d75a6e39bb529b1d9a9a6a89c889c6709c82a8154b8d6f03302f0d`).
Visual inspection confirms a legible 1600×768 heading, complete map, Smithy
symbol and the full stroke. The game stays at the sample party's opening tour,
15,1 W; no game statistics or save contents are edited.

A complete `.prnb` backup saved to Downloads is also byte-identical to its
prepared source. Cancelling an earlier export reports no saved backup; the
next export succeeds. Cancelling Remove leaves the note hash unchanged. A
confirmed removal retires only Notebook 3, then the file-picker restore brings
back the same UUID, tile, Smithy symbol and exact vector bytes. The reopened
page visually agrees. Reimporting the same file reports an existing-notebook
error without overwrite; Notebook 1 remains active until explicitly switched.
The original temple note and retained `.v1` bytes survive the in-place upgrade
and all transfer operations unchanged.

The final APK (including the input guard) was also installed in place after
normal game Quit and Finder Shut Down. Both original records and the restored
test note remained byte-identical. APK SHA-256:
`0026d638fe96e26c6ca9830b96f23815a56b4991dd366bb2dcf7af5394d2df00`.
The prototype signing certificate is unchanged; all 72 rune pictures match the
source and no ROMs, disks or game archives are bundled.

Storage tests additionally cover all-area/legacy/blank flags, label collisions,
checksums, malformed/truncated/trailing/oversized input, unsafe paths, unknown
records, failed staging publication, failed removal and post-commit cleanup.
Transfer-file tests cover write/flush failures, immutable retry sources and
safe pending-file restoration/expiry. These are software checks, not a claim
that every Android document provider or process-death scenario was exercised.
No CI is configured. Physical pen/e-ink acceptance and real rotation remain open.

### Android document-picker test input

On this emulator, ordinary `adb shell input tap` activated dialog buttons but
did not select DocumentsUI file rows. The explicit-finger helper below selected
the same rows successfully; no production code change was needed. It refuses
non-emulator hardware. Inspect the current picker bounds before choosing x/y;
coordinates below are an example, not a script for arbitrary screens.

```sh
picker_check=$(mktemp -d scratch/document-tap.XXXXXX)
javac --release 8 -cp /usr/lib/android-sdk/platforms/android-34/android.jar \
  -d "$picker_check/classes" tools/DocumentPickerTap.java
jar cf "$picker_check/classes.jar" -C "$picker_check/classes" .
/usr/lib/android-sdk/build-tools/34.0.0/d8 --min-api 21 \
  --output "$picker_check/document-tap.zip" "$picker_check/classes.jar"
adb -s emulator-5580 push "$picker_check/document-tap.zip" /data/local/tmp/
adb -s emulator-5580 shell \
  'CLASSPATH=/data/local/tmp/document-tap.zip app_process /system/bin DocumentPickerTap 310 425'
```

## 0.5.3 pen-note software acceptance (2026-09-14)

The Mac II universal/all-four-ABI build succeeds, versionCode 70. **170 Java
tests, zero failures/errors/skips**, all three native sanitizer suites, 13 new
synthetic Android pen-input checks, six existing composite-sheet checks and
seven party-pane View checks pass. The input harness exercises the actual
`InkSheetView` with Android pointer events, not a replacement input model.
[Controls, test details and physical acceptance checklist](PEN_NOTES.md).

An in-place update retains the original temple note and its migration backup
byte-for-byte. A disposable page accepts finger ink with Pen only off; enabling
Pen only prevents further finger gestures from changing the stored note bytes.
All 72 bundled rune images remain unchanged; no ROMs, disks, game archives or
private notes are in the public APK. The prototype signing certificate is
unchanged. These are local emulator/software checks, not CI or physical pen/
e-ink acceptance. N3 remains open for the actual tablet check.

The installed candidate's nearby-flag chooser opens the existing 2,2 page
from a finger tap on adjacent tile 1,2, without creating a second note. Pen
only remains enabled after the update/relaunch. A delayed selection was
declined during live sampling; the generation guard rejects changed map/
notebook context, so this is not evidence of uninterrupted mode recognition.

Forced ADB `wm size 480x1000` recreated the activity and rebooted the Mac core;
it did **not** validate the open editor's narrow-window toolbar. Restoring
1200×1600 left all saved note/backup hashes unchanged. Test ordinary rotation
and real resizable-window behavior separately under Q1; save the game before
forcing display configuration changes. Do not describe this as seamless resume.

## 0.5.2 party-health acceptance (2026-09-14)

The Mac II universal/all-four-ABI build succeeds, versionCode 69. **152 Java
tests, zero failures/errors/skips**, all three native sanitizer suites, and seven
actual Android health-pane View checks pass. All 72 rune GIFs are unchanged in
the APK; no ROMs, disks or game archives are bundled. The signing certificate
matches the preceding release, and an in-place update preserved both the
existing handwritten note and its original-byte backup exactly.

The real Slums combat list exposed a bug: enemies appended after the six
heroes caused the old eight-record reader to reject the entire list. The
corrected bounded reader distinguishes verified member slots from enemy groups.
The updated APK displays all six heroes during an actual kobold battle,
including new injuries and zero HP, without emitting enemy rows.

Normal game controls moved Zarram from last to first and saved `F7Injured`;
the update reloaded its names/order/HP correctly. After normal spell preparation
and rest, Cure Light Wounds raised Arax from **7/12 to 12/12** in both the guest
and companion pane. No game data was patched. [Evidence and replay/View-check
instructions](PARTY.md). These are local emulator checks, not CI or physical
e-ink/stylus acceptance; broader mode recognition remains open.

## 0.5.1 automatic-wheel acceptance (2026-09-13)

The normal Mac II universal/all-four-ABI build succeeds (versionCode 68), with
**152 Java tests, zero failures/errors/skips**, three native sanitizer suites
and the bundled-artwork/no-ROM-or-disks APK check passing. The same signing
certificate permits an in-place update; the existing note and original-byte
backup hashes were unchanged afterward.

The original game accepted **TEMPLE**, then **BEWARE** after normal game
Quit → Finder → Open, without restarting Android process 16292 or the emulator
core. Each entry read back six letters before one Return, with no outside answer
input. Both enabled Load Saved Game. The second run loaded SampleParty through
the original picker and reached Rolf's introduction with the live map, six HP
rows and the existing Temple flag. [Exact evidence](WHEEL_MEMORY.md).
Physical e-ink/stylus/tablet testing is not claimed.

## 0.5.0 integrated acceptance (2026-09-13)

- Normal Mac II universal build and all four ABI variants succeed, versionCode
  67. **149 Java tests pass**, with zero failures, errors or skipped tests.
  The automatic-input regression verifies that readbacks received while a key
  is still held cannot consume the next letter or Return without dispatching it.
- Native map, wheel and party readers pass their focused address/undefined
  sanitizer checks. All 72 checked-in rune GIFs match the APK; no ROM, disk or
  game archive is bundled in this public candidate.
- Six synthetic Android software-View checks exercise the actual composite
  ink sheet: left map/right writing, cross-sheet pen and ink-only erasing,
  symbol changes, nine distinct glyphs, cancellation and resize anchoring.
  See `tools/CompositeSheetRenderCheck.java` and [the page guide](MAP_INK.md).
- Character-field interpretation is checked against the original Mac executable
  and two private live captures, not inferred from identical full-health values.
  [Party evidence and limits](PARTY.md).
- The wheel reader follows the current input frame and live TextEdit buffer.
  The final candidate automatically typed six verified letters and Return;
  the original game accepted them and enabled Load Saved Game. No outside
  letter/Return input was supplied. The then-open distinct-prompt and game
  exit/relaunch checks are completed in 0.5.1 above. [Evidence](WHEEL_MEMORY.md).
- The existing ten-stroke TYR note at New Phlan 11,2 opened with a map on the
  left and unchanged handwriting on the right. Opening left its old file
  byte-identical; first save created an exact `.ink.v1` backup (SHA-256
  `c8b4c152a48451e712f85bfcca08aef973693f6cb652b7c98c36f8ce8c2e0aaf`).
- Changed that flag to Temple, drew across both halves and erased over a map
  wall: only ink disappeared. Undo/redo reproduced identical saved bytes.
  Undid the two test strokes, closed and reopened: Temple and TYR remained.
  A separate Smithy page at 10,2 retained its own stroke on reopen; explicit
  deletion removed only that test page. The original note/backup survived,
  and the guest stayed at 15,1 W. No standalone map-ink editor is shipped.
- The actual eight-action menu has no Annotate, standalone map editor or
  Capture RAM. Spells browse 55 entries, narrowed to eight Cleric 1 entries
  without search/keyboard. Equipment shows 46 weapons and 11 armor rows;
  banded-mail/chain-mail details and comparison pass in the upper-half panel.
- The sample party's six names and current/max HP display beside the map:
  12/12, 8/8, 7/7, 10/10, 9/9, 9/9. Further live damage/healing/reorder checks
  remain in [PARTY.md](PARTY.md), not inferred from full-health screenshots.
- No physical e-ink, stylus, real-device or full-game-mode acceptance is claimed.
- Published `v0.5.0` at commit `ec873f0`. An anonymous GitHub download matches
  the tested universal APK byte-for-byte; SHA-256 is
  `1ee7606613074028180d85bbca54a281eb723c065bd31fc005758e0f40373010`.
  The existing prototype signing certificate is unchanged.

## 0.4.0 handwritten cartographer acceptance (2026-09-13)

- Normal Mac II universal build and all four ABI variants pass. **90 Java tests**
  pass: the previous 50 plus 9 area-identity, 3 map-hit-coordinate, 10 ink-history,
  4 vector-model, 12 notebook-store, and 2 campaign-selection tests. Native
  address/undefined sanitizer checks and all 72 bundled-rune comparisons pass.
- VersionCode 66, versionName 0.4.0, same prototype signing key. Installed in
  place on the API 30 emulator; existing private ROM/disks remained available.
- Wrote a ten-stroke TYR note at New Phlan tile 11,2 with actual touch input.
  Close/reopen, pen/eraser, undo/redo and proportional portrait/landscape resize
  passed. Map/ink input did not move the guest party. The note file contains the
  selected notebook UUID, area key, tile and vectors, not a screenshot or text.
- Notebook 2 is empty in Browse mode; selecting Notebook 1 restores its flag.
  Confirmed linked deletion removes only a disposable second flag and its ink;
  cancellation retains both. A real permission-denied save leaves the sheet
  open and previous file unchanged, then succeeds when permission is restored.
- Completed the original Rolf tutorial, walked through New Phlan's west gate
  into the Slums (record 20), turned around and returned. The other area's flag
  set was empty; the original flag reappeared at 11,2 with byte-identical ink.
  [Identity and route evidence](AREA_IDENTITY.md), [handwriting evidence](NOTEBOOK.md).
- Android Back saves/closes the note. Force-stop/relaunch retains Notebook 1
  and byte-identical stored ink; startup correctly shows no area flags.
  After the complete Mac boot, the offline helper's 25/33/dots → VULCAN answer
  was accepted. Reloading the original sample party restored the flag in Browse
  mode; opening it showed the same TYR ink at 11,2. Crash buffer was empty.
- Physical e-ink, stylus/palm rejection, full-game area/mode coverage and
  notebook export/import are **not** established by these tests. No original
  game data or private notebook vectors are committed or included in the APK.

## 0.3.0 companion tools acceptance (2026-09-13)

- Built the normal Mac II universal APK and all four ABI variants. All **50 Java
  tests** pass (24 existing + 4 screenshot + 9 level + 7 spell + 6 money tests).
  The small native probe suite also passes with address/undefined sanitizers.
- APK inspection: versionCode 65, versionName 0.3.0, app label **Pool of
  Radiance**, ARM64/ARMv7/x86/x86_64. All 72 rune GIFs match the checked-in assets;
  no ROM, disk, save, RAM dump, or game archive is packaged.
- Upgraded the API 30 local Android emulator without uninstalling. Existing ROM
  and disk imports remain. Network stayed disabled. The actual code prompt
  13/21/dash-two-dots → JUNGLE was accepted through the helper's Enter + Return.
- Levels: Fighter overview, Thief level 2 and race-limit tab verified; all 29
  XP thresholds independently match the supplied Mac appendix. Supplemental
  DOS-based combat/saves/skills remain visibly qualified, not claimed as live
  Macintosh character readings.
- Spells: All → Magic-user, level 1, internal alphabet keypad → `sleep` gives
  one entry. Opened its range/duration/target/use details. Android reports
  `mInputShown=false`. Portrait and 1600×1200 landscape panel/Close bounds remain
  above the midpoint, without dimming the guest; long details scroll.
- Money: entered 5 gp + 1 cp in the real panel. It displays 1,001 cp,
  100 sp + 1 cp, 10 ep + 1 cp, 5 gp + 1 cp, and 1 pp + 1 cp exactly.
- Screenshot: used the actual menu action at New Phlan 15,1 W and 11,2 S. Its
  1200×1420 PNGs contain real map + guest pixels, no menu/system bars, and the
  keyboard when open. Android Save to Downloads produced byte-identical output
  (SHA-256 `318568f4626d0368a82759349445f6c67d5a43084675f10ed154173ce13291f3`
  for the first capture). Cancelled another Save picker and successfully took
  another screenshot afterward. Android's ChooserActivity opened with image/png
  content; no recipient was selected and nothing was transmitted. No screenshot
  errors were logged. These app-generated tutorial PNGs are the README images.
- Screenshot cache filename/restore/copy/expiry logic has focused tests and an
  independent code review. Active Save-picker state is serialized; actual
  process-death recovery was not separately forced in this acceptance run.
- Physical tablet, stylus, e-ink refresh, and broad game-area acceptance are
  still untested here. Handwritten notes and the new companion-tab design are
  not implemented by this release; see the authoritative backlog.

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
to check that all 72 images in the universal APK match the checked-in originals,
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

### Area identity investigation (not a finished notes feature)

`tools/AreaFingerprints.java` reuses our existing DAX/GEO reader to compare
full-geometry SHA-256 fingerprints without exporting map data. The supplied
29 decoded maps have distinct fingerprints; two separate New Phlan live
samples match GEO record 0. This is only a candidate identity scheme. It has
not yet passed cross-area, mutable-map, or note persistence acceptance, and
N1/M1 remain unchecked. The tool never changes the game or its save.

### Current live-map behavior

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
