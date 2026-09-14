# PoolRad Mac Maps — current backlog

Updated 2026-09-13. This is the authoritative feature queue. The earlier
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
| Done | 29 GEO records decoded; 90 Java tests plus native reader checks |
| Done — 0.3.0 | Offline levels/skills, spells, and exact mixed-coin reference panels |
| Done — 0.3.0 | Map/game/keyboard PNG capture with Android Save and Share |
| Done — 0.4.0 | Flag-linked handwritten notes, ink tools/autosave, and separate campaign notebooks |
| Done — 0.4.0 | Exact known-area identities; New Phlan/Slums round trip and restart/reopen note acceptance |
| Done | Sideload/update build and documented SMB transfer route |

Not done: comprehensive area/mode recognition, party RAM fields, direct map
drawing, notebook export/import, physical pen polish, or wallpaper controls.
Do not confuse a working first-area map and one validated gate round trip
with full-game tracking coverage. No numerical completion percentage is useful
while scope is expanding.

## NEXT — handwritten cartographer (P0)

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
- [ ] **N2 — Draw directly on the map.** Separate freehand ink layer with
  pen/eraser/undo, zoomable editing, and tile-space coordinates so strokes survive
  rotation, resize, and the future left-aligned map. Erasing ink must never erase
  walls, the party marker, or a flag accidentally. Keep browse and edit modes
  unmistakable; no stylus/finger event is forwarded to the Mac from this layer.
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

Suggested order after N1–N4: S1, P1, the REF panels, W1, A1. Correctness checks below are gates
for the affected feature, not a second giant framework project.

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
- [ ] **W1 — PoolRad → Desktop appearance.** First offer a quiet flat guest
  desktop and Restore original. Then an optional restrained monochrome fantasy
  motif or user-imported image, previewed before applying. This is the actual
  emulated Mac desktop, not merely the Android margins. Inspect the active
  guest desktop utility/settings; make changes only to the personal boot-disk
  copy and keep a recoverable previous setting. A menu control may need a small
  guest-side mechanism; do not fake it by painting over game windows.
- [ ] **A1 — Launch the game on startup.** Existing disk automount is already
  present. Add a guest startup alias or similarly small reliable launch path;
  avoid blind timed clicks. Keep a bypass/recovery path to the normal desktop.
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
- [ ] **REF3 — Weapons & armor.** Browse/compare damage, protection, cost,
  weight, and equipment restrictions supported by this game. Clearly separate
  base equipment values from a character's current bonuses or effects.
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

## P3 — later / optional

- [ ] **L1 — Separate wilderness map.** Only after its own structure/location
  is validated; do not stretch the existing 16×16 area renderer to impersonate it.
- [ ] **L2 — Tactical combat map.** A distinct read-only view, only if the game
  pane plus party conditions are insufficient; no automatic combat or spoilers.
- [ ] **L3 — Automatic rune/prompt recognition.** Optional convenience after
  the manual illustrated helper; never submit a guessed code automatically.
- [ ] **L6 — Automatic journal-entry recognition.** Detect journal numbers
  actually presented by the running game and maintain a persistent, deduplicated
  encountered-entry list, linked to REF5 for immediate reading without retyping
  numbers. Keep entry categories distinct and associate the history with the
  notebook/run. Read only observed prompts/state, not future script entries;
  uncertain recognition must not silently add a guessed number. Entirely local,
  no LLM or cloud; same optional priority as automatic rune recognition.
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
