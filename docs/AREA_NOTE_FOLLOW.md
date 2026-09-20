# Notebook pages open on request

From 0.104.0, entering an area never opens or switches a notebook page.
Open a page yourself by tapping a map tile or selecting a saved note in the
index. Closing a page leaves it closed; there is no deferred destination page.
An already-open note remains attached to its original area and square.
Existing handwriting and notebook backups are unchanged.

This fixes the automatic-opening behavior introduced in 0.87.0. The production
controller no longer schedules note reads or editor creation from area changes,
exploration samples, flag-load completion, or editor dismissal. The old
last-page preferences are left on disk but are no longer read or written.

## 0.104.0 verification

- The candidate universal APK built successfully; all 727 Java tests passed.
- On disposable emulator-5590, a real SampleParty walked New Phlan → Slums,
  returned to New Phlan, then crossed the Slums to Kuto’s Well and returned.
  Screenshots were inspected at the crossings: no notebook appeared. Kuto’s
  Well displayed the existing reference-only/unavailable-position view; no
  claim is made here about improving that separate map-reading behavior.
- The isolated Android controller/editor regression passed on emulator-5592:
  initial, new and returning areas stay closed; no arrival flags are created;
  manually opened reading and ink pages stay pinned; closing has no deferred
  reopen; saved ink reopens manually; physical Back saves and closes.
  These explicitly synthetic samples test the real Android UI but are separate
  from the live walking evidence above.
- Private local evidence: `scratch/manual-notes-slums-arrival.png`,
  `scratch/manual-notes-new-phlan-return.png`,
  `scratch/manual-notes-kuto-stable.png`, `scratch/manual-notes-walk*.log`, and
  `scratch/area-note-follow-check.2w54X2/screenshots/f57-check/`.

The earlier area-follow investigation was recalled from Deja session
`1d01c279-196` before removing the automatic-opening path.

## Historical 0.87.0 behavior and evidence (superseded)

An identified area opens its last-used tile note. If the campaign has never
opened a note there, or that note was deleted, the entry square supplies the
page. The page is editable immediately. Last-used squares are local app
preferences keyed by notebook UUID and area ID; they survive process restart
but are not added to notebook export archives.

Reading follows the party. The first edit (including erase, undo/redo, symbol
change or a template choice) pins that editor until Close & save, even after autosave completes.
An active stroke also pins it before the edit callback fires. Closing opens
only the latest destination, preserving ink under the original notebook,
area and square. Same-area steps and unavailable/camp frames do not count as
another entry. Changing campaigns resets the entry tracker.
Automatically opened editors accept pen/touch input within the page while
leaving the game's mouse and keyboard available. Android Back saves and closes
the automatic editor through a dialog-lifecycle Activity callback and the existing physical-key dispatch.

`AreaNoteFollow` records verified entries and their arrival squares.
`NotebookController` waits for the existing asynchronous flag load, reads the
note on its ordered I/O queue, and rechecks the area generation and editing
state on the UI thread before showing it. Obsolete requests cannot create
stray arrival flags. Manual editor and index opens remember the selected square.

## Verification

- 693 Java tests pass on the integrated 0.87.0 branch, including five entry-state regressions.
- `tools/check-area-note-follow.sh emulator-5590` passed on isolated Android
  API 30, 1200×1600. The real controller, editor, autosave and on-screen touch
  input were used with explicitly synthetic map samples. Screenshots were
  inspected for arrival/remembered pages, protected ink, deferred latest-area
  navigation and ink reopened on its original page. A held stroke survived
  two area changes; completed ink stayed pinned until Close & save. Returning
  to the already-open area did not reopen a closed page. Game-input window
  flags and an injected physical Back key also passed.
- Controlled evidence is in ignored
  `scratch/area-note-follow-check.Ri88mc/screenshots/f57-check/` and
  `scratch/f57-ui-integrated.log`. This harness requires a fresh disposable
  app with a selected ROM and no game disks or existing notebook collection;
  instrumentation restarts the app process. It is not a live-game test.
- The live gate exercise used the same notebook implementation before the
  unrelated Saves changes were integrated; the controlled emulator suite and
  693 Java tests were rerun on the merged 0.87.0 build.
- A real Pool of Radiance party walked from New Phlan through the gate at
  0,4 west into the Slums, then returned through 15,4 east. The Slums opened
  15,4 automatically (the remembered note had been removed); New Phlan
  reopened 7,8 with its original ink. Walking and turning worked while the
  automatic page stayed open. The initial New Phlan page also restored its
  remembered square and ink after app restart. This used a copied disk and
  matching validated full-machine snapshot in the disposable emulator.
  Screenshots: ignored scratch/f57-live-party.png,
  scratch/f57-live-slums.png and scratch/f57-live-return-observed.png.
  The OCR walking helper reported the boundary steps as blocked because the
  game's position text updates later; the new area geometry, game messages
  and notebook destination were observed directly.
- Universal APK builds and architecture/content/signature checks pass.
  `tools/verify.sh --fast` reports two optional Python skips (`machfs`,
  `macresources`). Its generic C invocation lacks the snapshot suites' model
  include paths; both pass separately via `tools/test-snapshot-native.sh`
  under AddressSanitizer/UndefinedBehaviorSanitizer. README screenshot
  freshness passes for 0.87.0.

The optional older CompanionLifecycleCheck has 13 stale fixture compilation
errors on both unchanged main (05a9e37) and this candidate; it was not counted
as a pass or changed as part of the notebook work. Comparison logs are
`scratch/f57-lifecycle-baseline.log` and `scratch/f57-lifecycle.log`.

No physical stylus, e-ink timing or full-game area coverage is claimed.
Prior gate-route guidance came from local recall session `1d01c279-196`
(`deja "Slums"`) and the matching `LOCAL_TESTING.md` instructions.
