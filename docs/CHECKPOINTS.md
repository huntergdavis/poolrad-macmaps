# Save checkpoints

**Info → Save checkpoints** keeps explicit, player-requested copies of one
writable emulator disk. A checkpoint is a complete byte-for-byte copy of the
disk file, verified by SHA-256 after it is written.

## What a checkpoint is not

It is **not an emulator save state.** It contains no RAM, no CPU state and no
running game, so it cannot resume a turn, an encounter or an unsaved position.
It captures exactly what the original game had already written to that disk.
Save inside the game first; then the checkpoint holds that save.

Because a copy taken while the guest is writing could catch a half-finished
write, every operation here demands the same **maintenance lease** the desktop
appearance tool uses. Saving, restoring and deleting are all refused while the
emulator holds the disk, and the panel says so:

> Save and quit the game, then use the Mac's Special > Shut Down. Wait for
> Restart Emulator, then reopen this panel. Do not use Force Power Off.

## Saving

Choose the disk, shut the Mac down, then **Save a checkpoint**. The copy is
written to a private staging folder, re-read, and published only if its digest
matches what was read from the disk. A copy that does not verify is discarded
and nothing is listed. Nothing is ever written into the emulator's disk folder.

Each checkpoint records the file name, size, SHA-256, the time it was taken, the
**last area the companion displayed**, the active notebook, and a small picture
of that map. Those labels are companion history, so you can tell one checkpoint
from another; they are **not** a position decoded out of the copied disk, and
the picture is optional — a checkpoint is still saved without one.

## Restoring

Restoring **replaces the whole disk, including every save inside it**. Before it
does, the disk as it stands is checkpointed automatically as *Before restoring a
checkpoint*, so the replacement can always be undone. The restore is refused,
leaving the live disk untouched, if:

- the emulator is running or another disk operation is in progress;
- the checkpoint no longer matches its recorded digest;
- the freshly written copy does not verify; or
- there is no free slot for the safety copy — delete one first.

The replacement itself is a single same-directory rename, so a failed restore
keeps the existing disk rather than leaving a partial file.

## Limits, honestly stated

- **Six checkpoints**, and a restore needs a free slot for its safety copy.
- Saving needs free space for a full copy plus a quarter, or it refuses.
- Checkpoints live in this app's private storage. **Uninstalling or clearing app
  data removes them**, and they are not part of a notebook backup.
- Your original supplied ROM, disks and archives are never read or written here.
- A checkpoint of a disk that was damaged before the copy faithfully preserves
  the damage. Verification proves the copy matches the source, not that the
  source is a healthy HFS volume.
- Deleting a checkpoint cannot be undone; it never changes the current disk.

## Storage

Each checkpoint is a private folder named by a random UUID containing
`disk.img`, the raw copy, and `checkpoint.bin`, a CRC-checked PRCK version-1
record holding the identity, timestamp, size, digest, labels and thumbnail.
A record whose id does not match its folder, whose checksum fails, or which is
truncated is reported as an error rather than being listed as an empty entry,
and a folder like that also refuses to be deleted or restored. Interrupted work
stages under a `.pending-` name and is never listed.
