# Every flag is a cartographer's page

Tap an empty tile on the live map to create a note. Tap a symbol to reopen it.
There is no Annotate checkbox and no separate Draw on map menu.

Actual emulator acceptance covers legacy migration, independent flag pages,
symbol persistence, cross-sheet ink/erasing, undo/redo and confirmed deletion.
[Evidence and hardware limits](LOCAL_TESTING.md#050-integrated-acceptance-2026-09-13).

```text
  [Pen] [Eraser] [Undo] [Redo] [Symbol] [Delete]
  +----------------------+----------------------+
  |                      |                      |
  |  MAP OF THIS AREA    |  WHITE WRITING SPACE |
  |  Draw routes, marks |  Names, clues, plans  |
  |  and reminders here |  and handwriting     |
  |                      |                      |
  +----------------------+----------------------+
               One flag, one saved page
```

The left-hand map fits at 90% of its half. Ink can cross from the map into the
writing space, with one undo/redo history. Every flag owns a different page;
these drawings are not shared area-wide marks on the normal live map.

The map is a **pinned snapshot**, not a second live game view. Its verified area
geometry and protected symbols stay underneath/above the ink respectively.
Eraser removes only handwriting: it cannot erase walls, doors, symbols or the
party arrow. The original game keeps its own rules and saves.

Choose **Flag, Smithy, Temple, Inn, Shop, Monster, Hidden wall, District or
Treasure** using the symbol button. These are your labels, not automatically
discovered secrets. The chosen symbol appears on the live map after closing.

Completed strokes and symbol changes autosave. Close & save, Android Back and
Escape wait for the save before closing. A failed save leaves the page visible
for retry. Deleting a symbol explicitly deletes its linked page as well.

## Storage and updates

Pages use fixed 8:3 proportions and normalized vector coordinates. The map's
position is constant within that page, so resizing does not shift ink to another
tile. A cancelled, resized, extra-pointer or interrupted stroke is not committed;
no page touch is forwarded to the Mac.

The notebook UUID, verified area ID and tile identify a page. `PRNI` version 3
adds a stable writing-half template ID after the symbol ID introduced in version 2.
Version-1 and version-2 notes open on plain paper without changing their ink. Existing version-1 handwriting moves proportionately
into the right-hand writing half, with its stroke widths, order and erasers
preserved. Reading does not change the original file; the first successful save
retains a byte-exact `.ink.v1` backup before atomic replacement. Unknown versions,
corruption and backup conflicts are reported rather than silently overwritten.

This reuses the [N1 notebook store and history](NOTEBOOK.md) and the live map's
geometry renderer. It does not add another map decoder or any network service.
Local session recall was attempted before implementation but timed out without
usable material; no recalled implementation was reused.

**Update in place. Uninstall/clear-data removes notebooks.** Complete notebook
export/import remains N4; keep another copy of irreplaceable information.

## Focused tests

The pure `InkSheetLayout` tests check proportions, map placement and coordinate
round trips. `tools/CompositeSheetRenderCheck.java` runs six checks on the actual
Android View using synthetic geometry: left map/right blank, cross-sheet ink and
protected erasing, symbol changes, nine distinct monochrome glyphs, cancellation
and resize anchoring. The storage suite covers legacy migration, independent
flags, symbols, corruption and real write/backup failures.

Software-Canvas and emulator results do **not** establish physical e-ink refresh,
stylus/palm rejection or hardware-GPU behavior. Those remain explicitly untested.
