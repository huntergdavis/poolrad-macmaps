# What the Macintosh version actually supports

Work for F82, done 2026-09-18. The converter (F81) has to bring a party from
another platform inside this version's limits, and it cannot adjust to limits
nobody has written down. Everything here was read out of the game itself.

## Where each fact comes from

| Fact | Source |
| --- | --- |
| Class and race names | the application's own `STRS` resource, read off the game disk |
| Experience thresholds and level caps | the table the game consults when deciding whether a character may train, at `A5-0x15b4`, `0x50` bytes per class |
| Spell levels | the game's own spell table at `A5-0xe84`, already used by the party reader |
| Race numbering | forced by a real saved character, not assumed |

## The headline: four classes are named and cannot be played

| Class | Level cap |
| --- | --- |
| Cleric | 6 |
| Fighter | 8 |
| Magic-User | 6 |
| Thief | 9 |
| **Druid** | **— no thresholds at all** |
| **Paladin** | **— no thresholds at all** |
| **Ranger** | **— no thresholds at all** |
| **Monk** | **— no thresholds at all** |

The game knows all eight names, and the training table has nothing behind four
of them. A character converted from a version that has those classes has
nowhere to go here, and F81 must **say so and refuse**, not pick the nearest
thing. Every combination containing one — `Cleric/Ranger`, for instance — goes
the same way.

The experience thresholds themselves, to reach each level:

- **Cleric** 1,501 · 3,001 · 6,001 · 13,001 · 27,501
- **Fighter** 2,001 · 4,001 · 8,001 · 18,001 · 35,001 · 70,001 · 125,001
- **Magic-User** 2,501 · 5,001 · 10,001 · 22,501 · 40,001
- **Thief** 1,251 · 2,501 · 5,001 · 10,001 · 20,001 · 42,501 · 70,001 · 110,001

A multi-class character meets its lowest wall first, so a Fighter/Magic-User
stops at 6 even though the fighter half could reach 8.

## The eighteen classes, in the game's own order

`Cleric` · `Druid` · `Fighter` · `Paladin` · `Ranger` · `Magic-User` · `Thief` ·
`Monk` · `Cleric/Fighter` · `Cleric/Fighter/Magic-User` · `Cleric/Ranger` ·
`Cleric/Magic-User` · `Cleric/Thief` · `Fighter/Magic-User` · `Fighter/Thief` ·
`Fighter/Magic-User/Thief` · `Magic-User/Thief` · `Monster`

The class byte counts **from zero**. Checked against the owner's own party: a
class byte of 2 is the fighter, 13 the "Spellsword" (Fighter/Magic-User), 14 the
dwarf thief (Fighter/Thief), 0 the cleric.

## The seven races

`Dwarf` · `Elf` · `Gnome` · `Half-Elf` · `Halfling` · `Half-Orc` · `Human`

The race byte counts **from one**, which is forced rather than assumed: a real
saved character reads 7, and a list of seven names counted from zero has no 7.

Note there is **no Half-Orc problem and no missing race** — all seven of the
usual ones are here.

## Spells

Spell levels **1 to 3 only**, which is what the party reader already assumes and
what the game's own spell table confirms. A character may hold **21** memorised
spells at once, the length of the array at `+0x17`.

## Not established here

**The item catalogue.** Item names come from the game's `ITEM%d.DAX` data files
rather than from any table in the application, and enumerating them is a
separate piece of work from a different structure. The running game's item names
are already read correctly by the party probe, by a different route. Tracked as
F85 rather than guessed at, because the converter will want it: an item from
another version that does not exist here is exactly the case F81 has to refuse.
