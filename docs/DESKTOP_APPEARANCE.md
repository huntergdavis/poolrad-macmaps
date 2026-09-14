# A quieter Mac desktop

**PoolRad → Desktop appearance** changes the actual emulated Mac desktop,
not an Android overlay. Game windows, rules and campaign data are not edited.
Use your own imported copy of the supported System 7.5.5 boot disk.

## Preview, apply, restart

1. Save your game and quit Pool of Radiance.
2. In the Mac's Finder, choose **Special → Shut Down**. Wait until the Android
   screen offers **Restart Emulator**. Do **not** use Force Power Off.
3. Open **PoolRad → Desktop appearance**. Select your boot disk if more than
   one disk is imported. Use **Check stopped disk** if needed.
4. Preview **White** (plain), **Mist** (light stippling), or **Stonework**
   (a small monochrome masonry pattern). Selecting a preview changes nothing.
5. Tap **Apply White/Mist/Stonework desktop**. Wait for the saved confirmation,
   close the panel, then tap **Restart Emulator**.

The game may reopen automatically if your disk has the usual startup alias.
Appearance changes require a stopped guest and no disk import or file mutation
in progress.
Unsupported or unclean disks are rejected; normal shutdown is not optional.

## Restore the original

Follow the same shutdown steps, select **Original**, then tap
**Restore original desktop** and restart.

Original means the **first desktop resource settings preserved by this tool**,
even if applying a style subsequently needed a retry. It is not the last style
selected and not an old disk image.
Restoration edits those settings in your **current** disk, preserving subsequent
campaign saves. The original setting remains available after restoration.

The small, checksum-checked `.prda` backup stays in the app's private
`desktop-appearance` folder, separate from imported disks and notebooks.
It is tied to the disk's volume identity and imported path; a renamed or
reimported disk may not find the previous backup. Clearing app data or uninstalling
removes it. Keep separate disk and notebook backups: this is not campaign backup.

## Scope and safeguards

- Supports the project's verified **System 7.5.5 resource layout** on a bare
  HFS disk up to 128 MiB. Other system versions, layouts and arbitrary wallpaper
  utilities are not supported. There is no image-import option yet.
- Only the imported app-private disk copy changes. Original source files
  elsewhere are untouched.
- The tool stages a complete copy, preserves the original settings, checks that
  the source has not changed, then replaces the imported file atomically.
  Allow free space for another copy of the disk. Failed preparation does not
  fall back to overwriting the live file or repairing its filesystem.
- Damaged setting backups cause an error rather than silent replacement.

Synthetic tests cover resource-only changes, interruption cleanup, source-change
rejection and restoration without rolling back newer save bytes.
Guest acceptance is recorded separately in [local testing](LOCAL_TESTING.md).
Physical tablet, stylus and e-ink acceptance remains unperformed.

## Why these resources?

Apple distinguishes the pattern **collection** in Desktop Pattern Prefs from
the active selection stored in the System file.
[Apple TIL 20999](https://savagetaylor.com/TIL/TIL20999.pdf)

The supported editor changes only the existing `PAT ` 16 / `ppat` 16
appearance bytes. Apple documents the desktop's binary/color pattern paths
and the separate on-disk pixel-pattern layout:
[SetDeskCPat](https://dev.os9.ca/techpubs/mac/Toolbox/Toolbox-273.html),
[pixel-pattern resources](https://dev.os9.ca/techpubs/mac/QuickDraw/QuickDraw-266.html).
It does not inject guest code or paint over game windows.
