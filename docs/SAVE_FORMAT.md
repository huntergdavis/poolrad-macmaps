# Macintosh Pool of Radiance v1.1 save format

Specification revision 1, 2026-09-19 (F80). This describes original-game saves,
not emulator snapshots. It is the project's own reverse engineering of the
owner-supplied Macintosh v1.1 executable, checked against existing saves and a
generated save loaded by the original game. No game assets or example game
payloads accompany this document.

The earlier research survey is preserved in
[SAVE_FORMAT_RESEARCH.md](SAVE_FORMAT_RESEARCH.md).

Offsets are hexadecimal, zero-based, and relative to the stated structure.
Lengths are decimal unless prefixed with `0x`. `BE16` and `BE32` mean unsigned
big-endian integers. Byte arrays remain byte arrays: do not assume an unknown
world field is a 68k integer. Unknown bytes must be preserved when round-tripping.

## File identity and forks

The HFS file has Finder type `prdt`, creator `prad`, and two forks:

- The data fork stores four state blocks and the ordered party names.
- The standard Macintosh resource fork stores one named `PoRc` resource per
  character, containing the character, inventory, and effects.

Both forks are required. A data-fork-only copy is not a complete game save.
The export tool accepts a new 1–31 character ASCII filename without colon or
slash. This is a conservative tool restriction, not the complete HFS character
set. Character/resource names use Macintosh Roman bytes.

## Data fork: exact framing

Let `N` be the party count. The expected length is **12810 + 16 × N**.
The verified writer accepts 1–8 party members; six members produce 12,906 bytes.

| Offset | Length | Contents | Source at save time |
| --- | ---: | --- | --- |
| `0x0000` | 1 | State byte | `A5−0x513c` |
| `0x0001` | 2048 | State block A | handle at `A5−0x5eb2` |
| `0x0801` | 2048 | State block B | handle at `A5−0x5eae` |
| `0x1001` | 1024 | State block C | handle at `A5−0x5eaa` |
| `0x1401` | 7680 | World-state block D | handle at `A5−0x5ea6` |
| `0x3201` | 6 | Trailer bytes | `A5−0x3aee` |
| `0x3207` | 1 | State byte | `A5−0x5e89` |
| `0x3208` | 1 | State byte | `A5−0x5e90` |
| `0x3209` | 1 | Party count `N` | roster length |
| `0x320a` | `16 × N` | Character names, in party order | each record `+0x00` |

A handle is read through its master pointer; its allocation address is not
stable between runs. These globals locate RAM for capture-based writing. They
are not offsets into the saved file.

The initial byte is easy to miss: block D begins at **0x1401**, not 0x1400.
The older exploratory region table in SAVE_FILE.md used approximate boundaries;
the table above and the writer supersede them.

### Save-time packing

Before copying the blocks, the game places these values into them. Apply this
packing to private copies, not to the running game's memory. Offsets below are
relative to block A or B, not to the file:

| Destination | Value |
| --- | --- |
| A `+0x1f8`, BE16 | zero-extended byte at `A5−0x5ea2` |
| A `+0x1fe`, BE16 | `((byte[A5−0x5e8d] × 2) & 255) + byte[A5−0x5e8e]` |
| A `+0x3f4,+0x3f6,+0x3f8`, BE16 each | first word of each four-byte entry at `A5−0x3ae8 + 4×i`, `i=1,2,3` |
| A `+0x3fa,+0x3fc,+0x3fe`, BE16 each | second word of those same entries |
| B `+0x624`, BE16 | zero-extended byte at `A5−0x513c` |

CODE 2 `+0x37b6..0x386a` performs this packing; `+0x386e..0x39ac`
writes the blocks and trailer; `+0x39ae..0x3a68` writes names and characters.
These are offsets within the private executable's CODE resource, not file data.

## Resource fork

Use ordinary Macintosh resource-fork framing: a 16-byte header of four BE32
values (data offset, map offset, data length, map length), a resource map, and
length-prefixed resource payloads. This project emits the data area at 256;
readers must follow the header rather than require that placement.

The type list contains `PoRc`. Each reference has a signed BE16 resource ID,
a BE16 name-list offset, an attribute byte, a 24-bit data-area offset, and four
reserved handle bytes. The resource's data begins with a BE32 payload length.
A name-list entry is a one-byte length followed by Macintosh Roman bytes.
Type/resource counts in the standard map are stored minus one.

The writer emits IDs 128 onward in roster order, zero attributes and reserved
handles, and names matching the character records. Those IDs are the writer's
choice; a reader should identify characters by the ordered names in the data
fork and resource names rather than assuming a particular numeric ID.
The separate `PoRCharacters` library containing `ChrL` is not a party save.

## PoRc payload

Let `I` be inventory count and `E` effect count:

| Payload offset | Length | Contents |
| --- | ---: | --- |
| `0x000` | 302 | Character record |
| `0x12e` | 2 | `I`, BE16 |
| `0x130` | `66 × I` | Inventory records in list order |
| `0x130 + 66×I` | 2 | `E`, BE16 |
| `0x132 + 66×I` | `10 × E` | Effect records in list order |

The exact payload length is **306 + 66 × I + 10 × E**. Inventory records do
not have variable sizes. The two-byte effect count is present even if zero.
Earlier low-byte item-count reads at `+0x12f` are sufficient only below 256;
the complete count begins at `+0x12e`.

CODE 2 `+0x245c..0x2698` serializes these structures.
`+0x269a..0x28f0` allocates and relinks them on load. Serialized memory handles
are not file offsets and must never be dereferenced by a file reader.

### Established character fields

These are a verified subset, not a complete semantic map of all 302 bytes.

| Record offset | Width | Meaning |
| --- | ---: | --- |
| `+0x00` | 16 | NUL-padded character name |
| `+0x10..0x15` | 6 | Ability-score bytes |
| `+0x16` | 1 | Exceptional strength |
| `+0x17..0x2b` | 21 | Memorized-spell slots |
| `+0x2f` | 1 | Zero-based class ID |
| `+0x32` | 1 | Maximum HP byte |
| `+0x82` | 4 | In-memory effect-list handle |
| `+0xc9` | 1 | Party slot; writer requires distinct values below 8 |
| `+0xd4` | 4 | In-memory inventory-list handle |
| `+0xd8` | 4 per slot | Readied-item handles; slot 0 weapon, slot 2 armor |
| `+0x10e` | 2 | BE16 encumbrance in gold-piece weight |
| `+0x110` | 4 | In-memory next-character handle |
| `+0x114` | 4 | In-memory own-character handle |
| `+0x118` | 1 | Condition |
| `+0x11b` | 1 | Quick flag, recognized values 0 and 1 |
| `+0x11d` | 1 | Armor-class byte |
| `+0x120` | 1 | Attacks remaining |
| `+0x12b` | 1 | Current HP byte |
| `+0x12c` | 1 | Movement |

Spell slot 0 is empty. Values 1–127 identify ready spells; bit 7 marks a spell
chosen but awaiting rest. This encoding does not imply all 127 IDs exist.
See [SPELL_READINESS.md](SPELL_READINESS.md) for code traces and restrictions.
Class IDs and version limits are in [VERSION_LIMITS.md](VERSION_LIMITS.md);
condition values are in [PARTY_CONDITIONS.md](PARTY_CONDITIONS.md).

The loader rebuilds next/own/inventory/effect handles from the serialized lists.
Capture-based exports preserve the full record exactly; a converter cannot
replace unknown fields with zero merely because these handles are rebuilt.

### Inventory and effects

Inventory nodes are **66 serialized bytes**. Their in-memory next handle is
at `+0x2a`. Name-part indexes occupy `+0x30..0x32`; the equipment probe
composes them in descending order through the game's vocabulary, applying the
suppression bits at `+0x36`. The leading Pascal-string buffer is display
scratch: it may contain quantity or magic columns and stale suffixes. It is
not a portable item identifier. See [EQUIPMENT_MEMORY.md](EQUIPMENT_MEMORY.md).

Effect nodes are **10 serialized bytes**, with the in-memory next handle at
`+0x06`. Their remaining bytes must be preserved; this specification does not
assign unverified effect semantics.

The host writer bounds lists to 256 items and 64 effects per member to reject
runaway/cyclic captures. These are defensive tool limits, not established
gameplay capacity limits.

## Portable wrappers

The game consumes the two HFS forks, not either wrapper below.

**MacBinary II:** the writer uses a 128-byte header, filename length at byte 1
and bytes at 2, type/creator at 65/69, BE32 fork lengths at 83/87, version bytes
129 at 122/123, and BE16 CRC-16/CCITT over header bytes 0–123 at 124 (initial
value zero). Header, data fork and resource fork are each padded to 128-byte
boundaries. The wrapper preserves both forks for import with `hcopy -m`.

**PRSV v1:** the companion's game-file backup wrapper, all integer fields BE:

```text
4 bytes   "PRSV"
BE16      version = 1
BE16      filename byte length
bytes     filename (current Java codec: ISO-8859-1; host writer: ASCII subset)
4 bytes   Finder type
4 bytes   Finder creator
BE32      data-fork length
bytes     data fork
BE32      resource-fork length
bytes     resource fork
BE32      CRC-32 over every preceding byte (standard java.util.zip/zlib CRC32)
```

The Java reader accepts name lengths 1–31, caps each fork at 4 MiB, verifies
the CRC, and refuses trailing bytes. The wrapper's filename codec is distinct
from Macintosh Roman resource names; do not reinterpret one as the other.

## Validation and evidence

A strict consumer should verify both fork bounds, resource-map bounds, count
arithmetic without overflow, exact PoRc lengths, party count/name consistency,
and uniqueness before making changes. Do not follow saved handles or assume
zero-filled unknown fields are safe. The existing `SavedParty` preview parser
reads a subset; it is not a complete conformance validator.

Reference implementation: [write-game-save.py](../tools/write-game-save.py).
It validates application identity, logical heap allocations, nonoverlapping
blocks, complete acyclic lists, names, slots, and the supported idle local
exploration state. It refuses camp, combat, wilderness, loading, relocation
and movement. These state restrictions are the verified writer's scope.

The six synthetic tests in [test-save-writer.py](../tools/test-save-writer.py)
exercise exact framing, source preservation, save-time packing, bad pointers,
cycles, state guards, and container checksums without distributing game data.

The original game loaded the generated ExportProof save at New Phlan 15,1 W.
After loading, all six complete character payloads matched byte for byte,
including 34 inventory records and seven effects. The complete world block
also matched. One data-fork byte, `0x0dc4`, changed from 8 to 255; its meaning
is unresolved. See [SAVE_WRITER.md](SAVE_WRITER.md) for the full live procedure.

## Known limits and conversion boundary

The middle of the character record is not fully aligned with DOS/Amiga.
World-state semantics, port-specific item identities, and some counters remain
unresolved. Similar record shapes do not establish cross-platform equivalence.
This specification enables exact capture-based Macintosh writing; it does not
claim a working cross-platform converter.

Conversions must never raise a stat, must report every downward adjustment
before writing, and must refuse fields without a defensible Macintosh mapping.
Always write a new save, retain an independent disk backup, and verify the
result by loading it in the original game. See the writer guide for safe
offline import. No game payload is distributed with the specification.

Prior research recalled through Deja: session
`1d01c279-196b-4165-83cb-2031016bb071` established F78–F82's scope and ordering.
The F79 writer and live-load evidence in this repository verify the framing
published here; [RECORD_ALIGNMENT.md](RECORD_ALIGNMENT.md) preserves the earlier
cross-platform comparison and its unresolved middle.
