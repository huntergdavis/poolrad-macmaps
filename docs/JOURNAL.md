# Adventure journal

**Info → Journal** reads the original references above the game. Tap **Look up
a number**, select Journal, Proclamation or Tavern tale, enter its number with
the local keypad, and Read. No system keyboard covers the Mac display.

- Journal entries **1–58**; tavern tales **1–23**.
- The **18 proclamations are sparse**, not a continuous range. The number
  picker includes their printed Roman labels and decimal equivalents. For
  example, LIX is 59; CCXIV is 214. An unavailable number is rejected.
- Original illustrations remain in order; tap one for a full-width page and
  scroll vertically to inspect it.
- Bookmark a reference or reopen one of the last 20 manual lookups. These lists
  are separate for each selected campaign notebook and survive app restarts.
- Only explicitly requested references open; no next-entry/read-ahead carousel.

A viewed entry is not proof the party encountered it, and tavern tales need not
be true.

## Encountered in play

When the running game names one of its own numbered references out loud, that
reference joins the selected notebook's **Encountered in play** list, in the
order the game named it, and is tappable straight into the reader. It is
recorded once; re-reading the same message does not list it twice. The list
belongs to the notebook and is included in its backup.

**What counts as the game naming a reference is deliberately narrow.** The app
matches only wording that has actually been seen in a running game:

- **Tavern tales** — `YOU OVERHEAR TAVERN TALE <number>`.
- **Proclamations** — `IN YOUR JOURNAL YOU NOTE PROCLAMATIONS LXIV, LXXVIII,
  CIX, AND LIX`, a list in Roman numerals. Only the eighteen numerals the
  supported journal defines are recognised, so a numeral it does not know is
  skipped rather than guessed at.
- **Journal entries** — `...ENTRY <number> IN YOUR JOURNAL`. This one is
  matched from a screenshot of a *different port* of the game, not of the
  supported Macintosh version, so it may not fire at all here. If the wording
  differs, nothing is recorded — the pattern cannot produce a wrong number.

Anything the reader cannot read cleanly, or a number the journal does not
define, records nothing at all. [How the text is read](MESSAGE_MEMORY.md).

## The journal is included

The journal ships inside the app, as
`assets/journal/adventurers-journal.prjr`: all 58 journal entries, 18
proclamations, 23 tavern tales and the 14 original illustrations. There is
nothing to import and nothing to prepare — open **Info → Journal** and read.

Pool of Radiance is a 1989 game for a machine discontinued in the 1990s, long
abandoned commercially, and the journal is the part of it a player cannot
reasonably do without: the game prints bare reference numbers and expects a
printed book at your elbow. Hunter's call, 2026-09-15.

`tools/prepare-journal.py` is kept because it is how that book is built from
the original Macintosh CEM8 documents, and how it would be rebuilt if the
source or the format changed:

```sh
scratch/personal-boot-venv/bin/python tools/prepare-journal.py \
  'scratch/extracted/Pool Of Radiance' \
  android/minivmac/src/main/assets/journal/adventurers-journal.prjr
```

`tools/check-wheel-apk.mjs` verifies the bundled book on every release: exactly
one `.prjr`, at that path, byte-identical to the one in the tree. ROMs, disks
and DAX archives are still refused.

**Backups:** the reference book is not copied into a notebook backup, and does
not need to be — it arrives with the app. From 0.16.0 the lookup history, bookmarks,
your own checked tasks and your flag links live inside the selected notebook and
**are** included in its [backup and restore](NOTEBOOK_BACKUPS.md). Any history
0.14.0 left in app preferences is moved into the notebook once, the first time
that notebook is opened. Uninstalling or clearing app data still removes
everything that was never exported.

## Linking references to your own notes

Reading a reference offers **Bookmark**, **Check off** and **Link a map flag…**.
All three are your own marks:

- A bookmark is a task you chose to keep. Checking one off records *your*
  judgement, not a quest the original game reports as finished, and only a
  bookmark can be checked off.
- A link points at a flag **you** placed on a verified area map, so the
  handwriting on that flag's page becomes the reference's comment. Links never
  reveal an unvisited place and never mark anything as discovered.
- Each reference takes up to eight flags and a notebook up to 256 links.
  Deleting a flag drops its links and leaves every other reference alone.

The handwritten flag page gains a **Journal** button in its existing scrolling
tool row, showing how many references you linked to that flag; the drawing area
keeps the full height 0.13.0 gave it. Opening a link from a reference needs that
area to be the current one, because the page belongs to a real map position.

## Fidelity and provenance

The converter accepts the exact supported Mac text/resource hashes listed in
`tools/prepare-journal.py`. Changed editions are rejected rather than mapped
using guessed offsets. Both original files are read-only:

- `Journal Entry 01-33`: includes all printed proclamations and the first
  journal section. Entry 33 continues at the beginning of the second file.
- `Journal Entry 34-END`: contains the continuation, entries 34–58, and tales.
- PICT resource IDs 1000 onward correspond to the documents' picture
  placeholders. Fourteen original pictures are converted to PNG locally.
- Image-only entries retain their printed headings inside the illustrations.
  Entry 37's two PICTs contain the atlas panels and Phlan picture; the original
  trailing PCX marker is redundant and removed, not replaced with invented artwork.
- Old Mac menu-opening instructions and printed page-break labels are removed.
  Graphic markers become readable captions. Original narrative wording,
  paragraph order, diagrams and occasional source typos remain.

Reused the supplied documents, the fork reader from [Q2](ARCHIVE_CHECK.md),
the existing Activity-owned local picker/ordered notebook IO patterns, and
`UpperHalfReferenceDialog` for companion-bounded windows. A bounded local
`deja` recall timed out; no prior successful implementation was claimed.

The `PRJR` v1 format contains only bounded UTF-8 text and PNG blocks, never HTML,
scripts, URLs to fetch, ZIP paths, or commands. The parser requires all 99 unique
valid keys, caps file/block sizes and image dimensions, and rejects trailing or
truncated data. Android verifies actual image decoding before atomic import.
The converter refuses an existing output path and verifies source hashes before
converting pictures. No original Mac file, ROM, disk or save is modified.

## Verification

Synthetic tests cover all number categories, corrupt/duplicate/truncated books,
image bounds, immutable content, bounded history, bookmark toggles, malformed
history and exact parser boundaries (entry 33, image-only entries and atlas).
Run `tools/test-prepare-journal.py` in the personal-boot venv and the normal
Android Java unit suite. All test fixtures contain invented text, not game data.

The Android build and all 355 Java tests pass, as do 81 Python helper tests
and eight Android companion View checks. Real document-picker import,
invalid replacement retaining the old file, all three reference categories,
and bookmark/recent persistence through an in-place update were verified on
the isolated emulator. The final full-width atlas rendering was visually
inspected. [Evidence and checksums](LOCAL_TESTING.md#ref5--offline-adventure-journal-2026-09-14-v0140).

Physical e-ink/stylus acceptance of this **new reader** remains untested. Earlier
user acceptance of drawing and two-finger notebook navigation remains valid;
it is not a claim of having tested Journal on the tablet.
