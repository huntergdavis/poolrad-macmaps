# Your personal, ready-to-play APK

Build an APK containing **your own** matching Mac II ROM and writable-disk
starting point. First launch verifies and imports those files, selects the
Mac II profile, and boots. A combined disk with the normal System 7 startup
alias then opens Pool of Radiance automatically. No ROM picker or disk import
is needed on a fresh installation.

The public download still contains **no ROM, system disk, game or saves**.
This opt-in build is for private use; keep its inputs and APK out of Git and
public releases. Putting files in an APK does not change their redistribution
requirements. Nothing is downloaded by this helper or at runtime.

## Prepare once

Start with the [Android build prerequisites](INSTALL.md#build-an-apk-from-source).
Python 3 is also required. Keep original media and current saves backed up.
For one-disk automatic launch, first use the existing
[personal boot-disk builder](PERSONAL_BOOT.md). Give it a **current, unmounted
copy of your campaign disk**: a workstation test image is not automatically
your tablet's latest party.

From the repository root, using new output directories:

```sh
python3 tools/prepare-personal-package.py prepare \
  --rom scratch/input/MacII.ROM \
  --disk scratch/personal-startup/disk1.dsk \
  --output scratch/my-private-package \
  --acknowledge-private-assets
python3 tools/prepare-personal-package.py verify scratch/my-private-package
```

The helper copies; it never mounts or modifies the input. It accepts a supported
256 KiB Macintosh II ROM and one to eight raw, 512-byte-aligned disks, at most
128 MiB each / 256 MiB total. Repeat `--disk` for additional disks in mount order;
the first must be bootable. The prepared names are `MacII.ROM`, `disk1.dsk`, etc.
The manifest records exact sizes and SHA-256 hashes. The full ROM word checksum
must also match a supported Mac II signature. Unknown files, symlinks, malformed
metadata or an existing output directory are rejected.

This checks package integrity and the ROM profile, not the health of every
game archive, HFS file or save. The earlier `ITEM2.DAX` input warning remains
[tracked](LOCAL_TESTING.md#resource-fork-safe-test-disk).

## Build and install

From `android/`, substitute the absolute path to that prepared directory:

```sh
./gradlew :minivmac:assembleMacIIPersonal \
  -PpoolradPersonalBundle=/absolute/path/to/scratch/my-private-package
```

Your private universal APK is:

```text
android/minivmac/build/outputs/apk/macII/personal/minivmac-macII-universal-personal.apk
```

Copy it to your tablet and install as usual. The version is marked `-personal`.
The Mac II app ID and signing configuration are shared with the ordinary build,
so updates from the **same signing key** install in place. Your local source
build normally has a different key from the public downloadable prototype;
do not uninstall a campaign to work around that without backing it up first.

Private assets belong only to the `personal` build type. Ordinary
`assembleMacIIDebug` / release builds do not include them, even if the property
or generated private assets remain present. The public APK checker rejects
the entire `assets/personal/` directory as well as ROM/disk files. There is no
Mac Plus personal variant.

## What happens to saves?

- Only a fresh app with no configured ROM or existing imported media receives
  the bundle. Existing installations keep their ROM, disks, settings and notes.
- First use copies and verifies in a hidden staging directory before publishing
  the complete package. The game cannot mount a half-copied disk.
- The installed disks are ordinary writable copies. Updates, including a
  switch back to the public build, keep using them; they never restore the
  bundled starting save. Intentionally removed/replaced disks stay that way.
- Handwritten notebooks remain in their existing, separate local storage.
  [Back up each notebook](NOTEBOOK_BACKUPS.md) and your guest disks separately.
- Rebuilding an APK with a newer starting disk is **not** a campaign update.
  Import/move your files deliberately with the existing tools instead.

Allow space for the APK, installed app assets, and a separate writable copy.
Uninstalling or clearing app data deletes that writable copy and your notes.
The APK is a starting point, not a backup of subsequent play.

## Recovery stays available

If verification or local writing fails, the setup screen offers **Retry personal
setup** and **Use my own files**. The latter remembers your choice and opens the
ordinary ROM/disk setup. It does not delete or overwrite media. Free storage
before retrying a write failure.

The normal ROM settings, disk import/export tools and guest Finder remain
available after setup. Quit the game and shut down the guest before copying
or replacing a writable disk. To stop automatic game launch, remove its normal
Startup Items alias using the [Finder recovery instructions](PERSONAL_BOOT.md);
there is no game executable patch or shortcut around original game rules.

A damaged installation receipt is reported rather than silently booting a
different empty disk directory. Back up surviving media before repairing app
storage. The package is never automatically recopied over a damaged campaign.

## Checks

```sh
python3 tools/test-personal-package.py -v
cd android
./gradlew :minivmac:testMacIIDebugUnitTest
```

The fixtures are synthetic; no ROM or game bytes are checked into tests.
[Local acceptance evidence](LOCAL_TESTING.md) distinguishes emulator checks
from physical e-ink/stylus testing, which remains unperformed.
