# Follow your own footsteps

The companion remembers the squares your party actually occupies on each
supported local map. Small black stipples distinguish walked tiles; a pair of
feet shows the latest recorded travel direction. The original game and its
saves are never changed.

Open **Info → Exploration trail**:

- **Show only walked squares (fog of war)** hides unvisited wall/door geometry.
  This is occupancy, not line of sight or a rule change inside the game.
- **Show directional footprints** toggles the feet. Both choices are remembered.
- **Recent observed route** lists up to 256 observations, newest first, with
  explicit return directions. This disambiguates crossings and repeated visits
  that cannot all be represented by one small pair of feet on a tile.
- **Clear footprints only…** retains every walked square. **Reset walked map…**
  also clears coverage for the named area in the named notebook. Both require
  confirmation; neither removes flags, handwriting, other areas or game saves.
  The party's current tile is recorded again when tracking resumes.

The full map remains the default. Map walls, doors, manual flags and the party
arrow paint above the footprints. Your manual flags remain visible in fog mode;
they are your annotations, not automatically discovered game events. Flag-note
pages keep their existing full-map drawing background. Turn fog off to return
to the original atlas view without erasing any history.

Door outlines are neutral, not promises of passability or automatic secret-door
labels. Empty edges do not acquire false walls from unused door bits.
[Wall and doorway symbols](MAP_EDGES.md).

## What is and is not recorded

Travel direction comes from a real change in tile coordinates, **not facing**.
Turning in place and blocked movement do not create a directional step. An
initial position, reload, new area, new notebook, unavailable sample, or large
jump begins an independent segment. Adjacent observations may connect only
within the same native continuity epoch and no more than 1.25 seconds apart.
There is no interpolation, row wrapping or pathfinding between missing points.

Normal movement briefly leaves the game's input-wait loop. Verified positions
can still display while story text is printing, without authorizing footprints.
Partly committed coordinates stay hidden. Those processing frames do not record
or refresh the recording deadline; the next safe observation may connect only if
the native epoch is unchanged and the time/tile limits above still pass.
An unavailable packet, unknown protocol or non-local engine breaks continuity.

Repeated stationary anchors are compacted. An anchor has no direction, so it
does not erase an earlier real footprint; the route list explicitly identifies
the gap. Feet describe recorded walking, not a guessed arrival after a reload.

The read-only native gate recognizes the supported Mac v1.1 local movement
input loop, Rolf's Continue-style story menus, the original one-square forward
command, and the verified New Phlan tour loop after both coordinates commit.
This restores the tutorial map hidden by 0.12.0:
verified tour positions reveal walked squares, and observed adjacent guided
steps can leave feet. Other script calls are not assumed to be walking.
Combat, camp, loading and outdoor state cannot authorize new footprints.
The narrow tour exception checks the original loop and route-table fingerprint,
engine, area, script and instruction phase. General scripted relocation still
breaks the trail, even for an adjacent jump; other scripts get no exemption.
A core-tick observer breaks continuity on
verified context changes even between the normal 250 ms Android samples.
This does not promise instruction-by-instruction replay: rapid movement or a
very short event between observations can be missed. Unobserved intermediate
tiles are never filled in; unexplained jumps remain separate segments.
[Native evidence and exact limits](MAP_MEMORY.md).

Recording continues while Info is open or the companion pane is hidden, as
long as the emulator activity is resumed. Backgrounding or destroying it stops
sampling and breaks continuity. The last known geometry may remain visible
without a live arrow when the movement gate is unavailable. Full tactical and
wilderness map views remain separate backlog items.

Coverage begins when this version first observes the party. It cannot recover
the places you visited before installing it. Coverage survives a return to an
older game save because it is your notebook's exploration memory, not a field
inside that save. **Choose a separate notebook for a separate campaign.**

## Persistence and backups

Each notebook/verified-area pair owns one small `exploration.bin` record:
256 coverage bits and at most 256 recent entries. Data is immutable in memory;
version, notebook/area identity, bounds and CRC are checked before loading or
replacement. Writes use the existing ordered notebook queue and atomic
same-directory publication. Corruption or write failure is reported, never
silently replaced with invented progress. No cloud or online service is used.

Complete `.prnb` notebook exports include exploration alongside the exact
handwritten records. Old backups still restore with initially empty coverage.
Backups containing exploration require version 0.12.0 or newer to restore;
older apps reject the unfamiliar entry rather than silently discarding it.
Uninstalling or clearing Android app data deletes local exploration too.
[Notebook backup guide](NOTEBOOK_BACKUPS.md).

The software checks use synthetic data and an isolated Android emulator.
On 2026-09-14 the user confirmed stylus drawing, two-finger zoom/scroll and
the overall e-ink presentation on their tablet. Detailed rotation, keyboard
and vendor-specific behavior remain unreported; this is user feedback, not an
agent-performed physical test. [Acceptance scope](PEN_NOTES.md).
