# Keep your cartography

Notebook backups are local files you choose, separate from the original Mac
game and its saves. No account, cloud backup or handwriting service is involved.

## Back up a notebook

1. Open **PoolRad → Notebooks** and select the campaign you want to protect.
2. Open Notebooks again and tap **Back up Notebook N…**.
3. Save the `.prnb` file in Downloads, removable storage or another local folder
   outside the app. Wait for **Notebook backup saved**.

Repeat for each campaign. A backup contains that notebook's original run UUID,
all areas' flags, chosen symbols and vector handwriting, including blank flags
and retained version-1 originals. It does not contain ROMs, disks, character
stats, game saves or another notebook. Completed autosaves queued before the
backup are included; an unfinished pen gesture is not a saved stroke.

The internal prepared file is temporary, **not** the backup. Cancelling the
Android file picker saves nothing. A failed write can leave an incomplete
destination document; the original notebook stays intact and Retry save is
offered. Keep the exported file somewhere safe before uninstalling/clearing
app data, and copy important backups off the device too.

## Restore

Choose **PoolRad → Notebooks → Restore backup…** and select a `.prnb` file.
After the restored notebook is reported, select it in Notebooks yourself;
restoring never silently switches your current campaign.

Restore preserves the original run UUID and note coordinates. If that run
already exists locally, the import is refused: **no merge or overwrite**.
To intentionally return to an older backup, first export the current notebook,
then explicitly remove it, then restore the older file. If an unrelated local
notebook has the same display label, the restored one gets an unused local
number; its campaign UUID and note bytes are unchanged.

Malformed, incomplete, corrupt, unsupported or oversized backups are rejected
before they become visible. A full backup is limited to 64 MiB; individual page
limits remain unchanged. This is a notebook backup, not an emulator save state.

## Save a readable picture

Open a flag page and tap **Save PNG** in its tool row (scroll the row if needed).
The image has a notebook/area/tile heading and the whole fitted map-and-writing
sheet at 1600×768, regardless of the editor's current zoom or pan. Map and symbols
remain protected from ink erasing. It uses the page's pinned map snapshot, not
a later live area. Save through Android's file picker as above.

**PNG is for reading, printing or sharing; it cannot restore editable notes.**
Use `.prnb` for that. The application’s note/management controls stay in the upper
half; Android owns its full-screen document picker.

## Remove a notebook deliberately

**Notebooks → Remove Notebook N…** names the notebook and requires confirmation
before removing all its flags/pages, across every area. Export first: there is
no undo button. Other notebooks and all original game saves stay untouched.
Removing the last notebook creates an empty replacement. If replacement
selection fails, the app reports that separately and lets you choose/create one.

## Storage design

The compact PRNA version-1 archive contains a run UUID, counted raw records and
a SHA-256 trailer. Strict paths, per-record/total limits and existing note
checksums/identities are validated; it is not an executable or compressed bundle.
Version-1 note backups and the earlier unused `map.ink` prototype file are
preserved, not interpreted as new flags. Interrupted `.pending-` writes are
excluded. A future/unknown record refuses export rather than being silently lost.

Import stages in a fresh private sibling directory, validates the complete
archive, then publishes the notebook with one directory rename. Confirmed
removal similarly retires the complete directory before bounded cleanup.
Cleanup failure can leave private retired files, not a half-cleared visible
notebook. All operations share the existing ordered autosave queue. The private
transfer cache expires after seven days and cannot replace a saved external file.

This reuses the existing notebook record validator and ScreenshotController's
Activity-owned document-picker approach. Pending export basenames survive
Activity recreation; a missing temporary source is reported, never invented.
Physical tablet/stylus/e-ink acceptance remains separate from software tests.
