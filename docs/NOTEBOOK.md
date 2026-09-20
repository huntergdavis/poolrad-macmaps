# Handwritten map notes

The cartographer adds your own ink without changing the original game or its saves.

1. Load a supported party and show the live map.
2. Tap an empty tile to place a symbol; tap an existing symbol to reopen it.
3. Its page contains a map at left and writing space at right. Plain paper is
   the default. **Template** optionally adds a blank grid, ruled list or blank
   map frame to the writing half; the area map stays visible.
4. Draw with a pen or finger. **Pen**, **Eraser**, **Undo**, and **Redo** affect only
   this sheet. There is no handwriting recognition, text field, or network request.
5. **Close & save** returns to the map. Completed strokes also autosave in the
   background. A failed save leaves the sheet open with a retry message.
6. Use the symbol button for a flag, smithy, temple, inn, shop, monster, hidden
   wall, district or treasure. **Delete…** confirms deletion of symbol and page.

There is no annotation toggle or separate map-drawing action. Each flag owns
its own [map-plus-writing page](MAP_INK.md), not a shared area-wide overlay.

From 0.104.0, notes open only when you choose a map tile or a saved note in
**Info → Notes index**.

- Entering an area leaves the notebook closed.
- An open page keeps its original area and square.
- **Close & save** returns to the map without opening another page.
- Android Back also saves and closes the page.

[Opening behavior and test results](AREA_NOTE_FOLLOW.md).

The note sheet stays above the screen midpoint and does not dim the game. Its
fixed-aspect paper keeps handwriting in proportion when the window changes size.
An interrupted, unfinished stroke is cancelled rather than joined to the next one.
Closing a note commits finished strokes, not an interrupted gesture.

**Paper templates:** use **Template** in the note toolbar, then choose
**Plain paper**, **Blank grid**, **Ruled list**, or **Blank map frame**.
The choice autosaves with that page. Changing paper preserves handwriting;
Eraser and stroke Undo/Redo leave the guides intact. Templates travel with
notebook backups and appear in note images and PDFs. No chooser interrupts
the normal new-page flow. See [template verification](NOTE_TEMPLATES.md).

**Compact controls:** the title and save status sit beside two fixed rows of
tools, drawing tools above (Pen, Eraser, Undo, Redo, Symbol) and page actions
below (Journal, Template, Fit page, Delete…), with **Close & save** standing
beside both. Nothing scrolls: every control is visible at once at every width
(since 2026-09-20; the single strip had grown a scrollbar). The rest of the
pane is your sketch. A pen or
one finger draws; two fingers zoom/pan. The old Pen only preference is no longer
applied. A nearby finger tap can open a large flag-choice list without
accidentally creating another note.
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
address or the party's changing position. Edited sheets keep their original
area and tile even when the party moves elsewhere; ink always saves there.

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

**Updates retain notes; uninstalling or clearing app data removes them.** Use
**Notebooks → Back up Notebook N…** to save a complete `.prnb` file outside the
app. **Restore backup…** never overwrites an existing campaign. Save PNG has
been removed from the flag editor to keep its controls compact; previously
exported images remain readable. [Backup/restore guide](NOTEBOOK_BACKUPS.md).
Keep a separate copy of irreplaceable notes; no Android cloud backup is used.

Version 3 adds the writing-half template; version 2 added symbols and a wide composite page; version-1 handwriting moves
into its right half without distortion, with an original-byte backup before
the first rewrite. N3's pen controls are implemented; physical stylus/palm/e-ink
acceptance remains unfinished. Emulator checks are not tablet acceptance.

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
