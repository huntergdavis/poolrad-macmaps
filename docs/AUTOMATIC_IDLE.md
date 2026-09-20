# Automatic foreground idle (F102, 0.100.0)

The supported Pool of Radiance v1.1 guest sleeps after five quiet seconds in a
positively identified, untimed player-input wait. The notebook remains usable,
with no switch or paused indicator. Note strokes do not wake the guest. The
first game key or mouse event wakes it and is delivered normally.

## Evidence for waiting on the player

This extends the read-only game identity/global checks in [MAP_MEMORY.md](MAP_MEMORY.md)
and the untimed native condition wait in [PAUSED_IDLE.md](PAUSED_IDLE.md).
A still screen alone is not sufficient: rest timers and automatic combat must
continue even when the player is reading.

Disassembly of the user-supplied game's CODE resources identifies these paths:

- CODE 6's menu reader starts at 0x06ba (LINK A6,-104), loaded through A5+0x4c0.
  At 0x0be6 it calls A5+0x2d2. At 0x0bea it removes the argument, stores the
  returned character at -1(A6), and repeats while that character is zero.
  This loop has no timeout. Its done flag at -4(A6) must also be zero.
- CODE 2's event reader starts at 0x7462 (LINK A6,-76), loaded through
  A5+0x2d0. Its returns from WaitNextEvent and GetNextEvent are 0x753e and
  0x7554. The active 68k A6 chain must contain one of those returns, and that
  event-reader frame must return directly to the menu's 0x0bea null-input loop.
  A timed caller, action handler or automatic fighter does not match.
- The detector also requires the original application name, loaded jump-table
  signatures, matching code bytes, engine 2–5, menu state 2 and no startup,
  load continuation or pending input. It uses CurrentA5 at low memory 0x904,
  since System 7 may have switched the CPU's A5 while servicing events.

All reads are bounded; the even-addressed frame chain must increase and stops
at 24 frames. Unknown code, unsupported contexts or an unproven wait keep
running. No game instructions, state or timers are patched.

A private saved live tavern fight independently validates the signature:
CurrentA5=0x790aec, CPU A6=0x78a4c4, loaded menu entry=0x6b856a,
event entry=0x69b40e. Frame 0x78a558 returns to 0x69b4ea; its parent
0x78a5bc returns to 0x6b8a9a; the menu frame is 0x78a62e. Both menu
locals are zero. The test reads the captured RAM without modification; the
copyrighted game, snapshot and RAM are not included in this repository.

## Sleep and wake

Guest input, held keys/buttons, disk transfers and active sound postpone sleep.
Screen redraws alone do not: CODE 2 at 0x74a4–0x7524 cycles animation frames
inside the proven input wait. Such redraws do not establish game progress;
the verified untimed caller is the deciding evidence. Mono sound ignores the unused B FIFO. Once asleep,
CPU execution, screen scans, audio and recurring map/wheel/helper/autosave
polling stop. A final read refreshes the companion without advancing the guest;
its last readings stay visible, and the multicast lock is released.

Input publishes activity atomically and signals the existing generation-based
condition wait. Wake has no timer/polling delay and retains the triggering
input. Native time is rebased, so reading does not cause catch-up execution.
Reset, restore and successful companion game-state actions also wake. Explicit
read-only probes and snapshots can be serviced while remaining asleep.

The existing boot-dialog helper stops when a party is positively identified.
A restored fight has no exploration-map snapshot; using that absence as a
boot signal sent six synthetic Returns and delayed sleep by about 30 seconds.

Automatic idle is separate from Android lifecycle pause. Backgrounding still
wins over foreground input; foreground resume restarts activity and polling.
Autosave keeps its original due time while sleeping and checks on wake if due,
so repeated idle periods do not postpone protection indefinitely.

## Verification

On the owned API 30 x86_64 emulator, with matching disposable disk/snapshot:

- 0.98.0 baseline, note open: 16.40 seconds used 1,010 emulator CPU ticks,
  10,202,988,555 runtime nanoseconds and 761 context switches.
- Candidate, same scene: automatic idle entered five seconds after restoration.
  A settled 17.76-second foreground reading sample had zero CPU ticks for the
  entire app, zero emulator runtime and zero emulator context switches.
- A new diagonal pen stroke appeared and saved in the open grid note while the
  guest remained asleep. A subsequent 12.48-second sample again had zero
  emulator ticks/runtime/context switches (five Android housekeeping ticks).
- One normal forward key woke and moved the party from 15,4 W to 14,4 W.
  The open note stayed in place; no second input or pause control was needed.
- Enabling the original Sounds option still allowed automatic idle. Read-only
  status showed lifecycle pause false, automatic idle true, AudioTrack null,
  map/wheel polling false and multicast lock released.
- Background/foreground from automatic idle resumed cleanly. A normal right
  turn updated both the original game and companion to 14,4 N.

Run bash tools/test-automatic-idle.sh for sanitized positive-wait,
unknown/timed-context rejection, bounded-stack and quiet-time checks. An
optional private RAM path and A6 value validate a real captured stack.
The audio native test covers mono/stereo idle eligibility plus the existing
25,600 muted/audible device-state comparisons. The emulation wait test
covers pre-wait/blocked wake, spurious signals, idle CPU and 2,000 wake races.

### Combat and integration checks

The 0.100.0 candidate's settled combat prompt recorded **13.58 seconds with
zero app CPU ticks and zero emulator runtime/context switches**. A single
Done click woke and opened the original action menu. Guard then allowed the
enemy phase to run: the game showed a Corporal acting, combatants moved, and
Shara became the current character. The original game and companion agreed.
Automatic idle returned only at the new player prompt.

All 739 Java tests passed after integration with the concurrent 0.99.0 release.
Native sound, wait and snapshot checks passed, as did APK ABI/content and
signature verification. Guest shutdowns were normal; disk descriptors were
checked closed before every package replacement or fixture restore.

### Final package

Installed versionCode 166 / 0.100.0 including the boot-helper guard. The
exploration save restored at 23:03:40 and automatic idle began at 23:03:45.
During a **17.06-second** measurement, a new horizontal pen stroke was drawn
and saved in the open note: emulator CPU ticks, runtime nanoseconds and
context switches all stayed at **zero**. Android rendering and note persistence
still ran as expected. The next ordinary forward key woke the guest and moved
15,4 W to 14,4 W, with the written note still open. Read-only status afterward
confirmed foreground automatic idle, no AudioTrack, no map/wheel polling and
no held multicast lock. The final build and all 739 Java tests completed
successfully; its APK content, ABI and signature checks passed.
