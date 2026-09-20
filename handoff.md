# PoolRad Mac Maps — continuation handoff

## Active backlog run — 2026-09-19

Owner instruction: continue until the whole backlog is complete; **each feature
is its own release**. Turn repeated operations into reusable scripts. If an
answer cannot be inferred, leave that item pending and work on another.
**Remove `dunk d6` only after the entire backlog is complete.** This supersedes
the older d4 reference below for the active run.

- F36 closed as not needed in 0.102.0: no companion path drives the game's
  save prompts (all saves are snapshots; the Cmd-key loader is dead F86 code).
  Evidence in docs/SAVE_STATES.md; removing `LoadSequence` is a follow-up.
- F52 shipped in 0.101.0: PoolRad → Rest until healed (`poolrad_party_rest`):
  HP to max, Unconscious/Dying → Okay, chosen spells ready, +0x2c cleared;
  clock and effects untouched. `PoolRad.Rest` logs per-row flags.
- F40 shipped in 0.99.0: fight end → `poolrad_party_bandage` (Dying → Unconscious
  at 0 HP) for each Dying member while someone is Okay, then a quick save.
  `PoolRad.Bandage` logs each row's result. Second authorised write; see
  docs/PARTY.md.
- F104 shipped in 0.96.0: "rest clears chosen spells" is the game's rule
  (camp entry/exit forget pending; only Magic → Rest memorizes). Reminder text
  fixed; code sites in docs/SPELL_READINESS.md. `scratch/f98/venv` has
  capstone/macresources/machfs; `scratch/f98/game.rsrc` is the app's resource
  fork from the private boot disk copy (do not commit).
- F103 shipped in 0.93.0: `AutosaveGate` skips a five-minute autosave when no
  guest input, sample change or disk change happened since the last snapshot
  or restore; `PoolRad.Autosave` logs each skip/request.
- F59 shipped in 0.90.0: Info → Since last rest (`mapper/RestTally`, pure
  Java) counts fights from the mode byte, casts from ready-slot drops and
  rests from an hour of camp clock or memorization; `.rest` snapshot sidecar.
  Casts were never exercised live: every rest in this build emptied chosen
  spell slots without memorizing them (worth its own investigation). The
  Q-toggle-in-combat report was not reproduced; `PoolRad.Quick` logs each step.
  **Sandbox caution:** one APK install on emulator-5586 followed a failed OCR
  shutdown check, so the guest may have been stopped hard; re-seed the sandbox
  disks before trusting them (docs/SINCE_LAST_REST.md, process note).
- F98 shipped in 0.86.0: Info → Saves names the active snapshot (loaded or
  the later manual save) and the room left; `SaveStatus` is pure Java. Live
  checks ran on emulator-5586 via `tools/update-test-app.py`.
- F97 awaits an answer: should old snapshots without disk-verification data
  load after an explicit warning, or be refused? Do not silently choose.
  The existing save/reference fsync runs after gzip closed the descriptor.
- F89 shipped in 0.71.0. Native selection tests and 634 Java
  tests pass; live Lara selection and moved-window Zarram selection passed.
  See `docs/PARTY_SELECTION.md`.
- F79 shipped in 0.72.0: the original game loaded a new generated save;
  every complete character resource matched after loading. See `docs/SAVE_WRITER.md`.
- F66 shipped in 0.73.0: guest selection is marked in both party layouts. `tools/shutdown-test-guest.py` now
  automates normal, OCR-guarded test shutdown and verifies closed disk handles.
- F35 shipped in 0.74.0: unknown saves offer a new notebook, recording
  waits for native completion, and rejected loads restore the previous campaign.
  638 Java tests and live cancel/create/repeat/known/rejected loads passed.
  F97's legacy-snapshot question remains open.
- F80 shipped in 0.75.0: docs/SAVE_FORMAT.md is the exact public format,
  including the leading data byte, BE16 item/effect counts, and unresolved
  semantics.
- F29 shipped in 0.76.0: Info → Options consolidates
  map/global preferences; Settings routes to the same bounded page. Build,
  638 Java, eight navigation and 26 map/party render checks passed.
  Live global persistence, area styles, and Settings-from-hidden checks passed.
- F47 is verified for 0.77.0: persistent per-notebook Message log, 200 readings,
  checked backup integration, 646 Java tests and eight Android navigation checks.
  Live real-message capture, deduplication, on-disk checksum and notebook
  isolation/reload passed. Publish before next feature.
- F42 is the next researched UI item: turn citation notices into an entry-opening
  action. JournalController.openEntry is private and loads through show(); no
  F42 implementation exists yet. A bounded companion notice is preferable to
  placing an Android Snackbar over the guest.
- tools/load-test-save.py now automates the repeated cold-boot game load. OCR
  uses both page and sparse modes; it supports resuming an open file picker and
  was verified loading ExportProof. Always inspect retained party/location
  screenshots. Current disposable guest is camping in New Phlan under Notebook 2.
  Native snapshot tests from before shutdown must not be reused across disk changes.
- Existing `emulator-5584` did not respond to guest input even though its core
  reported running and two disks mounted. It was left intact, then backgrounded
  normally. Do not replace its APK or force-stop it.
- A fresh `emulator-5586` uses `scratch/backlog-sandbox`, seeded with copies of
  the private ROM and `scratch/poolrad-slim-boot.dsk`. See
  `tools/start-test-sandbox.py`; normal Mac shutdown still precedes APK updates.
- `tools/release.sh` now uses accurate commit attribution, updates README and
  install links, runs Java tests, verifies the public universal APK, and locks
  out concurrent release processes. Write/commit feature notes before invoking.

## Current handoff — 2026-09-19, 0.70.0

Use `/home/hunter/workspace/poolrad-macmaps`, `main`, public origin
`git@github.com:huntergdavis/poolrad-macmaps.git`. Read `docs/BACKLOG.md` and
current Git status first. The September 14 notes below are historical, not the
current queue: journal detection and many later features have since shipped.

This slice adds F94 ten rotating quick saves with exact-frame previews, F95
game-file backups in Settings, and F96 queued character Q changes. Main files:
`SaveStateStore`, `SaveStateController`, `SavePreview`, `SaveRequestGate`,
`QuickToggleQueue`, `Core`, `LiveMapView`, and native `OSGLUJNI.c`.
The native snapshot format is unchanged; quick files now live in
`files/savestates/quick-history/` with optional PNG and notebook sidecars.
The old quick slot remains until rotation naturally retires it.
User clarified the controls: **Quick load immediately loads the latest quick
save; Load… browses every quick, named and automatic state.** Do not turn
Quick load back into a picker.

**New safety follow-up F97:** user relays another agent's disk-corruption report
after hard quits; they have not observed it on their tablet. RAM snapshots do
not rewind disk contents. Audit crash consistency and restoring before later
guest writes on disposable images; do not claim this is fixed. Recheck the
existing store's durability path too (its best-effort fsync follows gzip close).
`tools/play.py boot` no longer force-stops the guest. Always quit the game, use
Finder → Special → Shut Down, and verify disk handles closed before replacing
the APK. Never force-stop a mounted test guest for convenience.

Build/test: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ANDROID_HOME=/usr/lib/android-sdk android/gradlew -p android
:minivmac:assembleMacIIDebug :minivmac:testMacIIDebugUnitTest --max-workers=2`.
See `docs/LOCAL_TESTING.md` for this release's evidence. Publish only the universal
Mac II APK, never the personal package. The existing release script hardcodes
an unrelated coauthor; use a normal truthful commit/tag/release instead.
The physical e-ink/stylus acceptance reported by the user applies to earlier
versions; this release is emulator-tested only. Do not remove dunk d4: backlog
items remain. After F97, F89 character selection is the next unfinished P0.

## Archived handoff — September 14 (superseded)

Updated 2026-09-14. **R2 is complete for v0.22.0**, following the 0.21.0 party
sidebar fix, the 0.20.0 removals, R4/F13 (0.19.0), R3 (0.18.0), R5 (0.16.0) and
R1 (0.15.0). R8 and desktop appearance were withdrawn in 0.20.0. See
[docs/SPELL_READINESS.md](docs/SPELL_READINESS.md) for the memorized-spell
array, [docs/CHECKPOINTS.md](docs/CHECKPOINTS.md) for disk checkpoints, [docs/JOURNAL.md](docs/JOURNAL.md) for journal linking,
[docs/NOTEBOOK_BACKUPS.md](docs/NOTEBOOK_BACKUPS.md) for backup contents, and
[docs/PARTY_CONDITIONS.md](docs/PARTY_CONDITIONS.md) for the badge table.

## Resume here

- Repository: `/home/hunter/workspace/poolrad-macmaps`, public GitHub origin
  `git@github.com:huntergdavis/poolrad-macmaps.git`, branch `main`.
- Read **[docs/BACKLOG.md](docs/BACKLOG.md)** and current Git status/history.
  Do not resume the old Grind/Melt Squad projects from chat history.
- **Next actionable software item: R9 — automatic encountered journal entries
  (P0), which is BLOCKED.** Read [docs/MESSAGE_MEMORY.md](docs/MESSAGE_MEMORY.md)
  first. The reader is done and verified: the game's Message text is at
  `A5-0x6178` → `TERec` → `hText` (`teLength` +0x3c, `hText` +0x3e) and decodes
  correctly against every capture. **What is missing is the phrasing the game
  uses to cite an entry.** It is not in STRS0, not in any other resource of the
  game application, and not in the RLE-decoded DAX files; no capture has one
  because all are from the opening tour. Do not invent a pattern — R9 forbids
  guessing. **Ask Hunter for one screenshot, or the exact wording.**
  After R9: L1 wilderness map, L2 tactical combat map, R6 reduced to just a
  search-mode indicator. R7, L4 and L5 were cut by the user; Q1 and Q3 are
  closed on his own reports.
- **The character details pane now needs scrolling** at 1200x1600 to reach the
  training lines. It gained a line per release from 0.15.0 to 0.22.0 and is due
  a layout review before more is added.
- **Test against Hunter's real geometry**, not just the emulator: a Viwoods
  AiPaper Mini, 1920x1440 at 292 PPI, Android 13. The emulator's 1200x1600 at
  density 1.25 hid a bug that removed the whole party sidebar on his device.
- All P0 items are checked. Q1/Q3 still need actual tablet model/Android details
  and specific keyboard/rotation/vendor observations, already requested.
  The user HAS accepted stylus drawing, two-finger zoom/scroll and ordinary
  e-ink presentation. This was user-performed acceptance, not an agent test.
- **dunk d4 stays active:** remove only when the full backlog is complete.

## Q2 delivered

- `tools/game_content.py` and `tools/verify-game-extraction.py`: read-only
  classic StuffIt fork length/CRC audit and DAX index/RLE validation.
- Game-disk and personal-boot builders reject damaged DAX before disk creation
  or growth. No decompressor, game payload or runtime dependency added.
- Unar 1.10.8 left ITEM2 empty; an alternate decoder recovered all 632 bytes
  matching the archive CRC. Only that data fork changed in a NEW extraction.
- **108 files, 114 nonempty forks, 81 DAX files and 606 records pass.** A new
  HFS disk independently preserves all content and type/creator fields.
  Seven imported Finder flags lose bit 0x0100, explicitly documented.
- **76 Python helper tests and 346 freshly executed Java tests pass**; Android
  debug build succeeds. No new guest gameplay/device acceptance was performed.
- Q2 itself was tools-only; REF5 now advances the public app to v0.14.0.
  Existing installed disks, saves and private APK bundle remain unchanged.

## R4 and F13 delivered

- Character details now show the **readied weapon and armor** under the game's
  own names, plus **movement** (combat squares) and **carried weight**.
  Ammunition is deliberately absent: its "if verified" condition is unmet.
- Readied items are handles at character `+0xd8`, slot 0 weapon and slot 2
  armor. Names are composed from three parts at item `+0x2f+n` walked n=3→1,
  skipping zero indices and bits set in item `+0x36`, through the pointer table
  at `A5-0x5db2`. **Never read the item's own leading string** — it is the
  game's scratch render buffer and can say `" Yes  Shield "`.
- Movement `+0x12c` and carried weight `+0x10e` are plain record fields and are
  emitted even when item blocks are purged, which is the common case.
- **F13 (user request):** one map caption line instead of two, `MapViewport`
  reserving 22dp instead of 38dp so the grid grows; and the party sidebar falls
  back to **two columns** when one will not fit, taking width from the map.
  Before this, 7 or 8 members made the whole sidebar vanish at tablet height.
- **407 Java tests, the rebuilt native suite and 23 Android View checks pass.**
- Live: the companion matched the guest's own sheet (Long Sword / Banded Mail);
  the single caption line and larger grid were confirmed on the running app.
  **The eight-member two-column layout is harness-verified only** — the sample
  party has six, so it has not been seen with real NPCs.

## R3 delivered

- Character details gained per-level **Ready to cast** and **Awaiting rest**
  counts, plus a rest reminder shown only when something is actually waiting.
  The party sidebar is unchanged, so no View harness needed updating.
- Source of truth is the character's own **21-slot array at `+0x17`**:
  `0` empty, `1..0x7f` ready, `0x80|id` awaiting rest. Five agreeing code sites
  (CODE4 `+0x0ac2` memorize, CODE4 `+0x27c6` rest, CODE6 `+0x2790` cast list,
  CODE3 `+0x1562` cast consume, CODE6 `+0x4450` reset). Spell level is byte `+1`
  of the 16-byte entry at `A5-0xe84`.
- **Only counts leave the native reader.** New 248-byte PRP4 packet; PRP1/2/3
  still parse with spells unavailable.
- **393 Java tests and the rebuilt native suite pass.** Native coverage includes
  every spell id 1..127 in both states and every invalid level byte.
- Live: memorizing Cure Light Wounds gave "Awaiting rest: level 1 × 1" matching
  the guest's own pending list and its `Cleric Spells: 3` → `2` line; the city
  watch interrupting the rest returned both to empty together.
  **A completed rest was never observed** — every rest in that street was
  interrupted — so "ready to cast" rests on the decoder tests, not live play.
- The game's `Cleric Spells: N` allowance at record `+0xba + class*4 + level` is
  a **remaining** count, not a daily capacity. It is deliberately not presented.

## R8 delivered

- Info → Save checkpoints: verified copies of one writable disk, in
  `filesDir/checkpoints/<uuid>/` as `disk.img` plus a CRC-checked PRCK v1
  `checkpoint.bin` holding size, SHA-256, time, last shown area, notebook and a
  small map PNG. Staged under `.pending-` and published by one rename.
- **Every operation takes `DiskAccessGate.tryBeginMaintenance()`**, so saving,
  restoring and deleting are impossible while emulation holds the disk. This is
  the same lease the desktop appearance tool uses; reuse it for any future
  disk-touching feature rather than inventing another guard.
- Restore checkpoints the current disk first (labelled *Before restoring a
  checkpoint*), so it is always undoable, and is refused if the checkpoint or
  the new copy fails to verify or there is no free slot. Six checkpoints max.
- **385 Java tests and 8 companion View checks pass**; APK gate and signature
  verification pass. Python helpers unchanged and not re-run.
- Live on the real 32 MiB `disk1.dsk`: the copy matched `8a918d7d…` by
  independent on-device `sha256sum`; after a restart changed it to `ba177721…`,
  restoring returned it to `8a918d7d…` with the safety copy holding `ba177721…`,
  and the restored disk cold-booted clean with the campaign intact.
- "Last shown area" is *Not recorded* when the guest is shut down, because the
  companion genuinely has no verified area then. Do not present it otherwise.

## R5 delivered

- Journal lookups, bookmarks, player-checked tasks and player-created flag links
  moved out of app preferences into the notebook's own atomic `journal.bin`
  (PRNJ v1, CRC-checked), and are included in notebook backup/restore.
  A damaged record refuses export rather than backing up an empty history.
- One-time migration: existing `journal.<id>.recent`/`.stars` preferences move
  into the notebook, and the old keys are removed **only** after a successful
  store. Verified live on a notebook that really had 0.14.0 history.
- Only a bookmark can be checked off, and unbookmarking clears the check.
  Links point at a flag the player placed on a verified area map; up to eight
  per reference and 256 per notebook. Deleting a flag drops its links.
- Flag pages gain a **Journal N** action inside the existing scrolling tool row.
  Measured sheet height stays 450px at 1200×1600 — F12's allocation is intact.
- **377 Java tests and 6 note-editor Android View checks pass**; APK asset gate
  and signature verification pass. Python helpers were not re-run: unchanged.
- Live: migration decoded byte-for-byte, check-off and linking persisted across
  a guest reload, and the flag page listed the reference back.
- **Mistake to avoid repeating:** the rebuilt APK was installed while the guest
  still had `disk1.dsk` mounted. The next boot showed System 7.5's improper
  shutdown notice; the volume needed no repair and nothing was lost, but always
  quit the game, choose Special → Shut Down, and confirm `/proc/<pid>/fd` has no
  `.dsk` handle **before** `adb install -r`.

## R1 delivered

- Party sidebar: one dark badge replaces the class symbol only when warranted
  (`+` injured, `Z` unconscious, `!` dying, `X` dead, `S` petrified, `P`
  poisoned, `H` helpless, `A` animated, `>` running, `-` gone, `?` unavailable).
  Tap gives the exact combination in words; the class symbol returns otherwise.
- Condition is the character record's own status byte at `+0x118` against the
  nine-entry Mac name table, never inferred from zero HP. Effects come from the
  bounded `+0x82` handle chain, tracking only poison (ID55) and the original
  aggregate Helpless IDs (31,51,52,53). Unreadable chain = unavailable.
- New 184-byte **PRP3** packet; 168-byte PRP1/PRP2 still parse with conditions
  explicitly unavailable. Nonzero unused pairs and unknown versions are rejected.
- **363 Java tests, 22 Android View checks, the rebuilt native probe suite and
  81 Python helper tests pass**; APK asset gate and signature verification pass.
- Live on emulator-5584: clean guest shutdown, in-place 0.15.0 update, cold boot,
  automatic wheel, `SampleParty` loaded. Six characters keep their class symbols;
  Arax's details read "Condition: Okay". **No character was played into a rare
  condition**, so non-empty badges rest on the decoder/unit/View suites only.
  Physical e-ink acceptance of the badges is untested.
- The user's `m1gate` campaign save was listed but never opened, and nothing was
  saved to the guest during verification.

## REF5 delivered

- Info → Journal: local number keypad, 58 journal entries, 18 sparse
  proclamations, 23 tavern tales, 14 original illustrations with enlargement.
- One-time private `.prjr` import; public APK contains only the reader, no
  game/journal payload. `tools/prepare-journal.py` reads the exact supported
  Mac documents and resource forks, never modifies them, and refuses overwrite.
- Bounded binary parser, actual PNG decoding before atomic import, invalid-book
  rejection retaining the old book, 20 recent lookups and bookmarks per notebook.
- **355 Java tests, 81 Python helper tests, eight Android companion View checks**
  pass; public APK asset gate and signature verification pass. Android import,
  invalid replacement, in-place update retention and all three categories checked.
- Manual lookups are not encountered-game references. No new reader acceptance
  on physical e-ink/stylus hardware; prior notebook pen acceptance still stands.
- Keep the reference book for reimport. Journal recent/bookmark preferences are
  not yet included in handwritten notebook exports; do not claim they are.

## Private files / adoption safety

Everything below remains ignored; do not publish it.

- Originals: `scratch/input/` (ROM, boot disk, game archive).
- Evidence root: **`scratch/q2-archive.Ih4moF/`**.
  `extracted/` is unchanged broken unar output. `maconv-extracted/` is alternate
  output. **`verified-extraction/`** is the corrected fork-preserving copy.
  **`verified-game.dsk`** is the independently checked NEW disk.
  Source/logs/Maconv source and build also remain there.
- Recovered ITEM2: 632 bytes, CRC `feec`, SHA-256
  `48bedd08c0750aa174f4309d4fe742e7b9df9ffef63954d00fc29643e8512ace`.
- Original archive SHA-256 remains
  `6b00883f03f2eca2aaf1d5e9bbb6c6587b3ff5a864a22ccf5483ed5d4d461f9a`.
- Do NOT replace the whole tree with Maconv output: raw resource sidecars and
  faulty metadata assignments. Only its independently verified ITEM2 was used.
  Keep the GPLv3 decoder separate from the Android app; see ARCHIVE_CHECK.md.
- Existing private bundle: `scratch/b1-private-bundle`, NOT silently updated.
  APK updates intentionally do not replace writable disks.
- Current private journal: **`scratch/poolrad-journal-0.14.0.prjr`**, 200,819 bytes,
  SHA-256 `a178f61f3b948a85a408858452db9e5aece330e46adc8b73e0c1fdff2a30f374`.
  Copy this separately to the tablet and import under Info → Journal. The older
  `scratch/poolrad-journal.prjr` was a test draft; use the versioned file instead.
- Public universal APK: `scratch/poolrad-macmaps-0.20.0.apk`;
  SHA-256 `23d27ae23bb5dc49832a50587312c9b47212914a6c6b234d35afea34baae77fd`.
  Earlier builds remain for comparison: 0.19.0
  `c307965d034f7e1ced0b4223241edb77ef9a045b70a365e9d0edfb5583f1f262`, 0.18.0
  `fe396d9ae192fd67155bbfdb0e84cba755941ea7f5a45ee3dd8bda07ade19f94`, 0.17.0
  `b9893126176cfe5873102c73eae3fbd5ec212559841327deb0ad10149428286b`, 0.16.0
  `79fc4da52a2c79419329c6a9cdbbe53364c1a484c1978cbf43c96fc9db1d91e0`, 0.15.0
  `b46a9f4f89b847bd178fdc1af36daea7e1d7ff6e2878c904cad5cfdc3d3e394d`, 0.14.0
  `84068d6f6d9eb3703e9d6567bd627546e66ca018f9d8afaab22c57f4f71837e5`.
- **Combined boot + game test disk, supplied 2026-09-14:**
  `scratch/minivmacandpools.dsk`, 25,165,824 bytes, SHA-256
  `7ee39cb8ee3d96e190eecf10099811e98e086b85cafc516613617da2983816de`.
  Volume `Mini vMac Boot v2`; `PoolRadSave` holds only `SampleParty` and
  `PoRCharacters` — a clean baseline, no campaign save. **But all fifteen game
  items sit directly in `System Folder:Startup Items`, so System 7 opens every
  one of them at boot.** That explains the user's reports of the guest opening
  all the files instead of accepting Continue, the Mac's "longer than 8,000
  characters" text-editor refusals, and no party appearing. Rebuild it with
  `tools/prepare-personal-boot.py`, which requires an empty Startup Items and
  places one alias, before relying on it. **Copy before mounting; never mount the supplied file**, and
  take a checkpoint before letting it replace any existing disk. Verified as a
  file only; not yet booted.
- **Never overwrite a current campaign with a fresh sample disk.** A later
  repair needs clean guest shutdown, an exported current disk, untouched backup,
  and only the verified ITEM2 replacement on another copy; verify all saves
  and forks before import. No automatic installed-disk repair exists in Q2.

## Build/test

From repository root:

```sh
python3 tools/test-game-content.py
scratch/personal-boot-venv/bin/python tools/test-personal-boot.py
python3 tools/test-personal-package.py
python3 tools/test-fetch-personal-assets.py
scratch/personal-boot-venv/bin/python tools/test-prepare-journal.py
```

From `android/`:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/usr/lib/android-sdk \
  ./gradlew :minivmac:assembleMacIIDebug :minivmac:testMacIIDebugUnitTest \
  --rerun --console=plain --max-workers=2
```

The venv has machfs/macresources/mac_alias; requirements are in
`tools/personal-boot-requirements.txt`. **Run HFS jobs serially**: hfsutils shares
the host user's current-volume state. Never mount original supplied disks.
No GitHub Actions workflow is configured; do not claim CI verification.

REF5 uses the isolated `poolrad-package-test` AVD on emulator-5584. Shut down
the guest normally before stopping it. **emulator-5580 is not owned by this
task: do not kill/reset/install over it.** Check current ADB/process state before
reusing 5584. No app data was cleared or campaign disk replaced for REF5.
At handoff, 5584 is left running the final 0.18.0 APK with `SampleParty` loaded
in New Phlan after the guided tour, out of camp. Two checkpoints are stored
(`8a918d7d…` and the `ba177721…` safety copy) and can be deleted freely.
The party sidebar, journal history, tasks, flag links, the checkpoint panel and
the spell lines have been checked, not a new campaign playthrough. Nothing was
saved inside the guest; the memorize experiments live only in guest RAM. **Driving guest menus works with `adb shell input
motionevent DOWN/MOVE/UP`**, which holds the classic Mac menu open across
separate commands so it can be screenshotted; a plain `input tap` or `input
swipe` closes it again before capture. Do not
force-kill a mounted guest. Before the last APK update, the normal Mac exit
ejected all disks; `/proc/<app-pid>/fd` independently showed no open disk handles.

## Working rules

- Original game stays original. No stat editing, teleporting, cloud services,
  LLM dependency or public ROM/game redistribution. Preserve existing saves.
- Finish the selected item, build/test before ticking and committing, then push
  origin main. Final backlog response: one short line, item/finished/blocker.
- Use apply_patch for edits; preserve unrelated dirty work.
- Before debugging/reimplementing use `deja "query"` or recall and cite reuse.
  Q2's bounded recalls timed out; handoff, existing builders, archive warnings
  and existing DaxReader were reused.
- Every user-visible message needs a fresh America/Los_Angeles timestamp:
  `[YYYY-MM-DD HH:MM:SS PDT]` (current abbreviation). Immediately precede every
  tool operation with a timestamped description. Shell first source line must
  be literal `# [timestamp] COMMAND: description`, computed in orchestration,
  not a shell date substitution.
