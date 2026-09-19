# What is inside a Macintosh Pool of Radiance saved game

Work for F78, done 2026-09-18, against the three saved games on the owner's own
game disk. Two of them are the same party in two states, which made the
important parts fall out of a plain diff.

The verified framing is now published in [SAVE_FORMAT.md](SAVE_FORMAT.md).
That specification supersedes the approximate region boundaries below and
documents the full item/effect counts. This page retains the earlier discovery
history.

## The shape of it

A saved game is an ordinary Macintosh file with two forks, about 17 KB:

| | |
| --- | --- |
| Data fork | **12,906 bytes** in all three. Where the party is, what the world looks like, and the party's names. |
| Resource fork | 4.4–4.6 KB. **Who the party are** — one resource per character. |

The division is the Macintosh way round, and it is the single most useful fact
here: the characters are not in the data fork at all.

## The resource fork: one character per resource

A standard resource fork holding resources of type **`PoRc`**, one per party
member, in party order, each **named with the character's own name**.

**Each `PoRc` resource begins with the same 302-byte character record the running game
holds in memory.** Every offset this project established by reading the game's
own 68k code applies unchanged:

| Offset | Field | How it was confirmed here |
| --- | --- | --- |
| `+0x00` | name, 16 bytes NUL-padded | matches the resource's own name |
| `+0x10`–`+0x15` | the six ability scores | read as 18/15/14/17/16/16 for a fighter |
| `+0x16` | exceptional strength | 47 on that same fighter, an 18/47 |
| `+0x17`, 21 slots | memorised spells | zero for the fighters, set for the casters |
| `+0x2f` | class | distinct per character, stable across saves |
| `+0x32` | maximum hit points | never differs between two saves of one party |
| `+0x120` | attacks remaining | differs between the two states |
| `+0x12b` | **current hit points** | differs between the two states, and only this |
| `+0x12c` | movement | 9 for every member, as in memory |

The healed and injured saves of the same party differ inside a character
resource at exactly the bytes the in-memory layout predicts. That is the
confirmation that matters: the file record and the memory record are one
layout, and [RECORD_ALIGNMENT.md](RECORD_ALIGNMENT.md) therefore applies to
saved games too.

**After the record come that character's items.** F79 resolved the framing:
a big-endian 16-bit count at `+0x12e`, then exactly 66 bytes per item, then
another big-endian 16-bit count and exactly 10 bytes per effect. The extra
bytes previously attributed to variable item sizes belong to effects.
See [SAVE_WRITER.md](SAVE_WRITER.md) for disassembly and live-load evidence.

`PoRCharacters`, beside the saves, is a different thing: one `ChrL` resource
called "CharacterList", empty on this disk.

## The data fork

Read as a whole it is mostly zero for a party that has not adventured. The
`SampleParty` save has almost nothing outside two small regions; a played save
fills 7.7 KB that the sample leaves empty.

| Region | What is there |
| --- | --- |
| `0x0000`–`0x04ff` | sparse party-level state; a few bytes differ between the two states |
| `0x120c` | **where the party is standing.** Two values, low byte first, reading 6,6 in one save and 9,9 in the other |
| `0x125a` | **the last message shown**, as alternating character and zero bytes — "YOU SPY A GROUP OF SEEDY-LOOKING KOBOLDS." in one, orcs in the other |
| `0x1400`–`0x31ff` | **the world state**, ~7.7 KB, dense in a played save and entirely absent from an unplayed one |
| `0x3200`–`0x3269` | a short trailer, then **six 16-byte NUL-padded names in party order**, ending exactly at the end of the file |

**The world-state block does not need decoding: it is a copy of memory.**
Answered 2026-09-18 by loading `F7Injured`, walking the party out into the
Slums of Phlan, and searching a full RAM capture for the block. It is there,
contiguous and verbatim — **7,679 of its 7,680 bytes match**, the one exception
being the very first, and the party had walked a dozen squares since the save
was written.

So writing a save does not require understanding what is in this block. It
requires copying the right region out, which F79 now does with validated handles and a successful live-load round trip.

**One caveat that matters.** In the session measured, the block sat at
`A5 − 0x76a75`, about 475 KB below the application globals. That is heap
territory, not an A5-relative global, so the address will move between runs and
must not be hard-coded. F79 traced the stable handle to `A5−0x5ea6` and verified
its exact 7,680-byte logical allocation before copying it.

**One caution about the numbers.** The values at `0x120c` and the hit-point-like
bytes elsewhere in the data fork are stored low byte first, which is not what a
68k program usually does. Either they are byte values with padding, or the file
kept a little-endian layout from the DOS line. It matters for the converter, and
it is not settled — so nothing here is written as a 16-bit field.

## What can be done with this now

`SavedParty.parse` reads a party out of a save's resource fork, using the
offsets above. Run against all three of the owner's saves it reports every
member with their class, current and maximum hit points, movement, item count
and ability scores, and correctly refuses `PoRCharacters` as holding no party.

That is what F33, loading a save from the companion, needs in order to say what
a save contains before anybody loads it.
