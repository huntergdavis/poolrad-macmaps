# Informational message auto-skip (F31)

The companion Options dialog has **Auto-skip informational messages**, off by
default. The preference persists and applies immediately. Story/tutorial
Continue prompts, choices, confirmations and unrecognized dialogs stay manual.

## Detection

This reuses the supported Macintosh v1.1 application/loaded-CODE guards from
[the map reader](MAP_MEMORY.md) and the bounded event-wait stack reader from
[automatic idle](AUTOMATIC_IDLE.md). The [message text reader](MESSAGE_MEMORY.md)
is useful for display, but text or a lone Continue/OK button cannot establish
whether acknowledging a prompt will take a game action.

The native reader therefore permits only these researched callers:

| Notice | CODE segment and call offset |
| --- | --- |
| Numeric input outside the permitted range | CODE2 +0x1a12; CODE4 +0x333a |
| Empty or overlong character name | CODE3 +0x3444, +0x345a |
| Character departure acknowledgement | CODE7 +0x4248, +0x425a, +0x427e |
| Awarded experience / NPC share reports | CODE9 +0x5d42, +0x6618 |

Classic alerts must be inside CODE14's Alert(10000) wrapper, returning to
+0x1fde, with a null filter and the exact formatter/printf caller chain.
Character creation keeps A5−0x30e1 set even in its valid name-entry alert,
so alert eligibility depends on the proven caller chain, not the world-entry
startup flags. The live name-required capture resolved the alert frame at
0x78a3d8 and CODE3 return at 0x6cd5bc; the native test accepts that actual RAM
capture as well as synthetic stacks. The original DITL10000 has an OK button and static message text. Confirmation
DITL10004 has OK and Cancel and does not qualify. Save/load failures sharing
the alert formatter are not on the allowlist.

The two reports must be inside the CODE6 menu's actual untimed event wait,
with one Continue choice, and must return to the specified CODE9 callers.
Their remaining instructions clear the report window and return; experience
and shares are already assigned. The loot-selection menu stays manual.
CODE5's story interpreter call at +0x1a44 is excluded even when its button is
also Continue: advancing it can move the party or run another script action.

Loaded jump-table entries resolve relocated CODE bases. Signature checks,
range checks and a bounded ascending frame chain reject unfamiliar states.
No guest bytes, resources, disk data or program instructions are patched.

## Input and lifecycle

A notice needs positive observations at least 350 ms apart. Only a fresh
positive match can send one ordinary Return press from the emulator thread;
a still image or temporary event-stack gap cannot send input. The key is
released after 80 ms and repeat acknowledgement is suppressed while that
notice remains active. Player input, disabling the option, backgrounding,
reset/shutdown commands and save-state restoration cancel pending input and
release a synthetic key without releasing a physical Return the player holds.
There is no new polling timer, and automatic idle remains independent.

## Verification

The standalone sanitizer test exercises allowed and protected callers,
truncated/cyclic stacks, read-only behavior, settling, duplicate suppression,
and cancellation before and during a key press:

```sh
bash tools/test-auto-skip.sh
bash tools/test-automatic-idle.sh
```

On emulator-5590 (API 30, 1200×1600), the functional checks ran on
0.103.0 / code 168. After the version-only bump, code 169 was installed
and the isolated off/on check repeated:

- The option was initially unchecked. Enabling it persisted across installation
  and restart. The original name-required informational alert cleared and the
  underlying name-entry form remained open.
- Turning the option off through Options and requesting the same empty-name
  validation left the alert visible after six seconds, with no native
  acknowledgement. Turning it back on cleared that existing alert with exactly
  one logged acknowledgement at 00:04:13 PDT on September 20. The underlying
  name form stayed open without repeated input.
- The pre-game snapshot also triggered the existing startup Return helper;
  background/resume stopped that helper before the isolated off/on comparison.
  This did not change the auto-skip implementation or its eligibility rules.
- The original icon OK/Cancel confirmation remained open with the option on.
  Rolf's introduction and Continue button also stayed at New Phlan 15,1 W
  through an untouched 18-second observation, with no acknowledgement sent.
  One manual Continue then moved the party to 11,2 S; the next story prompt
  again waited for the player.
- The integrated build passed all 742 Java tests, both native sanitizer suites,
  the real captured alert stack, and the APK ABI/resource checks.

The final code 169 check again left the informational alert visible for six
seconds with the preference off. Enabling it then cleared that alert with one
acknowledgement at 00:14:25 PDT on September 20; the name-entry form stayed
open. Screenshots: scratch/f31-final-off-verified.png and
scratch/f31-final-on-verified.png. Earlier protected-prompt evidence includes
scratch/f31-story-still.png and scratch/f31-confirmation-still.png.
No game assets or private snapshots are shipped.

After integrating the upstream removal of the retired restart-based loader,
the final APK rebuilt successfully and all 732 remaining Java tests passed.
The message-skip implementation was unchanged.
