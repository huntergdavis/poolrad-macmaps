# A map that knows when it is not live

The companion now separates the game's mode from its last local map. The
existing header carries a dark, labeled mode badge; no new panel or controls
cover the original game.

| Header | What it means |
| --- | --- |
| Coordinates and facing | A verified local position, including tutorial stops, fully committed guided steps and story text. The arrow and map notes remain available; footprints additionally require a safe movement observation. |
| Updating | An unverified local action or partly committed coordinate update is underway. Transient coordinates are hidden and not recorded; this does not hide verified tutorial positions. |
| Combat | Use the original tactical display below. The retained local map is not a combat map. |
| Camp | Walking and map-note creation are paused while the party is in camp. |
| Wilderness | Outdoor travel has its own map system, not the 16×16 local map. No outdoor coordinates are guessed. |
| Loading / setup | The game's verified party-setup or loaded-game continuation flag is set. This is not a disk-progress meter. |
| Position unavailable | No supported state can be verified. Title screens, code-wheel screens, unknown modes and missing samples are not guessed. |

## The search marker

While the game's own position line reads `" search"`, the companion's header
appends one letter: `0, 4 W S`. It is the same bit the game tests — bit 0 of the
16-bit field at `*(A5-0x5eae) + 0x594`, which CODE3 `+0x2c52` uses to decide
whether to print `" search"` — so the marker turns on and off exactly when the
game's own line does. Nothing else about that record is interpreted.

The marker never appears without a verified position: a status-only observation
carries no coordinates, so it carries no marker either, and a search record the
probe could not read is reported as unavailable rather than as "not searching".
Older packets have no search byte at all and never show the marker.

When the arrow is hidden, **reference** appears beside the retained area's
name and **Reference only** replaces the normal tile-tap prompt. The explanation
fits the existing footer. With no previous map, the pane shows the mode's
explanation instead of invented geometry. All modes have text, not just color,
and accessibility descriptions never call old coordinates the current position.

Reference-map taps do not reach the Mac or create notes. Existing handwriting
and exploration are preserved, and normal tile/flag access returns with a
verified local position. A press started before a mode change is canceled.
Party health remains independently sampled; knowing the party's HP does not
make an old map position current.

Combat, camp, wilderness, loading and unavailable observations break the
footprint segment. A settled return starts afresh rather than connecting through
unseen combat/load/outdoor movement. Ordinary short local processing still uses
the existing continuity-epoch and time-window checks without recording a tile or
refreshing the deadline. The narrowly verified tutorial loop preserves continuity
through its partial coordinate assignments, then exposes only the committed
position. General relocations remain breaks. See [Exploration](EXPLORATION.md).

This is a read-only enhancement for the supported Macintosh v1.1 game. It does
not implement a tactical or wilderness mapper, change game rules, restore HP,
or move the party. Named states come from verified Macintosh code and bounded
memory fields, not DOS offsets or image guesses. [Native proof](MAP_MEMORY.md).

Physical e-ink refresh, pen behavior and vendor-specific window handling remain
untested. [Acceptance evidence and its limits](LOCAL_TESTING.md).
