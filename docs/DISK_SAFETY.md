# Snapshot disk consistency

F97 ships in 0.84.0. Acceptance completed on 2026-09-19.

Snapshots contain RAM, CPU, devices and video. They do not contain disk bytes.
Restoring cached HFS metadata against newer disk contents can lose files or
damage the filesystem. F97 requires the exact mounted disk contents from the
capture and refuses a mismatch.

F34 (0.85.0) extends the native machine body in PRQS4 and rejects PRQS1–3.
The disk-fingerprint layout and verification described below are retained.
See [AUTO_LOAD.md](AUTO_LOAD.md) for fresh-process and disk-I/O acceptance.

## Verified format (published 0.84.0)

PRQS3 stores a bounded disk fingerprint before the existing full/diff mode and
gzip payload. Each disk entry records its drive slot, write protection, byte
length and SHA-256 of the mounted file. Paths are not identities: an identical
copy in the same slot can match. Empty and occupied slots must match.

PRQS1 and PRQS2 are refused with an explicit unsupported-format message.
There is no compatibility mode or override, following the owner's 2026-09-19
pre-1.0 decision. The unchanged PRQR1 reference remains a compression dictionary,
not a loadable snapshot or disk backup.

Core holds one monitor across the native machine copy and creation of a disk
ticket. Mounts, ejects and write attempts use the same monitor and invalidate
the ticket before changing a disk. Fingerprinting uses positional reads on the
mounted handles on the worker thread, leaving guest file offsets intact.
Writable handles are synced before hashing. Any intervening disk change refuses
the capture; no partial snapshot is published.

Loads hash the current mounted disks on the worker thread before preparing a
notebook transition. Core requires a proof of a match when queuing the restore.
JNI checks that proof again with Core's monitor held through the actual native
restore, so a later write or mount cannot slip between verification and apply.
A mismatch preserves the running machine and its notebook.

This guards the application's mounted disk I/O. It is not a transactional HFS
filesystem, disk backup, or guarantee against device failure or arbitrary
external modification of a mounted file. Normal Mac shutdown and independent
disk backups remain necessary. A snapshot may be refused after shutdown or
reboot if those operations changed the disk.

## Decision: refuse mismatched disks

Use content fingerprints plus an in-process disk revision guard. This closes
the reproduced stale-RAM restore path while retaining the existing RAM diff
and quick-save history. It stores no disk copies and adds no disk hashing to
the UI thread or stopped native capture. Hashing and required sync do add worker
I/O; the measured timings below are evidence, not a guarantee of identical
latency on every disk or device.

Matched copy-on-write disks would make a different promise: rewinding guest
disk files with RAM, and retaining an overlay for every snapshot. This audit
does not justify adding that storage, recovery and retention system to the
existing snapshot feature. Refusal gives a bounded consistency rule and keeps
the owner's independent disk backups separate. It does not fix the HFS guest's
unflushed metadata after process death.

## Snapshot file durability

The gzip trailer is finished and flushed, then fsync is required while the
descriptor is open, before publishing the partial file. This applies to both
the reference and each snapshot. Sync failure leaves earlier saves in place
and never rotates history. The cached reference owns a copy of its bytes so
a caller cannot mutate the dictionary behind already-written diffs.

## Acceptance evidence

673 Java tests and the real Android preview codec check pass. Ten new disk/durability tests cover exact metadata
round-trips, unchanged guest file positions, same-size disk edits at the end of
the image, write attempts after verification, drive order/write protection,
ejection, stopped or foreign Core proofs, closed handles, malformed metadata,
per-diff fingerprints, complete gzip trailers before fsync, and failed sync.

The installed development build created PRQS3 with one writable 12 MiB disk.
A verified Quick load changed the selected character from Lara back to Arax
and reported actual native success. After the original game wrote a new
F97Guard save, the older quick snapshot was refused with the disk-mismatch
message. A copied PRQS2 fixture was refused with the unsupported-format
message. Both refusals preserved the game at its post-save prompt.

Observed emulator captures were 54–267 ms. The first quick save used 2220 ms
on the worker; the next used 1489 ms, and a later automatic save used 955 ms.
Hashing, compression and persistence remain off the UI thread. These figures
are from disposable emulators, not physical e-ink hardware.

## Disposable crash audit

The source image already has 118 reserved-catalog-field warnings from
Apple-derived fsck_hfs 540.1-Linux. A normal original-game quit followed by
Finder Shut Down closed every disk descriptor, set the clean-unmount flag,
and produced no additional checker diagnostics. This does not establish that
the source is free of every pre-existing issue.

One idle-game Android force-stop reproduced additional HFS metadata
inconsistencies: the clean-unmount flag was clear, and the checker reported
an MDB repair warning. Its debug output specifically identified invalid
drNxtCNID and drFilCnt, so this is not being dismissed as only an unclean flag.
No new allocation-bitmap or extent-tree diagnostics appeared in that trial.
The source image checksum remained unchanged.

After reboot, the original ExportProof save loaded with all six characters at
full HP in New Phlan, 15,1 W, 00:00. A second normal quit and Finder shutdown
set the clean-unmount flag, but the MDB warning remained. SHA-256 comparisons
of every data and resource fork in PoolRadSave matched the clean baseline.
This trial demonstrates persistent metadata inconsistency despite readable,
unchanged game saves; it does not demonstrate loss of those saves.

A valid PRQS3 container with a truncated native body also passed disk
verification and was refused, preserving the post-save guest prompt and
Notebook 2; the controller logged actual restore failure at 13:52:08 PDT.

On the released 0.83.0 baseline, a Quick save was captured before the original
game saved F97After. Quick load then accepted that stale state and returned to
exploration. The next original Save dialog no longer listed F97After. After
saving F97Rollback and shutting down normally, the offline HFS reader found
F97Rollback and the three original files, but no F97After. The checker reported
a new allocation-bitmap warning for orphaned blocks despite a clean-unmount
flag. The original input image checksum remained unchanged.

A fresh copy of the same clean baseline then received two original-game saves
without any snapshot restore. Both F97ControlA and F97ControlB remained in the
catalog, all five files read completely, the clean-unmount flag was set, and
the checker added no diagnostics. This separates the observed missing save
and orphaned blocks from the normal two-save path in this trial.
The disk fingerprint guard prevents stale RAM/disk restores; it does not
make abrupt process death safe for the original nontransactional filesystem.

Reusable tools:

- tools/audit-hfs-image.py always runs the checker with -fn, proves the image
  checksum is unchanged, and compares complete diagnostics with a baseline.
- tools/copy-stopped-test-disk.py requires the stopped app screen and closed
  disk descriptors before and after copying; it records the process and checksum.
- tools/crash-test-guest.py records the exact disposable emulator app process
  and its open disk before Android force-stop, then copies the disk only after process
  death is verified.
- tools/capture-test-screens.py records transient UI messages in a short burst.
- tools/start-test-sandbox.py validates the Mac II ROM and seeds its first-run
  selection before explicitly launching the fresh app.

ROMs, disk images, native states and audit captures remain private.

## Reproduction evidence

Private evidence retained under scratch/:

| Case | Checker evidence | Result beyond the common source warnings |
| --- | --- | --- |
| Normal shutdown | f97-clean-audit-v2.json | No added diagnostics |
| Idle hard quit | f97-crash-idle/audit.json; debug.log | Invalid drNxtCNID/drFilCnt; unclean flag |
| Recovery and normal shutdown | f97-recovered-audit.json | MDB warning remains; original save forks unchanged |
| Old snapshot, later write, normal shutdown | f97-unsafe-audit.json; f97-unsafe-fork-hashes.log | F97After absent; orphaned blocks |
| Two saves without restore, normal shutdown | f97-control-audit.json; f97-control-fork-hashes.log | Both saves retained; no added diagnostics |

The guarded build's mismatch and unsupported-format toast captures are in
f97-mismatch-toast/ and f97-legacy-toast/. Native body refusal and the settled
guest/Notebook 2 are in f97-native-refusal/ and f97-native-refusal-settled.png.
The final build log is f97-verified-build.log; preview acceptance is
f97-preview-check.log. The published universal 0.84.0 APK was installed through
normal shutdown, loaded ExportProof, created new verified quick/automatic saves,
and reported a successful restore at 14:10:18 PDT. See
f97-published-save-restore.log; all six README screenshots were recaptured from
that published APK. The original input checksum is retained in
f97-original-disk.sha256. These are local audit artifacts, not distributed game
assets or a promise that every failure mode has been exercised.
