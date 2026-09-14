# Game-faithful comfort features

Researched 2026-09-13 for the handwritten notebook / party-sidebar expansion.
These are design proposals, not claims that we have decoded these Mac RAM fields.

## Primary references

- [SSI's original Pool of Radiance rulebook and journal, archived PDF](https://www.bestoldgames.net/download/games/pool-of-radiance/pool-of-radiance-mac-manual.pdf).
  The archive labels this a Mac manual, but the text includes DOS-specific
  commands. Use it for original game rules; verify Mac UI and data separately.
- [Gold Box Companion author's feature list](https://gbc.zorbus.net/).
  Its HUD, note-capable map, journal access, and labeled save backups are useful
  precedents. Its editing, healing, teleport, and rule-bypass features are not
  proposed here. This is inspiration, not a source-code port or a Mac RAM profile.

## Original-game facts that matter

The rulebook describes six player-character slots plus two NPC slots (PDF p. 7),
descending AC (p. 19), status states and readied equipment (p. 10), training at
the guild (p. 8), carried weight affecting movement (p. 20), memorized spell uses
and rest (pp. 23–25), and search consuming additional travel time (p. 14).
The journal uses numbered entries, proclamations, and tavern tales, some untrue;
the book warns against reading ahead (p. 31). These references are PDF page
numbers, not necessarily the printed pagination.

Design implications: permit NPC/reordered rows; use a real HP fraction and
condition shape, not an invented mana system; show training/spell/load detail
on demand. Keep journal bookmarks player-led and spoiler-conscious. Read actual
guest state rather than infer it from our inputs or assumed PC save formats.

## Reference panels requested by the user

The five menu actions are Levels & skills, Spells, Weapons & armor, Money
conversion, and Adventure journal lookup. They share the top-half/no-dim
presentation already used by the rune helper. Use an in-panel numeric keypad
for journal numbers and money amounts; a system keyboard must not obscure the
guest prompt. Searchable lists can offer touch-first filters as well.

Table extraction/verification is future work: skill progression, equipment
values, spell parameters, and exchange ratios must come from the supplied game
and its original references, with tests for transcription errors. Journal
lookup should prefer the original files already present in the user's archive,
retain entry diagrams, separate categories, and reject nonexistent numbers.
Do not assume sample numbers in a feature request are actual valid entries.

## Handwriting interaction details

This becomes a digital graph-paper notebook, not another input method for the
emulated game. A small flag is an address into a large handwritten sheet.
Direct map sketches and flag-linked sheets are distinct stroke layers; erasing
one must not silently remove the other. Store normalized/map-space vector
strokes and an explicit area/run key locally, preserving quality when enlarged.

Use explicit annotate mode, generous hit targets, a black pen and eraser,
undo/redo, autosave, and an exportable notebook. Prefer simple stroke erasure
first, then refine partial-stroke erasure if the tablet workflow benefits.
Any palm rejection, stylus eraser button, or pressure support is a device test,
not a cross-tablet promise. A stable area key is needed before persistent flags.

## Recommended visual arrangement

```text
+----------------------------+------------------------+
| AREA MAP                   | PARTY                  |
|                           >| [icon] Name  AC   HP   |
| [flag]  handwritten marks   | [icon] Name  AC   HP   |
|                            | ...                    |
+----------------------------+------------------------+
|              ORIGINAL MAC GAME                      |
+-----------------------------------------------------+
|              KEYBOARD (when open)                    |
+-----------------------------------------------------+
```

This moves the map within the **upper pane**, not beside the whole Mac display.
On narrow windows, collapse the party strip. Detailed information lives behind
a row tap. Final Fantasy is a visual reference only; use original monochrome
class/face symbols or user-supplied art, not copied Final Fantasy sprites.

## Desktop appearance recommendation

Start with a flat, quiet guest desktop. It puts contrast into the game and notes
instead of competing with them. Offer an optional original one-bit fantasy
motif or a user-imported picture later, with Restore original always available.
Avoid noisy dithered scenery behind windows on e-ink.

The visible Mac wallpaper is guest state. An Android background setting cannot
replace it. Inspect the boot disk's actual desktop utility/settings before
choosing a guest startup setting, resource, or small guest-side helper. Apply
only to the writable personal copy with a recovery path; never mask arbitrary
framebuffer regions and risk painting over the game.

## Screenshots

Capture our own composed app image after the menu is dismissed, including the
map/ink/flags and guest framebuffer. Save/share an ordinary PNG locally. Verify
the existing display implementation before choosing view capture versus explicit
frame composition; some rendering paths need special handling. A full-device
recording service or permanent screen-capture permission is unnecessary scope.

Concrete slices and priority live in [BACKLOG.md](BACKLOG.md).
