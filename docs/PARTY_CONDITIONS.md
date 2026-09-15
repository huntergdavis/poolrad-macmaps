# Party conditions

The party sidebar now shows one dark condition badge in the existing class-icon
slot. Tap the character for the full condition, injury and tracked effects.
Names, current/max HP, AC and health bars retain their space. No new panel,
color dependency, flashing indicator or guest-game modification.

| Badge | Meaning |
| --- | --- |
| `+` | Injured: current HP is below maximum |
| `Z` | Unconscious |
| `!` | Dying |
| `X` | Dead |
| `S` | Petrified (the original game calls this Stoned) |
| `P` | Poisoned |
| `H` | Helpless |
| `A` | Animated |
| `>` | Running |
| `–` | Temporarily gone or Gone; tap for the exact status |
| `?` | Condition or tracked effects unavailable |

Death/dying/unconsciousness/petrification/absence take precedence over effects;
poison then helplessness take precedence over running/animated and injury.
Details retain the whole observed combination. The ordinary class symbol
returns when no badge is needed; class text remains in details throughout.
Unconscious, Dying and Dead are **not** inferred from zero HP.

Only poison and the original game's aggregate **Helpless** effect are tracked.
This is not an exhaustive buff/debuff viewer, duration estimate or spell list.
An unreadable effect chain says unavailable, not “no effects.” Small layouts
keep the existing sidebar collapse and named accessibility detail actions.

## Macintosh v1.1 evidence

Reuses the established character-list/heap guards in [PARTY.md](PARTY.md) and
the existing private `scratch/m4-code.py` disassembly helper. The required local
session recall query timed out; no unverified earlier-session claim was used.
Offsets below were checked against the user's supplied executable and original
RAM captures. No executable bytes, private RAM or game assets are included here.

**Primary condition:** character record `+0x118`, a byte indexing the nine-entry
name table at `A5−0x5dd6`. CODE3 `+0x3d16..+0x3d68` reads this for the original
Information window; CODE3 `+0x0ab8..+0x0ac8` uses it in the party formatter.

| Byte | Original name | Companion wording |
| --- | --- | --- |
| 0 | Okay | Okay |
| 1 | Animated | Animated |
| 2 | tempgone | Temporarily gone |
| 3 | Running | Running |
| 4 | Unconscious | Unconscious |
| 5 | Dying | Dying |
| 6 | Dead | Dead |
| 7 | Stoned | Petrified (Stoned) |
| 8 | Gone | Gone |

Independent semantics: CODE3's damage routine `+0x245c..+0x2596` writes 6 at
`+0x24d6` for fatal damage, 5 at `+0x24ee` for dying, and 4 at `+0x2520` for
exact-zero unconsciousness. CODE9 `+0x5b50..+0x5b60` changes Dying to
Unconscious after combat. CODE4's Stone to Flesh path `+0x75ca..+0x7646`
checks and clears status 7. This is why HP alone is insufficient.

**Effect chain:** character `+0x82` is a movable handle. Each handle resolves to
a **10-byte logical record**: effect ID at `+0`, next handle at `+6`.
CODE3 `+0x2406..+0x245a` implements the original ID lookup. CODE11's allocation
routine at `+0x0ea2` requests ten bytes at `+0x0ea8`, appends through `+6` and
writes the effect ID at `+0x0f12`. The companion follows the same ID-presence
semantics; it does not interpret the intervening duration/parameter bytes.

- **Poison: ID55 (`0x37`).** CODE4 `+0x747a..+0x7522` looks up 55 before
  Neutralize Poison. Its negative case uses STRS0 `+0x2e08`, “is not poisoned.”;
  the cure text is at `+0x2e1a`.
- **Helpless: IDs51,52,53,31.** CODE3 `+0x0b3e..+0x0b96` searches these four
  IDs using the original table at `A5−0x3d94`. Its party-formatter caller at
  `+0x0b04..+0x0b32` prints STRS0 `+0x134c`, “(Helpless)”. The individual IDs
  are deliberately not given guessed spell names.

The reader bounds each effect traversal at 64 nodes (a defensive work limit,
not a claim about the game's maximum), verifies alignment, RAM bounds,
relocatable heap type and exact logical size including allocator padding,
and rejects cycles/aliased nodes. A malformed or over-limit chain makes only
that member's tracked effects unavailable; valid names, HP, AC and primary
condition remain visible. Unknown condition bytes normalize to unavailable.
The existing eight-member/combat-monster filtering and application-profile
validation remain unchanged. Nothing writes to guest RAM.

## Small packet, compatible reader

PRP3 is **184 bytes**: the existing 168-byte PRP2 base (with version byte `3`)
plus eight pairs at offsets `168 + memberIndex × 2`:

```text
condition: 0..8, or FF unavailable
effects:   bit0 poison, bit1 helpless; FF unavailable
unused member pairs: 00 00
```

The Java parser still accepts exact-size 168-byte PRP1/PRP2 packets, with
conditions/effects explicitly unavailable. Legacy healthy packets retain their
class symbols. Unsupported version/length/flags and nonzero unused pairs are
rejected. Condition-only changes invalidate the immutable display and pending
character gestures, just like HP or party-order changes.

## Verification scope

Native tests cover all 256 condition bytes and all 256 effect IDs, combined
effects, cycle/alias rejection, bad pointers, exact logical allocation sizes,
64/65-node bounds, compatibility fields and failure clearing. Original intro,
fresh-party, camp, area-change and one active-combat capture replay with the
six correct characters, HP, **Okay** status and no tracked effects; unrelated
effect IDs are ignored rather than misnamed. The remaining combat captures were
already unavailable to the reader before this change, and still are: no
condition is invented for a sample the existing guards reject.

Java tests cover labels, zero-HP ambiguity, priorities, unavailable data,
legacy packets, strict parsing, snapshots and per-member independence.
The Android View harness checks distinct monochrome badges, changes confined
to the existing icon slot, unchanged health bars/map pixels, healthy recovery,
narrow layout, accessibility labels and cancelled stale taps/actions.

See [LOCAL_TESTING.md](LOCAL_TESTING.md) for the final build and live-emulator
checks. The **injured** badge has since been seen in actual play: an accidental
council-guard fight on 2026-09-14 left five members below full HP and the
sidebar drew `+` beside exactly those five. The other conditions remain
synthetic renderer/decoder coverage, **not** a claim that every condition was
played through in the original game. Physical
tablet acceptance of these new badges is still untested; the user's earlier
notebook stylus/e-ink acceptance remains valid and separate.
