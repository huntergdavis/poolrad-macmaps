# Pen-friendly flag pages

The original game stays unchanged. These controls affect only your own map notes.

- **A pen or one finger draws.** There is no Pen only checkbox; an older saved
  Pen only preference does not silently disable finger or capacitive-pen ink.
- **Pinch with two fingers** to zoom from the whole sheet up to 4×. Move both
  fingers to pan. **Fit page** returns to the whole map-and-writing sheet.
  Zoom/pan does not change saved strokes, undo/redo, or the pinned map snapshot.
- A stylus reported by Android as an **eraser** erases ink only. The Pen/Eraser
  buttons work on every device; vendor side-button mappings are not guessed.
- A finger tap close to a flag offers large **48dp-high choices** to open it
  or create a flag on the tile actually tapped. Nearby targets have a 24dp
  center radius, clipped to the map. Exact flag-tile taps open directly;
  stylus taps retain precise tile selection. Canceling the chooser creates nothing.
- The title and autosave status share one compact row with the drawing tools,
  **Fit page** and **Close & save**. Touch targets remain at least 48dp high;
  the middle tools scroll horizontally on narrow windows and Close stays
  visible. Separate hint/input rows, the stock dialog footer and Save PNG are
  gone so the drawing sheet receives the freed height. Notes and pickers still
  open without a fade/slide and stay above the original game.

## Input handling

The editor follows Android pointer IDs rather than array positions. A reported
pen takes precedence over tentative finger ink; unrelated finger/palm contacts
do not interrupt that pen's stroke. Recognized pen hover suppresses finger ink.
Canceled gestures and a canceled lifting pointer discard unfinished ink before
autosave. Completed strokes are unchanged. Focus loss, resizing, tool/mode
changes, Fit, and closing also cancel an unfinished stroke.

This follows [Android's stylus and palm-rejection event guidance](https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/advanced-stylus-features).
`FLAG_CANCELED` is reported on Android 13+; older versions still have
`ACTION_CANCEL`. We do not infer palms from pressure or promise vendor palm
classification. **Physical stylus, side buttons, pen latency, palm rejection
quality and e-ink ghosting remain untested.** There is no longer a pen-only UI
fallback; actual pen/palm behavior still needs checking on the intended tablet.

## Earlier pen-engine checks (0.5.3)

- 170 Java tests pass, including ten display-transform checks and eight new
  nearby-target checks. Stored page coordinates remain independent of zoom.
- Thirteen actual `InkSheetView` checks on the Android API 30 emulator exercise
  synthetic stylus/eraser/finger tools, nonzero and reordered pointer IDs,
  unrelated canceled palms, pen takeover, historical samples, cancellation,
  hover, zoom/pan, inverse coordinates, Fit, and unchanged edit notifications.
  Rebuild/run commands are in [InkInputCheck.java](../tools/InkInputCheck.java).
- Six existing composite-sheet software-render checks still pass: protected
  map/markers, ink-only erasing, symbols, cancellation and resize anchoring.
- Seven existing party-pane View checks and all three native reader suites
  pass; map touch routing remains separate from the original game display.
- The in-place 0.5.3 update preserves the existing temple note and its original
  migration backup byte-for-byte. No private note/game files are published.
- In the earlier 0.5.3 layout, an actual adjacent finger tap opened the large
  chooser and existing saved page; its former Pen only preference survived an
  update/relaunch. The compact editor intentionally ignores that old setting.

See [integrated acceptance and resize limits](LOCAL_TESTING.md#053-pen-note-software-acceptance-2026-09-14).
Forced emulator display-size changes rebooted the guest, not a seamless
open-editor resize; real-device rotation/window acceptance remains open.

These are software/emulator results, not physical-tablet acceptance.

The compact editor has a separate [Android View layout probe](../tools/NoteEditorLayoutCheck.java)
for sketch height, fixed Close, narrow scrolling, larger text, preserved strokes
and finger input. Its measurements describe the content View; the installed
dialog's real bounds and save controls must also be checked in the app.

## Remaining N3 tablet check

On the actual tablet, open a disposable flag page and check:

1. Draw with both a finger and pen; rest a palm before, during and after a pen
   stroke. Verify that a real pen is recognized and unwanted ink is absent.
2. Use two fingers to zoom/pan. Fit returns
   the whole sheet; toolbar and map do not move the game or draw accidental ink.
3. If the pen has a reported eraser end, erase, undo and redo. Record which pen
   and Android tool types actually work; side buttons are not assumed supported.
4. Rotate, switch apps and reopen the note. Finished ink persists; interrupted
   strokes do not join together. Confirm finger drawing remains available after relaunch.
5. Check thin black strokes, flag choices, keyboard layout, refresh/ghosting and
   actual pen latency on e-ink. Record tablet model, Android version and findings.

Until those checks are performed, N3's hardware acceptance stays unticked.
Notebook export/import and previously exported page PNGs are covered in the
[backup guide](NOTEBOOK_BACKUPS.md).
