# PoolRad Mac Maps — continuation handoff

Updated 2026-09-14. **R1 is complete for v0.15.0.** Party condition badges
follow REF5's journal reader. See [docs/PARTY_CONDITIONS.md](docs/PARTY_CONDITIONS.md)
for the badge table, Macintosh offsets and PRP3 packet, and
[docs/JOURNAL.md](docs/JOURNAL.md) for the previous release.

## Resume here

- Repository: `/home/hunter/workspace/poolrad-macmaps`, public GitHub origin
  `git@github.com:huntergdavis/poolrad-macmaps.git`, branch `main`.
- Read **[docs/BACKLOG.md](docs/BACKLOG.md)** and current Git status/history.
  Do not resume the old Grind/Melt Squad projects from chat history.
- **Next actionable software item: R5 — journal ↔ handwritten notebook.**
  REF5 stores lookup history and bookmarks in app preferences only, so a
  notebook backup silently loses them; fix that first, then link entries to map
  flags. Automatic encountered references remain P2 R9. Agreed order after R5:
  R8 save checkpoints, R3 spell readiness, R4 equipment, R2 training, R7 useful
  places, R9 automatic entries, then P3.
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
- Public universal APK: `scratch/poolrad-macmaps-0.15.0.apk`;
  SHA-256 `b46a9f4f89b847bd178fdc1af36daea7e1d7ff6e2878c904cad5cfdc3d3e394d`.
  The previous `scratch/poolrad-macmaps-0.14.0.apk` remains for comparison;
  SHA-256 `84068d6f6d9eb3703e9d6567bd627546e66ca018f9d8afaab22c57f4f71837e5`.
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
At handoff, 5584 is left running the final 0.15.0 APK with `SampleParty` loaded
at the Rolf tour; the party sidebar and details have been checked, not a new
campaign playthrough. **Driving guest menus works with `adb shell input
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
