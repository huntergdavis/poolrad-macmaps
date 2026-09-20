# World

**World**, beside **Map**, joins areas from your recorded travel into a map
you can browse. It replaces Connections in 0.113.0. Only walked squares are
drawn, even when fog is off on the Map tab.

## Controls

- Drag to move the map. Pinch or use **−/+** to zoom.
- **Whole world** or **Fit** shows everything.
- **Current area** frames your party's area when it is known.
- Tap a place name along the top to frame that city or separate area.
- Tap an area to see its name, walked-square count and recorded crossings
  below the map. Double-tap to frame it.
- The current area has a black border and **You are here** beside its name.
  A marker shows your party's square and facing when its position is verified.

These controls only browse the map. They do not move your party.

## How the map grows

- Crossings between opposite map edges join districts side by side. Their
  crossing squares line up, as at the gate between New Phlan and the Slums.
- Other crossings appear as dotted links between separate areas. The dot
  marks the departure square; the arrow marks the arrival.
- If joining an area would overlap another area or contradict an earlier
  placement, the crossing appears as a link instead.
- Places with no recorded route to your party's part of the world appear
  separately. World does not invent routes across the wilderness.

World includes the current area and areas named in recorded crossings.
It does not list every old notebook map. Revisit missing areas and travel
between them to record their connections. The drawings use the notebook's
remembered maps and walked squares; unavailable map data leaves an area blank.

History stays with each campaign notebook. Existing crossings, backups and
Map exit previews still work. A saved selection of the old Connections tab
opens World. [Connection recording and verification](AREA_CONNECTIONS.md).

## Implementation

`WorldView` draws the map and controls. `mapper/WorldLayout.java` places
areas using recorded crossing squares. `WorldLayoutTest` covers joining
edges, return trips, separate areas and conflicting placements.
The notebook controller supplies remembered maps and live exploration updates.

## Verification — 2026-09-20

- 748 Java unit tests pass, twelve of them new `WorldLayoutTest` cases.
- `tools/render-check.sh WorldViewCheck` on the sandbox emulator: 6 of 6
  actual View checks pass (empty state, gate stitching with a linked island,
  chip and Fit focus targets, zoom limits and drag panning, accessible
  selection with footer crossings, phone-width layout).
- `tools/render-check.sh CompanionPaneCheck`: 10 of 10, including the World
  tab retained beside Map and Info and the legacy "connections" selection.
- Live on the sandbox emulator (Notebook 2, which recorded the 0.108.0 gate
  crossing): the World tab drew New Phlan and the Slums stitched at the gate
  row with the walked path continuing across the seam; tapping the Slums put
  "Slums of Phlan · 9 of 256 squares walked / Crossings: New Phlan 0,4 → Slums
  of Phlan 15,4" in the footer; the city chip took focus; + and a drag zoomed
  and panned the board.
- Not verified live: the party marker and *Current area* chip with a running
  game position (the sandbox's game was at its main menu, not in an area), and
  islands or separate bands with real crossings (no stairs or boat crossing is
  recorded in any notebook yet; both are covered by unit tests and the render
  check). `tools/check-connections.sh` (instrumentation) was updated to assert
  through the World board but not run: it needs a disposable app with no disks
  and no notebooks.

*Development/test-build captures from the 0.113.0 checks above; these are not
verified captures of the published APK.*

![World tab with the Slums selected beside New Phlan](images/world-tab.png)

![The same city zoomed in and dragged](images/world-tab-zoomed.png)
