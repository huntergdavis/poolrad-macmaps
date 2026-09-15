# The outdoor view in memory — research notes

**Status: research only. L1 is not implemented.** The backlog gates L1 on
validating the wilderness's own structure and location first, and explicitly
forbids stretching the 16×16 area renderer to impersonate it. This records what
is now established, what is ruled out, and the one thing still missing.

## The wilderness has still never been observed

Every RAM capture this project holds — twenty-three of them — reports
exploration, camp, combat, loading or unavailable. **None is in the outdoor
mode**, so nothing here rests on a labelled wilderness capture, and the mode's
classification remains what it has always been: original-code evidence plus
synthetic regression coverage. See [MAP_MODES.md](MAP_MODES.md).

## Ruled out: the wilderness is not one of the 29 GEO records

All 29 catalogued records are local 16×16 areas, including the outlying sites
the wilderness leads to — the Kobold Caves, the Nomad Camp, the Zhentil
Outpost, the two dark caves. [AREA_NAMES.md](AREA_NAMES.md) describes several
of them as reached from *"the wilderness entrance"*, which is consistent: the
outdoor view is the thing those entrances sit on, not another GEO record. So
there is no existing geometry for L1 to reuse, which is exactly why the backlog
warns against pretending otherwise.

## Verified from the original code

Disassembled from the supplied Macintosh v1.1 `CODE` resources:

| Where | What it does |
| --- | --- |
| CODE 5 `+0x2ff0` | the local path: saves the current presentation into `-0x5e8a(a5)`, writes presentation `1` to `-0x5e89(a5)` and engine `4` to `-0x5e90(a5)` |
| CODE 5 `+0x3036` | outdoor sector A: presentation `2`, engine `3` |
| CODE 5 `+0x3044` | outdoor sector B: presentation `3`, engine `3` |
| CODE 5 `+0x306a` | outdoor sector C: presentation `4`, engine `3` |
| CODE 3 `+0x2d14` | outdoor entry: engine `3`, then draws through the same window long at `-0x51a2(a5)` the local entry at `+0x2d3a` uses |

Two further facts matter for any future reader:

- **The outdoor paths read the same 2,048-byte game-state block** the local map
  already uses, through `-0x5eb2(a5)`. Sector B tests `+0x366` against `0xfe`
  and reads `+0x344`. The wilderness state is therefore fields inside a block
  this project already validates, not a separate allocation to find.
- **The three sectors are chosen by a switch on the script id** at
  `-0x192b(a5)`, dispatched through the compiler's switch helper `$3a(a5)` at
  CODE 5 `+0x3026`, with a small three-case table inline immediately after the
  call. The table's exact encoding is not claimed here; it was read as bytes,
  not decoded against a running game.

## What is still missing

The party's outdoor position, and whatever the outdoor view draws as a map.
Neither can be found the way the tactical grid was, because that method needs a
before/after capture pair around one confirmed step — and the party has to be
in the wilderness to take that step.

**What would unblock it:** a campaign that has left the city. A fresh
`SampleParty` cannot: the wilderness lies beyond the districts it can reach,
and the two story NPCs who would send it there are gated (see
[MESSAGE_MEMORY.md](MESSAGE_MEMORY.md)). Either a saved game already outdoors,
or a session played far enough to travel, would make the same capture-pair
method work immediately — the tooling for it is already written and proven.
