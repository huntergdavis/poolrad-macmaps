# Making a boot disk that starts Pool of Radiance by itself

The companion can close the game, restart the machine and load a saved game
without you watching it happen — but only if the machine comes back up running
the game. This is how to make a startup disk that does that.

It is also just nicer to use: switch the tablet on and you are in the game,
not in the Finder.

## The whole trick

System 7 opens everything inside **`System Folder:Startup Items`** when it
boots. So the game needs to be reachable from there.

**Put an alias there, not the game and not the game's folder.**

1. Boot the Macintosh normally, into the Finder.
2. Find `Pool of Radiance v1.1` — the application itself, type `APPL`, about
   327 KB of resource fork. Not the folder it lives in.
3. Select it and choose **File → Make Alias**. An item called
   `Pool of Radiance v1.1 alias` appears beside it.
4. Drag that alias into `System Folder:Startup Items`. Rename it if you like;
   the name does not matter, the alias does.
5. Restart. The game should open on its own.

The owner's `poolrad-slim-boot.dsk` is exactly this: one item in Startup Items,
`Pool of Radiance`, type **`adrp`** creator **`prad`**, no data fork and a
664-byte resource fork. That is what an alias looks like from outside.

## The mistake worth avoiding

An earlier disk in this project put **all fifteen game items directly into
Startup Items** — the application, the journals, the rule books, the `PoolRad2`,
`PoolRad3`, `PoolRad4` and `PoolRadGen` folders, the lot.

System 7 dutifully opened every one of them. The machine came up with the game
running *and* a stack of text windows and folder windows over it, which is slow,
untidy, and in a small emulated screen actively confusing.

**One alias to the application. Nothing else.**

## What the companion needs from it

- The game must be running within about two and a half minutes of a restart.
  A Macintosh boot plus the game's own start-up fits inside that comfortably on
  the disks tested here.
- The saved games the companion lists come from **any** mounted disk with a
  `Pool Of Radiance:PoolRadSave` folder on it, so the saves do not have to live
  on the startup disk.
- If the game does not start itself, the companion says so and stops rather
  than going looking for it in the Finder. Typing a name into the Finder to
  select an application only works when that application is in the frontmost
  window, and when it is not, the typing lands on something else entirely —
  which is how one test run renamed a journal and another opened Apple Extras.

## Checking it worked

From a shell with the emulator running:

```sh
python3 tools/play.py --serial emulator-NNNN state
```

Straight after a restart this should say something other than *"Pool of Radiance
is not running"* within a couple of minutes. If it does, the companion's Load
will work.
