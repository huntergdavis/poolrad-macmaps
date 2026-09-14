# Pen-friendly flag pages

The original game stays unchanged. These controls affect only your own map notes.

- **Pen only** is optional and remembered between pages and app launches.
  A reported stylus writes; a finger moves the zoomed page without leaving ink.
  Leave it off for finger drawing or a capacitive pen reported as a finger.
- **Pinch with two fingers** to zoom from the whole sheet up to 4×. Move both
  fingers to pan. **Fit page** returns to the whole map-and-writing sheet.
  Zoom/pan does not change saved strokes, undo/redo, or the pinned map snapshot.
- A stylus reported by Android as an **eraser** erases ink only. The Pen/Eraser
  buttons work on every device; vendor side-button mappings are not guessed.
- A finger tap close to a flag offers large **48dp-high choices** to open it
  or create a flag on the tile actually tapped. Nearby targets have a 24dp
  center radius, clipped to the map. Exact flag-tile taps open directly;
  stylus taps retain precise tile selection. Canceling the chooser creates nothing.
- Tool buttons stay at least 72×48dp; the tool row scrolls on narrow windows.
  Notes and pickers open without a fade/slide and remain in the upper half.

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
quality and e-ink ghosting remain untested.** Pen-only is the explicit fallback
when a device reports unwanted finger input or no usable hover/cancel signal.

## Focused checks

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
- An actual adjacent finger tap opens the large chooser, then the existing
  saved page; Pen only remains selected across the app update/relaunch.

See [integrated acceptance and resize limits](LOCAL_TESTING.md#053-pen-note-software-acceptance-2026-09-14).
Forced emulator display-size changes rebooted the guest, not a seamless
open-editor resize; real-device rotation/window acceptance remains open.

These are software/emulator results, not physical-tablet acceptance.

## Remaining N3 tablet check

On the actual tablet, open a disposable flag page and check:

1. Draw normally; rest a palm before, during and after a pen stroke. Try Pen only
   both on and off; verify that a real pen is recognized and unwanted ink is absent.
2. Use two fingers to zoom/pan, then Pen only + one finger to pan. Fit returns
   the whole sheet; toolbar and map do not move the game or draw accidental ink.
3. If the pen has a reported eraser end, erase, undo and redo. Record which pen
   and Android tool types actually work; side buttons are not assumed supported.
4. Rotate, switch apps and reopen the note. Finished ink persists; interrupted
   strokes do not join together. Confirm Pen only remains selected after relaunch.
5. Check thin black strokes, flag choices, keyboard layout, refresh/ghosting and
   actual pen latency on e-ink. Record tablet model, Android version and findings.

Until those checks are performed, N3's hardware acceptance stays unticked.
Notebook export/import remains the separate next software item, N4.
