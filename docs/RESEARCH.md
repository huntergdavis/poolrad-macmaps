# Research and local evidence

Research date: 2026-09-13. Updated with the first Android implementation;
[LOCAL_TESTING.md](LOCAL_TESTING.md) records commands and current evidence.

The later notes/party/reference-panel research is in
[FEATURE_RESEARCH.md](FEATURE_RESEARCH.md); the complete current queue is
[BACKLOG.md](BACKLOG.md). The user now reports the app works well on the tablet;
specific stylus/e-ink and broader-area checks remain to be recorded.

## 1. The DOS companion the user remembers

**Gold Box Companion (GBC)** supplies automapping with a party marker and notes,
alongside many features outside this project's scope. Its site targets the DOS
games under DOSBox on Windows. The author describes locating processes and
reading their memory using Windows APIs, including `ReadProcessMemory`.
[Official features](https://gbc.zorbus.net/),
[author's memory-access explanation, post 9](https://www.gog.com/forum/forgotten_realms_collection/gold_box_companion_v260/post9).

**Do not plan on porting GBC's source.** I did not find an open-source release.
Its author explicitly called it closed source in 2020. The “source code
available” text on the GBC links page refers to Simeon Pilgrim's separate
Curse of the Azure Bonds reimplementation, not GBC itself.
[Author's statement, post 3](https://www.gog.com/forum/forgotten_realms_collection/gold_box_companion_v260/post3),
[GBC links](https://gbc.zorbus.net/).

The useful idea to borrow is simple: read the game's real position and place
it on decoded map data. DOS runtime addresses, Windows process hooks, and
assumptions about executable layout do not transfer to a 68k Mac program.

DOSBox Staging's maintainers documented the brittleness of companion tools
that assume a specific DOS memory layout. More recently, **Staging 0.83 added
an opt-in HTTP memory API**. This is a useful example of cooperating with the
emulator, but it is a DOSBox feature, not an API available in Mini vMac. We do
not need to copy the server architecture for a single in-app Android map.
[2025 issue](https://github.com/dosbox-staging/dosbox-staging/issues/4277),
[0.83 release notes](https://www.dosbox-staging.org/releases/release-notes/0.83.0/).

## 2. Open-source work that actually helps

**Gold Box Explorer**, copyright 2018 Bil Simser, is MIT-licensed. Inspected
revision: `eac30abaa6ee66aea6f5d65ebe6d676b10015a8f`.
[Repository](https://github.com/bsimser/Gold-Box-Explorer),
[license](https://github.com/bsimser/Gold-Box-Explorer/blob/eac30abaa6ee66aea6f5d65ebe6d676b10015a8f/LICENSE).

Sources reused as format references in the local diagnostic:

- [DaxFile.cs](https://github.com/bsimser/Gold-Box-Explorer/blob/eac30abaa6ee66aea6f5d65ebe6d676b10015a8f/src/Common/Plugins/Dax/DaxFile.cs): archive index and run-length decompression.
- [GeoDaxFile.cs](https://github.com/bsimser/Gold-Box-Explorer/blob/eac30abaa6ee66aea6f5d65ebe6d676b10015a8f/src/Common/Plugins/GeoDax/GeoDaxFile.cs): tile planes and map-ID/name reference.
- [GeoWallRecord.cs](https://github.com/bsimser/Gold-Box-Explorer/blob/eac30abaa6ee66aea6f5d65ebe6d676b10015a8f/src/Common/Plugins/GeoDax/GeoWallRecord.cs): directional door flags.

We should port the small reader, not the Windows Forms application. Include
the original copyright and full MIT notice with any adapted code. No
copyrighted game data is bundled. The subsequent implementation vendors the
pinned GPLv2 Android emulator and ports the small reader with its MIT notice.

### Observed DAX/GEO structure

The diagnostic used the upstream DAX layout: a two-byte little-endian header
length followed by nine-byte entries (ID, relative offset, unpacked length,
stored length). Signed RLE controls copy `control + 1` literal bytes when
nonnegative, or repeat the next byte `-control` times when negative. This is
not interchangeable with standard PackBits.

Every supplied GEO block expanded to 1,026 bytes. The reference decoder treats
this as a two-byte prefix followed by four 256-byte planes for a 16-by-16 area:
north/east wall nibbles, south/west wall nibbles, event bytes, and door bits.
Local decompression and bounds were checked; map orientation and the exact
appearance/meaning of each wall type still need in-game validation.

### Local inputs, kept private

| Input in `scratch/input/` | Observation |
| --- | --- |
| `MacII.ROM` | 262,144 bytes; now boot-tested with the Mac II flavor in the local Android emulator. Tablet configuration still unconfirmed. |
| `MinivMacBootv2.dsk` | 25,165,824 bytes; `file` recognizes a bootable HFS volume, “Mini vMac Boot v2.” |
| `Pool_Of_Radiance.SIT` | 1,519,532 bytes; recognized StuffIt archive containing the Mac game and data. |

Extraction used the installed `unar` tool into `scratch/extracted/`, with
resource forks retained as visible AppleDouble `.rsrc` files:

```sh
unar -q -s -k visible -o scratch/extracted scratch/input/Pool_Of_Radiance.SIT
```

This command reported an extraction failure for `PoolRad2/ITEM2.DAX`
(“Attempted to read more data than was available”). The cause is not established:
it might be the archive or extractor. **Do not describe the whole archive as
healthy.** All eight GEO files extracted and passed the independent structural
decompression check. Do not replace or repair the user's original archive.

| GEO file | Record IDs | Count |
| --- | --- | ---: |
| GEO1.DAX | 18, 24, 31 | 3 |
| GEO2.DAX | 9, 15, 20 | 3 |
| GEO3.DAX | 0, 14 | 2 |
| GEO4.DAX | 2, 10, 21 | 3 |
| GEO5.DAX | 3, 4, 5, 6, 7 | 5 |
| GEO6.DAX | 1, 25, 28 | 3 |
| GEO7.DAX | 17, 22, 23, 26 | 4 |
| GEO8.DAX | 13, 16, 27, 29, 30, 32 | 6 |
| **Total** | **29 distinct records; all 1,026 bytes** | **29** |

All prefixes were `00 04`. The check rejected out-of-bounds file slices,
incomplete literal/repeat runs, and mismatched decompressed sizes. This was a
read-only Node diagnostic using the above format references, not a game launch
or a live automapping test.

The archive also contains `PoolRadSave/SampleParty` (12,906-byte data fork and
a resource-fork sidecar) and a game application named `Pool of Radiance v1.1`
whose extracted executable content is in its resource fork. The sample save
is a possible research aid, not a confirmed save from the user's current run.
Merely copying Linux data-fork files into a Mac disk can lose essential content.

## 3. Android Mini vMac: a feasible integration point

Inspected Gil Osher's Android source at
`b6ff92ce04a006e642ebceadf90b73d9f0b9e454`:
[repository](https://github.com/dolfin/minivmac4android),
[GPL version 2 notice](https://github.com/dolfin/minivmac4android/blob/b6ff92ce04a006e642ebceadf90b73d9f0b9e454/README).

The source defines two Android product flavors:

| Flavor | Application ID | Source version name |
| --- | --- | --- |
| macPlus | `name.osher.gil.minivmac` | 1.7.2 |
| macII | `ninja.gil.miniv.ii` | 2.7.2 |

These are values in the inspected source, not a measurement of the user's
installed app. Both Play Store listings showed a March 5, 2025 update date when
checked. Mini V II advertises a 256 KiB ROM and multiple machine variants.
[Build configuration](https://github.com/dolfin/minivmac4android/blob/b6ff92ce04a006e642ebceadf90b73d9f0b9e454/minivmac/build.gradle),
[Mini vMac listing](https://play.google.com/store/apps/details?id=name.osher.gil.minivmac),
[Mini V II listing](https://play.google.com/store/apps/details?id=ninja.gil.miniv.ii).

Follow-up: the user explicitly confirmed the app name **Mini vMac**. Treat that
as the target, rather than substituting Mini V II based on the supplied ROM's
name. The selected emulated machine and matching ROM remain to be verified
when booting the local test setup.

The source's existing bridge exposes input, emulation control, display, sound,
and disk/clipboard callbacks, but no general RAM-export method was found.
The native core already owns RAM and provides access helpers. This supports a
small same-process addition rather than external process-memory inspection.

Useful pinned source locations:

- [Core.java](https://github.com/dolfin/minivmac4android/blob/b6ff92ce04a006e642ebceadf90b73d9f0b9e454/minivmac/src/main/java/name/osher/gil/minivmac/Core.java): existing Java/native boundary.
- [jni_proxy.c](https://github.com/dolfin/minivmac4android/blob/b6ff92ce04a006e642ebceadf90b73d9f0b9e454/minivmac/src/main/jni/jni_proxy.c): variant-library loading; any new callable native entry needs corresponding proxy wiring.
- [OSGLUJNI.c](https://github.com/dolfin/minivmac4android/blob/b6ff92ce04a006e642ebceadf90b73d9f0b9e454/minivmac/src/main/jni/src/OSGLUJNI.c): emulation-loop and Android callbacks; candidate location to capture a consistent snapshot on the emulation thread.
- [GLOBGLUE.h](https://github.com/dolfin/minivmac4android/blob/b6ff92ce04a006e642ebceadf90b73d9f0b9e454/minivmac/src/main/jni/src/GLOBGLUE.h): `RAM`, `kRAM_Size`, RAM readers, address mapping helpers.
- [ENDIANAC.h](https://github.com/dolfin/minivmac4android/blob/b6ff92ce04a006e642ebceadf90b73d9f0b9e454/minivmac/src/main/jni/src/ENDIANAC.h): big-endian guest-word access; do not cast guest state to host-native structures.
- [EmulatorFragment.java](https://github.com/dolfin/minivmac4android/blob/b6ff92ce04a006e642ebceadf90b73d9f0b9e454/minivmac/src/main/java/name/osher/gil/minivmac/EmulatorFragment.java): emulation thread and posting display changes onto the Android UI thread.

The snapshot patch now uses these integration points and has captured the
running guest's RAM locally. Live field extraction is not yet implemented.
RAM reads need bounds checks and lifecycle handling for reset, shutdown, and
machine changes. Poll the resolved fields after detection; do not repeatedly
copy all RAM to redraw a small map.

One upstream build wrinkle was Gradle unconditionally reading
`release.properties`. The fork removes that dependency and its signing setup;
a personal/debug build does not require the upstream
maintainer's signing credentials. Preserve upstream licensing if distributing
the modified emulator, and keep game/ROM content separate from its source/APK.

### Tablet layout follow-up

The user proposes a map above the Mac display and the virtual keyboard below,
using currently spare tablet space. Inspected the following at the same pinned
Android revision:

- [screen.xml](https://github.com/dolfin/minivmac4android/blob/b6ff92ce04a006e642ebceadf90b73d9f0b9e454/minivmac/src/main/res/layout/screen.xml): a vertical `LinearLayout`, weighted display container with screen/trackpad, and an optional keyboard beneath it.
- [ScreenView.java](https://github.com/dolfin/minivmac4android/blob/b6ff92ce04a006e642ebceadf90b73d9f0b9e454/minivmac/src/main/java/name/osher/gil/minivmac/ScreenView.java): aspect-ratio scaling/centering within the measured host view and translation of touch coordinates into guest coordinates.
- [EmulatorFragment.java](https://github.com/dolfin/minivmac4android/blob/b6ff92ce04a006e642ebceadf90b73d9f0b9e454/minivmac/src/main/java/name/osher/gil/minivmac/EmulatorFragment.java): `toggleKeyboard()` switches the embedded keyboard between visible and gone.

Inference: a separate map view above the existing game container fits this
architecture. It will require deliberate height allocation and touch/layout
verification, not just painting into the display's current blank margins.
The map and game remain independently sized. The Android layout is now
implemented and checked at 1200×1600, keyboard open/closed. Physical e-ink
readability and refresh behavior remain a device check.

## 4. Why not simply attach to the Play Store app?

Android assigns apps separate identities and isolates their memory and private
storage. Running native C code in a second app does not bypass that isolation.
An overlay permission only changes presentation; it does not grant RAM access.
[Android's application sandbox documentation](https://source.android.com/docs/security/app-sandbox).

Mini vMac's advertised clipboard and experimental LocalTalk integration are
not party-state feeds. Upstream ClipOut is an actual old-Mac desk accessory with
source, but exports the guest clipboard when invoked, not a continuously
available game-memory API.
[ClipOut documentation](https://minivmac.github.io/gryphel-mirror/c/minivmac/extras/clipout/index.html).

A purpose-built in-guest desk accessory might avoid an Android fork, but its
access to this game's state, scheduling while the game runs, and available
screen space all need proof. It does not remove the core memory-discovery
work. Keep it as a fallback if a personal Android build proves unacceptable.

## 5. State of the project after this pass

```text
[PROVEN]  local files present; originals left untouched
[PROVEN]  29 map records decode to the expected size
[PROVEN]  Android fork builds; copied Mac II setup boots the game and sample party
[PROVEN]  read-only 8 MiB snapshots from the running guest
[PROVEN]  native PoolRad menu and offline code-wheel lookup
[PROVEN]  A5-relative position/map probe reproduces two labeled RAM captures
[PROVEN]  live B&W New Phlan pane above the Mac after loading the sample party
[PROVEN]  keyboard below both panes; empty startup geometry rejected
[PROVEN]  live tour positions/facing match guest; map hide/show and resume work
[PROVEN]  manual turn, forward step, and blocked step match the guest readout
[TODO]    area transitions and non-exploration context
[TODO]    physical e-ink tablet check and personal startup shortcut
```

`deja "Pool Radiance Mini vMac automap"` and
`deja "DAX GEO Pool Radiance map decoder"` found no reusable indexed sessions.
The reuse here is from the cited source repositories, not recovered past-agent
implementation. The Android build/game-boot and snapshot claims now have local
test evidence. No claim of a completed automapper or full game-archive integrity
is made.

The live pane reuses the existing attributed GEO decoder. A separate map import
was dropped: the game holds the current four geometry planes behind a movable
Mac handle, reachable from CurrentA5. The read-only probe copies 1,200 bytes at
a safe emulation tick, not the whole RAM image. Profile offsets, synthetic
bounds/relocation tests, and the exact local evidence are documented in
[LOCAL_TESTING.md](LOCAL_TESTING.md#native-profile-and-update-path).

## 6. Code-wheel addition

The user supplied [Dave Kennedy's lookup](https://dkennedy.io/por-code-wheel/wwm.html)
and requested a **PoolRad → Code wheel lookup** menu action. The linked source
credits Andrew Schultz. Our Java implementation uses the same 36-entry table,
with rune numbers 1–36 and path offsets 0/12/24. It passes the answer to the
existing emulator keyboard interface and presses Return; it does not patch
the game or use a network lookup. The 30/20/dash-two-dots answer was accepted
by the actual Mac game during setup. The native Android helper subsequently
selected 21/14/dashes and submitted `WYVERN` plus Return successfully with the
test emulator's Wi-Fi and mobile data disabled.

The reference repository has no published license granting image redistribution.
At the user's request, the personal 0.2.1 APK now bundles the 72 illustrations
recovered from the existing private cache. The artwork stays in an ignored
`private-assets/codewheel/` build-input folder, not the source repository; this
does not claim an open-source license for those images. Public redistribution
of the artwork/APK is outside this personal-build arrangement. Runtime lookup
has no download/cache code and reads only packaged assets.

Test-game preparation uses MacBinary **II** (including the header CRC), as
required by the installed `hcopy` utility, preserving the game's resource fork.
[MacBinary II format reference](https://learn.microsoft.com/en-us/openspecs/exchange_server_protocols/ms-oxcmail/ec1a8b63-ae1e-47d2-ba3e-473a4b27eb45).
