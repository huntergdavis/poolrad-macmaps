# Stable notebook area identities

`AreaIdentity.resolve(GeoMap)` returns an immutable identity only when all
1,024 geometry bytes exactly match one of 29 known Macintosh v1.1 GEO records.
The durable key is `por-mac-v11-geo-N`, using the original record ID rather than
an index in the catalog. IDs are not continuous; the final record is 32.
Heap addresses, A5, coordinates, facing, filenames, and the reader's temporary
`GeoMap.id` are **not** part of the identity. A notebook/run identifier and tile
coordinates must be stored alongside this key, never inferred from it.

Only record 0 is presently named **New Phlan**, independently matched to the
running game's opening tour. Other recognized records retain **Area N** labels
pending the broader naming pass; record 20's Slums entry was observed on the
bounded acceptance route below. The catalog contains only SHA-256
digests and record numbers: no map layout, ROM, disk, or game archive is included.

## Exact match or unavailable

No fuzzy matching, nearest-map fallback, shared `unknown` notebook, heap-pointer
identity, or remembered previous identity is allowed. A null map, unknown hash,
empty startup geometry, or unavailable SHA-256 provider returns null. Any
malformed catalog row, duplicate record ID, or duplicate fingerprint disables
the complete catalog, not just the last row.

The two-byte DAX record prefix is excluded because the live heap payload does
not contain it. Every byte of the four remaining planes is included. The
existing decoder and its upstream source identify two wall planes, an event
plane, and a door plane. Those descriptions **do not establish immutability**.
We have not established which event/door bits the Macintosh game changes after
interactions. Masking a bit simply because it looks like an event flag could
silently merge different areas, so this implementation masks nothing.

If a game action mutates geometry away from the known source record, note
placement and map-linked note access must become unavailable with an explanation
such as “Area not recognized; notes are unavailable here.” Existing saved notes
remain intact and can reappear if the exact known area returns. Recognition does
not establish that the guest is in exploration rather than loading/combat: the
separate mode-detection backlog is still needed, and a retained last map must not
be presented as a newly recognized live area.

## Reuse and validation

This extends the existing [`DaxReader` and `GeoMap`](../android/minivmac/src/main/java/name/osher/gil/minivmac/mapper/)
and [`AreaFingerprints`](../tools/AreaFingerprints.java) research, rather than
introducing a second map decoder. The DAX/RLE and plane definitions were adapted
from Gold Box Explorer; the existing [MIT attribution](../licenses/GoldBoxExplorer-MIT.txt)
still applies. The native reader's A5-relative snapshot and relocation checks
remain in [`POOLRAD.h`](../android/minivmac/src/main/jni/src/POOLRAD.h).
The required local session recall was attempted before this work and timed out;
no recalled session material was reused.

Reproduce the local check with privately supplied extracted maps and PRM1 probes:

```sh
javac -d scratch/area-classes \
  android/minivmac/src/main/java/name/osher/gil/minivmac/mapper/GeoMap.java \
  android/minivmac/src/main/java/name/osher/gil/minivmac/mapper/DaxReader.java \
  android/minivmac/src/main/java/name/osher/gil/minivmac/mapper/AreaIdentity.java \
  android/minivmac/src/main/java/name/osher/gil/minivmac/mapper/PoolRadState.java \
  tools/AreaFingerprints.java
java -cp scratch/area-classes AreaFingerprints scratch/extracted \
  scratch/phlan-intro.probe scratch/phlan-tour.probe
```

The tool validates every supplied record against the production catalog and
rejects duplicate identities. It prints hashes/IDs only, never geometry. The
29 private source records have unique hashes and match the catalog; both
previously labeled New Phlan probes resolve to record 0. This confirms those
inputs, not full-game mutable-map coverage.

Unit tests use invented geometry and a package-private injected catalog. They
cover relocated snapshot metadata, movement/facing, stable IDs after revisit,
all 1,024 single-byte geometry mutations, unknown/empty states, malformed or
ambiguous catalogs, input ownership, and selected real checksum-to-ID mappings
without storing original game geometry. Physical e-ink/stylus acceptance is not
inferred from host tests.

## Actual gate round trip — 0.4.0

Using ordinary tutorial clicks and game movement keys in the Android emulator:

- New Phlan at 0,4 W had Notebook 1's flag at tile 11,2.
- Forward entered the game-described **Slums of Phlan**: the resolver selected
  record 20, position 15,4 W, with zero inherited flags. Two ordinary turns gave
  15,4 E in both the map pane and the guest's own position readout.
- Forward returned to New Phlan at 0,4 E. The flag returned at tile 11,2 and
  reopened the original TYR handwriting; its saved vector record was byte-identical.

Private evidence: `scratch/n1-slums.png`, `n1-slums-return-ready.png`,
`n1-returned.png`, and `n1-returned-note.png`. An arrival message can leave the
guest's printed position temporarily stale even while its actual state has
changed; the separate mode-detection work remains necessary.

This validates the N1 gate round trip, not all 29 areas, mutable geometry,
combat/wilderness modes, or the complete M1/M3 validation backlog.
