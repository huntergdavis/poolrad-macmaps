# PoolRad Mac Maps — current backlog

Updated 2026-09-14. This is the authoritative feature queue. The earlier
[implementation plan](PLAN.md) retains the architecture and historical proof
steps; this page supersedes its old exclusions of notes and party information.
Research and feature rationale: [FEATURE_RESEARCH.md](FEATURE_RESEARCH.md).

## Where we are

A working personal Android companion inside Mini vMac, not a game rewrite.
On 2026-09-14 the user explicitly verified stylus drawing and two-finger
zoom/scroll on the physical e-ink tablet and approved the experience. This is
user-performed acceptance, not an agent hardware test. Unreported rotation,
keyboard and vendor-specific pen behavior remain separate checks under Q1.

| Status | Delivered |
| --- | --- |
| Done | Separate-install Android fork, private ROM/disk imports, existing disk automount |
| Done | Original Mac game boots; sample party loads; originals remain untouched |
| Done | Monochrome map, walls, door outlines, coordinates, facing arrow |
| Done | Read-only small RAM samples, change-only redraw, startup/unavailable guards |
| Done | Map above game; optional keyboard below; map visibility preference |
| Done | First-area turns, steps, blocked movement, boot/load, and background/resume checks |
| Done | Offline code wheel, rune/path selection, answer plus Return |
| Done | All 72 rune pictures checked into the source and bundled in APK; no artwork download/cache |
| Done — 0.2.1 | Lookup and every picker constrained to upper half; no game dimming |
| Done | 29 GEO records decoded; 355 Java tests plus three existing native reader suites |
| Done — 0.3.0 | Offline levels/skills, spells, and exact mixed-coin reference panels |
| Done — 0.3.0 | Map/game/keyboard PNG capture with Android Save and Share |
| Done — 0.4.0 | Flag-linked handwritten notes, ink tools/autosave, and separate campaign notebooks |
| Done — 0.4.0 | Exact known-area identities; New Phlan/Slums round trip and restart/reopen note acceptance |
| Done — 0.5.0 | Always-on flags, per-flag map/writing pages, nine symbols and lossless old-note migration |
| Done — 0.5.0 | Browse-only spells and 59-entry offline equipment browser/comparison |
| Done — 0.5.1 | Automatic code wheel; distinct answers and same-process game quit/relaunch verified |
| Done — 0.5.2 | Current/max party health through combat; real damage, healing, reorder and reload verified |
| Done — 0.5.3 | Pen-only input, finger zoom/pan and larger flag targets; physical pen acceptance remains open |
| Done — 0.6.0 | Complete notebook backups/restore, readable PNG pages and confirmed notebook removal |
| Done — 0.7.0 | Opt-in personal APK, verified first-use import, save-preserving updates; public APK stays BYO-files |
| Done | Explicit pinned-source private builds, verified offline cache, no checkout downloads or public private-asset uploads |
| Withdrawn — 0.20.0 | Mac desktop appearance (shipped 0.8.0): removed, it could only work with the guest shut down |
| Done — 0.9.0 | Map/Info tabs, five offline tools, smaller menu and reference/picker windows fitted above the guest |
| Done — 0.10.0 | Fresh-save party fix, map-left HP/AC/class sidebar, tap-for-details and small-window collapse |
| Done — 0.11.0 | 29 named areas, live GEO ID plus immutable-prefix validation, door-stable note keys and bounded map headers |
| Done — 0.11.0 | Gate round trip, ordinary save/cold reload, preserved handwriting and matching party sidebar across an in-place update |
| Done — 0.12.0 | Walked tiles, optional visited-only fog, directional feet and recent return directions per notebook/area; backed up with notes |
| Done — 0.13.0 | Guided tutorial positions/footprints restored; explicit camp/combat/loading/wilderness reference states |
| Done — 0.13.0 | Compact flag-editor title/tool row and 2.1× sketch height; Pen only and per-note Save PNG removed |
| Done — 0.13.1 | Source-checked wall-first symbols remove phantom doors; two doorway appearances and blocked walls spot-checked in the original game |
| Done — 0.14.0 | Offline illustrated journal/proclamation/tavern lookup, private book import, recent numbers and per-notebook bookmarks |
| Done — 0.15.0 | Verified party condition badges, poison/helpless effects and condition text on tap |
| Done — 0.16.0 | Journal history inside the notebook and its backup; player-made flag links and checked tasks |
| Done — 0.17.0 | Verified disk checkpoints with automatic undo copy, taken only while the Mac is shut down |
| Done — 0.18.0 | Per-level memorized-spell readiness, awaiting-rest counts and a rest reminder in character details |
| Done — 0.19.0 | Readied weapon/armor names, movement and carried weight; one-line map caption; two-column party for NPC-sized parties |
| Withdrawn — 0.20.0 | Disk checkpoints (shipped 0.17.0): removed, same shutdown requirement |
| Done — 0.21.0 | Party sidebar kept on a 1440-wide high-density panel |
| Done — 0.22.0 | Experience and the game's own next-level figures in character details |
| Done — 0.20.0 | Quiet map header, no helper-state captions, and both shutdown-gated tools gone |
| User-verified — 2026-09-14 | Physical e-ink pen workflow: stylus drawing and two-finger zoom/scroll work well |
| User-verified — 2026-09-14 | Hunter reports all device testing done on e-ink; he considers the e-ink pass complete |
| Done | Private single boot disk, automatic game launch, sample-party load and desktop recovery |
| Done | Sideload/update build and documented SMB transfer route |

Not done: dedicated tactical/wilderness maps, training, automatic journal
detection, a dedicated Notes index, ammunition, or the remaining
rotation/keyboard hardware checks. Party conditions ship in 0.15.0 and
journal/map-note linking in 0.16.0; equipment and the other character panels
do not. Separate area-wide drawing
was replaced by the shipped flag pages.
Do not confuse a working first-area map and one validated gate round trip
with full-game tracking coverage. No numerical completion percentage is useful
while scope is expanding.

## NOW — user must-fix simplification (P0, 2026-09-13)

These requests supersede the separate area-wide drawing editor and the old
optional-only code-wheel recognition policy below. Finish them before expanding
the lower-priority queue.

- [x] **F13 — Reclaim the caption line and fit an NPC-sized party (P0, user
  request 2026-09-14).** Drop the standing "North up · N walked · Info: trail
  options" caption and give the height back to the map. A party can reach eight
  with NPCs; the sidebar must not vanish when it does.
  **Delivered 0.19.0:** one caption line instead of two, carrying whichever of
  the mode explanation, exploration status or tap hint actually says something,
  with the notebook name always along for the ride. `MapViewport` reserves
  22dp instead of 38dp, so the map cells genuinely grow. The party sidebar falls
  back to two columns when one will not fit, taking the width from the map as
  the user approved, so rows stay exactly as tall and as wide as a short
  party's. Previously 7 or 8 members silently collapsed the whole sidebar at the
  tablet's real pane height. Members fill the first column before the second,
  the empty cell of an odd party is not a tap target, and one column is still
  preferred whenever it fits. Verified live at six members and by an
  eight-member Android View check at the real density.

- [x] **F10 — Walked tiles and directional footprints (new P0).** Remember
  the squares actually occupied by this campaign's party on each verified area
  map. Offer a visited-only / fog-of-war view and small directional footprints
  so a player can follow their route back. Derive travel direction from observed
  moves, not the direction the party is facing; turning in place is not a step.
  Keep a bounded recent trail alongside permanent tile coverage. Do not invent
  connecting steps after a load, map change, missing sample, jump or combat.
  Save locally with the active notebook, retain across restarts and updates,
  include in notebook backup/restore, and allow explicit trail clearing without
  deleting handwritten flags. Keep walls, flags and the current arrow legible.
  Establish a reliable exploration-only sampling gate before recording movement;
  do not mistake stale exploration coordinates for a tactical/world-map route.
  This promotes R6's visited tiles/breadcrumbs ahead of the remaining P1 queue.
  **Delivered 0.12.0:** actual eastward travel supplies a westward return
  direction; fog reveals three walked Phlan squares, feet remain behind the
  party, and the Slums has independent coverage. Returning retains Phlan's
  coverage and old flag. Recording works with Info selected. Explicit reset
  and footprints-only clear preserve handwriting; a real Android backup
  restores both areas and exact ink through the production store. The corrected
  APK preserves earlier coverage through in-place update/cold reload. 325 Java
  tests, 32 Android View/Canvas checks and existing native/helper checks pass.
  The read-only settled-exploration gate rejects combat/camp/loading state;
  missing/slow samples deliberately break the route, not guess omitted steps.
  This is sampled history, not complete instruction-level replay. Physical
  e-ink/stylus and full tactical/wilderness presentation remain open.
  [Guide](EXPLORATION.md) · [evidence](LOCAL_TESTING.md).

- [x] **F11 — Restore the guided tutorial map and footprints (P0 regression).**
  Rolf's scripted tour must show the party's real local position, including
  stops and movement before normal player control begins. Record observed
  visited squares and genuine adjacent guided steps; do not turn a script jump,
  load or combat position into an invented connecting route. The 0.12.0
  settled-input guard hides the tour and needs a verified guided-movement path.
  **Delivered 0.13.0:** actual unsuspended SampleParty tour from 15,1 W to
  11,2 S reveals the intermediate x14,13,12,11 tiles and directional feet
  before normal control begins. The map matches the guest at the moving
  11,1 S frame and Temple stop; six previous visits become ten without
  erasing the old flag. Verified story-text positions remain visible even
  when footprint recording pauses. Only the original bounded tour profile
  gets the committed-coordinate exception; loads, combat and general script
  jumps still break the route. [Evidence](LOCAL_TESTING.md).
- [x] **F12 — Give flag notes their drawing space back (P0).** Remove the
  Pen only checkbox and per-note Save PNG action. Put smaller drawing controls
  and Fit page alongside the title where space allows, with a compact narrow
  layout. Reclaim roughly twice the current sketch height while retaining
  map-plus-whitespace ink, symbols, eraser, undo/redo and autosave above the game.
  **Delivered for 0.13.0:** the actual Android flag window has a 450-pixel
  sketch height instead of 213 at 1200×1600. One title/tool row, fixed Close
  & save, no Pen only or Save PNG; drawing, autosave, exact undo/redo, Fit and
  close/reopen pass with the old ink preserved. Five Android layout/input
  checks cover narrow screens and larger text. Physical pen/e-ink is untested.

- [x] **F1 — Browse spells, no search.** Remove spell text search and its keypad.
  Keep the small class/level lists. Future equipment references should browse
  their finite lists too, without a search UI.
  **Delivered 0.5.0:** emulator list shows all 55 entries; class/level controls
  narrow to eight Cleric 1 entries, with no name field or search keyboard.
- [x] **F2 — Flags are always available.** Remove the Annotate map checkbox.
  Tap an empty map tile to add a flag; tap an existing flag to reopen it.
  Keep deliberate linked-note deletion and never forward map touches to the Mac.
  **Delivered 0.5.0:** created, saved, reopened and deliberately deleted a new
  test flag without a mode toggle; the existing note and guest position stayed intact.
- [x] **F3 — Each flag gets a map + writing sheet.** Map on the left, fitted at
  roughly 90%; white handwriting space on the right. Draw across either part
  with one pen/eraser/undo history, separate for each flag. Protect the map and
  markers from erasing. Preserve existing notes, with stable coordinates through
  resize. This replaces N2's separate editor/menu workflow.
  **Delivered 0.5.0:** actual TYR note reopens in the right half; cross-sheet
  ink/eraser and byte-identical undo/redo pass. Original `.v1` backup matches
  the old note exactly. Six Android View checks cover rendering and resize.
- [x] **F4 — Automatic code wheel from verified memory.** Detect the actual
  prompt and its runes/path using small read-only Mac memory samples, solve
  locally, then enter the correct answer and Return without user intervention.
  Validate distinct prompts and exits/reloads; do not submit a guessed or stale
  answer. Keep the manual offline helper available when recognition is unknown.
  **Delivered 0.5.1:** original game accepted TEMPLE (index 10), then BEWARE
  (index 0) after normal Quit → Finder → Open in the same Android process/core.
  Both first attempts used six read-back-confirmed letters and one automatic
  Return, with no outside answer input. Tests cover all 13 answers, repeated
  addresses after exit, and stale-sample suppression. Other game versions and
  physical tablet timing remain untested; [evidence](WHEEL_MEMORY.md).
- [x] **F5 — Remove Capture RAM from the everyday menu.** Diagnostics belong in
  developer tooling, not the normal PoolRad actions.
  **Delivered 0.5.0:** verified the actual eight-action PoolRad menu without it.
- [x] **F6 — Choose a symbol for each flag.** Manually select flag, smithy,
  temple, inn, shop, monster, hidden wall, district/slums or treasure. Distinct
  monochrome icons appear on the live map and on that flag's note map. Persist
  the selection with the note; never infer undiscovered places from game scripts.
  **Delivered 0.5.0:** Temple and Smithy choices persist on separate live-map
  flags and reopened note pages; all nine glyphs pass distinct-render checks.
- [x] **F7 — Current/max party health at a glance.** A compact upper-pane party
  strip with character names (icons where useful), current/max HP and dark health
  bars. Verify the actual Mac party records and maximum HP first; no guessed
  totals or simulated mana. Validate reorder and damaged/healed values; collapse
  sensibly on narrow windows and leave the Mac display playable below.
  **Delivered 0.5.2:** fixed enemies sharing the Mac combat list so the strip
  continues to show only party members. Actual damage, zero HP, Zarram moved
  last-to-first, and injured-save reload agree with the game. Normal Cure Light
  Wounds raises Arax from 7/12 to 12/12, with the bar filling correctly. Seven
  Android View checks cover narrow/tiny layouts, touch routing and clearing.
  [Evidence](PARTY.md). Physical pen/e-ink and broader mode coverage remain open.
- [x] **F8 — Weapons & armor reference browser (REF3 promoted).** Original-game
  equipment lists with damage, protection, cost, weight and restrictions; compact
  categories, offline, upper-half only, and no search field. Verify the supplied
  Mac tables and original manuals, with disagreements visible rather than using
  modern D&D values. Track completion here and cross-reference REF3 below.
  **Delivered 0.5.0:** 59 offline entries; actual weapon/armor browsing and
  banded-mail/chain-mail detail comparison verified in the emulator. Nine tests
  pass; 58 Mac table rows independently checked. [Provenance](EQUIPMENT_REFERENCE.md).
- [x] **F9 — One boot disk, automatic game launch (P0).** Build one bootable HFS
  image containing the user's Mac system and Pool of Radiance, preserving all
  data/resource forks and the blessed System Folder. Configure a real guest
  startup item/alias, not timed clicks. Work only on new copies; retain originals,
  imported saves and a bypass/recovery path. Verify cold boot reaches the game
  from this single mounted disk. This promotes the combined-disk/auto-launch
  subset of B1/A1; public asset redistribution remains separately gated.
  **Delivered:** a catalog-preserving 32 MiB private disk cold-boots from one
  `disk1.dsk`, launches the original game automatically, accepts automatic wheel
  entry and loads the sample party with its live map/health. The separate
  `--no-startup` disk boots to Finder. Thirteen helper tests pass; original
  source hashes and all game/save forks are preserved. [Guide](PERSONAL_BOOT.md).

## Handwritten cartographer (P0, after the must-fixes)

Ship these in order, beginning immediately after the rune-wheel changes.
No OCR, handwriting-to-text service, or cloud dependency.

- [x] **N1 — Flag → handwritten note → reopen.** Explicit annotate mode; tap a
  tile to place a small monochrome flag; tap the flag to open an enlarged ink
  sheet. Pen, eraser, undo/redo, close with autosave, and deliberate flag deletion
  with undo/confirmation. A note is handwritten strokes, not a text field.
  Store locally, separate from the Mac save. Resolve stable area identity and
  tile coordinates first; never attach notes using only the current heap address.
  Unknown/ambiguous areas must not silently share notes. Include a notebook/run
  identifier so another campaign need not inherit this one's spoilers.
  **Acceptance:** write, close, reopen, restart app, leave/return to the area;
  note stays on the same tile. Deleting a flag is explicit about its linked note.
  **Delivered 0.4.0:** actual touch-written TYR note at New Phlan 11,2 survives
  close/reopen, New Phlan → Slums → return, and app force-stop/cold guest boot/
  party reload. Eraser/undo/redo, resize, notebook isolation, confirmed deletion,
  Android Back-save and real write-failure/retry checks pass in the emulator.
  [Guide and evidence](NOTEBOOK.md); physical e-ink/stylus remains untested.
- **N2 — Draw directly on the map: replaced by F3 above.** The user chose a
  map-plus-whitespace drawing attached to each flag, not another standalone
  area-wide editor/menu item. The ink-only erasing and stable-coordinate
  requirements carry forward into F3; no separate completion checkbox.
- [x] **N3 — Pen-tablet polish.** Large flag hit targets, dark strokes, palm
  rejection where Android reports usable stylus data,
  finger navigation, cancellation-safe strokes, and no animated note opening.
  Test real hardware rather than promise vendor-specific pen latency/eraser
  behavior from an emulator. Default pencil-style black ink, not color-only UI.
  **Software delivered; user accepted physical pen/e-ink workflow 2026-09-14:**
  the user reports stylus drawing works great, two fingers zoom/scroll, and the
  e-ink experience looks great. This closes everyday pen-workflow acceptance;
  it is not an agent-performed tablet test or a measurement of latency/palm
  rejection. Pen only was intentionally removed by F12, not reintroduced.
  Synthetic input/renderer checks cover cancellation and pen/eraser handling.
  Rotation, keyboard layout, vendor eraser/side buttons and detailed palm
  behavior remain unreported under Q1. Device model/version are requested.
  [Guide and acceptance scope](PEN_NOTES.md).
- [x] **N4 — Protect the notebook.** Export/import the complete local notebook
  (flags + vector strokes + area/run identities), export a readable image,
  atomic saves and failure feedback, and confirmed clearing. App updates retain
  notes; uninstall does not become the only way to discover a missing backup.
  **Delivered 0.6.0:** a real Android file-picker backup, confirmed removal and
  restore retain the notebook UUID, tile, Smithy symbol and exact vector bytes;
  duplicate import refuses overwrite and leaves the active campaign unchanged.
  Fitted 1600×768 PNG output matches its prepared image byte-for-byte. Cancelled
  export/removal and in-place update preserve notes. Checks cover corrupt/
  oversized records, atomic publication/removal and destination failures.
  [Backup guide](NOTEBOOK_BACKUPS.md) · [local evidence](LOCAL_TESTING.md).

## P1 — requested comfort features and remaining correctness

After the immediate P0 fixes, prioritize the ready-to-play package, startup and
wallpaper below, then Journal and the remaining comfort work. Correctness checks
are gates for the affected feature, not a second giant framework project.

- [x] **B1 — Ready-to-play personal package.** An opt-in bundled build imports
  the supplied boot/game images (and a compatible ROM if permitted), verifies
  their hashes, and copies them into private writable storage only on first use.
  Updating the app must never replace existing disks/saves. Keep a recovery/import
  path and a public bring-your-own-files flavor. No private images in Git.
  **Delivered 0.7.0:** the explicit personal build verifies and installs the
  supplied ROM/combined startup disk, then boots and launches the original game.
  Write-failure Retry and remembered manual import pass on a fresh emulator.
  Personal and public in-place APK updates preserve the already-modified disk,
  ROM and receipt byte-for-byte. Public artifact checks reject private payloads
  even with a prior personal build cached. 213 Java tests and 16 synthetic
  builder tests pass. [Guide](PERSONAL_PACKAGE.md) · [evidence and emulator
  interruptions](LOCAL_TESTING.md). Physical tablet acceptance is not claimed.
- [x] **B2 — Optional asset-fetching build pipeline.** Fetch explicitly configured
  images from a pinned repository/source into ignored private build inputs;
  verify checksums and fail clearly when absent or changed. No implicit checkout
  side effects. Treat the resulting bundled APK as containing those same images:
  public distribution requires a redistribution-rights review, not merely an
  archive.org/GitHub URL or an abandonware label. Personal artifacts must not be
  uploaded to public releases by default. [Distribution reference](https://www.copyright.gov/help/faq/faq-digital.html).
  **Delivered 2026-09-14 (build tools, after 0.7.0):** an explicitly configured
  HTTPS manifest feeds the personal Gradle build; full Git commit pins where
  applicable, exact sizes/SHA-256 and the existing ROM verifier gate every bundle.
  Verified caches work offline without repeat downloads. Missing opt-in, mixed
  input modes, absent/corrupt caches and changed payloads fail without fallback
  or overwrite. A real loopback HTTPS → personal APK route passes with synthetic
  assets, and the public APK remains private-data-free even in the same build.
  25 fetch tests, 16 existing builder tests, 213 Java tests and three native suites
  pass. No checkout hook, default remote assets, public upload or runtime changes.
  [Usage](PERSONAL_ASSET_FETCH.md) · [acceptance](LOCAL_TESTING.md).
- **A1 and W1 below are delivered:** reliable guest auto-launch and a
  recoverable custom Mac desktop complete the B1/B2 onboarding group.
  UI1 companion tabs are now delivered too. Continue with remaining party work,
  Journal and the other comfort features;
  none changes the original game rules.

- [x] **UI1 — Companion tabs.** Keep the Mac display and keyboard in place;
  switch only the upper companion pane. Start with working Map and Info tabs
  (Info groups the reference tools); add Journal and Notes when those workflows
  exist. Keep Screenshot/settings in the menu. Preserve map visibility and
  selected-tab state; use actual companion bounds for in-pane tools. No empty
  placeholder tabs. See [the implemented navigation guide](TABS.md).
  **Delivered 0.9.0:** one retained Map and scrolling Info with all five working
  reference tools, four PoolRad menu actions, hidden-preference migration and
  saved-tab state. Actual references and all rune/path pickers fit the companion
  with keyboard open/closed, without game dimming; the final APK also fixes
  picker creation reapplying theme animations. Info background/resume, held
  guest Return, automatic code-wheel entry, sample-party map and selected-tab
  PNG capture pass. 262 Java tests, ten lifecycle checks and eight Android View
  checks pass. Notes remain accessible from flags/Notebooks; a separate index
  and Journal tab await their workflows. Physical e-ink and Q1's native
  forced-recreation behavior remain unverified. [Evidence](LOCAL_TESTING.md).

- [x] **S1 — PoolRad → Screenshot.** Save one PNG of the full app composition:
  map, party sidebar when present, and the real guest display (plus keyboard if
  open). Include drawn marks/flags, dismiss the menu before capture, and offer
  Android save/share. Capture a coherent frame; avoid blank guest surfaces.
  No upload or request for broad screen-recording access just to capture our app.
  **Delivered 0.3.0:** guest/map captures at 15,1 W and 11,2 S, keyboard capture,
  byte-identical Android Save output, cancelled picker and successful subsequent
  capture, and the Android image/png share chooser verified in the emulator.
  No external recipient was selected. System bars/separate dialogs are excluded.
- [x] **P1 — Map left, compact party right.** Use the currently spare horizontal
  space in the upper pane; leave the Mac display underneath. Name, monochrome
  face/class icon, AC, and current/max HP with a small high-contrast bar. Tap a
  row for details instead of always showing a full sheet. Read actual Mac party
  records first; validate damage/healing, party reorder, and joining/leaving
  members. Show unavailable values honestly. Start with original class symbols
  or user-selected pictures; actual guest combat-icon extraction is optional.
  Auto-collapse the sidebar on narrow windows rather than shrink the game.
  **Immediate health subset promoted to F7:** current/max HP and compact names
  are now requested first. AC, portraits and expanded details remain here.
  **Delivered 0.10.0:** verified Mac AC/class fields, original class marks,
  readable snapshot details above the guest, and narrow/short/large-text
  collapse. The original SampleParty now shows all six correct HP/AC rows at
  Rolf's introduction. The fresh-save failure was allocator padding, not slots:
  validate the proven 302-byte logical record instead of a fixed physical size.
  Lara's negative AC/multiclass details, keyboard collapse/restoration and real
  Info → Map return pass. Existing F7 live damage/healing/reorder evidence is
  retained; native/Java/Android View fixtures validate join/leave changes,
  stale selections and unknown fields. 277 Java tests, 11 party View checks,
  eight companion View checks and existing helper/native suites pass. Broader
  live NPC/mode coverage and physical pen/e-ink remain separate; [evidence](PARTY.md)
  and [local acceptance](LOCAL_TESTING.md).
- [x] **W1 — PoolRad → Desktop appearance.** First offer a quiet flat guest
  desktop and Restore original. Then an optional restrained monochrome fantasy
  motif or user-imported image, previewed before applying. This is the actual
  emulated Mac desktop, not merely the Android margins. Inspect the active
  guest desktop utility/settings; make changes only to the personal boot-disk
  copy and keep a recoverable previous setting. A menu control may need a small
  guest-side mechanism; do not fake it by painting over game windows.
  **Delivered 0.8.0:** White, Mist and original monochrome Stonework previews;
  normal guest Shut Down enables Apply. The supported System 7.5.5 disk is
  staged and changed only in its existing desktop-resource appearance bytes.
  An immutable original-setting backup survives app updates. Restore operates
  on the current disk, never an old campaign image. Actual emulator White
  desktop, original-game/sample-party/map loading, all three style operations,
  exact restoration and original gray desktop reboot pass. Full-disk comparisons
  find no nonappearance edits and preserve intervening guest-session changes.
  251 Java tests, 54 Python helper tests and three native suites pass.
  [Guide](DESKTOP_APPEARANCE.md) · [evidence](LOCAL_TESTING.md). Other systems,
  arbitrary imported wallpapers and physical e-ink acceptance are not claimed.
- [x] **A1 — Launch the game on startup.** Existing disk automount is already
  present. Add a guest startup alias or similarly small reliable launch path;
  avoid blind timed clicks. Keep a bypass/recovery path to the normal desktop.
  **Delivered through F9:** standard System 7 Startup Items alias plus a tested
  desktop-only recovery build. Public APKs still require the user's own files.
- [x] **M1 — Reliable area identity and names.** Resolve and validate a stable
  identifier across transitions and reloads; label areas meaningfully. This is
  the N1 persistence prerequisite as well as a map usability improvement.
  **Delivered 0.11.0:** verified native GEO metadata cross-checked against all
  29 unique wall/event prefixes; the proven mutable door plane no longer changes
  notebook identity. Existing keys and strict PRM1 compatibility remain intact.
  All 29 maps have source-checked names or explicitly descriptive labels; long
  headers fit beside coordinates. Gate transitions, cold saved-game reload and
  pre-upgrade handwriting pass in the emulator. Source-derived door mutations
  and malformed/stale samples pass targeted regressions; a played-through door
  mutation, all-area playthrough and physical acceptance are not claimed.
  [Identity and limits](AREA_IDENTITY.md) · [name provenance](AREA_NAMES.md).
- [x] **M2 — Exploration/combat/wilderness/loading detection.** Retain geometry
  with an explicit non-live state when appropriate. Do not show the exploration
  party position as a tactical combatant or world-map coordinate.
  **Delivered 0.13.0:** named Camp, Combat, Wilderness, Loading / setup and
  Updating states use the existing header/footer. Non-live maps are labeled
  reference, hide the arrow and reject stale tile actions; independent HP
  still works. Original-code bounds and PRM4 parsing strip non-local positions.
  Real loading/setup, camp entry/exit and guided movement are checked; combat
  also replays a real captured state. Wilderness detection is source/synthetic
  checked, not a played-through outdoor journey. Dedicated maps, broader
  gameplay coverage and physical e-ink acceptance remain separate items.
  [Guide](MAP_MODES.md) · [evidence](LOCAL_TESTING.md).
- [x] **M3 — Cross-area / save-load acceptance route.** Walk between distinct
  areas, return, save/reload, and cold boot; pair observed positions with the
  game's own display. Verify notes and the eventual party sidebar on that route.
  **Delivered with M1, 0.11.0:** ordinary New Phlan → Slums → New Phlan route,
  separate camp save, clean Mac shutdown, in-place APK update, cold boot and
  saved-route reload; repeated the gate round trip in the updated APK. Settled
  coordinates/facing and all six party HP/AC rows match the guest. The original
  tile 11,2 handwriting reopens byte-identically, never appearing in the Slums.
  This is the bounded two-area acceptance route, not all-area/combat/wilderness
  coverage or physical e-ink testing. [Evidence](LOCAL_TESTING.md).
- [x] **M4 — Wall and door semantics.** Spot-check distinct wall/door types in
  the Mac game. Until validated, keep neutral door outlines rather than claiming
  a symbol is a secret, locked, or passable door.
  **Delivered 0.13.1:** original Mac accessors require a wall surface before
  door bits count. The shared live/fog/note renderer now ignores unused bits
  on open edges; 88 directional edges across the 29 private maps have them.
  All nonzero door states on real surfaces retain neutral outlines, with no
  lock/secret/passability claim. Actual New Phlan ivy-wall blocking, open-street
  movement and two differently drawn doorway crossings match the game and map;
  the temple crossing triggers its original conversation. 346 Java tests and
  ten focused Android Canvas checks pass. This is a bounded spot check, not
  all-area gameplay or live lock/secret-state mutation coverage.
  [Symbols and source evidence](MAP_EDGES.md) · [local checks](LOCAL_TESTING.md).
- [x] **Q1 — Physical e-ink pass.** Keyboard open/closed, rotation, readable
  game scaling, touch alignment, screen refresh/ghosting, and stylus behavior.
  Offer a manual redraw if useful; no mandatory continuous flashing refresh.
  **Emulator finding (0.5.3):** forcing display size with ADB recreated the
  activity and rebooted the guest, although saved notes survived. Investigate
  real rotation/window changes separately; do not claim seamless resizing.
  **User confirmation 2026-09-14:** stylus drawing, two-finger zoom/scroll and
  overall e-ink presentation work well. N3 is accepted.
  **Closed by the user 2026-09-14**, in his words: "I've done all testing on
  e-ink, so e-ink pass complete." All device testing for this project has been
  performed by Hunter on the physical e-ink tablet; this closes the item on his
  say-so. No agent has run on that hardware, and none of the individual
  sub-checks listed above were separately reported, so do not cite them
  individually — cite this blanket confirmation.
- [x] **Q2 — Input/archive check.** Investigate the `PoolRad2/ITEM2.DAX`
  extraction error on a copy. All maps parse, but that does not establish every
  encounter/item is intact. Do not replace the user's original archive.
  **Delivered 2026-09-14:** alternate extraction recovers the declared 632 bytes
  with matching CRC; only that data fork is replaced in a new extraction copy.
  All 108 files/114 nonempty forks and 81 DAX files/606 records pass the new
  source audit. A new private HFS disk independently preserves all file content.
  Both disk builders reject damaged DAX before creating another disk. 76 Python
  helper tests and 346 freshly executed Java tests pass; Android build succeeds.
  Original archive, installed disks/saves and private APK bundle remain unchanged.
  No full-game/tablet acceptance or automatic installed-disk repair is claimed.
  [Recovery, provenance and safe adoption](ARCHIVE_CHECK.md).
- [x] **Q3 — Reproducible personal updates.** Record the actual tablet model,
  Android/emulated-machine configuration, keep the same signing key, and keep
  upgrade/ROM/disk setup instructions short. No Play Store release required.
  **Supplied by the user 2026-09-14.** The target device is a **Viwoods AiPaper
  Mini**: 8.2-inch E Ink, **1920 × 1440 at 292 PPI**, adjustable front light
  (0–20), octa-core 2.0 GHz ARM, 4 GB LPDDR4X, 128 GB storage with no microSD,
  **Android 13**, stylus with 4,096 pressure levels, Wi-Fi/Bluetooth/USB-C
  (USB 2.0), 191 × 138 × 5.2 mm. Same debug signing key as every prior release;
  updates install in place over the existing app.
  **This immediately mattered:** all emulator testing runs at 1200 × 1600 and
  density 1.25, which is both taller and far coarser than the real panel. At
  1440 px wide and a density near 2.25 the old fixed 216dp party column could
  not coexist with the map's 280dp floor, so the sidebar silently disappeared —
  the "not showing the party up top" report. Fixed in 0.21.0. **Test layout
  changes against 1440-wide high-density geometry, not just the emulator.**

### P1 — five requested reference panels

Each is a separate **Info tool** (moved from the PoolRad menu by UI1), not
another permanent panel. All
five use only the upper half, leave the lower game view undimmed, scroll within
their own bounds, and work offline. Search/keypad controls must stay in that
upper panel too; do not pop a system keyboard over the game for numeric lookup.
Validate/reference the original game's data rather than substitute modern D&D
rules. Make provenance visible and include concise explanations in our own words.

- [x] **REF1 — Levels & skills.** Browse class/level and skill progression:
  XP thresholds, applicable thief abilities, and relevant race/class caps.
  Include related combat/saving-throw values where the original game supports
  them. This is a table viewer, not a leveling or character-editing command.
  **Delivered 0.3.0:** all 29 XP rows match the supplied Mac tables; supplemental
  DOS-derived combat/skill numbers are labeled. Emulator class/level/race-tab
  checks pass; [provenance](LEVEL_REFERENCE.md).
- [x] **REF2 — Spells.** Filter by class, level, and name; show effect, range,
  duration, targeting, and when a spell is usable. Static reference first;
  linking the party's actual prepared spells is the later R3 enhancement.
  **Delivered 0.3.0:** 54 chart entries plus a flagged rulebook-only entry;
  original-source disagreements remain visible. Class/level/name filtering,
  details, internal keypad and portrait/landscape bounds checked in the emulator;
  [provenance](SPELL_REFERENCE.md).
- [x] **REF3 — Weapons & armor.** Browse/compare damage, protection, cost,
  weight, and equipment restrictions supported by this game. Clearly separate
  base equipment values from a character's current bonuses or effects.
  Browse the finite equipment lists; no search field/keypad is needed.
  **Delivered 0.5.0:** see F8 above for the browser, comparison and verification.
- [x] **REF4 — Money conversion.** Enter an amount/denomination and see its
  equivalent in copper, silver, electrum, gold, and platinum, using the verified
  original-game exchange table. Handle mixed coin totals and exact remainders;
  keep gems/jewelry appraisal separate. Calculator only: no changes to the purse.
  **Delivered 0.3.0:** mixed coin inputs and exact remainders, including the
  emulator check 5 gp + 1 cp = 1,001 cp. Own keypad stays above the game;
  [Mac-table provenance](MONEY_REFERENCE.md).
- [x] **REF5 — Adventure journal lookup.** Enter the number the game gives,
  open that entry immediately, and keep recent numbers/bookmarks for fast
  reopening. Support the correct entry type (journal/proclamation/tavern tale),
  validate its real number range, and preserve illustrations/diagrams. Use the
  user's supplied journal files where possible; no online service required.
  No read-ahead dump or assumption that the user's example numbers exist.
  Linking entries to handwritten map notes is the later R5 extension.
  **Delivered 0.14.0:** Info → Journal opens 58 journal entries, 18 sparse
  proclamations and 23 tavern tales from a one-time private local import, with
  14 original illustrations, full-width image pages, recent numbers and
  per-notebook bookmarks. The public APK contains no journal/game payload.
  Actual Android import, invalid replacement preserving the old book,
  category/number validation, bookmark retention through in-place update,
  illustration scrolling and keyboard-open lookup pass. Build, 355 Java tests,
  81 Python helper tests and eight Android companion View checks pass.
  Automatic encountered references remain R9; notebook links/history backups
  remain R5. New-reader physical tablet acceptance is untested, without
  invalidating the user's earlier handwritten-notes acceptance.
  [Import and use](JOURNAL.md) · [evidence](LOCAL_TESTING.md).

## P2 — research-backed additions, not yet implementations

These enrich the original game without changing its rules. Every live field
still needs Macintosh-specific discovery and verification. See the linked
research for sources and portability limits.

- [x] **R1 — Party condition badges.** Clear injured/unconscious/dying/dead and
  relevant effect symbols; text on tap, not a rainbow of tiny indicators.
  **Delivered 0.15.0:** one dark badge takes over the existing class-icon slot
  only when the character needs it; the ordinary class symbol returns otherwise.
  Condition comes from the Macintosh character record's own status byte and its
  nine-entry name table, never inferred from zero HP, and the bounded effect
  chain contributes only the original game's poison and aggregate Helpless
  states. An unreadable chain says unavailable, not "no effects". The 184-byte
  PRP3 packet keeps old PRP1/PRP2 readable with conditions explicitly
  unavailable. 363 Java tests, 22 Android View checks, the rebuilt native probe
  suite and 81 Python helper tests pass; real captures replay as Okay with no
  tracked effects. Live on emulator-5584 the six sample characters keep their
  class symbols and Arax's details read "Condition: Okay". No character was
  played into a rare condition and physical tablet acceptance is untested.
  [Badges and Mac evidence](PARTY_CONDITIONS.md) · [checks](LOCAL_TESTING.md).
- [x] **R2 — Training readiness.** Small XP progress and a training reminder;
  respect class/race limits and normal training, not automatic level-ups.
  **Delivered 0.22.0:** character details show the experience the game has
  recorded and, per class held, the next-level figure the game itself compares
  against — either how much more is needed or that it has been reached, with a
  reminder that a hall teaching that class is still required. Read from
  experience at record `+0xb4`, per-class levels at `+0x9a+slot`, and the game's
  own threshold table at `A5-0x15b4 + slot*0x50 + (level+1)*4`, mirroring the
  comparison CODE7 `+0x4644` makes. A class with no further level never reads as
  ready. Nothing trains, levels, edits experience or predicts a result. 381 Java
  tests and the rebuilt native suite pass; the captures decode the whole sample
  party and agree with the game's own sheet. Live, Arax read "Experience: 2134"
  and "Fighter level 1 · 2001 reached, ready to train" against the guest's own
  `EXP: 2134 / Level: 1`. Nobody was trained through a hall, and the hall's class
  restriction and fee are not modelled. [Evidence](TRAINING_READINESS.md).
- [x] **R3 — Spell readiness.** Prepared-versus-spent spell uses and optional
  resting reminder in expanded party details. No invented mana gauge or
  instant spell restoration.
  **Delivered 0.18.0:** character details now say which memorized spells the
  game will let that character cast right now and which ones they chose that
  still need rest, counted per spell level, with a rest reminder only when
  something is actually waiting. Read from the character's own 21-slot array at
  `+0x17`, whose three states are fixed by five agreeing code sites, with the
  spell level taken from the game's own table. Only counts leave the reader —
  never a spell list or a raw slot byte. The new PRP4 packet keeps PRP1/2/3
  readable with spells unavailable. No mana gauge, and the app never memorizes,
  casts, rests or restores anything. 393 Java tests and the rebuilt native suite
  pass. **Live:** memorizing Cure Light Wounds in the original game produced
  "Awaiting rest: level 1 × 1" matching the guest's own pending list and its
  3→2 allowance, and the city watch interrupting the rest returned both to
  empty. A completed rest was never observed, so "ready to cast" rests on the
  decoder tests. [Evidence](SPELL_READINESS.md) · [checks](LOCAL_TESTING.md).
- [x] **R4 — Equipment at a glance.** Readied weapon/armor, ammunition if
  verified, and a carrying-load/movement warning; no equipment editing.
  **Delivered 0.19.0:** character details show the readied weapon and armor
  under the game's own names, composed the way the game composes them from its
  three-part name table, plus movement in combat squares and carried weight,
  with one warning when the game's own movement has already reached the slowest
  the printed rules describe. Nothing equips, unequips or changes an item.
  An empty hand reads "No weapon readied"; unreadable item blocks read
  "unavailable" and never masquerade as empty. Movement and carried weight are
  plain record fields, so they survive when the item blocks do not.
  **Ammunition is deliberately not reported** — the item's own "if verified"
  condition is unmet, and the details pane says so. 407 Java tests, the rebuilt
  native probe suite and 23 Android View checks pass. Live, the companion read
  Weapon: Long Sword / Armor: Banded Mail exactly matching the guest's own
  character sheet. [Offsets and limits](EQUIPMENT_MEMORY.md).
  *Earlier research note (superseded):* The readied items are a
  handle array at character record `+0xd8` — slot 0 weapon, slot 2 armor —
  confirmed against the code and against the one capture taken in live combat,
  which decodes the sample party's real kit. Blocked on a **stable name
  source**: the item record's leading string is the game's own scratch render
  buffer and sometimes reads back as a fragment or with a stray magic column,
  so it must not be shown. Needs the item type id confirmed and resolved against
  the game's `ITEMS` data before anything ships. Purged item handles must read
  as unavailable, never as "nothing readied".
  [Findings and open questions](EQUIPMENT_MEMORY.md).
- [x] **R5 — Journal ↔ handwritten notebook.** Extend REF5 by linking viewed
  entries/bookmarks to map flags, handwritten comments, and manually checked
  tasks. No automatic quest truth or read-ahead spoiler dump.
  Include the new per-notebook journal lookup history/bookmarks in notebook
  backup/restore; REF5 currently stores these locally in app preferences only.
  **Delivered 0.16.0:** lookups, bookmarks, checked tasks and flag links now
  live in the notebook's own atomic `journal.bin` record and travel with backup
  and restore; a damaged record refuses export rather than backing up nothing.
  History 0.14.0 left in app preferences moves into the notebook once, and the
  old keys are removed only after that record is stored. A bookmark can be
  checked off as the player's own task, never as a quest the game reports
  finished, and a link points only at a flag the player placed on a verified
  area map, so that flag's handwriting becomes the reference's comment. Deleting
  a flag drops its links and leaves other references alone. The flag page gains
  a Journal action inside its existing scrolling tool row, measured to cost the
  0.13.0 sketch height nothing. No entry is ever detected from the running game.
  [Linking and limits](JOURNAL.md) · [backup contents](NOTEBOOK_BACKUPS.md).
- [x] **R6 — Exploration aids — REDUCED by the user 2026-09-14 to the
  search-mode indicator only. Delivered 0.23.0.** A small "S" (or similar) while
  the game is in search mode. **Coordinates on demand are cut**; visited-only
  reveal and directional breadcrumbs already shipped as P0 F10.
  **Delivered 0.23.0:** the header reads `0, 4 W S` while the game's own
  position line reads `" search"`. The probe reports only the bit CODE3
  `+0x2c52` tests — bit 0 of the 16-bit field at `*(A5-0x5eae) + 0x594` — as
  byte 1200 of the new 1,204-byte PRM5 packet, with 255 for an unreadable
  record. Status-only observations carry no marker, and older packets have no
  search byte at all.
  **Live proof:** tapping the game's Search button put the guest at
  `0,4 W 00:00 search` and the companion at `0, 4 W S` in the same frame;
  tapping again cleared both together. 388 Java tests and the native map-probe
  suite pass. Two defects were found and fixed in the same pass: `MapObservation`
  had hard-rejected any packet that was not exactly 1,200 bytes, which turned
  the whole map off on the first PRM5 build, and both parsers indexed the packet
  header before checking its length, which threw on a truncated sample.
  [Header behaviour](MAP_MODES.md) · [evidence](LOCAL_TESTING.md).
- **R7 — Useful places: CUT by the user 2026-09-14.** Most of it had already
  shipped anyway — F6 gave nine player-chosen flag symbols including inn,
  temple, shop and smithy, and R5 gave checked-off tasks on journal bookmarks.
  The only genuinely new part would have been a checkbox on a map flag itself.
  Do not build.
- [x] **R8 — Save checkpoints. Built in 0.17.0, then WITHDRAWN in 0.20.0 at the
  user's direction.** The feature worked and was verified, but it could only
  ever run with the guest shut down, and the user ruled that a non-starter for
  everyday use. Copying a disk the emulator is actively writing yields a torn
  image, so there is no safe instantaneous version; it was removed rather than
  left as an everyday menu entry that mostly refuses. The store, dialog and
  their tests are deleted; `DiskAccessGate` stays because the core and file
  manager still use it. [Original design](CHECKPOINTS.md) is kept as a record.
  Do not reintroduce this without a way to quiesce the guest that does not
  require a shutdown.
  *Original delivery notes:*
  **Delivered 0.17.0:** Info → Save checkpoints copies one writable disk while
  holding the same maintenance lease the desktop tool uses, so saving, restoring
  and deleting are all refused while the emulator holds the disk. Each copy is
  re-read and published only if its SHA-256 matches; each record keeps the size,
  digest, time, last shown area, notebook and a small map picture, and the panel
  says plainly that this is not an emulator save state. Restoring replaces the
  whole disk after checkpointing the current one automatically, so it can always
  be undone, and is refused if anything fails to verify or there is no room for
  the safety copy. Bounded at six checkpoints with a free-space check.
  **Live proof:** a 32 MiB copy of the real `disk1.dsk` matched
  `8a918d7d…` byte-for-byte by independent `sha256sum`; after a reboot changed
  the disk to `ba177721…`, restoring returned it to `8a918d7d…` while the
  automatic safety copy held `ba177721…`, and the restored disk cold-booted with
  the campaign intact. 385 Java tests and 8 companion View checks pass.
  [Guide and limits](CHECKPOINTS.md) · [evidence](LOCAL_TESTING.md).
- [ ] **R9 — Encountered journal entries, automatically (formerly L6).
  PROMOTED TO P0 by the user 2026-09-14.** Detect
  journal entries, proclamations and tavern tales actually shown by the running
  game; add them to the active notebook's persistent, deduplicated Journal list.
  Tap to read the matching locally supplied entry through REF5, including its
  illustrations. Keep categories distinct, do not guess uncertain numbers, and
  never import future script references as if the player had encountered them.
  User priority: the manual reader shipped in REF5; the user has now raised this
  automatic collection to **P0**. Local only.
  **Unblocked and one third delivered in 0.24.0; two categories still open.**
  Following the user's bar hint, the party was driven in-game to the gambling
  tavern of civilized New Phlan (map tile 10,8) and the game printed, in its
  own Message window, the wording that had been missing:
  `YOU OVERHEAR TAVERN TALE 15` — a plain sentence with a bare number, no
  parentheses, no `#`, no `SEE`. `scratch/r9-tavern-tale-15.png`.
  **Shipped 0.24.0:** the Message-window reader is now in the app as a native
  `PRT1` packet on the existing 250 ms poll; `JournalCitation` recognises that
  one verified wording and nothing else; a recognised reference joins the
  selected notebook's **Encountered in play** list once, is tappable straight
  into the REF5 reader, and rides along in that notebook's backup. A truncated
  or unreadable sample records nothing, and a number the supplied journal does
  not define is dropped rather than invented.
  **Live proof:** on a second visit the game printed
  `YOU OVERHEAR TAVERN TALE 18` and Info → Journal showed
  *Encountered in play · Tavern tale 18* in the same frame
  (`scratch/r9-encountered-live.png`) — a different number from the first
  sighting, so it is being read rather than remembered. 408 Java tests and all
  three native probe suites pass.
  **Still open, and why this item is not ticked:** the wordings for **journal
  entries** and **proclamations** have still not been observed, so neither is
  recognised. The city-hall lead was ruled out earlier (the Council Clerk's
  commissions cite nothing), and the tavern run above only produces tales.
  **Searched again 2026-09-14 and the remaining two are gated, not merely
  unfound.** The clerk's whole commission list was stepped through to its end
  and cites nothing; Junior Councilman Cadorna at 6,5 is blocked by the council
  guard (`YOUR PRESENCE IS NOT AUTHORIZED`), and Bishop Braccio at 10,5 by the
  temple guards (`THE BISHOP IS NOT RECEIVING VISITORS AT THIS TIME`). Both are
  story-gated, so in the opening state the civilized district cites tavern
  tales and nothing else.
  **The Slums was swept 2026-09-15 and cites nothing either.** It is the one
  district a fresh party can reach — one step west of where the tour ends — and
  crossing it end to end produced no citation, only wandering monsters. Three
  scripting corrections for that district (doorway-permissive routing, the
  Combat/Wait/Flee/Advance prompt and how to detect it, and that the published
  Slums map's coordinates do **not** match the game's, unlike New Phlan's) are
  in the doc. The cheap in-game leads are now exhausted.
  **Unblocked by one screenshot, or the exact wording, of the game citing a
  journal entry and one citing a proclamation** — from a campaign that has
  actually progressed. Hunter's own `m1gate` save would do and is deliberately
  never opened here; the alternative is playing the sample party far enough to
  be admitted to Cadorna or the Bishop, which is hours of emulated play rather
  than a scripted walk. Driving the guest is scriptable and the corrected
  recipe — number keys, not arrow keys; the Continue button; open-edge-only
  route planning from the live GEO geometry; published map coordinates match
  the game 0-based — is in the doc.
  [Reader and evidence](MESSAGE_MEMORY.md). The risk noted earlier stands and
  must be respected: a wrong number silently plants a spoiler in the notebook,
  so an uncertain detection must record nothing rather than guess.

## P3 — later / optional

- [ ] **L1 — Separate wilderness map. Confirmed wanted by the user
  2026-09-14.** Only after its own structure/location is validated; do not stretch the existing 16×16 area renderer to impersonate it.
  **Validation started 2026-09-15; blocked on reaching the wilderness.**
  Ruled out: it is **not** one of the 29 GEO records — those are all local
  16×16 areas, including the outlying sites the wilderness leads to — so there
  is no existing geometry to reuse, which is why the warning above stands.
  Established from the original code: CODE 5 selects three outdoor sectors at
  `+0x3036`, `+0x3044` and `+0x306a`, each writing presentation `2`, `3` or `4`
  with engine `3`, against the local path at `+0x2ff0` writing presentation `1`
  with engine `4`; the sector is chosen by a switch on the script id at
  `-0x192b(a5)`; and the outdoor paths read the **same 2,048-byte state block**
  at `-0x5eb2(a5)` the local map already uses, testing `+0x366` and `+0x344`.
  Also confirmed: **none of the twenty-three captures is in outdoor mode**, so
  the wilderness has still never been observed.
  **Blocked:** the party's outdoor position needs the same confirmed-step
  capture pair that solved L2, and a fresh `SampleParty` cannot reach the
  wilderness — it lies past the districts it can walk to, and the NPCs who
  would send it there are story-gated. A saved game already outdoors, or a
  session played far enough to travel, unblocks it; the tooling is written and
  proven. [Research notes](WILDERNESS_MEMORY.md).
- [x] **L2 — Tactical combat map. Confirmed wanted by the user 2026-09-14.
  Delivered 0.25.0.** A distinct read-only view; no automatic combat or spoilers.
  **Delivered 0.25.0:** while a battle runs, the companion draws one mark per
  combatant on the squares the game itself placed them — the party filled,
  everyone else hollow — with the occupied area's corners and a plain count in
  the header. It is reference only: nothing on it can be tapped, it names no
  monster, reads no initiative and suggests no move, and it clears the moment
  the battle ends. A packet that does not fully verify draws nothing rather
  than a half-read battlefield.
  Read from a four-byte-per-combatant table at `A5-0x46e4` with its count at
  `A5-0x46e8`, cross-checked against the combat roster so a count that
  disagrees with the chain is refused.
  **Live proof:** a real council-guard battle showed `BATTLE · overview`,
  `25,11 to 36,19`, `6 of yours · 29 others`, with six filled circles against
  twenty-nine hollow squares in the same arrangement as the game's own Combat
  View (`scratch/l2-combat-overview-live.png`). 416 Java tests, four native
  probe suites and 6 detached View checks pass.
  **Deliberately not included:** no terrain is decoded, so the overview shows
  where combatants stand and not what they stand on, and the arena's own bounds
  are unknown, so the pane frames the occupied squares. Those are the natural
  next additions. [How it is read](COMBAT_MEMORY.md) · [evidence](LOCAL_TESTING.md).
  *Research notes from the blocked first attempt:*
  The combatant list needs no new work — it is the party list with monsters
  appended, and the existing reader walks it (six members plus ten `ORC`
  records in one live battle). Ruled out: the 302-byte record does **not**
  carry tactical position. Ten orcs on ten different squares are byte-identical
  except for the chain handle at `+0x110` and their own handle at `+0x114`, so
  the grid and the combatants' places on it are a separate structure.
  Established the cheap repeatable battle for this work — the **council guard**
  inside the City Hall, not a tavern brawl, which spawns waves faster than
  quick combat clears them.
  **Blocked on one experiment:** the Mac build does not move a combatant with
  the number keys that move the party outdoors, so a clean one-square
  before/after RAM diff has not been obtained and **no offsets are claimed**.
  Settle how combat movement input is taken — most likely a mouse click on the
  destination square — then repeat the capture pair with a visibly confirmed
  move. [Research notes](COMBAT_MEMORY.md).
- **L3 — Automatic rune/prompt recognition: promoted to P0 F4.** The user now
  requests hands-off solving from verified memory. Unknown reads still must
  never submit guesses. Track completion above, not twice.
- **L6 — Automatic journal-entry recognition: promoted to P2 R9.** Journal,
  proclamation and tavern-tale references belong in one encountered-entry list.
- **L4 — More visual personalization: CUT by the user 2026-09-14.** Portrait
  picker, user artwork and guest-icon reuse are not wanted. Do not build.
- **L5 — Fine layout preferences: CUT by the user 2026-09-14.** Adjustable
  upper-pane sizing and map zoom are not wanted; the panes are tuned by hand
  instead. Do not build.

## Not in this project

LLMs, cloud accounts/sync, telemetry, rooting, an exposed RAM server, general
multi-game support, stat/HP editors, teleporting, auto-heal/auto-ammo, bypassing
training restrictions, emulator rewrites, and marathon CI or all-device matrices.

The new approved scope is a read-only game companion plus user-owned notes,
screenshots, and explicit desktop personalization. Those user-owned writes do
not authorize hidden game-state manipulation. Tests remain small, relevant,
and attached to each shipped slice.
