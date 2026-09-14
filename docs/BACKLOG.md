# PoolRad Mac Maps — current backlog

Updated 2026-09-14. This is the authoritative feature queue. The earlier
[implementation plan](PLAN.md) retains the architecture and historical proof
steps; this page supersedes its old exclusions of notes and party information.
Research and feature rationale: [FEATURE_RESEARCH.md](FEATURE_RESEARCH.md).

## Where we are

A working personal Android companion inside Mini vMac, not a game rewrite.
The user reports that it is working well on the tablet. Detailed physical
e-ink/stylus acceptance remains a separate check, not inferred from that report.

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
| Done | 29 GEO records decoded; 152 Java tests plus three native reader suites |
| Done — 0.3.0 | Offline levels/skills, spells, and exact mixed-coin reference panels |
| Done — 0.3.0 | Map/game/keyboard PNG capture with Android Save and Share |
| Done — 0.4.0 | Flag-linked handwritten notes, ink tools/autosave, and separate campaign notebooks |
| Done — 0.4.0 | Exact known-area identities; New Phlan/Slums round trip and restart/reopen note acceptance |
| Done — 0.5.0 | Always-on flags, per-flag map/writing pages, nine symbols and lossless old-note migration |
| Done — 0.5.0 | Browse-only spells and 59-entry offline equipment browser/comparison |
| Done — 0.5.1 | Automatic code wheel; distinct answers and same-process game quit/relaunch verified |
| Done — 0.5.2 | Current/max party health through combat; real damage, healing, reorder and reload verified |
| Done | Private single boot disk, automatic game launch, sample-party load and desktop recovery |
| Done | Sideload/update build and documented SMB transfer route |

Not done: comprehensive area/mode recognition, expanded party details,
bundled personal APK, notebook export/import, physical pen polish, or wallpaper
controls. Separate area-wide drawing was replaced by the shipped flag pages.
Do not confuse a working first-area map and one validated gate round trip
with full-game tracking coverage. No numerical completion percentage is useful
while scope is expanding.

## NOW — user must-fix simplification (P0, 2026-09-13)

These requests supersede the separate area-wide drawing editor and the old
optional-only code-wheel recognition policy below. Finish them before expanding
the lower-priority queue.

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
- [ ] **N3 — Pen-tablet polish.** Large flag hit targets, dark strokes, palm
  rejection where Android reports usable stylus data, optional pen-only mode,
  finger navigation, cancellation-safe strokes, and no animated note opening.
  Test real hardware rather than promise vendor-specific pen latency/eraser
  behavior from an emulator. Default pencil-style black ink, not color-only UI.
- [ ] **N4 — Protect the notebook.** Export/import the complete local notebook
  (flags + vector strokes + area/run identities), export a readable image,
  atomic saves and failure feedback, and confirmed clearing. App updates retain
  notes; uninstall does not become the only way to discover a missing backup.

## P1 — requested comfort features and remaining correctness

After the immediate P0 fixes, prioritize the ready-to-play package, startup and
wallpaper below, then Journal and the remaining comfort work. Correctness checks
are gates for the affected feature, not a second giant framework project.

- [ ] **B1 — Ready-to-play personal package.** An opt-in bundled build imports
  the supplied boot/game images (and a compatible ROM if permitted), verifies
  their hashes, and copies them into private writable storage only on first use.
  Updating the app must never replace existing disks/saves. Keep a recovery/import
  path and a public bring-your-own-files flavor. No private images in Git.
- [ ] **B2 — Optional asset-fetching build pipeline.** Fetch explicitly configured
  images from a pinned repository/source into ignored private build inputs;
  verify checksums and fail clearly when absent or changed. No implicit checkout
  side effects. Treat the resulting bundled APK as containing those same images:
  public distribution requires a redistribution-rights review, not merely an
  archive.org/GitHub URL or an abandonware label. Personal artifacts must not be
  uploaded to public releases by default. [Distribution reference](https://www.copyright.gov/help/faq/faq-digital.html).
- **A1 and W1 below are next in this P1 group:** reliable guest auto-launch and
  a restrained, recoverable custom Mac desktop. Together with B1/B2 these form
  the modern ready-to-play onboarding slice; they do not change the game rules.

- [ ] **UI1 — Companion tabs.** Keep the Mac display and keyboard in place;
  switch only the upper companion pane. Start with working Map and Info tabs
  (Info groups the reference tools); add Journal and Notes when those workflows
  exist. Keep Screenshot/settings in the menu. Preserve map visibility and
  selected-tab state; use actual companion bounds for in-pane tools. No empty
  placeholder tabs. See [the implementation design](TABS.md).

- [x] **S1 — PoolRad → Screenshot.** Save one PNG of the full app composition:
  map, party sidebar when present, and the real guest display (plus keyboard if
  open). Include drawn marks/flags, dismiss the menu before capture, and offer
  Android save/share. Capture a coherent frame; avoid blank guest surfaces.
  No upload or request for broad screen-recording access just to capture our app.
  **Delivered 0.3.0:** guest/map captures at 15,1 W and 11,2 S, keyboard capture,
  byte-identical Android Save output, cancelled picker and successful subsequent
  capture, and the Android image/png share chooser verified in the emulator.
  No external recipient was selected. System bars/separate dialogs are excluded.
- [ ] **P1 — Map left, compact party right.** Use the currently spare horizontal
  space in the upper pane; leave the Mac display underneath. Name, monochrome
  face/class icon, AC, and current/max HP with a small high-contrast bar. Tap a
  row for details instead of always showing a full sheet. Read actual Mac party
  records first; validate damage/healing, party reorder, and joining/leaving
  members. Show unavailable values honestly. Start with original class symbols
  or user-selected pictures; actual guest combat-icon extraction is optional.
  Auto-collapse the sidebar on narrow windows rather than shrink the game.
  **Immediate health subset promoted to F7:** current/max HP and compact names
  are now requested first. AC, portraits and expanded details remain here.
- [ ] **W1 — PoolRad → Desktop appearance.** First offer a quiet flat guest
  desktop and Restore original. Then an optional restrained monochrome fantasy
  motif or user-imported image, previewed before applying. This is the actual
  emulated Mac desktop, not merely the Android margins. Inspect the active
  guest desktop utility/settings; make changes only to the personal boot-disk
  copy and keep a recoverable previous setting. A menu control may need a small
  guest-side mechanism; do not fake it by painting over game windows.
- [x] **A1 — Launch the game on startup.** Existing disk automount is already
  present. Add a guest startup alias or similarly small reliable launch path;
  avoid blind timed clicks. Keep a bypass/recovery path to the normal desktop.
  **Delivered through F9:** standard System 7 Startup Items alias plus a tested
  desktop-only recovery build. Public APKs still require the user's own files.
- [ ] **M1 — Reliable area identity and names.** Resolve and validate a stable
  identifier across transitions and reloads; label areas meaningfully. This is
  the N1 persistence prerequisite as well as a map usability improvement.
  N1 now supplies exact fingerprints for 29 known records and validates the
  New Phlan/Slums gate round trip. Broader names, mutable geometry and coverage
  remain open; [current limits](AREA_IDENTITY.md).
- [ ] **M2 — Exploration/combat/wilderness/loading detection.** Retain geometry
  with an explicit non-live state when appropriate. Do not show the exploration
  party position as a tactical combatant or world-map coordinate.
- [ ] **M3 — Cross-area / save-load acceptance route.** Walk between distinct
  areas, return, save/reload, and cold boot; pair observed positions with the
  game's own display. Verify notes and the eventual party sidebar on that route.
- [ ] **M4 — Wall and door semantics.** Spot-check distinct wall/door types in
  the Mac game. Until validated, keep neutral door outlines rather than claiming
  a symbol is a secret, locked, or passable door.
- [ ] **Q1 — Physical e-ink pass.** Keyboard open/closed, rotation, readable
  game scaling, touch alignment, screen refresh/ghosting, and stylus behavior.
  Offer a manual redraw if useful; no mandatory continuous flashing refresh.
- [ ] **Q2 — Input/archive check.** Investigate the `PoolRad2/ITEM2.DAX`
  extraction error on a copy. All maps parse, but that does not establish every
  encounter/item is intact. Do not replace the user's original archive.
- [ ] **Q3 — Reproducible personal updates.** Record the actual tablet model,
  Android/emulated-machine configuration, keep the same signing key, and keep
  upgrade/ROM/disk setup instructions short. No Play Store release required.

### P1 — five requested reference panels

Each is a separate **PoolRad menu option**, not another permanent panel. All
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
- [ ] **REF5 — Adventure journal lookup.** Enter the number the game gives,
  open that entry immediately, and keep recent numbers/bookmarks for fast
  reopening. Support the correct entry type (journal/proclamation/tavern tale),
  validate its real number range, and preserve illustrations/diagrams. Use the
  user's supplied journal files where possible; no online service required.
  No read-ahead dump or assumption that the user's example numbers exist.
  Linking entries to handwritten map notes is the later R5 extension.

## P2 — research-backed additions, not yet implementations

These enrich the original game without changing its rules. Every live field
still needs Macintosh-specific discovery and verification. See the linked
research for sources and portability limits.

- [ ] **R1 — Party condition badges.** Clear injured/unconscious/dying/dead and
  relevant effect symbols; text on tap, not a rainbow of tiny indicators.
- [ ] **R2 — Training readiness.** Small XP progress and a training reminder;
  respect class/race limits and normal training, not automatic level-ups.
- [ ] **R3 — Spell readiness.** Prepared-versus-spent spell uses and optional
  resting reminder in expanded party details. No invented mana gauge or
  instant spell restoration.
- [ ] **R4 — Equipment at a glance.** Readied weapon/armor, ammunition if
  verified, and a carrying-load/movement warning; no equipment editing.
- [ ] **R5 — Journal ↔ handwritten notebook.** Extend REF5 by linking viewed
  entries/bookmarks to map flags, handwritten comments, and manually checked
  tasks. No automatic quest truth or read-ahead spoiler dump.
- [ ] **R6 — Exploration aids.** Search-mode indicator, coordinates on demand,
  visited-only reveal option, and subtle visited-tile/breadcrumb history.
- [ ] **R7 — Useful places.** Player-created service symbols for inns, temples,
  shops, and training; manually checked tasks linked to notes. Do not expose
  unvisited event scripts as if the player discovered them.
- [ ] **R8 — Save checkpoints.** Explicit backups of writable disk/save copies
  with a thumbnail and known area label. Quiesce disk writes before copying;
  never claim an in-flight disk copy is a safe emulator save state.
- [ ] **R9 — Encountered journal entries, automatically (formerly L6).** Detect
  journal entries, proclamations and tavern tales actually shown by the running
  game; add them to the active notebook's persistent, deduplicated Journal list.
  Tap to read the matching locally supplied entry through REF5, including its
  illustrations. Keep categories distinct, do not guess uncertain numbers, and
  never import future script references as if the player had encountered them.
  User priority: P1 manual reader first, P2 automatic collection next. Local only.

## P3 — later / optional

- [ ] **L1 — Separate wilderness map.** Only after its own structure/location
  is validated; do not stretch the existing 16×16 area renderer to impersonate it.
- [ ] **L2 — Tactical combat map.** A distinct read-only view, only if the game
  pane plus party conditions are insufficient; no automatic combat or spoilers.
- **L3 — Automatic rune/prompt recognition: promoted to P0 F4.** The user now
  requests hands-off solving from verified memory. Unknown reads still must
  never submit guesses. Track completion above, not twice.
- **L6 — Automatic journal-entry recognition: promoted to P2 R9.** Journal,
  proclamation and tavern-tale references belong in one encountered-entry list.
- [ ] **L4 — More visual personalization.** Portrait picker, user artwork, and
  optional guest-icon reuse; monochrome defaults, no third-party sprite rip pack.
- [ ] **L5 — Fine layout preferences.** Upper-pane sizing and map zoom, with
  saved settings and a one-tap return to a readable default.

## Not in this project

LLMs, cloud accounts/sync, telemetry, rooting, an exposed RAM server, general
multi-game support, stat/HP editors, teleporting, auto-heal/auto-ammo, bypassing
training restrictions, emulator rewrites, and marathon CI or all-device matrices.

The new approved scope is a read-only game companion plus user-owned notes,
screenshots, and explicit desktop personalization. Those user-owned writes do
not authorize hidden game-state manipulation. Tests remain small, relevant,
and attached to each shipped slice.
