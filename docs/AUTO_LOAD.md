# Automatic launch restore

F34 — delivered in 0.85.0.

The first emulated guest tick waits for one launch restore attempt. Disk files
are already mounted, and hardware initialization has completed, but no guest
instruction can alter the disk before F97 verification. The UI and worker
remain responsive. Start normally cancels before application; a 30-second
deadline also releases a stalled verification. Late callbacks cannot apply a
cancelled attempt. The native event loop still services the queued restore
while the guest is held.

The launch attempt uses the most recently completed quick, named or automatic
snapshot. A synced, atomically replaced relative-path record preserves ordering
across wall-clock changes. Existing saves without that record use the browser's
order. Deleting the remembered file falls back to the remaining browser list.
A corrupt newest snapshot is refused; the loader does not quietly select an
older game. The marker cannot point outside the save directory.

Manual and launch loads share the disk-verification and notebook transaction.
Launch loading waits for notebook initialization, then requires an existing
binding. It never creates a campaign automatically. Missing pairings require a
manual load. Any refusal continues normal boot without resetting the guest.
The Options toggle defaults on and applies when the app starts; background
resume and an explicit Restart Emulator do not repeat the launch attempt.

## Cross-process machine-state correction

The first disposable process-restart trial used the previous native snapshot
body. Its disk fingerprint matched exactly after process death, and the native
callback reported success, but the restored color screen was interpreted as
monochrome garbage. A success callback and readable party RAM were insufficient
evidence of a working resumed guest.

State review found that video mode/palette, Sony driver state, ADB transactions,
sound and serial registers were omitted. The prior in-process round-trip could
not detect fields absent from both captures. The new body adds those fields and
IWM state, captures the interrupt level, and rebuilds memory-map tables before
restoring CPU fetch pointers. It checks the machine model and ROM checksum as
well as the complete body length.

PRQS4 carries this native PRSS3 body and the existing disk fingerprint. PRQS1,
PRQS2 and PRQS3 are explicitly unsupported, consistent with the owner's pre-1.0
decision. There is no attempt to invent the missing hardware state in older
snapshots. Original-game files and the PRQR1 compression dictionary remain
separate.

## Evidence and remaining checks

Eight tests cover startup cancellation/commit boundaries, separate Core
attempts, latest named/automatic save selection across clock rollback, initial
fallback and deletion, corrupt-newest refusal, path confinement and failed
marker publication. The first 681-test build passed before the expanded native
body. First-launch rejection of an old format continued to a normally loaded
original game.

The failed process trial is retained privately in scratch/f34-first-restart,
f34-first-launch-state.prqs and f34-first-relaunch-screens. The failed guest was
stopped with tools/crash-test-guest.py, preserving its disk separately. Original
input disks were not changed.

The acceptance checks below cover the corrected PRQS4 build, fresh-process
video/input/disk I/O, native rejection and launch fallbacks. The changed Options
and snapshot README pictures are recaptured from the published APK.


The second trial (scratch/f34-prqs4-restart) again verified exact disk identity
and returned a successful native callback, but still displayed monochrome
garbage. Inspection found that OSGLUJNI does not include CNFUDPIC.h: all its
conditional device calls were compiled out. This also affected the previously
existing VIA and RTC calls. Device traversal now lives in model-aware GLOBGLUE.
The production traversal regression requires all nine Mac II devices exactly
once; packet tests check new-process buffer ownership, aliasing and bounds.
Run both with bash tools/test-snapshot-native.sh. The failed process was stopped
and its disk retained in scratch/f34-prqs4-failed-stop. No release is claimed
from either failed trial.


## Successful complete-device trial

At 14:52:26 PDT on 2026-09-19, new process 17384 automatically restored the
most recently completed named LaunchComplete snapshot, following an earlier
quick snapshot with Arax selected. The named snapshot selected Lara and was
paired with Notebook 2. The stopped disk matched its exact SHA-256 fingerprint
(e0343527c4cdc63089defd04105d0ab59cff5d15d691dca8c53131f7255aa1ad).
The rendered guest showed the correct color scene, Lara in the original
Information window, and the existing notebook. Real guest clicks then selected
Arax and Look advanced the game clock from 00:00 to 00:10. This demonstrates
running guest execution after a new-process restore, beyond callback success.

Private evidence: scratch/f34-complete-store, f34-complete-restart,
f34-complete-relaunch-screens, f34-complete-after.png and
f34-input-after-restore. The build passed all 681 Java tests, and
tools/test-snapshot-native.sh passed both production traversal and packet
ownership/bounds checks under AddressSanitizer and UndefinedBehaviorSanitizer.
Original-game save I/O and launch-fallback results follow below.


The real manual-load path refused a wrong model header (14:54:43), wrong ROM
header (14:55:10), and internally consistent truncated body (14:55:36), with
native completion false in each case. These valid PRQS4 containers used the
matching disk fingerprint and existing notebook binding, so refusal occurred
inside native preflight. Arax remained selected at 00:10 with Notebook 2.
The fixtures and before/confirmation/after screenshots are retained privately
under scratch/f34-rejected-states and f34-reject-{model,rom,short}.
tools/MakeRejectedState.java generates these cases; tools/load-test-snapshot.py
drives the exact visible app controls and checks a new completion from the same
process. A subsequent Home/foreground cycle reported HOT and preserved the
same Arax/00:10 session, with no repeated launch restore.


The resumed guest wrote a new original-game F34Resume save through camp/Save,
then quit and shut down through Finder normally. The closed disk copy contains
all 12,906 data-fork bytes and 4,610 resource-fork bytes of f34resume. All four
earlier game files retain identical complete fork hashes. Read-only fsck.hfsplus
reported the clean-unmount flag and no added diagnostics relative to the
pre-restore disk (the baseline already has catalog warnings). Evidence:
scratch/f34-normal-shutdown, f34-resume-disk.dsk.json, f34-resume-forks.log,
f34-before-forks.log and f34-{before,after}-audit.json.

A new Android process after that verified shutdown logged at 14:59:23:
Auto-load skipped because the mounted disks differ from the snapshot.
Normal guest boot continued. This protects the newly written F34Resume file
from an automatic stale-state restore. tools/restart-stopped-test-app.py
requires the stopped guest UI and closed disk descriptors before cold-launching.


## Launch fallback and cancellation acceptance

- No snapshots: on emulator-5588, the prior test states were moved aside
  reversibly while the process was stopped. A new process logged No automatic
  launch restore and booted normally. The prior states remain preserved under
  files/f34-preserved-savestates on that disposable emulator.
- Disabled: the real Options checkbox was verified default-on, switched off,
  and checked in persisted preferences. A fresh snapshot exactly matched the
  disk after verified process death. The next process made no startup restore
  attempt and booted normally. The option was then restored to on.
- Missing pairing: a newly captured snapshot's own pairing was moved aside.
  Its disk fingerprint matched exactly after verified process death. Startup
  logged notebook unavailable and booted normally; the notebook count stayed
  at three, and the test pairing was restored afterward.
- Cancellation: LaunchCancelCheck found and clicked the real Start normally
  button during a matching-snapshot launch. The app acknowledged Automatic
  load skipped; no native launch restore followed, including after the
  30-second deadline. The corrected UI test passed in 11.569 seconds.
  Unit tests cover cancellation before the first tick, cancellation during
  verification, late application refusal and the committed-apply boundary.
- The original game loaded the new F34Resume file after disk-mismatch fallback:
  six healthy characters, Arax selected, New Phlan 15,1 W, 00:10.

Private evidence includes scratch/f34-no-save-start, f34-disabled-restart,
f34-disabled-startup.log, f34-unbound-restart, f34-unbound-startup.log,
f34-resume-readback, f34-cancel-final-restart and ui-test.qKKeKC/result.log.
The earlier UI runner attempt aborted before executing a test but printed an
OK footer; it is not counted as a pass. The reusable runner rejects aborts and
uses the [AOSP simple reporter](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/android-31/+/refs/heads/androidx-savedstate-release/com/android/uiautomator/testrunner/UiAutomatorTestRunner.java).
UI click return values are supplemented by the app's actual cancellation
acknowledgement, since the legacy event-wait can report false after a successful
click. The test asserts that no native restore was applied.

## Reusable checks

Run native regressions with bash tools/test-snapshot-native.sh; release.sh
runs them before changing versions. The production coordinator must visit all
nine Mac II devices exactly once. Packet tests verify ownership in a new
process, receive-buffer aliasing and rejection of oversized metadata.

tools/check-test-snapshot-disk.py compares PRQS4 against a verified stopped-disk
audit. tools/load-test-snapshot.py checks actual native load completion from
visible app controls. tools/restart-stopped-test-app.py requires the stopped
guest screen and closed disk descriptors before cold launch.
tools/set-test-option.py changes a visible companion checkbox.
bash tools/ui-test.sh SERIAL LaunchCancelCheck runs the startup cancellation
test after its documented matching-snapshot crash-audit precondition.

Snapshots still do not restore disk contents or make arbitrary hard quits safe.
Use normal guest shutdown and independent disk backups.
