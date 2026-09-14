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

This is **manual lookup**, not automatic detection of references shown by the
game. Automatic encountered references remain backlog R9. A viewed entry is
not proof the party encountered it, and tavern tales need not be true.

## Import your own journal once

The public APK contains the reader, **not the copyrighted journal content**.
The user's supplied Macintosh game archive includes two CEM8 documents with
text and PICT resource forks. On the development machine, convert those files
into a private reference book:

```sh
# Existing personal-boot venv provides macresources. ImageMagick (magick) must
# also be installed. Use the fork-preserving extraction, including .rsrc files.
scratch/personal-boot-venv/bin/python tools/prepare-journal.py \
  'scratch/q2-archive.Ih4moF/verified-extraction/Pool Of Radiance' \
  scratch/NEW-poolrad-journal.prjr
```

Copy the resulting `.prjr` file to the tablet (the same SMB/file-manager route
as the APK works). In **Info → Journal → Import journal book**, select it.
It is validated and copied into private app storage, so later reading needs
neither the original path nor a network connection. Cancel leaves the old book
alone; a damaged import does not replace it. Replacing a reference book does
not replace the game's disks or the notebook's lookup history.

For this workstation, `scratch/poolrad-journal-0.14.0.prjr` has been prepared:
99 references and 14 original illustrations. It is private and
ignored by Git. No source download, text generation, OCR service, or emulator
memory access is involved.

**Backup limits:** retain the `.prjr` file for reimport. Recent/bookmarked lists
are stored locally in app preferences per notebook ID; they are **not yet
included in handwritten notebook exports**. Uninstalling/clearing app data
removes them. Cross-linking entries to map notes is the later R5 feature.

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
