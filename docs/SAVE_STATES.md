# Save states: real save/load outside the game

Owner's direction, 2026-09-19, and the new P0 line of implementation.

## Why

The game only offers *Load Saved Game* before a game begins. Reaching that point
from inside a running game meant restarting the emulated machine, and a restart
that did not come back cleanly wedged a real device badly enough to need a
force-reboot. Load was withdrawn (0.58.0).

The way out is to stop using the game's menus at all. Snapshot the whole
emulated machine and swap it back in later — no File menu, no restart, no way to
strand the machine. That is what an emulator save state is.

## The theory (owner's), and why it holds

A full machine snapshot is dominated by one thing: **8 MB of emulated RAM**.
Everything else — the 68k registers, the VIA, the Sony floppy driver, the RTC,
the SCC, sound, the screen device — is a few hundred bytes to a few KB. So a
naive save state is ~8 MB, which is why whole-machine save states were dismissed
before.

But this build only ever runs one thing. So:

- **Bake in one reference state R** — a fresh boot with the game running, at a
  known idle point. Compressed, this is ~1–2 MB in the APK.
- **A save is R plus a diff.** Minutes into play, almost all of RAM is identical
  to R — the Mac OS, the game code, untouched heap — so the difference
  compresses to tens of KB. That is the ~17 KB scale the game's own saves have,
  reached a different way.
- **Keep R's RAM resident** (a second 8 MB buffer) so a save diffs against it and
  a restore applies onto it without touching a file mid-operation.

Restoring: pause at a safe boundary, write R's RAM, apply the diff, restore the
small CPU+device blob, resume. The game never knows it happened.

## What is genuinely hard, stated plainly

**Not the diff, and not the space. The full-machine capture and restore.**

Mini vMac was not built for save states, and this fork has none. Every stateful
global across the device modules and the CPU has to be captured and restored
*consistently*, or the machine glitches or hangs on resume. Miss one pending
interrupt or timer and the resume is wrong. This is the bulk of the work and
where the risk lives.

Two things make it tractable:

- The non-RAM state is small and each device module keeps its state in a known,
  localized set of globals.
- Snapshotting always at the **same controlled boundary** — between frames, at a
  VBL, with no disk I/O in flight — means the device state is in a predictable
  configuration every time, not an arbitrary one.

## The sequence — do not skip Phase 1

1. **F90 — round-trip the whole machine — PASSED 2026-09-19.** The whole
   machine (RAM + CPU + VIA1 + VIA2 + RTC + timing + interrupt state, 8,388,816
   bytes) captures and restores. Two proofs on the running Mac II: the in-place
   self-test is byte-identical, and the behavioural test is decisive — saved at
   *Party at 0, 4 W*, walked the party to *15, 4 W*, restored, and the party
   snapped back to *0, 4 W* with the game still live (it went on to raise a
   random orc encounter, which only a running CPU can do). Serial and sound are
   deferred and did not stop the resume; whether ADB (keyboard) state needs
   adding will be settled when F92 gives a clean on-demand trigger to test
   repeatedly. **The make-or-break is behind us: the approach works.**

2. **F91 — the reference and the diff.** Bake R in, store saves as R + delta,
   compress. Purely a space optimization on top of a proven round-trip.

3. **F92 — save and load from the companion**, outside the game entirely: name a
   save, list saves, restore one. This is the feature the owner actually wants,
   and it cannot strand the machine because it never uses the game's menus and
   never restarts.

4. **F93 — pair each save with its notebook, by reference.** A companion save
   records *which* notebook and area it belongs with, rather than copying the
   ink. The notes and journals already live on disk (the notebook store); the
   save state soft-links to them. So restoring a machine state also brings up the
   right notebook, at ~no extra size.

## How this relates to the game's own save format

The record and save-file work already done (F77, F78, F82, F85) is **not
wasted and not replaced**. It serves a different feature: the converter (F81),
which imports parties other people saved on other platforms. That reads and
writes the *game's* format. This save-state line is for the player's own
save/load of their own session. The two coexist.

## Boundary

This is emulator-core work. The exclusions list rules out *emulator rewrites*;
adding save-state support is a feature addition in service of the companion, not
a rewrite, and the owner has named it the P0 line. A save state is inherently
tied to the machine configuration it was taken on — the same ROM and the same
emulator variant — which is fine for a single-purpose app, and is why the
reference R is baked per build.
