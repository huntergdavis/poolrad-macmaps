# PoolRad Mac Maps — continuation handoff

Updated 2026-09-14 after backlog check #18. **Q2 is complete.** This replaces
the earlier paused/uncommitted snapshot. Detailed recovery evidence, source
pins, commands and limits are in [docs/ARCHIVE_CHECK.md](docs/ARCHIVE_CHECK.md).

## Resume here

- Repository: `/home/hunter/workspace/poolrad-macmaps`, public GitHub origin
  `git@github.com:huntergdavis/poolrad-macmaps.git`, branch `main`.
- Read **[docs/BACKLOG.md](docs/BACKLOG.md)** and current Git status/history.
  Do not resume the old Grind/Melt Squad projects from chat history.
- **Next actionable software item: REF5 — adventure journal lookup.** Offline
  reader, correct categories/ranges, recent numbers/bookmarks and illustrations,
  using locally supplied files. Keep it above the game. Automatic encountered
  references remain the separate P2 R9 item; manual lookup does not close it.
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
- No new APK/version/tag: tools-only change. Latest public app remains v0.13.1.
  Existing installed disks, saves and private APK bundle remain unchanged.

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

Prior task emulator 5584 was safely shut down. **emulator-5580 is not owned by
this task: do not kill/reset/install over it.** Use a separate isolated emulator
for later journal UI acceptance. No Q2 background build/test sessions remain.

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
