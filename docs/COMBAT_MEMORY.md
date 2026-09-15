# The tactical combat state in memory — research notes

**Status: L2 shipped in 0.25.0 from these findings.** This records how the
original game's tactical combat is read, what was ruled out on the way, and
what is still undecoded.

## The table, verified

    A5-0x46e8            count, zero whenever no battle is running
    A5-0x46e4 + i*4      { i, flag, x, y } for i in 0..count-1
    A5-0x46e4 + count*4  a sentinel entry whose x and y are both zero

Entry `i` is the `i`-th combatant of the roster chain, so the party members
come first and the party/other split needs no new field. Each entry states its
own index, which makes the table self-checking: the reader refuses it if any
entry disagrees, if the sentinel is missing, or if the count disagrees with the
chain length. Confirmed on two live battles (sixteen combatants and
thirty-five) and on camp, walking and an area arrival, where the count is zero.

**The input question is answered: in combat the party moves with the arrow
keys**, the mirror of exploration, where it moves with the number keys. The
`Move Left` counter confirms each step, which is what finally made a clean
one-square before/after diff possible.

## Verified: the combatant list is already reachable

The party reader already walks the combat roster, because in this game it is
the same linked list as the party with the monsters appended. From
`A5 - 0x519e` (`POOLRAD_PARTY_HEAD_BACK`) through each record's next handle at
`+0x110`, with slot `0..7` for party members and `8` for monster groups. See
[PARTY.md](PARTY.md) and `POOLRAD_PARTY.h`; nothing new is needed for the
roster itself.

A live capture of one battle walked cleanly: six party members followed by ten
`ORC` records, all validated by the existing heap-block and slot guards.

## Ruled out: the records do not carry tactical positions

The ten orc records in that capture are **byte-for-byte identical except at
four offsets**:

| Offset | What it is |
| --- | --- |
| `+0x110..+0x113` | the next handle in the chain |
| `+0x114..+0x117` | a distinct handle per combatant, four bytes apart, all inside one small master-pointer block — the combatant's own handle |

There is no per-orc coordinate anywhere in the 302-byte record: ten monsters
standing on ten different squares share every other byte. So the tactical grid
and the combatants' places on it live in a **separate structure**, and L2 needs
that structure, not another record field.

## The method for controlled captures

The cheap, repeatable battle is the **council guard**, not a tavern brawl:

- Walk into the City Hall (door at 3,4 facing east), south to 4,5, east to 5,5,
  then one more step east. The guard asks `DO YOU LEAVE?`; answering **No**
  starts a small fight immediately.
- A tavern tale is followed by `A DRUNKEN BRAWL BREAKS OUT`, which spawns waves
  faster than quick combat clears them and is a poor research subject. Two
  sessions were lost to it.

Route planning and the keyboard recipe are in [MESSAGE_MEMORY.md](MESSAGE_MEMORY.md).

## How the offsets were found

Two capture pairs, each around a single square of movement confirmed both on
screen and by the `Move Left` counter:

- one step **north** left a handful of bytes changed by exactly minus one;
- one step **east** left a different handful changed by exactly plus one.

Intersecting them — changed by a step on one axis, untouched by the step on the
other — left exactly one adjacent pair, `x` then `y`, inside the table above.
An earlier pair taken around a keypress that did **not** move anything was
discarded rather than interpreted; without a confirmed move a diff cannot be
told apart from the enemy's turn and the redraw.

## Still undecoded

- **Terrain.** Nothing in the A5 globals looks like an arena grid: the largest
  block that appears only during a battle is the position table itself. The
  terrain is presumably in a heap allocation that has not been located, so the
  shipped overview draws no walls and says so.
- **The arena's own bounds.** Coordinates from 18 to 36 and 9 to 19 have been
  seen, but no width or height field has been identified, so the overview
  frames the squares that are occupied instead of claiming an arena size.
- **Initiative, facing, and which combatant is acting.**

## Scope reminder

L2 is a **distinct read-only view**. It must not automate combat, move anyone,
or reveal anything the player cannot already see on the game's own Combat View.
The companion already names Combat mode correctly and refuses to present the
retained exploration map as a tactical one; that behaviour stays.
