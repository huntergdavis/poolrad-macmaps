# Macintosh live-map identity proof

This profile is for the supplied **Pool of Radiance v1.1 Macintosh application**,
running in the supported 24-bit Mac II configuration. It reads guest memory;
it does not modify the game, disk, party, or notebook identity. Application
resource offsets below are offsets inside the original `CODE` resource,
including its four-byte segment header. Private executable, RAM, and map bytes
are not part of the repository.

## Stable record number, not an address

| Field | Verified Macintosh location | Evidence |
| --- | --- | --- |
| Current A5 | Low-memory long at `0x904` | Existing supported application/A5 profile |
| Geometry handle | A5 `−0x3b40` | CODE 12 `+0x4fa..0x504` requests 1,024 logical bytes and stores the handle |
| Game-state handle | A5 `−0x5eb2` | CODE 12 `+0x4a6..0x4b2` requests 2,048 logical bytes and stores this separate handle |
| Current GEO record | Dereferenced game state, big-endian word `+0x18a` | CODE 6 `+0x6f4a..0x7062` loads the requested GEO record and stores its number at `+0x705c` |
| Local-map presentation | Byte at A5 `−0x5e89`, value `1` | CODE 9 `+0x2904..0x291a` selects the local view rather than the outdoor renderer; CODE 2 `+0x3444..0x346a` uses the same distinction during load |

The loader formats the GEO archive name, passes its requested record number to
the DAX reader, requires a decompressed length of `0x402`, skips the two-byte
record prefix, and copies four 256-byte planes into the existing geometry
handle. It then stores the requested record number in the state word. The
record number is independent of the movable map or state address.

Observed read-only captures independently agree: New Phlan uses record `0`,
while the Slums capture uses `20`. Fresh-start and previous boot images relocate
the state/geometry blocks without changing these identities. The Java catalog
uses the original sparse record numbers, not their order in the catalog.

## What may change without changing the area

An aligned raw-displacement inventory of all original `CODE` resources found
26 references to the geometry global. Inspecting each instruction and its
consumers avoids relying on a linear disassembler's interpretation of embedded
68k switch tables:

- CODE 12: one allocation/global assignment.
- CODE 6: nine reads in the three tile-accessor functions. These return a wall,
  event, or door value, not an escaping geometry pointer. Four further
  references are the loader's four `BlockMove` destinations.
- CODE 4: twelve references in the two door mutation routines described below.

The supported executable's ordinary map mutations therefore do not write the
first three planes. Geometry bytes `0..511` are wall nibbles and `512..767` are
event indices. Bytes `768..1023` contain four two-bit door fields per tile.

CODE 4 `+0x523e..0x52f2` clears one directional pair with masks `0x3f`, `0xcf`,
`0xf3`, or `0xfc`. CODE 4 `+0x52f4..0x546c` bounds the coordinates to `0..15`,
preserves the other pairs, and sets the selected pair to `1`. Both address only
`geometry + 0x300 + y*16 + x`. This is executable evidence for excluding the
fourth plane from **identity**, not a claim that every changed door value is a
valid game action or that we have decoded secret/locked/passable door semantics.

PRM2 requires the reported GEO number **and an exact SHA-256 of all first 768
bytes** to match the same catalog record. Those prefixes are unique across all
29 privately supplied records. A changed wall/event byte, an unknown record,
or an ambiguous catalog is unavailable; no nearest-map or partial-bit matching
is used. A door change retains the same notebook key and remains visible in
the live geometry. The older PRM1 format keeps its full-1,024-byte exact-match
behavior because it has no independent record number.

## Transitions and the deliberate boundary

The outer script handler in CODE 5 `+0x2bca` can store a new GEO number **before**
calling the loader. Consequently the word alone must never authorize a notebook:

| Sample | Identity result |
| --- | --- |
| New number + previous area's wall/event prefix | Unavailable |
| Previous number + new area's prefix | Unavailable |
| Mixed prefix matching no complete catalog record | Unavailable |
| New number + complete new prefix, with a changed fourth plane | New area |
| Missing/bad state block or non-local presentation mode | Unavailable |

The last accepted case is safe for the area key: the whole identifying prefix
belongs to the new record. It does **not** establish that the four-plane copy
or the guest's redraw is atomic. During the copy's last stage, its door plane
may briefly still contain earlier bytes. M2 owns loading-state presentation,
combat/wilderness detection, and suppression of a stale exploration arrow;
this change does not claim a tactical combat position or complete mode decoder.
In particular the captured Slums combat still has presentation value `1`.

## Native PRM2 packet

The packet remains 1,200 bytes; coordinates, geometry, and diagnostic pointers
stay at their existing offsets. Only the unused end of the diagnostic
application-name field is repurposed:

| Bytes | Meaning |
| --- | --- |
| `0..3` | `PRM2` |
| `32` | Raw presentation byte; `255` if unavailable |
| `33` | Metadata valid: exactly `0` or `1` |
| `34..35` | Big-endian GEO record number; `0xffff` when invalid |
| `36..47` | Existing A5, geometry handle, and geometry pointer diagnostics |
| `48..175` | Existing 128-byte globals snapshot |
| `176..1199` | All 1,024 current geometry bytes, unchanged |

Native metadata validity requires the bounded 2,048-byte state, local mode `1`,
and record range `0..32`; Java additionally requires catalog membership and
the matching prefix. An explicit PRM2 refusal must not fall back to PRM1 hash
recognition. Native failure of identity metadata alone preserves the raw map
sample for independent consumers such as the party reader; the UI decoder
decides whether to expose an authenticated live map.

Both handles are resolved afresh. The original exact 1,032-byte physical map
allocation check was too restrictive: reuse the party reader's verified
logical-size rule. Require a 24-bit relocatable block, reserved tag bits zero,
four-byte-aligned physical size, and
`physical = logical + 8-byte header + tag's low-nibble correction`. Check the
entire physical allocation before dereferencing. The logical sizes come from
the allocation calls above, not just a conveniently sized capture. This follows
[Apple's Memory Manager, pages 2-22–2-23](https://dev.os9.ca/techpubs/mac/pdf/Memory/Memory_Manager.pdf).

## Reuse and checks

Reuse the existing `POOLRAD.h` profile, `probe-ram.c`, `GeoMap`, `DaxReader`,
`AreaFingerprints`, and the allocation proof in [PARTY.md](PARTY.md). The existing
[Gold Box Explorer MIT attribution](../licenses/GoldBoxExplorer-MIT.txt) covers
the reused DAX/map decoder; the new offsets and write-path audit come from the
original Macintosh executable, not DOS assumptions. Required local `deja`
recall was attempted before this work and timed out without reusable results.

`tools/test-map-probe.c` uses synthetic RAM. It checks all 256 mode bytes, every
heap tag, every correction nibble, sparse-ID range boundaries, state/map pointer
failures and relocation, truncated physical blocks, unavailable metadata, and
unchanged geometry output. Compile it with address/undefined-behavior sanitizers;
optional RAM-file arguments replay the exact production probe while printing
only identity/position metadata. The independent party probe remains passing.

The same production reader successfully replayed six private labeled captures:
the old New Phlan introduction and walking tour, Slums combat, both fresh-party
captures, and `m1-new-phlan-before.ram` from the new cold boot. Results were
GEO `0` for all New Phlan samples and `20` for Slums. These are actual input
checks, not claims of all-area playthrough, physical e-ink/stylus acceptance,
or M3's complete save/load route. Live transition and door-interaction evidence
is recorded separately in [AREA_IDENTITY.md](AREA_IDENTITY.md).
