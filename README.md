# PoolRad Mac Maps

[Build status](https://github.com/huntergdavis/poolrad-macmaps/actions/workflows/ci.yml)

**Play Macintosh Pool of Radiance on Android, with a live map above the game.**

Built for an e-ink tablet, with party details, handwritten notes and the
adventurer's journal close at hand. Runs in a fork of Mini vMac.
Works offline. No root or account needed.

## Install

[Download the 0.110.0 APK](https://github.com/huntergdavis/poolrad-macmaps/releases/download/v0.110.0/poolrad-macmaps-0.110.0.apk)
for **Android 5.0 or later**.

1. Tap the APK and allow installation from your file manager when prompted.
2. Open **Pool of Radiance** and select your own matching Macintosh II ROM.
3. Import copies of your Mac system and game disks through the disk menu.
4. Launch the game and load or create your party.

**Bring your own ROM, Mac system disk and Macintosh game.** None are included.
The tested game is **Pool of Radiance v1.1**; keep its original application
name so the map can recognize it.

Updates install over the existing app. Back up your notebooks, game saves and
disks before uninstalling or clearing app data.
[Setup and troubleshooting](docs/INSTALL.md).

## Find your way

- See walls, doors, coordinates, your facing direction and the game's own clock.
  The map recognizes 29 named areas.
- Track walked squares and directional footprints. Marks show doors you have
  not crossed, places you fought and discoveries the app recorded.
- Turn on **fog of war** in **Info → Options** to hide unwalked squares.
  Fog and footprint settings are remembered for each area.
- Tap the map header to highlight your party's square.
- Use **−/+** to zoom and drag to look around. **Fit** resets the view.
  [Map and combat zoom](docs/MAP_ZOOM.md).
- **Original area tile size (1:1)** in **Info → Options** starts map squares at
  32 screen pixels. With it enabled, **1:1** replaces **Fit** and resets that
  scale. Tap the header to bring your party back into view.
  [See the map size option](docs/MAP_SCALE.md).
- Tap **⏎** in the map's lower-right corner to press Return in the game.
- Open **Info → Map legend** for the symbols.
- **Connections** shows passages between areas you have visited.
  Arrows record only the directions you traveled.
  Tap an area to browse its neighbors; **Current area** returns the view to
  your party. Routes stay with your notebook and its backups. Loading a save
  adds no route. [See the Connections view](docs/AREA_CONNECTIONS.md).
- **Exit previews:** stand on an exit square you have already used to see
  its destination beside your map. Only explored squares appear, even with fog
  off. Tap the preview to browse multiple destinations. Older notebooks gain
  previews as you revisit areas. [See an exit preview](docs/NEIGHBOR_PREVIEW.md).

When your position is unavailable, the app hides the party arrow and labels any
retained map as a reference. Combat has its own overview.

## Check your party

Names, health, armor class and class symbols sit beside the map.
The heading counts anyone hurt, down, waiting for rest or ready to train.

- **Tap a character** for conditions, spells, readied gear, carried weight and
  experience needed for the next level. This also selects them in the game's
  Information window.
- **Hold a character's row** to open their original game sheet.
- **Q** toggles that character's quick combat mode. A busy game queues the change.
- **T** marks someone eligible to train. They still need a suitable training hall.
- **NPC** marks a non-player party member.
- **Info → Marching order** lists everyone from first to last.
- In the original game, memorize chosen spells with **Magic → Rest** before
  leaving camp.
  Timed **Camp → Rest** does not memorize them. Leaving camp clears those
  choices. [Memorizing steps and verification](docs/SPELL_READINESS.md#how-memorizing-actually-works-2026-09-19).

**PoolRad → Rest until healed** previews full healing, waking Unconscious or
Dying members, and memorizing chosen spells. Confirm it to apply those changes
outside combat. It does not advance time, cure poison, revive the dead or save
your game. The companion HP display updates after healing; the original game
window may still show old numbers. [Rest helper guide and limits](docs/REST_UNTIL_HEALED.md).

**Info → Money** shows each purse, party coin totals and their gold value.
Gems and jewelry are counted separately. Open the coin converter from this page.

**Info → Since last rest** shows observed fights and spell casts since the
last detected rest, with the game's rest time. Until a rest is detected, it
counts from loading the game. Snapshots keep their own counts.
[Counting rules and test coverage](docs/SINCE_LAST_REST.md).

Each fight opens with your party and enemies in view. Use **−/+** to zoom,
drag to look around, or **Fit** to see the full 50 × 25-square arena.
Tap the battle header to frame the fighters again. The view keeps your chosen
zoom and position as the fight continues. It names the monsters and counts
those still standing. A ring and a marked party row identify the acting party member. Tap a party marker to highlight its row. Crosses distinguish
fallen characters who can still be helped from those who are dead or petrified.
Take your combat actions in the original game below.
[Combat view and verification](docs/MAP_ZOOM.md).

## Keep a notebook

Tap a map tile to open a page with the map on the left and writing space on
the right.

- **Template** adds a blank grid, ruled list or blank map frame to the writing
  half. Plain paper is the default.
- Changing templates keeps your handwriting. Erasing removes ink, not the guides.
- Each page remembers its template. Templates appear in backups, images and PDFs.
  [Template guide](docs/NOTE_TEMPLATES.md).

Notes open only when you choose a map tile or a note in the index. Entering an
area never opens or switches a notebook page, and closing a note leaves it
closed. [Notebook opening behavior](docs/AREA_NOTE_FOLLOW.md).

- Draw with a pen or one finger. Use two fingers to zoom and move the page.
- Erase your ink, undo or redo, and choose from nine note symbols. Notes autosave.
- Keep separate notebooks for separate campaigns.
- Open **Info → Notes index** to browse saved notes by area and date.
- Choose **Export all notes as a PDF…** in the index to share your handwriting.
  PDF pages include the area and tile, but not the map's walls.
- Use **PoolRad → Notebooks → Back up everything…** to save all notebooks and
  fog and footprint settings in one file. It includes notes, trails and journal history.
  Game saves, emulator snapshots and disks need separate backups.

After five quiet seconds at a recognized player prompt, the game sleeps while
you read or write notes. Your next game action wakes it. Timed rest and
automatic combat keep running. [Idle behavior and verification](docs/AUTOMATIC_IDLE.md).

[Handwriting guide](docs/NOTEBOOK.md) · [Notebook backups](docs/NOTEBOOK_BACKUPS.md)

## Read without leaving the game

You can tap the game below an open companion page, such as Saves or Money.
The page stays open. Keyboard input still goes to the page; close it to use
game keys. [Page behavior](docs/TABS.md#pages-stay-out-of-the-games-way-2026-09-20).

**Info → Journal** includes all 58 journal entries, 18 proclamations,
23 tavern tales and 14 original illustrations. References detected in game
messages collect in your notebook; a **Read** notice opens a new citation.

**Info → Message log** keeps the latest 200 distinct messages the app observed
in the current notebook.

Turn on **Info → Options → Auto-skip informational messages** to dismiss
supported routine notices. It starts off. Story prompts, choices and
confirmations still wait for you. [Which notices it skips](docs/AUTO_SKIP_MESSAGES.md).

The Info tab also has spells, weapons and armor, and class progression tables.
Code-wheel prompts are answered automatically when recognized, with an
illustrated manual lookup as a fallback.

Turning off **Options → Sounds** in the original game also stops the app's
audio work. **Walking Sounds** controls footsteps.
[Sound behavior](docs/AUDIO_MUTE.md).

## Save and resume

- **PoolRad → Quick save** keeps your ten latest quick snapshots.
- After a fight's results and treasure screens, the app bandages Dying party
  members if someone is still standing, then takes a quick save. Bandaged
  characters remain Unconscious at zero hit points. A quick save is taken
  even when nobody needs bandaging.
  [Behavior and test limits](docs/PARTY.md#the-bandage-write-f40-2026-09-19).
- **Quick load** immediately restores the newest quick save.
- **Load…** lists quick, named and automatic saves with dates and screen previews.
  Choose one to preview and load.
- **Info → Saves** shows your last loaded or manually saved snapshot, how many
  saves you have, and how much space is left. It estimates how many more saves
  will fit. Use **Load… → Delete…** to remove an old save.
  [See the Saves page](docs/SAVE_STATES.md#f98--info--saves).
- Each snapshot remembers its notebook.
- **Info → Options** turns on autosaves. The app checks every five minutes
  while a party is in the world and keeps the latest 20 automatic snapshots.
  A check due during automatic idle waits until the game wakes.
  It skips a check if it has seen no activity since the last successful snapshot
  or restore. Game input, changes to the party, map or messages, and disk
  activity all count. [Autosave details](docs/SAVE_STATES.md#f103--autosaves-that-have-nothing-to-protect).

On launch, the app tries to resume your last completed snapshot. Its disks must
match and its notebook must still exist. Otherwise, the Mac boots normally.
Choose **Start normally** to skip the attempt, or turn it off in **Info → Options**.
That page also controls compact party rows and larger game messages.

Switching away pauses the emulator and stops its background work until you
return. [Pause and resume](docs/PAUSED_IDLE.md).

**Snapshots restore the running Mac, not its disk contents.** Loading requires
the mounted disks to match exactly; a mismatch leaves your current game intact.
Snapshots made before version 0.85 are unsupported.
Original-game saves are unaffected.

Back up original-game saves through **Settings → Back up / restore game saves…**
and keep separate disk backups. Quit the game and use the Mac's normal shutdown
before stopping the emulator; a forced quit can damage a disk.
[Save-state details](docs/SAVE_STATES.md).

## What's still limited

This is a prototype tested with Macintosh Pool of Radiance v1.1.
Tracking has been checked in New Phlan and on the Slums gate round trip.
Full-game area transitions and newly recruited NPCs still need testing.
The combat overview shows no terrain. In the wilderness, use the game's own map;
a separate companion map is not planned.

Stylus drawing and two-finger zoom/scroll have been tested on a real e-ink
tablet. Rotation and keyboard layout still need checking.
[Current backlog](docs/BACKLOG.md).

## About the project

Inspired by [Gold Box Companion](https://gbc.zorbus.net/).
This is an independent Macintosh/Android project, not a port or an official
game release.

Built on [Mini vMac for Android](android/UPSTREAM.md) (GPLv2).
Game map-format work credits [Gold Box Explorer](licenses/GoldBoxExplorer-MIT.txt)
(MIT). Code-wheel artwork is [bundled and credited](licenses/CODE_WHEEL_ARTWORK.md).

[Build and test](docs/LOCAL_TESTING.md) · [Design](docs/DESIGN.md) ·
[Research](docs/RESEARCH.md) · [Save-file format](docs/SAVE_FORMAT.md) ·
[Personal boot disk](docs/PERSONAL_BOOT.md) · [Personal APK](docs/PERSONAL_PACKAGE.md)
