# Game archive integrity — Q2

**Resolved 2026-09-14:** the supplied archive's `PoolRad2/ITEM2.DAX` can be
recovered intact. `unar` 1.10.8 fails to extract it, leaving an empty file;
the archive itself contains checksum-correct data. A new private extraction and
game disk now contain the recovered file. **Existing tablet disks, saves and
personal APK bundles have not been replaced.**

This is an input-integrity fix, not a change to the original game, a full-game
playthrough, or a claim of on-tablet acceptance.

## Check before making a disk

Use Python 3 and a fork-preserving extraction, such as unar's visible AppleDouble
sidecars. Keep the original archive and extract only into a new private folder.
Never ignore an extractor's nonzero exit status.

```sh
# Strong check: original archive versus every extracted data/resource fork.
# DIRECTORY is the parent containing the archive's "Pool Of Radiance" folder.
python3 tools/verify-game-extraction.py \
  --archive scratch/input/Pool_Of_Radiance.SIT scratch/verified-extraction

# Weaker check when the archive is unavailable: all DAX indexes and RLE streams.
python3 tools/verify-game-extraction.py 'scratch/verified-extraction/Pool Of Radiance'

# Only after the source audit succeeds; destination must not already exist.
node tools/prepare-test-game.mjs \
  'scratch/verified-extraction/Pool Of Radiance' scratch/NEW-game.dsk
```

The audit is read-only, returns JSON on success and exits nonzero with affected
paths on failure. It does not download, decompress or repair files. The Node
game-disk builder now runs DAX preflight before staging or creating a disk.
The combined-boot builder checks DAX before growing/copying its input images;
it no longer packages an empty ITEM2 with only a warning.

The full audit validates classic StuffIt header CRCs and each declared fork's
size/CRC, checks AppleDouble resource-envelope bounds, and validates every DAX
record's index/range and compressed output length. Genuine empty forks remain
legal; an empty extraction of a declared 632-byte fork does not. DAX-only checks
cannot detect all same-length corruption: that requires the source CRC check.

Supported scope: classic `SIT!`/`rLau` v1/v2 with a 22-byte header, unencrypted
entries, Mac filenames fitting the supported extraction paths. Limits are
128 MiB/archive, 16 MiB/fork, 4,096 headers and 64 nested folders. Unsupported
formats, unsafe paths, symlinks and malformed structures fail explicitly.
The top header's unused CRC field is not enforced; individual 112-byte entry
header CRCs are. The supplied v2 file uses zero in the top field.
This is not a general archive-security scanner or a claim of source authenticity.

## Recovery evidence

Original `scratch/input/Pool_Of_Radiance.SIT`: **1,519,532 bytes**, SHA-256
`6b00883f03f2eca2aaf1d5e9bbb6c6587b3ff5a864a22ccf5483ed5d4d461f9a`.
The source hash is unchanged after recovery and disk creation.

| Check | Result |
| --- | --- |
| `lsar -t` / unar 1.10.8 | 119 passed / 1 failed; counts include entries/forks |
| Failing fork | `Pool Of Radiance/PoolRad2/ITEM2.DAX` data, extracted as 0 bytes |
| Archive declaration | Method 13, 417 compressed bytes, 632 unpacked bytes, CRC-16 `feec` |
| Alternate extraction | 632 bytes, matching CRC `feec`, 8 valid DAX records |
| Full corrected extraction | 108 files, 114 nonempty forks, 81 DAX files, 606 records pass |
| Change inventory | Only ITEM2 data bytes changed; every other extracted file/sidecar identical |

Recovered ITEM2 SHA-256:
`48bedd08c0750aa174f4309d4fe742e7b9df9ffef63954d00fc29643e8512ace`.
CRC uses reflected polynomial `0xa001`, initial zero; the synthetic known-vector
test checks `123456789` against `bb3d`.

Private evidence is in `scratch/q2-archive.Ih4moF/`: `source.sit`, `listing.json`,
`integrity.log`, `extraction.log`, original `extracted/`, alternate
`maconv-extracted/`, and corrected **`verified-extraction/`**. No game payload is
checked into Git or published with these tools.

### Alternate decoder

[Maconv](https://github.com/ParksProjets/Maconv), pinned commit
`e54e2f5b5e388bc3f5c2d4649d00ec37e8af64dd`, recovered the fork with an unchanged
method-13 decoder. Its implementation is The Unarchiver-derived; this does not
establish the exact cause of unar's read-past-end error.

Build prerequisites are CMake and a C++ compiler. The private source copy needed
only missing standard-header compatibility fixes: add `#include <array>` after
`<memory>` in `src/formats/file_signature.cc`, then:

```sh
cmake -S scratch/q2-archive.Ih4moF/Maconv \
  -B scratch/q2-archive.Ih4moF/maconv-build \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
  '-DCMAKE_CXX_FLAGS=-include cstdint -include string'
cmake --build scratch/q2-archive.Ih4moF/maconv-build -j2
# Use a NEW destination when repeating extraction:
scratch/q2-archive.Ih4moF/maconv-build/maconv e \
  scratch/q2-archive.Ih4moF/source.sit scratch/NEW-maconv-extraction
```

**Do not use Maconv's whole output as a replacement game tree.** Its `.rsrc`
files are raw forks, not unar's AppleDouble envelopes; its StuffIt importer has
incorrect metadata assignments and does not verify fork CRCs. We copied only
the validated ITEM2 data into a new copy of unar's fork-preserving tree, then
audited the complete result against the original archive. Maconv remains a
separate private GPLv3 investigation tool, not an Android dependency or vendored
decoder. The new repository checker is a metadata/content validator only.

### New HFS disk and regression checks

`scratch/q2-archive.Ih4moF/verified-game.dsk` was created from the audited tree.
An independent `machfs` read verifies its exact 108-file inventory, all data and
resource bytes, every type/creator field and all 606 DAX records. The game
application retains its **327,595-byte resource fork**. The import cleared flag
bit `0x0100` on seven files; no exact Finder-flag preservation is claimed for
this newly constructed test volume. No file-content or type/creator mismatch
was observed. Existing disks were not mounted or edited.

Disk SHA-256:
`89a6573495f96bfab4f46fd06bec0e6bdb221d58d47f941a879de8f7f7804496`.
Newly generated HFS metadata includes timestamps, so this output hash is an
evidence identifier, not a reproducible-build promise.

Tests executed: **21 archive/DAX tests, 14 boot-builder tests, 16 package tests,
25 fetch tests and 346 Java tests**, all passing. Android debug build succeeds.
Fixtures are invented, not copied game bytes. Checks include a failed Node
preflight leaving no destination and a damaged HFS DAX being rejected before
boot-disk growth. No fresh emulator boot, encounter playthrough, or physical
e-ink/stylus test was performed for this tooling-only change.

## Existing campaigns: keep your saves

An app update deliberately does not replace writable disks. The old personal
bundle and installed tablet disk may therefore still contain empty ITEM2.
**Do not install this fresh sample-game disk over an existing campaign.**

For an existing campaign, first save, quit and cleanly shut down the guest;
export its current disk and retain an untouched backup. Any follow-up repair
must replace only the verified ITEM2 data on another copy, retain the current
save/resource forks, and be checked before import. This change supplies the
verified source and build-time rejection, not an automatic installed-disk
repair command. No current game progress was discarded to resolve Q2.

## Reused sources

- Existing [fork-preserving disk builder](../tools/prepare-test-game.mjs),
  [boot builder](PERSONAL_BOOT.md) and earlier input warning. Local `deja`
  recalls timed out; the handoff and these recorded findings were reused.
- [XADMaster StuffIt parser](https://github.com/MacPaw/XADMaster/blob/master/XADStuffItParser.m)
  and [CRC implementation](https://github.com/MacPaw/XADMaster/blob/master/XADCRCHandle.m):
  entry layout, fork CRC and top-header handling.
- [Maconv](https://github.com/ParksProjets/Maconv), its StuffIt format notes and
  observed extraction; [XADMaster method 13](https://github.com/MacPaw/XADMaster/blob/master/XADStuffIt13Handle.m)
  for decoder context. No external decompressor code was copied into the app.
- Existing `mapper/DaxReader.java`, adapted from Gold Box Explorer; see
  [its retained MIT attribution](../licenses/GoldBoxExplorer-MIT.txt).
