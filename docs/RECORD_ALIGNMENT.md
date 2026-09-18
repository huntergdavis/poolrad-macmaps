# The Macintosh character record, against the documented DOS one

Work for F77, done 2026-09-18. The question was whether the Macintosh's
302-byte character record is the same record as the documented DOS 285-byte one
with different padding, or a different design. **It is the same record**, and
both ends of it now line up exactly.

## Where the DOS table comes from, and how far to trust it

The DOS field table used here was read from the public
[malcyon/wish](https://github.com/malcyon/wish) project, which documents Pool of
Radiance's 285-byte DOS record "measured against 24 real specimens", and
describes the Amiga record as the DOS one with pad bytes inserted ahead of
even-aligned fields, big-endian, 16-byte NUL-padded name. Amiga is also
big-endian 68k, which is why it is the better comparison for a Macintosh port.

**It is second-hand and it is treated that way.** Nothing below is claimed
because that table says so. Every Macintosh offset in the "ours" column was
found independently by this project, by reading the game's own 68k code and
verifying against captures, months before that table was consulted. What the
table does is *explain* them — and the explanation is worth having, because it
predicts where the fields nobody has looked for yet will be.

No code, no data and no file from that project is used here.

## The head: identical, no shift at all

| Ours | DOS | Field |
| --- | --- | --- |
| `+0x00` (16 bytes) | `0x000` (1 count + 15) | name |
| `+0x17`, 21 slots | `0x017`, 21 bytes | memorised spells |
| `+0x2f` | `0x02F` | class |
| `+0x32` | `0x032` | maximum hit points |

Three offsets this project found on its own sit exactly where the DOS record
puts them, including the 21-slot memorised-spell array — found here by tracing
CODE4 `+0x0ac2`, and the same length in both. The name occupies the same
sixteen bytes in both, spent differently: DOS a count byte and fifteen, ours
NUL-padded, which is what the Amiga port does too.

## The tail: a constant twelve, then sixteen

| Ours | DOS | Shift | Field |
| --- | --- | --- | --- |
| `+0x10e` (2) | `0x102` (2) | +12 | encumbrance |
| `+0x110`, `+0x114` | `0x104` (8 bytes, "heap") | +12 | **two Mac handles** — next member, own |
| `+0x118` | `0x10C` (4 bytes, combat status) | +12 | condition |
| `+0x11b` | `0x10C` + 3 | +12 | **quick flag** |
| `+0x11c` | `0x110` | +12 | to-hit, not read here |
| `+0x11d` | `0x111` | +12 | armour class |
| `+0x120` | `0x114`, inside a nine-byte tail | +12 | attacks remaining |
| `+0x12b` | `0x11B` | **+16** | current hit points |
| `+0x12c` | `0x11C` | **+16** | movement |

Two things fall out of this that are worth more than the alignment itself.

**The eight bytes DOS calls "heap" are where the Macintosh put its two
handles.** This project found a next-member handle at `+0x110` and the
character's own handle at `+0x114` by following the game's roster walk. They
land exactly on an eight-byte field that the DOS record has no real use for.
That is what a port does: it spends the dead space on the memory model it
actually has.

**The quick flag is inside DOS's four-byte combat-status field.** Condition at
`+0x118` is byte 0 of it and quick at `+0x11b` is byte 3. Both were found here
by capture and diff, with no idea they were neighbours. It also means the two
remaining bytes, `+0x119` and `+0x11a`, are combat status of some kind and are
the first place to look for anything else that field carries.

**The arithmetic closes.** DOS ends at `0x11C`, one byte for movement, 285. Ours
ends at `+0x12c`, one byte for movement, 301 — padded to an even 302, exactly as
the Amiga port pads Pool of Radiance's 287 to 288. `285 + 16 + 1 = 302`.

## The middle: not aligned, and not guessed at

Between `+0x32` and `+0x10e` the shift grows from 0 to 12, and the offsets this
project found there do not yet form a consistent picture against the DOS table:
the level at `+0x9a`, experience at `+0xb4` and the slot at `+0xc9` imply shifts
that do not increase monotonically, which cannot be right — insertions only ever
push fields later. Either one of those offsets means something other than its
name suggests, or the second-hand DOS table is imprecise in that region.

**So it is left open rather than written down as fact.** The readied-equipment
array at `+0xd8` and the item records it points at are separately established
from the game's own code ([EQUIPMENT_MEMORY.md](EQUIPMENT_MEMORY.md)) and do not
depend on any of this.

## What this changes

- **F39, auto-memorise, is not blocked.** The memorised-spell array is the
  21 bytes at `+0x17`, already read by the probe, and the DOS record agrees on
  both its position and its length. The write target is known.
- **F41, auto-equip, was never blocked on this.** Readied items are handles at
  `+0xd8`, slot 0 the weapon, established from CODE3. What it still needs is a
  way to pick an item out of the character's inventory, which is a different
  structure.
- **F78, decoding the saved game, gains a strong prior.** If the record in
  memory is the DOS record re-padded, the record in a save file very likely is
  too, and the same alignment should carry over.

`tools/test-record-alignment.c` pins the probe's own constants to the
relationships above, so an offset cannot be changed without the alignment being
reconsidered.
