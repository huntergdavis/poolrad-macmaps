# The tactical combat state in memory — research notes

**Status: research only. L2 is not implemented.** This records what has been
established about the original game's tactical combat, what has been ruled out,
and the one question that must be answered before the work can continue, so the
next attempt does not repeat this ground.

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

## The open question, and why no offsets are claimed yet

A before/after diff across a single square of movement is the obvious way to
find the position fields. That experiment has **not** been performed
successfully, because the Mac version does not appear to move a combatant with
the number keys that move the party in exploration: with the prompt reading
`Move/Attack, Move Left = 9`, pressing `6` left the selection where it was, no
byte anywhere in RAM went from 9 to 8, and the combat view was unchanged apart
from the flashing selection box.

A pair of captures was taken around that keypress and shows a compact region
changing near `0x72a9b3` and across `0x72b5d6..0x72b6fe`. **No offsets are
claimed from it**, because without a confirmed move the diff cannot be
distinguished from the enemy's own turn and the game's redraw.

**What has to happen first:** establish how the Mac build takes combat movement
input — most likely a mouse click on the destination square rather than a key —
then repeat the capture pair with a move that is visibly confirmed on screen and
in the `Move Left` counter. Only then are position offsets worth naming.

## Scope reminder

L2 is a **distinct read-only view**. It must not automate combat, move anyone,
or reveal anything the player cannot already see on the game's own Combat View.
The companion already names Combat mode correctly and refuses to present the
retained exploration map as a tactical one; that behaviour stays.
