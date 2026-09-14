# Stable notebook area identities

The durable key is `por-mac-v11-geo-N`, using the original record ID rather than
an index in the catalog. IDs are not continuous; the final record is 32.
Heap addresses, A5, coordinates, facing, filenames, and the reader's temporary
legacy `GeoMap.id` are **not** part of the identity. A notebook/run identifier
and tile coordinates must be stored alongside this key, never inferred from it.

- **PRM1 compatibility:** all 1,024 geometry bytes must exactly match the source
  catalog. The public `AreaIdentity.resolve(GeoMap)` retains this behavior.
- **PRM2:** validated native area-map metadata supplies the actual GEO record ID.
  That ID must also match the exact SHA-256 fingerprint of the first 768 loaded
  geometry bytes. Only the independently verified mutable door plane is excluded.
  Missing metadata or a mismatch rejects the snapshot; no PRM1 fallback is used.

Both paths produce identical durable keys. No notebook files are renamed or
migrated, and ordinary door-state changes in validated PRM2 maps retain notes.

Area names come from the supplied original Macintosh event scripts and their
explicit GEO-load paths, with live New Phlan/Slums evidence below. Unverified
names retain **Area N** rather than copying a similarly numbered DOS location.
The catalog contains SHA-256 digests, record numbers and display labels: no map
layout, ROM, disk, or game archive is included.

All 29 labels and their original Macintosh script/journal evidence are in
[AREA_NAMES.md](AREA_NAMES.md), including explicit confidence limits for the
descriptive cave, pyramid-section and mansion-district names. This is not a
claim that all 29 areas have been visited on the tablet.

## Exact identity proof or unavailable

No fuzzy matching, nearest-map fallback, shared `unknown` notebook, heap-pointer
identity, or remembered previous identity is allowed. A null map, unknown hash,
empty startup geometry, or unavailable SHA-256 provider returns null. Any
malformed catalog row, duplicate record ID, or duplicate fingerprint disables
the complete catalog, not just the last row.

The record-ID cross-check is equally strict: an ID for the destination cannot
authenticate the previous area's immutable prefix, nor can an old ID authenticate
a new area's prefix. An ID alone never authorizes notes on unrecognized geometry.
Unknown states leave existing notebook files intact; recognizing the same area
again restores the same `por-mac-v11-geo-N` key, with no notebook migration.

Area identity is part of `PoolRadState.sameDisplay`, including transitions to
and from unavailable identity. An identity-bearing snapshot must not leave
the previous area's flags displayed merely because its coordinates and geometry
bytes have not changed.

The two-byte DAX record prefix is excluded because the live heap payload does
not contain it. The existing decoder identifies two wall planes, an event plane,
and a door plane; those names alone would not establish immutability. For this
Mac v1.1 profile, the native CODE reference inventory independently identifies
the loader, byte-returning readers and fourth-plane door mutators. The first
three planes remain unchanged after loading. Their 768-byte prefixes have 29
unique SHA-256 hashes in the supplied GEO set. No fuzzy matching, nearest-area
selection or guessed per-bit mask is involved.

If a PRM1 action mutates any geometry byte, or a PRM2 sample has an unrecognized
immutable prefix or invalid native identity, note placement and map-linked note
access become unavailable with an explanation
such as “Area not recognized; notes are unavailable here.” Existing saved notes
remain intact and can reappear if the known area returns. Recognition does
not establish that the guest is in exploration rather than loading/combat: the
separate mode-detection backlog is still needed, and a retained last map must not
be presented as a newly recognized live area.

### Native PRM2 contract

The packet remains 1,200 bytes. Coordinates and geometry retain their old offsets.

| Bytes | Meaning and Java acceptance |
| --- | --- |
| 0–3 | `PRM2` signature; other versions except legacy `PRM1` are rejected |
| 32 | Native map mode: exactly `1` for this area-map profile |
| 33 | Native metadata validity: exactly `1`; unavailable is never a hash fallback |
| 34–35 | Big-endian GEO record ID: must exist in both paired catalogs |
| 176–943 | Exact 768-byte immutable-prefix match for that same record ID |
| 944–1199 | Mutable 256-byte door plane; still rendered and included in redraw comparison |

Native validation checks the bounded 2,048-byte movable state allocation reached
through the handle at `A5−0x5eb2`, and reads its big-endian GEO ID at `+0x18a`.
Map mode is at `A5−0x5e89`. These addresses belong only to the verified original
Mac v1.1 profile; they are not ECL virtual addresses or portable DOS offsets.

CODE6's loader at `0x6f4a` checks the 1,026-byte GEO record, copies its four
planes and stores the ID at `0x705c`. However, outer CODE5 at `0x2bca` can write
the destination ID first. Therefore the ID is cross-checked against the loaded
prefix, not trusted alone. Once the new ID and new immutable prefix agree, the
notebook identity is the destination even if a transient last-plane copy has not
yet finished. This does **not** prove an atomic four-plane frame or complete
loading/combat/wilderness presentation; those remain M2 work.
In particular, mode `1` was also observed in a Slums combat capture; it must not
be relabeled “exploration verified.” The complete executable-reference inventory,
heap-allocation validation and native tests are in [MAP_MEMORY.md](MAP_MEMORY.md).

## Reuse and validation

This extends the existing [`DaxReader` and `GeoMap`](../android/minivmac/src/main/java/name/osher/gil/minivmac/mapper/)
and [`AreaFingerprints`](../tools/AreaFingerprints.java) research, rather than
introducing a second map decoder. The DAX/RLE and plane definitions were adapted
from Gold Box Explorer; the existing [MIT attribution](../licenses/GoldBoxExplorer-MIT.txt)
still applies. The native reader's A5-relative snapshot and relocation checks
remain in [`POOLRAD.h`](../android/minivmac/src/main/jni/src/POOLRAD.h).
The required local session recall was attempted before this work and timed out;
no recalled session material was reused.

Reproduce the local check with privately supplied extracted maps and optional
PRM1 or PRM2 probes (the helper uses JDK 17; app sources remain Java 8):

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

The tool validates every supplied record against both production catalogs and
rejects duplicate identities. It also constructs source-derived PRM2 samples,
changes their complete door planes and verifies unchanged identities. These are
controlled source-data checks, not game actions. For probe packets it reports `PoolRadState.area`,
not a second exact-hash fallback that could hide an explicit identity refusal.
It prints hashes/IDs only, never geometry. All 29 private source records have
unique full and prefix hashes matching the paired catalog; every source-derived
door-plane change retains its key. The historical New Phlan PRM1 probes resolve
to record 0. This confirms those inputs, not full-game playthrough coverage.

Unit tests use invented geometry and a package-private injected catalog. They
cover relocated snapshot metadata, movement/facing, stable IDs after revisit,
all 1,024 single-byte geometry mutations, unknown/empty states, malformed or
ambiguous catalogs, input ownership, and selected real checksum-to-ID mappings
without storing original game geometry. Additional checks cover mismatched
reported IDs, reloads retaining old notebook keys, and identity-only changes
invalidating the display. The focused M1 run passes **25 tests**, including all
256 fourth-plane byte changes accepted by PRM2, all 768 wall/event-byte changes
refused, mode/validity/ID failures, early-ID transitions and ambiguous paired
catalogs. PRM1 still refuses every geometry mutation as an identity match.
Physical e-ink/stylus acceptance is not inferred from host tests.

## Actual upgrade, gate route and cold reload — 0.11.0

The new native reader and Java catalog replay fresh labeled New Phlan/Slums/
return captures with the expected `0 → 20 → 0` identities. An ordinary camp
save, clean Mac shutdown and public APK update preserve both the writable disk
and the pre-upgrade tile `11,2` handwriting. Cold boot and normal game load
restore New Phlan at `0,4 E`; the note reopens with its original strokes and
unchanged saved bytes.

The gate route was repeated in 0.11.0: named Slums at `15,4 W/E` shows none of
New Phlan's flags, and returning to New Phlan restores the flag. Settled guest
coordinates/facing and all six party HP/AC rows agree. This fulfills M1 and the
bounded M3 route check, not all-area, door-action, tactical-mode or physical
acceptance. [Screenshots, checksums and exact steps](LOCAL_TESTING.md#m1-named-area-identity-0110-2026-09-14).

## Historical gate round trip — 0.4.0

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
