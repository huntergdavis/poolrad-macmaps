# Automatic wheel recognition — Macintosh v1.1

The wheel reader follows the original game's current input call. It does not
guess runes, scan screen pixels, patch validation, or send a hidden bypass word.
The separate manual illustrated helper remains available for unsupported states.

## Observed profile

Private captures and disassembly of the supplied Macintosh executable establish:

- `CurApName` at `0x910` must be `Pool of Radiance v1.1`. `CurrentA5` is read at
  `0x904`; heap, code, and stack addresses are resolved afresh.
- A5 minus `0x248` contains thirteen 12-byte records: the actual accepted
  word pointer, four glyph bytes, and the path-string pointer. The game selects
  an index in 0–12; this is not the public illustrated wheel's rune numbering.
- The wheel function stores its selected index at its own A6 minus `0x2b`
  and attempt counter at A6 minus `0x2c`. Its input routine receives a prompt
  pointer, limits, and an output-buffer pointer; after return the game compares
  that output with the selected record's word.
- A current CPU A6 frame-chain walk, bounded by A7, validates the input call's
  exact instruction context, arguments, destination address, prompt literal,
  attempts, and selected answer. Finding matching bytes elsewhere in RAM is
  insufficient. A frame that has returned cannot authorize input.
- **The wheel output buffer is not the live edit field.** It is copied only
  after Return. While input is active, the input routine's A6 minus `0x108`
  points to the DialogRecord. Dialog offset 160 holds its TextEdit handle;
  the dereferenced record has text length at 60 and text handle at 62.
  That handle must match the game's item handle at A5 minus `0x5f4e`.
- The modal-loop byte at the input A6 minus `0x119` must be 1, item kind at
  minus `0x118` must be 16, and input cap at A5 minus `0x5f4a` must be 40.
  Both TextEdit selection positions at 32/34 must equal the current length:
  samples do not authorize appending into a selection or a moved caret.

The native reader returns an 80-byte `PRW1` packet or unavailable. It samples
small bounded records between emulated CPU ticks, not recurring full RAM dumps.
Java rejects malformed packets. Automatic entry must start only from stable,
empty, verified input, read back each typed prefix, submit Return only for the
complete exact answer, and cancel on changed state or manual input. Normal
emulated key events are the only game interaction.

## Evidence and limits

The old private startup capture identifies selected index 1, `ZOMBIE`; both
preserved in-game captures reject that wheel frame. A fresh normal guest
quit/relaunch on 2026-09-13 selected index 9, `SAVIOR`. Before Return, the
screen and a second capture both show `SAVIOR` in TextEdit while the wheel's
deferred output buffer remains empty. The corrected native reader reports
the two states as `typed=""` and `typed="SAVIOR"` respectively.
Submitting that verified word with a held normal Return cleared the prompt and
exposed the original game's **Load Saved Game** menu. No guessed answer or
validation patch was used. This confirms the selected-word interpretation;
it is distinct from the automatic-entry acceptance below.

Absolute addresses in those captures were A5 `0x76b2a8`, input A6 `0x764de0`,
wheel A6 `0x764e22`, and input return `0x679f3c`; these are evidence, not fixed
addresses used by the reader. Native synthetic tests cover relocated frames,
stale chains, wrong app/arguments/code, bounded text and pointers, loop state,
caret changes, and the deferred-buffer regression under address/undefined
sanitizers. Java packet tests cover typed-prefix tokens and invalid records.

## Live automatic acceptance (2026-09-13)

The integrated Mac II APK built for all four ABIs, passed 149 Java tests, and
was installed without clearing data on the local Android API 30 test emulator.
After a normal cold boot and original-game launch, the app detected the live
wheel call through the **current CPU registers**, read back each typed prefix,
and delivered all six letters followed by one Return. The local dispatch log
records prefix lengths 0–5 at 22:46:08.635–22:46:10.474 PDT and the final
confirmed-length-6 Return at 22:46:10.881. No external letter or Return supplied
any part of this answer. The original game's enabled **Load Saved Game** menu
then verified acceptance (`scratch/wheel-final-accepted-menu.png`).

An earlier live attempt correctly recognized `DRAGON` but stopped at `DRAG`
without submitting. Read-only debugger inspection showed a valid `DRAG` sample
while the controller expected `DRAGO`. The caller had advanced its expected
prefix before checking whether the previous key was still held, allowing a
new key to be discarded. The production `observeIfReleased` gate now prevents
that state advance; a regression test exercises held-key readbacks after every
letter and before Return. The successful acceptance above used this correction.

An independent single-disk cold boot on emulator 5582 also completed six letters
and Return automatically at 23:12, followed by a successful sample-party load;
see [the boot evidence](PERSONAL_BOOT.md). These are two automatic startup
instances, plus the separate manual `SAVIOR` validation. The answer indices were
not recorded for both successful runs; the additional checks below close that
distinct-answer and same-process quit/relaunch gap. Other game versions,
physical e-ink hardware, and all possible device timing conditions are not
validated by these checks. RAM captures, extracted executable bytes, ROMs,
disks, and diagnostic screenshots remain private.

## 0.5.1 — distinct answers and same-process relaunch

On 2026-09-13, the all-ABI Mac II candidate (versionCode 68) passed **152 Java
tests**, all three native sanitizer suites and the APK artwork/asset check.
It was installed in place on emulator 5580, API 30, at normal 1× guest speed.
The existing note and its legacy backup remained byte-identical after updating.

| Original-game launch | Verified index / answer | First letter → confirmed Return (PDT) | Result |
| --- | --- | --- | --- |
| Finder → Open after guest boot | 10 / TEMPLE, attempt 1 | 23:41:03.793 → 23:41:06.685 | Load Saved Game enabled |
| Normal game Quit → confirm → Finder → Open | 0 / BEWARE, attempt 1 | 23:43:54.533 → 23:43:56.897 | Load Saved Game enabled |

Both runs used Android process **16292**, without resetting the emulator core
between game launches. Each log contains prefix lengths 0–5 followed by exactly
one length-6 Return. No external letter, Return, debugger write or validation
patch supplied either answer. The original game's enabled menu, not merely the
dispatch log, establishes acceptance. Private evidence:
`scratch/f4-live-wheel.log`, `scratch/f4-temple-accepted-menu.png`,
`scratch/f4-same-process-finder.png`, `scratch/f4-second-accepted-menu.png`.

After the second acceptance, normal File → Load Saved Game → PoolRadSave →
SampleParty reached Rolf's introduction at New Phlan 15,1 W, with all six
current/max HP rows and the existing Temple flag visible. No additional wheel
keys were dispatched during reload (`scratch/f4-sample-reloaded.png`).

Debug builds now include the verified prompt index and attempt alongside prefix
lengths. They do not log arbitrary typed text or dump memory. Three focused
controller tests cover all thirteen distinct answers without controller reset,
relaunch with the exact same answer/addresses after sustained absence, and
transient missing samples after submission without another Return. These are
bounded regressions, not a long-running device matrix. Physical tablet/e-ink
timing and unsupported game profiles remain untested; the manual illustrated
offline helper is still available.
