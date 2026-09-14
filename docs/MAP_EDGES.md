# Walls and door outlines

The map uses three deliberately simple symbols:

| Original Mac tile edge | Companion artwork |
| --- | --- |
| No wall surface, regardless of unused door bits | Open edge |
| Wall surface, door bits zero | Solid wall line |
| Wall surface, door bits 1, 2 or 3 | Neutral doorway outline |

A doorway outline does **not** promise an unlocked, discovered, or passable
door. The original game owns searches, locks, opening actions and scripted
events. A player's Hidden wall flag is their annotation, not an automatic
secret-door classification. Surfaces have different appearances in different
areas; their numbers are not a universal material or lock-state vocabulary.

Version 0.13.1 fixes phantom wall/door outlines caused by reading door bits
without first checking that the edge has a surface. The shared renderer applies
this correction to the live map, visited-only view and each flag's note map.
It does not rewrite map bytes, notebook identities, history, ink or game state.

## Original Macintosh evidence

This reuses the supplied v1.1 executable and the verified geometry layout in
[MAP_MEMORY.md](MAP_MEMORY.md), not assumptions from the DOS port. Offsets
include the four-byte CODE-resource header. Private executable/map bytes are
not distributed. A bounded historical `deja` recall timed out; no unseen
session is used as evidence.

- CODE 6 `+0x5ee8..0x6030` reads the wall surface: north/east from the high/low
  nibbles of plane 0, south/west from plane 1. Raw Mac facings 0,2,4,6 mean
  north,east,south,west. Java retains its existing normalized direction order.
- The directional barrier accessor sets its default result to 1 at
  `+0x5dd2..0x5dd4`, calls the wall accessor at `+0x5df0`, and returns early
  at `+0x5dfc` when the surface is zero. It does not read door bits on that edge.
- With a nonzero surface, the plane-3 masks are N `03`, E `0c`, S `30`, W `c0`.
  Their case bodies begin at `+0x5eb6`, `+0x5e84`, `+0x5e52`, `+0x5e1e`.
  Inline switch-table targets establish their order; linear disassembly alone
  can misread those embedded tables as instructions.
- CODE 4's movement dispatch at `+0x62ba` sends result 1 to the normal success
  case at `+0x62ca`. Results 2 and 3 enter separate original action-menu paths
  at `+0x62d4` and `+0x6454`; result 0 does not advance. Actual movement at
  `+0x65ce` requires success. This proves why all nonzero bits must not be
  advertised as freely passable; it does not establish secret/locked labels.
- The existing CODE 4 door mutators change only the selected two-bit field.
  They are not called by this app. Opposite sides can have different surfaces
  or states in original data; the decoder does not invent a mirrored value.

## Focused checks

`GeoMapEdgeTest` covers all 256 raw door bytes, all 15 nonzero surfaces,
every direction/tile mask, asymmetric opposite sides, unchanged raw bytes and
invalid coordinates. The six new tests bring the Java suite to 346 tests.

`ExplorationRenderCheck` runs the production renderer on Android software
Canvas. Its ten checks include byte-identical open maps despite unused door
bits in full/fog/footprint and note-page views, and identical neutral symbols
for states 1, 2 and 3. These synthetic checks are not physical-display tests.

`InspectMaps` audits the user's eight private GEO archives: 29 unique maps,
29,696 directional tile edges. It finds 16,898 open edges, 9,780 walls and
3,018 neutral doorways. **88 surface-free directional edges contain nonzero
door bits**; the old renderer could falsely draw those edges. These are edge
counts, not 88 distinct rooms or a full-game playthrough.

Live original-game spot checks and their exact scope are recorded in
[LOCAL_TESTING.md](LOCAL_TESTING.md). No new locked/secret-door labels or
pathfinding are introduced by this correction.
