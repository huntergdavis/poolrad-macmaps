# The saved game, and who has already decoded one

Research done 2026-09-18 for F33/F34.

**The result changed the project's boundary.** The research was commissioned
under a decision that we would *not* write our own save format. On reading it
the owner reversed that: "I take it back, we're going to be writing our own
saves and we'll need to document and publish the format." The plan is now to
align the Macintosh format, verify it, publish the specification, and build a
converter that turns any platform's save into a Macintosh one — so parties
other people played, at any point in the game, can be loaded here for testing.
Recorded in [DESIGN.md](DESIGN.md); the ordered work is the save block of
[BACKLOG.md](BACKLOG.md), F49 and F77 through F81.

## The short answer

**Nobody has decoded the Macintosh save.** Every public project found targets
the DOS, C64 or Amiga line. Searching for Macintosh Gold Box reverse
engineering now returns *this repository* among the results, which is its own
kind of answer.

**But the record is documented field-by-field on three other platforms,** and
those platforms are the same record at different sizes. That turns the Mac job
from a decode into an alignment.

## What exists

| Project | Platform | What it has | Use to us |
| --- | --- | --- | --- |
| [malcyon/wish](https://github.com/malcyon/wish) | C64, reads/writes DOS and Amiga saves | `docs/README.md` covers the container, the character record and the **save game layout**; `goldbox/dos_layout.py` records what differs between four record layouts | The closest thing to a Rosetta stone |
| [Gold Box Companion](https://gbc.zorbus.net/) | DOS under DOSBox | Ships `formats.zip` describing character save file formats, plus lists of effects and items | Field meanings |
| [Gold Box Explorer](https://github.com/bsimser/Gold-Box-Explorer) | DOS | Views and exports Gold Box game files | Data files, not saves |
| [Amiga-dev wiki](http://amiga-dev.wikidot.com/project:pool-of-radiance) | Amiga | Data file formats; ByteKiller 2.0 unpacking | Background |
| [OpenGold](https://github.com/stdarg/OpenGold) | DOS data | Reimplementation that loads original records, unknown bytes retained | Field meanings |

## Sizes, which are the encouraging part

| Platform | Character record |
| --- | --- |
| DOS | 285 bytes |
| Amiga | 288 bytes |
| **Macintosh (this project's own measurement)** | **302 bytes** |

`wish` documents the Amiga record as *the DOS record plus padding*, applied
through an `amiga_por_offset` table, big-endian. Amiga is also big-endian 68k.
The Mac is 14 bytes off the Amiga, which is the shape of a port that kept the
field order and changed the alignment — not a different design. So the likely
job is aligning a documented layout against the offsets this project has
already found on its own (name `+0x00`, class `+0x2f`, maxHP `+0x32`,
encumbrance `+0x10e`, chain `+0x110`, own handle `+0x114`, condition `+0x118`,
quick `+0x11b`, AC `+0x11d`, attacks `+0x120`, currentHP `+0x12b`, movement
`+0x12c`), rather than starting from nothing.

## Two cautions

**These are save editors.** Every one of them edits stats, which is outside
this project's boundary and stays outside it. What is useful here is their
knowledge of *where the fields are*, for reading a save in order to load it.
Take the layout knowledge, not the code, and not the purpose.

**A Mac save is not just a record.** The measured file is a 12,906-byte data
fork plus a ~4.4 KB resource fork. The DOS-lineage documentation describes the
character and party structures inside a save; it says nothing about how the Mac
port arranges its two forks around them. That part is ours to work out.

## Architecture note, offered as validation

Gold Box Companion reads **game memory rather than save files**, and says so
plainly: its character editor "reads/modifies memory so it's instant compared
to save file editors." That is independently the same architecture this project
arrived at, for the same reason.


## Why the converter is the point

`wish` already converts between DOS, C64 and Amiga saves. **Nobody has the
Macintosh side.** Adding it would make this the only route by which a party
played anywhere else reaches the Macintosh port — and for this project
specifically it solves a problem the scripting harness cannot solve at any
price. `tools/play.py` can reach a battle in the slums; it cannot reach the
endgame, and it will not be able to for a very long time. A converted save can,
immediately, and with a party somebody actually played.

That also means the format work has a second audience. Publishing the spec is
the part that outlives this app.

## Rules for anything that writes

A bad save costs the owner his game, so these are not style preferences.

1. **Back up the save disk first.** F49 is a prerequisite for this block, not a
   nice-to-have. Today the disk image is the only copy of his saves.
2. **Never overwrite an existing save.** Construct into a new file.
3. **Verify by loading.** No save is claimed to work until the game has loaded
   it and the party reads back correctly.


## The converter adjusts; it does not edit

Owner's requirement, 2026-09-18: "if the mac version has different level caps
or is missing certain resources etc, the converter should understand that and
adjust."

Ports are not identical, so a converter that copies fields across and hopes will
produce a party the Macintosh game cannot represent or will not load. It has to
know this version's own limits — which is why F82, establishing the Macintosh
level caps, class and race lists, and item and spell tables, comes before F81.

The line between adjusting and editing is where this stays honest, and it is
drawn three ways:

1. **Never adjust upward.** Bringing a level-9 fighter down to the Macintosh cap
   is conversion. Raising a score, a level or a hit point is an edit, and needs
   the owner's per-edit sign-off like any other.
2. **Never adjust silently.** Every adjustment is named in a report shown before
   the save is written — what changed, from what, to what, and why. A conversion
   that quietly loses a spellbook is worse than one that refuses.
3. **Refuse rather than invent.** A class this port does not have, an item that
   does not exist here: stop and say what blocked it. Do not substitute the
   nearest thing and carry on.

This matters beyond our own testing. If the specification is published (F80)
and the converter with it, these adjustments are what other people's parties
will be subjected to, and a silent one would be a bug in someone else's
campaign rather than ours.
