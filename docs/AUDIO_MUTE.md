# The original game's sound switches

Use **Options → Sounds** and **Options → Walking Sounds** in the original
Mac game. There is no companion sound switch and no new default preference.

**Sounds** is the game's master switch: turning it off now also releases the
Android audio stream and skips host sample generation, volume conversion and
native-to-Java audio transfers. **Walking Sounds** filters footsteps while
Sounds remains on; other effects still work. This follows the game's existing
rules, including when its walking checkbox remains checked with Sounds off.

The emulator reads the verified v1.1 preferences once per emulated tick.
It never writes guest RAM or preferences. Before a verified game setting is
seen, audio behaves normally. Once observed, the game’s choice stays in force
through System 7 background-process slices and other unknown states until a
verified game option changes it or a new emulator session starts. No cached
guest pointer is read. Pause releases the stream too; resume and snapshot restore
recheck the game flags before generating audio. Turning sound back on creates
a fresh stream and resets the output buffer, so muted audio cannot replay.

The ASC retains only guest-visible state while muted: FIFO consumption,
status/interrupts and waveform phase advance in bulk, without reading sample
data or looping over samples. Classic sound skips its output-only synthesis.
Sound-device snapshot fields and the snapshot format are unchanged.

## Evidence for the original controls

The prior-session search `deja "Walking Sounds"` located the earlier menu
inventory, recorded in [Guest menu keys](GUEST_MENU_KEYS.md) and
[Local testing](LOCAL_TESTING.md). This work reused that identification of
the original Options menu, then verified its implementation and live flags.
The background-process observation-gap handling reuses the finding documented
in [Map memory](MAP_MEMORY.md): System 7 temporarily swaps the game’s low-memory
application name and A5 for Finder’s while the game remains on screen.

In the local v1.1 executable, CODE2 +40a0 and +40ae toggle bits 0 and 1 of
the option word at CurrentA5 -0x6228 (low byte -0x6225). CODE2 +41c6 and
+41ea draw the two checkmarks; set bits mean unchecked/off. CODE2 +58bc
checks bit 0 for every effect and bit 1 additionally for footsteps, sound 10.
The native reader checks the application name, A5 alignment/range and known
four-bit option word; no executable bytes or private RAM are distributed.

Live captures confirmed option words 15 (both off), 14 (Sounds on, walking
off) and 12 (both on), matching the original menu checkmarks. Before this
change Android reported an active 22,255 Hz AudioTrack while both were off.

## Regression checks

Run `bash tools/test-audio-native.sh`. It compares all serialized chip state
and interrupt counts across 25,600 audible/muted combinations: silent mode,
mono/stereo FIFO thresholds and wraparound, underrun, mono fallback,
four-channel waveform phase overflow, volume levels and every subtick.
Muted runs request zero sample buffers. Synthetic option-reader fixtures also
cover all menu settings, wrong applications, invalid pointers and read-only
behavior. Scheduling-gap fixtures keep the last verified mute through repeated
Finder/invalid observations and unmute only on a verified game setting.
No game data is included.

## Live verification in 0.95.0

Tested the installed universal APK on the owned API 30 Android emulator,
using the original game's menu and normal game keys. The party visibly turned
and walked from Slums 15,4 to 14,4; no guest memory was modified by testing.

| Ten-second observation | Output callbacks | Samples transferred | AudioTrack |
| --- | ---: | ---: | --- |
| Both game options off after cold restore, with a turn key | 0 | 0 | absent |
| Both options on, with a walk key | 114 | 58,368 | present |
| Sounds turned off after playback, with another turn key | 0 | 0 | absent |
| Both options off after Android background/resume | 0 | 0 | absent |

The enabled run establishes that the output path resumes; captured samples in
that interval were silence, so this is not an audible-fidelity claim.
Android AudioFlinger independently showed zero active tracks when muted.
Native sample-generation and transfer counters supplement the Java callback
observations; logging occurs only when the game setting/output state changes.
Across 98 seconds muted, the totals stayed exactly at 1,703,480 generated
samples and 3,327 transfers, including toggling Walking Sounds off and
background/resume. Re-enabling Sounds created a stream with those same
totals before new audio began.

The first live trial caught a brief unwanted stream restart every five seconds
when System 7 switched to Finder's world. A capture at that transition proved
the cause. Retaining the last verified game choice removes those restarts;
the final cold restore stayed at zero generated samples and zero transfers
until Sounds was explicitly enabled.

All 736 Java tests, the sanitizer-backed audio state comparisons, existing
native snapshot tests, universal-ABI/content checks and APK signature
verification pass. Private RAM captures remain in ignored scratch files.

To repeat the read-only callback observation on a disposable test emulator:

    bash tools/debug-ui.sh emulator-NNNN AudioActivity

An optional final argument (for example, 8) sends one normal game key through
the existing input path while observing. The helper does not edit RAM or
create an audio stream.
