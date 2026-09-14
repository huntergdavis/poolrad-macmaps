# Handwritten map notes

The cartographer adds your own ink without changing the original game or its saves.

<img src="images/temple-note-map.png" width="360" alt="The preserved TYR note beside its map, with a temple symbol and Rolf's introduction below">

*0.5.0 Android-emulator capture, not a mockup or physical e-ink test.
Original game artwork belongs to its respective owners.*

1. Load a supported party and show the live map.
2. Tap an empty tile to place a symbol; tap an existing symbol to reopen it.
3. Its page contains a map at left and blank writing space at right.
4. Draw with a pen or finger. **Pen**, **Eraser**, **Undo**, and **Redo** affect only
   this sheet. There is no handwriting recognition, text field, or network request.
5. **Close & save** returns to the map. Completed strokes also autosave in the
   background. A failed save leaves the sheet open with a retry message.
6. Use the symbol button for a flag, smithy, temple, inn, shop, monster, hidden
   wall, district or treasure. **Delete…** confirms deletion of symbol and page.

There is no annotation toggle or separate map-drawing action. Each flag owns
its own [map-plus-writing page](MAP_INK.md), not a shared area-wide overlay.

The note sheet stays above the screen midpoint and does not dim the game. Its
fixed-aspect paper keeps handwriting in proportion when the window changes size.
An interrupted, unfinished stroke is cancelled rather than joined to the next one.
Closing a note commits finished strokes, not an interrupted gesture.

**Pen-friendly controls:** optional remembered **Pen only**, two-finger zoom/pan,
one-finger movement in pen-only mode, and **Fit page**. A nearby finger tap can
open a large flag-choice list without accidentally creating another note.
[Controls, input behavior and the remaining tablet check](PEN_NOTES.md).

## Separate campaigns

**PoolRad → Notebooks** switches notebooks or creates a separate empty one.
Notebook names are currently automatic: Notebook 1, Notebook 2, and so on.
The active notebook is visible on the map and saved locally. **Switching a Mac
save does not automatically switch notebooks**: choose the matching notebook
yourself. Creating or selecting one never edits a guest disk or another notebook.
A missing remembered notebook is an error, not permission to open another run.

## Area identity

Flags use `(notebook UUID, verified area identity, tile x, tile y)`, never a heap
address or the party's changing position. An open sheet keeps the area and tile
that you tapped, even if the game moves while it is open.

The supported Macintosh v1.1 profile matches exact full-geometry fingerprints
against 29 independently decoded records. Unknown or changed geometry disables
map-note access and clears the displayed flags; saved notes remain on disk.
There is no approximate match, shared “unknown” notebook area, or last-known-area
fallback. Thus an unrecognized map mutation may temporarily hide notes rather
than attach them to the wrong place. See [identity evidence](AREA_IDENTITY.md).

## Storage and limits

Notes are versioned vector strokes in the app's private `files/notebooks/`
directory. Each file includes its notebook/area/tile identity and checksum.
Saves use a synced temporary file and same-directory atomic replacement; damaged
or unrecognized data is reported instead of silently overwritten. IO is ordered
off the UI thread. Each note permits 2,048 strokes and 131,072 coordinate pairs;
exceeding a limit rejects the unfinished stroke with visible feedback.

**Updates retain notes; uninstalling or clearing app data removes them.** Notebook
export/import is still the separate N4 backlog item. Do not treat this prototype
as your only copy of irreplaceable campaign notes. No Android cloud backup is used.

Version 2 adds symbols and a wide composite page; version-1 handwriting moves
into its right half without distortion, with an original-byte backup before
the first rewrite. N3's pen controls are implemented; physical stylus/palm/e-ink
acceptance and notebook backup/import (N4) remain unfinished. Emulator checks
are not tablet acceptance.

## Local acceptance — 0.4.0

On the Android API 30 test emulator, a ten-stroke **TYR** note was drawn through
touch input at New Phlan tile 11,2 while the original game remained at 15,1 W.
Close/reopen preserved the note. Eraser, undo and redo produced the expected
images and byte-identical saved-vector round trips. Resizing 1200×1600 to
1600×1200 and back preserved the paper's proportions and upper-half controls.

Creating Notebook 2 hid Notebook 1's flag; tapping the empty tile in Browse did
not create a note. Switching back restored the flag. A separate disposable flag
survived cancellation of Delete, then was removed with its ink only after the
explicit linked-note confirmation. The TYR note remained unchanged.

Temporarily removing write permission from that test area's note directory
caused autosave and Close to fail visibly without dismissing the sheet or
changing the previous disk record. Restoring permission, undoing the test stroke,
and retrying Close succeeded. This checks a real write failure, not disk-full,
power-loss, or physical-tablet behavior.

The original game's tutorial was completed, then ordinary movement crossed
New Phlan's west gate into the Slums (record 20) and returned. Flags cleared for
the other area, then the TYR flag reappeared at tile 11,2 and reopened with
byte-identical ink. [Area-route details](AREA_IDENTITY.md).

Android Back saved and closed the sheet. The app was then force-stopped and
relaunched without clearing data. After a full Mac boot, normal code-wheel
entry (25/33/dots → VULCAN), and sample-party reload, Notebook 1's flag returned
in Browse mode. Opening tile 11,2 showed the same TYR handwriting, with
byte-identical vectors. No crash-buffer entries were reported. Private evidence:
`scratch/n1-final-reopened.png`; no game RAM or notebook records are published.
