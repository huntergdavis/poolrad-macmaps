# Paused means idle

Android's existing pause/resume lifecycle now puts the emulator thread into an
untimed condition-variable wait. Backgrounding the app stops guest execution,
screen scans, audio and companion polling until foreground resume. There is no
new setting. The guest clock is rebased on resume using the existing time
adjustment, so time spent away does not become a burst of catch-up execution.

Explicit pending operations (snapshots, restores, read-only probes and shutdown)
wake the thread to service that request, then it sleeps again without advancing
the guest. Host controls publish through atomics; guest reset/interrupt flags
remain owned by the emulator thread. A generation ticket taken before checking
requests prevents a resume or request arriving just before sleep from being lost.
Spurious condition signals do not run another iteration.

Java records the latest pause/resume request even before native initialization.
Native startup applies that request before entering the guest loop. Fast
pause/resume pairs no longer depend on whether the native thread has already
acknowledged the preceding request. Helper retries stop while paused. The Wi-Fi multicast lock is retained by the
fragment and released on pause, then reacquired on resume; the old stop handler
created a different lock and therefore could not release the held one.

## Regression checks

Run `bash tools/test-emulation-wait.sh`: wake before sleep, wake during sleep,
spurious signals, sleeping-thread CPU time and 2,000 raced wake-ups. The sound and
snapshot native checks remain part of release validation.

## Live verification

Initial pause implementation checks used the 0.96.0 development build before
that version was claimed by the concurrent spell-help release. Tested on the owned API 30 x86_64 emulator, using a matching disposable guest
disk and snapshot. No instrumentation run or forced stop touched mounted disks.

- The 0.95.0 baseline accumulated 38 emulator-thread CPU ticks during a 13.55-second
  background sample, with the thread in the zero-duration sleep loop.
- Paused 0.96.0 during initial restoration. Outstanding startup work finished,
  then a settled **22.89-second** sample recorded **zero CPU ticks for the whole
  app**, **zero additional native-thread runtime**, and **zero context switches**.
  The emulator thread remained in `futex_wait_queue_me`.
- Read-only debugger status confirmed native initialization was still valid,
  lifecycle pause was true, all companion/automatic polling was off, AudioTrack
  was null and the Wi-Fi multicast lock was released.
- Foreground resume restored the game and notebook. A normal forward key moved
  the party from 15,4 W to 14,4 W; both the game view and companion map updated.
- Twenty rapid pause/resume pairs completed. Two explicit RAM requests while
  paused both returned TickCount **27381**, more than one second apart, proving
  pending requests are served without advancing the guest. Resume then succeeded.

A second **22.01-second** background sample started with the game's Sounds
enabled. AudioTrack was released; emulator runtime, CPU ticks and context
switches again stayed unchanged. Android's Profile Saver performed brief runtime
housekeeping (5 of the process's 6 CPU ticks); there was no recurring guest,
audio or companion work. Foreground status before pausing showed an active
AudioTrack, polling and multicast lock. After the second resume all three were
active again, and a normal turn updated the companion to 14,4 N. The guest then
quit and shut down normally; its disk descriptors were confirmed closed.

Reproduce the settled measurement after pressing Android Home:

```sh
python3 tools/paused-activity.py emulator-5590 20
bash tools/debug-ui.sh emulator-5590 CoreStatus
```

With the app foregrounded, `bash tools/debug-ui.sh emulator-5590 PauseActivity`
checks rapid lifecycle requests and paused RAM delivery. These helpers are
restricted to emulator serials. The RAM probe uses the existing debug capture;
it does not write guest memory.

All **736 Java tests**, the native wait test, **25,600 sound-state comparisons**,
native snapshot checks, APK ABI/content checks and signature verification pass.


### Final 0.97.0 package

After rebasing onto the concurrent release, the pause implementation was
byte-for-byte unchanged at source. Rebuilt and installed versionCode 163 /
0.97.0, reran all 736 Java tests and the APK checks, and repeated the live checks:

- Twenty rapid pause/resume pairs passed again. Paused RAM replies both held
  TickCount **28138**.
- **22.77 seconds** backgrounded: **zero emulator CPU ticks**, **zero runtime
  nanoseconds**, **zero context switches**; the thread remained in the futex wait.
  Android GC/finalizer housekeeping accounted for the process's nonzero ticks.
- Resume accepted a normal forward step and the game and companion map showed
  **14,4 W**, with **v0.97.0** visible in the map footer.
