# Install PoolRad Mac Maps

This is an Android APK, not a website. Download the
[0.8.0 prototype](https://github.com/huntergdavis/poolrad-macmaps/releases/tag/v0.8.0)
or build from source below. No ROM, Mac system disk, game disk, or save is supplied.
The universal Mac II APK supports ARM64, ARMv7, x86, and x86_64 on Android 5.0+.

## On your tablet

1. **Get the APK onto the tablet.** Download
   [`poolrad-macmaps-0.8.0.apk`](https://github.com/huntergdavis/poolrad-macmaps/releases/download/v0.8.0/poolrad-macmaps-0.8.0.apk)
   directly, copy it to Downloads using USB, or browse your
   computer's SMB share with [Material Files](https://f-droid.org/packages/me.zhanghai.android.files/).
2. **Tap the APK and install.** If Android asks, allow installation from that
   file manager. You can disable that permission afterward. Updates signed with
   the same key install over the existing app; do not uninstall just to update.
3. **Open Pool of Radiance.** Tap **Browse…** and select your own matching
   Macintosh II ROM. This fork installs separately from the stock emulator.
4. **Import copies of your disks.** Use the floppy-disk menu → **Import file…**
   for a bootable Mac system disk and an HFS disk containing your Mac game.
   Select them from the floppy-disk menu to insert them. Keep original disks and
   saves backed up elsewhere. Numbered `disk1.dsk`, `disk2.dsk`, etc. are picked
   up by the existing startup automount behavior.
5. **Launch Pool of Radiance inside the Mac.** Verified code-wheel prompts are
   answered automatically using ordinary keystrokes and Return. If recognition
   is unavailable or you have already started typing, use **PoolRad → Code wheel
   lookup**, select the runes/path, then **Enter code** as the manual fallback.
   Load/create your party normally; the map appears when supported area state
   is available. For automatic launch from one combined disk, use the optional
   [personal boot-disk builder](PERSONAL_BOOT.md).

For a private APK that supplies **your own files** on first launch, see the
[personal-package guide](PERSONAL_PACKAGE.md). It skips steps 3–5 on a fresh
installation when the combined disk includes the startup alias. Existing
installs are left alone, and updates never replace their writable disks.
The public APK above remains bring-your-own-files.

The tested configuration is the Mac II flavor with a 256 KiB Mac II ROM and
the game application named `Pool of Radiance v1.1`. The mapper currently relies
on that exact application identity; renaming it can stop tracking.
The Mac Plus flavor needs its own matching ROM and has not been runtime-tested.

The **keyboard icon** opens the keyboard beneath the game.
**PoolRad → Show live map** toggles the map. Rune lookup and its pickers stay
in the upper half without dimming the game.
When recognized party records are available, names, current/max HP and black
health bars appear beside the map. Unknown health is not guessed or retained.

**PoolRad → Screenshot** captures the current map, game, and visible keyboard.
Choose **Save PNG…** to keep it in Downloads or another location, or **Share
PNG…** to open Android's share chooser. Menus and system bars are not part of
the picture. Unsaved captures are temporary; use Save for pictures you want to keep.

**PoolRad → Desktop appearance** previews White, Mist or Stonework for the
supported System 7.5.5 guest desktop. Save and quit the game, use the Mac's
**Special → Shut Down**, wait for **Restart Emulator**, then Apply and restart.
**Original** restores only the saved desktop setting, never an old campaign.
Do not use Force Power Off. [Desktop guide and supported disks](DESKTOP_APPEARANCE.md).

**Levels & skills**, **Spells**, **Weapons & armor**, and **Money conversion** are offline PoolRad
menu tools. Their own touch controls stay above the game; they never edit your
characters. Sources and any unverified Macintosh-specific values are identified.
Spells and equipment use finite browsable lists, not text search or a keyboard.
Equipment details distinguish the printed rules from the Macintosh item values.

**Tap a map tile** to add a flag, or an existing symbol to reopen its page.
Draw on the map at left and write in the white space at right. Pen, eraser,
undo/redo and **Close & save** affect only that page. The symbol button chooses
smithy, temple, monster and other personal labels. No annotation checkbox or
separate drawing menu is needed. **Notebooks** selects a separate campaign;
changing a Mac save does not select a notebook for you.
[Handwriting guide](NOTEBOOK.md) · [Map-plus-writing pages](MAP_INK.md).

**Pen only** optionally reserves handwriting for a reported stylus and remembers
your choice. Pinch with two fingers to zoom; drag with two fingers (or one in
pen-only mode) to move the page. **Fit page** restores the complete sheet.
[Pen controls and device limitations](PEN_NOTES.md).

**Update in place to retain your notes.** Uninstalling or clearing app data
removes them. **PoolRad → Notebooks → Back up Notebook N…** saves that campaign
as a `.prnb` file outside the app. **Restore backup…** restores without overwriting
another notebook; select the restored campaign yourself. Each flag page also
has **Save PNG** for a readable picture, not an editable backup.
[Backup, restore and deliberate removal guide](NOTEBOOK_BACKUPS.md).

### If something doesn't work

- **Can't install:** use the universal APK, not an IDE test-only build. Check
  available storage and whether an existing installation uses a different
  signing key. Back up app data before considering an uninstall.
- **Mac doesn't boot:** check that the ROM matches the selected machine and
  the inserted system disk is bootable. Start at normal emulation speed.
- **Game won't open:** a `.SIT` archive isn't a disk image. Extract it with Mac
  resource forks intact and install it on an HFS disk; copying only the ordinary
  data file can produce an empty/unlaunchable Mac application.
- **Waiting for party:** launch the supported game and load a party. Full-game
  area transitions and combat/wilderness contexts are not yet fully validated.
- **SMB install fails:** copy the APK from the share to local Downloads, then
  open that copy. Your tablet and SMB server need network connectivity.

## Build an APK from source

Install JDK 17 and Android Studio's SDK tools. Through SDK Manager,
install Android SDK 34, Build-Tools 34.0.0, and NDK 27.0.12077973. Point
`ANDROID_HOME` at that SDK (or configure `android/local.properties`).

```sh
git clone https://github.com/huntergdavis/poolrad-macmaps.git
cd poolrad-macmaps
cd android
./gradlew :minivmac:assembleMacIIDebug :minivmac:testMacIIDebugUnitTest
```

All 72 rune illustrations are already included in the repository and APK.
There is no artwork-download step and no runtime dependency on the reference
site. See [artwork credits](../licenses/CODE_WHEEL_ARTWORK.md). The first source
build still needs internet access for ordinary Gradle/Android dependencies.

Your APK is here:

```text
android/minivmac/build/outputs/apk/macII/debug/minivmac-macII-universal-debug.apk
```

Keep the same signing key for future updates. The public prototype is debug-signed
with the same key as the earlier personal builds; dedicated production release
signing is not configured. Your own source build normally uses a different key,
so it cannot update over the downloadable APK without resolving that difference.
For local testing,
private game-disk preparation, and exact validation coverage, see
[LOCAL_TESTING.md](LOCAL_TESTING.md).
