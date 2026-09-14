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

## PRM3: settled exploration, not combat coordinates

F10 adds a narrow **positive exploration gate**, independent of map identity.
A combat screenshot and its read-only capture have presentation mode `1` just
like exploration: that byte alone cannot authorize footprints or a live walking
arrow. The additional fields come from the same original Macintosh executable,
not a DOS profile or a list of values observed without code evidence.

| A5-relative field | Required value | Verified use |
| --- | --- | --- |
| `−0x5e90`, engine/layout byte | `4` | CODE 3 `+0x2d40` initializes the local exploration view. CODE 9 combat entry `+0x54` writes `5` at `+0x5a`; CODE 4 camp entry `+0x1c2a` writes `2` at `+0x1c36`, restoring the previous value at exit. Outdoor paths select `3`. |
| `−0x617e`, active input tag | `0x56`, or verified story path below | Ordinary movement loop CODE 4 `+0x5e08..0x5e3e` clears pending input, sets this tag, calls the input reader, then clears the tag **before** acting on its result. Rolf uses tag `0`; only the proven menu, forward, and committed table-driven tour phases below are added. Engine/startup checks remain mandatory. |
| `−0x60a4`, pending input byte | `0` | Cleared at CODE 4 `+0x5e08` immediately before entering that input wait. An already pending action is not a settled sample. |
| `−0x5ed4`, big-endian menu-state word | `2` | CODE 2 `+0x7432` / `+0x747c` store the current menu classification before updating enabled items. Case `2` of `+0x432a` reaches `+0x4574`, disabling File **Load Saved Game**, **Save Current Game**, and **Begin Adventuring**. |
| `−0x30e1`, initial party/load flag | `0` | CODE 7 `+0x1df6` sets `1` before initial party selection and the saved-game loader; `+0x1e34` clears it after successful loading. The manual File-load completion path clears it at CODE 2 `+0x3f92`. |
| `−0x5e8b`, pending loaded-game continuation | `0` | Successful automatic/manual load sets it at CODE 7 `+0x1e30` / CODE 2 `+0x3f8e`; the script initialization path consumes it at CODE 5 `+0x43a`. |
| `−0x1921`, script-coordinate relocation flag | `0`, or the exact Rolf route exception below | Script variable setters CODE 5 `+0xfbe`, `+0xfce`, and `+0x1026` mark relocation before coordinate changes. The redraw path tests it at `+0x2e1e` and clears it at `+0x2e3c`. Ordinary movement CODE 4 `+0x5ad6` changes coordinates without this flag. General scripted setters remain hard discontinuities. |

The loading checks matter: CODE 2's saved-game reader restores the engine byte
at `+0x3354..0x3358` **before** all state/maps finish loading. Merely seeing
engine `4` would accept an intermediate load. The initial-load flag, pending
loaded-game flag, and actual menu eligibility close that known path. Root's
separate live `f10-file-walking.png` screenshot confirms all three File commands
above are disabled during ordinary exploration. Camp is rejected independently,
even if it uses input tag `0x56` and the same local geometry.

These predicates are added to the existing bounded application, A5, geometry,
state-handle, local-mode, and record-number checks. Java must **still** authenticate
the exact immutable prefix against the reported GEO ID. Unknown/malformed state
is not an exploration sample. An unsafe sample may retain an authenticated area
map for viewing; it cannot authorize a new visited tile. PRM4 separately proves
whether its current position is suitable for display, including narrative text.

### Host-only continuity token

`poolrad_walk_tracker` lives on the emulator thread and never writes guest RAM.
`OSGLUJNI.c` observes it once on entry to `WaitForNextTick`, not merely when the
Android UI asks for a map. `poolrad_walk_probe` observes once more at delivery.
Entering a hard discontinuity (camp/combat/outdoors, loading/general relocation flag,
non-exploration menu, or an invalid current-game profile) changes its epoch. A changed A5,
geometry address, state address, or GEO number also changes it. Addresses serve
only as conservative continuity breaks; they never become saved area keys.

**Clearing the input tag or receiving pending input alone does not change the
epoch.** Normal walking itself does that between every step. Java identifies a
PRM3 version-1 packet with `safe=0` and engine `4` as `explorationProcessing`:
it records nothing and leaves the previous safe observation unchanged, rather
than interrupting the route during each ordinary action. The next safe sample
must still have the same native epoch, notebook, and area, be adjacent to the
last safe position, and arrive within 1,250 ms of that observation. Processing
frames do not refresh that deadline. A loading/menu/script change may also have
engine `4`, but its native epoch change prevents joining across it. Repeated
observation of the same hard interval does not repeatedly advance the token. Native restart
advances the sequence; unsigned-32-bit exhaustion disables recording instead of
wrapping to an earlier token.

System 7 background scheduling is **not** such a discontinuity. Apple's
[context-switching documentation](https://dev.os9.ca/techpubs/mac/Toolbox/Toolbox-34.html)
explains that a minor switch can leave the game's windows frontmost while
replacing its A5 world and application-specific low-memory environment.
[CurrentA5 belongs to the currently executing process](https://dev.os9.ca/techpubs/mac/Memory/Memory-10.html),
not necessarily the frontmost window. Actual `f7-combat-active-1.ram` and
`-3.ram` contain Finder's name/A5 while `-2.ram` contains the game's. The first
live F10 trial also exposed the consequence: two correctly visited tiles but
repeated disconnected anchors rather than the observed one-tile step.

The corrected **tick observer only** treats a bounded, nonempty, different
application name as an observation gap. It leaves the prior context, epoch,
and any already observed hard break unchanged. It never dereferences a cached
game pointer while another process is current. A malformed name/truncated RAM,
or the exact game name with invalid A5/map/state, still breaks continuity.
An actual Android map request during a background-process interval still returns
unavailable and breaks the consumer's segment. On return to the game, every
normal profile and continuity check runs again. This removes an artificial
per-scheduling-slice epoch break; it does not pretend to observe execution during
the gap or weaken the separate loading/combat guards.

This is sampled evidence, not an instruction-by-instruction execution log.
A transition completely between native observations can be missed. The consumer
therefore also breaks on missing samples, non-processing unsafe samples, time
gaps, context changes, and nonadjacent movement. Skipped local-processing frames
cannot authorize a tile or bypass the next safe sample's checks; the consumer
must never interpolate an unobserved route or infer
direction from facing alone. No claim is made to detect every possible game
script, every tactical combat state, or the exact stage of a map redraw. Those
broader mode/presentation details remain outside this narrow F10 gate.

### Wire format and checks

PRM3 keeps 1,200 bytes and all PRM2 identity/geometry offsets. Its explicit magic
is essential: PRM1/2 copied unchecked trailing bytes from the diagnostic app-name
buffer, so a plausible-looking extension in an old packet is not trustworthy.

| Bytes | PRM3 meaning |
| --- | --- |
| `0..3` | `PRM3` |
| `25` | Movement-profile version, exactly `1` |
| `26` | Settled exploration: exactly `0` or `1` |
| `27` | Raw verified engine byte; `255` if unavailable |
| `28..31` | Big-endian unsigned continuity epoch; `0` disables tracking |
| `32..1199` | Unchanged PRM2 identity, diagnostic, globals, and geometry fields |

The original `poolrad_probe` still emits PRM2 for independent party readers and
legacy diagnostic tools. `poolrad_walk_probe` remains the PRM3 tracked wrapper;
current JNI display delivery uses its PRM4 extension below. Legacy PRM1/2 maps
never enable movement recording.

Native address/undefined-behavior sanitizer tests cover all input-tag, pending
input, engine, and low menu values; each hard-break field; ordinary busy/turn/move
sequences that retain the epoch; area round trips between UI polls; A5, map, and
state relocation; invalid/short RAM; restart; overflow; and unchanged guest RAM.
Additional regressions cover safe game → Finder ticks → same safe game preserving
the epoch; a real game hard break surviving intervening Finder ticks; requested
Finder samples remaining unavailable; and malformed names/current-game A5 still
breaking the epoch.
Existing map, party, and wheel native suites also pass.

Read-only replay of actual captures gives these independently labeled results:

| Capture | Result |
| --- | --- |
| `m1-new-phlan-before.ram` (Rolf introduction) | Safe after the story-menu correction below; the old tag-only gate incorrectly rejected it |
| `m1-slums-arrival.ram`, `m1-slums-settled.ram` | Safe: local exploration engine `4` |
| `m1-new-phlan-return.ram`, `m1-upgrade-reload.ram` | Safe: settled New Phlan after return/reload |
| `f7-combat-active-2.ram` | Unsafe: combat engine `5`, despite local presentation `1` |
| `f10-walking.ram` | Safe: New Phlan `(0,4)` east after dismissing the File menu |
| `f10-camp.ram` | Unsafe: camp engine `2`, same area and coordinates |

This verifies the positive and negative native inputs; end-to-end rendering,
on-device acceptance, and physical e-ink/stylus behavior are separate checks.
The mandatory F10 `deja` recall timed out without reusable results; this work
reuses the existing M1 probe and private resource-disassembly tooling.

### Rolf's guided tour: scripted movement is still real movement

The 0.12 tag-only gate caused a user-visible regression: the original guided
tour's map arrow/footsteps disappeared until ordinary walking resumed. Three
unmodified introduction/tour captures (`m1-new-phlan-before.ram`,
`phlan-15-1-west-intro.ram`, and `phlan-11-2-south-tour.ram`) show valid GEO `0`,
engine `4`, local presentation `1`, menu state `2`, and zero pending-input,
startup, loaded, and relocation flags. Their tag is `0`, but the active script
opcode at A5 `−0x2f60` is `0x2b`, not an unclassified loading interval.

The first correction verified these two tag-zero paths under the existing
local-profile, menu, loading, pending-input, and relocation guards:

- **Horizontal story menu (`0x2b`).** The original Macintosh dispatcher at
  CODE 5 `+0x5c84` selects `+0x5e06`, which calls the handler at `+0x3cdc`.
  It calls helper `+0x19de`; that helper explicitly clears pending input and
  the input tag at `+0x1a0c..0x1a10`, then waits through the ordinary input reader
  at `+0x1a44`. Rolf's Continue prompt is therefore a legitimate observed local
  position, not necessarily an unfinished movement. Any selected-result write
  that changes coordinates still uses the guarded script setter.
- **Exact scripted one-tile-forward call.** Opcode `0x2d` with decoded operand
  high byte A5 `−0x2f16=0xc0` and low byte `−0x2ed5=0x1e` calls target `0xc01e`.
  CODE 5 `+0x2d92` translates it to switch key `0x401f`; the original compiler
  helper at CODE 1 `+0x134` establishes the offset convention, resolving CODE 5
  `+0x2de0`'s offset `0xae` to `+0x2e8e`, then `+0x1d8e`. That routine changes
  exactly one coordinate by the facing direction, wraps at map bounds, and
  refreshes tile/door fields. It does not set the relocation flag or load GEO.

Actual live testing then showed that permitting these alone restored the tour
stops but **not the guided movement between them**. `m2-tour-moving-1.ram` is
engine `4`, tag `0`, pending `0`, menu `2`, GEO/ECL `0`, position `(6,3)` west,
relocation `1`, opcode `0x2d`, decoded CALL `0x2c90`, VM IP `0xb1b9`. This is the
redraw routine, not the separate one-tile-forward call.

The original New Phlan ECL explains why. Its route loop at VM `0xb145` reads a
34-entry x/y/facing/event table, then calls helper `0xb1a7`. That helper saves
the staged x and y separately to VM variables `0xc04b` and `0xc04c`, calls
`0x2c90` to redraw, then plays a sound/delay. The next route entry is an adjacent
tile or a turn in place. Direction changes also use the relocation setter at
CODE 5 `+0x1026`. Treating every such flag as teleportation broke every tour step.

The exception is restricted to this original routine, not arbitrary scripts:

- GEO `0`, active ECL byte at A5 `−0x192b=0`, and state-block ECL byte `+0x1e5=0`.
- A5 `−0x5ea6` resolves to a bounded, correctly tagged logical 7,680-byte program
  allocation. The live VM IP is the big-endian word at A5 `−0x5e96`.
- The immutable loop `[0xb145,0xb1c5)` and route tables `[0xb592,0xb61a)` match
  their combined 64-bit FNV-1a profile checksum `5b12181c49eae5fa`. Their separate
  SHA-256 evidence hashes are `8334d494374e79f5e4750187730ce26d662abaf62221f483d7c78b4418be62ea`
  and `1b6bc299450febc6a14b4c26959dd935f7cc0ae0c0a75ef96e06794f0d0f13d9`.
  No original program payload is distributed. This is a bounded supported-version
  checksum, **not an adversarial security boundary**; the native profile already
  assumes the supplied original Mac executable, rather than a hostile emulator.
- Only the verified GETTABLE/GOSUB/SAVE/CALL instruction and operand-loading
  phases preserve the epoch while direction/x/y are being committed. These
  half-coordinate phases remain status-only **Updating**, never footstep samples.
- The exact redraw CALL at IP `0xb1b9`, and its following volume/sound/delay
  boundaries `0xb1bf`, `0xb1c3`, `0xb1c4`, expose the already committed position.
  Pending input, loading, menu, native map identity, and Java catalog checks
  still apply. An unrelated opcode, IP, ECL, or changed loop/table loses the exception.

**General scripted coordinate setters still cause a hard epoch break**, even
when adjacent. A turn alone adds no footstep. The recorder still needs actually
observed adjacent positions, the same epoch/area/notebook, and its time bound;
missed positions are never filled in or inferred from facing.

Native regressions cover every active-opcode byte, each possible high/low CALL
operand byte, menu pending-input rejection, forward→menu continuity, and a
general scripted relocation interrupting even an adjacent result. Optional
private-capture tests exercise every accepted intermediate instruction phase,
withhold half-coordinate positions, preserve the epoch, and reject changed
instructions, route tables, or ECL identity. They modify only a malloc-owned
test copy, never the capture file or emulator. The actual moving redraw capture
now returns `safe=1`; `m2-tour-moving-2.ram` (PRINTCLEAR at `(5,2)` east) is
displayable with `safe=0`; its subsequent Continue capture is `safe=1`. Live
end-to-end moving-footprint acceptance remains separately recorded by the root.
Walking remains safe while combat, camp, and loading/setup remain unsafe.
The existing MIT-licensed Gold Box Explorer opcode/operand decoder was
used as leads, then independently verified against the original Mac dispatchers
and handlers; no DOS instruction address is used by the implementation.

## PRM4: explicit display modes without stale local coordinates

M2 separates **which guest mode is known** from **whether a valid local map is
available**. Previously, a missing/zero map prevented any packet from reaching
Android, while combat/camp could leave perfectly valid but inactive exploration
geometry in RAM. `poolrad_display_probe` emits the same 1,200-byte envelope with
magic `PRM4` and display mode at byte `24`:

| Value | Display mode | Independently verified classification |
| --- | --- | --- |
| `0` | Unavailable | No supported named state; no guessed interpretation |
| `1` | Exploration | Engine `4`, local presentation `1`, menu `2`, no loading flags, and committed coordinates (relocation `0`, or the verified completed Rolf coordinate commit above). Geometry still needs separate authentication. |
| `2` | Combat | Engine `5`, written at CODE 9 combat entry `+0x5a` |
| `3` | Camp | Engine `2`, written at CODE 4 camp entry `+0x1c36` |
| `4` | Wilderness | Engine `3`; CODE 3 `+0x2d14..0x2d38` selects the outdoor view. CODE 5 `+0x3036..0x3072` selects presentation `2`, `3`, or `4` with engine `3` for the outdoor sectors. |
| `5` | Loading / setup | Initial party/load flag `−0x30e1=1` or pending loaded-game continuation `−0x5e8b=1`; takes precedence over the saved engine value |
| `6` | Updating | Engine `4` with nonlocal presentation, non-exploration menu, or uncommitted/general relocation. No local position is exported. |

The loading label deliberately includes **setup**: the original flag spans
initial party selection as well as reading a saved game. It is not a claim to
measure disk I/O or identify every resource-loading instruction. Engine `0`
without either flag (including the real title/code-wheel captures) remains
Unavailable. Other unclassified engines `1`, `6`, and `7` also remain
Unavailable; no shop, aftermath, or picture-dialog label is inferred. Named
states require the original engine range `0..7`, presentation `1..4`, and both
loading flags to be exactly `0` or `1`. Invalid combinations remain Unavailable.

### Independent status profile

Mode-only output still requires the exact supported application name and a
valid, even 24-bit `CurrentA5`. The original CODE 0 resource declares 25,572
bytes below A5 and 4,688 above; that entire interval must be within RAM. The
state handle at A5 `−0x5eb2` must resolve to the independently verified logical
2,048-byte movable block with valid header, correction, alignment, and full
physical bounds. This is a stronger check than reading a conveniently small
engine byte from arbitrary startup RAM. It does **not** require a valid GEO
handle or local coordinates. Unrelated applications, malformed A5/state blocks,
or incomplete RAM return no packet.

PRM4 preserves the PRM3 walk version/safety/engine/epoch fields at `25..31` and
the PRM2 presentation/identity layout at `32..35`. Byte `24` replaces the last
byte of the old diagnostic application-name copy: it is no longer a complete
name field. The supported name is validated against guest RAM before output,
not reconstructed from packet diagnostics.

Every non-exploration mode is **status-only**, even when an old local GEO record
is still present in RAM. Exploration with unavailable geometry/identity is also
status-only. Such packets set identity-valid byte `33` to `0`, record number
`34..35` to `0xffff`, zero geometry-pointer diagnostics `40..47` and all
globals/geometry `48..1199`, then set coordinate/facing bytes `130..132` to
`255`. They always have `safe=0`. An authenticated local exploration packet can
retain geometry and display its verified current position while `safe=0`, for
example during narrative printing, but it cannot record a new visited tile.
Displayability is no longer confused with the stricter footstep gate.
`safe=1` requires mode Exploration,
valid local identity metadata, and a nonzero epoch; Java separately requires
the exact ID/prefix catalog match.

The UI may retain an explicitly labeled **last known map** as a reference during
combat, camp, wilderness, setup, or unavailable states. The packet does not
present its old coordinates as current, and cannot authorize note creation or
footprint recording in those modes. This is not a tactical combat map or a
wilderness coordinate decoder.

### Verification boundary

`tools/test-map-probe.c` tests all 256 engine values, all 256 presentation bytes,
each possible byte in both loading flags, status-only output with absent/zero
GEO or out-of-grid coordinates, exact clearing of every coordinate/geometry byte,
unknown bootstrap state, independent A5/state bounds, every state-block header
tag, null callers, and relocation. PRM2/PRM3 behavior and the existing party/wheel
suites remain passing under address/undefined-behavior sanitizers.

Production PRM4 replay of unmodified private captures confirms:

- `startup.ram`, `wheel-fresh-20260913.ram`: Unavailable, status-only engine `0`.
- `m1-new-phlan-before.ram`: Exploration with valid map, `safe=1` during Rolf's
  verified horizontal story menu after the guided-tour correction above.
- `f10-walking.ram`: Exploration with valid map and `safe=1`.
- `f10-camp.ram`: Camp, no local geometry/coordinates exported.
- `f7-combat-active-2.ram`: Combat, no local geometry/coordinates exported.
- `f7-combat-active-1.ram`: no packet while Finder is executing.
- `m2-loading-setup.ram`: actual original-game setup after automatic code-wheel
  completion, awaiting initial File Load. The 8,388,608-byte unmodified capture
  emits display mode `5`, engine `0`, `safe=0`, presentation `1`, identity-valid
  `0`, record `0xffff`, and x/y/facing all `255`; the native wrapper exports no
  stale map geometry. The independently captured `m2-loading-setup.png` shows
  the Loading / setup state over the original game.

Wilderness classification still has original-code and synthetic regression
evidence, not a labeled actual wilderness capture here. Root records other
actual UI/route acceptance separately. No physical tablet,
e-ink, or stylus acceptance is implied. The required M2 `deja` query timed out
without reusable hits; this work reuses the F10 proof and parser directly.
