# Install PoolRad Mac Maps

This is an Android APK, not a website. The public repository currently provides
source, not a prebuilt public APK download. Build it below, or use a trusted
personal build. No ROM, Mac system disk, game disk, or save is supplied.

## On your tablet

1. **Get the APK onto the tablet.** Copy
   `minivmac-macII-universal-debug.apk` to Downloads using USB, or browse your
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
5. **Launch Pool of Radiance inside the Mac.** At the code-wheel prompt use
   **PoolRad → Code wheel lookup**, select the runes/path, then **Enter code**.
   Load/create your party normally; the map appears when supported area state
   is available. Automatic game launch is still on the backlog.

The tested configuration is the Mac II flavor with a 256 KiB Mac II ROM and
the game application named `Pool of Radiance v1.1`. The mapper currently relies
on that exact application identity; renaming it can stop tracking.
The Mac Plus flavor needs its own matching ROM and has not been runtime-tested.

The **keyboard icon** opens the keyboard beneath the game.
**PoolRad → Show live map** toggles the map. Rune lookup and its pickers stay
in the upper half without dimming the game.

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

Keep the same signing key for future updates. The current build is debug-signed;
public release signing/distribution is not configured. For local testing,
private game-disk preparation, and exact validation coverage, see
[LOCAL_TESTING.md](LOCAL_TESTING.md).
