# The game's own keyboard equivalents

Found 2026-09-18 while building F33, by reading the `MENU` resources out of the
game application's resource fork — the same reader F78 built for saved games.

**Every menu command this project needs has a Command-key equivalent.** That
changes the shape of all the save automation: nothing has to find a menu title
on screen, click at a measured coordinate, or read pixels to tell whether an
item is enabled. A key combination is exact, and it costs nothing to send.

## File

| | |
| --- | --- |
| `Cmd-L` | Load Saved Game… |
| `Cmd-S` | Save Current Game… |
| `Cmd-B` | Begin Adventuring |
| `Cmd-Q` | Quit |

## Character

| | |
| --- | --- |
| `Cmd-N` | Create New Character… |
| `Cmd-D` | Drop Character… |
| `Cmd-M` | Modify Character… |
| `Cmd-T` | Train Character… |
| `Cmd-E` | View Character… |
| `Cmd-A` | Add Character To Party… |
| `Cmd-R` | Remove Character From Party |

## Edit

`Cmd-Z` undo, `Cmd-X` cut, `Cmd-C` copy, `Cmd-V` paste.

## Options, which has no equivalents

Sounds · Walking Sounds · Use Compass · Hide Windows in Background · Reset
Window Locations. These are checkbox items with no key, so anything wanting them
still needs the menu itself.

## Why this matters beyond F33

- **F33 and F34 (loading)** need `Cmd-Q` then `Cmd-L`, and no coordinates.
- **F37 (auto-save)** needs `Cmd-S`.
- **F43 (long-press a row for the character sheet)** is `Cmd-E`.
- **F39 and F52 (memorise, rest)** reach the training and camp screens the same
  way where a menu is involved.

## The caution that still applies

A keyboard equivalent is *sent* reliably; it is not *accepted* reliably. The
game greys Load Saved Game out while a game is running, and a disabled item
ignores its own key just as it ignores a click. `tools/play.py` records what
happens when that goes unnoticed: the folder and save names get typed into the
running game instead, "where a stray letter opened the camp menu and the party
was found altering its marching order".

So the gate stays what it was going to be anyway: **check whether a game is
running by reading guest memory**, before sending anything. The keys remove the
need to read the screen; they do not remove the need to know the state.
