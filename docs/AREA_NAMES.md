# Macintosh area names

Research checked 2026-09-14 against the privately supplied Macintosh **Pool of
Radiance v1.1** files. These names describe the 29 original GEO records, not
their order in a DOS catalog. No game archive, decoded script, map layout, ROM,
or save data is included here.

## Naming contract

- Keep the durable key `por-mac-v11-geo-N`; changing a display label must never
  move, rename, merge, or orphan notebook pages.
- **Verified** means an original Mac script loads the identified GEO record and
  identifies the place directly, or an independently linked quest/entrance in
  the same Mac scripts identifies it. It does not mean every place was visited
  on a physical tablet.
- **Descriptive** means the place/record relationship is established, but the
  wording is an honest description rather than a recovered official title.
  Do not silently upgrade these to exact floor numbers or invented proper names.
- An unsupported record or unresolved runtime identity remains unavailable;
  names must not be used as a substitute for the identity checks in
  [AREA_IDENTITY.md](AREA_IDENTITY.md).

All hexadecimal offsets below include the unpacked ECL record's two-byte prefix.
`ECL6/1`, for example, means record 1 inside the supplied `ECL6.DAX`, **not**
record number 6. `LOAD` refers to the script's `LOAD FILES` instruction, whose
GEO-selection operand is corroborated by the existing game decoder and the
observed New Phlan/Slums transition.

## Complete catalog

| GEO ID | Display label | Confidence and original Mac evidence |
| --- | --- | --- |
| 0 | New Phlan | Verified live: opening tour and gate round trip. ECL3/0 loads GEO 0 at `0x20c`; council/tutorial scripts 8 and 11 also load 0. |
| 1 | Buccaneer Base | Verified: ECL6/1 names the base at `0x2d2`; loads GEO 1 at `0x1dd0`. |
| 2 | Cadorna Textile House | Verified: ECL4/2 loads 2 at `0x22`; identifies Cadorna's family seal at `0x1014` and the textile house at `0x164e`. |
| 3 | Valjevo Castle — Northwest | Verified complex and direction-table relationship; see castle proof below. |
| 4 | Valjevo Castle — Northeast | Verified complex and direction-table relationship; ECL5/4 directly loads 4 at `0x277`. |
| 5 | Valjevo Castle — Southeast | Verified complex and direction-table relationship; shared maze script selects geometry independently from its own ECL ID. |
| 6 | Valjevo Castle — Southwest | Verified complex and direction-table relationship; ECL5/6 directly loads 6 at `0x368`. |
| 7 | Valjevo Castle — Inner Tower | Verified: ECL5/7 loads 7 at `0x14c`, identifies the boss's audience tower at `0xb65`, and returns to castle GEO 3 at `0x1c4`. Supplied Journal Entry 48 independently places his audiences in Valjevo Castle. |
| 9 | Stojanow Gate | Verified: ECL2/9 names the gate at `0x10dc`; loads 9 at `0x7bb`. |
| 10 | Valhingen Graveyard | Verified: ECL4/10 loads 10 at `0x180`; completion writes quest flag `0x4ab1` at `0x1879`. The council's named graveyard quest in ECL3/8 tests that same flag at `0xc9f` and names Valhingen at `0xcc4`. |
| 13 | Kobold Caves | Verified: the wilderness entrance in ECL8/27 describes the kobolds' caves at `0x405`, then enters ECL 13 at `0x4dd`; ECL8/13 loads 13 at `0x1d`. |
| 14 | Kovel Mansion | Verified: ECL3/14 loads 14 at `0x1c9b`; completion sets quest flag `0x4ab2` at `0x1a63`. The council's Kovel Mansion request in ECL3/8 tests that same flag at `0x121d` before its named request at `0x1228`. |
| 15 | Mendor's Library | Verified by cross-reference: ECL2/15 loads 15 at `0x2a2` and identifies its library stacks at `0x479`. ECL4/21's account of the sage Mendor and his overrun library at `0x134d` supplies the proper name; the actual library script contains the recoverable histories described in that account. This is not a literal title stored beside the GEO record. |
| 16 | Lizardmen Keep | Verified descriptive site name: ECL8/27's ruined-castle entrance at `0x273` enters ECL 16 at `0x30a`; ECL8/16 loads 16 at `0x316` and establishes the lizardmen inhabitants at `0x611` onward. |
| 17 | Nomad Camp | Verified: ECL7/17 loads 17 at `0x5eb`; the local approach describes nomads emerging from the camp's tents at `0x98c`. |
| 18 | Podal Plaza | Verified: ECL1/18 loads 18 at `0x73` and uses auction quest flag `0x4ab0` at `0x8f`, `0xf2d`, and `0xf6d`. ECL3/8 names the Podal Plaza auction at `0x10bd` and sets that same flag at `0x112c`. |
| 20 | Slums of Phlan | Verified live and scripted: ECL2/20 loads 20 at `0x18d`; its arrival names the slums at `0x1bb`. Existing gate round-trip evidence independently pairs the area with record 20. |
| 21 | Sokal Keep | Verified: ECL4/21 loads 21 at `0x17e`; boat arrival explicitly names Sokal Keep at `0x1b1`. |
| 22 | Sorcerer's Pyramid — Entrance | Verified site; descriptive section label. ECL7/26's pyramid entrance at `0x69b` enters ECL 22 at `0x71c`; ECL7/22 loads 22 at `0x31d`. See pyramid limits below. |
| 23 | Sorcerer's Pyramid — Inner Chambers | Verified site; descriptive section label. ECL7/22 enters ECL 23 at `0xd2f`/`0xd44`; ECL7/23 loads 23 at `0x305`, contains the experimental chambers, and returns to ECL 22 at `0x51c`. |
| 24 | Temple of Bane | Verified: ECL1/24 explicitly distinguishes current GEO 24 from 31, dispatching temple events for 24. Temple entrance and named welcome occur at `0xb27` and `0xdd7`; its startup loads 24 at `0x1dd7`. |
| 25 | Dark Cave (25) | Descriptive, verified location: ECL6/25's small-dark-cave prompt at `0xb27`, Enter branch, and chosen cave entrance converge on GEO 25 load at `0xbf6`. The ID suffix distinguishes it without inventing a proper name. |
| 26 | Grove and Ruined Huts | Descriptive, verified shared map: ECL7/26's wooded-grove prompt at `0xb5f` and ruined-huts prompt at `0xba8` converge on the selected entrance and GEO 26 load at `0xc71`. Calling the complete record only a grove would omit another supported entrance. |
| 27 | Dark Cave (27) | Descriptive, verified location: ECL8/27's small-dark-cave prompt at `0x90f`, Enter branch, and selected cave entrance converge on GEO 27 load at `0x9db`. This is not the separate kobold-cave destination GEO 13. |
| 28 | Zhentil Outpost | Verified: ECL6/28 explicitly names the outpost at `0x188`; loads 28 at `0x1da2`. |
| 29 | Kuto's Well | Verified: ECL8/29 names the well at `0x1cf`; selects surface GEO 29 at `0x35d`. |
| 30 | Lizardmen Catacombs | Verified descriptive lower-area name: ECL8/16 offers a hidden tunnel leading down at `0x416`, then loads 30 at `0x467`. Return to the upper keep loads 16 at `0x113c`. No independent ECL 30 is required. |
| 31 | Mansion District | Descriptive, verified separate district: ECL1/24 checks current GEO 31 at `0xc9` and dispatches its own events at `0xd4`; its large-mansion event is at `0x814`. Startup selects 31 at `0x1ddf`. The alternative temple branch belongs to 24. This does not claim a recovered official title such as “Wealthy Quarter.” |
| 32 | Kuto's Well Catacombs | Verified descriptive lower-area name: ECL8/29 offers descent into the well at `0x154c`, describes climbing down at `0x158f`, then loads GEO 32 at `0x15b1`; startup also distinguishes the lower map at `0x36c`. |

IDs **8, 11, 12, and 19 have no GEO record** in the supplied set. Some are real
script IDs; they must not become fabricated map identities. There are exactly
29 catalog rows above.

## Castle proof: direction, not DOS ordering

The original ECL5/5 script checks current GEO `0x49c5` against 3, 4, 5, and 6 at
`0x196`, `0x1a7`, `0x1b8`, and `0x1c9`. It indexes the corresponding transition
table by actual facing, then loads that GEO at `0x1da`. The four original tables
are at virtual ECL addresses `0x9aeb`, `0x9aef`, `0x9af3`, and `0x9af7`:

| Current GEO | North | East | South | West |
| --- | --- | --- | --- | --- |
| 3 | 3 | 4 | 6 | 3 |
| 4 | 4 | 4 | 5 | 3 |
| 5 | 4 | 5 | 5 | 6 |
| 6 | 3 | 5 | 6 | 6 |

The existing live-facing proof establishes north/east/south/west = 0/1/2/3.
Thus 3 is northwest, 4 northeast, 5 southeast, and 6 southwest. This is a
derived relationship from the original Mac scripts, not an inference from
ascending record IDs. ECL5/7's tower passage links this complex to the audience
hall that the supplied Journal Entry 48 places in Valjevo Castle.

`0x49c5` and the addresses above are **ECL virtual addresses**, not host pointers
or newly proposed Android RAM offsets. Runtime extraction still requires the
separate native profile proof.

## Shared scripts and deliberate naming limits

- ECL 24 handles both the temple (GEO 24) and neighboring mansion district
  (GEO 31). ECL 16 handles the keep and its lower map (16/30). ECL 29 handles the
  well and its lower map (29/32). ECL 5 can run with several castle GEO records.
  Therefore **current ECL ID is not a reliable area ID**.
- ECL 25, 26, and 27 also drive wilderness scenes. Their normal wilderness
  startup uses different `LOAD FILES` modes; only the explicit cave/grove
  branches above establish the corresponding 16×16 GEO records. A wilderness
  script number alone must not display one of these dungeon labels.
- The pyramid's entry and interconnected inner chambers are proved. Exact
  “level 1” versus “levels 2 and 3” wording from a PC tool's catalog was not
  independently established in this pass, so the labels do not assert it.
- Caves 25 and 27 are distinct records with multiple selectable entry positions.
  No specific creature's name, compass region, or fabricated lair owner is used
  as the map title. Their descriptive ID suffixes are intentional.
- These names do not establish whether mutated geometry, combat, loading, or
  wilderness state can safely resolve a notebook identity. Those are separate
  acceptance checks; a pleasant name must never hide an unavailable sample.

## Reproducibility and source reuse

Private source root: `scratch/extracted/Pool Of Radiance/`. The eight ECL
archives are in its `PoolRad2`, `PoolRad3`, and `PoolRad4` directories; the
supplied journal text is alongside them. Paths are evidence locations, not
assets distributed with this project.

The read-only analysis reused the already-attributed DAX/RLE definition from
[`DaxReader.java`](../android/minivmac/src/main/java/name/osher/gil/minivmac/mapper/DaxReader.java)
and the existing local Gold Box Explorer checkout's `DaxEcl/Memory.cs`,
`Program.cs`, `Commands.cs`, and `DaxEclFile.cs` for operand lengths, control-flow
opcodes, and six-bit dialogue decoding. See the existing
[Gold Box Explorer MIT attribution](../licenses/GoldBoxExplorer-MIT.txt).
The PC tool's name table supplied candidate search terms only; it was **not**
accepted as the Macintosh record-to-name proof.

Unpack each selected record, exclude its first two bytes for the virtual script
address space, and start from the five entry jumps at unpacked offsets
`2, 6, 10, 14, 18`. A virtual target `a` maps to unpacked offset
`a - 0x9900 + 2`. Follow conditional skip paths, jumps, subroutine calls, and
indexed branch destinations; skip string operands by their declared lengths.
Check real instruction boundaries before interpreting an apparent `0x21`
inside dialogue as a map load. This bounded analysis reached the cited loaders
and branches; it is not a replacement game interpreter or an assertion that
every original script path was exercised. The nomad script additionally contains
one unrelated instruction ambiguity at `0x43f`; its cited startup map load and
camp description do not depend on that ambiguous path.

The required `deja` recall was attempted before this work; no usable recalled
session material was reused. Existing local identity/live-tour evidence is
credited above. No emulator input, RAM mutation, save modification, or physical
e-ink/stylus acceptance was performed during this naming research.
