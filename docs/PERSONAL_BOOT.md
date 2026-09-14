# One personal boot disk

`tools/prepare-personal-boot.py` combines your **System 7 HFS boot disk** and
**Pool of Radiance game disk** into one private 32 MiB HFS image. It retains the
original filesystem and adds the game plus a normal **Startup Items alias**.
No timed launch clicks, RAM patches, replacement Finder or game download.
The Macintosh ROM is imported separately in the public APK. The optional
[personal APK builder](PERSONAL_PACKAGE.md) can bundle your ROM and this disk
for verified, automatic first-use import; its private output stays out of Git
and public releases.

**Verified locally:** the combined disk cold-boots and automatically launches
the original game on an isolated Android emulator. The code-wheel helper
completes its answer and Return without guest launch/input clicks. The copied
SampleParty loads with its map and six-member health display, and the separate
desktop-only recovery image cold-boots to Finder. **Physical tablet/e-ink testing
is still unperformed.**

Earlier experimental **machfs-reconstructed** images failed in General Controls
and must not be deployed. That writer path has been removed. The current builder
preserves the existing catalog rather than recreating it.

## Build locally

Close the game, shut down the guest safely, and export copies of the boot and
writable game disks. **Use the game disk containing your current saves**, not a
pristine archive. Never copy a disk while the emulator is writing to it.

The builder now rejects empty/malformed DAX before creating a combined disk.
If an older input contains the empty ITEM2 extraction, follow the
[archive-integrity findings](ARCHIVE_CHECK.md) first. Do not substitute a fresh
sample campaign for a disk containing your saves; installed disks are not
automatically repaired by an APK update.

Install Python 3, venv support and `hfsutils` through your OS package manager.
From this repository:

```sh
mkdir -p scratch/personal-startup scratch/personal-recovery
python3 -m venv scratch/personal-boot-venv
scratch/personal-boot-venv/bin/python -m pip install \
  -r tools/personal-boot-requirements.txt

scratch/personal-boot-venv/bin/python tools/prepare-personal-boot.py \
  YOUR-BOOT-COPY.dsk YOUR-CURRENT-GAME-COPY.dsk scratch/personal-startup/disk1.dsk
```

This is a **bounded personal builder**, not an HFS repair/conversion utility:
a clean raw HFS boot disk smaller than 32 MiB, System 7, an empty original
Startup Items folder and the original v1.1 game application are required.
Partitioned disks, dirty/locked boot volumes, allocation formats needing more than
65,535 blocks, nonstandard startup overrides and file collisions fail without
overwriting inputs. Only 32 MiB output is supported. Run one hfsutils operation
at a time: those tools share the host user's current-volume state.

Existing disks **and manifests are never overwritten**, including dangling
symlinks. Inputs are read-only; HFS mount/check operations use disposable copies.
The private adjacent manifest records input/output SHA-256 hashes, original
catalog IDs, fork hashes, alias targets, warnings and verification results.
**Neither disk nor manifest belongs in git, Releases or a public APK.**

## Preservation and checks

- Keep the original 1,024 boot-block bytes, blessed System Folder ID, volume
  name/creation identity and entire original allocation area. Expand the bitmap
  and shift allocation storage intact: HFS extents are relative to its start.
  Allocation block size never changes.
- Append with `hfsutils`, without reconstructing the catalog. Check every
  original content file/folder CNID before/after, and compare original content
  forks—including existing aliases—exactly. Broken/external aliases are not
  rewritten or repaired. Boot desktop caches remain in the original allocation
  area rather than being regenerated.
- Copy game and save files through **MacBinary II**, retaining both forks,
  types, creators, Finder flags and creation/modification dates. Parent directory
  modification dates and copied game backup dates are not promised identical.
  Names stay `Pool Of Radiance` and `Pool of Radiance v1.1`; the original RAM
  profile still applies. No gameplay data is edited.
- A maintained version-2 Alias encoder creates only the new
  `System Folder:Startup Items:Pool of Radiance` alias, using the actual
  destination application/folder CNIDs. An independent reader resolves it back
  to the game. Separate MacBinary exports recheck every original content fork
  and the System Folder blessing.
- Recheck original source hashes before publishing a complete image. A failed
  offline validation publishes no disk. Offline checks alone are **not** proof
  of cold boot, automatic launch or saved-party loading.

## Import and recover

With the guest shut down, use **Manage Disks → Export** to preserve current
imported boot/game images outside the app. Verify those backups before removing
the old imported copies from the active list. Import the combined image **named
`disk1.dsk`**, with **only that disk** in the active list.

The emulator automatically mounts `disk1.dsk`, then `disk2.dsk`, etc. Ejecting an
old numbered disk does not stop it returning on the next boot; an arbitrary
imported filename does not enable automatic mounting. This builder does not
delete or replace any existing imports. Keep recovery/original images outside
the active list. Verify your saved party before retiring any backup.

The game retains its normal Quit command. To stop later automatic launches, quit
to Finder and move the alias out of Startup Items; application and saves stay
in place. Alternatively build a desktop-only recovery image:

```sh
scratch/personal-boot-venv/bin/python tools/prepare-personal-boot.py \
  YOUR-BOOT-COPY.dsk YOUR-CURRENT-GAME-COPY.dsk \
  scratch/personal-recovery/disk1.dsk --no-startup
```

Recovery contains the game and supplied saves, without the startup alias. It is
a **point-in-time copy**: never overwrite later progress with an older recovery
image. Preserve the later disk first. No special Shift-key behavior is assumed.

## Local evidence, 2026-09-13

The supplied 24 MiB boot volume is `Mini vMac Boot v2`, with 2,027,008 free bytes.
The actual System `vers` resource identifies **7.5.5**; the blessed System Folder
is CNID **173** and Startup Items is empty. The game has **108 content files**,
including saves with both forks. The earlier acceptance disk preserved and
reported an already-empty `PoolRad2/ITEM2.DAX`; that was not a repair. Q2 later
recovered and verified the original file on new copies, and the current builder
rejects that bad input before copying. [Evidence and existing-save limits](ARCHIVE_CHECK.md).

The preserved 32 MiB boot-only control reaches Finder on isolated emulator
**5582**, with the same original ROM and `libmnvmcoreii.so`/8 MiB guest-memory
profile as the successful original boot copy. The combined disk also reaches
Finder and automatically opens the game, with exactly one imported `disk1.dsk`.
The 23:09 cold boot reached the wheel, and at 23:12 the six readback-confirmed
characters plus Return completed automatically. The original game's
Character/Options/Windows menus appeared; no Finder launch gestures were used.
At 23:18, normal File → Load Saved Game → PoolRadSave → SampleParty loaded the
copied save: **New Phlan, 15,1 W**, with all six names and HP **12/12, 8/8, 7/7,
10/10, 9/9, 9/9**, matching the original game's Information window. Rolf's intro
tutorial then appeared with the same live map and health display.

The successful gameplay test copy was preserved separately. At 23:20 the
isolated app was stopped and only the `--no-startup` recovery copy was installed
as `disk1.dsk`; at **23:22 it reached the normal Finder desktop**, with the
File/Edit/View/Label/Special menus and no game auto-launch. This recovery-image
boot—not an unverified Quit interaction during the modal intro—is the tested
recovery path. Source hashes remain unchanged. Primary emulator 5580 and its
files were not used.

**13 synthetic tests pass:** actual HFS growth/append/read round trips, full
allocation-area preservation, exact existing alias forks/new alias target,
both save forks, recovery mode, independent hfsutils checks, collision and
overwrite/symlink refusal, bad startup/System/application inputs, dirty/truncated
images, and inconsistent/overlapping bitmaps. No original System, ROM, game or
character bytes appear in the fixtures.

```sh
scratch/personal-boot-venv/bin/python tools/test-personal-boot.py -v
```

## Sources and reuse

- Apple, [Macintosh User's Guide (1991)](https://vintageapple.org/macmanuals/pdf/Macintosh_Users_Guide_1991.pdf),
  “Specifying which items you want opened at startup”: System 7 Startup Items,
  not modern macOS login items.
- Apple, [HFS volume organization](https://dev.os9.ca/techpubs/mac/Files/Files-100.html)
  and [volume bitmap](https://dev.os9.ca/techpubs/mac/Files/Files-103.html): relative
  allocation storage and 65,535-block/16-bitmap-block limit.
- [mac_alias](https://github.com/dmgbuild/mac_alias), MIT,
  [Alias API](https://mac-alias.readthedocs.io/en/latest/mac_alias.html): pinned
  2.2.3 version-2 encoder, not hand-invented alias bytes.
- [machfs](https://github.com/elliotnunn/machfs), Elliot Nunn, MIT: pinned 1.3
  independent reader and synthetic fixtures, **not the production boot catalog
  writer**. `macresources` 1.2 handles resource forks.
- [hfsutils](https://www.mars.org/home/rob/proj/hfs/): existing catalog updates,
  MacBinary fork transport and independent `hls -id` checks. MacBinary wrapper
  layout reuses the repository's `tools/prepare-test-game.mjs` approach.
- [Local testing notes](LOCAL_TESTING.md) established fork-preserving inputs.
  Prior-session `deja` recalls timed out; this work uses the supplied copies,
  observed controls and cited implementations.
