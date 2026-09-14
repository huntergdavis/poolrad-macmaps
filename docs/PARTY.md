# Read-only party health

Scope: the supplied **Macintosh Pool of Radiance v1.1**. Read the guest's
ordered party names and current/maximum HP; do not change stats, automate
combat, guess mana, or treat a party member as a tactical map position.

## What is proved

The offsets below were checked against the original Macintosh executable in
the user's private RAM captures, not assumed from equal full-health values.
Addresses in this evidence are from one loaded instance and are **not** used
as fixed addresses by the implementation.

| Field | Mac location | Independent executable evidence |
| --- | --- | --- |
| Party head handle | Current A5 − 20,894 (`−0x519e`) | Append routine at `0x6716c2` initializes this slot for an empty list and walks it to append members. |
| Selected member, **not** the head | A5 − 20,898 (`−0x51a2`) | Routine at `0x671b76` copies the head into this slot, advances it through the list, then resets it. Starting here would omit preceding members. |
| Next member handle | Dereferenced record + `0x110` | Append routine follows this field until null; party rendering at `0x673cd8` follows it in displayed order. |
| Name | Record + `0x00`, 16-byte field | The party renderer uses the record start as a NUL-terminated string; the captured names confirm the representation. |
| Current HP | Record + `0x12b`, unsigned byte | Guest HP formatter zero-extends this byte before decimal formatting; healing changes this field. |
| Maximum HP | Record + `0x32`, unsigned byte | Healing clamps current HP to this byte; level-up increases this byte and preserves the previous missing-HP difference. |
| Party slot / monster group | Record + `0xc9` | CODE 2 `+0x2f52..0x305c` assigns unique party slots 0–7. CODE 5 starts monster groups at 8 and appends them to the same list. |

The append routine builds eight occupancy slots at `0x671714`, accepts slot
numbers 0–7 at `0x671746`, and stops at eight at `0x6717a2`. The reader therefore
accepts **one through eight** members, including lists not equal to the sample
party's six. Null, not a guessed party count or name, terminates traversal.

### Combat uses the same list

The first Slums orc encounter appends ten ORCs after the six heroes. Rejecting
every list longer than eight therefore hid the health strip during combat.
The slot field above distinguishes actual members from nonparty combatants;
names, HP values, and the selected combatant do not establish membership.

CODE 5 `+0x2d56` starts the monster-group slot at 8; `+0x28b2` / `+0x290e`
write it to each new record, and `+0x2ac6` advances the group. The monster
counter is capped at 63 by `+0x27ae` / `+0x2abe`. The reader walks at most
71 links (eight members plus 63 nonparty combatants), validates every record's
bounds, heap size and unique handle/address, and emits only slots 0–7 in linked
order. Duplicate party slots and the transient unassigned `0xff` marker fail
closed. Nonparty names and HP are neither emitted nor treated as party values.

### Current versus maximum: three independent uses

1. **Injured styling**, `0x673c96`–`0x673c9e`: the party row compares current
   (`+0x12b`) against maximum (`+0x32`) and selects the injured text style when
   current is smaller.
2. **Healing cap**, `0x69be02`–`0x69be28`: healing adds to `+0x12b`, compares it
   to `+0x32`, and copies maximum into current if the unsigned current exceeds it.
3. **Maximum increase**, `0x6a4242`–`0x6a427e`: the game calculates maximum minus
   current, increases maximum, and sets current to new maximum minus the missing
   amount. This establishes the relationship independently of a full-health save.

**These bytes are unsigned.** The guest formatter at `0x6a51dc` (the target of
the A5 `+0x3a2` jump-table entry) clears `d0`, loads the current-HP byte into it,
and passes that zero-extended value to `%d`. The healing/style branches also
use unsigned comparisons. A separate decrement path at `0x69e224`–`0x69e234`
only subtracts while current HP is positive. Do not interpret values 128–255
as negative HP based on tabletop rules. Death, unconsciousness and other
status fields have not been decoded by this slice.

## Reader and display packet

`POOLRAD_PARTY.h` reuses the existing `POOLRAD.h` app-name, A5, map-handle,
geometry/bootstrap and coordinate bounds checks. It resolves the current head
and every movable handle afresh, masks the established 24-bit address format,
and checks each address before dereferencing. Observed character blocks have
an eight-byte heap header with low-24-bit size 312, leaving **304 record bytes**.
This exact block size is required. Handle or record cycles/aliases, bad member
names, invalid pointers, truncated records, an empty party, duplicate member
slots or an oversized list make the whole sample unavailable.

HP is accepted only when `1 <= max <= 255` and `0 <= current <= max`. Zero
current is valid; it is not labeled dead. An unexplained above-maximum value
is rejected rather than silently clamped or assigned speculative meaning.

`poolrad_party_probe(ram, size, out)` writes a **168-byte PRP1 packet**:

```text
0..3       PRP1
4          member count (1..8)
5..7       zero
8..167     eight rows of 20 bytes:
             0..15   name, Macintosh Roman, NUL-terminated and zero-padded
             16      current HP, unsigned
             17      maximum HP, unsigned
             18..19  zero
           unused rows are entirely zero
```

The caller supplies a separate 168-byte output buffer. Failure returns zero
and clears it; success returns one. Only names, order and health leave this
reader—no heap pointers, raw character records or unrelated RAM.

`PartyState.parse(byte[])` returns null for unavailable/invalid packets, or an
immutable state with public `members`. Each member exposes `name`, `currentHp`,
`maxHp` and `healthFraction()`. Strict packet length, version, reserved-byte,
padding and HP checks reject malformed/native-version mismatches. Names use
Macintosh Roman, not UTF-8 or Latin-1. `sameDisplay` supports change-only redraws;
the state owns a defensive packet copy.

The emulator must sample on its core thread, not while another thread mutates
guest RAM. The UI must discard health on unavailable samples, pause/shutdown or
replacement of its core; retaining a previous valid packet is not live health.
This reader is **not** a new general exploration/combat/wilderness detector.

## Checks performed

- Native synthetic suite, compiled with address/undefined sanitizers: relocated
  A5/handles/records, one through eight members, selected-member changes,
  nontrivial linked order, synthetic damage/healing/zero and unsigned HP, invalid
  profile/bootstrap, pointer/record bounds, loops, duplicate records, bad heap
  size/names/health, and zeroed output on rejection.
- The character-boundary test retains all valid map/A5 fields while truncating
  exactly the final record byte; it rejects the short input and accepts the
  exact record end. Synthetic fixtures contain no original game records.
- Private `phlan-15-1-west-intro.ram` and `phlan-11-2-south-tour.ram` each decode
  the same ordered party: Arax the Bold 12/12, Lara Spellsword 8/8, Tanarakis 7/7,
  Hogarth 10/10, Shara the Grey 9/9, Zarram 9/9. Their exported PRP1 packets match
  byte-for-byte. `startup.ram` is rejected with no party packet emitted.
- **11 Java tests pass** in an isolated JUnit run: strict validation, immutable snapshots, one/eight-member
  bounds, reorder/join/leave changes, health changes, zero and unsigned HP,
  MacRoman names, padding and future/invalid packet rejection.
- The real native intro/tour packets also pass the Java decoder and compare
  equal; its empty startup output decodes as unavailable.
- **Combat regression (2026-09-14):** unchanged private capture
  `scratch/f7-combat-active-2.ram` contains six full-health heroes and ten ORCs.
  The old reader rejects it; the corrected reader returns exactly the six
  original health rows. Two sibling captures caught Finder scheduling and are
  correctly unavailable; their app/A5 fields were not patched for a passing test.
  Synthetic tests cover the 71-link / 63-enemy limits, interleaved member order,
  duplicate member slots, unassigned slots, enemy cycles/aliases/bad pointers,
  malformed heap blocks, and damage/healing with enemies present.

### Bounded live integration check (2026-09-13)

On the local Android API 30 emulator, with the integrated 149-test APK, the
original sample party and the right-hand health pane showed the same six names
and current HP: Arax the Bold 12/12, Lara Spellsword 8/8, Tanarakis 7/7,
Hogarth 10/10, Shara the Grey 9/9, and Zarram 9/9. All six bars were full.
The normal Rolf tour moved from New Phlan `15,1 W` to `0,4 W`; the map followed
the guest while names and full-health values remained stable.

The original game's supported order controls are **Encamp → Alter → Order**.
Selecting Zarram through that interface visibly highlighted the last member
and displayed “Zarram has been selected.” The companion pane still showed all
six members starting with Arax, not a truncated list beginning at the selected
member. This is a live check of the head-versus-selected-member distinction.

An attempted Place operation did **not** produce a changed order in either the
guest or companion pane, including after leaving Order. Consequently it is not
a successful reorder acceptance test. The approximately eight-minute check
ended without reaching actual damage or healing. No stats, RAM, notes, or saved
games were edited; only normal guest controls were used, with no save operation.

Private evidence: `scratch/party-live-initial.png`, `party-tour-gate.png`,
`party-order-selected.png`, and `party-order-exited.png`. These screenshots are
local emulator evidence, not physical-tablet acceptance.

### Live damage and reorder (2026-09-14)

The original sample party walked through the Slums gate to `10,2 W` and fought
the orcs using the game's own Combat/Quick commands. After victory the Mac's
party window and the companion agreed: Arax **7/12**, Lara **6/8**, Tanarakis
**7/7**, Hogarth **10/10**, Shara **9/9**, Zarram **5/9**. The missing-health
bar portions were visible. No stats, RAM or saves were edited to create damage.

In the Macintosh Order screen, click Zarram → Select, press ordinary digit
**1**, then Place. This wraps the last member to first; digit **7** moves up.
Clicking a different row changes the selection, not the destination. This
behavior was checked in CODE 4 `+0x1674..0x179e` and then performed in the
actual game. Both lists changed to Zarram, Arax, Lara, Tanarakis, Hogarth,
Shara, with the correct health still attached to each name. The original
Save command wrote a new private `F7Injured` file, not over `SampleParty`.

Private screenshots: `scratch/f7-postcombat.png`, `f7-reorder-actual.png`.
These first damage/reorder observations used the installed 0.5.1 build;
the combat-list correction is in the subsequent 0.5.2 build.

After a clean guest shutdown, the universal **0.5.2 / versionCode 69** APK
was installed over the existing app. The actual `F7Injured` save reopened with
the same order and all six HP pairs. Both the existing temple note and its
legacy backup retained their pre-update SHA-256 hashes.

A later rest was interrupted by kobolds. In the real combat screen, the
updated app retained all six reordered health rows, including Zarram 5/9,
Arax 7/12 and Lara 6/8; it did not list the kobolds as companions. Further actual
damage reduced Tanarakis to 5/7 and Zarram/Lara to 0/9 and 0/8. Empty bars
matched zero HP without inventing a dead/unconscious status. Evidence:
`scratch/f7-reload-settled.png`, `f7-fixed-combat.png`, `f7-kobold-progress.png`
and `f7-camp-heal.png`. Occasional unavailable samples during Mac task scheduling
still clear the strip; reliable mode/lifecycle presentation remains in M2,
not disguised as verified live data.

### Actual healing (2026-09-14, 0.5.2)

After the kobold victory, Tanarakis prepared **Cure Light Wounds** through
Encamp → Magic → Memorize. The original game's confirmation must receive
**Yes**; exiting the pending-spell summary is not itself confirmation.
The game calculated **4 hours 15 minutes** of rest and completed it, advancing
the camp clock from 06:29 to 10:44. Cast → Cure Light Wounds → Arax → Select
raised Arax from **7/12 to 12/12**. Both the original party window and companion
row showed 12, and Arax's bar filled while the other five HP pairs stayed fixed.
Private evidence: `scratch/f7-spell-confirm.png`, `f7-rest-confirmed.png`,
`f7-cast-list.png` and `f7-healed-actual.png`.

This used normal guest inputs, not memory edits, save patching, or an app
auto-heal feature. The workflow reuses the supplied Rule Book Section 3 page 22;
CODE 4 `+0x0b54..0x0b70` confirms that **No** cancels pending memorization.
An earlier canceled selection and a later interrupted long rest were not
counted as healing. No game/manual bytes or private saves are published.

Seven focused actual Android `LiveMapView` software-Canvas checks pass on API
30: full/half/zero bars and labels; damage/healing confined to the party region;
two-column narrow layout; correct map/sidebar touch routing after resize;
reorder; unavailable/invalid clearing; tiny-window collapse and restoration.
Rebuild/run instructions are in `tools/PartyPaneRenderCheck.java`. The harness
initializes Android's default system fonts, normally initialized during APK
startup but absent from standalone `app_process`. These synthetic View checks
are not a substitute for real guest combat or physical-tablet acceptance.

**F7 acceptance is complete** for the supported Mac v1.1 profile: real damage,
healing, reorder and reload plus focused layout checks. This does **not**
establish all encounters/NPC variants/modes, physical e-ink readability, or
stylus/device acceptance. Those remain separate backlog work.

Run the focused native checks from the repository root:

```sh
cc -std=c11 -Wall -Wextra -Werror -fsanitize=address,undefined \
  tools/test-party-probe.c -o scratch/test-party-probe
scratch/test-party-probe
```

An optional argument replays a private RAM file: binary PRP1 goes to stdout,
readable HP to stderr; exit status 2 means unavailable. Keep captures and
generated packets in ignored `scratch/`. Java coverage is `PartyStateTest`
under the normal Android `testMacIIDebugUnitTest` task.

## Provenance

This work reuses the repository's already-tested profile/bounds helpers and
[local Macintosh identity findings](LOCAL_TESTING.md). The locally checked-out
[Gold Box Explorer](https://github.com/bsimser/Gold-Box-Explorer) character
annotations (`FruaCharacter.cs`, maximum HP at `0x32`) provided a search clue,
not a portable Mac layout. Its different current-HP offset was **not** copied.
See the existing [Gold Box Explorer license](../licenses/GoldBoxExplorer-MIT.txt).
The Mac offsets, list behavior and unsigned interpretation above were verified
independently in the supplied executable using read-only Capstone disassembly.
No original executable bytes, game records, saves or RAM are published here.
