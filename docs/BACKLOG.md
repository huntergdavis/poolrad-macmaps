# PoolRad Mac Maps — current backlog

Updated 2026-09-18. This is the authoritative feature queue. The earlier
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

- [x] **F18 — A Return button in the corner of the map (P0, user requested
  2026-09-16, shipped 0.32.0).** "Much of the game is mouseable, but I still need to open the
  keyboard to press 'enter' sometimes. Having a tiny return button (like the
  symbol for return) at the bottom-right of the area that holds the map would be
  very convenient." A small ⏎ button in the bottom-right of the companion map
  pane that sends Return to the guest. This is the same key the app's own
  on-screen keyboard already sends, so it is a shortcut for something the app
  does today, not a new kind of access: the readers stay read-only and no guest
  memory is written. Match the footprint and fog buttons — same size, same 48dp
  touch target.
  **Done:** a 26dp ⏎ in the bottom-right of the map pane, 48dp touch target,
  kept inside the pane at any size. It goes through the same scancode path the
  code-wheel answer and the on-screen keyboard use. Verified live: the Mac's own
  "press the Return key to continue" dialog was dismissed by tapping it.
- [x] **F19 — A per-character Quick toggle in the party list (P0, user requested
  2026-09-16, shipped 0.33.0).** "I'd like the ability to toggle quick mode on and off for each
  character. The little character icons in the listview we made should have a
  little square Q toggle that is either on or off (similar to our map toggles)."
  Reading the flag and drawing it is ordinary probe work. **Setting it is not:**
  every reader in this app is read-only and the BOUNDARY says the original game
  stays original, so the toggle must drive the game's own input the way a player
  would — the way F18's Return button and the on-screen keyboard do — and must
  not write emulated RAM. Settle first, against the running game:
  **Checked against the running game, 2026-09-16 — and the answer is awkward.**
  Both remaining menus were photographed. **Character** offers Create New, Drop,
  Modify, Train, View, Add To Party, Remove From Party. **Options** offers
  Sounds, Walking Sounds, Use Compass, Hide Windows in Background, Reset Window
  Locations. Neither mentions quick. The game's only Quick is the one on the
  combat button row — Move / View / Aim / Use / **Quick** / Done — and it
  applies to the character whose turn it is.

  So there is no path through the game's own UI to set quick for an arbitrary
  character at an arbitrary moment, and a Q that worked any time could only work
  by writing the character record in emulated RAM. Every reader in this app is
  read-only and the BOUNDARY says the original game stays original, so that is
  the user's call to make, not one to slip in. Two shapes, both honest:

  1. **Read-only Q, plus a tap that works when the game would accept one.** The
     badge shows each character's real quick state, read from the record; during
     combat, the acting character's Q taps the game's own Quick button, exactly
     the way the Return button presses Return. Other rows show the state but do
     not act. Nothing is written. This is the smaller feature.
  2. **A Q that writes the flag.** Works from anywhere, and is the first time
     this app has written guest memory. It is one byte in a character record and
     not a stat, but it is still a write, and it needs the user to say so.

  Either way the flag has to be found first: capture the record before and after
  using the game's own Quick in a battle and diff, the same method that found
  the combat coordinates. The harness reaches a battle unattended now, so this
  is cheap.

  **Done.** The owner chose the write: "it's time to break the barrier and start
  writing to game memory." The flag is record `+0x11b`, found by capturing all
  8 MB of guest RAM around one press of the game's own Quick button in a live
  sixteen-combatant battle — exactly two bytes moved in that character's record
  and none in the other fifteen, and the other of the two is attacks remaining
  ([PARTY.md](PARTY.md)). A square Q sits at the right of every party row:
  filled when on, outlined when off, a question mark when the byte does not read
  as either. `poolrad_party_set_quick` is the only write in the project and it
  refuses unless the whole party validates and the byte already holds 0 or 1.
  Verified in the emulated machine's own memory: tapping the Q set Tanarakis's
  byte to 1 and no one else's, tapping again set it back, and exactly one byte
  across the six records moved each way.
- [x] **F20 — The numeric keypad reaches the guest (P0, user reported
  2026-09-17, shipped 0.34.0).** Hunter: "the numeric keys 1-9 on a
  keyboard are better, because of the diagonals! ... 1 and 3 are upper left and
  upper right respectively." `keycodeTranslationTable` ran to 114 entries, and
  Android's keypad keycodes start at 144, so a hardware keypad reached the
  emulated Mac **not at all** — only the app's own on-screen numpad did. The
  table now runs to 161 and maps the keypad to the same Mac codes
  `us_numpad.xml` sends. Verified live in a battle: keypad 1, 7 and 9 each moved
  the acting character and the game's own `Move Left` counter fell from 9.
  Also corrected: COMBAT_MEMORY.md and LOCAL_TESTING.md both said combat used
  the **arrow** keys. It does not, and the four arrow keycodes map to -1, so an
  arrow has never reached the guest either. A speculative arrow mapping added
  on the strength of that note was reverted rather than kept.
- [x] **F23 — Whose turn it is (brainstormed and picked 2026-09-17, shipped
  0.37.0).** In a fight the hard thing on a small screen is keeping track of who
  the game is waiting for. It says so in its **Combat Message** window, which is
  a different window from the ordinary Message one: that lives at `A5-0x6178`
  and is empty during a battle, while the combat text is at **`A5-0x6230`**.
  Found by taking the visible text, locating its buffer, and walking back
  through the master pointer to the TERec and then to the global holding its
  handle; confirmed against all sixteen battle captures, where it names the
  acting character and matches the screenshot taken beside it. The packet
  becomes PRC2 with the name in its tail. The overview rings that character's
  marker and the party row gets a bar down its left edge.
- [x] **F24 — "Can train" and "spells await rest" marks (brainstormed and picked
  2026-09-17, shipped 0.37.0).** Both facts were already read and never shown:
  a **T** when the game's own experience threshold for one of the character's
  classes has been passed, saving a speculative walk to the training hall, and
  an **R** when spells chosen at the Memorize screen are still waiting on rest,
  saving a camp nobody needed. Neither is advice — the app is not saying train
  or rest, only that the game would allow it.
- [x] **F22 — Dying and dead told apart, in both views (P0, user requested
  2026-09-17, shipped 0.36.0).** "It should say on the characters view too, if
  they are dying or dead not just on the map view." The combat entry's spare
  fourth byte now carries the game's own condition, so the map draws a diagonal
  ✕ for someone still savable and an upright ✝ for someone past saving — two
  shapes, not one shape in two weights — and the party row prints the word
  (Unconscious, Dying, Dead, Petrified) where the armour class usually sits.
  The header tallies "1 down · 1 lost" and gets more of the line in combat so
  the casualties are not lost to an ellipsis.
- [x] **F21 — Crosses for party members who are down (P0, user requested
  2026-09-17, shipped 0.35.0).** "Can we update the map to have Xs for party
  members who are dead/dying that we could try to bandage etc?" A fair
  correction to F16: dropping everyone at zero hit points also dropped the
  bodies a player most needs to find. The overview now asks the game's own
  condition byte (record `+0x118`) instead of inferring from hit points —
  4 Unconscious, 5 Dying, 6 Dead, 7 Petrified are **down but reachable**, and 2
  Temporarily gone and 8 Gone are off the field. One of your own who is down is
  drawn as a **cross** and counted in the header ("6 of yours · 6 others ·
  2 down"); a monster in the same state is still left out, because the game's
  own Combat View stops drawing it. With no readable condition it falls back on
  hit points, as before.
- [x] **F16 — The battle overview kept drawing the dead (P0, user reported
  2026-09-15, found and fixed 2026-09-17, shipped 0.34.0).** Retitled: the sides
  were never wrong. Hunter: "I don't think the enemy squares code is
  correct, I only saw squares on my people who were on squares that used to be
  occupied by enemies." The coordinates come straight from the game's own
  table and are almost certainly right; what is suspect is **which side each
  square belongs to**. The table entry is `{ i, flag, x, y }` and carries no
  identity — entry `i` only states its own index — so the reader pairs the
  `i`-th entry with the `i`-th combatant of the roster chain. That was
  confirmed on two captures taken at the *start* of a battle, and his report is
  good evidence it stops holding once a fight is under way (initiative, deaths,
  monsters leaving the list).
  Two leads, neither guessed at in the shipped code:
  1. `flag` (entry `+1`, only ever 0 or 1, currently validated and ignored) may
     be the side itself, or alive/acting.
  2. Each record carries **its own handle at `+0x114`**, and in the captured
     battle those handles were four bytes apart inside one master-pointer
     block — so `(handle - block base) / 4` may be the combatant's true table
     index, which would pair record to entry by identity instead of by order.
  **Answered, and it was not the labelling.** Three live experiments settled it.
  (1) At the start of a battle the sides are drawn correctly. (2) After a
  character moves they are *still* correct: one keypad step with Hogarth acting
  moved exactly table entry 3, and Hogarth is chain #3. (3) The real cause is
  that **the dead stay in the list**. A killed orc keeps its place in the chain,
  keeps its entry in the position table and keeps its last square, while the
  game's own Combat View stops drawing it — verified in a live battle, an orc at
  0 hit points still combatant 7 of 16 at (31,15). So the overview left a marker
  where an enemy no longer was, and when the party advanced onto that square it
  looked exactly like "squares on my people who were on squares that used to be
  occupied by enemies". The probe now leaves combatants at 0 hit points out,
  after counting them for the index pairing, which is by position in the chain
  and must not shift. [Research notes](COMBAT_MEMORY.md).
  **First live check, 2026-09-16, with F17's harness:** a real 6-vs-10 fight was
  reached automatically and compared against the game's own Combat View. At the
  *start* of a battle the labelling is **correct** — the game draws the party as
  six checkered figures on the right (Arax the Bold was the selected one) and
  the ten enemies as light shield-bearers on a diagonal up the left, and the
  companion's six filled circles and ten hollow squares sit exactly that way.
  So the order assumption holds when the table is built, which is consistent
  with the two original captures. What is still unchecked is whether it survives
  initiative, movement and deaths, which is what Hunter was looking at. Next:
  advance a battle by rounds and compare again.
- [~] **F17 — Script the guest so a battle can be reached without hands (P0,
  user requested 2026-09-15; steps 1-6 done, 7 in progress).** "I have a feeling we'll be coming back to this
  one." Every new combat feature needs a real battle on screen, and reaching
  one by hand through `adb shell input tap` has repeatedly failed: Mac menus do
  not open from a synthetic tap and double-clicks do not register. His plan, in
  order:
  1. Send keyboard input to the guest from the command line. **Already
     answered:** `adb -s <emulator> shell input keyevent 66` dismissed the
     Mac's own "press Return to continue" dialog on 2026-09-15, because
     `LiveMapView` is deliberately not focusable so hardware keys reach the
     guest. Write it up and wrap it.
  2. A script that sequences those inputs with waits, and settles on a real
     readiness signal rather than a fixed sleep — the screen digest, or the
     `PRT1` Message text the probe already reads.
  3. `load game`, from a default save prepared for the purpose.
  4. `continue`, for the opening walkthrough prompts.
  5. `go to the slums`.
  6. `walk until attacked, then choose Attack`.
  7. Use it for screenshots of each new feature on request.
  Emulator only, never a physical tablet — `android-ui.mjs` already refuses
  anything but an `emulator-NNNN` serial and this must do the same. It drives
  the game the way a player would; it must not write guest memory, and the
  BOUNDARY stands.
  **Built:** `tools/guest.py` (screen digests, ink, settle/await-change, and a
  real mouse through `input motionevent`) and `tools/play.py` (boot, load, tour,
  walk, wander, fight). Written up in [GUEST_SCRIPTING.md](GUEST_SCRIPTING.md),
  tested by `tools/test-guest.py`. Verified end to end: booted, loaded
  `SampleParty`, clicked through Rolf's whole tour, walked to the City Hall door
  at 3,4 and collected its proclamation citation, then loaded `m1gate` and
  wandered the Slums declining the inn. Movement keys confirmed one at a time:
  8 forward, 4 left, 6 right, 2 about-face.
  **Step 6 done 2026-09-16:** `battle` walks until something attacks and answers
  with Combat. From a loaded game it reached a real 6-vs-10 fight in 16 moves,
  and the companion drew the battle overview beside the game's own Combat View.
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
- [x] **R9 — Encountered journal entries, automatically (formerly L6).
  PROMOTED TO P0 by the user 2026-09-14. Delivered 0.24.0 and completed
  0.26.0.** Detect journal entries, proclamations and tavern tales actually
  shown by the running game; add them to the active notebook's persistent,
  deduplicated Journal list. Tap to read the matching locally supplied entry
  through REF5, including its illustrations. Keep categories distinct, do not
  guess uncertain numbers, and never import future script references as if the
  player had encountered them. Local only.
  **Delivered 0.24.0:** the Message-window reader as a native `PRT1` packet on
  the existing 250 ms poll, the `Encountered in play` list in the notebook, and
  the tavern-tale wording `YOU OVERHEAR TAVERN TALE <n>`.
  **Completed 0.26.0**, from wordings the user supplied and, for the
  proclamations, reproduced here:
  - **Proclamations** — `IN YOUR JOURNAL YOU NOTE PROCLAMATIONS LXIV, LXXVIII,
    CIX, AND LIX`, a Roman-numeral list. Matched against the canonical
    spellings of exactly the eighteen the journal defines, so an unknown
    numeral is skipped rather than parsed into some other number.
  - **Journal entries** — `...ENTRY <n> IN YOUR JOURNAL`.
  **Live proof:** standing at New Phlan 3,4 the game printed the proclamation
  sentence and the notebook recorded **Proclamation 64, 78, 109 and 59**, in the
  order printed, beside the Tavern tale 18 that survived two reinstalls
  (`scratch/r9-proclamations-live.png`, `scratch/r9-encountered-proclamations.png`).
  Tavern tales were proven live in 0.24.0 with a different number at a different
  tavern. 425 Java tests and four native probe suites pass.
  **The one honest gap, recorded rather than glossed:** the journal-entry
  wording comes from a screenshot of a **different port**, not of the supported
  Macintosh build, so that third of the feature is shipped but unconfirmed
  here. Its anchor puts the number between `ENTRY` and `IN YOUR JOURNAL`, so a
  wording mismatch means silence, never a wrong number. One Mac-side sighting —
  Mendor's Library is the likely place — would confirm it.
  [Reader and evidence](MESSAGE_MEMORY.md) · [what the player sees](JOURNAL.md).

## P3 — later / optional

- **L1 — Separate wilderness map: CUT by the user 2026-09-15.** The game
  already draws its own overworld map out there, so a companion copy would be
  double-mapping for no gain — the same judgement that cut R7, L4 and L5. Do
  not build. The validation done before the cut is kept as a record, because it
  is the honest answer to "what is the wilderness" and would be needed if this
  ever comes back: it is **not** one of the 29 GEO records (those are all local
  16×16 areas, including the outlying sites the wilderness leads to); CODE 5
  selects three outdoor sectors at `+0x3036`, `+0x3044` and `+0x306a`, each
  writing presentation `2`, `3` or `4` with engine `3`, chosen by a switch on
  the script id at `-0x192b(a5)`; and those paths read the same 2,048-byte
  state block at `-0x5eb2(a5)` the local map uses. The companion still names
  Wilderness mode correctly and refuses to present a stale local map as an
  outdoor one, which is all it should do. [Research notes](WILDERNESS_MEMORY.md).

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

## Queue from the brainstorm rounds (2026-09-17 → 2026-09-18)

Fifty-eight items the owner kept across seven spitball rounds; ideas he skipped
are not recorded. **This list is in his priority order** and the order is the
instruction — where two items are only ordered because one depends on the
other, the entry says so.

**Every group below ships as a real release to `origin/main`,** by his
instruction on 2026-09-18, so that each can be tested on the tablet as it
lands rather than at the end. The planned releases:

| Release | Group |
| --- | --- |
| 0.38.0 | In flight — F27, F28 |
| 0.39.0 | Original P0s — F25, F26, F54 |
| 0.40.0 | Save foundations — F49, F77 |
| 0.41.0 | Reading a save — F78, F33, F34 |
| 0.42.0 | Writing a save — F79, F37, F35, F38 |
| 0.43.0 | The spec and the converter — F82, F81, F80 |
| 0.44.0 | P1 — F66 |
| 0.45.0 | Options page — F29, F55, F31, F64, F69, F32, F59, F30 |
| 0.46.0 | Helper actions — F40, F41, F39, F52, F61 |
| 0.47.0 | Reading the fight — F70, F71, F73 |
| 0.48.0 | The map — F51, F74, F50, F45, F60, F75, F58, F62 |
| 0.49.0 | The party list — F67, F72, F43, F44 |
| 0.50.0 | The notebook — F42, F57, F56, F53 |
| 0.51.0 | Remaining — F46, F48, F68, F47, F76 |
| 0.52.0 | Back of the backlog — F63, F65 |

### P0 — his designation

- [x] **F25 (delivered 0.41.0) — Stop reading when there is nothing to read.** Pause the RAM polls
  while the guest is idle, and suspend the emulator when the app is
  backgrounded. Conditional on auto-resuming the moment it is foregrounded; if
  resume cannot be made reliable, drop the suspend half and keep the pause.
- [x] **F26 (delivered 0.39.0) — Fog and footprints remembered per area.** Today both toggles are
  one global setting. They should be per GEO area, so clearing fog in the slums
  does not clear it in the kobold caves.
- [x] **F54 (delivered 0.40.0) — Show the exits you have mapped but never walked through.** The
  wall and door decoding already knows where the openings are; this is the map
  you drew telling you where you have not been. It spoils nothing, because it
  reads only squares you have already seen.

### P0 — the save chain (promoted 2026-09-18)

**Owner decision, 2026-09-18, replacing the one made earlier the same day.**
The plan had been to call the game's own save routine and never touch the
format. The research into who had already decoded a Gold Box save changed his
mind: "I take it back, we're going to be writing our own saves and we'll need
to document and publish the format."

The reasoning is in [SAVE_FORMAT.md](SAVE_FORMAT.md) and it is worth repeating
here, because it is what makes this block worth its size. The same character
record is documented field-by-field for DOS (285 bytes), Amiga (288) and C64.
Ours measures 302. The Amiga record is documented as the DOS one plus padding,
big-endian, and the Amiga is also big-endian 68k — so the Macintosh is very
likely the same field order with different alignment, and this is an alignment
job against existing work rather than a decode from nothing. **The prize is the
converter at the end:** any platform's save turned into a Macintosh one, so
parties other people played, at any point in the game, can be loaded here for
testing. Nobody has the Macintosh side of that.

Measured on his own `disk2.dsk`: a save is a 12,906-byte data fork plus a
~4.4 KB resource fork, about 17 KB. The DOS-lineage documentation describes the
structures inside a save and says nothing about how the Macintosh port arranges
its two forks around them; that part is ours.

**Three rules for this block, because a bad save costs him his game.** Back up
before anything writes. Never overwrite an existing save — construct into a new
file. Nothing is claimed to work until the game has loaded it and the party
reads back correctly.

- [x] **F49 (delivered 0.42.0) — Back up and restore saves off the disk image.** Promoted to the
  front of this block and treated as a prerequisite: nothing here writes to the
  save disk until his saves exist somewhere else. At ~17 KB each this is cheap,
  and today the disk image is the only copy.
- [x] **F77 (delivered 0.43.0) — Align the Macintosh character record against the documented DOS,
  Amiga and C64 layouts.** Start here, because it is the cheapest step and the
  one that pays elsewhere: the offsets this project found independently (name
  `+0x00`, class `+0x2f`, maxHP `+0x32`, encumbrance `+0x10e`, chain `+0x110`,
  own handle `+0x114`, condition `+0x118`, quick `+0x11b`, AC `+0x11d`, attacks
  `+0x120`, currentHP `+0x12b`, movement `+0x12c`) either fall in the documented
  order once padding is accounted for, or they do not. If they do, the whole map
  is confirmed in one pass — **which unblocks F41 and F39**, both currently
  stuck on the equipment and spell sections.
- [x] **F78 (delivered 0.44.0) — Decode the saved game itself:** what the data fork holds, what the
  resource fork holds, and where the party, position and world state sit in it.
- [ ] **F84 — Decode the saved game's world-state block.** Located by F78 at
  `0x1400`–`0x31ff` in the data fork, exactly 7,680 bytes, dense in a played
  save and entirely absent from an unplayed one.

  **First look, 2026-09-18: it does not yield to byte-gazing.** 239 distinct
  byte values, 6.50 bits of entropy per byte, and no repetition worth the name
  at any stride from 16 to 3,840 — so it is not a table of fixed-size records.
  It reads as a variable-length stream, and the two saves that exist are of the
  same world, so there is nothing to diff.

  **Attempted 2026-09-18 and blocked by F87.** The save was loaded, the game was
  running, and every RAM snapshot came back empty, so the correspondence could
  not be tested at all. What little was learned: none of the block appears in
  the one good snapshot from an earlier session, but that snapshot is of a
  different world, so it says nothing either way.

  **The experiment to do next is not more staring.** The character records in a
  save turned out to be the in-memory records verbatim (F78), so the obvious
  question is whether this block is a verbatim copy of a region of guest RAM
  too. Load a save, take a full RAM snapshot with `tools/CapturePartyRam.java`,
  and search the snapshot for these 7,680 bytes. If they are there, writing a
  save (F79) is largely a matter of copying the right regions out, and the
  block never needs decoding at all. If they are not, the game's own save
  routine has to be traced in its CODE resources, which is a much larger job. Also open from F78: the framing of an item block, which is
  roughly but not exactly 66 bytes, and whether the data fork's numbers really
  are little-endian.
- [x] **F33 (delivered 0.47.0) — Load a save from the companion,** by reading the file.
  **PAUSED 2026-09-18, one question for the owner.** Reading a save is done —
  `SavedParty.parse` already says who is in one, so the companion can show what
  each save holds. Making the game *load* it is the part that needs a decision,
  because of something this project already learned painfully: **the game greys
  out File → Load Saved Game the moment a game is running.** Only Quit stays
  enabled. `tools/play.py` records what happened when that went unnoticed — the
  folder and save names were typed into the running game instead, "where a stray
  letter opened the camp menu and the party was found altering its marching
  order".

  So loading is only possible before a game starts, and there are two ways to
  offer it, which are different features:

  1. **Only at the title screen.** The companion lists saves any time, and the
     Load button works only when no game is running; otherwise it says why. Safe,
     and honest about what the game allows.
  2. **Quit and relaunch to load.** The companion ends the running game and
     loads the chosen save. Works whenever you ask, and throws away anything
     unsaved.

  Guessing costs something either way: (1) looks broken to somebody who wanted
  to load mid-session, and (2) can discard progress. **Waiting for the owner.**

  Whichever it is, one gate is settled and applies to both: the companion will
  check that no game is running by **reading it from guest memory**, not by
  looking at whether a menu item appears grey. That is the check the scripting
  harness did not have, and it is the reason it failed silently.
- [ ] **F87 — RAM snapshots come back empty, and the first diagnosis was wrong.**
  Found 2026-09-18. Snapshots are the right size, 8,388,608 bytes, and almost
  entirely zeros: 22 pages with anything in them against 861 in one from an
  earlier session, with low memory's application-globals pointer at `0x904`
  reading `0xffffff`.

  **The first write-up of this said the probes were reading fine while the
  snapshot was empty, and that was wrong.** They cannot disagree:
  `DeliverRamSnapshot` and `DeliverMapSample` both call the same
  `GetRamForSnapshot`, which returns the one `RAM` pointer and `kRAM_Size`. When
  the snapshot is empty the companion shows "Position unavailable" at the same
  moment, which is the probes finding nothing too.

  So the real question is why the app reads an empty guest while the emulated
  Macintosh is visibly running on screen — a more serious question than a broken
  snapshot path, and worth answering before anything else needs a capture.

  **What makes this hard to pin down:** every attempt costs a multi-minute boot,
  and the machine kept drifting between steps — at a Continue prompt, at the
  title with no party, quit to the Finder. "Position unavailable" is the
  *correct* display in all of those, so it proves nothing on its own. What is
  needed is one capture taken with the party demonstrably walking around, and
  `tools/snapshot-with-save.sh` now boots, loads, checks the state, captures and
  checks the state again in one go so that the reading means something. It has
  not yet caught the machine in play.

  `tools/check-snapshot.py` refuses an empty snapshot rather than handing one
  back, because an empty one is the right size and looks exactly like a real one
  until something tries to read it.

  **Blocks F84**, which cannot be tested without a snapshot of a played game.

- [ ] **F86 — Get the load sequence through a live machine, start to finish.**
  F33 ships the reading, the chooser, the overlay and the guarded sequence, and
  every guard is unit-tested. What has **not** happened is one clean run that
  ends with a party loaded.

  Two real bugs were found by trying, and both are fixed: the Finder step sent
  Return, which renames rather than opens, and once renamed a journal and opened
  that instead of the game; and `Cmd-L` was sent the instant the probe saw the
  game's globals, which is well before it is drawing menus, so it was swallowed
  and the sequence waited out its patience for a dialog nobody had opened.

  Where it reaches now: the quit works, the Finder type-select selects the game
  correctly, and the game relaunches. Whether the load dialog then opens is
  unconfirmed. Each attempt costs a full Macintosh boot, and reinstalling the
  app restarts the emulated machine, which confounds the run -- so this wants a
  harness that drives the app's own menu without reinstalling, not more manual
  attempts.

  **A failure is safe:** the sequence stops, says why, and leaves the guest
  alone. Nothing is typed once a party is seen.

- [ ] **F79 — Write a save the game will load.** Verified the only way that
  counts: the game loads it and the party reads back correctly.
- [ ] **F34 — Auto-load the last save on launch.**
- [ ] **F37 — Periodic auto-save,** named by wall-clock date in am/pm form,
  keeping the most recent 20 and rotating the oldest out. **The rotation is the
  point:** an automatic save must never overwrite a save the player made.
- [ ] **F35 — Notebooks follow save states,** and an unknown campaign offers a
  new notebook rather than writing into the wrong one.
- [x] **F38 (delivered 0.45.0) — Resume polling automatically after the guest restarts.** Today it
  takes a tab toggle.
- [ ] **F80 — Publish the format specification** once it is verified. The
  project's own reverse-engineering work, and the Macintosh piece nobody else
  has. The format only: no game assets, no disk images, no extracted content.
- [x] **F82 (delivered 0.46.0) — Establish what the Macintosh version itself supports:** the level
  cap for each class, the class and race lists, and the item and spell tables it
  actually has. A prerequisite for F81 — a converter cannot adjust to limits
  nobody has written down, and these are exactly the limits a port is likely to
  have changed.
- [ ] **F88 — Decode how an `ITEMS` record becomes a printed name.** Left open
  by F85: the 128 sixteen-byte records in `PoolRadGen:ITEMS` are the items'
  numbers, and the words their names are built from are in the application's
  `STRS`, but which record picks which words is not established. Until it is,
  the converter can say whether an item name is representable -- which is the
  refusal F81 needs -- but not which record to write.

- [x] **F85 (delivered 0.49.0) — Enumerate the item catalogue this version has.** Left open by
  F82: item names come from the game's `ITEM%d.DAX` data files rather than from
  any table in the application, so they are a separate structure from the class,
  race and spell lists. The running game's item names are already read correctly
  by the party probe, by a different route. F81 needs this, because an item from
  another version that does not exist here is exactly the case it has to refuse.
- [ ] **F81 — A converter: any platform's save into a Macintosh one.** The
  reason for the whole block. It lets a party somebody else played, at any point
  in the game, be loaded here — which is a test fixture the scripting harness
  cannot produce at any price.

  **The converter understands the version differences and adjusts for them.**
  Ports are not identical: level caps differ, and a class, race, spell or item
  present in one version may simply not exist in another. A converter that
  copies fields across and hopes produces a party the Macintosh game cannot
  represent, or will not load. So it must know the Macintosh limits (F82) and
  bring the incoming party inside them.

  Three rules keep that on the right side of the stat-editing line:

  1. **Never adjust upward.** Bringing a level-9 fighter down to the Macintosh
     cap is conversion. Raising anything — a score, a level, a hit point — is an
     edit, and needs his sign-off like any other.
  2. **Never adjust silently.** Every adjustment is named in a report shown
     before the save is written: what was changed, from what, to what, and why.
     A conversion that quietly loses a character's spellbook is worse than one
     that refuses.
  3. **Refuse rather than invent.** If something has no Macintosh equivalent and
     no defensible substitute — a class the port does not have, an item that
     does not exist here — stop and say what blocked it. Do not pick the nearest
     thing and carry on.

  **Boundary:** a converted save arrives with whatever that party legitimately
  earned, and converting is not editing. Clamping down to what this version
  supports is conversion; anything that improves a character is an edit, and the
  sign-off for that is his to give per edit, not the converter's to assume.
- [ ] **F36 — Answer the game's own save and overwrite prompts.** Kept, demoted:
  it is only needed on whatever paths still go through the game's own dialogs
  once F79 exists, and may turn out to be unnecessary.

### P1 — his designation

- [ ] **F66 — Mark which character the game has selected outside combat,** the
  way F23 marks whose turn it is inside one. The same question — who is the
  game waiting on — and it goes unanswered everywhere but the guest screen.

### In flight — finish before starting anything below

- [x] **F27 (delivered 0.38.0) — The `W` mark for a slowed character.** Drawn
  when a member's movement is below the party's best, i.e. they are carrying too
  much. `LiveMapView` and `PartyState.slowedByLoad`, with `PartyLoadTest` and
  the render checks that go with it.
- [x] **F28 (delivered 0.38.0) — Tap a combatant on the overview, highlight that
  party row** for a few seconds. Read-only: it identifies, it does not command.
- [x] **F83 (delivered 2026-09-18) — `tools/PartyPaneRenderCheck.java` runs
  clean.** All 23 checks pass on an emulator; the suite had never once run to
  completion. The last two failures were both rules later releases deliberately
  overturned, and both are now checks on the behaviour that replaced them:

  - **Check 20 demanded the local area map during combat.** Since 0.25.0
    `LiveMapView.drawCombat` draws the tactical overview in the map's own
    allocation and returns before any exploration ink. The check now asserts
    what combat owes: the reference map is gone from the screen — fewer than
    half its ink pixels still drawn in place — while the area identity, the
    walked squares and the refusal to invent a position all survive.
  - **Check 21 demanded a visually distinct screen per mode.** Before any local
    observation the header deliberately reads `AREA MAP · Position unavailable`
    whatever the mode, because naming each transient mode made the header flash
    several times a second as the game settled; the mode moved to the accessible
    description. The check now asserts that steadiness — combat draws its own
    screen, every other mode shares one, and `checkStatus` still requires each
    to name itself in the description.

  Worth keeping in mind: a density rule was the wrong instrument here. Combat's
  centred explanation is *denser* than the map's thin walls (1298 dark pixels
  against 1127), so "fewer pixels" would have failed on correct behaviour.
  Overlap with the reference map's own ink is what actually says it is gone.

### The options page, and the toggles that need it to exist first

- [ ] **F29 — An options page.** Absorbs the toggles now scattered on the map,
  and everything below in this block lands on it.
- [ ] **F55 — A legend for the map's marks,** reached from a link in the
  bottom-right of the map area. The marks have piled up — the crosses, the
  daggers, T, R, W, Q and the acting ring — and e-ink has no colour to lean on.
- [ ] **F31 — Option: auto-skip messages.**
- [ ] **F64 — Mirror the game's message window in larger type,** as a toggle.
  The Combat Message and ordinary Message TEHandles are already read, so the
  text costs nothing to obtain; 1984 Mac type at e-ink size is the hardest
  thing on the screen to read. An option, not a default, because it covers
  screen the map wants.
- [ ] **F69 — One-line party rows, as an options toggle (one row vs two).**
  *Investigated 2026-09-18 against his measured geometry, 1440 x 684 at density
  2.0 — there is room, and the gain is larger than the idea suggested.* A row
  is 48dp today: name, then HP with armour class, then the health bar. At six
  members the sidebar already fits one 432px column, so one-line rows would buy
  the map nothing there. They pay at seven and eight members, and at any font
  scale from 1.10 up, where `header + rows x 96px` exceeds the 684px pane and
  the layout falls back to **two** columns: the sidebar takes 792 of 1440px and
  the map drops from 1008px wide to 648px. One-line rows at roughly 28dp keep a
  single column in all of those cases, so the map keeps its full width instead
  of losing a third of it. Worth building for the eight-member and large-type
  cases, not for the common six.
- [ ] **F32 — An info panel** naming which save is loaded and how much room is
  left on the save disk.
- [ ] **F59 — A "since last rest" counter:** fights fought, spells spent. Kept
  on the info screen, not on the map. Pairs with F39 and F52.
- [ ] **F30 — Auto-dismiss the Mac's boot dialog.**

### Helper actions — performed by writing memory, not by driving menus

**Owner decision, 2026-09-18.** These were queued as menu-driving on the
reasoning that an action the game performs itself is the conservative one. He
overruled it, for a reason about the player rather than about safety: "the
player shouldn't have to see a bunch of menu commands being executed for things
the helper does." A companion that puppets the File and Camp menus in front of
you is worse than one that quietly sets the field. So the write is the
implementation here, not the fallback. Recorded in [DESIGN.md](DESIGN.md).
These particular writes need no further sign-off, because each does a thing the
player could have done through the game at a moment they asked for it. Stat
editing, which does not clear that bar, was separately allowed later the same
day — but only edit by edit, with his sign-off each time. Teleporting stays
out.

Each of these is opt-in, writes one named field in a record that has already
passed every check the party reader makes, and refuses rather than guessing when
the field does not hold a value it recognises — the same contract as the shipped
quick-flag write.

- [ ] **F40 — Bandage when a fight ends,** by writing the character's condition
  and current HP, and quicksave alongside it. The owner's standing answer to
  what should always happen after a fight. Needs the condition byte at `+0x118`
  and current HP at `+0x12b`, both already read. This supersedes the
  "auto-heal" exclusion for this specific case; see DESIGN.md. The quicksave
  half is F37's problem, not a memory write — see the note there.
- [ ] **F41 — Equip a weapon at battle start** for anyone without one, by
  writing the equipped field directly. **Blocked on decoding:** the equipment
  section of the character record is known to start where the fixture puts it,
  stride 68, but which field marks a weapon as equipped has not been
  established. Decode it before building.
- [ ] **F39 — Restore memorised spells after rest,** the same spells as last
  time, by writing the memorised-spell fields. **Blocked on decoding:** the
  spell section's layout is known by position but not by meaning.
- [ ] **F52 — "Rest until healed",** by restoring HP, conditions and spells
  directly. Diverges further from the game's own rest than the others do,
  because resting also advances the game clock; decide whether the clock is
  advanced to match, and say so in the release note either way.
- [ ] **F61 — Big Yes/No buttons when the game asks a yes/no question.** The one
  item in this block that stays as input rather than a write, because it is the
  player answering the game, not the helper acting for them. Same family as
  F30, and it routes around the keyboard-focus problem that has repeatedly
  broken the scripting harness.

### Reading the fight

- [ ] **F70 — Name the monsters in the combat header.** The roster walk already
  reads their name bytes; "29 others" is the least informative thing on the
  overview.
- [ ] **F71 — Count how many of each monster type remain** as the fight thins.
- [ ] **F73 — Draw the real arena bounds.** The overview frames the occupied
  squares today because the arena's own bounds were never decoded, which is
  the known limit recorded under L2.

### The map

- [ ] **F51 — Mark where you were attacked.** Wherever a fight started, the map
  keeps a monster icon with a cross through it. Drawn from what actually
  happened to this party, so it spoils nothing and needs no bestiary.
- [ ] **F74 — Mark squares where you found something** — an exclamation mark,
  an open chest, and so on. User-visible history, not a spoiler: it records
  only what this party already found.
- [x] **F50 (delivered 0.50.0) — Area progress on the map header** — "explored 62 of 256 squares".
- [ ] **F45 — Older footprints drawn slightly smaller,** so a trail reads
  directionally. Deliberately not shading: shading does not hold up on e-ink.
- [ ] **F60 — Tap the header to ping your own square,** for finding yourself on
  a large map.
- [ ] **F75 — A 1:1 option** drawing the map at the game's own grid scale.
- [ ] **F58 — An area-connection map** — which door led where, built only from
  movement this party actually made.
- [ ] **F62 — A stitch view:** the neighbouring area's map beside the current
  one when you cross a boundary.

### The party list

- [ ] **F67 — Mark NPCs in the party distinctly** from characters the player
  rolled. The roster walk already tells them apart.
- [ ] **F72 — Show the marching order.** Wanted, with a design caution from the
  owner: the row is already carrying a badge, a name, HP, armour class, a bar,
  T/R/W and Q, and this must not make it busy.
- [ ] **F43 — Long-press a party row, open that character's sheet in the game.**
- [ ] **F44 — A chores tab:** "2 can train · 1 needs rest · 1 dying".

### The notebook

The map already serves as the notebook's underlay — notes are drawn over the
live map surface and linked to flags on it — so no separate tracing feature is
needed. Confirmed 2026-09-18.

- [ ] **F42 — A citation notice opens that journal entry.**
- [ ] **F57 — Open the notebook page for the area you just entered.**
- [ ] **F56 — A note index:** every note listed by area and date, tap to open.
  Handwriting cannot be searched, but it can be listed.
- [ ] **F53 — Export the whole notebook as one file** — every map and every
  note in a campaign, not a single page, because the notes span areas.

### Remaining

- [ ] **F46 — A "Money" page:** the existing converter plus what the party
  actually holds. The purse has not been located in RAM yet.
- [ ] **F48 — The game's own clock in the header,** as a day and an am/pm
  time, never 24-hour. Same rule wherever else a time is shown, including the
  auto-save names in F37.
- [ ] **F68 — Trim the on-screen keyboard to the keys the game uses,** with
  bigger targets. Needs care rather than a fixed subset: names still have to be
  typeable, so the full alphabet must stay reachable — most likely a compact
  default that expands when the game is asking for text.
- [ ] **F47 — A message log.**

### P3

- [ ] **F76 — Templates for a new notebook page** — a blank grid, a ruled list,
  a blank map frame.

### Back of the backlog

**Standing rule for `tools/play.py`:** its ability to play the game grows by
slow iteration and always sits at the back of the backlog, behind every
companion feature. The eventual aim is a harness that can play the game
through unattended; that is a direction, not a milestone, and no round of work
should trade a requested feature for it.

- [ ] **F63 — One backup file for the whole companion** — notebooks, fog and
  trails together. Wanted, but by the owner's own account rarely used, so it
  sits behind everything above. Broader than F53, which is an export for
  reading rather than a restore.
- [ ] **F65 — Teach `tools/play.py` to reach a named area on demand,** so
  screenshots for a feature are repeatable instead of hand-driven. Complex, and
  filed behind everything under the standing rule above.


## Not in this project

LLMs, cloud accounts/sync, telemetry, rooting, an exposed RAM server, general
multi-game support, teleporting, auto-ammo, bypassing training restrictions,
emulator rewrites, and marathon CI or all-device matrices.

**Amended 2026-09-18, twice.** "auto-heal" left this list first: the owner
asked for an after-fight bandage and for rest-until-healed, both performed by
writing memory rather than by driving menus. Then "stat/HP editors" left it too
— "I actually think stat editing is OK from now on, **if I sign off on it**."

The gate is the whole of that, and it is what this list now records. Stat
editing is not a capability the app has; it is something he can authorise one
edit at a time. **No feature writes a stat on its own initiative**, no general
editor gets built as scaffolding for a specific request, and sign-off is per
edit and recorded with the work — "he approved stat editing" is not a citation.

**Teleporting is still excluded.** It was not mentioned, and has been paired
with stat editing here since the beginning; that pairing is not a reason to
assume it went too.

The new approved scope is a read-only game companion plus user-owned notes,
screenshots, and explicit desktop personalization. Those user-owned writes do
not authorize hidden game-state manipulation. Tests remain small, relevant,
and attached to each shipped slice.
